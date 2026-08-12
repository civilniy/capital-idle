package ru.capital.idle.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.Exchange
import ru.capital.idle.core.game.Investments
import ru.capital.idle.core.game.Auction
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
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
    /**
     * Накопители окупаемости предприятий, по одной записи на предприятие в том же порядке,
     * что и в [enterprisesRaw]: `вложено:выручка:зарплата:деньУчёта:показаннаяПрибыль`.
     *
     * Отдельной колонкой, а не полями внутри `enterprisesRaw`: там последним полем идёт
     * название, и читается оно с `limit`, чтобы двоеточие внутри названия не ломало разбор.
     * Дописать что-то после названия нельзя, а менять формат старой колонки — чинить то,
     * что не ломалось.
     *
     * **Пустая строка означает сохранение, сделанное до появления учёта.** Именно поэтому
     * при отсутствии предприятий колонка всё равно непустая (разделители на месте): иначе
     * «нет предприятий» было бы не отличить от «истории нет».
     */
    val enterpriseStatsRaw: String,
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
    // давление элит: хранимая величина дня, а не расчёт на лету
    val pressure: Double,
    val pressureDay: Int,
    val statsShownDay: Int,
    /** Доход в день на момент последнего снимка — вход для награды за тап. */
    val tapIncome: Double,
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
    /** Активные торги одной строкой через «|»; пусто — торгов нет. */
    val auctionRaw: String,
    val auctionNextGameH: Double,
    val auctionSeed: Long,
    val lastTitleIdx: Int,
    val experiencesDoneCsv: String,
    val debt: Double,
    val activatedCardTier: Int,
    val chronicleRaw: String,
    val museumRaw: String,
    val milestonesClaimed: Int,
    /** Максимальный достигнутый капитал: храповик порогов разделов и титулов. */
    val peakNetWorth: Double,
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

private fun List<List<Enterprise>>.entStatsCsv(): String =
    joinToString(";") { ind ->
        ind.joinToString("|") {
            "${it.invested}:${it.earned}:${it.salaryPaid}:${it.statsSinceDay}:${it.profitShown}"
        }
    }

/**
 * Наложить накопители из отдельной колонки на уже разобранный список предприятий.
 *
 * Пустая строка — сохранение до появления учёта: истории в нём нет и взять её неоткуда.
 * Восстанавливать её оценкой «уровень × цена» нельзя — это были бы выдуманные числа.
 * Поэтому накопители начинаются с нуля, а предприятие помечается днём, с которого учёт
 * пошёл: карточка честно скажет, что числа неполные.
 */
private fun List<List<Enterprise>>.withEntStats(raw: String, todayDay: Int): List<List<Enterprise>> {
    if (raw.isEmpty()) return map { ind -> ind.map { it.copy(statsSinceDay = todayDay) } }
    val parts = raw.split(";")
    return mapIndexed { i, ind ->
        val chunk = parts.getOrNull(i) ?: ""
        val recs = if (chunk.isEmpty()) emptyList() else chunk.split("|")
        ind.mapIndexed { j, e ->
            val f = recs.getOrNull(j)?.split(":") ?: return@mapIndexed e.copy(statsSinceDay = todayDay)
            val earned = f.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val salaryPaid = f.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            e.copy(
                invested = f.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                earned = earned,
                salaryPaid = salaryPaid,
                statsSinceDay = f.getOrNull(3)?.toIntOrNull() ?: Enterprise.STATS_FROM_START,
                // пятого поля нет в сохранениях до появления снимка — берём прибыль как есть,
                // иначе карточка до первой смены суток показывала бы ноль
                profitShown = f.getOrNull(4)?.toDoubleOrNull() ?: (earned - salaryPaid)
            )
        }
    }
}

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
 * Активные торги одной строкой: поля через «|» в фиксированном порядке.
 * Имя соперника хранится индексом, поэтому строка целиком числовая, кроме id предмета —
 * разделитель в ней встретиться не может.
 */
private fun Auction?.aucRaw(): String {
    val a = this ?: return ""
    return listOf(
        a.itemId, a.tierOrdinal, a.startGameH, a.endsGameH, a.bid, a.bids, a.playerBids,
        if (a.playerLeads) 1 else 0, a.playerEscrow, a.rivalNameIdx, a.rivalLimit,
        a.rivalStepFrac, if (a.rivalReplies) 1 else 0, a.rivalReplyAtGameH
    ).joinToString("|")
}

