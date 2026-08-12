package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.core.game.Milestones
import ru.capital.idle.core.game.Onboarding
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Пороги разделов и титулов считаются от капитала, а не от валового дохода.
 *
 * На главном экране показан капитал (`GameMath.netWorth`) — чистые активы. Пороги же
 * сравнивались с `totalEarned`, валовым доходом до вычета зарплат управляющих и содержания
 * имущества. Числа расходились на глазах: при капитале 791 464 игрок уже получал титул
 * «Миллионер» и доступ к «Миру».
 *
 * Сравнивается при этом не текущий капитал, а максимальный достигнутый: капитал умеет
 * падать, а открытый раздел не должен закрываться и полученный титул — понижаться.
 */
class ProgressGatesTest {

    /** Состояние с заданным капиталом и уже проставленным храповиком. */
    private fun withCapital(worth: Double): GameState {
        val st = GameState(money = worth, onboarded = true)
        assertEquals("подготовка теста: капитал равен наличным", worth, GameMath.netWorth(st), EPS)
        return st.copy(peakNetWorth = GameMath.netWorth(st))
    }

    private val millionaire = Lifestyle.titles.indexOfFirst { it.name == "Миллионер" }

    // ===================== пороги разделов =====================

    @Test
    fun `раздел Мир открывается ровно при капитале миллион`() {
        assertFalse("до миллиона — закрыт", Onboarding.unlocked(withCapital(999_999.99), "world"))
        assertTrue("ровно на миллионе — открыт", Onboarding.unlocked(withCapital(1_000_000.0), "world"))

        // случай из замера: капитала 791 464 не хватает, хотя валовой доход давно за миллионом
        val fromReport = withCapital(791_464.0).copy(totalEarned = 5_000_000.0)
        assertFalse("валовой доход больше не открывает раздел",
            Onboarding.unlocked(fromReport, "world"))
    }

    @Test
    fun `подразделы окружения и престижа тоже по капиталу`() {
        assertFalse(Onboarding.unlocked(withCapital(4_999.99), "net"))
        assertTrue(Onboarding.unlocked(withCapital(5_000.0), "net"))

        assertFalse(Onboarding.unlocked(withCapital(999_999_999.0), "pres"))
        assertTrue(Onboarding.unlocked(withCapital(1_000_000_000.0), "pres"))
    }

    /** Условие про вехи для «Мира» осталось прежним: одна взятая веха открывает раздел. */
    @Test
    fun `взятая веха открывает Мир и без капитала`() {
        assertTrue(Onboarding.unlocked(withCapital(1_000.0).copy(milestonesClaimed = 1), "world"))
    }

    // ===================== титул =====================

    @Test
    fun `титул Миллионер выдаётся при том же капитале`() {
        assertEquals(1_000_000.0, Lifestyle.titles[millionaire].threshold, EPS)
        assertTrue(Lifestyle.titleIndex(1_000_000.0) == millionaire)
        assertTrue("до миллиона титул ниже", Lifestyle.titleIndex(999_999.99) < millionaire)
    }

    @Test
    fun `титул и доступ к Миру приходят на одном и том же капитале`() {
        val below = withCapital(999_999.99)
        val at = withCapital(1_000_000.0)

        assertFalse(Onboarding.unlocked(below, "world"))
        assertTrue(Lifestyle.titleIndex(below.peakNetWorth) < millionaire)

        assertTrue(Onboarding.unlocked(at, "world"))
        assertEquals(millionaire, Lifestyle.titleIndex(at.peakNetWorth))
    }

    // ===================== храповик =====================

    @Test
    fun `после падения капитала раздел остаётся открытым, а титул не понижается`() {
        val rich = withCapital(2_000_000.0)
        assertTrue(Onboarding.unlocked(rich, "world"))
        assertEquals(millionaire, Lifestyle.titleIndex(rich.peakNetWorth))

        // акции рухнули, имущество продано: наличных осталось на сотню
        val crashed = rich.copy(money = 100.0)
        assertEquals("подготовка теста: капитал действительно упал",
            100.0, GameMath.netWorth(crashed), EPS)

        assertTrue("раздел закрываться не должен", Onboarding.unlocked(crashed, "world"))
        assertEquals("титул понижаться не должен",
            millionaire, Lifestyle.titleIndex(crashed.peakNetWorth))
    }

    // ===================== вехи =====================

    /**
     * Вехи и раньше считались по капиталу (в игровом цикле — от `GameMath.netWorth`),
     * менять их не пришлось. Здесь закрепляется, что после перевода титулов на ту же
     * величину «Первый миллион» и «Миллионер» срабатывают на одном значении.
     */
    @Test
    fun `веха Первый миллион и титул Миллионер срабатывают на одном значении`() {
        val first = Milestones.all.first { it.name.contains("миллион", ignoreCase = true) }
        assertEquals("порог вехи и порог титула — одно число",
            Lifestyle.titles[millionaire].threshold, first.thresholdUsd, EPS)

        val at = withCapital(first.thresholdUsd)
        assertTrue("веха берётся", GameMath.netWorth(at) >= first.thresholdUsd)
        assertEquals("титул выдаётся", millionaire, Lifestyle.titleIndex(at.peakNetWorth))

        val below = withCapital(first.thresholdUsd - 0.01)
        assertFalse("веха не берётся", GameMath.netWorth(below) >= first.thresholdUsd)
        assertTrue("титула нет", Lifestyle.titleIndex(below.peakNetWorth) < millionaire)
    }

    // ===================== старые сохранения =====================

    /** Файл, записанный до появления храповика: ключа `peakNetWorth` в нём нет. */
    private fun jsonWithoutPeak(st: GameState): String {
        val o = org.json.JSONObject(SaveFile.toJson(st.toEntity()))
        o.remove("peakNetWorth")
        return o.toString()
    }

    /**
     * Игрок старой версии: валовой доход давно за миллионом, «Мир» открыт и объявлен,
     * а капитала на миллион не набирается — деньги ушли в содержание и зарплаты.
     * Доступ он терять не должен.
     */
    @Test
    fun `старое сохранение с открытым Миром сохраняет доступ при малом капитале`() {
        val old = GameState(
            money = 300_000.0, totalEarned = 8_000_000.0, onboarded = true,
            announced = setOf("dev", "inv", "world", "prof", "net")
        )
        val back = SaveFile.fromJson(jsonWithoutPeak(old))!!.toState()

        assertTrue("капитала на порог не хватает", back.peakNetWorth < 1_000_000.0)
        assertTrue("раздел остаётся открытым", Onboarding.unlocked(back, "world"))
        assertTrue("и окружение тоже", Onboarding.unlocked(back, "net"))
        assertFalse("а необъявленный престиж закрыт — капитала на него нет",
            Onboarding.unlocked(back, "pres"))
    }

    @Test
    fun `при загрузке храповик встаёт на текущий капитал`() {
        val old = GameState(money = 4_200_000.0, onboarded = true)
        val back = SaveFile.fromJson(jsonWithoutPeak(old))!!.toState()
        assertEquals(GameMath.netWorth(old), back.peakNetWorth, EPS)
    }

    @Test
    fun `накопленный храповик переживает круг записи-чтения и не падает`() {
        val st = GameState(money = 100.0, peakNetWorth = 9_000_000.0, onboarded = true)
        val back = SaveFile.fromJson(SaveFile.toJson(st.toEntity()))!!.toState()
        assertEquals("максимум сохраняется, даже если капитал давно просел",
            9_000_000.0, back.peakNetWorth, EPS)
        assertTrue(Onboarding.unlocked(back, "world"))
    }
}
