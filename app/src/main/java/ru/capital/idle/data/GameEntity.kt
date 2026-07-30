package ru.capital.idle.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.Exchange
import ru.capital.idle.core.game.Investments
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.Lifestyle

@Entity(tableName = "game_state")
data class GameEntity(
    @PrimaryKey val id: Int = 0,
    val money: Double,
    val totalEarned: Double,
    val bullion: Long,
    val pIncome: Int,
    val pNegotiator: Int,
    val pStart: Int,
    val pStudy: Int,
    val pSafe: Int,
    val sleepH: Int,
    val workH: Int,
    val bizH: Int,
    val jobId: String,
    val enterprisesRaw: String,
    val eduDoneCsv: String,
    val studyingId: String,
    val studyProgress: Double,
    val investValuesCsv: String,
    val investCostsCsv: String,
    val capitalizeMask: Int,
    val boostEndsAtMillis: Long,
    val stockPricesCsv: String,
    val stockQtyCsv: String,
    val stockAvgCsv: String,
    val stockHour: Double,
    val newsStockIndex: Int,
    val newsGood: Boolean,
    val newsStrength: Double,
    val newsTitleKey: Int,
    val newsHoursLeft: Int,
    val newsTotalHours: Int,
    val nextNewsDay: Int,
    val netOwnedCsv: String,
    val reputation: Double,
    // множества купленных предметов — источник истины
    val ownedHomesCsv: String,
    val ownedCarsCsv: String,
    val ownedTechsCsv: String,
    // legacy-индексы «текущего уровня»: пишутся как максимум из множества и читаются
    // только старыми сейвами, где множеств ещё нет
    val ownedHome: Int,
    val ownedCar: Int,
    val ownedTech: Int,
    /** Коллекция в формате "id:цена,id:цена". */
    val collectiblesRaw: String,
    val lastTitleIdx: Int,
    val experiencesDoneCsv: String,
    val debt: Double,
    val activatedCardTier: Int,
    val chronicleRaw: String,
    val museumRaw: String,
    val milestonesClaimed: Int,
    val statTaps: Long,
    val statTapEarned: Double,
    val statAllTimeEarned: Double,
    val statBestDayIncome: Double,
    val statDaysPrevLives: Int,
    val statBullionEarned: Long,
    val statLifeItems: Int,
    val statBizLevels: Int,
    val statBestTitle: Int,
    val phaseIndex: Int,
    val phaseEndGameH: Double,
    val gameHours: Double,
    val startDateMillis: Long,
    val tutorialStep: Int,
    val hintsSeenCsv: String,
    val seenTabsCsv: String,
    val announcedCsv: String,
    val currencyCode: String,
    val playerName: String,
    val onboarded: Boolean,
    val lastSeenMillis: Long
)

private fun List<Double>.dCsv() = joinToString(",")
private fun List<Int>.iCsv() = joinToString(",")

// экранирование названия: разделители CSV (; | :) не должны ломать формат
private fun String.escName(): String =
    replace("\\", "\\b").replace(";", "\\s").replace("|", "\\p").replace(":", "\\c")
private fun String.unescName(): String =
    replace("\\c", ":").replace("\\p", "|").replace("\\s", ";").replace("\\b", "\\")

private fun List<List<Enterprise>>.entCsv(): String =
    joinToString(";") { ind -> ind.joinToString("|") { "${it.level}:${it.managerOrdinal}:${it.name.escName()}" } }

