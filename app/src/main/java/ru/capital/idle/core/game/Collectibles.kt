package ru.capital.idle.core.game

import kotlin.math.floor

/** Редкость коллекционного предмета. Пока влияет только на подпись в интерфейсе. */
enum class Rarity(val title: String) {
    COMMON("обычный"),
    RARE("редкий"),
    UNIQUE("уникальный")
}

/**
 * Коллекционный предмет: искусство и редкие объекты.
 *
 * Отличие от имущества (`Lifestyle`): здесь нет лестницы — предметы покупаются
 * в любом порядке и владеть можно всеми сразу. Содержания не требуют и на доход
 * не влияют: это вложение, которое дорожает само.
 *
 * @param basePrice цена в первый игровой день
 * @param growthPerDay прибавка к цене за игровой день, в долях базовой цены
 * @param status очки социального статуса, пока предмет в коллекции
 */
data class Collectible(
    val id: String,
    val emoji: String,
    val title: String,
    val info: String,
    val basePrice: Double,
    val growthPerDay: Double,
    val status: Int,
    val rarity: Rarity
)

/**
 * Набор коллекционных предметов: несколько вещей, собранных по смыслу.
 *
 * Наборы ничего не хранят в состоянии игры — полнота вычисляется из тех же
 * `state.collectibles`, что и владение отдельными предметами. Один предмет
 * может входить в несколько наборов: так поздняя покупка закрывает сразу
 * две цели, и собирать становится интереснее, чем скупать по порядку цен.
 *
 * @param itemIds идентификаторы предметов, входящих в набор
 * @param bonus очки социального статуса за полностью собранный набор
 */
data class CollectibleSet(
    val id: String,
    val emoji: String,
    val title: String,
    val info: String,
    val itemIds: List<String>,
    val bonus: Int
)

object Collectibles {

    /**
     * Потолок роста: цена не поднимается выше базовой более чем в MAX_GROWTH_MULT раз.
     * Без него линейный рост за долгую игру уводил бы цены в бессмыслицу.
     */
    const val MAX_GROWTH_MULT = 8.0

    /**
     * Двенадцать предметов от «первой покупки коллекционера» до музейного уровня.
     * Скорость роста намеренно не связана с ценой: дешёвая литография дорожает
     * быстрее храма, поэтому ранние вложения не обесцениваются на фоне поздних.
     */
    val all = listOf(
        Collectible("litho", "🖼", "Литография с автографом",
            "Тираж 50 экземпляров, подпись автора на полях",
            50_000.0, 0.00250, 5, Rarity.COMMON),
        Collectible("amphora", "🏺", "Античная амфора",
            "Средиземноморье, VI век до н.э., реставрирована",
            180_000.0, 0.00060, 9, Rarity.COMMON),
        Collectible("violin", "🎻", "Скрипка старого мастера",
            "Кремонская школа, звучит до сих пор",
            630_000.0, 0.00180, 14, Rarity.RARE),
        Collectible("folio", "📜", "Первое издание с пометками",
            "Поля исписаны рукой самого автора",
            2_240_000.0, 0.00035, 20, Rarity.RARE),
        Collectible("mask", "🗿", "Ритуальная маска",
            "Вывезена задолго до всех запретов",
            7_950_000.0, 0.00120, 28, Rarity.COMMON),
        Collectible("diamond", "💎", "Жёлтый бриллиант",
            "128 карат, огранка «подушка», собственное имя",
            28_200_000.0, 0.00200, 38, Rarity.RARE),
        Collectible("canvas", "🎨", "Полотно импрессиониста",
            "Тот самый сад, который узнают без подписи",
            100_000_000.0, 0.00090, 52, Rarity.RARE),
        Collectible("rex", "🦖", "Скелет тираннозавра",
            "Сохранность 73%, собран целиком",
            355_000_000.0, 0.00230, 70, Rarity.UNIQUE),
        Collectible("meteor", "🌑", "Лунный метеорит",
            "Килограмм породы, которой нет на Земле",
            1_260_000_000.0, 0.00045, 95, Rarity.UNIQUE),
        Collectible("crown", "👑", "Историческая корона",
            "Была на голове, о которой пишут в учебниках",
            4_470_000_000.0, 0.00150, 125, Rarity.UNIQUE),
        Collectible("lost", "🖌", "Утраченный шедевр",
            "Сто лет считался погибшим при пожаре",
            15_800_000_000.0, 0.00070, 160, Rarity.UNIQUE),
        Collectible("temple", "🏛", "Античный храм",
            "Перевезён по камню и собран заново в вашем парке",
            50_000_000_000.0, 0.00020, 200, Rarity.UNIQUE),
    )

