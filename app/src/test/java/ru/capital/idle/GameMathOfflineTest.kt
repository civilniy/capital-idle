package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.GameConfig
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Prestige

/**
 * Оффлайн-«сейф»: две фазы (полная и половинная), дальше начисление обнуляется.
 * Во всех тестах доход игрока = 480 $ за игровой день => 20 $ за реальную секунду.
 */
class GameMathOfflineTest {

    /** Недвижимость 48 000 под 1% в день = 480 $/день, сутки идут 24 реальные секунды. */
    private val player = GameState(investValues = listOf(0.0, 0.0, 48_000.0))
    private val perSec = 480.0 / 24.0   // 20 $ реальной секунды

    @Test
    fun `нулевое и отрицательное время не приносят ничего`() {
        assertEquals(0.0, GameMath.offlineEarnings(player, 0.0).first, EPS)
        assertEquals(0.0, GameMath.offlineEarnings(player, 0.0).second, EPS)
        // отрицательный интервал (сдвиг системных часов) не уводит баланс в минус
        assertEquals(0.0, GameMath.offlineEarnings(player, -10_000.0).first, EPS)
        assertEquals(0.0, GameMath.offlineEarnings(player, -10_000.0).second, EPS)
    }

    @Test
    fun `первая фаза идёт по 25 процентов дохода`() {
        assertEquals(1800.0, GameConfig.OFFLINE_FULL_SEC, EPS)
        assertEquals(0.25, GameConfig.OFFLINE_EFF_FULL, EPS)

        // 10 минут отсутствия
        val (earned, missed) = GameMath.offlineEarnings(player, 600.0)
        assertEquals(600.0 * perSec * 0.25, earned, EPS)
        assertEquals(3_000.0, earned, EPS)
        assertEquals(0.0, missed, EPS)
    }

    @Test
    fun `вторая фаза идёт по 10 процентов, обе фазы вместе — час`() {
        // ровно 1 час = 30 мин полной + 30 мин половинной
        val (earned, missed) = GameMath.offlineEarnings(player, 3_600.0)
        assertEquals(1800.0 * perSec * 0.25 + 1800.0 * perSec * 0.10, earned, EPS)
        assertEquals(12_600.0, earned, EPS)
        assertEquals(0.0, missed, EPS)
    }

    @Test
    fun `сверх часа начисления не растут, но копится «упущено»`() {
        val (earned, missed) = GameMath.offlineEarnings(player, 7_200.0)   // 2 часа
        // заработок замер на потолке часа
        assertEquals(12_600.0, earned, EPS)
        // лишний час оценивается по нижней эффективности
        assertEquals(3_600.0 * perSec * 0.10, missed, EPS)
        assertEquals(7_200.0, missed, EPS)
    }

    @Test
    fun `«упущено» ограничено четырьмя длительностями сейфа`() {
        // сутки офлайна: overSec зажимается в (1800+1800)*4 = 14 400 сек
        val (earned, missed) = GameMath.offlineEarnings(player, 86_400.0)
        assertEquals(12_600.0, earned, EPS)
        assertEquals(14_400.0 * perSec * 0.10, missed, EPS)
        assertEquals(28_800.0, missed, EPS)
    }

    @Test
    fun `престиж-сейф удлиняет обе фазы на 15 минут за уровень`() {
        assertEquals(900.0, GameConfig.OFFLINE_SAFE_BONUS_SEC, EPS)

        val safe1 = player.copy(pSafe = 1)   // фазы 45 + 45 минут
        // 45 минут по полной ставке
        assertEquals(2_700.0 * perSec * 0.25, GameMath.offlineEarnings(safe1, 2_700.0).first, EPS)
        // полтора часа = обе удлинённые фазы целиком
        assertEquals(2_700.0 * perSec * 0.25 + 2_700.0 * perSec * 0.10,
            GameMath.offlineEarnings(safe1, 5_400.0).first, EPS)
        // без апгрейда за те же полтора часа — заметно меньше
        assertEquals(12_600.0, GameMath.offlineEarnings(player, 5_400.0).first, EPS)

        // тексты про длительность сейфа согласованы с формулой
        assertEquals(60, Prestige.safeTotalMinutes(player))
        assertEquals(30, Prestige.safeFullMinutes(player))
        assertEquals(90, Prestige.safeTotalMinutes(safe1))
        assertEquals(45, Prestige.safeFullMinutes(safe1))
    }

    @Test
    fun `слитки за перерождение = 25 корней из заработанного к 100 миллионам`() {
        assertEquals(0L, Prestige.gainFrom(0.0))
        assertEquals(0L, Prestige.gainFrom(-1_000.0))
        assertEquals(25L, Prestige.gainFrom(1e8))
        assertEquals(50L, Prestige.gainFrom(4e8))
        assertEquals(250L, Prestige.gainFrom(1e10))
        // порог первого слитка: нужно ~160 000 $ за заход
        assertEquals(0L, Prestige.gainFrom(1.5e5))
        assertEquals(1L, Prestige.gainFrom(1.7e5))
    }
}