private fun String.toEnterprises(nIndustries: Int): List<List<Enterprise>> {
    val parts = if (isEmpty()) emptyList() else split(";")
    return List(nIndustries) { i ->
        val chunk = parts.getOrNull(i) ?: ""
        if (chunk.isEmpty()) emptyList()
        else chunk.split("|").mapNotNull { e ->
            val f = e.split(":", limit = 3)
            val lv = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val mg = f.getOrNull(1)?.toIntOrNull() ?: -1
            val nm = f.getOrNull(2)?.unescName() ?: ""   // старые сейвы без названия → пусто
            Enterprise(lv, mg, nm)
        }
    }
}
private fun Set<String>.sCsv() = joinToString(",")
private fun String.toDoubles(size: Int): List<Double> {
    val p = if (isBlank()) emptyList() else split(",").mapNotNull { it.trim().toDoubleOrNull() }
    return List(size) { p.getOrElse(it) { 0.0 } }
}
private fun String.toInts(size: Int): List<Int> {
    val p = if (isBlank()) emptyList() else split(",").mapNotNull { it.trim().toIntOrNull() }
    return List(size) { p.getOrElse(it) { 0 } }
}
private fun List<String>.recCsv() = joinToString(";;")
private fun String.toRecList(): List<String> =
    if (isBlank()) emptyList() else split(";;").filter { it.isNotEmpty() }

private fun String.toSet(): Set<String> =
    if (isBlank()) emptySet() else split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

private fun Set<Int>.iSetCsv() = sorted().joinToString(",")

/** Коллекция: "id:цена,id:цена". Id предметов латинские, разделители в них не встречаются. */
private fun Map<String, Double>.collCsv(): String =
    entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

private fun String.toCollectibles(): Map<String, Double> {
    if (isBlank()) return emptyMap()
    val out = LinkedHashMap<String, Double>()
    split(",").forEach { rec ->
        val f = rec.split(":", limit = 2)
        val id = f.getOrNull(0)?.trim().orEmpty()
        val paid = f.getOrNull(1)?.trim()?.toDoubleOrNull()
        // мусор и неизвестные предметы пропускаем: каталог мог измениться между версиями
        if (id.isNotEmpty() && paid != null && Collectibles.byId(id) != null) out[id] = paid
    }
    return out
}

/**
 * Множество купленных предметов. Пустая строка = сейв старого формата, где
 * хранился только индекс уровня: восстанавливаем лестницу {0..legacyMaxIdx}.
 * Результат чистится по каталогу категории, чтобы мусорный индекс из
 * повреждённого файла не дошёл до состояния игры.
 */
private fun String.toOwnedSet(legacyMaxIdx: Int, cat: Lifestyle.Category): Set<Int> {
    val parsed = if (isBlank()) emptySet()
        else split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    val raw = if (parsed.isEmpty()) Lifestyle.ladderSet(legacyMaxIdx) else parsed
    return Lifestyle.sanitizeOwned(cat, raw)
}

fun GameState.toEntity() = GameEntity(
    id = 0,
    money = money, totalEarned = totalEarned, bullion = bullion,
    pIncome = pIncome, pNegotiator = pNegotiator, pStart = pStart, pStudy = pStudy, pSafe = pSafe,
    sleepH = sleepH, workH = workH, bizH = bizH,
    jobId = jobId,
    enterprisesRaw = enterprises.entCsv(),
    eduDoneCsv = eduDone.sCsv(),
    studyingId = studyingId, studyProgress = studyProgress,
    investValuesCsv = investValues.dCsv(), investCostsCsv = investCosts.dCsv(),
    capitalizeMask = capitalizeMask,
    boostEndsAtMillis = boostEndsAtMillis,
    stockPricesCsv = stockPrices.dCsv(),
    stockQtyCsv = stockQty.dCsv(),
    stockAvgCsv = stockAvg.dCsv(),
    stockHour = stockHour, newsStockIndex = newsStockIndex, newsGood = newsGood,
    newsStrength = newsStrength, newsTitleKey = newsTitleKey,
    newsHoursLeft = newsHoursLeft, newsTotalHours = newsTotalHours, nextNewsDay = nextNewsDay,
    netOwnedCsv = netOwned.sCsv(),
    reputation = reputation,
    ownedHomesCsv = ownedHomes.iSetCsv(),
    ownedCarsCsv = ownedCars.iSetCsv(),
    ownedTechsCsv = ownedTechs.iSetCsv(),
    ownedHome = ownedHome, ownedCar = ownedCar, ownedTech = ownedTech,
    collectiblesRaw = collectibles.collCsv(),
    lastTitleIdx = lastTitleIdx,
    experiencesDoneCsv = experiencesDone.sCsv(), debt = debt, activatedCardTier = activatedCardTier,
    chronicleRaw = chronicle.recCsv(), museumRaw = museum.recCsv(),
    milestonesClaimed = milestonesClaimed,
    statTaps = statTaps, statTapEarned = statTapEarned,
    statAllTimeEarned = statAllTimeEarned, statBestDayIncome = statBestDayIncome,
    statDaysPrevLives = statDaysPrevLives, statBullionEarned = statBullionEarned,
    statLifeItems = statLifeItems, statBizLevels = statBizLevels, statBestTitle = statBestTitle,
    phaseIndex = phaseIndex, phaseEndGameH = phaseEndGameH,
    gameHours = gameHours, startDateMillis = startDateMillis,
    tutorialStep = tutorialStep,
    hintsSeenCsv = hintsSeen.sCsv(),
    seenTabsCsv = seenTabs.sCsv(),
    announcedCsv = announced.sCsv(),
    currencyCode = currencyCode, playerName = playerName, onboarded = onboarded,
    lastSeenMillis = lastSeenMillis
)