    fun byId(id: String): Collectible? = all.firstOrNull { it.id == id }

    // ===================== наборы =====================

    /**
     * Четыре набора из тех же двенадцати предметов.
     *
     * Каждый предмет входит хотя бы в один набор, а «Полотно импрессиониста»
     * и «Утраченный шедевр» — сразу в два: это и живопись, и вещи с именем автора.
     *
     * Бонусы подобраны так, чтобы их сумма (600) была заметно меньше суммы
     * статуса самих предметов (816): наборы — весомая надбавка сверху, а не
     * вторая, более выгодная валюта. Внутри бонус растёт со сложностью сбора —
     * дороже вход и больше предметов, больше очков.
     */
    val sets = listOf(
        CollectibleSet(
            "nature", "🌑", "Дар природы",
            "Три вещи, которых не касалась рука человека",
            listOf("diamond", "rex", "meteor"), 110
        ),
        CollectibleSet(
            "gallery", "🖼", "Галерея",
            "Стена, на которую водят гостей",
            listOf("litho", "canvas", "lost"), 140
        ),
        CollectibleSet(
            "ancient", "🏺", "Древний мир",
            "От черепка до целого храма",
            listOf("amphora", "mask", "temple"), 170
        ),
        CollectibleSet(
            "names", "👑", "Имена в истории",
            "У каждой вещи есть хозяин, о котором пишут в учебниках",
            listOf("violin", "folio", "canvas", "crown", "lost"), 180
        ),
    )

    fun setById(id: String): CollectibleSet? = sets.firstOrNull { it.id == id }

    /** Предметы набора в порядке каталога; неизвестные идентификаторы отбрасываются. */
    fun itemsOf(set: CollectibleSet): List<Collectible> = set.itemIds.mapNotNull { byId(it) }

    /** Наборы, в которые входит предмет. Пустой список — предмет сам по себе. */
    fun setsWith(itemId: String): List<CollectibleSet> = sets.filter { itemId in it.itemIds }

    /** Сколько предметов набора уже в коллекции. */
    fun ownedInSet(state: GameState, set: CollectibleSet): Int =
        itemsOf(set).count { owns(state, it.id) }

    /** Сколько предметов в наборе всего. */
    fun sizeOf(set: CollectibleSet): Int = itemsOf(set).size

    /** Набор собран, когда в коллекции есть все его предметы. Пустой набор собранным не считается. */
    fun isComplete(state: GameState, set: CollectibleSet): Boolean {
        val items = itemsOf(set)
        return items.isNotEmpty() && items.all { owns(state, it.id) }
    }

    /** Очки за конкретный набор: полный бонус или ничего, промежуточных начислений нет. */
    fun setBonus(state: GameState, set: CollectibleSet): Int =
        if (isComplete(state, set)) set.bonus else 0

    /** Сумма бонусов за все собранные наборы. */
    fun setBonusPoints(state: GameState): Int = sets.sumOf { setBonus(state, it) }

    /** Сколько наборов собрано полностью. */
    fun completedSets(state: GameState): Int = sets.count { isComplete(state, it) }

    // ===================== цена =====================

