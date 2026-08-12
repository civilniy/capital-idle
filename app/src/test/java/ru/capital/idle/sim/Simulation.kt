package ru.capital.idle.sim

import ru.capital.idle.core.game.Asset
import ru.capital.idle.core.game.Education
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameLoop
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Jobs
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.core.game.Manager
import ru.capital.idle.core.game.Milestones
import ru.capital.idle.core.game.Onboarding

/**
 * Каркас автоматической симуляции: прогоняет игру день за днём на чистой JVM и проверяет,
 * что базовые правила экономики не нарушаются.
 *
 * Ходит тем же кодом, которым живёт игра: шаг дня — это `GameLoop.economy` + `GameLoop.progress`,
 * вынесенные из `GameViewModel`. Копию формул здесь заводить нельзя — она разошлась бы
 * с оригиналом, и симуляция проверяла бы саму себя.
 *
 * Чего в симуляции нет: биржи (её цены разыгрываются генератором), торгов, анонсов разделов
 * и шагов гида. Ни одна из двух стратегий их не использует.
 */

// ===================== стратегии =====================

/** Что игрок делает в начале дня. Возвращает состояние после своих действий. */
interface Strategy {
    val name: String
    /** Разовая настройка перед первым днём: распорядок, стартовая работа. */
    fun setup(s: GameState): GameState = s
    /** Ход одного игрового дня. */
    fun act(s: GameState): GameState
}

/** Курсы в порядке изучения — общий «скелет» развития для обеих стратегий. */
private val COURSE_PATH = listOf("school", "sales", "acc", "uni", "mgmt", "mba")

/** Следующий неизученный курс, если предпосылки уже есть. */
private fun nextCourse(s: GameState) =
    COURSE_PATH.asSequence()
        .mapNotNull { Education.byId(it) }
        .firstOrNull { c -> c.id !in s.eduDone && (c.reqCourse == null || c.reqCourse in s.eduDone) }

/** Начать учёбу, если не учимся и хватает денег. */
private fun study(s: GameState): GameState {
    if (s.studyingId.isNotEmpty()) return s
    val c = nextCourse(s) ?: return s
    if (s.money < c.cost) return s
    return s.copy(money = s.money - c.cost, studyingId = c.id, studyProgress = 0.0)
}

/**
 * Резерв под следующий курс: без него стратегия, вкладывающая всё, никогда не доучится.
 *
 * Копим только под курс, который уже по карману на этом этапе: иначе игрок замирал бы
 * с полумиллионом под MBA, ничего не покупая годами.
 */
private fun reserve(s: GameState): Double {
    if (s.studyingId.isNotEmpty()) return 0.0
    val c = nextCourse(s) ?: return 0.0
    val worth = GameMath.netWorth(s)
    return if (c.cost <= maxOf(1_000.0, worth * 0.5)) c.cost else 0.0
}

/** Учится ли игрок сейчас или ему ещё есть что учить. */
private fun studying(s: GameState) = s.studyingId.isNotEmpty() || nextCourse(s) != null

/**
 * Часы под учёбу. Без них ни одна стратегия не сдвинется: `studyHoursPerDay` считается
 * от остатка суток после работы и бизнеса, и распорядок «всё в работу» замораживает
 * развитие насовсем.
 */
private const val STUDY_HOURS = 6

/** Лучшая доступная вакансия. */
private fun takeJob(s: GameState): GameState {
    val best = Jobs.all.lastOrNull { it.reqCourse == null || it.reqCourse in s.eduDone } ?: return s
    return if (best.id == s.jobId) s else s.copy(jobId = best.id)
}

/**
 * «Только бизнес»: покупает и улучшает предприятия, как только хватает денег.
 * Вклады не трогает, не тапает. Управляющих нанимает, когда точка их окупает, — иначе
 * дальше двух-трёх предприятий стратегия упирается в часы личного управления.
 */
class BusinessOnly : Strategy {
    override val name = "Только бизнес"

    override fun setup(s: GameState) = s.copy(sleepH = 8, workH = 8, bizH = 8, jobId = "courier")

