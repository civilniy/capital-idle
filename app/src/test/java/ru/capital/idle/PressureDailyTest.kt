package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.BusinessConfig
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.GameTime
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Manager
import ru.capital.idle.core.game.Pressure
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState
import kotlin.math.floor

/**
 * Давление элит — величина игрового дня, а не расчёт на лету.
 *
 * Раньше `Pressure.value` звали из `bizGlobalMult` на каждом тике. Деньги растут непрерывно,
 * поэтому доход бизнесов монотонно сползал прямо на глазах — около 0,18% в секунду при
 * обороте в сотни миллионов в день. Здесь проверяется главное следствие: внутри игровых
 * суток доход стоит намертво, а на границе суток давление берётся заново.
 */
class PressureDailyTest {

    /** Поздняя игра: все отрасли развиты, капитал такой, что давление уже работает. */
    private fun rich(money: Double, gameHours: Double = 8.0) = GameMath.withPressure(
        GameState(
            money = money, gameHours = gameHours, bizH = 6, reputation = 41.4, pIncome = 9,
            enterprises = List(Industries.count) { i ->
                List(BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY) {
                    Enterprise(Industries.all[i].levels.lastIndex, Manager.TOP.ordinal)
                }
            }
        )
    )

    /**
     * Один тик игрового цикла: 100 мс реального времени. Деньги прирастают доходом,
     * часы — игровым временем, давление обновляется по тем же правилам, что в цикле.
     */
    private fun tick(s: GameState): GameState {
        val dtGameH = GameTime.gameHours(0.1)
        val grown = s.copy(
            money = s.money + GameMath.incomePerDay(s) * (dtGameH / 24.0),
            gameHours = s.gameHours + dtGameH
        )
        return GameMath.pressureOnNewDay(grown)
    }

    // ===================== сутки без дрожания =====================

    @Test
    fun `в пределах одного игрового дня доход бизнесов не меняется при росте денег`() {
        var s = rich(1_000_000_000_000.0, gameHours = 1.0)
        val income0 = GameMath.bizPerDay(s)
        val money0 = s.money

        // 200 тиков — это 20 игровых часов, всё ещё первые сутки
        repeat(200) {
            s = tick(s)
            assertEquals("доход обязан стоять на месте внутри суток",
                income0, GameMath.bizPerDay(s), EPS)
        }

        assertEquals("день не сменился", 1, GameMath.gameDay(s.gameHours))
        assertTrue("капитал за эти часы вырос", s.money > money0)
        // проверка не пустая: считай игра давление на лету, оно бы за эти часы сдвинулось
        assertNotEquals("иначе тест ничего не ловит",
            s.pressure, Pressure.value(s.money, 41.0), 1e-6)
    }

    @Test
    fun `на границе игрового дня давление пересчитывается`() {
        val start = rich(1_000_000_000.0, gameHours = 20.0)

        // капитал вырос в тысячу раз, но день тот же — давление не трогаем
        val sameDay = GameMath.pressureOnNewDay(start.copy(money = 1e12, gameHours = 23.9))
        assertEquals(start.pressure, sameDay.pressure, EPS)
        assertEquals(1, sameDay.pressureDay)

        // сутки сменились — считаем заново от текущих денег
        val nextDay = GameMath.pressureOnNewDay(sameDay.copy(gameHours = 24.0))
        assertEquals(2, nextDay.pressureDay)
        assertTrue("давление обязано вырасти вслед за капиталом", nextDay.pressure > start.pressure)
        assertEquals(Pressure.value(1e12, 41.0), nextDay.pressure, EPS)
    }

    @Test
    fun `смена дня доводит доход бизнесов до новой величины`() {
        var s = rich(1_000_000_000_000.0, gameHours = 23.0)
        val income0 = GameMath.bizPerDay(s)

        // 40 тиков = 4 игровых часа: сутки успевают смениться
        repeat(40) { s = tick(s) }

        assertEquals(2, s.pressureDay)
        assertTrue("после смены суток доход учитывает выросшее давление", GameMath.bizPerDay(s) < income0)
    }

