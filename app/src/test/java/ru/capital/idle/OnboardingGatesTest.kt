package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Milestones
import ru.capital.idle.core.game.Onboarding
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Раскрытие разделов: кто считается ветераном и что написано на замке.
 *
 * Обе правки — из находок: ветеран по слиткам нашла автоматическая симуляция (PR #29),
 * а текст замка «Развитие» противоречил условию на новой игре.
 */
class OnboardingGatesTest {

    private val allIds = listOf("dev", "inv", "world", "prof", "net", "pres")

    /** Состояние с заданным капиталом и уже проставленным храповиком. */
    private fun withCapital(worth: Double) =
        GameState(money = worth, onboarded = true).let { it.copy(peakNetWorth = GameMath.netWorth(it)) }

    // ===================== ветеран =====================

    @Test
    fun `слитки за веху не делают игрока ветераном`() {
        // веха «Первый миллион» выдаёт пять слитков ещё в первой жизни
        val first = Milestones.all.first()
        val s = withCapital(1_000_000.0).copy(
            bullion = first.rewardBullion, milestonesClaimed = 1, statLives = 0
        )
        assertTrue("подготовка теста: слитки должны быть", s.bullion > 0)

        assertTrue("«Мир» открыт по капиталу", Onboarding.unlocked(s, "world"))
        assertFalse("а «Престиж» — нет, до него миллиард", Onboarding.unlocked(s, "pres"))
    }

    @Test
    fun `престиж открывается на миллиарде, а не на миллионе`() {
        val million = withCapital(1_000_000.0).copy(bullion = 5)
        val billion = withCapital(1_000_000_000.0).copy(bullion = 5)

        assertFalse(Onboarding.unlocked(million, "pres"))
        assertTrue(Onboarding.unlocked(billion, "pres"))
    }

    @Test
    fun `престиж-апгрейды сами по себе тоже не делают ветераном`() {
        // купить их можно только за слитки, а слитки бывают и за вехи
        val s = withCapital(1_000_000.0).copy(bullion = 2, pIncome = 3, statLives = 0)
        assertFalse(Onboarding.unlocked(s, "pres"))
    }

    @Test
    fun `после перерождения игрок ветеран и разделы открыты`() {
        val s = GameState(onboarded = true, statLives = 1)
        allIds.forEach { id ->
            assertTrue("«$id» должен быть открыт ветерану", Onboarding.unlocked(s, id))
        }
    }

    // ===================== старые сохранения =====================

    private fun jsonWithout(st: GameState, vararg keys: String): String {
        val o = org.json.JSONObject(SaveFile.toJson(st.toEntity()))
        keys.forEach { o.remove(it) }
        return o.toString()
    }

    @Test
    fun `старое сохранение переродившегося игрока остаётся ветеранским`() {
        // в сохранении до появления счётчика перерождения видны по музею и дням прошлых жизней
        val old = GameState(
            onboarded = true, bullion = 40, statDaysPrevLives = 420,
            museum = listOf("1|180|4.2e11|6|5|7", "2|200|9.9e11|7|6|8")
        )
        val back = SaveFile.fromJson(jsonWithout(old, "statLives"))!!.toState()

        assertEquals("две прошлые жизни", 2, back.statLives)
        allIds.forEach { assertTrue("«$it» открыт ветерану", Onboarding.unlocked(back, it)) }
    }

    @Test
    fun `старое сохранение со слитками без перерождений теряет ветеранство, но не доступ`() {
        val old = GameState(
            money = 1_200_000.0, onboarded = true, bullion = 5, milestonesClaimed = 1,
            announced = setOf("dev", "inv", "world", "prof", "net")
        )
        val back = SaveFile.fromJson(jsonWithout(old, "statLives", "peakNetWorth"))!!.toState()

        assertEquals("перерождений не было", 0, back.statLives)
        // объявленное остаётся открытым — условие из PR #28
        listOf("dev", "inv", "world", "prof", "net").forEach {
            assertTrue("«$it» уже объявляли, доступ терять нельзя", Onboarding.unlocked(back, it))
        }
        assertFalse("а «Престиж» не объявляли и капитала на него нет",
            Onboarding.unlocked(back, "pres"))
    }

    @Test
    fun `счётчик жизней переживает круг записи-чтения`() {
        val st = GameState(onboarded = true, statLives = 3)
        assertEquals(3, SaveFile.fromJson(SaveFile.toJson(st.toEntity()))!!.toState().statLives)
    }

    // ===================== тексты замков =====================

    /**
     * Гид продвигается в игровом цикле: как только условие шага выполнено, шаг растёт.
     * Здесь то же самое, чтобы сравнивать текст с условием на устоявшемся состоянии.
     */
    private fun settleGuide(s0: GameState): GameState {
        var s = s0
        var guard = 0
        while (s.tutorialStep < Onboarding.steps.size && Onboarding.stepDone(s) && guard++ < 10) {
            s = s.copy(tutorialStep = s.tutorialStep + 1)
        }
        return s
    }

    @Test
    fun `на новой игре с полусотней без работы замок Развития просит работу`() {
        // ровно случай из отчёта: капитал $ 50, работы нет
        val s = settleGuide(GameState(money = 50.0, onboarded = true))
        assertEquals("подготовка теста: гид стоит на шаге с работой", 1, s.tutorialStep)
        assertFalse("раздел закрыт", Onboarding.unlocked(s, "dev"))

        val (title, text) = Onboarding.lockText("dev", s)
        assertEquals("Развитие", title)
        assertTrue("замок обязан просить работу, а не деньги: «$text»", text.contains("работу"))
        assertFalse("про накопить $ 50 говорить нельзя — они уже есть", text.contains("50"))
    }

    @Test
    fun `с работой и тридцатью долларами замок Развития просит деньги`() {
        val s = settleGuide(GameState(money = 30.0, jobId = "courier", onboarded = true))
        assertEquals("подготовка теста: гид стоит на шаге с деньгами", 2, s.tutorialStep)
        assertFalse(Onboarding.unlocked(s, "dev"))

        val (_, text) = Onboarding.lockText("dev", s)
        assertTrue("замок обязан просить деньги: «$text»", text.contains("50"))
        assertFalse("про работу говорить уже незачем", text.contains("Устройтесь"))
    }

    @Test
    fun `в самом начале замок просит первые десять долларов`() {
        val s = GameState(onboarded = true)
        assertEquals(0, s.tutorialStep)
        val (_, text) = Onboarding.lockText("dev", s)
        assertTrue("«$text»", text.contains("10"))
    }

    @Test
    fun `замок Профиля тоже называет текущий шаг`() {
        val start = GameState(onboarded = true)
        assertTrue(Onboarding.lockText("prof", start).second.contains("10"))

        val withMoney = settleGuide(GameState(money = 12.0, onboarded = true))
        assertEquals(1, withMoney.tutorialStep)
        assertTrue(Onboarding.lockText("prof", withMoney).second.contains("работу"))
    }

    /**
     * Главная проверка: текст замка не должен называть условие, которое уже выполнено.
     * Именно на этом ломался игрок — читал выполненное требование при закрытом разделе.
     */
    @Test
    fun `закрытый раздел всегда называет невыполненное условие`() {
        val states = listOf(
            GameState(onboarded = true),
            GameState(money = 10.0, onboarded = true),
            GameState(money = 50.0, onboarded = true),
            GameState(money = 50.0, jobId = "courier", onboarded = true),
            GameState(money = 30.0, jobId = "courier", onboarded = true),
            GameState(money = 500.0, jobId = "courier", onboarded = true, tutorialStep = 3),
            withCapital(4_000.0),
            withCapital(900_000.0),
            withCapital(50_000_000.0)
        ).map { settleGuide(it) }

        states.forEach { s ->
            allIds.forEach { id ->
                if (!Onboarding.unlocked(s, id)) {
                    val lock = Onboarding.lock(id, s)
                    assertFalse(
                        "раздел «$id» закрыт, а замок говорит «${lock.text}» — а это уже выполнено " +
                            "(деньги ${s.money}, работа «${s.jobId}», шаг ${s.tutorialStep}, " +
                            "капитал ${s.peakNetWorth})",
                        lock.done(s)
                    )
                }
            }
        }
    }

    /** И наоборот: выполнено названное условие — раздел открывается (для составных — после гида). */
    @Test
    fun `выполненное условие замка открывает раздел`() {
        // «Инвестиции», «Мир», «Окружение», «Престиж» — условие одно, оно же и открывает
        mapOf(
            "inv" to GameState(onboarded = true, eduDone = setOf("acc")),
            "world" to withCapital(1_000_000.0),
            "net" to withCapital(5_000.0),
            "pres" to withCapital(1_000_000_000.0)
        ).forEach { (id, s) ->
            assertTrue("условие замка выполнено", Onboarding.lock(id, s).done(s))
            assertTrue("значит, раздел «$id» обязан открыться", Onboarding.unlocked(s, id))
        }

        // «Развитие» и «Профиль» составные: выполняем шаг за шагом, и раздел открывается
        var s = GameState(onboarded = true)
        var guard = 0
        while (!Onboarding.unlocked(s, "dev") && guard++ < 10) {
            val lock = Onboarding.lock("dev", s)
            s = settleGuide(
                when (s.tutorialStep) {
                    0 -> s.copy(money = 10.0)
                    1 -> s.copy(jobId = "courier")
                    else -> s.copy(money = 50.0)
                }
            )
            assertTrue("шаг «${lock.text}» должен был выполниться", lock.done(s))
        }
        assertTrue("«Развитие» открылось", Onboarding.unlocked(s, "dev"))
        assertTrue("и «Профиль» вместе с ним", Onboarding.unlocked(s, "prof"))
    }
}