    override fun act(s0: GameState): GameState {
        var s = takeJob(study(s0))

        // распорядок: часы под учёбу, пока есть что учить; остальное — бизнесу,
        // а пока он не кормит — половину остатка работе
        val budget = s.dayBudget
        val forStudy = if (studying(s)) STUDY_HOURS else 0
        val rest = (budget - forStudy).coerceAtLeast(0)
        val work = if (GameMath.bizPerDay(s) > GameMath.salaryPerDay(s)) 0 else rest / 2
        s = s.copy(workH = work, bizH = rest - work)

        // Управляющие: без них дальше нескольких точек не уйти — личное управление съедает
        // часы. Запас четырёхкратный: зарплата платится каждый день, а выручка падает
        // в кризис более чем вдвое, и на тонкой марже точка утаскивает игрока в долги
        s = s.copy(enterprises = s.enterprises.mapIndexed { i, list ->
            val ind = Industries.all[i]
            list.map { e ->
                val gross = GameMath.enterpriseGrossPerDay(s, ind, e)
                val want = Manager.entries.lastOrNull { gross > it.salaryPerDay * 4.0 }
                if (want != null && want.ordinal > e.managerOrdinal) e.copy(managerOrdinal = want.ordinal) else e
            }
        })

        // за день делаем ограниченное число покупок: игрок не кликает бесконечно
        repeat(4) {
            val upgraded = bestUpgrade(s)
            if (upgraded != null) { s = upgraded; return@repeat }
            val opened = bestOpen(s)
            if (opened != null) s = opened
        }
        return s
    }

    /**
     * Открывать новую точку имеет смысл, только если ей есть кем управлять: часы личного
     * управления делятся на все ручные предприятия, и лишняя точка режет доход остальным.
     */
    private fun hasRoomToOpen(s: GameState): Boolean {
        val manual = s.enterprises.sumOf { list -> list.count { it.isManual } }
        return (manual + 1) * 3 <= s.bizH
    }

    /** Самое дешёвое доступное улучшение. */
    private fun bestUpgrade(s: GameState): GameState? {
        var best: Triple<Int, Int, Double>? = null
        Industries.all.forEachIndexed { i, ind ->
            s.enterprises.getOrElse(i) { emptyList() }.forEachIndexed { j, e ->
                val next = e.level + 1
                if (next >= ind.levels.size) return@forEachIndexed
                val req = ind.levels[next].reqCourse
                if (req != null && req !in s.eduDone) return@forEachIndexed
                val cost = GameMath.upgradeEnterpriseCost(s, i, j)
                if (cost + reserve(s) <= s.money && (best == null || cost < best!!.third)) {
                    best = Triple(i, j, cost)
                }
            }
        }
        val (i, j, cost) = best ?: return null
        val lists = s.enterprises.toMutableList()
        val list = lists[i].toMutableList()
        val e = list[j]
        list[j] = e.copy(level = e.level + 1, invested = e.invested + cost)
        lists[i] = list
        return s.copy(money = s.money - cost, enterprises = lists,
            statBizLevels = s.statBizLevels + 1)
    }

    /** Самое дешёвое доступное открытие. */
    private fun bestOpen(s: GameState): GameState? {
        var best: Pair<Int, Double>? = null
        Industries.all.forEachIndexed { i, ind ->
            if (!GameMath.canOpenEnterprise(s, i) || !hasRoomToOpen(s)) return@forEachIndexed
            val req = ind.levels[0].reqCourse
            if (req != null && req !in s.eduDone) return@forEachIndexed
            val cost = GameMath.openEnterpriseCost(s, i)
            if (cost + reserve(s) <= s.money && (best == null || cost < best!!.second)) {
                best = i to cost
            }
        }
        val (i, cost) = best ?: return null
        val lists = s.enterprises.toMutableList()
        lists[i] = listOf(Enterprise(level = 0, managerOrdinal = -1, name = "точка", invested = cost)) +
            lists[i]
        return s.copy(money = s.money - cost, enterprises = lists,
            statBizLevels = s.statBizLevels + 1)
    }
}

/**
 * «Только вклады»: всё свободное кладёт в лучший доступный инструмент с капитализацией.
 * Бизнесы не покупает. Работает и учится — без этого не откроются ни вклады, ни жильё.
 */
class DepositsOnly : Strategy {
    override val name = "Только вклады"

    override fun setup(s: GameState) = s.copy(sleepH = 8, workH = 16, bizH = 0, jobId = "courier")

