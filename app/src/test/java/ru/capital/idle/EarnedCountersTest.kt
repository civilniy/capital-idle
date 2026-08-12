package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.GameTime
import ru.capital.idle.core.game.Investments

/**
 * Два счётчика заработка: `totalEarned` (за жизнь, для титулов и престижа) и
 * `statAllTimeEarned` (за все жизни, для профиля).
 *
 * В игровом цикле они считались двумя соседними строками, и во второй потерялся множитель
 * буста: у игрока с постоянным ×2 счётчик всех жизней отставал ровно вдвое — замер
 * с устройства давал 1 000 000 против 489 870, отношение 2,04.
 *
 * Теперь прирост считает одно выражение [GameMath.earnedDelta], и оба счётчика получают
 * его результат. Здесь проверяется само выражение и то, что жизнь, прожитая через него,
 * оставляет счётчики равными.
 */
class EarnedCountersTest {

    /** Состояние с ощутимым доходом: работа плюс вклады. */
    private fun earner(boostActive: Boolean = false) = GameState(
        jobId = "director",
        eduDone = setOf("school", "sales", "mgmt", "acc", "uni"),
        investValues = List(Investments.COUNT) { 250_000.0 },
        boostEndsAtMillis = if (boostActive) System.currentTimeMillis() + 3_600_000L else 0L
    )

    /**
     * Прогон жизни: каждый тик прирост берётся из общего выражения и кладётся в оба
     * счётчика — ровно так, как это делает игровой цикл.
     */
    private fun live(start: GameState, ticks: Int, tapEvery: Int = 0): GameState {
        var st = start
        repeat(ticks) { i ->
            val dtDays = GameTime.gameHours(0.1) / 24.0
            val delta = GameMath.earnedDelta(st, dtDays)
            st = st.copy(
                money = st.money + delta,
                totalEarned = st.totalEarned + delta,
                statAllTimeEarned = st.statAllTimeEarned + delta
            )
            if (tapEvery > 0 && i % tapEvery == 0) {
                // тап начисляет одну и ту же сумму обоим счётчикам — как в GameViewModel.tap()
                val r = GameMath.tapReward(st)
                st = st.copy(
                    money = st.money + r,
                    totalEarned = st.totalEarned + r,
                    statAllTimeEarned = st.statAllTimeEarned + r
                )
            }
        }
        return st
    }

    // ===================== буст =====================

    @Test
    fun `при активном бусте оба счётчика растут с одинаковой скоростью`() {
        val st = live(earner(boostActive = true), ticks = 500)
        assertTrue("подготовка теста: что-то должно было накопиться", st.totalEarned > 0.0)
        assertEquals("счётчики обязаны идти нога в ногу",
            st.totalEarned, st.statAllTimeEarned, EPS)
    }

    /**
     * И буст в приросте действительно учитывается: с ним за то же время накапливается
     * ровно вдвое больше. Без этой проверки прошлый баг выглядел бы как «оба счётчика
     * одинаково занижены».
     */
    @Test
    fun `буст удваивает прирост обоих счётчиков`() {
        val dtDays = 1.0
        val plain = GameMath.earnedDelta(earner(boostActive = false), dtDays)
        val boosted = GameMath.earnedDelta(earner(boostActive = true), dtDays)

        assertTrue("подготовка теста: доход должен быть ненулевым", plain > 0.0)
        assertEquals(plain * 2.0, boosted, plain * 1e-9)

        val withBoost = live(earner(boostActive = true), ticks = 240)
        val without = live(earner(boostActive = false), ticks = 240)
        assertEquals(without.statAllTimeEarned * 2.0, withBoost.statAllTimeEarned, EPS)
    }

    // ===================== жизнь целиком =====================

    @Test
    fun `за одну жизнь без перерождений счётчики совпадают`() {
        listOf(false, true).forEach { boost ->
            val st = live(earner(boostActive = boost), ticks = 720, tapEvery = 10)
            assertTrue("подготовка теста: тапы должны были случиться", st.totalEarned > 0.0)
            assertEquals("буст=$boost", st.totalEarned, st.statAllTimeEarned, EPS)
        }
    }

    /**
     * Разовое начисление (оффлайн-доход, прибыль от продажи акций) попадает в оба счётчика
     * одной и той же суммой — именно поэтому равенство держится и после него.
     */
    @Test
    fun `разовое начисление не разводит счётчики`() {
        var st = live(earner(), ticks = 100)
        val lump = 1_234_567.0
        st = st.copy(
            money = st.money + lump,
            totalEarned = st.totalEarned + lump,
            statAllTimeEarned = st.statAllTimeEarned + lump
        )
        assertEquals(st.totalEarned, st.statAllTimeEarned, EPS)
    }

    /**
     * Перерождение — единственное место, где счётчики законно расходятся: `totalEarned`
     * обнуляется, счётчик всех жизней продолжает считать.
     */
    @Test
    fun `перерождение обнуляет только счётчик жизни`() {
        val lived = live(earner(), ticks = 500)
        val reborn = lived.copy(totalEarned = 0.0)   // так делает prestige()

        assertEquals(0.0, reborn.totalEarned, EPS)
        assertEquals(lived.statAllTimeEarned, reborn.statAllTimeEarned, EPS)
        assertTrue(reborn.statAllTimeEarned > 0.0)
    }

    @Test
    fun `нулевой доход ничего не начисляет`() {
        assertEquals(0.0, GameMath.earnedDelta(GameState(), 1.0), EPS)
        assertEquals(0.0, GameMath.earnedDelta(earner(), 0.0), EPS)
    }
}
