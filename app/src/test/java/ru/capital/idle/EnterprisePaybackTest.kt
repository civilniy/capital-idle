package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.GameTestFixtures.withEnterprises
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Manager

/**
 * Окупаемость предприятия: что показывает карточка.
 *
 * Числа здесь не балансовые — считается по накопителям самого предприятия и его текущему
 * чистому доходу, поэтому тесты задают накопители прямо и проверяют арифметику и границы.
 */
class EnterprisePaybackTest {

    private val trade = Industries.all[0]

    /**
     * Состояние с одним предприятием в торговле; отрасль одна, чтобы не мешало насыщение.
     *
     * Часы на бизнес заданы с запасом: предприятие, которым игрок занимается лично, без
     * выделенных часов не приносит ничего (`mgmtEff` = 0), и проверять на нём было бы нечего.
     */
    private fun stateWith(e: Enterprise) =
        GameMath.withDayShown(withEnterprises(GameState(bizH = 12), 0, e))

    /** Карточка читает снятую прибыль, поэтому предприятие берётся из состояния со снимком. */
    private fun payback(e: Enterprise) =
        stateWith(e).let { GameMath.payback(it, trade, it.enterprises[0][0]) }

    @Test
    fun `вложено — только покупки, зарплата в него не входит`() {
        val e = Enterprise(level = 0, invested = 1_000.0, salaryPaid = 250.0, earned = 0.0)
        val p = payback(e)
        assertEquals("разовые затраты и ничего кроме", 1_000.0, p.invested, EPS)
        assertEquals("зарплата ушла в минус прибыли", -250.0, p.earned, EPS)
        // остаток до окупаемости тот же, что давала прежняя формула
        assertEquals(1_250.0, p.invested - p.earned, EPS)
    }

    @Test
    fun `обычный случай — срок считается по остатку и чистому доходу`() {
        // лично: чистый доход = валовому, зарплаты нет
        val e = Enterprise(level = 0, invested = 1_000.0, earned = 400.0)
        val p = payback(e)
        val net = GameMath.enterpriseNetPerDay(stateWith(e), trade, e)

        assertFalse(p.paidOff)
        assertFalse(p.stalled)
        assertNotNull(p.daysLeft)
        assertEquals(600.0 / net, p.daysLeft!!, EPS)
    }

    @Test
    fun `точка окупаемости — заработано ровно столько же, сколько вложено`() {
        val e = Enterprise(level = 0, invested = 1_000.0, earned = 1_000.0)
        val p = payback(e)
        assertTrue("ровно на нуле уже считается окупившимся", p.paidOff)
        assertNull("считать больше нечего", p.daysLeft)
        assertEquals(100.0, p.returnedPct, EPS)
    }

    @Test
    fun `уже окупилось — срока нет, есть процент возврата`() {
        val e = Enterprise(level = 0, invested = 1_000.0, earned = 2_500.0)
        val p = payback(e)
        assertTrue(p.paidOff)
        assertNull(p.daysLeft)
        assertFalse(p.stalled)
        assertEquals(250.0, p.returnedPct, EPS)
    }

    /**
     * Топ-менеджер стоит 2200 в день, а лоток первой ступени приносит куда меньше.
     * Делить на такой доход нельзя: срок вышел бы отрицательным.
     */
    @Test
    fun `отрицательный чистый доход — срока нет, помечено как не окупается`() {
        val e = Enterprise(
            level = 0, managerOrdinal = Manager.TOP.ordinal,
            invested = 1_000.0, earned = 10.0
        )
        val st = stateWith(e)
        assertTrue(
            "проверка имеет смысл, только пока зарплата больше выручки",
            GameMath.enterpriseNetPerDay(st, trade, e) < 0.0
        )

        val p = GameMath.payback(st, trade, e)
        assertTrue(p.stalled)
        assertNull(p.daysLeft)
        assertFalse(p.paidOff)
    }

    @Test
    fun `нулевой чистый доход — деления на ноль нет`() {
        // зарплата ровно съедает выручку — доход нулевой
        val e = Enterprise(level = 0, managerOrdinal = Manager.STUDENT.ordinal, invested = 100.0)
        val st = stateWith(e)
        val gross = GameMath.enterpriseGrossPerDay(st, trade, e)
        val zeroNet = Manager.entries[Manager.STUDENT.ordinal]
        assertTrue("подготовка теста: выручка меньше зарплаты", gross < zeroNet.salaryPerDay)

        val p = GameMath.payback(st, trade, e)
        assertTrue(p.stalled)
        assertNull(p.daysLeft)
    }