    /** Номер игрового дня: в первые сутки 0. */
    fun dayOf(state: GameState): Int =
        floor(state.gameHours / GameTime.HOURS_PER_DAY).toInt().coerceAtLeast(0)

    /** Множитель цены на указанный игровой день — линейный рост с потолком. */
    fun priceMult(c: Collectible, day: Int): Double =
        (1.0 + c.growthPerDay * day.coerceAtLeast(0)).coerceAtMost(MAX_GROWTH_MULT)

    /** Цена предмета на указанный игровой день. Чистая функция, без случайности. */
    fun priceAt(c: Collectible, day: Int): Double = c.basePrice * priceMult(c, day)

    /** Цена предмета в текущий момент игры. */
    fun priceIn(c: Collectible, state: GameState): Double = priceAt(c, dayOf(state))

    /** На какой игровой день предмет упрётся в потолок роста. */
    fun capReachedOnDay(c: Collectible): Int =
        if (c.growthPerDay <= 0.0) Int.MAX_VALUE
        else kotlin.math.ceil((MAX_GROWTH_MULT - 1.0) / c.growthPerDay).toInt()

    // ===================== владение =====================

    fun owns(state: GameState, id: String): Boolean = id in state.collectibles

    /** Сколько заплачено за предмет (0, если не куплен). */
    fun paidFor(state: GameState, id: String): Double = state.collectibles[id] ?: 0.0

    /** Прибыль по предмету: текущая цена минус уплаченная. Отрицательная — убыток. */
    fun profit(state: GameState, c: Collectible): Double =
        if (owns(state, c.id)) priceIn(c, state) - paidFor(state, c.id) else 0.0

    /** Стоимость всей коллекции по текущим ценам. */
    fun portfolioValue(state: GameState): Double =
        state.collectibles.keys.sumOf { id -> byId(id)?.let { priceIn(it, state) } ?: 0.0 }

    /** Сколько всего вложено в коллекцию. */
    fun totalPaid(state: GameState): Double =
        state.collectibles.entries.sumOf { (id, paid) -> if (byId(id) != null) paid else 0.0 }

    fun totalProfit(state: GameState): Double = portfolioValue(state) - totalPaid(state)

    /** Очки статуса от самих предметов, без бонусов за наборы. */
    fun itemStatusPoints(state: GameState): Int =
        state.collectibles.keys.sumOf { id -> byId(id)?.status ?: 0 }

    /** Очки социального статуса от собранной коллекции: предметы плюс бонусы за полные наборы. */
    fun statusPoints(state: GameState): Int = itemStatusPoints(state) + setBonusPoints(state)

    /** Сколько предметов собрано из каталога. */
    fun ownedCount(state: GameState): Int = state.collectibles.keys.count { byId(it) != null }

    // ===================== действия =====================

    /** Можно ли купить: предмета ещё нет и хватает денег на текущую цену. */
    fun canBuy(state: GameState, id: String): Boolean {
        val c = byId(id) ?: return false
        return !owns(state, id) && state.money >= priceIn(c, state)
    }

    /**
     * Купить по текущей цене. Уплаченная цена запоминается — по ней считается прибыль.
     * Возвращает null, если покупка невозможна (нет такого предмета, уже куплен, не хватает денег).
     */
    fun buy(state: GameState, id: String): GameState? {
        val c = byId(id) ?: return null
        if (owns(state, id)) return null
        val price = priceIn(c, state)
        if (state.money < price) return null
        return state.copy(
            money = state.money - price,
            collectibles = state.collectibles + (id to price)
        )
    }

    /**
     * Продать по текущей цене. Возвращает null, если предмета нет в коллекции.
     * Долг гасится обычным порядком на ближайшем тике, отдельной логики здесь нет.
     */
    fun sell(state: GameState, id: String): GameState? {
        val c = byId(id) ?: return null
        if (!owns(state, id)) return null
        return state.copy(
            money = state.money + priceIn(c, state),
            collectibles = state.collectibles - id
        )
    }
}
