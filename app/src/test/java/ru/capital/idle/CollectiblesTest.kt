package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.Collectible
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.core.game.Rarity

/**
 * Коллекция: цена как чистая функция игрового дня, покупка/продажа, прибыль.
 * Числа фиксируют текущий каталог как есть.
 */
class CollectiblesTest {

    private val litho = Collectibles.byId("litho")!!      // 50 000, рост 0.0025/день
    private val temple = Collectibles.byId("temple")!!    // 50 млрд, рост 0.0002/день

    /** Состояние на нужном игровом дне. */
    private fun onDay(day: Int, money: Double = 0.0) =
        GameState(money = money, gameHours = day * 24.0)

    // ===================== каталог =====================

    @Test
    fun `в каталоге двенадцать предметов с уникальными id и разбросом цен`() {
        assertEquals(12, Collectibles.all.size)
        assertEquals(12, Collectibles.all.map { it.id }.toSet().size)

        val prices = Collectibles.all.map { it.basePrice }
        assertEquals(50_000.0, prices.min(), EPS)
        assertEquals(50_000_000_000.0, prices.max(), EPS)
        // цены идут по возрастанию — каталог читается сверху вниз как лестница богатства
        prices.zipWithNext().forEach { (a, b) -> assertTrue("цены должны расти: $a -> $b", b > a) }

        // все три редкости представлены
        assertEquals(Rarity.entries.toSet(), Collectibles.all.map { it.rarity }.toSet())
        // скорости роста разные и положительные
        Collectibles.all.forEach { assertTrue("${it.id}: рост должен быть > 0", it.growthPerDay > 0.0) }
        assertTrue(Collectibles.all.map { it.growthPerDay }.toSet().size >= 8)
        // статус положительный и растёт вместе с ценой
        Collectibles.all.map { it.status }.zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
    }

    @Test
    fun `цены самых дорогих предметов форматируются компактно`() {
        // на карточке коллекции цена и прибыль должны оставаться в одну строку,
        // поэтому фиксируем длину строк на потолке роста самого дорогого предмета
        val top = Collectibles.all.maxByOrNull { it.basePrice }!!
        val capped = Collectibles.priceAt(top, Collectibles.capReachedOnDay(top))
        assertEquals(400_000_000_000.0, capped, 1.0)

        Currency.entries.forEach { c ->
            val s = GameMath.formatMoney(capped, c)
            assertTrue("$c: строка «$s» длиннее 10 знаков (${s.length})", s.length <= 10)
        }
        assertEquals("$ 400B", GameMath.formatMoney(capped, Currency.USD))
        assertEquals("₽ 40,0T", GameMath.formatMoney(capped, Currency.RUB))

        // и максимально возможная прибыль тоже короткая
        val maxProfit = capped - top.basePrice
        Currency.entries.forEach { c ->
            val s = "+" + GameMath.formatMoney(maxProfit, c)
            assertTrue("$c: строка прибыли «$s» длиннее 11 знаков (${s.length})", s.length <= 11)
        }
    }

    @Test
    fun `предмет находится по id, мусор даёт null`() {
        assertEquals("Литография с автографом", Collectibles.byId("litho")?.title)
        assertNull(Collectibles.byId("нет-такого"))
    }

    // ===================== цена =====================

    @Test
    fun `цена растёт линейно от номера игрового дня`() {
        // день 0 — базовая цена
        assertEquals(50_000.0, Collectibles.priceAt(litho, 0), EPS)
        // 1 + 0.0025 * 100 = 1.25
        assertEquals(62_500.0, Collectibles.priceAt(litho, 100), EPS)
        // 1 + 0.0025 * 1000 = 3.5
        assertEquals(175_000.0, Collectibles.priceAt(litho, 1_000), EPS)
        // медленный предмет за те же 1000 дней прибавляет всего 20%
        assertEquals(60_000_000_000.0, Collectibles.priceAt(temple, 1_000), EPS)
    }