fun GameEntity.toState() = GameState(
    money = money, totalEarned = totalEarned, bullion = bullion,
    pIncome = pIncome, pNegotiator = pNegotiator, pStart = pStart, pStudy = pStudy, pSafe = pSafe,
    sleepH = sleepH, workH = workH, bizH = bizH,
    jobId = jobId,
    enterprises = enterprisesRaw.toEnterprises(Industries.count),
    eduDone = eduDoneCsv.toSet(),
    studyingId = studyingId, studyProgress = studyProgress,
    investValues = investValuesCsv.toDoubles(Investments.COUNT),
    investCosts = investCostsCsv.toDoubles(Investments.COUNT),
    capitalizeMask = capitalizeMask,
    boostEndsAtMillis = boostEndsAtMillis,
    stockPrices = stockPricesCsv.toDoubles(Exchange.COUNT).mapIndexed { i, v ->
        if (v <= 0.0) Exchange.stocks[i].basePrice else v
    },
    stockQty = stockQtyCsv.toDoubles(Exchange.COUNT),
    stockAvg = stockAvgCsv.toDoubles(Exchange.COUNT),
    stockHour = stockHour, newsStockIndex = newsStockIndex, newsGood = newsGood,
    newsStrength = newsStrength, newsTitleKey = newsTitleKey,
    newsHoursLeft = newsHoursLeft, newsTotalHours = newsTotalHours, nextNewsDay = nextNewsDay,
    netOwned = netOwnedCsv.toSet(),
    reputation = reputation,
    ownedHomes = ownedHomesCsv.toOwnedSet(ownedHome, Lifestyle.home),
    ownedCars = ownedCarsCsv.toOwnedSet(ownedCar, Lifestyle.car),
    ownedTechs = ownedTechsCsv.toOwnedSet(ownedTech, Lifestyle.tech),
    collectibles = collectiblesRaw.toCollectibles(),
    lastTitleIdx = lastTitleIdx,
    experiencesDone = experiencesDoneCsv.toSet(), debt = debt, activatedCardTier = activatedCardTier,
    chronicle = chronicleRaw.toRecList(), museum = museumRaw.toRecList(),
    milestonesClaimed = milestonesClaimed,
    statTaps = statTaps, statTapEarned = statTapEarned,
    statAllTimeEarned = statAllTimeEarned, statBestDayIncome = statBestDayIncome,
    statDaysPrevLives = statDaysPrevLives, statBullionEarned = statBullionEarned,
    statLifeItems = statLifeItems, statBizLevels = statBizLevels, statBestTitle = statBestTitle,
    phaseIndex = phaseIndex, phaseEndGameH = phaseEndGameH,
    gameHours = gameHours, startDateMillis = startDateMillis,
    tutorialStep = tutorialStep,
    hintsSeen = hintsSeenCsv.toSet(),
    seenTabs = seenTabsCsv.toSet().ifEmpty { setOf("main") },
    announced = announcedCsv.toSet(),
    currencyCode = currencyCode, playerName = playerName, onboarded = onboarded,
    lastSeenMillis = lastSeenMillis
)
