package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.Asset
import ru.capital.idle.core.game.AutoInvest
import ru.capital.idle.core.game.GameLoop
import ru.capital.idle.core.game.GameState
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Автовклад: раз в игровой день переносит деньги с карты во вклад.
 *
 * Не путать с капитализацией: та оставляет во вкладе его собственный доход, а автовклад
 * берёт деньги с карты. Механики разные и работают вместе — здесь проверяется вторая.
 */
class AutoInvestTest {

    /** Игрок с открытыми вкладами, без источников дохода: деньги двигает только автовклад. */
    private fun saver(
        money: Double = 10_000.0,
        reserve: Double = 1_000.0,
        on: Boolean = true,
        debt: Double = 0.0,
        edu: Set<String> = setOf("school", "acc")
    ) = GameState(
        money = money, debt = debt, eduDone = edu,
        autoInvestOn = on, autoInvestReserve = reserve,
        gameHours = 23.0, statsShownDay = 1, pressureDay = 1
    )

    /** Смена игрового дня: цикл проходит границу суток. */
    private fun crossDay(s: GameState) = GameLoop.progress(s.copy(gameHours = 24.0))

    private fun invested(s: GameState) = s.investValues.sum()

    // ===================== перенос =====================

    @Test
    fun `деньги сверх резерва уходят во вклад при смене игрового дня`() {
        val after = crossDay(saver())
        assertEquals("во вклад ушло превышение над резервом", 9_000.0, invested(after), EPS)
        assertEquals("резерв остался на карте", 1_000.0, after.money, EPS)
    }

    @Test
    fun `сумма резерва остаётся на карте при любом размере резерва`() {
        listOf(0.0, 100.0, 1_000.0, 10_000.0).forEach { reserve ->
            val after = crossDay(saver(money = 50_000.0, reserve = reserve))
            assertEquals("резерв $reserve", reserve, after.money, EPS)
            assertEquals(50_000.0 - reserve, invested(after), EPS)
        }
    }

    @Test
    fun `вложенное учитывается и в теле вклада, и в его стоимости`() {
        val after = crossDay(saver())
        val i = AutoInvest.target(after)!!.ordinal
        assertEquals(9_000.0, after.investValues[i], EPS)
        assertEquals("вложено столько же — иначе прибыль вклада посчитается неверно",
            9_000.0, after.investCosts[i], EPS)
    }

    @Test
    fun `на карте не больше резерва — ничего не происходит`() {
        val s = saver(money = 800.0, reserve = 1_000.0)
        val after = crossDay(s)
        assertEquals(800.0, after.money, EPS)
        assertEquals(0.0, invested(after), EPS)
        assertEquals("на карте не больше резерва", AutoInvest.blockedReason(s))
    }

    @Test
    fun `выключенный автовклад ничего не переносит`() {
        val after = crossDay(saver(on = false))
        assertEquals(10_000.0, after.money, EPS)
        assertEquals(0.0, invested(after), EPS)
        assertNull("выключенный — это не «заблокирован»", AutoInvest.blockedReason(saver(on = false)))
    }

    // ===================== долг =====================

    @Test
    fun `при долге автовклад не срабатывает`() {
        val s = saver(debt = 500.0)
        assertEquals(0.0, AutoInvest.amount(s), EPS)
        assertEquals("сначала гасим долг", AutoInvest.blockedReason(s))

        val after = crossDay(s)
        assertEquals("деньги остались на карте", 10_000.0, after.money, EPS)
        assertEquals(0.0, invested(after), EPS)
    }

    @Test
    fun `после погашения долга автовклад снова работает`() {
        val after = crossDay(saver(debt = 0.0))
        assertTrue(invested(after) > 0.0)
    }

    // ===================== раз в игровой день =====================

    @Test
    fun `в пределах одного игрового дня срабатывает не больше раза`() {
        val first = crossDay(saver())
        assertEquals(9_000.0, invested(first), EPS)
        assertEquals(2, first.statsShownDay)

        // деньги пришли снова в тот же день — до следующей смены суток они остаются на карте
        val sameDay = GameLoop.progress(first.copy(money = first.money + 5_000.0))
        assertEquals("новый доход ждёт следующего дня", 6_000.0, sameDay.money, EPS)
        assertEquals(9_000.0, invested(sameDay), EPS)

        // а на следующем дне уходит
        val nextDay = GameLoop.progress(sameDay.copy(gameHours = 48.0))
        assertEquals(1_000.0, nextDay.money, EPS)
        assertEquals(14_000.0, invested(nextDay), EPS)
    }

    @Test
    fun `много тиков внутри суток не дают много переносов`() {
        var s = saver()
        // 24 прохода цикла в пределах тех же суток
        repeat(24) { s = GameLoop.progress(s.copy(money = s.money + 100.0)) }
        assertEquals("ни одного переноса до смены суток", 0.0, invested(s), EPS)
        assertEquals(10_000.0 + 2_400.0, s.money, EPS)
    }

    // ===================== оффлайн =====================

    /**
     * Игровые часы, пока игра закрыта, стоят, поэтому смены дня не будет: при возвращении
     * автовклад вызывается один раз напрямую — так делает `applyOfflineProgress`.
     */
    @Test
    fun `возвращение из оффлайна даёт одно срабатывание`() {
        // за неделю отсутствия начислено разом
        val back = saver(money = 10_000.0).copy(money = 10_000.0 + 70_000.0)
        val once = AutoInvest.apply(back)

        assertEquals("на карте остался ровно резерв", 1_000.0, once.money, EPS)
        assertEquals(79_000.0, invested(once), EPS)

        // повторный вызов ничего не добавляет: превышения над резервом больше нет
        val twice = AutoInvest.apply(once)
        assertEquals(1_000.0, twice.money, EPS)
        assertEquals("второго переноса быть не должно", 79_000.0, invested(twice), EPS)
    }

