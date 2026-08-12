package ru.capital.idle.core.game

/**
 * Детерминированная часть игрового цикла, вынесенная из `GameViewModel`.
 *
 * Здесь лежит всё, что не зависит ни от Android, ни от случайности: деньги и долг, счётчики
 * заработка, накопители предприятий, учёба, репутация, капитализация вкладов, фаза рынка,
 * вехи, храповик капитала, титул и хроника. Биржа (её цены разыгрываются генератором),
 * торги, анонсы разделов и шаги гида остались в `GameViewModel` — они либо случайны, либо
 * обращаются к UI.
 *
 * Вынос сделан ради автоматической симуляции: прогнать тысячи игровых дней на чистой JVM
 * можно только тем же кодом, которым живёт игра, — копия формул рано или поздно разойдётся
 * с оригиналом. Логика при переносе не менялась, порядок действий сохранён.
 */
object GameLoop {

    /**
     * То, что цикл помнит между тиками, но чего нет в состоянии.
     *
     * Капитализация вкладов начисляется раз в игровые сутки, и отметка дня для неё живёт
     * в памяти процесса, а не в сохранении: так было до выноса, так и осталось.
     */
    data class Cursor(val lastInvestDay: Long = -1L)

    /** Результат шага: новое состояние, сдвинутый курсор и события для хроники. */
    data class Step(
        val state: GameState,
        val cursor: Cursor,
        val events: List<Pair<String, String>> = emptyList()
    )

    /**
     * Деньги, долг, счётчики заработка, накопители предприятий, учёба, репутация
     * и капитализация вкладов за `dtDays` игровых дней.
     */
    fun economy(state: GameState, dtDays: Double, cursor: Cursor): Step {
        var s = state
        val gameH = s.gameHours + dtDays * 24.0

        // доход минус содержание имущества = чистый доход
        val incD = GameMath.incomePerDay(s)
        val upkeepD = Lifestyle.dailyUpkeep(s) + GameMath.managersSalaryPerDay(s)
        // доход вкладов с включённой капитализацией не идёт на карту, а реинвестируется в тело
        val tierBonusCap = CardTier.entries.getOrElse(s.activatedCardTier) { CardTier.CLASSIC }.passiveBonus
        var capIncomeD = 0.0
        Asset.entries.forEach { a ->
            if (s.capitalizeMask and (1 shl a.ordinal) != 0) {
                val v = s.investValues.getOrElse(a.ordinal) { 0.0 }
                capIncomeD += v * Investments.rate(a, s.eduDone) * (1.0 + tierBonusCap)
            }
        }
        val netD = (incD - upkeepD - capIncomeD) * GameMath.boostMult(s)   // на карту, удваивается рекламой
        var money = s.money + netD * dtDays
        // вехи/титулы — по валовому доходу, с бустом. Тем же приростом идёт и счётчик
        // всех жизней: выражение одно на оба, чтобы они не разошлись
        val earnedTick = GameMath.earnedDelta(s, dtDays)
        val total = s.totalEarned + earnedTick
        var debt = s.debt

        // ушли в минус — копится долг (с процентом); гасится из плюсового баланса
        if (money < 0.0) { debt += -money; money = 0.0 }
        if (debt > 0.0) {
            debt *= (1.0 + GameConfig.DEBT_RATE_PER_DAY * dtDays)
            if (money > 0.0) {
                val pay = minOf(money, debt)
                money -= pay; debt -= pay
            }
        }

        // накопители окупаемости: у каждого предприятия своя выручка и своя зарплата
        s = s.copy(enterprises = GameMath.accrueEnterpriseStats(s, dtDays))

        val events = mutableListOf<Pair<String, String>>()

        // учёба
        var studying = s.studyingId
        var prog = s.studyProgress
        var edu = s.eduDone
        if (studying.isNotEmpty()) {
            prog += GameMath.studyHoursPerDay(s) * dtDays
            val course = Education.byId(studying)
            if (course != null && prog >= course.durationHours) {
                edu = edu + course.id
                events += "edu" to course.id
                studying = ""
                prog = 0.0
            }
        }

        // репутация: прирост от окружения + дрейф к уровню, заданному соц-статусом (образ жизни)
        val statusTarget = (20.0 + Lifestyle.socialStatus(s) * 0.3).coerceAtMost(100.0)
        val repDrift = (statusTarget - s.reputation) * (0.02 * dtDays).coerceIn(0.0, 1.0)
        val rep = (s.reputation + Network.repPerDay(s.netOwned) * dtDays + repDrift).coerceIn(0.0, 100.0)

        // капитализация вкладов — раз в игровые сутки. Волатильности у накоплений нет:
        // депозит/облигации/недвижимость растут предсказуемо (как показано в «доход/день»).
        val curDay = (gameH / 24.0).toInt().toLong()
        val applyCap = curDay != cursor.lastInvestDay
        val values = if (!applyCap) s.investValues else s.investValues.mapIndexed { i, v ->
            val a = Asset.entries[i]
            if (v > 0 && (s.capitalizeMask and (1 shl i) != 0))
                v + v * Investments.rate(a, s.eduDone) * (1.0 + tierBonusCap)
            else v
        }

        return Step(
            state = s.copy(
                money = money, totalEarned = total, debt = debt,
                gameHours = gameH,
                studyingId = studying, studyProgress = prog, eduDone = edu,
                reputation = rep, investValues = values,
                statAllTimeEarned = s.statAllTimeEarned + earnedTick,
                statBestDayIncome = maxOf(s.statBestDayIncome, incD)
            ),
            cursor = if (applyCap) cursor.copy(lastInvestDay = curDay) else cursor,
            events = events
        )
    }