    @Test
    fun `давление берётся из состояния, а не считается заново`() {
        // деньги обнулили, а давление осталось от своего дня — показываем именно его
        val s = rich(4e12).copy(money = 0.0)
        assertTrue(s.pressure > 0.0)
        assertEquals(s.pressure, GameMath.pressureShown(s), EPS)
    }

    @Test
    fun `до миллиарда давления нет`() {
        val s = rich(999_000_000.0)
        assertEquals(0.0, s.pressure, EPS)
        assertEquals(1, s.pressureDay)
    }

    @Test
    fun `отметка дня считается от игровых часов`() {
        assertEquals(1, GameMath.gameDay(0.0))
        assertEquals(1, GameMath.gameDay(23.99))
        assertEquals(2, GameMath.gameDay(24.0))
        assertEquals(43, GameMath.gameDay(24.0 * 42 + 5))
    }

    // ===================== оффлайн =====================

    /**
     * Пока игра закрыта, игровые часы стоят, а деньги приходят одной суммой. Смены дня
     * не будет, поэтому при возвращении давление пересчитывают прямым вызовом.
     */
    @Test
    fun `после оффлайна давление считается от новых денег`() {
        val before = rich(2_000_000_000.0, gameHours = 30.0)
        // вернулись из оффлайна: деньги выросли, день тот же
        val credited = before.copy(money = before.money + 6e12)

        assertEquals("сам по себе день не сменился — пересчёта не будет",
            before.pressure, GameMath.pressureOnNewDay(credited).pressure, EPS)

        val after = GameMath.withPressure(credited)
        assertEquals(Pressure.value(credited.money, 41.0), after.pressure, EPS)
        assertEquals(2, after.pressureDay)
        assertTrue(after.pressure > before.pressure)
    }

    // ===================== сохранения =====================

    /** Файл, записанный до того, как давление стало храниться: ключей в нём просто нет. */
    private fun jsonWithoutPressure(st: GameState): String {
        val o = org.json.JSONObject(SaveFile.toJson(st.toEntity()))
        o.remove("pressure")
        o.remove("pressureDay")
        return o.toString()
    }

    @Test
    fun `загрузка старого сохранения даёт корректное давление`() {
        val src = rich(3_000_000_000_000.0, gameHours = 24.0 * 40 + 3)
        val back = SaveFile.fromJson(jsonWithoutPressure(src))!!.toState()

        assertEquals("посчитано от денег и целой репутации",
            Pressure.value(src.money, floor(src.reputation)), back.pressure, EPS)
        assertEquals("отметка — текущий игровой день", 41, back.pressureDay)
        // и доход сразу правильный, а не завышенный
        assertEquals(GameMath.bizPerDay(src), GameMath.bizPerDay(back), EPS)
    }

    @Test
    fun `старое сохранение до миллиарда даёт нулевое давление с живой отметкой`() {
        val src = rich(500_000_000.0, gameHours = 24.0 * 3)
        val back = SaveFile.fromJson(jsonWithoutPressure(src))!!.toState()
        assertEquals(0.0, back.pressure, EPS)
        assertEquals("отметка обязана быть проставлена, иначе пересчёт пойдёт каждую загрузку",
            4, back.pressureDay)
    }

    @Test
    fun `сохранённое давление переживает круг записи-чтения`() {
        val src = rich(7_000_000_000_000.0, gameHours = 24.0 * 12)
        val back = SaveFile.fromJson(SaveFile.toJson(src.toEntity()))!!.toState()
        assertEquals(src.pressure, back.pressure, EPS)
        assertEquals(src.pressureDay, back.pressureDay)
    }

    /**
     * Значение дня загрузка не пересчитывает: игрок ушёл в середине суток, деньги с тех пор
     * не менялись, и давление должно остаться тем же, что было на экране.
     */
    @Test
    fun `живую отметку загрузка не трогает`() {
        val src = rich(1_000_000_000.0, gameHours = 24.0 * 5).copy(money = 9e13)
        val back = SaveFile.fromJson(SaveFile.toJson(src.toEntity()))!!.toState()
        assertEquals(src.pressure, back.pressure, EPS)
        assertNotEquals("именно старое значение, а не пересчёт от новых денег",
            Pressure.value(9e13, 41.0), back.pressure, EPS)
    }
}