    // ===================== выбор инструмента =====================

    @Test
    fun `по умолчанию деньги идут в лучший доступный инструмент`() {
        // с «Бухгалтерией» открыты депозит и облигации, лучший из них — облигации
        val s = saver(edu = setOf("school", "acc"))
        assertEquals(Asset.BONDS, AutoInvest.target(s))

        // с «Университетом» открывается недвижимость
        assertEquals(Asset.REALTY, AutoInvest.target(s.copy(eduDone = setOf("school", "acc", "uni"))))
    }

    @Test
    fun `закреплённый инструмент важнее лучшего`() {
        val s = saver(edu = setOf("school", "acc", "uni")).copy(autoInvestAsset = Asset.DEPOSIT.ordinal)
        assertEquals(Asset.DEPOSIT, AutoInvest.target(s))

        val after = crossDay(s)
        assertEquals(9_000.0, after.investValues[Asset.DEPOSIT.ordinal], EPS)
        assertEquals(0.0, after.investValues[Asset.REALTY.ordinal], EPS)
    }

    @Test
    fun `закреплённый, но ещё не открытый инструмент подменяется доступным`() {
        val s = saver(edu = setOf("school", "acc")).copy(autoInvestAsset = Asset.REALTY.ordinal)
        assertEquals("недвижимость требует «Университет»", Asset.BONDS, AutoInvest.target(s))
    }

    @Test
    fun `без открытых инструментов автовклад не срабатывает`() {
        val s = saver(edu = setOf("school"))
        assertNull(AutoInvest.target(s))
        assertEquals(0.0, AutoInvest.amount(s), EPS)
        assertEquals("нет открытых инструментов", AutoInvest.blockedReason(s))
        assertEquals(10_000.0, crossDay(s).money, EPS)
    }

    /**
     * Список причин закрыт, и `blockedReason` не выдаёт ничего сверх него.
     *
     * На список опирается вёрстка: карточка автовклада меряет каждую причину, чтобы место
     * под итог не меняло высоту (иначе экран подпрыгивает каждый игровой день). Причина
     * мимо списка осталась бы неизмеренной — и подпрыгивание вернулось бы.
     */
    @Test
    fun `все причины блокировки перечислены в списке`() {
        val cases = listOf(
            saver(debt = 500.0),                 // сначала гасим долг
            saver(edu = setOf("school")),        // нет открытых инструментов
            saver(money = 800.0, reserve = 1_000.0)  // на карте не больше резерва
        )
        val seen = cases.mapNotNull { AutoInvest.blockedReason(it) }
        assertEquals("каждый случай даёт причину", cases.size, seen.size)
        assertEquals("список причин перечисляет ровно их", AutoInvest.REASONS.toSet(), seen.toSet())
    }

    // ===================== резерв =====================

    @Test
    fun `ступени резерва идут по порядкам и не выходят за края`() {
        assertEquals(0.0, AutoInvest.RESERVE_STEPS.first(), EPS)
        assertEquals(0.0, AutoInvest.stepDown(0.0), EPS)
        assertEquals(100.0, AutoInvest.stepUp(0.0), EPS)
        assertEquals(1_000.0, AutoInvest.stepUp(100.0), EPS)
        assertEquals(100.0, AutoInvest.stepDown(1_000.0), EPS)

        val top = AutoInvest.RESERVE_STEPS.last()
        assertEquals("выше последней ступени не уйти", top, AutoInvest.stepUp(top), EPS)

        // ступени строго возрастают
        AutoInvest.RESERVE_STEPS.zipWithNext { a, b -> assertTrue("$a -> $b", b > a) }
    }

    // ===================== сохранения =====================

    @Test
    fun `настройки автовклада переживают круг записи-чтения`() {
        val s = saver().copy(autoInvestAsset = Asset.DEPOSIT.ordinal, autoInvestReserve = 10_000.0)
        val back = SaveFile.fromJson(SaveFile.toJson(s.toEntity()))!!.toState()

        assertTrue(back.autoInvestOn)
        assertEquals(Asset.DEPOSIT.ordinal, back.autoInvestAsset)
        assertEquals(10_000.0, back.autoInvestReserve, EPS)
    }

    @Test
    fun `в старом сохранении автовклад выключен`() {
        val o = org.json.JSONObject(SaveFile.toJson(saver().toEntity()))
        listOf("autoInvestOn", "autoInvestAsset", "autoInvestReserve").forEach { o.remove(it) }
        val back = SaveFile.fromJson(o.toString())!!.toState()

        assertFalse("включать без спроса нельзя", back.autoInvestOn)
        assertEquals(-1, back.autoInvestAsset)
        assertEquals(0.0, back.autoInvestReserve, EPS)
        assertEquals("и ничего не переносит", 10_000.0, crossDay(back).money, EPS)
    }

    // ===================== это не капитализация =====================

    /**
     * Механики независимы: капитализация оставляет во вкладе его собственный доход,
     * автовклад переносит деньги с карты. Работать они могут вместе.
     */
    @Test
    fun `автовклад и капитализация не мешают друг другу`() {
        val withCap = saver().copy(capitalizeMask = 1 shl Asset.BONDS.ordinal)
        val after = crossDay(withCap)
        assertEquals("перенос состоялся", 9_000.0, invested(after), EPS)
        assertEquals("капитализация осталась включённой",
            1 shl Asset.BONDS.ordinal, after.capitalizeMask)
    }
}