private fun String.toAuction(): Auction? {
    if (isBlank()) return null
    val f = split("|")
    if (f.size < 14) return null
    val id = f[0]
    // предмета может уже не быть в каталоге — тогда лот бессмысленен
    if (Collectibles.byId(id) == null) return null
    return try {
        Auction(
            itemId = id,
            tierOrdinal = f[1].toInt(),
            startGameH = f[2].toDouble(),
            endsGameH = f[3].toDouble(),
            bid = f[4].toDouble(),
            bids = f[5].toInt(),
            playerBids = f[6].toInt(),
            playerLeads = f[7] == "1",
            playerEscrow = f[8].toDouble(),
            rivalNameIdx = f[9].toInt(),
            rivalLimit = f[10].toDouble(),
            rivalStepFrac = f[11].toDouble(),
            rivalReplies = f[12] == "1",
            rivalReplyAtGameH = f[13].toDouble()
        )
    } catch (t: Throwable) {
        null
    }
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
    enterpriseStatsRaw = enterprises.entStatsCsv(),
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
    pressure = pressure, pressureDay = pressureDay, statsShownDay = statsShownDay,
    tapIncome = tapIncome,
    ownedHomesCsv = ownedHomes.iSetCsv(),
    ownedCarsCsv = ownedCars.iSetCsv(),
    ownedTechsCsv = ownedTechs.iSetCsv(),
    ownedHome = ownedHome, ownedCar = ownedCar, ownedTech = ownedTech,
    collectiblesRaw = collectibles.collCsv(),
    auctionRaw = auction.aucRaw(),
    auctionNextGameH = auctionNextGameH,
    auctionSeed = auctionSeed,
    lastTitleIdx = lastTitleIdx,
    experiencesDoneCsv = experiencesDone.sCsv(), debt = debt, activatedCardTier = activatedCardTier,
    chronicleRaw = chronicle.recCsv(), museumRaw = museum.recCsv(),
    milestonesClaimed = milestonesClaimed, peakNetWorth = peakNetWorth,
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
    enterprises = enterprisesRaw.toEnterprises(Industries.count)
        .withEntStats(enterpriseStatsRaw, (gameHours / 24.0).toInt() + 1),
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
    pressure = pressure, pressureDay = pressureDay, statsShownDay = statsShownDay,
    tapIncome = tapIncome,
    ownedHomes = ownedHomesCsv.toOwnedSet(ownedHome, Lifestyle.home),
    ownedCars = ownedCarsCsv.toOwnedSet(ownedCar, Lifestyle.car),
    ownedTechs = ownedTechsCsv.toOwnedSet(ownedTech, Lifestyle.tech),
    collectibles = collectiblesRaw.toCollectibles(),
    auction = auctionRaw.toAuction(),
    auctionNextGameH = auctionNextGameH,
    auctionSeed = auctionSeed,
    lastTitleIdx = lastTitleIdx,
    experiencesDone = experiencesDoneCsv.toSet(), debt = debt, activatedCardTier = activatedCardTier,
    chronicle = chronicleRaw.toRecList(), museum = museumRaw.toRecList(),
    milestonesClaimed = milestonesClaimed, peakNetWorth = peakNetWorth,
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
    // код валюты приводим к существующему: в старом сейве может лежать удалённый EUR или CNY,
    // и без этого он молча дожил бы до следующей записи файла
    currencyCode = Currency.fromCode(currencyCode).code,
    playerName = playerName, onboarded = onboarded,
    lastSeenMillis = lastSeenMillis
).let { st ->
    // сохранение, сделанное до того, как давление стало храниться: считаем его здесь,
    // иначе до первой смены игрового дня доход бизнесов был бы завышен
    if (st.pressureDay > 0) st else GameMath.withPressure(st)
}.let { st ->
    // сохранение, сделанное до того, как доход для тапа стал храниться: считаем его здесь,
    // иначе до первой смены игрового дня тап давал бы минимальный доллар
    if (st.tapIncome > 0.0) st else st.copy(tapIncome = GameMath.incomePerDay(st))
}.let { st ->
    // храповик капитала начинается с текущего капитала: в сохранении, сделанном до его
    // появления, накопителя нет, а пороги разделов и титулов уже сравниваются с ним
    st.copy(peakNetWorth = maxOf(st.peakNetWorth, GameMath.netWorth(st)))
}