    /**
     * Фаза рынка, вехи, храповик капитала, титул, хроника и снятие величин дня.
     *
     * Отдельно от [economy], потому что между ними в игре стоит биржа: она двигает деньги
     * дивидендами, а её цены разыгрываются генератором и в симуляции не нужны.
     */
    fun progress(state: GameState, events: List<Pair<String, String>> = emptyList()): GameState {
        val s = state
        val gameH = s.gameHours
        val newEvents = events.toMutableList()

        // фаза рынка
        var phaseIdx = s.phaseIndex
        var phaseEnd = s.phaseEndGameH
        if (phaseEnd <= 0.0) phaseEnd = gameH + MarketPhase.randomLengthDays() * 24.0
        if (gameH >= phaseEnd) {
            phaseIdx = MarketPhase.next(s.phase).ordinal
            phaseEnd = gameH + MarketPhase.randomLengthDays() * 24.0
        }

        // вехи (по полному капиталу, а не по наличным)
        var claimed = s.milestonesClaimed
        var bull = s.bullion
        var bullEarned = 0L
        val worthNow = GameMath.netWorth(s)
        while (claimed < Milestones.all.size && worthNow >= Milestones.all[claimed].thresholdUsd) {
            bull += Milestones.all[claimed].rewardBullion
            bullEarned += Milestones.all[claimed].rewardBullion
            newEvents += "ms" to claimed.toString()
            claimed++
        }

        // максимальный достигнутый капитал: по нему открываются разделы и выдаются титулы.
        // Капитал умеет падать, а открытое и полученное назад не отбирается
        val peak = maxOf(s.peakNetWorth, worthNow)

        // титул
        var titleIdx = s.lastTitleIdx
        val nowTitle = Lifestyle.titleIndex(peak)
        if (nowTitle > titleIdx) {
            titleIdx = nowTitle
            newEvents += "title" to nowTitle.toString()
        }

        val next = s.copy(
            bullion = bull,
            phaseIndex = phaseIdx, phaseEndGameH = phaseEnd,
            milestonesClaimed = claimed,
            peakNetWorth = peak,
            lastTitleIdx = titleIdx,
            statBullionEarned = s.statBullionEarned + bullEarned,
            statBestTitle = maxOf(s.statBestTitle, titleIdx),
            chronicle = if (newEvents.isEmpty()) s.chronicle else {
                val day = GameMath.gameDay(gameH)
                (newEvents.map { Chronicle.entry(day, it.first, it.second) } + s.chronicle)
                    .take(Chronicle.MAX)
            }
        )

        // величины дня: давление элит, показанная прибыль предприятий, доход для тапа
        val newDay = GameMath.gameDay(next.gameHours) != next.statsShownDay
        val out = GameMath.dayShownOnNewDay(GameMath.pressureOnNewDay(next))
        // автовклад тоже величина дня: срабатывает на границе суток, а не каждый тик
        return if (newDay) AutoInvest.apply(out) else out
    }
}