    override fun act(s0: GameState): GameState {
        var s = takeJob(study(s0))
        // часы под учёбу, остальное — работе: бизнеса у этой стратегии нет
        val forStudy = if (studying(s)) STUDY_HOURS else 0
        s = s.copy(workH = (s.dayBudget - forStudy).coerceAtLeast(0), bizH = 0)

        val asset = Asset.entries.lastOrNull { it.reqCourse in s.eduDone } ?: return s
        // капитализация включена: доход инструмента идёт в тело, а не на карту
        s = s.copy(capitalizeMask = s.capitalizeMask or (1 shl asset.ordinal))

        val free = s.money - reserve(s)
        if (free <= 0.0) return s
        val values = s.investValues.toMutableList().also { it[asset.ordinal] += free }
        val costs = s.investCosts.toMutableList().also { it[asset.ordinal] += free }
        return s.copy(money = s.money - free, investValues = values, investCosts = costs)
    }
}

/** «Ничего не делает»: нужна, чтобы мерить рост капитала без вмешательства игрока. */
class Idle : Strategy {
    override val name = "Ничего не делает"
    override fun act(s: GameState) = s
}

// ===================== симулятор =====================

/** Слепок одного игрового дня. */
data class DayRecord(
    val day: Int,
    val state: GameState,
    /** Сколько заработано за этот день (прирост totalEarned). */
    val earned: Double,
    /** Капитал сразу после ходов игрока — с него считается пассивный рост за день. */
    val worthAfterActions: Double,
    /** Делал ли игрок в этот день покупки. */
    val acted: Boolean
) {
    val worth: Double get() = GameMath.netWorth(state)
}

/**
 * Прогон игры по дням.
 *
 * @param ticksPerDay сколько раз за игровой день дёргается цикл. В игре это 240 тиков
 *   по 100 мс; здесь достаточно 24 — все начисления линейны по времени, а всё, что
 *   привязано к суткам (капитализация, величины дня), срабатывает по номеру дня.
 */
class Simulator(
    private val strategy: Strategy,
    private val ticksPerDay: Int = 24,
    start: GameState = GameState(onboarded = true, tutorialStep = Onboarding.DONE),
    /**
     * Воспроизвести старую формулу счётчика всех жизней — без множителя буста.
     * Нужна, чтобы убедиться, что каркас видит уже исправленную ошибку.
     */
    private val statAllTimeWithoutBoost: Boolean = false
) {
    private var state: GameState = strategy.setup(start)
    private var cursor = GameLoop.Cursor()

    fun run(days: Int): List<DayRecord> {
        val out = ArrayList<DayRecord>(days)
        repeat(days) { d ->
            val before = state
            state = strategy.act(state)
            val acted = state !== before && changed(before, state)
            val worthAfterActions = GameMath.netWorth(state)
            val earnedBefore = state.totalEarned

            repeat(ticksPerDay) {
                val dt = 1.0 / ticksPerDay
                // разница между «с бустом» и «без буста» — ровно то, что терялось в старой строке
                val lost = if (statAllTimeWithoutBoost)
                    GameMath.earnedDelta(state, dt) - GameMath.incomePerDay(state) * dt else 0.0
                val eco = GameLoop.economy(state, dt, cursor)
                cursor = eco.cursor
                state = GameLoop.progress(eco.state, eco.events)
                if (lost != 0.0) state = state.copy(statAllTimeEarned = state.statAllTimeEarned - lost)
            }
            out += DayRecord(d + 1, state, state.totalEarned - earnedBefore, worthAfterActions, acted)
        }
        return out
    }

    /** Ход считается действием, только если что-то куплено: смена работы сама по себе не в счёт. */
    private fun changed(a: GameState, b: GameState) =
        a.money != b.money || a.enterprises != b.enterprises || a.investValues != b.investValues
}

// ===================== инварианты =====================

data class Violation(val day: Int, val rule: String, val detail: String) {
    override fun toString() = "день $day · $rule · $detail"
}

object Invariants {

    /** Допуск на накопленную ошибку double при сложении десятков тысяч слагаемых. */
    private const val REL = 1e-6

