package ru.capital.idle.core.game

/** Иммутабельное состояние игры. Деньги в долларах. Время в игровых часах. */
data class GameState(
    val money: Double = 0.0,
    val totalEarned: Double = 0.0,
    val bullion: Long = 0L,
    // престиж-апгрейды
    val pIncome: Int = 0,        // множитель дохода бизнесов
    val pNegotiator: Int = 0,    // + к зарплате и тапу
    val pStart: Int = 0,         // стартовый капитал
    val pStudy: Int = 0,         // быстрая учёба
    val pSafe: Int = 0,          // вместительный сейф (кап оффлайна)
    // распорядок дня
    val sleepH: Int = 8,
    val workH: Int = 10,
    val bizH: Int = 0,
    // работа и отрасли
    val jobId: String = "",          // пусто = безработный
    // предприятия по отраслям: для каждой отрасли список предприятий (каждое со своим уровнем и управляющим)
    val enterprises: List<List<Enterprise>> = List(Industries.count) { emptyList() },
    // образование
    val eduDone: Set<String> = emptySet(),
    val studyingId: String = "",          // пусто = не учится
    val studyProgress: Double = 0.0,      // учебных часов пройдено
    // инвестиции
    val investValues: List<Double> = List(Investments.COUNT) { 0.0 },
    val investCosts: List<Double> = List(Investments.COUNT) { 0.0 },
    val capitalizeMask: Int = 0,   // битовая маска: какие вклады капитализируют доход (бит i = Asset.ordinal)
    val boostEndsAtMillis: Long = 0L,   // реклама: до какого момента (реального) действует удвоение дохода ×2
    // биржа
    val stockPrices: List<Double> = Exchange.stocks.map { it.basePrice },
    val stockQty: List<Double> = List(Exchange.COUNT) { 0.0 },
    val stockAvg: List<Double> = List(Exchange.COUNT) { 0.0 },
    val stockHour: Double = 0.0,                 // возраст биржи в игровых часах (для циклов)
    val newsStockIndex: Int = -1,                // активное событие: индекс бумаги (-1 нет)
    val newsGood: Boolean = false,
    val newsStrength: Double = 0.0,
    val newsTitleKey: Int = 0,
    val newsHoursLeft: Int = 0,
    val newsTotalHours: Int = 0,
    val nextNewsDay: Int = 2,                    // на какой игровой день запланирована следующая новость
    // окружение
    val netOwned: Set<String> = emptySet(),
    val reputation: Double = 0.0,
    // стиль жизни
    val ownedHome: Int = 0,
    val ownedCar: Int = 0,
    val ownedTech: Int = 0,
    val lastTitleIdx: Int = 0,
    val experiencesDone: Set<String> = emptySet(),   // светская жизнь
    val debt: Double = 0.0,                          // долг при жизни не по средствам
    val activatedCardTier: Int = 0,                  // ordinal активированного тира карты (Classic=0)
    val chronicle: List<String> = emptyList(),   // записи Chronicle.entry
    val museum: List<String> = emptyList(),      // записи Museum.entry
    // цели
    val milestonesClaimed: Int = 0,
    // статистика (за все жизни)
    val statTaps: Long = 0L,
    val statTapEarned: Double = 0.0,
    val statAllTimeEarned: Double = 0.0,
    val statBestDayIncome: Double = 0.0,
    val statDaysPrevLives: Int = 0,
    val statBullionEarned: Long = 0L,
    val statLifeItems: Int = 0,
    val statBizLevels: Int = 0,
    val statBestTitle: Int = 0,
    // рынок
    val phaseIndex: Int = 0,
    val phaseEndGameH: Double = 0.0,
    // время и мета
    val gameHours: Double = 8.0,          // старт в 08:00
    val startDateMillis: Long = 0L,       // день 1 = дата старта игры
    val tutorialStep: Int = 0,            // 0..5 — шаги гида, 99 — пройден
    val hintsSeen: Set<String> = emptySet(),  // прочитанные подсказки разделов
    val seenTabs: Set<String> = setOf("main"),   // посещённые вкладки (точка «новое»)
    val announced: Set<String> = emptySet(),     // показанные анонсы открытий
    val currencyCode: String = "USD",
    val playerName: String = "",
    val onboarded: Boolean = false,
    val lastSeenMillis: Long = 0L
) {
    /** Бюджет дня: транспорт добавляет часы. */
    val dayBudget: Int get() = 24 - sleepH + Lifestyle.carExtraHours(this)
    val studyHCalc: Int get() = (dayBudget - workH - bizH).coerceAtLeast(0)
    val phase: MarketPhase get() = MarketPhase.entries[phaseIndex.coerceIn(0, MarketPhase.entries.size - 1)]
}
