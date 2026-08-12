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

    /** Лучший (максимальный) купленный предмет категории. */
    fun ownedIndex(state: GameState, catId: String): Int = when (catId) {
        "home" -> state.ownedHome
        "car" -> state.ownedCar
        else -> state.ownedTech
    }

    /** Все купленные предметы категории. */
    fun ownedSet(state: GameState, catId: String): Set<Int> = when (catId) {
        "home" -> state.ownedHomes
        "car" -> state.ownedCars
        else -> state.ownedTechs
    }

    /**
     * Лестница {0..maxIdx} — как имущество хранилось раньше, одним индексом уровня.
     * Используется при чтении старых сейвов: ownedHome=N означает «куплены ступени 0..N».
     */
    fun ladderSet(maxIdx: Int): Set<Int> = (0..maxIdx.coerceAtLeast(0)).toSet()

    /**
     * Чистка множества купленного: индексы вне каталога отбрасываются, пустой
     * результат откатывается к стартовому предмету. Вызывается на границе загрузки —
     * битый сейв не должен ронять экраны, которые индексируют items[ownedHome] напрямую.
     */
    fun sanitizeOwned(cat: Category, raw: Set<Int>): Set<Int> {
        val valid = raw.filter { it in cat.items.indices }.toSet()
        return if (valid.isEmpty()) setOf(0) else valid
    }

    /** Сумма поля по всем купленным предметам категории. */
    private fun Category.sumOwned(owned: Set<Int>, field: (Item) -> Double): Double =
        owned.sumOf { i -> items.getOrNull(i)?.let(field) ?: 0.0 }

    /** Содержание всего имущества в день. */
    fun dailyUpkeep(state: GameState): Double =
        home.sumOwned(state.ownedHomes) { it.upkeep } +
        car.sumOwned(state.ownedCars) { it.upkeep } +
        tech.sumOwned(state.ownedTechs) { it.upkeep }

    /** Полная цена всех купленных предметов (для расчёта капитала). */
    fun ownedCost(state: GameState): Double =
        home.sumOwned(state.ownedHomes) { it.cost } +
        car.sumOwned(state.ownedCars) { it.cost } +
        tech.sumOwned(state.ownedTechs) { it.cost }

    /** Социальный статус: имущество + светская жизнь. */
    fun socialStatus(state: GameState): Int {
        var st = 0
        st += home.items.getOrElse(state.ownedHome) { home.items.first() }.status
        st += car.items.getOrElse(state.ownedCar) { car.items.first() }.status
        st += tech.items.getOrElse(state.ownedTech) { tech.items.first() }.status
        st += state.experiencesDone.sumOf { id -> experienceById(id)?.status ?: 0 }
        st += Collectibles.statusPoints(state)
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

    /**
     * Титул по капиталу. Раньше считался от валового дохода, из-за чего «Миллионер»
     * приходил раньше, чем капитал на главном экране доходил до миллиона.
     *
     * Передавать сюда полагается максимальный достигнутый капитал ([GameState.peakNetWorth]):
     * заработанный титул не понижается, даже если капитал просел.
     */
    fun titleIndex(netWorth: Double): Int {
        var idx = 0
        titles.forEachIndexed { i, t -> if (netWorth >= t.threshold) idx = i }
        return idx
    }
}

/** Хроника жизни: компактные коды событий -> человекочитаемый текст. */
object Chronicle {
    const val MAX = 60

    /**
     * Запись: "день|код|параметр".
     *
     * Параметр экранируется: в него попадает название предприятия, которое игрок вписывает
     * сам, а `|` разделяет поля записи и `;` — записи между собой в сохранении. Экранируются
     * только эти два знака и сама обратная косая; двоеточие внутри параметра осмысленно
     * (им разделены подполя) и остаётся как есть. Старые записи проходят через снятие
     * экранирования без изменений: ни `\\p`, ни `\\s` в них встретиться не могло.
     */
    fun entry(gameDay: Int, code: String, param: String = "") =
        "$gameDay|$code|${param.escParam()}"

    private fun String.escParam(): String =
        replace("\\", "\\b").replace("|", "\\p").replace(";", "\\s")

    private fun String.unescParam(): String =
        replace("\\s", ";").replace("\\p", "|").replace("\\b", "\\")

    fun render(record: String): Pair<Int, String>? {
        // limit = 3: всё после второго разделителя — параметр целиком
        val p = record.split("|", limit = 3)
        if (p.size < 2) return null
        val day = p[0].toIntOrNull() ?: return null
        val param = p.getOrElse(2) { "" }.unescParam()
        val text = when (p[1]) {
            "start" -> "Вы начали свой путь: комната в общежитии, обычные часы"
            "job" -> "Устроились: ${Jobs.byIdOrNull(param)?.title ?: param}"
            "quit" -> "Уволились с работы"
            // параметр: "отрасль:ступень:название". Название идёт последним и читается
            // с limit — двоеточие внутри него не должно ломать разбор. У записей из старых
            // сохранений названия нет, и строка остаётся прежней, без него
            "biz" -> {
                val parts = param.split(":", limit = 3)
                val ind = Industries.all.firstOrNull { it.id == parts.getOrNull(0) }
                val lvl = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val type = ind?.levels?.getOrNull(lvl - 1)?.name ?: param
                val own = parts.getOrNull(2).orEmpty()
                if (own.isEmpty()) "Бизнес: открыто «$type»" else "Бизнес: $type «$own»"
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
            // торги: параметр — "id:цена". Запись остаётся в хронике навсегда, поэтому исход
            // лота можно узнать и после перезапуска, когда всплывающая карточка уже не покажется
            "auc+", "auc-" -> {
                val parts = param.split(":")
                val item = Collectibles.byId(parts.getOrNull(0) ?: "")
                val price = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                val name = item?.let { "${it.emoji} ${it.title}" } ?: param
                if (p[1] == "auc+") "Торги выиграны: $name за ${GameMath.format(price)}"
                else "Торги проиграны: $name ушёл за ${GameMath.format(price)}"
            }
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
