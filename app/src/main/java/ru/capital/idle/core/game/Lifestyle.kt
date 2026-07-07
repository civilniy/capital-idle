package ru.capital.idle.core.game

/** Имущество, титулы, хроника. Фиксация личного успеха. */
object Lifestyle {

    /** Предмет: цена покупки, содержание/день, очки соц-статуса. */
    data class Item(
        val emoji: String, val title: String,
        val cost: Double, val upkeep: Double, val status: Int
    )
    data class Category(val id: String, val title: String, val items: List<Item>)

    // === ЖИЛЬЁ (реальные локации) ===
    val home = Category("home", "ЖИЛЬЁ", listOf(
        Item("\uD83C\uDFDA", "Комната в общежитии", 0.0, 0.0, 0),
        Item("\uD83C\uDFD8", "Хрущёвка на окраине", 8_000.0, 120.0, 5),
        Item("\uD83C\uDFE2", "Квартира в новостройке", 90_000.0, 700.0, 14),
        Item("\uD83C\uDFD9", "Квартира в Москва-Сити", 1_200_000.0, 8_000.0, 38),
        Item("\uD83C\uDFE1", "Особняк на Рублёвке", 25_000_000.0, 120_000.0, 85),
        Item("\uD83C\uDF03", "Пентхаус в Дубае", 400_000_000.0, 1_500_000.0, 150),
        Item("\uD83C\uDFF0", "Вилла на Лазурном берегу", 5_000_000_000.0, 18_000_000.0, 230),
        Item("\uD83C\uDFDD", "Остров в Карибском море", 80_000_000_000.0, 200_000_000.0, 450),
    ))

    // === ТРАНСПОРТ (реальные модели) ===
    val car = Category("car", "ТРАНСПОРТ", listOf(
        Item("\uD83D\uDEB6", "Пешком", 0.0, 0.0, 0),
        Item("\uD83D\uDE97", "Lada Vesta", 18_000.0, 200.0, 5),
        Item("\uD83D\uDE97", "Volkswagen Passat", 45_000.0, 500.0, 12),
        Item("\uD83D\uDE99", "BMW M5", 140_000.0, 2_200.0, 25),
        Item("\uD83D\uDE99", "Mercedes-Maybach", 350_000.0, 6_000.0, 42),
        Item("\uD83C\uDFCE", "Porsche 911 Turbo", 900_000.0, 18_000.0, 60),
        Item("\uD83C\uDFCE", "Lamborghini Aventador", 4_000_000.0, 55_000.0, 90),
        Item("\uD83C\uDFCE", "Bugatti Chiron", 30_000_000.0, 300_000.0, 140),
        Item("\uD83D\uDEE5", "Яхта Azimut", 2_000_000_000.0, 9_000_000.0, 220),
        Item("\u2708\uFE0F", "Gulfstream G700", 40_000_000_000.0, 120_000_000.0, 420),
    ))

    // === АКСЕССУАРЫ И СТИЛЬ ===
    val tech = Category("tech", "АКСЕССУАРЫ И СТИЛЬ", listOf(
        Item("\u231A", "Обычные часы", 0.0, 0.0, 0),
        Item("\u231A", "Tissot", 8_000.0, 0.0, 8),
        Item("\u231A", "Rolex Submariner", 25_000.0, 0.0, 18),
        Item("\uD83D\uDC54", "Брендовый гардероб", 120_000.0, 2_000.0, 30),
        Item("\u231A", "Patek Philippe", 1_500_000.0, 0.0, 55),
        Item("\uD83D\uDC8E", "Коллекция украшений", 8_000_000.0, 40_000.0, 90),
        Item("\uD83D\uDDBC", "Собрание искусства", 200_000_000.0, 800_000.0, 170),
    ))

    val categories = listOf(home, car, tech)
    fun byId(id: String): Category? = categories.firstOrNull { it.id == id }

    // === СВЕТСКАЯ ЖИЗНЬ (разовые траты, статус навсегда) ===
    data class Experience(val id: String, val emoji: String, val title: String, val cost: Double, val status: Int)

    val experiences = listOf(
        Experience("dubai", "\uD83C\uDFD6", "Отпуск в Дубае", 50_000.0, 10),
        Experience("monaco", "\uD83C\uDFB0", "Уикенд в Монако", 300_000.0, 25),
        Experience("opera", "\uD83C\uDFAD", "Ложа в опере (сезон)", 1_200_000.0, 40),
        Experience("safari", "\uD83E\uDD81", "Сафари в Африке", 6_000_000.0, 70),
        Experience("space", "\uD83D\uDE80", "Полёт в космос", 500_000_000.0, 150),
    )
    fun experienceById(id: String) = experiences.firstOrNull { it.id == id }

    // ---------- работающие бонусы (по индексу уровня) ----------
    private val homeBonus = listOf(0.0, 0.02, 0.05, 0.07, 0.10, 0.12, 0.15, 0.20)
    private val carHours = listOf(0, 0, 0, 1, 1, 1, 2, 2, 2, 3)
    private val techStudy = listOf(0.0, 0.03, 0.08, 0.10, 0.14, 0.16, 0.18)

    /** Жильё компенсирует недосып (бодрость не выше 100%). */
    fun homeSleepBonus(state: GameState): Double =
        homeBonus.getOrElse(state.ownedHome) { homeBonus.last() }

    /** Транспорт добавляет часы в бюджет дня. */
    fun carExtraHours(state: GameState): Int =
        carHours.getOrElse(state.ownedCar) { carHours.last() }

