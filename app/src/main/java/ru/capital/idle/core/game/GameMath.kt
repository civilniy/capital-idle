package ru.capital.idle.core.game

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object GameMath {

    // ===================== доходы (всё в $ за игровой день) =====================

    /** Бодрость: сон + комфорт жилья, не выше 100%. */
    fun awakeEff(state: GameState): Double =
        (Sleep.eff(state.sleepH) + Lifestyle.homeSleepBonus(state)).coerceAtMost(1.0)

    fun salaryPerDay(state: GameState): Double {
        val job = Jobs.byIdOrNull(state.jobId) ?: return 0.0
        return job.ratePerHour * state.workH * awakeEff(state) * Prestige.negotiatorMult(state)
    }

    /** Часы нужны только под предприятия, которыми игрок управляет ЛИЧНО (по 3 часа каждое). */
    fun bizNeedHours(state: GameState): Int {
        var manual = 0
        state.enterprises.forEach { ind -> ind.forEach { e -> if (e.isManual) manual++ } }
        return manual * BusinessConfig.HOURS_PER_MANUAL_ENTERPRISE
    }

    fun mgmtEff(state: GameState): Double {
        val need = bizNeedHours(state)
        return if (need == 0) 1.0 else (state.bizH.toDouble() / need).coerceAtMost(1.0)
    }

    fun crisisMult(state: GameState): Double =
        if (state.phase == MarketPhase.CRISIS && "crisis" in state.eduDone) 0.65 else state.phase.mult

    /** Доход предприятия в день на его уровне (ступени лестницы отрасли). */
    fun enterpriseIncomePerDay(ind: Industry, e: Enterprise): Double {
        val lvl = ind.levels.getOrElse(e.level) { ind.levels.last() }
        return lvl.incomePerHour * Industries.WORK_HOURS * e.efficiency
    }

    /** Общий множитель дохода бизнеса (рынок, лидерство, престиж, репутация, давление элит). */
    /** Множитель удвоения дохода от рекламы: ×2 пока буст активен, иначе ×1. */
    fun boostMult(state: GameState): Double =
        if (state.boostEndsAtMillis > System.currentTimeMillis()) 2.0 else 1.0

    /** Сколько миллисекунд буста осталось (0 если не активен). */
    fun boostRemainingMs(state: GameState): Long =
        (state.boostEndsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Репутация, как её видит игрок: целое значение, а не непрерывный дрейф. */
    fun reputationShown(state: GameState): Double =
        floor(state.reputation).coerceIn(0.0, 100.0)

    /** Номер игрового дня: игра начинается в 08:00 первого дня, поэтому счёт с единицы. */
    fun gameDay(gameHours: Double): Int = (gameHours / 24.0).toInt() + 1

    /**
     * Давление элит от текущих денег и репутации — «посчитать заново».
     *
     * Прямой вызов нужен ровно там, где деньги изменились скачком мимо игрового цикла:
     * при возвращении из оффлайна и при загрузке старого сохранения. В обычной игре
     * давление берут из состояния, а обновляет его [pressureOnNewDay].
     */
    fun pressureFresh(state: GameState): Double =
        Pressure.value(state.money, reputationShown(state))

    /** Пересчитать давление и поставить отметку сегодняшнего игрового дня. */
    fun withPressure(state: GameState): GameState =
        state.copy(pressure = pressureFresh(state), pressureDay = gameDay(state.gameHours))

    /**
     * Обновить давление, если наступил новый игровой день; иначе оставить как есть.
     *
     * Давление зависит от денег, а деньги растут каждый тик игрового цикла (100 мс).
     * Считая его на лету, игра пересчитывала весь доход бизнесов десять раз в секунду:
     * при обороте в сотни миллионов в день цифра дохода сползала прямо на глазах,
     * примерно на 0,18% в секунду. Огрубление денег до трёх значащих цифр (PR #8)
     * не помогало — на таких суммах шаг проскакивался по несколько раз в секунду.
     *
     * Поэтому давление — величина дня: посчитали на границе суток и держим до следующей.
     * Внутри дня доход бизнесов не меняется вообще, а игровые сутки идут 24 реальные
     * секунды, так что реакция на рост капитала остаётся быстрой.
     */
    fun pressureOnNewDay(state: GameState): GameState =
        if (gameDay(state.gameHours) == state.pressureDay) state else withPressure(state)

    /** Давление элит, как его видит игрок и как его считает доход: сохранённое значение дня. */
    fun pressureShown(state: GameState): Double = state.pressure

    fun bizGlobalMult(state: GameState): Double {
        val leadMult = if ("lead" in state.eduDone) 1.15 else 1.0
        // репутация: при 0 доход 70%, при 100 — 100%. Берём ЦЕЛОЕ значение (как показано игроку),
        // чтобы доход не дрожал от непрерывного дрейфа репутации между секундами.
        val repMult = 0.70 + 0.30 * (reputationShown(state) / 100.0)
        // давление берём готовым: оно обновляется раз в игровой день — см. pressureOnNewDay
        return crisisMult(state) * leadMult * repMult * Prestige.incomeMult(state) *
            (1.0 - pressureShown(state))
    }

    /** Доход одного предприятия в день со всеми множителями, БЕЗ насыщения отрасли (сырой вклад). */
    fun enterpriseRawPerDay(state: GameState, ind: Industry, e: Enterprise): Double {
        val base = enterpriseIncomePerDay(ind, e)
        val hours = if (e.isManual) mgmtEff(state) else 1.0
        return base * hours * bizGlobalMult(state)
    }

    /** Множитель насыщения для отрасли по числу предприятий в ней. */
    fun industrySaturation(state: GameState, indIndex: Int): Double =
        BusinessConfig.saturationMult(state.enterprises.getOrElse(indIndex) { emptyList() }.size)

    /** Доход одного предприятия в день со всеми множителями И насыщением рынка (как идёт в общий доход). */
    fun enterpriseGrossPerDay(state: GameState, ind: Industry, e: Enterprise): Double {
        val indIndex = Industries.all.indexOf(ind)
        return enterpriseRawPerDay(state, ind, e) * industrySaturation(state, indIndex)
    }

    /** Чистый доход одного предприятия: валовый со всеми множителями минус зарплата управляющего. */
    fun enterpriseNetPerDay(state: GameState, ind: Industry, e: Enterprise): Double =
        enterpriseGrossPerDay(state, ind, e) - (e.manager?.salaryPerDay ?: 0.0)

    /**
     * Начислить каждому предприятию его выручку и его зарплату за `dtDays` игровых дней.
     *
     * Считается по той же ставке, что показана на карточке («выручка N $/день»), поэтому
     * «заработано» всегда сходится с этой ставкой. Удвоение за рекламу сюда не входит:
     * это множитель всего денежного потока игрока, а не выручки предприятия, и от него
     * накопленное разошлось бы с показанной ставкой.
     */
    fun accrueEnterpriseStats(state: GameState, dtDays: Double): List<List<Enterprise>> {
        if (dtDays <= 0.0) return state.enterprises
        return Industries.all.mapIndexed { i, ind ->
            state.enterprises.getOrElse(i) { emptyList() }.map { e ->
                e.copy(
                    earned = e.earned + enterpriseGrossPerDay(state, ind, e) * dtDays,
                    salaryPaid = e.salaryPaid + (e.manager?.salaryPerDay ?: 0.0) * dtDays
                )
            }
        }
    }

    // ===================== окупаемость предприятия =====================

    /**
     * Снять величины дня на текущий игровой день.
     *
     * Таких величин две, и обе — производные от того, что растёт каждый тик:
     *
     * - прибыль предприятий: накопители `earned` и `salaryPaid` растут непрерывно, и карточка,
     *   читая их напрямую, показывала бегущее число — замеры с устройства давали прирост около
     *   90 $/с, ровно зарплату управляющего, размазанную по 24 секундам игровых суток;
     * - доход для награды за тап: без снимка награда плыла бы внутри суток вслед за репутацией
     *   и капитализацией вкладов.
     *
     * Отметка дня общая: снимаются они в один и тот же момент, на границе игровых суток.
     */
    fun withDayShown(state: GameState): GameState = state.copy(
        enterprises = state.enterprises.map { ind ->
            ind.map { it.copy(profitShown = it.earned - it.salaryPaid) }
        },
        tapIncome = incomePerDay(state),
        statsShownDay = gameDay(state.gameHours)
    )

    /** Обновить величины дня, если наступил новый игровой день; иначе оставить как есть. */
    fun dayShownOnNewDay(state: GameState): GameState =
        if (gameDay(state.gameHours) == state.statsShownDay) state else withDayShown(state)

    /**
     * Окупаемость предприятия: сколько вложено, сколько заработано и что из этого следует.
     *
     * @param invested цена открытия + все улучшения. Разовые затраты, и только они:
     *   меняется при покупке улучшения и больше ни от чего
     * @param earned чистая прибыль — выручка за вычетом выплаченной управляющему зарплаты,
     *   как она была на последнюю смену игрового дня ([Enterprise.profitShown]).
     *   Может быть отрицательной: управляющий способен съедать больше, чем приносит точка
     * @param daysLeft через сколько дней вложения вернутся. `null` — считать нечего:
     *   либо уже окупилось, либо чистый доход не положителен
     * @param returnedPct сколько процентов вложений уже вернулось. Заполнено всегда,
     *   но показывать имеет смысл после окупаемости
     * @param stalled чистый доход не положителен: при таком управляющем предприятие
     *   не окупится никогда, и делить на него нельзя
     * @param unknown вложений не записано вовсе. Так выглядит предприятие из сохранения,
     *   сделанного до появления учёта: сказать про окупаемость нечего, и «окупилось» здесь
     *   было бы неправдой — не окупилось, а просто не с чем сравнивать
     */
    data class Payback(
        val invested: Double,
        val earned: Double,
        val daysLeft: Double?,
        val returnedPct: Double,
        val stalled: Boolean,
        val unknown: Boolean
    ) {
        val paidOff: Boolean get() = !unknown && earned >= invested
    }

    /**
     * Посчитать окупаемость по накопителям предприятия и его текущему чистому доходу.
     *
     * Срок считается по **текущему** чистому доходу: это оценка «если дальше так же»,
     * а не предсказание. Прошлое в неё не входит — оно уже учтено в `earned`.
     *
     * Зарплата управляющего вычитается из прибыли, а не прибавляется к вложениям.
     * Остаток до окупаемости от этого не меняется — `вложено − прибыль` это те же
     * `(открытие + улучшения + зарплата) − выручка`, — но «вложено» перестаёт расти
     * само по себе, а именно это и выглядело ошибкой.
     */
    fun payback(state: GameState, ind: Industry, e: Enterprise): Payback {
        val invested = e.invested
        val earned = e.profitShown
        val left = invested - earned
        val netPerDay = enterpriseNetPerDay(state, ind, e)
        val returnedPct = if (invested > 0.0) earned / invested * 100.0 else 0.0
        return when {
            invested <= 0.0 ->
                Payback(invested, earned, null, 0.0, stalled = false, unknown = true)
            left <= 0.0 ->
                Payback(invested, earned, null, returnedPct, stalled = false, unknown = false)
            netPerDay <= 0.0 ->
                Payback(invested, earned, null, returnedPct, stalled = true, unknown = false)
            else ->
                Payback(invested, earned, left / netPerDay, returnedPct, stalled = false, unknown = false)
        }
    }

    fun bizPerDay(state: GameState): Double {
        var perDay = 0.0
        Industries.all.forEachIndexed { i, ind ->
            val list = state.enterprises.getOrElse(i) { emptyList() }
            if (list.isEmpty()) return@forEachIndexed
            // сумма сырого дохода предприятий отрасли × насыщение рынка
            var indRaw = 0.0
            list.forEach { e -> indRaw += enterpriseRawPerDay(state, ind, e) }
            perDay += indRaw * BusinessConfig.saturationMult(list.size)
        }
        return perDay
    }

    /** Суммарная зарплата всех управляющих в день. */
    fun managersSalaryPerDay(state: GameState): Double {
        var s = 0.0
        state.enterprises.forEach { ind -> ind.forEach { e -> s += e.manager?.salaryPerDay ?: 0.0 } }
        return s
    }

    fun invPerDay(state: GameState): Double {
        val base = Investments.incomePerDay(state.investValues, state.eduDone)
        val tierBonus = CardTier.entries.getOrElse(state.activatedCardTier) { CardTier.CLASSIC }.passiveBonus
        return base * (1.0 + tierBonus)
    }

    fun incomePerDay(state: GameState): Double =
        salaryPerDay(state) + bizPerDay(state) + invPerDay(state)

    /** Чистый доход в день (то, что на карте): доход − содержание − зарплаты управляющих.
     *  Реклама-буст удваивает ВЕСЬ чистый поток (включая зарплату), чтобы на карте было ровно ×2. */
    fun netIncomePerDay(state: GameState): Double =
        (incomePerDay(state) - Lifestyle.dailyUpkeep(state) - managersSalaryPerDay(state)) * boostMult(state)

    /**
     * Насколько тап слабее своей базовой доли (1/48 дневного дохода) при таком доходе.
     *
     * До [GameConfig.TAP_FULL_INCOME_PER_DAY] — в полную силу, дальше каждое удесятерение
     * дохода режет отдачу вдвое. Шкала непрерывная (в пороге ровно 1) и без нижнего предела:
     * поставь ограничитель — и на больших числах тап снова обгонит экономику.
     *
     * Награда при этом всё равно растёт вместе с доходом, просто медленнее его:
     * `доход^(1 − lg2)` ≈ `доход^0,7`. Стократный рост дохода поднимает клик в 25 раз.
     */
    fun tapEfficiency(incomePerDay: Double): Double {
        if (incomePerDay <= GameConfig.TAP_FULL_INCOME_PER_DAY) return 1.0
        val decades = log10(incomePerDay / GameConfig.TAP_FULL_INCOME_PER_DAY)
        return 1.0 / GameConfig.TAP_DECAY_PER_DECADE.pow(decades)
    }

    /**
     * Сколько заработано за `dtDays` игровых дней — валовой доход с учётом буста.
     *
     * Единственный вход обоих счётчиков заработка: и `totalEarned` (титулы, престиж),
     * и `statAllTimeEarned` (за все жизни). Раньше формула стояла в игровом цикле дважды,
     * и во второй копии потерялся множитель буста — у игрока с постоянным ×2 счётчик всех
     * жизней отставал ровно вдвое. Одно выражение на оба счётчика делает такое расхождение
     * невозможным.
     */
    fun earnedDelta(state: GameState, dtDays: Double): Double =
        incomePerDay(state) * boostMult(state) * dtDays

    /**
     * Тап — подработка: доля дневного дохода, тем меньшая, чем больше доход. Минимум $1.
     *
     * Доход берётся снятым на игровой день ([GameState.tapIncome]), а не живым: иначе
     * награда плыла бы внутри суток вслед за репутацией и капитализацией вкладов.
     */
    fun tapReward(state: GameState): Double =
        (state.tapIncome / 48.0 * tapEfficiency(state.tapIncome)).coerceAtLeast(1.0)

    /** Цена уровня отрасли с учётом фазы рынка и скидок образования. */
    /**
     * Полный капитал игрока (как в Forbes): деньги не «испаряются» при покупках,
     * а переходят в активы. Считаем без двойного счёта:
     *   наличные − долг + пассив + акции(по текущей цене)
     *   + бизнесы(сумма вложенного) + имущество(полная цена покупок) + светская жизнь.
     */
    fun netWorth(state: GameState): Double {
        var w = state.money - state.debt

        // пассив (депозит/облигации/недвижимость)
        w += state.investValues.sum()

        // акции по текущей цене
        Exchange.stocks.indices.forEach { i ->
            w += state.stockQty.getOrElse(i) { 0.0 } * state.stockPrices.getOrElse(i) { 0.0 }
        }

        // бизнесы: сумма вложенного во все предприятия (открытие + улучшения до текущего уровня)
        Industries.all.forEachIndexed { idx, ind ->
            state.enterprises.getOrElse(idx) { emptyList() }.forEach { e ->
                for (l in 0..e.level) w += ind.levels.getOrElse(l) { ind.levels.last() }.cost
            }
        }

        // имущество: полная цена всех купленных предметов (вариант А)
        w += Lifestyle.ownedCost(state)

        // коллекция: вложение, считаем по текущей цене (как акции)
        w += Collectibles.portfolioValue(state)

        // светская жизнь: разовые траты тоже часть «прожитого» капитала
        w += state.experiencesDone.sumOf { id -> Lifestyle.experienceById(id)?.cost ?: 0.0 }

        return w.coerceAtLeast(0.0)
    }

    /** Цена открытия нового предприятия: базовая ступень 0 × рост за каждое уже открытое в отрасли. */
    fun openEnterpriseCost(state: GameState, indIndex: Int): Double {
        val ind = Industries.all[indIndex]
        val n = state.enterprises.getOrElse(indIndex) { emptyList() }.size
        var d = 1.0
        if ("sales" in state.eduDone) d *= 0.95
        if ("mgmt" in state.eduDone) d *= 0.90
        return ind.levels[0].cost * Math.pow(BusinessConfig.OPEN_PRICE_GROWTH, n.toDouble()) * state.phase.sale * d
    }

    /** Цена улучшения конкретного предприятия (переход на следующую ступень). */
    fun upgradeEnterpriseCost(state: GameState, indIndex: Int, entIndex: Int): Double {
        val ind = Industries.all[indIndex]
        val e = state.enterprises.getOrElse(indIndex) { emptyList() }.getOrNull(entIndex) ?: return Double.MAX_VALUE
        val nextLv = e.level + 1
        if (nextLv >= ind.levels.size) return Double.MAX_VALUE
        var d = 1.0
        if ("sales" in state.eduDone) d *= 0.95
        if ("mgmt" in state.eduDone) d *= 0.90
        return ind.levels[nextLv].cost * state.phase.sale * d
    }

    /** Можно ли открыть новое предприятие: лимит + образование первой ступени + порог статуса. */
    fun canOpenEnterprise(state: GameState, indIndex: Int): Boolean {
        val g = openGate(state, indIndex)
        return g.limitOk && g.eduOk && g.statusOk
    }

    /** Детали ворот открытия отрасли (для UI: что именно мешает). */
    data class OpenGate(val limitOk: Boolean, val eduOk: Boolean, val statusOk: Boolean,
                        val needEdu: String?, val needStatus: Int, val haveStatus: Int) {
        val ok: Boolean get() = limitOk && eduOk && statusOk
    }

    fun openGate(state: GameState, indIndex: Int): OpenGate {
        val ind = Industries.all[indIndex]
        val list = state.enterprises.getOrElse(indIndex) { emptyList() }
        val limitOk = list.size < BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY
        val firstReq = ind.levels[0].reqCourse
        val eduOk = firstReq == null || firstReq in state.eduDone
        val needStatus = Industries.statusGateFor(ind.id)
        val haveStatus = Lifestyle.socialStatus(state)
        val statusOk = haveStatus >= needStatus
        return OpenGate(limitOk, eduOk, statusOk, firstReq, needStatus, haveStatus)
    }

    /** Самая дешёвая доступная следующая трата по бизнесу (открытие или улучшение) — для стены прогресса. */
    fun cheapestNextBiz(state: GameState): Double? {
        var best: Double? = null
        Industries.all.indices.forEach { i ->
            val list = state.enterprises.getOrElse(i) { emptyList() }
            if (list.size < BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY) {
                val c = openEnterpriseCost(state, i)
                if (best == null || c < best!!) best = c
            }
            list.indices.forEach { j ->
                val c = upgradeEnterpriseCost(state, i, j)
                if (c != Double.MAX_VALUE && (best == null || c < best!!)) best = c
            }
        }
        return best
    }

    /** Игрок «упёрся в стену»: следующий рывок требует дольше 80 игровых дней чистого дохода. */
    fun atProgressWall(state: GameState): Boolean {
        val net = incomePerDay(state) - Lifestyle.dailyUpkeep(state) - managersSalaryPerDay(state)
        if (net <= 0.0) return false
        val next = cheapestNextBiz(state) ?: return true
        return next / net > 80.0
    }

    /** Учебных часов в игровой день при текущем распорядке. */
    fun studyHoursPerDay(state: GameState): Double {
        val mentor = if ("mentor" in state.netOwned) 1.5 else 1.0
        return state.studyHCalc * awakeEff(state) * mentor *
            Prestige.studyMult(state) * Lifestyle.techStudyMult(state)
    }

    /**
     * Оффлайн-сейф, две фазы: первые FULL секунд на полную силу, следующие HALF — на половинной.
     * Бонус престиж-апгрейда удлиняет обе фазы. Возвращает (заработано, упущено сверх ёмкости).
     */
    fun offlineEarnings(state: GameState, elapsedRealSec: Double): Pair<Double, Double> {
        val bonus = state.pSafe * GameConfig.OFFLINE_SAFE_BONUS_SEC
        val fullSec = GameConfig.OFFLINE_FULL_SEC + bonus
        val halfSec = GameConfig.OFFLINE_HALF_SEC + bonus
        val perSecBase = incomePerDay(state) / GameTime.DAY_REAL_SEC

        val t = elapsedRealSec.coerceAtLeast(0.0)
        val inFull = t.coerceAtMost(fullSec)
        val inHalf = (t - fullSec).coerceIn(0.0, halfSec)
        val earned = inFull * perSecBase * GameConfig.OFFLINE_EFF_FULL +
                     inHalf * perSecBase * GameConfig.OFFLINE_EFF_HALF

        // упущено: время сверх обеих фаз, оценочно по нижней эффективности
        val overSec = (t - fullSec - halfSec).coerceIn(0.0, (fullSec + halfSec) * 4)
        val missed = overSec * perSecBase * GameConfig.OFFLINE_EFF_HALF
        return earned to missed
    }

    // ===================== форматирование =====================

    /**
     * Дробное число с запятой — независимо от языка системы.
     *
     * Locale.ROOT даёт предсказуемую точку, которую мы меняем на запятую сами. Без него
     * на английском телефоне вышла бы точка, на русском запятая, и на одном экране
     * получился бы разнобой. Правило «разделитель всегда запятая» — в CLAUDE.md.
     */
    fun decimal(value: Double, digits: Int = 1): String =
        String.format(Locale.ROOT, "%.${digits}f", value).replace('.', ',')

    private val SUFFIXES = listOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx")
    private const val FULL_LIMIT = 1_000_000_000.0   // до миллиарда показываем число целиком

    fun format(n: Double): String {
        val v = abs(n)
        if (v < 10.0) {
            return if (v != floor(v)) decimal(n)
            else floor(n).toLong().toString()
        }
        if (v < FULL_LIMIT) return groupDigits(floor(n).toLong())
        var tier = (log10(v) / 3.0).toInt()
        if (tier >= SUFFIXES.size) tier = SUFFIXES.size - 1
        val scaled = n / 1000.0.pow(tier)
        val s = if (abs(scaled) < 100) decimal(scaled) else decimal(scaled, 0)
        return s + SUFFIXES[tier]
    }

    /** Компактный формат для тесных мест (ячейки сводки): всегда с суффиксом от тысяч. 2,4M, 1,2B. */
    fun formatShort(n: Double): String {
        val v = abs(n)
        if (v < 1000.0) return floor(n).toLong().toString()
        var tier = (log10(v) / 3.0).toInt()
        if (tier >= SUFFIXES.size) tier = SUFFIXES.size - 1
        val scaled = n / 1000.0.pow(tier)
        val s = if (abs(scaled) < 100) decimal(scaled) else decimal(scaled, 0)
        return s + SUFFIXES[tier]
    }

    /** Группировка разрядов неразрывными пробелами: 1 234 567. */
    private fun groupDigits(n: Long): String {
        val str = n.toString()
        val neg = str.startsWith("-")
        val digits = if (neg) str.substring(1) else str
        val sb = StringBuilder()
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('\u00A0')
            sb.append(digits[i])
        }
        return (if (neg) "-" else "") + sb
    }

    fun formatMoney(amountUsd: Double, currency: Currency): String =
        currency.symbol + " " + format(amountUsd * currency.ratePerUsd)

    fun formatAmount(amountUsd: Double, currency: Currency): String =
        format(amountUsd * currency.ratePerUsd)

    fun formatFull(n: Long): String {
        val str = n.toString()
        val sb = StringBuilder()
        for (i in str.indices) {
            if (i > 0 && (str.length - i) % 3 == 0) sb.append('\u00A0')
            sb.append(str[i])
        }
        return sb.toString()
    }

    fun formatRank(n: Long): String = when {
        n >= 1_000_000_000L -> decimal(n / 1e9) + " млрд"
        n >= 1_000_000L -> decimal(n / 1e6) + " млн"
        n >= 1_000L -> decimal(n / 1e3) + " тыс"
        else -> n.toString()
    }

    /** Дата и время игрового календаря: "01.01.27 · 08:00". */
    fun gameDateTime(state: GameState): String {
        // Locale.ROOT обязателен: в тайской или арабской локали getInstance() отдаёт
        // не григорианский календарь, и год игрового календаря разъезжается на столетия.
        val cal = java.util.Calendar.getInstance(Locale.ROOT)
        cal.timeInMillis = if (state.startDateMillis > 0) state.startDateMillis else System.currentTimeMillis()
        cal.add(java.util.Calendar.DAY_OF_YEAR, floor(state.gameHours / 24.0).toInt())
        val h = floor(state.gameHours % 24.0).toInt()
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val mo = cal.get(java.util.Calendar.MONTH) + 1
        val yy = cal.get(java.util.Calendar.YEAR) % 100
        return String.format(Locale.ROOT, "%02d.%02d.%02d · %02d:00", d, mo, yy, h)
    }
}
