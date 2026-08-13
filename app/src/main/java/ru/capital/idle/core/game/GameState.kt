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
    // автовклад: раз в игровой день переносит с карты во вклад всё сверх резерва.
    // Не путать с капитализацией — та оставляет во вкладе его собственный доход
    val autoInvestOn: Boolean = false,
    val autoInvestAsset: Int = -1,       // ordinal закреплённого инструмента; -1 = лучший доступный
    val autoInvestReserve: Double = 0.0, // сколько всегда остаётся на карте
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
    // давление элит: не считается на лету, а хранится и обновляется раз в игровой день.
    // Деньги растут каждый тик, и без этого доход бизнесов сползал бы прямо на глазах —
    // см. GameMath.pressureOnNewDay
    val pressure: Double = 0.0,
    val pressureDay: Int = 0,      // игровой день, на который посчитано pressure. 0 = ещё не считали
    // игровой день, на который сняты величины дня: прибыль предприятий (Enterprise.profitShown)
    // и доход для расчёта тапа. Отметка своя, а не общая с давлением: у старых сохранений
    // правила восстановления разные
    val statsShownDay: Int = 0,
    // доход в день на момент последнего снимка — по нему считается награда за тап
    val tapIncome: Double = 0.0,
    // стиль жизни
    // имущество: множества индексов КУПЛЕННЫХ предметов — источник истины.
    // Стартовый набор (комната в общежитии, пешком, обычные часы) — индекс 0 в каждой категории.
    val ownedHomes: Set<Int> = setOf(0),
    val ownedCars: Set<Int> = setOf(0),
    val ownedTechs: Set<Int> = setOf(0),
    // коллекция: id предмета -> цена, за которую он куплен (для расчёта прибыли)
    val collectibles: Map<String, Double> = emptyMap(),
    // торги: активный лот (null — торгов нет), когда начнутся следующие и посев ГПСЧ торгов
    val auction: Auction? = null,
    val auctionNextGameH: Double = 0.0,
    val auctionSeed: Long = 0L,
    val lastTitleIdx: Int = 0,
    val experiencesDone: Set<String> = emptySet(),   // светская жизнь
    val debt: Double = 0.0,                          // долг при жизни не по средствам
    val activatedCardTier: Int = 0,                  // ordinal активированного тира карты (Classic=0)
    val chronicle: List<String> = emptyList(),   // записи Chronicle.entry
    val museum: List<String> = emptyList(),      // записи Museum.entry
    // цели
    val milestonesClaimed: Int = 0,
    // максимальный достигнутый капитал: храповик для порогов разделов и титулов.
    // Сам капитал умеет падать (упали акции, продано имущество), а открытое не закрывается
    val peakNetWorth: Double = 0.0,
    // статистика (за все жизни)
    val statTaps: Long = 0L,
    val statTapEarned: Double = 0.0,
    val statAllTimeEarned: Double = 0.0,
    val statBestDayIncome: Double = 0.0,
    val statDaysPrevLives: Int = 0,
    // сколько раз игрок переродился. Ноль — первая жизнь, сколько бы слитков ни было:
    // слитки выдают и за вехи, а признаком «уже проходил игру» служит само перерождение
    val statLives: Int = 0,
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
    // оформление интерфейса. Ключ темы лежит строкой, а не перечислением, потому что
    // core/game не знает про ui: неизвестный ключ (и старое сохранение, где поля нет)
    // означает тему по умолчанию — см. AppTheme.byId
    val themeId: String = "glass",
    val playerName: String = "",
    val onboarded: Boolean = false,
    val lastSeenMillis: Long = 0L
) {
    /** Лучшее купленное жильё. Раньше это было хранимое поле — теперь максимум из множества. */
    val ownedHome: Int get() = ownedHomes.maxOrNull() ?: 0
    /** Лучший купленный транспорт. */
    val ownedCar: Int get() = ownedCars.maxOrNull() ?: 0
    /** Лучший купленный аксессуар. */
    val ownedTech: Int get() = ownedTechs.maxOrNull() ?: 0

    /** Бюджет дня: транспорт добавляет часы. */
    val dayBudget: Int get() = 24 - sleepH + Lifestyle.carExtraHours(this)
    val studyHCalc: Int get() = (dayBudget - workH - bizH).coerceAtLeast(0)
    val phase: MarketPhase get() = MarketPhase.entries[phaseIndex.coerceIn(0, MarketPhase.entries.size - 1)]
}