    /**
     * Все проверки, кроме удвоения капитала: оно смотрит на историю целиком.
     *
     * [titleOf] и [unlockedBy] подставляются, чтобы тем же кодом проверить и старое
     * (ошибочное) поведение: раньше и титул, и пороги разделов считались от валового дохода.
     */
    fun check(
        prev: DayRecord?,
        cur: DayRecord,
        titleOf: (GameState) -> Int = { it.lastTitleIdx },
        unlockedBy: (GameState, String) -> Boolean = Onboarding::unlocked
    ): List<Violation> {
        val v = mutableListOf<Violation>()
        val s = cur.state

        // 1. счётчик всех жизней не отстаёт от счётчика жизни, а в первой жизни совпадает
        if (s.statAllTimeEarned < s.totalEarned * (1 - REL)) {
            v += Violation(cur.day, "счётчики заработка",
                "statAllTimeEarned ${s.statAllTimeEarned} < totalEarned ${s.totalEarned}, " +
                    "отношение ${s.totalEarned / s.statAllTimeEarned.coerceAtLeast(1e-9)}")
        }
        val firstLife = s.bullion == 0L && s.museum.isEmpty()
        if (firstLife && !close(s.statAllTimeEarned, s.totalEarned)) {
            v += Violation(cur.day, "счётчики заработка",
                "первая жизнь, но statAllTimeEarned ${s.statAllTimeEarned} != totalEarned ${s.totalEarned}")
        }

        // 2. капитал не растёт быстрее, чем начислено дохода. Считается от капитала ПОСЛЕ
        //    покупок: покупка сама по себе двигает капитал (в него бизнес входит по цене
        //    ступеней лестницы, а платится цена с наценкой за число точек и скидкой фазы)
        val passiveGrowth = cur.worth - cur.worthAfterActions
        if (passiveGrowth > cur.earned * (1 + REL) + 1.0) {
            v += Violation(cur.day, "рост капитала",
                "капитал вырос на $passiveGrowth при доходе ${cur.earned}")
        }

        // 3. титул соответствует своему порогу
        val wantTitle = Lifestyle.titleIndex(s.peakNetWorth)
        val gotTitle = titleOf(s)
        if (gotTitle != wantTitle) {
            v += Violation(cur.day, "титул",
                "титул $gotTitle (${Lifestyle.titles[gotTitle].name}), " +
                    "а по капиталу ${s.peakNetWorth} полагается $wantTitle " +
                    "(${Lifestyle.titles[wantTitle].name})")
        }

        // 4. открытые разделы соответствуют своим порогам
        gates.forEach { (id, threshold) ->
            val open = unlockedBy(s, id)
            val should = s.peakNetWorth >= threshold ||
                id in s.announced ||
                (id == "world" && s.milestonesClaimed > 0)
            if (open != should) {
                v += Violation(cur.day, "порог раздела",
                    "«$id» ${if (open) "открыт" else "закрыт"} при капитале ${s.peakNetWorth} " +
                        "(порог $threshold)")
            }
        }

        // 5. ничего не ушло в минус, в NaN или в бесконечность
        finite(cur, v, "деньги", s.money, allowZero = true)
        finite(cur, v, "капитал", cur.worth, allowZero = true)
        finite(cur, v, "доход в день", GameMath.incomePerDay(s), allowZero = true)
        finite(cur, v, "репутация", s.reputation, allowZero = true)
        finite(cur, v, "totalEarned", s.totalEarned, allowZero = true)
        finite(cur, v, "statAllTimeEarned", s.statAllTimeEarned, allowZero = true)
        finite(cur, v, "peakNetWorth", s.peakNetWorth, allowZero = true)
        finite(cur, v, "долг", s.debt, allowZero = true)
        if (s.reputation > 100.0) {
            v += Violation(cur.day, "репутация", "выше сотни: ${s.reputation}")
        }
        // храповик капитала не должен убывать
        if (prev != null && s.peakNetWorth < prev.state.peakNetWorth - 1e-6) {
            v += Violation(cur.day, "храповик капитала",
                "было ${prev.state.peakNetWorth}, стало ${s.peakNetWorth}")
        }
        return v
    }

    /** Пороги разделов, завязанные на капитал. */
    private val gates = listOf("world" to 1_000_000.0, "net" to 5_000.0, "pres" to 1_000_000_000.0)

