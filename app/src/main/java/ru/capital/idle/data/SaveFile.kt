package ru.capital.idle.data

import org.json.JSONObject

/**
 * Сериализация сейва в JSON-файл, устойчивая к версиям.
 * Источник истины при загрузке — этот файл (а не Room), поэтому
 * изменения схемы БД (любые fallbackToDestructiveMigration) не обнуляют прогресс.
 * Недостающие в файле поля берут значения по умолчанию из дефолтного Entity,
 * лишние поля игнорируются. Так старый файл читается новой версией игры.
 */
object SaveFile {
    const val SAVE_VERSION = 1

    fun toJson(e: GameEntity): String {
        val o = JSONObject()
        o.put("__saveVersion", SAVE_VERSION)
        o.put("money", e.money)
        o.put("totalEarned", e.totalEarned)
        o.put("bullion", e.bullion)
        o.put("pIncome", e.pIncome)
        o.put("pNegotiator", e.pNegotiator)
        o.put("pStart", e.pStart)
        o.put("pStudy", e.pStudy)
        o.put("pSafe", e.pSafe)
        o.put("sleepH", e.sleepH)
        o.put("workH", e.workH)
        o.put("bizH", e.bizH)
        o.put("jobId", e.jobId)
        o.put("enterprisesRaw", e.enterprisesRaw)
        o.put("enterpriseStatsRaw", e.enterpriseStatsRaw)
        o.put("eduDoneCsv", e.eduDoneCsv)
        o.put("studyingId", e.studyingId)
        o.put("studyProgress", e.studyProgress)
        o.put("investValuesCsv", e.investValuesCsv)
        o.put("investCostsCsv", e.investCostsCsv)
        o.put("capitalizeMask", e.capitalizeMask)
        o.put("boostEndsAtMillis", e.boostEndsAtMillis)
        o.put("stockPricesCsv", e.stockPricesCsv)
        o.put("stockQtyCsv", e.stockQtyCsv)
        o.put("stockAvgCsv", e.stockAvgCsv)
        o.put("stockHour", e.stockHour)
        o.put("newsStockIndex", e.newsStockIndex)
        o.put("newsGood", e.newsGood)
        o.put("newsStrength", e.newsStrength)
        o.put("newsTitleKey", e.newsTitleKey)
        o.put("newsHoursLeft", e.newsHoursLeft)
        o.put("newsTotalHours", e.newsTotalHours)
        o.put("nextNewsDay", e.nextNewsDay)
        o.put("netOwnedCsv", e.netOwnedCsv)
        o.put("reputation", e.reputation)
        o.put("pressure", e.pressure)
        o.put("pressureDay", e.pressureDay)
        o.put("statsShownDay", e.statsShownDay)
        o.put("tapIncome", e.tapIncome)
        o.put("ownedHomesCsv", e.ownedHomesCsv)
        o.put("ownedCarsCsv", e.ownedCarsCsv)
        o.put("ownedTechsCsv", e.ownedTechsCsv)
        // legacy-индексы пишем и дальше: старая версия игры прочитает такой файл
        o.put("ownedHome", e.ownedHome)
        o.put("ownedCar", e.ownedCar)
        o.put("ownedTech", e.ownedTech)
        o.put("collectiblesRaw", e.collectiblesRaw)
        o.put("auctionRaw", e.auctionRaw)
        o.put("auctionNextGameH", e.auctionNextGameH)
        o.put("auctionSeed", e.auctionSeed)
        o.put("lastTitleIdx", e.lastTitleIdx)
        o.put("experiencesDoneCsv", e.experiencesDoneCsv)
        o.put("debt", e.debt)
        o.put("activatedCardTier", e.activatedCardTier)
        o.put("chronicleRaw", e.chronicleRaw)
        o.put("museumRaw", e.museumRaw)
        o.put("milestonesClaimed", e.milestonesClaimed)
        o.put("statTaps", e.statTaps)
        o.put("statTapEarned", e.statTapEarned)
        o.put("statAllTimeEarned", e.statAllTimeEarned)
        o.put("statBestDayIncome", e.statBestDayIncome)
        o.put("statDaysPrevLives", e.statDaysPrevLives)
        o.put("statBullionEarned", e.statBullionEarned)
        o.put("statLifeItems", e.statLifeItems)
        o.put("statBizLevels", e.statBizLevels)
        o.put("statBestTitle", e.statBestTitle)
        o.put("phaseIndex", e.phaseIndex)
        o.put("phaseEndGameH", e.phaseEndGameH)
        o.put("gameHours", e.gameHours)
        o.put("startDateMillis", e.startDateMillis)
        o.put("tutorialStep", e.tutorialStep)
        o.put("hintsSeenCsv", e.hintsSeenCsv)
        o.put("seenTabsCsv", e.seenTabsCsv)
        o.put("announcedCsv", e.announcedCsv)
        o.put("currencyCode", e.currencyCode)
        o.put("playerName", e.playerName)
        o.put("onboarded", e.onboarded)
        o.put("lastSeenMillis", e.lastSeenMillis)
        return o.toString()
    }

