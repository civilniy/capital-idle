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

    fun bizGlobalMult(state: GameState): Double {
        val leadMult = if ("lead" in state.eduDone) 1.15 else 1.0
        // репутация: при 0 доход 70%, при 100 — 100%. Берём ЦЕЛОЕ значение (как показано игроку),
        // чтобы доход не дрожал от непрерывного дрейфа репутации между секундами.
        val repShown = floor(state.reputation).coerceIn(0.0, 100.0)
        val repMult = 0.70 + 0.30 * (repShown / 100.0)
        return crisisMult(state) * leadMult * repMult * Prestige.incomeMult(state) *
            (1.0 - Pressure.value(state.money, state.reputation))
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

    /** Тап — подработка: примерно полчаса дневного дохода, минимум $1. */
    fun tapReward(state: GameState): Double =
        (incomePerDay(state) / 48.0).coerceAtLeast(1.0)

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
