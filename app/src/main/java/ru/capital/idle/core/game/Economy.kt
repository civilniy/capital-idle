package ru.capital.idle.core.game

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/** Игровое время: 1 игровой час = 1 сек реального времени, сутки = 24 сек. */
object GameTime {
    const val DAY_REAL_SEC = 24.0
    const val HOURS_PER_DAY = 24.0
    /** Сколько игровых часов проходит за dt реальных секунд. */
    fun gameHours(dtRealSec: Double): Double = dtRealSec * HOURS_PER_DAY / DAY_REAL_SEC
}

/** Эффективность от сна: 8ч = норма, недосып штрафует, пересып не даёт бонуса. */
object Sleep {
    fun eff(sleepH: Int): Double = when (sleepH.coerceIn(3, 9)) {
        3 -> 0.45; 4 -> 0.60; 5 -> 0.75; 6 -> 0.87; 7 -> 0.95; else -> 1.0
    }
}

/** Вакансия наёмной работы. ratePerHour в долларах. */
data class Job(val id: String, val title: String, val ratePerHour: Double, val reqCourse: String?)

object Jobs {
    val all = listOf(
        Job("courier", "Курьер", 0.8, null),
        Job("seller", "Продавец", 2.5, "school"),
        Job("manager", "Менеджер по продажам", 8.0, "sales"),
        Job("fin", "Финансовый аналитик", 30.0, "uni"),
        Job("director", "Директор филиала", 110.0, "mgmt"),
    )
    /** null = безработный (пустой id). */
    fun byIdOrNull(id: String): Job? = all.firstOrNull { it.id == id }
}

/** Уровень отрасли: цена, доход в час работы точки, часы управления в день, требование. */
data class IndustryLevel(
    val name: String,
    val cost: Double,
    val incomePerHour: Double,
    val needHours: Int,
    val reqCourse: String?,
    val note: String? = null
)

data class Industry(val id: String, val title: String, val levels: List<IndustryLevel>)

object Industries {
    /** Точка работает 12 часов в игровые сутки. */
    const val WORK_HOURS = 12.0

    val all = listOf(
        Industry("trade", "Торговля", listOf(
            IndustryLevel("Лоток", 200.0, 1.28, 2, null),
            IndustryLevel("Точка на рынке", 1_300.0, 3.85, 3, null),
            IndustryLevel("Ларёк", 8_450.0, 11.5, 4, "school"),
            IndustryLevel("Магазин у дома", 54_925.0, 34.6, 4, "school"),
            IndustryLevel("Универмаг", 357_012.0, 103.8, 5, "sales"),
            IndustryLevel("Сеть магазинов", 2_320_581.0, 311.5, 5, "sales"),
            IndustryLevel("Гипермаркет", 15_083_778.0, 934.6, 4, "mgmt"),
            IndustryLevel("Торговый дом", 98_044_558.0, 2_804.0, 3, "mgmt"),
            IndustryLevel("Ритейл-империя", 637_289_626.0, 8_412.0, 2, "mba"),
        )),
        Industry("food", "Общепит", listOf(
            IndustryLevel("Кофейная точка", 1_500.0, 9.62, 2, "school"),
            IndustryLevel("Стритфуд", 9_750.0, 28.8, 3, "school"),
            IndustryLevel("Кофейня", 63_375.0, 86.5, 4, "school"),
            IndustryLevel("Кафе", 411_938.0, 259.6, 4, "sales"),
            IndustryLevel("Ресторан", 2_677_594.0, 778.8, 5, "sales"),
            IndustryLevel("Сеть кофеен", 17_404_359.0, 2_337.0, 5, "mgmt"),
            IndustryLevel("Сеть ресторанов", 113_128_336.0, 7_010.0, 4, "mgmt"),
            IndustryLevel("Ресторанный холдинг", 735_334_184.0, 21_029.0, 3, "mba"),
        )),
        Industry("serv", "Услуги", listOf(
            IndustryLevel("Автомойка", 11_000.0, 70.5, 2, "school"),
            IndustryLevel("Барбершоп", 71_500.0, 211.5, 3, "school"),
            IndustryLevel("Автосервис", 464_750.0, 634.6, 4, "sales"),
            IndustryLevel("Салон красоты", 3_020_875.0, 1_904.0, 4, "sales"),
            IndustryLevel("Фитнес-клуб", 19_635_688.0, 5_712.0, 5, "sales"),
            IndustryLevel("Сеть салонов", 127_631_969.0, 17_135.0, 5, "mgmt"),
            IndustryLevel("Сеть фитнес-клубов", 829_607_797.0, 51_404.0, 4, "mgmt"),
            IndustryLevel("Велнес-холдинг", 5_392_450_680.0, 154_212.0, 3, "uni"),
        )),
        Industry("prod", "Производство", listOf(
            IndustryLevel("Мастерская", 75_000.0, 480.8, 2, "sales"),
            IndustryLevel("Мини-цех", 487_500.0, 1_442.0, 3, "sales"),
            IndustryLevel("Цех", 3_168_750.0, 4_327.0, 4, "mgmt"),
            IndustryLevel("Малый завод", 20_596_875.0, 12_981.0, 4, "mgmt"),
            IndustryLevel("Фабрика", 133_879_688.0, 38_942.0, 5, "uni"),
            IndustryLevel("Завод", 870_217_969.0, 116_827.0, 5, "uni"),
            IndustryLevel("Промкомплекс", 5_656_416_797.0, 350_481.0, 4, "mba"),
            IndustryLevel("Промышленная группа", 36_766_709_180.0, 1_051_442.0, 3, "mba"),
        )),
        Industry("log", "Логистика", listOf(
            IndustryLevel("Пункт выдачи", 480_000.0, 3_077.0, 2, "mgmt"),
            IndustryLevel("Курьерская служба", 3_120_000.0, 9_231.0, 3, "mgmt"),
            IndustryLevel("Автопарк", 20_280_000.0, 27_692.0, 4, "mgmt"),
            IndustryLevel("Склад", 131_820_000.0, 83_077.0, 4, "uni"),
            IndustryLevel("Логистический центр", 856_830_000.0, 249_231.0, 5, "uni"),
            IndustryLevel("Транспортная компания", 5_569_395_000.0, 747_692.0, 5, "uni"),
            IndustryLevel("Сеть терминалов", 36_201_067_500.0, 2_243_077.0, 4, "mba"),
            IndustryLevel("Логистический холдинг", 235_306_938_750.0, 6_729_231.0, 3, "mba"),
        )),
        Industry("it", "IT и финансы", listOf(
            IndustryLevel("Стартап", 3_300_000.0, 21_154.0, 2, "uni"),
            IndustryLevel("Студия разработки", 21_450_000.0, 63_462.0, 3, "uni"),
            IndustryLevel("IT-компания", 139_425_000.0, 190_385.0, 4, "uni"),
            IndustryLevel("Продуктовая компания", 906_262_500.0, 571_154.0, 4, "mba"),
            IndustryLevel("Экосистема", 5_890_706_250.0, 1_713_462.0, 5, "mba"),
            IndustryLevel("Корпорация", 38_289_590_625.0, 5_140_385.0, 5, "mba"),
            IndustryLevel("Инвестхолдинг", 248_882_339_062.0, 15_421_154.0, 4, "mba"),
        )),
    )
    val count: Int get() = all.size

