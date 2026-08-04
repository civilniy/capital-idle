package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.GameTestFixtures.withEnterprises
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Manager
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Накопители окупаемости через все слои: GameState -> GameEntity -> JSON -> обратно.
 *
 * Отдельная колонка `enterpriseStatsRaw` идёт параллельно `enterprisesRaw`, запись в запись.
 * Пустая колонка означает сохранение, сделанное до появления учёта.
 */
class EnterpriseStatsPersistenceTest {

    private val one = Enterprise(
        level = 2, managerOrdinal = Manager.PRO.ordinal, name = "Ларёк у дома",
        invested = 12_345.5, earned = 6_789.25, salaryPaid = 1_000.75
    )

    @Test
    fun `накопители переживают круг записи-чтения`() {
        val src = withEnterprises(GameState(), 0, one)
        val back = src.toEntity().toState()
        val e = back.enterprises[0][0]

        assertEquals(12_345.5, e.invested, EPS)
        assertEquals(6_789.25, e.earned, EPS)
        assertEquals(1_000.75, e.salaryPaid, EPS)
        assertEquals("учёт полный — пометки быть не должно", Enterprise.STATS_FROM_START, e.statsSinceDay)
        assertFalse(e.statsPartial)
        // остальные поля не задеты
        assertEquals(2, e.level)
        assertEquals(Manager.PRO.ordinal, e.managerOrdinal)
        assertEquals("Ларёк у дома", e.name)
    }

    @Test
    fun `накопители переживают запись в файл сохранения`() {
        val src = withEnterprises(GameState(), 0, one)
        val back = SaveFile.fromJson(SaveFile.toJson(src.toEntity()))!!.toState()
        val e = back.enterprises[0][0]
        assertEquals(12_345.5, e.invested, EPS)
        assertEquals(6_789.25, e.earned, EPS)
        assertEquals(1_000.75, e.salaryPaid, EPS)
    }

    @Test
    fun `накопленное за игру доезжает до файла и обратно`() {
        var st = withEnterprises(GameState(), 0, Enterprise(level = 1, managerOrdinal = Manager.MANAGER.ordinal))
        st = st.copy(enterprises = GameMath.accrueEnterpriseStats(st, 7.0))
        val expected = st.enterprises[0][0]
        assertTrue("подготовка теста: за 7 дней что-то накопилось", expected.earned > 0.0)

        val back = SaveFile.fromJson(SaveFile.toJson(st.toEntity()))!!.toState().enterprises[0][0]
        assertEquals(expected.earned, back.earned, 1e-6)
        assertEquals(expected.salaryPaid, back.salaryPaid, 1e-6)
    }

    @Test
    fun `накопители не путаются между предприятиями и отраслями`() {
        val a = Enterprise(level = 0, name = "А", invested = 1.0, earned = 2.0, salaryPaid = 3.0)
        val b = Enterprise(level = 1, name = "Б", invested = 10.0, earned = 20.0, salaryPaid = 30.0)
        val c = Enterprise(level = 2, name = "В", invested = 100.0, earned = 200.0, salaryPaid = 300.0)

        val lists = MutableList(ru.capital.idle.core.game.Industries.count) { emptyList<Enterprise>() }
        lists[0] = listOf(a, b)
        lists[3] = listOf(c)
        val back = GameState(enterprises = lists).toEntity().toState()

        assertEquals(2.0, back.enterprises[0][0].earned, EPS)
        assertEquals(20.0, back.enterprises[0][1].earned, EPS)
        assertEquals(200.0, back.enterprises[3][0].earned, EPS)
        assertEquals(300.0, back.enterprises[3][0].salaryPaid, EPS)
    }

    @Test
    fun `название с двоеточием не ломает разбор накопителей`() {
        // накопители лежат отдельной колонкой именно поэтому: название может содержать
        // любые разделители, и дописывать что-то после него в старую колонку нельзя
        val tricky = Enterprise(level = 0, name = "Всё:по|плану;да", invested = 5.0, earned = 6.0)
        val back = withEnterprises(GameState(), 0, tricky).toEntity().toState().enterprises[0][0]
        assertEquals("Всё:по|плану;да", back.name)
        assertEquals(5.0, back.invested, EPS)
        assertEquals(6.0, back.earned, EPS)
    }

    // ===================== старые сохранения =====================

    /** Файл, записанный до появления учёта: ключа `enterpriseStatsRaw` в нём просто нет. */
    private fun jsonWithoutStats(st: GameState): String {
        val json = SaveFile.toJson(st.toEntity())
        val o = org.json.JSONObject(json)
        o.remove("enterpriseStatsRaw")
        return o.toString()
    }

    @Test
    fun `старое сохранение читается, накопление начинается с нуля`() {
        val src = withEnterprises(
            GameState(gameHours = 24.0 * 127),   // идёт 128-й игровой день
            0,
            Enterprise(level = 3, managerOrdinal = Manager.TOP.ordinal, name = "Универмаг")
        )
        val back = SaveFile.fromJson(jsonWithoutStats(src))!!.toState()
        val e = back.enterprises[0][0]

        assertEquals("предприятие на месте", "Универмаг", e.name)
        assertEquals(3, e.level)
        assertEquals("истории нет — накопители пустые", 0.0, e.earned, EPS)
        assertEquals(0.0, e.salaryPaid, EPS)
        assertEquals("вложения не выдумываем", 0.0, e.invested, EPS)
        assertTrue("учёт помечен как неполный", e.statsPartial)
        assertEquals("учёт идёт с текущего дня", 128, e.statsSinceDay)
    }

    @Test
    fun `новое сохранение без предприятий не принимают за старое`() {
        // колонка пустая по смыслу, но разделители в ней есть — этим и отличается
        val e = GameState().toEntity()
        assertNotEquals("", e.enterpriseStatsRaw)
        assertTrue(e.enterpriseStatsRaw.all { it == ';' })
    }

    @Test
    fun `после сохранения помеченного предприятия пометка остаётся`() {
        val src = withEnterprises(GameState(gameHours = 24.0 * 9), 0, Enterprise(level = 0))
        val migrated = SaveFile.fromJson(jsonWithoutStats(src))!!.toState()
        assertEquals(10, migrated.enterprises[0][0].statsSinceDay)

        // второй круг: теперь колонка есть, день учёта не должен «обновиться» на сегодняшний
        val again = SaveFile.fromJson(
            SaveFile.toJson(migrated.copy(gameHours = 24.0 * 40).toEntity())
        )!!.toState()
        assertEquals(10, again.enterprises[0][0].statsSinceDay)
        assertTrue(again.enterprises[0][0].statsPartial)
    }

    @Test
    fun `предприятие, открытое после обновления, пометки не получает`() {
        val fresh = Enterprise(level = 0, invested = 200.0)
        val back = withEnterprises(GameState(gameHours = 24.0 * 50), 0, fresh)
            .toEntity().toState().enterprises[0][0]
        assertFalse(back.statsPartial)
        assertEquals(200.0, back.invested, EPS)
    }
}
