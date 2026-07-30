package ru.capital.idle

import org.junit.Assert.assertEquals
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameTime
import ru.capital.idle.core.game.Sleep
import org.junit.Test

/**
 * Игровые часы, сон и распорядок дня.
 * Тесты фиксируют текущее поведение как есть.
 */
class GameTimeTest {

    @Test
    fun `одна реальная секунда равна одному игровому часу`() {
        assertEquals(24.0, GameTime.DAY_REAL_SEC, GameTestFixtures.EPS)
        assertEquals(1.0, GameTime.gameHours(1.0), GameTestFixtures.EPS)
        assertEquals(24.0, GameTime.gameHours(24.0), GameTestFixtures.EPS)
        // 0.1 сек тика игрового цикла = 0.1 игрового часа
        assertEquals(0.1, GameTime.gameHours(0.1), 1e-12)
    }

    @Test
    fun `эффективность сна по таблице и с зажимом в 3-9 часов`() {
        assertEquals(0.45, Sleep.eff(3), GameTestFixtures.EPS)
        assertEquals(0.60, Sleep.eff(4), GameTestFixtures.EPS)
        assertEquals(0.75, Sleep.eff(5), GameTestFixtures.EPS)
        assertEquals(0.87, Sleep.eff(6), GameTestFixtures.EPS)
        assertEquals(0.95, Sleep.eff(7), GameTestFixtures.EPS)
        assertEquals(1.0, Sleep.eff(8), GameTestFixtures.EPS)
        // пересып бонуса не даёт, недосып ниже 3ч не штрафует сильнее
        assertEquals(1.0, Sleep.eff(9), GameTestFixtures.EPS)
        assertEquals(1.0, Sleep.eff(20), GameTestFixtures.EPS)
        assertEquals(0.45, Sleep.eff(0), GameTestFixtures.EPS)
    }

    @Test
    fun `бодрость складывается с бонусом жилья и не превышает 100 процентов`() {
        val s = GameState()
        assertEquals(1.0, GameMath.awakeEff(s), GameTestFixtures.EPS)

        // недосып 5ч (0.75) + хрущёвка (+0.02)
        assertEquals(0.77, GameMath.awakeEff(s.copy(sleepH = 5, ownedHome = 1)), GameTestFixtures.EPS)

        // нормальный сон + особняк: сумма 1.10 зажимается до 1.0
        assertEquals(1.0, GameMath.awakeEff(s.copy(sleepH = 8, ownedHome = 4)), GameTestFixtures.EPS)
    }

    @Test
    fun `бюджет дня зависит от сна и транспорта`() {
        val s = GameState()
        // 24 - 8 сна, пешком часов не добавляет
        assertEquals(16, s.dayBudget)
        assertEquals(6, s.studyHCalc)   // 16 - 10 работы - 0 бизнеса

        // BMW M5 (индекс 3) добавляет час
        val withCar = s.copy(ownedCar = 3)
        assertEquals(17, withCar.dayBudget)
        assertEquals(7, withCar.studyHCalc)

        // часы нельзя увести в минус
        assertEquals(0, s.copy(workH = 12, bizH = 12).studyHCalc)
    }

    @Test
    fun `учебные часы в день множатся наставником, престижем и техникой`() {
        val s = GameState()
        assertEquals(6.0, GameMath.studyHoursPerDay(s), GameTestFixtures.EPS)

        // наставник ×1.5
        assertEquals(9.0, GameMath.studyHoursPerDay(s.copy(netOwned = setOf("mentor"))), GameTestFixtures.EPS)
        // престиж «быстрая учёба» ×(1 + 0.25·lvl)
        assertEquals(9.0, GameMath.studyHoursPerDay(s.copy(pStudy = 2)), GameTestFixtures.EPS)
        // Rolex (индекс 2) ускоряет учёбу на 8%
        assertEquals(6.48, GameMath.studyHoursPerDay(s.copy(ownedTech = 2)), GameTestFixtures.EPS)
        // недосып бьёт по учёбе через бодрость: 19ч бюджета − 13ч работы = 6 учебных часов × 0.75
        val sleepy = s.copy(sleepH = 5, workH = 13)
        assertEquals(6, sleepy.studyHCalc)
        assertEquals(4.5, GameMath.studyHoursPerDay(sleepy), GameTestFixtures.EPS)
    }
}