    /** Порог соц-статуса (образ жизни) для ОТКРЫТИЯ отрасли. Высокие отрасли требуют уровня жизни. */
    val statusGate = mapOf(
        "trade" to 0, "food" to 0, "serv" to 8,
        "prod" to 25, "log" to 55, "it" to 90
    )
    fun statusGateFor(indId: String): Int = statusGate[indId] ?: 0
}

/** Курс образования: цена, длительность в учебных часах, требование. */
data class Course(
    val id: String, val title: String, val cost: Double,
    val durationHours: Double, val info: String, val reqCourse: String?
)

data class EduBranch(val title: String, val courses: List<Course>)

object Education {
    val branches = listOf(
        EduBranch("КОММЕРЦИЯ", listOf(
            Course("school", "Среднее образование", 50.0, 18.0, "вакансия: Продавец · первые бизнесы в торговле, общепите, услугах", null),
            Course("sales", "Курсы продаж", 2_000.0, 30.0, "вакансия: Менеджер · средние уровни бизнесов · цены бизнесов -5%", "school"),
            Course("mgmt", "Менеджмент", 50_000.0, 50.0, "вакансия: Директор · Логистика · управляющие: -40% часов", "sales"),
        )),
        EduBranch("ФИНАНСЫ", listOf(
            Course("acc", "Бухгалтерия", 1_000.0, 24.0, "инвестиции: Депозит, Облигации · биржа: фонды", "school"),
            Course("uni", "Университет (финансы)", 20_000.0, 70.0, "вакансия: Финансист · отрасль IT · Недвижимость", "acc"),
            Course("mba", "MBA", 500_000.0, 120.0, "вершины отраслей · доходность пассива +20%", "uni"),
        )),
        EduBranch("УПРАВЛЕНИЕ", listOf(
            Course("lead", "Лидерство", 10_000.0, 40.0, "доход бизнесов +15%", "school"),
            Course("crisis", "Антикризисное управление", 200_000.0, 80.0, "кризис мягче: ×0.45 → ×0.65", "lead"),
        )),
    )
    val allCourses: List<Course> by lazy { branches.flatMap { it.courses } }
    fun byId(id: String): Course? = allCourses.firstOrNull { it.id == id }
}

/** Окружение: наставник, клуб, фонд. */
data class NetItem(val id: String, val title: String, val cost: Double, val info: String)

object Network {
    val all = listOf(
        NetItem("mentor", "Наставник", 5_000.0, "учёба быстрее ×1.5"),
        NetItem("club", "Бизнес-клуб", 1_000_000.0, "+1 репутация/день"),
        NetItem("charity", "Благотворительный фонд", 1_000_000_000.0, "+4 репутации/день"),
    )
    fun repPerDay(owned: Set<String>): Double =
        (if ("club" in owned) 1.0 else 0.0) + (if ("charity" in owned) 4.0 else 0.0)
}

