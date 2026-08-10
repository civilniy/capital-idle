package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.GameConfig
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.GameTime
import ru.capital.idle.core.game.Investments
import kotlin.math.pow

/**
 * Отдача тапа убывает с ростом дохода.
 *
 * Было: `incomePerDay / 48`, доля постоянная. Доход растёт экспоненциально, поэтому тап
 * навсегда оставался способом обогнать экономику: при доходе 7,5 B в день один тап давал
 * 156 886 466, и сорока восьми кликов за десять секунд хватало на целый игровой день —
 * а игровой день идёт 24 секунды.
 *
 * Стало: до порога отдача полная, дальше каждое удесятерение дохода режет её вдвое.
 * Награда при этом всё равно растёт вместе с доходом — иначе развитие выглядело бы
 * наказанием, — просто медленнее его.
 */
class TapEfficiencyTest {

    /** Состояние с заданным снятым доходом: именно из него считается награда. */
    private fun withIncome(income: Double) = GameState(tapIncome = income)

    private fun reward(income: Double) = GameMath.tapReward(withIncome(income))

    // ===================== главный тест: монотонность =====================

    /**
     * Награда строго растёт вместе с доходом на всём диапазоне — от доллара в день до
     * квадриллиона, 121 точка по логарифмической шкале.
     *
     * Ниже $48 в день формула даёт меньше доллара, и её накрывает обязательный минимум
     * в $1 — там награда стоит на месте по построению. Поэтому строгий рост проверяется
     * везде, где минимум уже не действует, а на всём диапазоне — неубывание.
     */
    @Test
    fun `награда за тап растёт вместе с доходом на всём диапазоне`() {
        val points = (0..120).map { 10.0.pow(it * 15.0 / 120.0) }   // 1 .. 1e15
        assertTrue("подготовка теста: точек должно быть не меньше сотни", points.size >= 100)

        var prev = reward(points.first())
        points.drop(1).forEach { income ->
            val cur = reward(income)
            assertTrue("награда упала при доходе $income: $prev -> $cur", cur >= prev)
            if (prev > 1.0) {
                assertTrue("награда не выросла при доходе $income: $prev -> $cur", cur > prev)
            }
            prev = cur
        }

        // и по краям диапазона она действительно выросла на порядки, а не еле-еле
        assertTrue(reward(1e15) > reward(1.0) * 1e6)
    }

    // ===================== форма шкалы =====================

    @Test
    fun `до порога награда равна доходу делить на 48`() {
        listOf(1.0, 48.0, 500.0, 4_200.0, GameConfig.TAP_FULL_INCOME_PER_DAY).forEach { income ->
            assertEquals("доход $income", (income / 48.0).coerceAtLeast(1.0), reward(income), EPS)
        }
        assertEquals(1.0, GameMath.tapEfficiency(GameConfig.TAP_FULL_INCOME_PER_DAY), EPS)
        assertEquals("ниже порога ничего не режется", 1.0, GameMath.tapEfficiency(1.0), EPS)
    }

    @Test
    fun `каждое удесятерение дохода режет отдачу вдвое`() {
        var income = GameConfig.TAP_FULL_INCOME_PER_DAY
        var expected = 1.0
        repeat(10) {
            assertEquals("доход $income", expected, GameMath.tapEfficiency(income), expected * 1e-9)
            income *= 10.0
            expected /= 2.0
        }
    }

    /** То же самое, но со стороны награды: она равна доле от дохода, и доля падает вдвое. */
    @Test
    fun `доля дохода, которую даёт тап, падает вдвое на каждое удесятерение`() {
        val a = 1e6
        val b = 1e7
        val shareA = reward(a) / a
        val shareB = reward(b) / b
        assertEquals(shareA / 2.0, shareB, shareA * 1e-9)
    }

    /**
     * Ориентиры из постановки задачи, допуск 5%.
     *
     * Последняя строка таблицы в задании — «доход 1 000 000 000 000 → 0,8%» — правилу
     * не соответствует: от порога в 10 000 до триллиона восемь удесятерений, то есть
     * 0,5^8 = 0,39%, а 0,8% это 0,5^7, на одно удесятерение меньше. Остальные четыре
     * строки правилу отвечают точно, поэтому здесь стоит значение по правилу.
     */
    @Test
    fun `ориентиры эффективности сходятся с таблицей`() {
        val expected = mapOf(
            10_000.0 to 1.0,
            100_000.0 to 0.5,
            10_000_000.0 to 0.125,
            1_000_000_000.0 to 0.03,        // 0,5^5 = 3,125% — в допуск 5% попадает
            1_000_000_000_000.0 to 0.5.pow(8)
        )
        expected.forEach { (income, want) ->
            val got = GameMath.tapEfficiency(income)
            assertEquals("доход $income: ожидалось ~$want, вышло $got", want, got, want * 0.05)
        }
    }

