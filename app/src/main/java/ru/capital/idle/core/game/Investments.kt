package ru.capital.idle.core.game

/** Пассивные инструменты: ratePerDay — доля дохода за игровой день. */
enum class Asset(val title: String, val ratePerDay: Double, val vol: Double, val reqCourse: String) {
    DEPOSIT("Депозит", 0.002, 0.0, "acc"),
    BONDS("Облигации", 0.005, 0.004, "acc"),
    REALTY("Недвижимость", 0.010, 0.008, "uni");

    fun riskText(): String = when {
        vol == 0.0 -> "стабильно"
        vol < 0.01 -> "низкий риск"
        else -> "средний риск"
    }
}

object Investments {
    val COUNT = Asset.entries.size

    fun rate(a: Asset, eduDone: Set<String>): Double =
        a.ratePerDay * (if ("mba" in eduDone) 1.2 else 1.0)

    /** Доход пассивного портфеля за игровой день. */
    fun incomePerDay(values: List<Double>, eduDone: Set<String>): Double {
        var s = 0.0
        Asset.entries.forEachIndexed { i, a -> s += values.getOrElse(i) { 0.0 } * rate(a, eduDone) }
        return s
    }
}

/** Биржа: торгуемые бумаги. Цена живёт циклами и реагирует на новости. */
data class Stock(
    val ticker: String,
    val title: String,
    val basePrice: Double,
    val amp: Double,        // амплитуда ценового цикла
    val period: Double,     // период цикла в игровых часах
    val vol: Double,        // часовой шум
    val growth: Double,     // медленный базовый рост за час
    val divPerDay: Double,  // дивиденд: доля стоимости в день (0 = не платит)
    val info: String,
    val reqCourse: String
)

/** Активное новостное событие по бумаге. */
data class StockEvent(val stockIndex: Int, val good: Boolean, val strength: Double,
                      val titleKey: Int, val hoursLeft: Int, val totalHours: Int)

object Exchange {
    val stocks = listOf(
        Stock("IDX", "Индексный фонд", 142.0, 0.04, 60.0, 0.004, 0.0006, 0.0008, "фонд · платит дивиденды", "acc"),
        Stock("GLD", "Золотой резерв", 97.0, 0.06, 90.0, 0.006, 0.0003, 0.0005, "защитный · дивиденды", "acc"),
    )
    val COUNT = stocks.size

    // фонды (с дивидендами) события не получают — только акции
    val EVENT_FIRST_INDEX = 2

    /**
     * Цена как функция «возраста» бумаги в игровых часах (детерминированно от hour).
     * base растёт ростом, поверх — синус-цикл; шум добавляется отдельно в ViewModel.
     */
    /**
     * Цена как функция «возраста» бумаги в игровых часах.
     * Стабильная база + медленный ограниченный дрейф + циклическая волна.
     * Без накопительной экспоненты, поэтому цена не улетает в космос.
     */
    fun cyclePrice(s: Stock, hour: Double): Double {
        // очень медленный дрейф базы, жёстко ограниченный коридором ±60%
        val driftRaw = s.growth * hour * 0.02
        val drift = 1.0 + driftRaw.coerceIn(-0.6, 0.6)
        // длинная волна цикла
        val cycle = 1.0 + s.amp * kotlin.math.sin(2.0 * Math.PI * hour / s.period + (s.ticker.hashCode() % 7))
        return (s.basePrice * drift * cycle).coerceAtLeast(1.0)
    }

    /** Множитель активного события на текущий час (1.0 если события нет). */
    fun eventMult(ev: StockEvent?): Double {
        if (ev == null) return 1.0
        val dir = if (ev.good) 1.0 else -1.0
        // влияние сильнее в начале, затухает к концу
        val k = ev.hoursLeft.toDouble() / ev.totalHours
        return 1.0 + dir * ev.strength * k
    }

    // заголовки новостей: good и bad
    val goodNews = listOf(
        "{n} запускает прорывной продукт" to "Аналитики ждут роста в ближайшие дни",
        "Сильный отчёт {n}" to "Прибыль выше прогнозов",
        "{n} заключает крупный контракт" to "Рынок реагирует позитивно",
    )
    val badNews = listOf(
        "Расследование против {n}" to "Регулятор начал проверку",
        "Провальный отчёт {n}" to "Прибыль ниже ожиданий",
        "Скандал вокруг {n}" to "Инвесторы выходят из бумаги",
    )

    fun newsTitle(ev: StockEvent): Pair<String, String> {
        val name = stocks[ev.stockIndex].title
        val pool = if (ev.good) goodNews else badNews
        val (t, x) = pool[ev.titleKey % pool.size]
        return t.replace("{n}", name) to x
    }
}