/** Фаза рынка. mult — множитель дохода бизнесов, sale — множитель цен бизнесов. */
enum class MarketPhase(val title: String, val mult: Double, val sale: Double) {
    GROWTH("Рост", 1.0, 1.0),
    BOOM("Бум", 1.6, 1.25),
    CRISIS("Кризис", 0.45, 0.6),
    RECOVERY("Восстановление", 0.85, 0.9);

    companion object {
        /** Длительность фазы в игровых днях. */
        fun randomLengthDays(): Double = 5.0 + Math.random() * 7.0
        fun next(cur: MarketPhase): MarketPhase {
            val step = if (Math.random() < 0.8) 1 else 2
            return entries[(cur.ordinal + step) % entries.size]
        }
    }
}

/** Давление элит: включается после $1млрд, гасится репутацией. */
object Pressure {
    fun value(moneyUsd: Double, reputation: Double): Double {
        if (moneyUsd < 1e9) return 0.0
        val base = min(0.65, 0.12 * log10(moneyUsd / 1e9 + 1.0) * 1.6)
        return base * (1.0 - min(0.7, reputation / 140.0))
    }
}


/** Класс управляющего: эффективность дохода и зарплата в день. */
enum class Manager(val title: String, val eff: Double, val salaryPerDay: Double) {
    STUDENT("Студент", 0.60, 40.0),
    MANAGER("Менеджер", 0.80, 180.0),
    PRO("Профессионал", 0.95, 650.0),
    TOP("Топ-менеджер", 1.10, 2_200.0);

    companion object {
        fun byOrdinalOrNull(i: Int): Manager? = entries.getOrNull(i)
    }
}

/**
 * Одно предприятие игрока: название, какой уровень (ступень лестницы) и кто управляет
 * (null = лично). Плюс накопители для окупаемости.
 *
 * Копить приходится: вычислить задним числом ничего нельзя. Цена открытия зависит от того,
 * сколько предприятий уже было в отрасли, от фазы рынка и от скидок за образование **в момент
 * покупки** — по уровню и индексу её не восстановить. То же с ценой улучшений и с зарплатой:
 * управляющего меняют и снимают, а сколько дней кто проработал, из текущего состояния не видно.
 *
 * @param earned суммарная выручка за всё время, до вычета зарплаты
 * @param salaryPaid суммарно выплачено управляющим за всё время
 * @param invested суммарно вложено деньгами: цена открытия + все улучшения (зарплата отдельно)
 * @param statsSinceDay игровой день, с которого ведётся учёт. [STATS_FROM_START] — учёт полный,
 *   ведётся с открытия. Иначе предприятие досталось из сохранения, сделанного до появления
 *   учёта: истории в нём нет, и числа заведомо неполные
 */
data class Enterprise(
    val level: Int = 0,
    val managerOrdinal: Int = -1,
    val name: String = "",
    val earned: Double = 0.0,
    val salaryPaid: Double = 0.0,
    val invested: Double = 0.0,
    val statsSinceDay: Int = STATS_FROM_START,
    /**
     * Чистая прибыль (`earned - salaryPaid`) на момент последней смены игрового дня —
     * то, что показывает карточка. Сами накопители растут каждый тик, и без этого снимка
     * строка окупаемости бежала бы на глазах. Обновляется в [GameMath.profitShownOnNewDay].
     */
    val profitShown: Double = 0.0
) {
    val manager: Manager? get() = if (managerOrdinal < 0) null else Manager.byOrdinalOrNull(managerOrdinal)
    val isManual: Boolean get() = managerOrdinal < 0
    val efficiency: Double get() = manager?.eff ?: 1.0   // лично = 100%

    /** Учёт неполный: предприятие старше своих накопителей. */
    val statsPartial: Boolean get() = statsSinceDay != STATS_FROM_START

    companion object {
        /** Учёт ведётся с самого открытия — накопители полные. */
        const val STATS_FROM_START = -1
    }
}

object BusinessConfig {
    const val MAX_ENTERPRISES_PER_INDUSTRY = 10
    const val HOURS_PER_MANUAL_ENTERPRISE = 3   // личное ведение одного предприятия ест столько часов

    /** Насыщение рынка: степень отдачи при росте числа предприятий в отрасли (n^SATURATION). */
    const val SATURATION_EXP = 0.85
    /** Множитель цены открытия за каждое уже открытое предприятие в отрасли (рынок дорожает). */
    const val OPEN_PRICE_GROWTH = 1.6

    /** Множитель суммарной отдачи отрасли с n предприятиями: n^0.85 вместо n (убывающая отдача). */
    fun saturationMult(n: Int): Double =
        if (n <= 1) 1.0 else Math.pow(n.toDouble(), SATURATION_EXP) / n.toDouble()
}
