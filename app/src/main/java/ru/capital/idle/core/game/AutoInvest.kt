package ru.capital.idle.core.game

/**
 * Автовклад: раз в игровой день переносит деньги с карты во вклад.
 *
 * **Это не капитализация.** Капитализация оставляет во вкладе его собственный доход —
 * тело растёт само. Автовклад берёт деньги с карты (зарплата, выручка бизнесов, тапы)
 * и кладёт их в тело вклада. Вещи разные и работают вместе.
 *
 * Резерв — обязательная часть механики, а не украшение: без него на карте не осталось бы
 * ни доллара, и игрок не смог бы ничего купить, не сняв деньги вручную.
 */
object AutoInvest {

    /**
     * Ступени резерва. Игрок ходит по ним кнопками «−» и «+»: суммы в игре растут
     * на порядки, и ползунок с шагом в тысячу был бы бесполезен уже к первому миллиону.
     */
    val RESERVE_STEPS: List<Double> =
        listOf(0.0, 100.0, 1_000.0, 10_000.0, 100_000.0, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12)

    /** Следующая ступень резерва вверх (или та же, если уже последняя). */
    fun stepUp(reserve: Double): Double =
        RESERVE_STEPS.firstOrNull { it > reserve + 1e-9 } ?: RESERVE_STEPS.last()

    /** Следующая ступень резерва вниз (или ноль). */
    fun stepDown(reserve: Double): Double =
        RESERVE_STEPS.lastOrNull { it < reserve - 1e-9 } ?: RESERVE_STEPS.first()

    /** Инструменты, которые игроку уже доступны. */
    fun available(s: GameState): List<Asset> = Asset.entries.filter { it.reqCourse in s.eduDone }

    /**
     * Куда пойдут деньги: закреплённый игроком инструмент, а если он ещё не открыт
     * (или не выбран) — лучший доступный. `null` — открывать нечего.
     */
    fun target(s: GameState): Asset? =
        Asset.entries.getOrNull(s.autoInvestAsset)?.takeIf { it.reqCourse in s.eduDone }
            ?: available(s).lastOrNull()

    /**
     * Сколько уйдёт во вклад при ближайшем срабатывании. Ноль — не сработает.
     *
     * Долг важнее вклада: класть деньги под процент, будучи в минусе, бессмысленно —
     * долг растёт быстрее любой ставки.
     */
    fun amount(s: GameState): Double {
        if (!s.autoInvestOn) return 0.0
        if (s.debt > 0.0) return 0.0
        if (target(s) == null) return 0.0
        return (s.money - s.autoInvestReserve).coerceAtLeast(0.0)
    }

    const val REASON_DEBT = "сначала гасим долг"
    const val REASON_NO_ASSETS = "нет открытых инструментов"
    const val REASON_BELOW_RESERVE = "на карте не больше резерва"

    /**
     * Все причины, по которым автовклад может не сработать, — закрытый список.
     *
     * Список нужен вёрстке: место под итог держится постоянной высоты, а для этого карточка
     * измеряет каждую возможную причину (см. `AutoInvestCard`). Список и [blockedReason]
     * обязаны ходить парой — это пришито тестом.
     */
    val REASONS: List<String> = listOf(REASON_DEBT, REASON_NO_ASSETS, REASON_BELOW_RESERVE)

    /** Почему автовклад не сработает прямо сейчас (или `null`, если сработает). */
    fun blockedReason(s: GameState): String? = when {
        !s.autoInvestOn -> null
        s.debt > 0.0 -> REASON_DEBT
        target(s) == null -> REASON_NO_ASSETS
        s.money <= s.autoInvestReserve -> REASON_BELOW_RESERVE
        else -> null
    }

    /**
     * Одно срабатывание. Вызывается на смене игрового дня и один раз при возвращении
     * из оффлайна — не по разу за каждый пропущенный день.
     */
    fun apply(s: GameState): GameState {
        val amount = amount(s)
        if (amount <= 0.0) return s
        val i = (target(s) ?: return s).ordinal
        return s.copy(
            money = s.money - amount,
            investValues = s.investValues.toMutableList().also { it[i] = it[i] + amount },
            investCosts = s.investCosts.toMutableList().also { it[i] = it[i] + amount }
        )
    }
}