    @Test
    fun `рост упирается в потолок и дальше не идёт`() {
        assertEquals(8.0, Collectibles.MAX_GROWTH_MULT, EPS)
        // литография: потолок при 0.0025 * day = 7, то есть на дне 2800
        assertEquals(2_800, Collectibles.capReachedOnDay(litho))
        assertEquals(400_000.0, Collectibles.priceAt(litho, 2_800), EPS)
        assertEquals(400_000.0, Collectibles.priceAt(litho, 10_000), EPS)
        assertEquals(400_000.0, Collectibles.priceAt(litho, 1_000_000), EPS)
        // ни один предмет никогда не превысит восьмикратной базовой цены
        Collectibles.all.forEach { c ->
            assertEquals(c.basePrice * 8.0, Collectibles.priceAt(c, Int.MAX_VALUE / 2), c.basePrice * 1e-9)
        }
    }

    @Test
    fun `цена не зависит ни от чего, кроме дня — воспроизводима`() {
        val a = onDay(500, money = 1_000.0)
        val b = onDay(500, money = 9e12).copy(reputation = 88.0, bullion = 42L, phaseIndex = 2)
        assertEquals(Collectibles.priceIn(litho, a), Collectibles.priceIn(litho, b), EPS)
        // повторный вызов даёт то же самое
        assertEquals(Collectibles.priceIn(litho, a), Collectibles.priceIn(litho, a), EPS)
        // отрицательное время не уводит цену ниже базовой
        assertEquals(50_000.0, Collectibles.priceAt(litho, -100), EPS)
    }

    @Test
    fun `номер игрового дня считается от игровых часов`() {
        assertEquals(0, Collectibles.dayOf(GameState()))              // старт в 08:00 первого дня
        assertEquals(0, Collectibles.dayOf(onDay(0).copy(gameHours = 23.9)))
        assertEquals(1, Collectibles.dayOf(onDay(0).copy(gameHours = 24.0)))
        assertEquals(7, Collectibles.dayOf(onDay(7)))
    }

    // ===================== покупка и продажа =====================

    @Test
    fun `покупка списывает текущую цену и запоминает её`() {
        val before = onDay(100, money = 100_000.0)
        assertTrue(Collectibles.canBuy(before, "litho"))

        val after = Collectibles.buy(before, "litho")!!
        assertEquals(100_000.0 - 62_500.0, after.money, EPS)
        assertTrue(Collectibles.owns(after, "litho"))
        assertEquals(62_500.0, Collectibles.paidFor(after, "litho"), EPS)
        assertEquals(1, Collectibles.ownedCount(after))
    }

    @Test
    fun `нельзя купить дважды, без денег и несуществующее`() {
        val rich = onDay(0, money = 100_000.0)
        val owned = Collectibles.buy(rich, "litho")!!

        assertFalse(Collectibles.canBuy(owned, "litho"))
        assertNull(Collectibles.buy(owned, "litho"))

        val poor = onDay(0, money = 49_999.0)
        assertFalse(Collectibles.canBuy(poor, "litho"))
        assertNull(Collectibles.buy(poor, "litho"))
        // ровно впритык — можно
        assertNotNull(Collectibles.buy(onDay(0, money = 50_000.0), "litho"))

        assertNull(Collectibles.buy(rich, "нет-такого"))
        assertFalse(Collectibles.canBuy(rich, "нет-такого"))
    }

    @Test
    fun `продажа отдаёт текущую цену и убирает предмет`() {
        val bought = Collectibles.buy(onDay(0, money = 50_000.0), "litho")!!
        assertEquals(0.0, bought.money, EPS)

        // продаём через 400 дней: 50 000 * (1 + 0.0025*400) = 100 000
        val later = bought.copy(gameHours = 400 * 24.0)
        val sold = Collectibles.sell(later, "litho")!!
        assertEquals(100_000.0, sold.money, EPS)
        assertFalse(Collectibles.owns(sold, "litho"))
        assertEquals(0, Collectibles.ownedCount(sold))

        // нечего продавать
        assertNull(Collectibles.sell(sold, "litho"))
        assertNull(Collectibles.sell(sold, "нет-такого"))
    }

    @Test
    fun `прибыль — это текущая цена минус уплаченная`() {
        val bought = Collectibles.buy(onDay(0, money = 50_000.0), "litho")!!
        // сразу после покупки прибыли нет
        assertEquals(0.0, Collectibles.profit(bought, litho), EPS)

        val later = bought.copy(gameHours = 400 * 24.0)
        assertEquals(50_000.0, Collectibles.profit(later, litho), EPS)
        assertEquals(100_000.0, Collectibles.portfolioValue(later), EPS)
        assertEquals(50_000.0, Collectibles.totalPaid(later), EPS)
        assertEquals(50_000.0, Collectibles.totalProfit(later), EPS)

        // по не купленному предмету прибыли нет
        assertEquals(0.0, Collectibles.profit(later, temple), EPS)
    }