    fun fromJson(json: String): GameEntity? {
        return try {
            val o = JSONObject(json)
            val d = DEFAULT
            GameEntity(
        money = o.optDouble("money", d.money),
        totalEarned = o.optDouble("totalEarned", d.totalEarned),
        bullion = o.optLong("bullion", d.bullion),
        pIncome = o.optInt("pIncome", d.pIncome),
        pNegotiator = o.optInt("pNegotiator", d.pNegotiator),
        pStart = o.optInt("pStart", d.pStart),
        pStudy = o.optInt("pStudy", d.pStudy),
        pSafe = o.optInt("pSafe", d.pSafe),
        sleepH = o.optInt("sleepH", d.sleepH),
        workH = o.optInt("workH", d.workH),
        bizH = o.optInt("bizH", d.bizH),
        jobId = o.optString("jobId", d.jobId),
        enterprisesRaw = o.optString("enterprisesRaw", d.enterprisesRaw),
        // пустышка, а не d.enterpriseStatsRaw: пустая строка — признак файла,
        // записанного до появления учёта, и по ней срабатывает миграция
        enterpriseStatsRaw = o.optString("enterpriseStatsRaw", ""),
        eduDoneCsv = o.optString("eduDoneCsv", d.eduDoneCsv),
        studyingId = o.optString("studyingId", d.studyingId),
        studyProgress = o.optDouble("studyProgress", d.studyProgress),
        investValuesCsv = o.optString("investValuesCsv", d.investValuesCsv),
        investCostsCsv = o.optString("investCostsCsv", d.investCostsCsv),
        capitalizeMask = o.optInt("capitalizeMask", d.capitalizeMask),
        boostEndsAtMillis = o.optLong("boostEndsAtMillis", d.boostEndsAtMillis),
        stockPricesCsv = o.optString("stockPricesCsv", d.stockPricesCsv),
        stockQtyCsv = o.optString("stockQtyCsv", d.stockQtyCsv),
        stockAvgCsv = o.optString("stockAvgCsv", d.stockAvgCsv),
        stockHour = o.optDouble("stockHour", d.stockHour),
        newsStockIndex = o.optInt("newsStockIndex", d.newsStockIndex),
        newsGood = o.optBoolean("newsGood", d.newsGood),
        newsStrength = o.optDouble("newsStrength", d.newsStrength),
        newsTitleKey = o.optInt("newsTitleKey", d.newsTitleKey),
        newsHoursLeft = o.optInt("newsHoursLeft", d.newsHoursLeft),
        newsTotalHours = o.optInt("newsTotalHours", d.newsTotalHours),
        nextNewsDay = o.optInt("nextNewsDay", d.nextNewsDay),
        netOwnedCsv = o.optString("netOwnedCsv", d.netOwnedCsv),
        reputation = o.optDouble("reputation", d.reputation),
        // отметка дня по умолчанию нулевая: отсутствие ключа должно быть отличимо
        // от честно записанного значения, иначе старый сейв не пересчитать
        pressure = o.optDouble("pressure", 0.0),
        pressureDay = o.optInt("pressureDay", 0),
        statsShownDay = o.optInt("statsShownDay", 0),
        tapIncome = o.optDouble("tapIncome", 0.0),
        // ВАЖНО: дефолт здесь — пустая строка, а НЕ значение из DEFAULT.
        // Отсутствие ключа означает сейв старого формата, и множество должно
        // достроиться из legacy-индекса ниже (toOwnedSet), а не стать стартовым {0}.
        ownedHomesCsv = o.optString("ownedHomesCsv", ""),
        ownedCarsCsv = o.optString("ownedCarsCsv", ""),
        ownedTechsCsv = o.optString("ownedTechsCsv", ""),
        ownedHome = o.optInt("ownedHome", d.ownedHome),
        ownedCar = o.optInt("ownedCar", d.ownedCar),
        ownedTech = o.optInt("ownedTech", d.ownedTech),
        collectiblesRaw = o.optString("collectiblesRaw", d.collectiblesRaw),
        auctionRaw = o.optString("auctionRaw", d.auctionRaw),
        auctionNextGameH = o.optDouble("auctionNextGameH", d.auctionNextGameH),
        auctionSeed = o.optLong("auctionSeed", d.auctionSeed),
        lastTitleIdx = o.optInt("lastTitleIdx", d.lastTitleIdx),
        experiencesDoneCsv = o.optString("experiencesDoneCsv", d.experiencesDoneCsv),
        debt = o.optDouble("debt", d.debt),
        activatedCardTier = o.optInt("activatedCardTier", d.activatedCardTier),
        chronicleRaw = o.optString("chronicleRaw", d.chronicleRaw),
        museumRaw = o.optString("museumRaw", d.museumRaw),
        milestonesClaimed = o.optInt("milestonesClaimed", d.milestonesClaimed),
        statTaps = o.optLong("statTaps", d.statTaps),
        statTapEarned = o.optDouble("statTapEarned", d.statTapEarned),
        statAllTimeEarned = o.optDouble("statAllTimeEarned", d.statAllTimeEarned),
        statBestDayIncome = o.optDouble("statBestDayIncome", d.statBestDayIncome),
        statDaysPrevLives = o.optInt("statDaysPrevLives", d.statDaysPrevLives),
        statBullionEarned = o.optLong("statBullionEarned", d.statBullionEarned),
        statLifeItems = o.optInt("statLifeItems", d.statLifeItems),
        statBizLevels = o.optInt("statBizLevels", d.statBizLevels),
        statBestTitle = o.optInt("statBestTitle", d.statBestTitle),
        phaseIndex = o.optInt("phaseIndex", d.phaseIndex),
        phaseEndGameH = o.optDouble("phaseEndGameH", d.phaseEndGameH),
        gameHours = o.optDouble("gameHours", d.gameHours),
        startDateMillis = o.optLong("startDateMillis", d.startDateMillis),
        tutorialStep = o.optInt("tutorialStep", d.tutorialStep),
        hintsSeenCsv = o.optString("hintsSeenCsv", d.hintsSeenCsv),
        seenTabsCsv = o.optString("seenTabsCsv", d.seenTabsCsv),
        announcedCsv = o.optString("announcedCsv", d.announcedCsv),
        currencyCode = o.optString("currencyCode", d.currencyCode),
        playerName = o.optString("playerName", d.playerName),
        onboarded = o.optBoolean("onboarded", d.onboarded),
        lastSeenMillis = o.optLong("lastSeenMillis", d.lastSeenMillis)
            )
        } catch (t: Throwable) {
            null
        }
    }

    /** Дефолтный Entity — источник значений по умолчанию для отсутствующих полей. */
    private val DEFAULT: GameEntity by lazy { ru.capital.idle.core.game.GameState().toEntity() }
}
