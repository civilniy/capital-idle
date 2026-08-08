package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.GameTestFixtures.withEnterprises
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.GameTime
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Manager

/**
 * Строка окупаемости на карточке предприятия: что в ней стоит и как часто она меняется.
 *
 * Раньше «вложено» включало накопленную зарплату управляющего и потому непрерывно росло:
 * замеры с устройства давали прирост около 90 $/с — это зарплата 2 200 $/день, размазанная
 * по 24 секундам игровых суток. Формально верно, но «вложено» означает разовые затраты,
 * и бегущее число выглядело ошибкой.
 *
 * Теперь «вложено» — только покупки, «заработано» — чистая прибыль, а показываются обе
 * величины снимком на игровой день.
 */
class EnterprisePaybackShownTest {

    private val trade = Industries.all[0]

    private fun stateWith(e: Enterprise, gameHours: Double = 8.0) =
        withEnterprises(GameState(bizH = 12, gameHours = gameHours), 0, e)

    private fun shown(st: GameState) = GameMath.payback(st, trade, st.enterprises[0][0])

    /** Один тик игрового цикла: 100 мс. Накопители растут, показываемое — по правилам дня. */
    private fun tick(st: GameState): GameState {
        val dtGameH = GameTime.gameHours(0.1)
        val next = st.copy(
            enterprises = GameMath.accrueEnterpriseStats(st, dtGameH / 24.0),
            gameHours = st.gameHours + dtGameH
        )
        return GameMath.profitShownOnNewDay(next)
    }

    // ===================== та же арифметика, что раньше =====================

    /**
     * Старая формула: `(вложено + зарплата − выручка) / чистый доход`.
     * Новая: `(вложено − прибыль) / чистый доход`, где прибыль = выручка − зарплата.
     * Числитель тот же, поэтому срок не должен разойтись ни на день.
     */
    private fun oldFormulaDays(st: GameState, e: Enterprise): Double =
        ((e.invested + e.salaryPaid) - e.earned) / GameMath.enterpriseNetPerDay(st, trade, e)

    @Test
    fun `срок окупаемости совпадает со старой формулой`() {
        val cases = listOf(
            Enterprise(level = 0, invested = 1_000.0, earned = 400.0),
            Enterprise(level = 1, invested = 12_000.0, earned = 3_400.0, salaryPaid = 900.0),
            Enterprise(
                level = 3, managerOrdinal = Manager.MANAGER.ordinal,
                invested = 250_000.0, earned = 90_000.0, salaryPaid = 41_500.0
            ),
            Enterprise(level = 2, invested = 40_000.0, earned = 0.0, salaryPaid = 0.0)
        )
        cases.forEach { e ->
            val st = GameMath.withProfitShown(stateWith(e))
            val p = shown(st)
            assertNotNull("подготовка теста: срок должен считаться", p.daysLeft)
            assertEquals("предприятие ${e.level}", oldFormulaDays(st, e), p.daysLeft!!, EPS)
        }
    }

    @Test
    fun `остаток до окупаемости тот же, что давала прежняя пара чисел`() {
        val e = Enterprise(
            level = 2, managerOrdinal = Manager.PRO.ordinal,
            invested = 80_000.0, earned = 31_000.0, salaryPaid = 7_500.0
        )
        val p = shown(GameMath.withProfitShown(stateWith(e)))
        assertEquals((e.invested + e.salaryPaid) - e.earned, p.invested - p.earned, EPS)
    }

    // ===================== вложено =====================

    @Test
    fun `вложено не меняется от течения времени`() {
        var st = GameMath.withProfitShown(
            stateWith(Enterprise(level = 1, managerOrdinal = Manager.TOP.ordinal, invested = 500_000.0))
        )
        val invested0 = shown(st).invested

        // двое с лишним игровых суток: зарплата за это время набегает изрядная
        repeat(500) {
            st = tick(st)
            assertEquals("вложено обязано стоять на месте", invested0, shown(st).invested, EPS)
        }
        assertEquals(500_000.0, invested0, EPS)
        assertTrue("проверка не пустая: зарплата за это время выплачена",
            st.enterprises[0][0].salaryPaid > 1_000.0)
    }

    @Test
    fun `вложено меняется от покупки улучшения`() {
        val e = Enterprise(level = 0, invested = 1_000.0)
        val before = shown(GameMath.withProfitShown(stateWith(e))).invested
        // так игра записывает покупку улучшения: цена прибавляется к вложенному
        val upgraded = e.copy(level = 1, invested = e.invested + 4_200.0)
        val after = shown(GameMath.withProfitShown(stateWith(upgraded))).invested

        assertEquals(1_000.0, before, EPS)
        assertEquals(5_200.0, after, EPS)
    }