    @Test
    fun `купленный дорого предмет может уйти в минус`() {
        // куплено на дне 2800, когда цена уже упёрлась в потолок 400 000
        val expensive = onDay(2_800, money = 500_000.0)
        val bought = Collectibles.buy(expensive, "litho")!!
        assertEquals(400_000.0, Collectibles.paidFor(bought, "litho"), EPS)
        // цена дальше не растёт, прибыль остаётся нулевой, а не отрицательной
        assertEquals(0.0, Collectibles.profit(bought.copy(gameHours = 9_000 * 24.0), litho), EPS)

        // а вот если бы предмет купили дороже цены каталога — виден убыток
        val overpaid = bought.copy(collectibles = mapOf("litho" to 900_000.0))
        assertEquals(-500_000.0, Collectibles.profit(overpaid, litho), EPS)
        assertEquals(-500_000.0, Collectibles.totalProfit(overpaid), EPS)
    }

    @Test
    fun `владеть можно всеми предметами сразу и в любом порядке`() {
        // покупаем с конца каталога, минуя дешёвые
        var st = onDay(0, money = 1e12)
        st = Collectibles.buy(st, "crown")!!
        st = Collectibles.buy(st, "litho")!!
        st = Collectibles.buy(st, "meteor")!!

        assertEquals(3, Collectibles.ownedCount(st))
        assertTrue(Collectibles.owns(st, "crown"))
        assertTrue(Collectibles.owns(st, "litho"))
        assertFalse(Collectibles.owns(st, "temple"))
        // порядок покупки на цену не влияет
        assertEquals(4_470_000_000.0, Collectibles.paidFor(st, "crown"), EPS)
    }

    // ===================== связи с игрой =====================

    @Test
    fun `коллекция даёт статус и не требует содержания`() {
        val plain = onDay(0, money = 1e12)
        val statusBefore = Lifestyle.socialStatus(plain)
        val upkeepBefore = Lifestyle.dailyUpkeep(plain)

        val withArt = Collectibles.buy(Collectibles.buy(plain, "litho")!!, "crown")!!
        assertEquals(5 + 125, Collectibles.statusPoints(withArt))
        assertEquals(statusBefore + 5 + 125, Lifestyle.socialStatus(withArt))
        // содержания у коллекции нет
        assertEquals(upkeepBefore, Lifestyle.dailyUpkeep(withArt), EPS)
    }

    @Test
    fun `коллекция не влияет на доход, но входит в капитал по текущей цене`() {
        val plain = onDay(0, money = 1e12).copy(jobId = "courier")
        val incomeBefore = GameMath.incomePerDay(plain)
        val netBefore = GameMath.netIncomePerDay(plain)

        val bought = Collectibles.buy(plain, "litho")!!
        assertEquals(incomeBefore, GameMath.incomePerDay(bought), EPS)
        assertEquals(netBefore, GameMath.netIncomePerDay(bought), EPS)

        // капитал не проседает от покупки: деньги перешли в предмет
        assertEquals(GameMath.netWorth(plain), GameMath.netWorth(bought), EPS)
        // и растёт вместе с ценой предмета
        val later = bought.copy(gameHours = 400 * 24.0)
        assertEquals(GameMath.netWorth(bought) + 50_000.0, GameMath.netWorth(later), EPS)
    }

    @Test
    fun `неизвестные предметы в состоянии игнорируются, а не роняют расчёты`() {
        // сейв из будущей версии с предметом, которого больше нет в каталоге
        val st = onDay(100).copy(collectibles = mapOf("litho" to 1_000.0, "исчез" to 777.0))
        assertEquals(1, Collectibles.ownedCount(st))
        assertEquals(62_500.0, Collectibles.portfolioValue(st), EPS)
        assertEquals(1_000.0, Collectibles.totalPaid(st), EPS)
        assertEquals(5, Collectibles.statusPoints(st))
    }
}
