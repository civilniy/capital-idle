package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle

/**
 * Имущество хранится множествами купленных предметов; ownedHome/ownedCar/ownedTech —
 * производные «лучший купленный». Тесты проверяют, что миграция со старого формата
 * (один индекс уровня) даёт ровно прежние числа.
 */
class LifestyleOwnershipTest {

    // ===================== миграция =====================

    @Test
    fun `старый индекс уровня превращается в лестницу от нуля`() {
        assertEquals(setOf(0), Lifestyle.ladderSet(0))
        assertEquals(setOf(0, 1), Lifestyle.ladderSet(1))
        assertEquals(setOf(0, 1, 2, 3, 4), Lifestyle.ladderSet(4))
        // битый индекс не роняет загрузку: остаётся стартовый предмет
        assertEquals(setOf(0), Lifestyle.ladderSet(-3))
    }

    @Test
    fun `новая игра начинается со стартовым предметом каждой категории`() {
        val s = GameState()
        assertEquals(setOf(0), s.ownedHomes)
        assertEquals(setOf(0), s.ownedCars)
        assertEquals(setOf(0), s.ownedTechs)
        // стартовое имущество бесплатно и без содержания
        assertEquals(0.0, Lifestyle.dailyUpkeep(s), EPS)
        assertEquals(0.0, Lifestyle.ownedCost(s), EPS)
        assertEquals(0, Lifestyle.socialStatus(s))
    }

    @Test
    fun `ownedHome, ownedCar и ownedTech — максимум из множества`() {
        val s = GameState(
            ownedHomes = Lifestyle.ladderSet(3),
            ownedCars = setOf(0, 5),
            ownedTechs = setOf(0)
        )
        assertEquals(3, s.ownedHome)
        assertEquals(5, s.ownedCar)
        assertEquals(0, s.ownedTech)

        // пустое множество (теоретически битый сейв) читается как стартовый предмет
        val broken = GameState(ownedHomes = emptySet())
        assertEquals(0, broken.ownedHome)
    }

    // ===================== числа не поехали =====================

    @Test
    fun `содержание мигрированного сейва совпадает со старым расчётом`() {
        // старое ownedHome = 4 значило «куплены ступени 0..4»
        val migrated = GameState(ownedHomes = Lifestyle.ladderSet(4))
        val oldWay = Lifestyle.home.items.take(5).sumOf { it.upkeep }
        assertEquals(oldWay, Lifestyle.dailyUpkeep(migrated), EPS)
        // 0 + 120 + 700 + 8 000 + 120 000
        assertEquals(128_820.0, Lifestyle.dailyUpkeep(migrated), EPS)

        // все три категории сразу
        val full = GameState(
            ownedHomes = Lifestyle.ladderSet(2),
            ownedCars = Lifestyle.ladderSet(3),
            ownedTechs = Lifestyle.ladderSet(3)
        )
        val oldFull = Lifestyle.home.items.take(3).sumOf { it.upkeep } +
            Lifestyle.car.items.take(4).sumOf { it.upkeep } +
            Lifestyle.tech.items.take(4).sumOf { it.upkeep }
        assertEquals(oldFull, Lifestyle.dailyUpkeep(full), EPS)
        assertEquals(5_720.0, Lifestyle.dailyUpkeep(full), EPS)
    }

    @Test
    fun `статус и бонусы мигрированного сейва совпадают со старым расчётом`() {
        val s = GameState(
            ownedHomes = Lifestyle.ladderSet(3),   // Москва-Сити: статус 38
            ownedCars = Lifestyle.ladderSet(3),    // BMW M5: статус 25, +1 час
            ownedTechs = Lifestyle.ladderSet(2)    // Rolex: статус 18, учёба +8%
        )
        assertEquals(38 + 25 + 18, Lifestyle.socialStatus(s))
        assertEquals(0.07, Lifestyle.homeSleepBonus(s), EPS)
        assertEquals(1, Lifestyle.carExtraHours(s))
        assertEquals(1.08, Lifestyle.techStudyMult(s), EPS)
        assertEquals(17, s.dayBudget)
    }