    /**
     * Расхождения, которые каркас уже нашёл и которые чинить не поручено.
     *
     * Пока такое расхождение здесь перечислено, оно печатается в отчёт, но не роняет прогон:
     * иначе одна известная ошибка закрывала бы собой все будущие находки. Исправят — строка
     * отсюда уходит, и прогон снова начинает падать, если ошибка вернётся.
     *
     * 1. Слиток за веху делает игрока «ветераном». `Onboarding.veteran` считает ветераном
     *    любого, у кого есть слитки, а первые пять слитков выдаёт веха «Первый миллион».
     *    В результате на капитале в миллион разом открывается всё, включая «Престиж»,
     *    порог которого — миллиард.
     */
    fun isKnownFinding(v: Violation): Boolean =
        v.rule == "порог раздела" && v.detail.contains("«pres»") && v.detail.contains("открыт")

    /**
     * Капитал не удваивается сам по себе быстрее чем за [window] игровых дней.
     *
     * Смотрятся только промежутки, в которых игрок ничего не покупал: удвоение за счёт
     * собственных вложений — это не «само по себе».
     */
    fun checkDoubling(records: List<DayRecord>, window: Int = 200): List<Violation> {
        val v = mutableListOf<Violation>()
        for (i in 0 until records.size - window) {
            val a = records[i]
            val b = records[i + window]
            val quiet = (i + 1..i + window).none { records[it].acted }
            if (!quiet) continue
            if (a.worth > 1.0 && b.worth > a.worth * 2.0) {
                v += Violation(b.day, "удвоение капитала",
                    "без единого действия игрока капитал вырос с ${a.worth} (день ${a.day}) " +
                        "до ${b.worth} за $window дней")
                return v   // одного примера достаточно, дальше он будет повторяться каждый день
            }
        }
        return v
    }

    private fun finite(r: DayRecord, out: MutableList<Violation>, what: String, x: Double,
                       allowZero: Boolean) {
        if (x.isNaN() || x.isInfinite()) out += Violation(r.day, "не число", "$what = $x")
        else if (x < if (allowZero) 0.0 else 1e-12) out += Violation(r.day, "отрицательная величина", "$what = $x")
    }

    private fun close(a: Double, b: Double) =
        kotlin.math.abs(a - b) <= kotlin.math.max(kotlin.math.abs(a), kotlin.math.abs(b)) * REL + 1e-6
}

// ===================== отчёт =====================

object Report {

    fun of(strategyName: String, records: List<DayRecord>): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=".repeat(78))
        sb.appendLine("СИМУЛЯЦИЯ · $strategyName · ${records.size} игровых дней")
        sb.appendLine("=".repeat(78))

        sb.appendLine("Капитал по дням:")
        listOf(100, 500, 1000, 2000).forEach { d ->
            records.getOrNull(d - 1)?.let {
                sb.appendLine("  день %5d : %s".format(d, money(it.worth)))
            }
        }

        sb.appendLine("Во сколько раз растёт за тысячу дней:")
        var from = 1
        while (from + 1000 <= records.size) {
            val a = records[from - 1].worth
            val b = records[from + 999].worth
            sb.appendLine("  дни %5d..%5d : ×%s".format(from, from + 999,
                if (a > 0.0) "%.1f".format(b / a) else "—"))
            from += 1000
        }

        sb.appendLine("Разделы открылись:")
        listOf("net", "world", "pres").forEach { id ->
            val at = records.firstOrNull { Onboarding.unlocked(it.state, id) }
            sb.appendLine("  %-6s : %s".format(id,
                if (at == null) "не открылся" else "день ${at.day}, капитал ${money(at.worth)}"))
        }

        sb.appendLine("Титулы:")
        var seen = -1
        records.forEach { r ->
            if (r.state.lastTitleIdx > seen) {
                seen = r.state.lastTitleIdx
                sb.appendLine("  день %5d : %-18s капитал %s"
                    .format(r.day, Lifestyle.titles[seen].name, money(r.worth)))
            }
        }

        val last = records.last().state
        sb.appendLine("Вехи: ${last.milestonesClaimed} из ${Milestones.all.size}")
        sb.appendLine("Итог: капитал %s · доход %s/день · заработано %s"
            .format(money(records.last().worth), money(GameMath.incomePerDay(last)),
                money(last.totalEarned)))
        sb.appendLine("=".repeat(78))
        return sb.toString()
    }

    private fun money(v: Double) = "$ " + GameMath.format(v)
}