    @Test
    fun `нулевая выручка — ничего не заработано, процент возврата нулевой`() {
        val e = Enterprise(level = 0, invested = 5_000.0, earned = 0.0)
        val p = payback(e)
        assertEquals(0.0, p.returnedPct, EPS)
        assertFalse(p.paidOff)
        assertNotNull("лоток что-то приносит, срок посчитать можно", p.daysLeft)
    }

    /**
     * Предприятие из старого сохранения: вложений не записано. Считать окупаемость не из
     * чего, и «окупилось» тут было бы неправдой — не окупилось, а сравнивать не с чем.
     */
    @Test
    fun `вложений не записано — окупаемость неизвестна, а не достигнута`() {
        val p = payback(Enterprise(level = 0))
        assertTrue(p.unknown)
        assertFalse("нельзя объявлять окупившимся то, чего не считали", p.paidOff)
        assertNull(p.daysLeft)
        assertEquals("процент от нуля не считаем", 0.0, p.returnedPct, EPS)
    }

    @Test
    fun `вложений не записано, но выручка уже пошла — всё равно неизвестна`() {
        val p = payback(Enterprise(level = 0, earned = 10_000.0))
        assertTrue(p.unknown)
        assertFalse(p.paidOff)
        assertEquals(0.0, p.returnedPct, EPS)
    }

    @Test
    fun `после первого улучшения окупаемость снова считается`() {
        // у предприятия из старого сейва вложения появляются с первой же покупки
        val p = payback(Enterprise(level = 1, invested = 1_300.0, earned = 200.0))
        assertFalse(p.unknown)
        assertNotNull(p.daysLeft)
    }

    // ===================== накопление =====================

    @Test
    fun `выручка и зарплата растут по мере игры`() {
        val e = Enterprise(level = 0, managerOrdinal = Manager.MANAGER.ordinal)
        var st = stateWith(e)
        val gross = GameMath.enterpriseGrossPerDay(st, trade, st.enterprises[0][0])
        val salary = Manager.MANAGER.salaryPerDay

        st = st.copy(enterprises = GameMath.accrueEnterpriseStats(st, 2.0))
        val after2 = st.enterprises[0][0]
        assertEquals(gross * 2.0, after2.earned, EPS)
        assertEquals(salary * 2.0, after2.salaryPaid, EPS)

        st = st.copy(enterprises = GameMath.accrueEnterpriseStats(st, 3.0))
        val after5 = st.enterprises[0][0]
        assertEquals("накопление продолжается, а не начинается заново", gross * 5.0, after5.earned, EPS)
        assertEquals(salary * 5.0, after5.salaryPaid, EPS)
    }

    @Test
    fun `накопление мелкими шагами даёт то же, что одним большим`() {
        val e = Enterprise(level = 2, managerOrdinal = Manager.PRO.ordinal)
        var many = stateWith(e)
        repeat(100) { many = many.copy(enterprises = GameMath.accrueEnterpriseStats(many, 0.01)) }
        val one = stateWith(e).let { it.copy(enterprises = GameMath.accrueEnterpriseStats(it, 1.0)) }

        assertEquals(one.enterprises[0][0].earned, many.enterprises[0][0].earned, 1e-6)
        assertEquals(one.enterprises[0][0].salaryPaid, many.enterprises[0][0].salaryPaid, 1e-6)
    }

    @Test
    fun `нулевой и отрицательный шаг времени ничего не начисляют`() {
        val st = stateWith(Enterprise(level = 0, managerOrdinal = Manager.PRO.ordinal))
        assertEquals(st.enterprises, GameMath.accrueEnterpriseStats(st, 0.0))
        assertEquals(st.enterprises, GameMath.accrueEnterpriseStats(st, -1.0))
    }

    @Test
    fun `у предприятия без управляющего зарплата не копится`() {
        var st = stateWith(Enterprise(level = 0))
        st = st.copy(enterprises = GameMath.accrueEnterpriseStats(st, 10.0))
        assertEquals(0.0, st.enterprises[0][0].salaryPaid, EPS)
        assertTrue(st.enterprises[0][0].earned > 0.0)
    }

    @Test
    fun `накопление идёт каждому предприятию своё`() {
        val cheap = Enterprise(level = 0)
        val rich = Enterprise(level = 3)
        var st = withEnterprises(GameState(bizH = 12), 0, cheap, rich)
        st = st.copy(enterprises = GameMath.accrueEnterpriseStats(st, 1.0))
        assertTrue(
            "у старшей ступени выручка больше",
            st.enterprises[0][1].earned > st.enterprises[0][0].earned
        )
    }
}