    @Test
    fun `капитал считает полную цену всех купленных предметов`() {
        val migrated = GameState(money = 1_000.0, ownedHomes = Lifestyle.ladderSet(2))
        val oldWay = Lifestyle.home.items.take(3).sumOf { it.cost }
        assertEquals(oldWay, Lifestyle.ownedCost(migrated), EPS)
        // 0 + 8 000 + 90 000
        assertEquals(98_000.0, Lifestyle.ownedCost(migrated), EPS)
        assertEquals(1_000.0 + 98_000.0, GameMath.netWorth(migrated), EPS)
    }

    // ===================== новое поведение: набор с пропусками =====================

    @Test
    fun `содержание платится только за реально купленные предметы`() {
        // жильё куплено «через ступень»: общежитие и Москва-Сити, без промежуточных
        val sparse = GameState(ownedHomes = setOf(0, 3))
        assertEquals(8_000.0, Lifestyle.dailyUpkeep(sparse), EPS)
        assertEquals(1_200_000.0, Lifestyle.ownedCost(sparse), EPS)
        // бонусы и статус по-прежнему от лучшего предмета
        assertEquals(3, sparse.ownedHome)
        assertEquals(38, Lifestyle.socialStatus(sparse))
        assertEquals(0.07, Lifestyle.homeSleepBonus(sparse), EPS)
    }

    @Test
    fun `индекс вне каталога игнорируется в суммах`() {
        val s = GameState(ownedHomes = setOf(0, 99))
        assertEquals(0.0, Lifestyle.dailyUpkeep(s), EPS)
        assertEquals(0.0, Lifestyle.ownedCost(s), EPS)
        // socialStatus берёт лучший индекс; вне каталога — откат на стартовый предмет
        assertEquals(0, Lifestyle.socialStatus(s))
    }

    @Test
    fun `чистка режет индексы вне каталога и не оставляет пустое множество`() {
        val home = Lifestyle.home
        // мусор из повреждённого сейва выбрасывается, годное остаётся
        assertEquals(setOf(0, 2), Lifestyle.sanitizeOwned(home, setOf(0, 2, 99)))
        assertEquals(setOf(0, 2), Lifestyle.sanitizeOwned(home, setOf(0, 2, -1)))
        // полностью мусорное множество откатывается к стартовому предмету
        assertEquals(setOf(0), Lifestyle.sanitizeOwned(home, setOf(99, 100)))
        assertEquals(setOf(0), Lifestyle.sanitizeOwned(home, emptySet()))
        // корректное множество не трогается
        assertEquals(Lifestyle.ladderSet(3), Lifestyle.sanitizeOwned(home, Lifestyle.ladderSet(3)))

        // после чистки ownedHome всегда указывает на существующий предмет —
        // экраны, индексирующие items[ownedHome] напрямую, не падают
        val cleaned = GameState(ownedHomes = Lifestyle.sanitizeOwned(home, setOf(0, 99)))
        assertEquals(0, cleaned.ownedHome)
        assertEquals(home.items.lastIndex,
            GameState(ownedHomes = Lifestyle.sanitizeOwned(home, setOf(0, home.items.lastIndex))).ownedHome)
    }

    @Test
    fun `ownedSet и ownedIndex отдают множество и лучший предмет по id категории`() {
        val s = GameState(
            ownedHomes = setOf(0, 2),
            ownedCars = setOf(0, 1),
            ownedTechs = setOf(0, 4)
        )
        assertEquals(setOf(0, 2), Lifestyle.ownedSet(s, "home"))
        assertEquals(setOf(0, 1), Lifestyle.ownedSet(s, "car"))
        assertEquals(setOf(0, 4), Lifestyle.ownedSet(s, "tech"))

        assertEquals(2, Lifestyle.ownedIndex(s, "home"))
        assertEquals(1, Lifestyle.ownedIndex(s, "car"))
        assertEquals(4, Lifestyle.ownedIndex(s, "tech"))
    }
}