    /** Стиль/техника ускоряет учёбу. */
    fun techStudyMult(state: GameState): Double =
        1.0 + techStudy.getOrElse(state.ownedTech) { techStudy.last() }

    fun ownedIndex(state: GameState, catId: String): Int = when (catId) {
        "home" -> state.ownedHome
        "car" -> state.ownedCar
        else -> state.ownedTech
    }

    /** Содержание всего имущества в день. */
    fun dailyUpkeep(state: GameState): Double {
        var u = 0.0
        u += home.items.take(state.ownedHome + 1).sumOf { it.upkeep }
        u += car.items.take(state.ownedCar + 1).sumOf { it.upkeep }
        u += tech.items.take(state.ownedTech + 1).sumOf { it.upkeep }
        return u
    }

    /** Социальный статус: имущество + светская жизнь. */
    fun socialStatus(state: GameState): Int {
        var st = 0
        st += home.items.getOrElse(state.ownedHome) { home.items.first() }.status
        st += car.items.getOrElse(state.ownedCar) { car.items.first() }.status
        st += tech.items.getOrElse(state.ownedTech) { tech.items.first() }.status
        st += state.experiencesDone.sumOf { id -> experienceById(id)?.status ?: 0 }
        return st
    }

    // ---------- титулы ----------
    data class Title(val name: String, val threshold: Double)

    val titles = listOf(
        Title("Безработный", 0.0),
        Title("Работяга", 50.0),
        Title("Предприниматель", 5_000.0),
        Title("Бизнесмен", 100_000.0),
        Title("Миллионер", 1_000_000.0),
        Title("Мультимиллионер", 50_000_000.0),
        Title("Олигарх", 1_000_000_000.0),
        Title("Магнат", 100_000_000_000.0),
        Title("Легенда", GameConfig.TOP1_USD),
    )

    fun titleIndex(totalEarned: Double): Int {
        var idx = 0
        titles.forEachIndexed { i, t -> if (totalEarned >= t.threshold) idx = i }
        return idx
    }
}

/** Хроника жизни: компактные коды событий -> человекочитаемый текст. */
object Chronicle {
    const val MAX = 60

    /** Запись: "день|код|параметр". */
    fun entry(gameDay: Int, code: String, param: String = "") = "$gameDay|$code|$param"

    fun render(record: String): Pair<Int, String>? {
        val p = record.split("|")
        if (p.size < 2) return null
        val day = p[0].toIntOrNull() ?: return null
        val param = p.getOrElse(2) { "" }
        val text = when (p[1]) {
            "start" -> "Вы начали свой путь: комната в общежитии, обычные часы"
            "job" -> "Устроились: ${Jobs.byIdOrNull(param)?.title ?: param}"
            "quit" -> "Уволились с работы"
            "biz" -> {
                val parts = param.split(":")
                val ind = Industries.all.firstOrNull { it.id == parts.getOrNull(0) }
                val lvl = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val name = ind?.levels?.getOrNull(lvl - 1)?.name ?: param
                "Бизнес: открыто «$name»"
            }
            "edu" -> "Диплом: ${Education.byId(param)?.title ?: param}"
            "own" -> {
                val parts = param.split(":")
                val cat = Lifestyle.byId(parts.getOrNull(0) ?: "")
                val item = cat?.items?.getOrNull(parts.getOrNull(1)?.toIntOrNull() ?: 0)
                "Куплено: ${item?.emoji ?: ""} ${item?.title ?: param}"
            }
            "sell" -> {
                val parts = param.split(":")
                val cat = Lifestyle.byId(parts.getOrNull(0) ?: "")
                val item = cat?.items?.getOrNull(parts.getOrNull(1)?.toIntOrNull() ?: 0)
                "Продано: ${item?.title ?: param}"
            }
            "exp" -> "Светская жизнь: ${Lifestyle.experienceById(param)?.title ?: param}"
            "title" -> "Новый статус: ${Lifestyle.titles.getOrNull(param.toIntOrNull() ?: 0)?.name ?: param}"
            "net" -> "Окружение: ${Network.all.firstOrNull { it.id == param }?.title ?: param}"
            "ms" -> "Веха: «${Milestones.all.getOrNull(param.toIntOrNull() ?: 0)?.name ?: param}»"
            else -> return null
        }
        return day to text
    }
}

/** Музей прошлых жизней. Запись: "номер|дней|заработано|титул|дом|машина". */
object Museum {
    fun entry(num: Int, days: Int, earned: Double, titleIdx: Int, homeIdx: Int, carIdx: Int) =
        "$num|$days|$earned|$titleIdx|$homeIdx|$carIdx"

    data class Life(val num: Int, val days: Int, val earned: Double,
                    val title: String, val home: String, val car: String)

    fun parse(record: String): Life? {
        val p = record.split("|")
        if (p.size < 6) return null
        return Life(
            num = p[0].toIntOrNull() ?: return null,
            days = p[1].toIntOrNull() ?: 0,
            earned = p[2].toDoubleOrNull() ?: 0.0,
            title = Lifestyle.titles.getOrNull(p[3].toIntOrNull() ?: 0)?.name ?: "",
            home = Lifestyle.home.items.getOrNull(p[4].toIntOrNull() ?: 0)
                ?.let { "${it.emoji} ${it.title}" } ?: "",
            car = Lifestyle.car.items.getOrNull(p[5].toIntOrNull() ?: 0)
                ?.let { "${it.emoji} ${it.title}" } ?: ""
        )
    }
}
