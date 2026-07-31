package ru.capital.idle.screenshot

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.GameViewModel

/**
 * Состояния игры для осмотра вёрстки: начало, середина, поздняя игра и жизнь в долг.
 * Только для скриншот-тестов — в приложении эти данные не используются.
 */
object AuditStates {

    /** Начало: денег мало, ничего не куплено, все списки пустые. */
    val early = GameState(
        money = 120.0,
        totalEarned = 340.0,
        jobId = "courier",
        gameHours = 3 * 24.0 + 9,
        playerName = "Игрок",
        onboarded = true,
        tutorialStep = Onboarding.DONE,
        currencyCode = "RUB"
    )

    /**
     * Начало, но окружение уже открыто: раздел «Окружение» появляется с $5 000 заработка.
     * Нужно, чтобы снять именно пустой список знакомств — при [early] экран молча
     * возвращается на вкладку курсов, и пустое состояние остаётся неснятым.
     */
    val earlyNetUnlocked = early.copy(
        money = 6_400.0,
        totalEarned = 12_000.0
    )

    /** Середина: миллионы, часть предметов куплена, часть отраслей открыта. */
    val mid: GameState = run {
        val ent = MutableList(Industries.count) { emptyList<Enterprise>() }
        ent[0] = listOf(Enterprise(3, Manager.MANAGER.ordinal, "Лавка у дома"), Enterprise(2, -1, "Точка"))
        ent[1] = listOf(Enterprise(2, Manager.PRO.ordinal, "Кофейня на углу"))
        GameState(
            money = 4_800_000.0,
            totalEarned = 21_000_000.0,
            bullion = 3L,
            pIncome = 1,
            jobId = "fin",
            bizH = 3,
            enterprises = ent,
            eduDone = setOf("school", "sales", "acc", "lead"),
            studyingId = "uni",
            studyProgress = 22.0,
            investValues = listOf(250_000.0, 900_000.0, 0.0),
            investCosts = listOf(250_000.0, 900_000.0, 0.0),
            stockPrices = Exchange.stocks.map { it.basePrice * 1.08 },
            stockQty = listOf(1_200.0, 400.0),
            stockAvg = listOf(131.0, 92.0),
            netOwned = setOf("mentor"),
            reputation = 41.0,
            ownedHomes = Lifestyle.ladderSet(2),
            ownedCars = Lifestyle.ladderSet(3),
            ownedTechs = Lifestyle.ladderSet(2),
            collectibles = mapOf("litho" to 51_200.0, "violin" to 640_000.0),
            experiencesDone = setOf("dubai"),
            milestonesClaimed = 3,
            statTaps = 840L,
            gameHours = 220 * 24.0 + 14,
            playerName = "Игрок",
            onboarded = true,
            tutorialStep = Onboarding.DONE,
            currencyCode = "RUB",
            chronicle = listOf(
                Chronicle.entry(1, "start"),
                Chronicle.entry(4, "job", "courier"),
                Chronicle.entry(31, "edu", "school"),
                Chronicle.entry(58, "biz", "trade:1"),
                Chronicle.entry(140, "own", "home:2")
            )
        )
    }

    /** Поздняя игра: триллионы, всё куплено, максимальные значения. */
    val late: GameState = run {
        val ent = List(Industries.count) { i ->
            val ind = Industries.all[i]
            List(BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY) { n ->
                Enterprise(ind.levels.lastIndex, Manager.TOP.ordinal, "Актив ${n + 1}")
            }
        }
        GameState(
            money = 3_400_000_000_000.0,
            totalEarned = 28_000_000_000_000.0,
            bullion = 4_820L,
            pIncome = 9, pNegotiator = 7, pStart = 5, pStudy = 6, pSafe = 4,
            jobId = "director",
            bizH = 6,
            enterprises = ent,
            eduDone = Education.allCourses.map { it.id }.toSet(),
            investValues = listOf(9.0e11, 1.4e12, 2.2e12),
            investCosts = listOf(9.0e11, 1.4e12, 2.2e12),
            stockPrices = Exchange.stocks.map { it.basePrice * 1.42 },
            stockQty = listOf(9_400_000.0, 6_100_000.0),
            stockAvg = listOf(118.0, 74.0),
            netOwned = setOf("mentor", "club", "charity"),
            reputation = 100.0,
            ownedHomes = Lifestyle.ladderSet(Lifestyle.home.items.lastIndex),
            ownedCars = Lifestyle.ladderSet(Lifestyle.car.items.lastIndex),
            ownedTechs = Lifestyle.ladderSet(Lifestyle.tech.items.lastIndex),
            collectibles = Collectibles.all.associate { it.id to it.basePrice },
            experiencesDone = Lifestyle.experiences.map { it.id }.toSet(),
            activatedCardTier = CardTier.entries.lastIndex,
            milestonesClaimed = Milestones.all.size,
            statTaps = 184_000L,
            statAllTimeEarned = 9.4e13,
            statBestDayIncome = 8.1e11,
            statDaysPrevLives = 12_400,
            statBullionEarned = 9_100L,
            statLifeItems = 26,
            statBizLevels = 480,
            statBestTitle = Lifestyle.titles.lastIndex,
            lastTitleIdx = Lifestyle.titles.lastIndex,
            gameHours = 3_500 * 24.0 + 19,
            playerName = "Магнат",
            onboarded = true,
            tutorialStep = Onboarding.DONE,
            currencyCode = "RUB",
            museum = listOf(
                Museum.entry(1, 900, 4.2e9, 6, 4, 6),
                Museum.entry(2, 1400, 3.1e11, 7, 6, 8)
            )
        )
    }

    /** Жизнь не по средствам: долг, отрицательный чистый доход. */
    val debt = mid.copy(
        money = 0.0,
        debt = 420_000_000.0,
        jobId = "",
        enterprises = List(Industries.count) { emptyList() },
        investValues = List(Investments.COUNT) { 0.0 },
        stockQty = List(Exchange.COUNT) { 0.0 },
        ownedHomes = Lifestyle.ladderSet(5),
        ownedCars = Lifestyle.ladderSet(7),
        ownedTechs = Lifestyle.ladderSet(5)
    )

    /**
     * ViewModel с подставленным состоянием.
     *
     * Экраны игры принимают GameViewModel, а он тянет за собой Room и игровой цикл.
     * Поэтому цикл останавливается, а состояние подставляется в приватный StateFlow
     * рефлексией: это тестовая обвязка, приложение ради снимков не переделывалось.
     */
    fun viewModelWith(state: GameState): GameViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        vm.onAppBackground()   // остановить игровой цикл, иначе кадр «дрожит» между снимками
        val field = GameViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<GameState>).value = state
        return vm
    }
}
