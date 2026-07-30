package ru.capital.idle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.capital.idle.core.game.*
import ru.capital.idle.data.AppDatabase
import ru.capital.idle.data.GameRepository
import kotlin.random.Random

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameRepository(AppDatabase.get(app).gameDao(), app)

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** Тройка: заработано в сейф / упущено сверх ёмкости / секунд отсутствия. */
    private val _offlineGain = MutableStateFlow(Triple(0.0, 0.0, 0.0))
    val offlineGain: StateFlow<Triple<Double, Double, Double>> = _offlineGain.asStateFlow()

    /** Очередь анонсов открытий разделов. */
    private val _announceQueue = MutableStateFlow<List<Onboarding.Announce>>(emptyList())
    val announceQueue: StateFlow<List<Onboarding.Announce>> = _announceQueue.asStateFlow()
    fun dismissAnnounce() { _announceQueue.update { it.drop(1) } }

    /** Активное новостное событие биржи для UI (или null). */
    /** Переключить капитализацию вклада (доход в тело vs на карту). */
    /** Награда за просмотр рекламы: +1 час удвоения, запас обрезается до 4 часов. */
    fun grantAdBoost() {
        _state.update { st ->
            val now = System.currentTimeMillis()
            val cur = (st.boostEndsAtMillis - now).coerceAtLeast(0L)   // текущий запас, мс
            val capped = (cur + 3_600_000L).coerceAtMost(4 * 3_600_000L)  // +1ч, потолок 4ч
            st.copy(boostEndsAtMillis = now + capped)
        }
        persistSoon()
    }

    /** Можно ли сейчас добрать час (запас ≤ 3 ч, чтобы +1ч не превысил потолок). */
    fun canGrantBoost(st: GameState): Boolean {
        val cur = (st.boostEndsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        return cur <= 3 * 3_600_000L
    }

    /** Через сколько мс откроется добор (запас стечёт до 3 ч). 0 если уже можно. */
    fun boostUnlockInMs(st: GameState): Long {
        val cur = (st.boostEndsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        return (cur - 3 * 3_600_000L).coerceAtLeast(0L)
    }

    fun toggleCapitalize(a: Asset) {
        _state.update { st ->
            val bit = 1 shl a.ordinal
            st.copy(capitalizeMask = st.capitalizeMask xor bit)
        }
        persistSoon()
    }

    fun activeStockEvent(s: GameState): StockEvent? =
        if (s.newsStockIndex < 0) null
        else StockEvent(s.newsStockIndex, s.newsGood, s.newsStrength,
            s.newsTitleKey, s.newsHoursLeft, s.newsTotalHours)

    /** Тир, ожидающий активации: текущий по капиталу выше активированного (или null, если нечего активировать). */
    fun pendingCardTier(s: GameState): CardTier? {
        val earned = CardTier.forWorth(GameMath.netWorth(s))
        return if (earned.ordinal > s.activatedCardTier) earned else null
    }

    /** Активировать новый тир карты: записываем его как активированный (включает бонус привилегии). */
    fun activateCardTier() {
        _state.update { st ->
            val earned = CardTier.forWorth(GameMath.netWorth(st))
            if (earned.ordinal > st.activatedCardTier)
                st.copy(activatedCardTier = earned.ordinal)
            else st
        }
        persistSoon()
    }

    /** История цен биржи (в памяти, для графиков): по бумаге до 60 точек. */
    private val _stockHistory = MutableStateFlow(List(Exchange.COUNT) { listOf<Double>() })
    val stockHistory: StateFlow<List<List<Double>>> = _stockHistory.asStateFlow()
    private var lastStockHour = -1L
    private var lastInvestDay = -1L   // волатильность вкладов применяется раз в игровые сутки

    private var loaded = false

    init {
        viewModelScope.launch {
            repo.load()?.let { saved ->
                var st = saved
                _state.value = st
                // оффлайн-доход за время отсутствия (при первом запуске процесса)
                applyOfflineProgress()
                st = _state.value
                // всё, что уже открыто к моменту загрузки, считаем объявленным (без показа)
                if (st.onboarded) {
                    val silent = Onboarding.announceIds.filter { Onboarding.unlocked(st, it) }
                    _state.value = st.copy(announced = st.announced + silent)
                }
            }
            loaded = true
            startLoop()
            startAutosave()
        }
    }

    /** Пересчёт прогресса за время, пока игра была закрыта/свёрнута. Двигает биржу и начисляет оффлайн-доход. */
    fun applyOfflineProgress() {
        var st = _state.value
        if (st.onboarded && st.lastSeenMillis > 0) {
            val elapsedSec = (System.currentTimeMillis() - st.lastSeenMillis) / 1000.0
            if (elapsedSec > 30) {
                // биржа жила без вас: цены сдвигаются по циклам на пропущенные часы
                val hoursAway = GameTime.gameHours(elapsedSec)
                val newHour = st.stockHour + hoursAway
                val rngJ = java.util.Random()
                st = st.copy(
                    stockHour = newHour,
                    stockPrices = Exchange.stocks.mapIndexed { i, stk ->
                        val center = Exchange.cyclePrice(stk, newHour)
                        (center * (1.0 + (rngJ.nextDouble() * 2 - 1) * stk.vol)).coerceAtLeast(1.0)
                    },
                    newsStockIndex = -1   // старое событие истекло, пока вас не было
                )
                val (gain, missed) = GameMath.offlineEarnings(st, elapsedSec)
                if (gain > 0.5) {
                    _offlineGain.value = Triple(gain, missed, elapsedSec)
                    st = st.copy(money = st.money + gain, totalEarned = st.totalEarned + gain)
                }
                // сдвигаем метку на текущий момент, чтобы повторный вызов (init + ON_START) не начислил снова
                st = st.copy(lastSeenMillis = System.currentTimeMillis())
                _state.value = st
            }
        }
    }

    // ===================== игровой цикл =====================

    private var loopJob: kotlinx.coroutines.Job? = null

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        val job = viewModelScope.launch {
            var lastMs = System.currentTimeMillis()
            while (true) {
                delay(100)
                // работает только актуальный цикл: если стартовал новый (после возврата из фона),
                // старый завершается, не начисляя — это убирает кратковременное дёрганье цифр
                if (loopJob !== coroutineContext[kotlinx.coroutines.Job]) break
                val now = System.currentTimeMillis()
                val dtReal = ((now - lastMs) / 1000.0).coerceAtMost(1.0)  // защита от разовых больших дельт
                lastMs = now
                if (!_state.value.onboarded) continue
                tick(dtReal)
            }
        }
        loopJob = job
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** Игра ушла в фон (свёрнута / другое приложение): стоп онлайн-цикла + фиксация метки времени. */
    fun onAppBackground() {
        stopLoop()
        // фиксируем момент ухода и в state (для корректного оффлайн-пересчёта при возврате), и в сейв
        _state.value = _state.value.copy(lastSeenMillis = System.currentTimeMillis())
        persist()
    }

    /** Игра вернулась на передний план: пересчитать оффлайн за время отсутствия + перезапустить цикл. */
    fun onAppForeground() {
        if (!loaded) return
        applyOfflineProgress()
        startLoop()
    }

    private fun tick(dtReal: Double) {
        val dtGameH = GameTime.gameHours(dtReal)
        val dtDays = dtGameH / 24.0
        _state.update { st ->
            var s = st
            val gameH = s.gameHours + dtGameH

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
            var total = s.totalEarned + incD * GameMath.boostMult(s) * dtDays   // вехи/титулы — по валовому, тоже с бустом
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

            val newEvents = mutableListOf<Pair<String, String>>()

            // учёба
            var studying = s.studyingId
            var prog = s.studyProgress
            var edu = s.eduDone
            if (studying.isNotEmpty()) {
                prog += GameMath.studyHoursPerDay(s) * dtDays
                val course = Education.byId(studying)
                if (course != null && prog >= course.durationHours) {
                    edu = edu + course.id
                    newEvents += "edu" to course.id
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
            val applyCap = curDay != lastInvestDay
            if (applyCap) lastInvestDay = curDay
            val values = if (!applyCap) s.investValues else s.investValues.mapIndexed { i, v ->
                val a = Asset.entries[i]
                if (v > 0 && (s.capitalizeMask and (1 shl i) != 0))
                    v + v * Investments.rate(a, s.eduDone) * (1.0 + tierBonusCap)
                else v
            }

            // биржа: циклы + новостные события + дивиденды
            var prices = s.stockPrices
            var stockHour = s.stockHour
            var money2 = money
            // событие
            var nStock = s.newsStockIndex
            var nGood = s.newsGood
            var nStrength = s.newsStrength
            var nKey = s.newsTitleKey
            var nLeft = s.newsHoursLeft
            var nTotal = s.newsTotalHours
            var nextNews = s.nextNewsDay

            val hourNow = gameH.toLong()
            if (lastStockHour < 0) lastStockHour = hourNow
            var hoursToTick = (hourNow - lastStockHour).coerceIn(0, 8)
            if (hoursToTick > 0) {
                lastStockHour = hourNow
                val rng = java.util.Random()
                while (hoursToTick > 0) {
                    stockHour += 1.0
                    // затухание активного события
                    val ev = if (nStock >= 0)
                        StockEvent(nStock, nGood, nStrength, nKey, nLeft, nTotal) else null
                    prices = prices.mapIndexed { i, _ ->
                        val center = Exchange.cyclePrice(Exchange.stocks[i], stockHour)
                        val noise = (rng.nextDouble() * 2 - 1) * Exchange.stocks[i].vol
                        val evMult = if (ev?.stockIndex == i) Exchange.eventMult(ev) else 1.0
                        (center * (1.0 + noise) * evMult).coerceAtLeast(1.0)
                    }
                    if (nStock >= 0) { nLeft--; if (nLeft <= 0) nStock = -1 }
                    hoursToTick--
                }
                _stockHistory.update { h ->
                    h.mapIndexed { i, list -> (list + prices[i]).takeLast(60) }
                }
            }

            // дивиденды раз в игровой день
            run {
                Exchange.stocks.forEachIndexed { i, st0 ->
                    if (st0.divPerDay > 0.0)
                        money2 += s.stockQty[i] * prices[i] * st0.divPerDay * dtDays
                }
            }
            money = money2

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
            var bullEarnedTick = 0L
            val worthNow = GameMath.netWorth(s.copy(money = money, debt = debt))
            while (claimed < Milestones.all.size && worthNow >= Milestones.all[claimed].thresholdUsd) {
                bull += Milestones.all[claimed].rewardBullion
                bullEarnedTick += Milestones.all[claimed].rewardBullion
                newEvents += "ms" to claimed.toString()
                claimed++
            }

            // титул
            var titleIdx = s.lastTitleIdx
            val nowTitle = Lifestyle.titleIndex(total)
            if (nowTitle > titleIdx) {
                titleIdx = nowTitle
                newEvents += "title" to nowTitle.toString()
            }

            // анонсы открывшихся разделов
            var announced = s.announced
            for (id in Onboarding.announceIds) {
                if (id !in announced) {
                    val probe = s.copy(money = money, totalEarned = total, eduDone = edu)
                    if (Onboarding.unlocked(probe, id)) {
                        announced = announced + id
                        Onboarding.announces[id]?.let { a ->
                            _announceQueue.update { q -> q + a }
                        }
                    }
                }
            }

            // онбординг: продвижение шага гида
            var tut = s.tutorialStep
            if (tut < Onboarding.steps.size) {
                val probe = s.copy(money = money, totalEarned = total, eduDone = edu, studyingId = studying)
                if (Onboarding.stepDone(probe)) {
                    tut += 1
                    if (tut >= Onboarding.steps.size) tut = Onboarding.DONE
                }
            }

            s.copy(
                money = money, totalEarned = total, bullion = bull,
                gameHours = gameH,
                studyingId = studying, studyProgress = prog, eduDone = edu,
                reputation = rep, investValues = values,
                stockPrices = prices,
                stockHour = stockHour,
                newsStockIndex = nStock, newsGood = nGood, newsStrength = nStrength,
                newsTitleKey = nKey, newsHoursLeft = nLeft, newsTotalHours = nTotal,
                nextNewsDay = nextNews,
                phaseIndex = phaseIdx, phaseEndGameH = phaseEnd,
                milestonesClaimed = claimed,
                lastTitleIdx = titleIdx,
                debt = debt,
                statAllTimeEarned = s.statAllTimeEarned + incD * dtDays,
                statBestDayIncome = maxOf(s.statBestDayIncome, incD),
                statBullionEarned = s.statBullionEarned + bullEarnedTick,
                statBestTitle = maxOf(s.statBestTitle, titleIdx),
                chronicle = if (newEvents.isEmpty()) s.chronicle else {
                    val day = (gameH / 24.0).toInt() + 1
                    (newEvents.map { Chronicle.entry(day, it.first, it.second) } + s.chronicle)
                        .take(Chronicle.MAX)
                },
                announced = announced,
                tutorialStep = tut
            )
        }
    }

    private fun startAutosave() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                persist()
            }
        }
    }

    fun persist() {
        if (!loaded) return
        val snapshot = _state.value.copy(lastSeenMillis = System.currentTimeMillis())
        viewModelScope.launch { repo.save(snapshot) }
    }

    // ===================== действия игрока =====================

    private fun GameState.withChronicle(code: String, param: String = ""): GameState {
        val day = (gameHours / 24.0).toInt() + 1
        val rec = Chronicle.entry(day, code, param)
        val list = (listOf(rec) + chronicle).take(Chronicle.MAX)
        return copy(chronicle = list)
    }

    fun tap() {
        _state.update {
            val r = GameMath.tapReward(it) * Prestige.negotiatorMult(it)
            it.copy(
                money = it.money + r, totalEarned = it.totalEarned + r,
                statTaps = it.statTaps + 1,
                statTapEarned = it.statTapEarned + r,
                statAllTimeEarned = it.statAllTimeEarned + r
            )
        }
    }

    fun setSchedule(sleep: Int, work: Int, biz: Int) {
        _state.update { st ->
            val sl = sleep.coerceIn(3, 9)
            val budget = 24 - sl + Lifestyle.carExtraHours(st)
            // что изменилось относительно текущего — то и ограничиваем остатком,
            // не трогая второй ползунок (симметрия: ни работа, ни бизнес не воруют часы)
            val workChanged = work != st.workH
            val w: Int
            val b: Int
            if (workChanged) {
                b = biz.coerceIn(0, budget)
                w = work.coerceIn(0, budget - b)   // работа упирается в остаток после бизнеса
            } else {
                w = work.coerceIn(0, budget)
                b = biz.coerceIn(0, budget - w)     // бизнес упирается в остаток после работы
            }
            st.copy(sleepH = sl, workH = w, bizH = b)
        }
    }

    fun setJob(id: String) {
        _state.update { st ->
            if (id.isEmpty()) {
                return@update if (st.jobId.isEmpty()) st
                else st.copy(jobId = "").withChronicle("quit")
            }
            val job = Jobs.byIdOrNull(id) ?: return@update st
            if (job.reqCourse != null && job.reqCourse !in st.eduDone) st
            else st.copy(jobId = job.id).withChronicle("job", job.id)
        }
    }

    /** Открыть новое предприятие в отрасли (стартовый уровень 0, лично управляемое). */
    fun openEnterprise(index: Int, name: String = "") {
        _state.update { st ->
            val ind = Industries.all.getOrNull(index) ?: return@update st
            if (!GameMath.canOpenEnterprise(st, index)) return@update st
            val req = ind.levels[0].reqCourse
            if (req != null && req !in st.eduDone) return@update st
            val cost = GameMath.openEnterpriseCost(st, index)
            if (st.money < cost) return@update st
            val finalName = name.trim().ifEmpty { EnterpriseNames.random(ind.id) }
            val lists = st.enterprises.toMutableList()
            // новое предприятие — в НАЧАЛО списка (показывается сверху, удобно сразу настроить)
            val newList = listOf(Enterprise(level = 0, managerOrdinal = -1, name = finalName)) +
                lists.getOrElse(index) { emptyList() }
            lists[index] = newList
            st.copy(money = st.money - cost, enterprises = lists,
                statBizLevels = st.statBizLevels + 1)
                .withChronicle("biz", "${ind.id}:open")
        }
        persistSoon()
    }

    /** Переименование предприятия (тап по названию). */
    fun renameEnterprise(index: Int, entIndex: Int, name: String) {
        val nm = name.trim()
        if (nm.isEmpty()) return
        _state.update { st ->
            val lists = st.enterprises.toMutableList()
            val list = lists.getOrElse(index) { return@update st }.toMutableList()
            val e = list.getOrNull(entIndex) ?: return@update st
            list[entIndex] = e.copy(name = nm)
            lists[index] = list
            st.copy(enterprises = lists)
        }
        persistSoon()
    }

    /** Улучшить конкретное предприятие (следующая ступень лестницы отрасли). */
    fun upgradeEnterprise(index: Int, entIndex: Int) {
        _state.update { st ->
            val ind = Industries.all.getOrNull(index) ?: return@update st
            val list = st.enterprises.getOrElse(index) { emptyList() }
            val e = list.getOrNull(entIndex) ?: return@update st
            val nextLv = e.level + 1
            if (nextLv >= ind.levels.size) return@update st
            val req = ind.levels[nextLv].reqCourse
            if (req != null && req !in st.eduDone) return@update st
            val cost = GameMath.upgradeEnterpriseCost(st, index, entIndex)
            if (st.money < cost) return@update st
            val lists = st.enterprises.toMutableList()
            val newList = list.toMutableList().also { it[entIndex] = e.copy(level = nextLv) }
            lists[index] = newList
            st.copy(money = st.money - cost, enterprises = lists,
                statBizLevels = st.statBizLevels + 1)
        }
        persistSoon()
    }

    /** Назначить управляющего (ordinal класса) на предприятие. */
    fun assignManager(index: Int, entIndex: Int, managerOrdinal: Int) {
        _state.update { st ->
            val list = st.enterprises.getOrElse(index) { emptyList() }
            val e = list.getOrNull(entIndex) ?: return@update st
            val lists = st.enterprises.toMutableList()
            lists[index] = list.toMutableList().also { it[entIndex] = e.copy(managerOrdinal = managerOrdinal) }
            st.copy(enterprises = lists)
        }
        persistSoon()
    }

    /** Снять управляющего (вернуть предприятие под личное управление). */
    fun fireManager(index: Int, entIndex: Int) {
        _state.update { st ->
            val list = st.enterprises.getOrElse(index) { emptyList() }
            val e = list.getOrNull(entIndex) ?: return@update st
            val lists = st.enterprises.toMutableList()
            lists[index] = list.toMutableList().also { it[entIndex] = e.copy(managerOrdinal = -1) }
            st.copy(enterprises = lists)
        }
        persistSoon()
    }

    fun startStudy(courseId: String) {
        _state.update { st ->
            val c = Education.byId(courseId) ?: return@update st
            if (c.id in st.eduDone || st.studyingId.isNotEmpty()) return@update st
            if (c.reqCourse != null && c.reqCourse !in st.eduDone) return@update st
            if (st.money < c.cost) return@update st
            st.copy(money = st.money - c.cost, studyingId = c.id, studyProgress = 0.0)
        }
        persistSoon()
    }

    fun invest(a: Asset, frac: Double) {
        _state.update { st ->
            if (a.reqCourse !in st.eduDone) return@update st
            val amt = (if (frac < 0) st.money else st.money * frac).coerceAtMost(st.money)
            if (amt <= 0.0) return@update st
            val i = a.ordinal
            val values = st.investValues.toMutableList().also { it[i] = it[i] + amt }
            val costs = st.investCosts.toMutableList().also { it[i] = it[i] + amt }
            st.copy(money = st.money - amt, investValues = values, investCosts = costs)
        }
    }

    fun sellAsset(a: Asset) {
        _state.update { st ->
            val i = a.ordinal
            val v = st.investValues[i]
            if (v <= 0.0) return@update st
            val values = st.investValues.toMutableList().also { it[i] = 0.0 }
            val costs = st.investCosts.toMutableList().also { it[i] = 0.0 }
            st.copy(money = st.money + v, investValues = values, investCosts = costs)
        }
    }

    fun buyStock(index: Int, frac: Double) {
        _state.update { st ->
            val stock = Exchange.stocks.getOrNull(index) ?: return@update st
            if (stock.reqCourse !in st.eduDone) return@update st
            val amt = (if (frac < 0) st.money else st.money * frac).coerceAtMost(st.money)
            val price = st.stockPrices[index]
            if (amt < price) return@update st
            val qty = amt / price
            val newQty = st.stockQty[index] + qty
            val newAvg = (st.stockAvg[index] * st.stockQty[index] + qty * price) / newQty
            st.copy(
                money = st.money - qty * price,
                stockQty = st.stockQty.toMutableList().also { it[index] = newQty },
                stockAvg = st.stockAvg.toMutableList().also { it[index] = newAvg }
            )
        }
    }

    fun sellStock(index: Int) {
        _state.update { st ->
            val qty = st.stockQty.getOrElse(index) { 0.0 }
            if (qty <= 0.0) return@update st
            val gain = qty * st.stockPrices[index]
            st.copy(
                money = st.money + gain,
                totalEarned = st.totalEarned + (gain - qty * st.stockAvg[index]).coerceAtLeast(0.0),
                stockQty = st.stockQty.toMutableList().also { it[index] = 0.0 },
                stockAvg = st.stockAvg.toMutableList().also { it[index] = 0.0 }
            )
        }
    }

    fun buyNetItem(id: String) {
        _state.update { st ->
            val item = Network.all.firstOrNull { it.id == id } ?: return@update st
            if (id in st.netOwned || st.money < item.cost) return@update st
            st.copy(money = st.money - item.cost, netOwned = st.netOwned + id)
                .withChronicle("net", id)
        }
        persistSoon()
    }

    fun prestige() {
        _state.update { st ->
            val gain = Prestige.gainFrom(st.totalEarned)
            if (gain < 1) return@update st
            val lifeNum = st.museum.size + 1
            val days = (st.gameHours / 24.0).toInt() + 1
            val memorial = Museum.entry(
                lifeNum, days, st.totalEarned,
                st.lastTitleIdx, st.ownedHome, st.ownedCar
            )
            st.copy(
                statDaysPrevLives = st.statDaysPrevLives + days,
                statBullionEarned = st.statBullionEarned + gain,
                money = Prestige.startMoney(st.pStart),
                totalEarned = 0.0,
                bullion = st.bullion + gain,
                jobId = "",
                enterprises = List(Industries.count) { emptyList() },
                investValues = List(Investments.COUNT) { 0.0 },
                investCosts = List(Investments.COUNT) { 0.0 },
                stockQty = List(Exchange.COUNT) { 0.0 },
                stockAvg = List(Exchange.COUNT) { 0.0 },
                studyingId = "", studyProgress = 0.0,
                gameHours = 8.0,
                startDateMillis = System.currentTimeMillis(),
                phaseIndex = 0, phaseEndGameH = 0.0,
                tutorialStep = Onboarding.DONE,
                ownedHomes = setOf(0), ownedCars = setOf(0), ownedTechs = setOf(0),
                lastTitleIdx = 0,
                museum = (listOf(memorial) + st.museum).take(20),
                chronicle = listOf(Chronicle.entry(1, "start")),
                announced = st.announced + Onboarding.announceIds
                // остаются: eduDone, netOwned, reputation, престиж-апгрейды, имя, валюта, вехи
            )
        }
        persist()
    }

    fun buyPrestigeUpgrade(u: PrestigeUpgrade) {
        _state.update { st ->
            val lvl = Prestige.levelOf(st, u)
            val cost = u.costAt(lvl)
            if (st.bullion < cost) return@update st
            val b = st.bullion - cost
            when (u) {
                PrestigeUpgrade.INCOME -> st.copy(bullion = b, pIncome = st.pIncome + 1)
                PrestigeUpgrade.NEGOTIATOR -> st.copy(bullion = b, pNegotiator = st.pNegotiator + 1)
                PrestigeUpgrade.START -> st.copy(bullion = b, pStart = st.pStart + 1)
                PrestigeUpgrade.STUDY -> st.copy(bullion = b, pStudy = st.pStudy + 1)
                PrestigeUpgrade.SAFE -> st.copy(bullion = b, pSafe = st.pSafe + 1)
            }
        }
        persistSoon()
    }

    fun markTabSeen(id: String) {
        _state.update { if (id in it.seenTabs) it else it.copy(seenTabs = it.seenTabs + id) }
    }

    fun markHintSeen(id: String) {
        _state.update { it.copy(hintsSeen = it.hintsSeen + id) }
        persistSoon()
    }

    fun buyLifeItem(catId: String) {
        _state.update { st ->
            val cat = Lifestyle.byId(catId) ?: return@update st
            val curIdx = Lifestyle.ownedIndex(st, catId)
            val next = cat.items.getOrNull(curIdx + 1) ?: return@update st
            if (st.money < next.cost) return@update st
            val s2 = when (catId) {
                "home" -> st.copy(money = st.money - next.cost, ownedHomes = st.ownedHomes + (curIdx + 1))
                "car" -> st.copy(money = st.money - next.cost, ownedCars = st.ownedCars + (curIdx + 1))
                else -> st.copy(money = st.money - next.cost, ownedTechs = st.ownedTechs + (curIdx + 1))
            }.let { it.copy(statLifeItems = it.statLifeItems + 1) }
            s2.withChronicle("own", "$catId:${curIdx + 1}")
        }
        persistSoon()
    }

    /** Продать текущий уровень имущества (возврат 60% цены). */
    fun sellLifeItem(catId: String) {
        _state.update { st ->
            val cat = Lifestyle.byId(catId) ?: return@update st
            val curIdx = Lifestyle.ownedIndex(st, catId)
            if (curIdx <= 0) return@update st
            val item = cat.items[curIdx]
            val refund = item.cost * 0.6
            val s2 = when (catId) {
                "home" -> st.copy(ownedHomes = st.ownedHomes - curIdx)
                "car" -> st.copy(ownedCars = st.ownedCars - curIdx)
                else -> st.copy(ownedTechs = st.ownedTechs - curIdx)
            }
            // выручка сначала гасит долг
            var money = s2.money + refund
            var debt = s2.debt
            if (debt > 0.0) { val pay = minOf(money, debt); money -= pay; debt -= pay }
            s2.copy(money = money, debt = debt)
                .withChronicle("sell", "$catId:$curIdx")
        }
        persistSoon()
    }

    /** Светская жизнь: разовая трата, статус навсегда. */
    fun buyExperience(id: String) {
        _state.update { st ->
            if (id in st.experiencesDone) return@update st
            val exp = Lifestyle.experienceById(id) ?: return@update st
            if (st.money < exp.cost) return@update st
            st.copy(money = st.money - exp.cost, experiencesDone = st.experiencesDone + id)
                .withChronicle("exp", id)
        }
        persistSoon()
    }

    fun cycleCurrency() {
        _state.update { it.copy(currencyCode = Currency.next(it.currencyCode).code) }
    }

    fun finishOnboarding(name: String) {
        _state.update {
            it.copy(
                playerName = name.trim().take(18),
                onboarded = true,
                startDateMillis = System.currentTimeMillis(),
                gameHours = 8.0,
                lastSeenMillis = System.currentTimeMillis(),
                chronicle = listOf(Chronicle.entry(1, "start"))
            )
        }
        persist()
    }

    fun renamePlayer(name: String) {
        if (name.isBlank()) return
        _state.update { it.copy(playerName = name.trim().take(18)) }
        persist()
    }

    fun clearOfflineGain() { _offlineGain.value = Triple(0.0, 0.0, 0.0) }

    /** DEV-чит: умножает баланс ×100 (минимум +$10K). Убрать перед релизом. */
    fun devAddMoney() {
        _state.update {
            val add = (it.money * 99.0).coerceAtLeast(10_000.0)
            it.copy(money = it.money + add, totalEarned = it.totalEarned + add)
        }
        persistSoon()
    }

    fun hardReset() {
        _state.value = GameState()
        persist()
    }

    private fun persistSoon() = viewModelScope.launch { persist() }
}