    @Test
    fun `шкала непрерывна в пороге`() {
        val t = GameConfig.TAP_FULL_INCOME_PER_DAY
        assertEquals(GameMath.tapEfficiency(t * 0.999999), GameMath.tapEfficiency(t * 1.000001), 1e-6)
        assertEquals(reward(t * 0.999999), reward(t * 1.000001), t * 1e-5)
    }

    @Test
    fun `нижнего предела у шкалы нет`() {
        // на каждое следующее удесятерение отдача продолжает падать, без «пола»
        val far = GameMath.tapEfficiency(1e30)
        assertTrue("отдача на запредельном доходе должна быть мизерной, а не упереться в предел",
            far < 1e-7 && far > 0.0)
        assertTrue(GameMath.tapEfficiency(1e31) < far)
    }

    // ===================== минимум =====================

    @Test
    fun `награда никогда не опускается ниже доллара`() {
        listOf(0.0, -100.0, 1.0, 10.0, 47.0, 48.0).forEach { income ->
            assertTrue("доход $income", reward(income) >= 1.0)
        }
        assertEquals(1.0, reward(0.0), EPS)
        assertEquals(1.0, reward(-1_000.0), EPS)
    }

    // ===================== замер с устройства =====================

    /**
     * Тот самый случай из замера: доход 7,5 B в день. Сорок восемь кликов давали целый
     * игровой день дохода — теперь заметно меньше.
     */
    @Test
    fun `на доходе 7,5 миллиарда сорок восемь кликов уже не дают дневного дохода`() {
        val income = 7.5e9
        val fortyEight = reward(income) * 48
        assertTrue("48 кликов дают ${fortyEight / income * 100}% дневного дохода",
            fortyEight < income * 0.05)
        // но клик всё равно ощутимый, а не символический
        assertTrue(reward(income) > 1_000_000.0)
    }

    // ===================== снимок дня =====================

    @Test
    fun `в пределах одного игрового дня награда не меняется`() {
        // доход, который живёт своей жизнью: вклады капитализируются, репутация дрейфует
        var st = GameMath.withDayShown(
            GameState(
                gameHours = 1.0, jobId = "director",
                eduDone = setOf("school", "sales", "mgmt", "acc", "uni"),
                investValues = List(Investments.COUNT) { 5_000_000.0 },
                capitalizeMask = (1 shl Investments.COUNT) - 1,
                reputation = 41.4
            )
        )
        val reward0 = GameMath.tapReward(st)
        assertTrue("подготовка теста: награда должна быть выше минимума", reward0 > 1.0)

        // 200 тиков — 20 игровых часов, всё ещё первые сутки. Репутация ползёт, вклады растут
        repeat(200) {
            val dtGameH = GameTime.gameHours(0.1)
            st = GameMath.dayShownOnNewDay(
                st.copy(
                    gameHours = st.gameHours + dtGameH,
                    reputation = st.reputation + 0.01,
                    investValues = st.investValues.map { it * 1.0005 }
                )
            )
            assertEquals("награда обязана стоять внутри суток", reward0, GameMath.tapReward(st), EPS)
        }
        assertEquals("день не сменился", 1, GameMath.gameDay(st.gameHours))
        // проверка не вхолостую: живой доход за эти часы ушёл вперёд
        assertTrue("иначе тест ничего не ловит", GameMath.incomePerDay(st) > st.tapIncome * 1.05)
    }

    @Test
    fun `на границе суток награда догоняет доход`() {
        val base = GameMath.withDayShown(
            GameState(gameHours = 20.0, investValues = listOf(0.0, 0.0, 48_000.0))
        )
        val grown = base.copy(investValues = listOf(0.0, 0.0, 48_000_000.0), gameHours = 24.0)
        val after = GameMath.dayShownOnNewDay(grown)

        assertEquals(2, after.statsShownDay)
        assertEquals(GameMath.incomePerDay(grown), after.tapIncome, EPS)
        assertTrue("награда выросла вслед за доходом", GameMath.tapReward(after) > GameMath.tapReward(base))
    }

    // ===================== множители тапа не тронуты =====================

    /**
     * «Переговорщик» и буст применяются поверх награды, в вызывающем коде, — и должны
     * остаться там же. Здесь фиксируется, что сама [GameMath.tapReward] их не знает.
     */
    @Test
    fun `престиж-апгрейд и буст в саму награду не входят`() {
        val st = withIncome(1e8)
        assertEquals(GameMath.tapReward(st), GameMath.tapReward(st.copy(pNegotiator = 3)), EPS)
        assertEquals(
            GameMath.tapReward(st),
            GameMath.tapReward(st.copy(boostEndsAtMillis = System.currentTimeMillis() + 3_600_000L)),
            EPS
        )
    }
}