    // ===================== заработано =====================

    @Test
    fun `заработано за игровой день равно выручке минус зарплате за тот же день`() {
        val e = Enterprise(level = 2, managerOrdinal = Manager.MANAGER.ordinal)
        val start = GameMath.withProfitShown(stateWith(e, gameHours = 0.0))
        val gross = GameMath.enterpriseGrossPerDay(start, trade, start.enterprises[0][0])
        val salary = Manager.MANAGER.salaryPerDay
        assertEquals("подготовка теста: снимок начинается с нуля", 0.0, shown(start).earned, EPS)

        // ровно игровые сутки накопления, а следом смена дня
        val afterDay = GameMath.profitShownOnNewDay(
            start.copy(
                enterprises = GameMath.accrueEnterpriseStats(start, 1.0),
                gameHours = 24.0
            )
        )
        assertEquals(gross - salary, shown(afterDay).earned, EPS)
        assertEquals(2, afterDay.statsShownDay)

        // и на вторые сутки — за две
        val afterTwo = GameMath.profitShownOnNewDay(
            afterDay.copy(
                enterprises = GameMath.accrueEnterpriseStats(afterDay, 1.0),
                gameHours = 48.0
            )
        )
        assertEquals((gross - salary) * 2.0, shown(afterTwo).earned, EPS)
    }

    @Test
    fun `заработано уходит в минус, если управляющий дороже выручки`() {
        val e = Enterprise(level = 0, managerOrdinal = Manager.TOP.ordinal, invested = 1_000.0)
        val st = GameMath.withProfitShown(
            stateWith(e, gameHours = 0.0)
                .let { it.copy(enterprises = GameMath.accrueEnterpriseStats(it, 1.0), gameHours = 24.0) }
        )
        val p = shown(st)
        assertTrue("топ-менеджер съедает больше, чем приносит лоток", p.earned < 0.0)
        assertTrue(p.stalled)
    }

    // ===================== неподвижность внутри суток =====================

    @Test
    fun `внутри одного игрового дня показанные числа не меняются`() {
        // ступень взята такая, чтобы управляющий окупался: иначе срока нет и сравнивать нечего
        var st = GameMath.withProfitShown(
            stateWith(
                Enterprise(level = 6, managerOrdinal = Manager.PRO.ordinal, invested = 400_000_000.0),
                gameHours = 1.0
            )
        )
        val p0 = shown(st)
        assertNotNull("подготовка теста: срок должен считаться", p0.daysLeft)

        // 200 тиков — 20 игровых часов, всё ещё первые сутки
        repeat(200) {
            st = tick(st)
            val p = shown(st)
            assertEquals("вложено", p0.invested, p.invested, EPS)
            assertEquals("заработано", p0.earned, p.earned, EPS)
            assertEquals("срок", p0.daysLeft!!, p.daysLeft!!, EPS)
        }
        assertEquals("день не сменился", 1, GameMath.gameDay(st.gameHours))
        // проверка не пустая: накопители за эти часы выросли, и без снимка числа бы поехали
        assertNotEquals("иначе тест ничего не ловит",
            p0.earned, st.enterprises[0][0].earned - st.enterprises[0][0].salaryPaid, 1e-6)
    }

    @Test
    fun `на границе суток показанные числа догоняют накопители`() {
        var st = GameMath.withProfitShown(
            stateWith(
                Enterprise(level = 6, managerOrdinal = Manager.PRO.ordinal, invested = 400_000_000.0),
                gameHours = 23.0
            )
        )
        val earned0 = shown(st).earned

        // тикаем ровно до смены суток
        repeat(40) { if (st.statsShownDay == 1) st = tick(st) }
        assertEquals("сутки должны были смениться", 2, st.statsShownDay)

        val e = st.enterprises[0][0]
        assertEquals("в момент снимка показано ровно то, что накоплено",
            e.earned - e.salaryPaid, shown(st).earned, EPS)
        assertTrue("прибыль за сутки прибавилась", shown(st).earned > earned0)
    }

    @Test
    fun `срок окупаемости внутри суток стоит, а на границе укорачивается`() {
        var st = GameMath.withProfitShown(
            stateWith(Enterprise(level = 2, invested = 60_000.0), gameHours = 20.0)
        )
        val days0 = shown(st).daysLeft!!

        repeat(30) { st = tick(st) }   // 3 игровых часа, тот же день
        assertEquals(days0, shown(st).daysLeft!!, EPS)

        repeat(20) { st = tick(st) }   // ещё 2 часа — сутки сменились
        assertEquals(2, st.statsShownDay)
        assertTrue("остаток уменьшился на заработанное за сутки", shown(st).daysLeft!! < days0)
    }
}
