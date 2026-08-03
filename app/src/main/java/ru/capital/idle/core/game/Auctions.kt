package ru.capital.idle.core.game

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Уровень торгов. Обычные открыты всем, закрытые требуют репутации и социального статуса.
 *
 * Пороги закрытых торгов выверены по существующей прогрессии: репутация сама дрейфует
 * к `20 + статус × 0.3` (см. тик в GameViewModel), поэтому репутация 60 держится
 * при статусе примерно от 135. Статус 220 — это уже приличный дом, машина и пара выездов
 * (особняк 85 + Porsche 60 + гардероб 30 + Дубай и Монако 35 = 210) плюс что-нибудь из коллекции.
 * Цель достижимая, но не мгновенная.
 */
enum class AuctionTier(
    val title: String,
    val reqReputation: Double,
    val reqStatus: Int,
    /** Стартовая цена лота в долях текущей каталожной цены предмета. */
    val startFraction: Double,
    /** Границы скрытого предела зала, в долях каталожной цены. */
    val limitMin: Double,
    val limitMax: Double
) {
    OPEN("Открытые торги", 0.0, 0, 0.60, 0.85, 1.35),
    CLOSED("Закрытые торги", 60.0, 220, 0.80, 1.10, 1.90);

    fun unlocked(state: GameState): Boolean =
        state.reputation >= reqReputation && Lifestyle.socialStatus(state) >= reqStatus
}

/**
 * Активные торги. Всё, что разыгрывается случайно, разыгрывается ОДИН раз при старте
 * и лежит здесь — дальше торги ведут себя как чистая функция от времени. Благодаря этому
 * результат не зависит от того, тикала игра часто или пролежала свёрнутой (см. `Auctions.advance`).
 *
 * @param bid текущая ставка
 * @param bids сколько ставок сделано; 0 — торгуется стартовая цена, никто ещё не ставил
 * @param playerLeads ведёт ли игрок
 * @param playerEscrow сколько денег игрока заблокировано под его ставкой (0 — игрок не ставил)
 * @param rivalLimit скрытый предел зала: выше этой суммы соперники не идут
 * @param rivalStepFrac на сколько зал поднимает ставку за раз
 * @param rivalReplies собирается ли зал отвечать; отдельным флагом, а не «особым» значением
 *   времени — при возврате из оффлайна метки сдвигаются назад и могут стать отрицательными,
 *   так что сентинел вроде -1 был бы неотличим от честного времени
 * @param rivalReplyAtGameH игровой час ответа зала; осмысленно только при `rivalReplies`
 */
data class Auction(
    val itemId: String,
    val tierOrdinal: Int,
    val startGameH: Double,
    val endsGameH: Double,
    val bid: Double,
    val bids: Int,
    /** Сколько ставок сделал игрок. Нужно, чтобы отличить проигрыш от лота, мимо которого он прошёл. */
    val playerBids: Int,
    val playerLeads: Boolean,
    val playerEscrow: Double,
    val rivalNameIdx: Int,
    val rivalLimit: Double,
    val rivalStepFrac: Double,
    val rivalReplies: Boolean,
    val rivalReplyAtGameH: Double
) {
    val tier: AuctionTier
        get() = AuctionTier.entries.getOrElse(tierOrdinal) { AuctionTier.OPEN }
}

object Auctions {

    // ===================== константы торгов =====================

    /** Длительность торгов в игровых часах (1 игровой час = 1 реальная секунда). */
    const val LENGTH_H = 72.0

    /** Пауза между торгами. */
    const val GAP_H = 120.0

    /**
     * Антиснайпинг: ставка в последние часы продлевает торги до этого запаса.
     * Больше самой долгой паузы зала (18 ч) — иначе выигрышной стратегией стало бы
     * «поставить минимум в последнюю секунду», и торги превратились бы в таймер.
     */
    const val ANTISNIPE_H = 20.0

    /** Минимальный перебив — доля от текущей ставки. */
    const val MIN_RAISE = 0.05

    /** Кнопка «уверенная ставка»: во столько раз выше минимального перебива. */
    const val BOLD_RAISE_MULT = 1.25

    private const val RIVAL_DELAY_MIN_H = 6.0
    private const val RIVAL_DELAY_MAX_H = 18.0
    private const val RIVAL_STEP_MIN = 0.05
    private const val RIVAL_STEP_MAX = 0.12

    /**
     * Предметы, которых больше нет в каталоге: только с торгов.
     * Это пять уникальных лотов — самые дорогие в коллекции. Сами предметы и их цены
     * не менялись, изменился только способ их получить.
     */
    val auctionOnly = setOf("rex", "meteor", "crown", "lost", "temple")

    /**
     * Предельная длина имени соперника. Имя показывается в ячейке «ВЕДЁТ» одной строкой,
     * и при системном шрифте 1.5 туда влезает около тринадцати знаков — длиннее обрежется.
     */
    const val MAX_RIVAL_NAME_LEN = 13

    /** Соперники в зале. Хранится индекс, а не текст — сейв не зависит от формулировок. */
    val rivalNames = listOf(
        "Аноним",
        "По телефону",
        "Частный фонд",
        "Из Женевы",
        "Наследник",
        "Представитель",
        "Музей",
        "Первый ряд"
    )

    fun rivalName(idx: Int): String = rivalNames[idx.mod(rivalNames.size)]

    // ===================== доступность =====================

    /** Продаётся ли предмет в каталоге. Уникальные лоты — нет. */
    fun inCatalog(id: String): Boolean = id !in auctionOnly

    /** Можно ли купить предмет прямо в каталоге: и правило доступности, и деньги. */
    fun canBuyInCatalog(state: GameState, id: String): Boolean =
        inCatalog(id) && Collectibles.canBuy(state, id)

    /** На каком уровне торгов выставляется предмет. */
    fun tierOf(id: String): AuctionTier =
        if (id in auctionOnly) AuctionTier.CLOSED else AuctionTier.OPEN

    /** Предметы, которые могут выпасть лотом на этом уровне: чего у игрока ещё нет. */
    fun lotPool(state: GameState, tier: AuctionTier): List<Collectible> =
        Collectibles.all.filter { tierOf(it.id) == tier && !Collectibles.owns(state, it.id) }

    /**
     * Осталось ли что выставлять на торги. Когда собрано всё, следующего лота не будет
     * никогда — ждать нечего, и интерфейс не должен обещать таймер.
     */
    fun hasLotsLeft(state: GameState): Boolean =
        Collectibles.all.any { !Collectibles.owns(state, it.id) }

    // ===================== ставки =====================

    /** Наименьшая допустимая ставка: стартовая цена, если никто ещё не ставил. */
    fun minBid(a: Auction): Double =
        if (a.bids == 0) ceil(a.bid) else ceil(a.bid * (1.0 + MIN_RAISE))

    /** Уверенная ставка: с запасом над минимальным перебивом. */
    fun boldBid(a: Auction): Double = ceil(minBid(a) * BOLD_RAISE_MULT)

    /** Может ли игрок сейчас поставить указанную сумму. */
    fun canBid(state: GameState, amount: Double): Boolean {
        val a = state.auction ?: return false
        if (!a.tier.unlocked(state)) return false
        if (a.playerLeads) return false              // перебивать самого себя нельзя
        return amount >= minBid(a) && state.money >= amount
    }

    /**
     * Поставить. Деньги блокируются сразу: так не бывает выигрыша, за который нечем платить.
     * При перебиве зал возвращает их полностью — проигрыш не стоит игроку ничего.
     */
    fun bid(state: GameState, amount: Double): GameState? {
        val a = state.auction ?: return null
        if (!canBid(state, amount)) return null
        val now = state.gameHours
        // антиснайпинг: ставка на флажке продлевает торги, чтобы зал успел ответить
        val ends = if (a.endsGameH - now < ANTISNIPE_H) now + ANTISNIPE_H else a.endsGameH
        return state.copy(
            money = state.money - amount,
            auction = a.copy(
                bid = amount,
                bids = a.bids + 1,
                playerBids = a.playerBids + 1,
                playerLeads = true,
                playerEscrow = amount,
                endsGameH = ends,
                rivalReplies = true,
                rivalReplyAtGameH = now + rivalDelayH(a)
            )
        )
    }

    /** Пауза зала перед ответом. Разыграна при старте и зашита в шаг соперников. */
    private fun rivalDelayH(a: Auction): Double {
        // шаг зала лежит в [RIVAL_STEP_MIN, RIVAL_STEP_MAX]; тем же положением задаём и темп:
        // напористый зал (крупный шаг) отвечает быстрее
        val f = ((a.rivalStepFrac - RIVAL_STEP_MIN) / (RIVAL_STEP_MAX - RIVAL_STEP_MIN)).coerceIn(0.0, 1.0)
        return RIVAL_DELAY_MAX_H - (RIVAL_DELAY_MAX_H - RIVAL_DELAY_MIN_H) * f
    }

    // ===================== ход времени =====================

    /** Чем закончились торги. Нужен интерфейсу для сообщения игроку. */
    data class Ended(val itemId: String, val price: Double, val won: Boolean) {
        /** Код и параметр записи в хронику: исход лота должен пережить перезапуск. */
        val chronicleCode: String get() = if (won) "auc+" else "auc-"
        val chronicleParam: String get() = "$itemId:$price"
    }

    data class Advance(val state: GameState, val ended: Ended? = null)

    /**
     * Прокрутить торги до указанного игрового часа.
     *
     * Одна и та же функция работает и в обычном тике, и при возврате из оффлайна:
     * вся случайность разыграна при старте лота, поэтому сто мелких шагов дают ровно то же,
     * что один большой. Торги **не замораживаются** на время отсутствия игрока — зал
     * продолжает перебивать по своим правилам. Замораживать было бы нечестно (ставка «зал
     * ответит через N часов» переставала бы работать), а аннулировать — обесценивало бы
     * уже сделанную ставку.
     */
    fun advance(state: GameState, nowGameH: Double): Advance {
        var st = state
        var ended: Ended? = null
        var guard = 0
        while (guard++ < 64) {
            val a = st.auction
            if (a != null) {
                st = runRival(st, minOf(nowGameH, a.endsGameH))
                if (nowGameH < st.auction!!.endsGameH) break
                val fin = finish(st)
                st = fin.state
                if (fin.ended != null) ended = fin.ended
                continue
            }
            if (nowGameH < st.auctionNextGameH) break
            // торги, которые успели бы и начаться, и кончиться без игрока, никому не интересны:
            // вместо мёртвого лота в прошлом открываем свежий прямо сейчас
            val startAt = if (nowGameH >= st.auctionNextGameH + LENGTH_H) nowGameH else st.auctionNextGameH
            val opened = start(st, startAt)
            if (opened == null) {
                // выставлять нечего — вся коллекция собрана; попробуем позже
                st = st.copy(auctionNextGameH = nowGameH + GAP_H)
                break
            }
            st = opened
        }
        return Advance(st, ended)
    }

    /**
     * Возврат из оффлайна. Общие игровые часы, пока игра закрыта, стоят на месте
     * (см. `applyOfflineProgress`), поэтому двигаем не их, а сами торги: сдвигаем метки
     * лота назад на прошедшее время и прокручиваем обычным `advance`. Результат тот же,
     * что если бы игра всё это время работала, и ничего, кроме торгов, не задето.
     */
    fun skipOffline(state: GameState, hoursAway: Double): Advance {
        if (hoursAway <= 0.0) return Advance(state)
        val a = state.auction
        val shifted = state.copy(
            auction = a?.copy(
                startGameH = a.startGameH - hoursAway,
                endsGameH = a.endsGameH - hoursAway,
                rivalReplyAtGameH = a.rivalReplyAtGameH - hoursAway
            ),
            auctionNextGameH = state.auctionNextGameH - hoursAway
        )
        return advance(shifted, state.gameHours)
    }

    /** Ответы зала до указанного момента. */
    private fun runRival(state: GameState, until: Double): GameState {
        var st = state
        var steps = 0
        while (steps++ < 64) {
            val a = st.auction ?: break
            if (!a.rivalReplies || a.rivalReplyAtGameH > until) break
            if (!a.playerLeads) {
                st = st.copy(auction = a.copy(rivalReplies = false))
                continue
            }
            val next = ceil(a.bid * (1.0 + a.rivalStepFrac))
            if (next > a.rivalLimit) {
                // зал упёрся в свой предел и молчит до конца торгов
                st = st.copy(auction = a.copy(rivalReplies = false))
            } else {
                // перебили: залог игрока возвращается сразу, проигрыш ничего не стоит
                st = st.copy(
                    money = st.money + a.playerEscrow,
                    auction = a.copy(
                        bid = next,
                        bids = a.bids + 1,
                        playerLeads = false,
                        playerEscrow = 0.0,
                        // ставку перехватывает следующий в зале — видно, что соперник не один
                        rivalNameIdx = a.rivalNameIdx + 1,
                        rivalReplies = false
                    )
                )
            }
        }
        return st
    }

    /** Закрыть торги: отдать предмет победителю и назначить следующие. */
    private fun finish(state: GameState): Advance {
        val a = state.auction ?: return Advance(state)
        val next = a.endsGameH + GAP_H
        return if (a.playerLeads) {
            // деньги уже списаны при ставке — остаётся записать предмет и цену покупки
            Advance(
                state.copy(
                    collectibles = state.collectibles + (a.itemId to a.playerEscrow),
                    auction = null,
                    auctionNextGameH = next
                ),
                Ended(a.itemId, a.playerEscrow, won = true)
            )
        } else {
            // залог игроку уже вернули в момент перебива; сообщаем о проигрыше только тому,
            // кто действительно торговался — мимо чужого лота игрок прошёл молча
            Advance(
                state.copy(auction = null, auctionNextGameH = next),
                if (a.playerBids > 0) Ended(a.itemId, a.bid, won = false) else null
            )
        }
    }

    // ===================== старт лота =====================

    /**
     * Открыть новые торги. Уровень выбирается сам: закрытые лоты выставляются, пока они есть,
     * иначе идут открытые. Доступ игрока на выбор лота не влияет — закрытые торги идут
     * своим чередом, просто без него.
     *
     * Возвращает null, если выставлять нечего (вся коллекция собрана).
     */
    fun start(state: GameState, atGameH: Double): GameState? {
        var seed = nextSeed(seedOf(state))
        // лоты обоих уровней в общем котле: закрытые выставляются и без доступа у игрока —
        // так видно, что именно закрыто, и есть за чем тянуться. Открытые при этом
        // не пропадают, и раздел не мертвеет ни до, ни после получения доступа
        val pool = lotPool(state, AuctionTier.OPEN) + lotPool(state, AuctionTier.CLOSED)
        if (pool.isEmpty()) return null

        val (s1, pick) = roll(seed, pool.size); seed = s1
        val item = pool[pick]
        val tier = tierOf(item.id)
        val price = Collectibles.priceIn(item, state)

        val (s2, limitR) = rollUnit(seed); seed = s2
        val (s3, stepR) = rollUnit(seed); seed = s3
        val (s4, nameR) = roll(seed, rivalNames.size); seed = s4

        return state.copy(
            auctionSeed = seed,
            auctionNextGameH = atGameH + LENGTH_H + GAP_H,
            auction = Auction(
                itemId = item.id,
                tierOrdinal = tier.ordinal,
                startGameH = atGameH,
                endsGameH = atGameH + LENGTH_H,
                bid = floor(price * tier.startFraction),
                bids = 0,
                playerBids = 0,
                playerLeads = false,
                playerEscrow = 0.0,
                rivalNameIdx = nameR,
                rivalLimit = price * (tier.limitMin + (tier.limitMax - tier.limitMin) * limitR),
                rivalStepFrac = RIVAL_STEP_MIN + (RIVAL_STEP_MAX - RIVAL_STEP_MIN) * stepR,
                rivalReplies = false,
                rivalReplyAtGameH = atGameH
            )
        )
    }

    // ===================== простой ГПСЧ =====================
    // Свой, а не java.util.Random: результат торгов обязан быть воспроизводимым
    // и не зависеть от платформы. Классический LCG из Numerical Recipes.

    private const val LCG_A = 1_664_525L
    private const val LCG_C = 1_013_904_223L
    private const val LCG_M = 1L shl 32

    fun nextSeed(seed: Long): Long = (LCG_A * seed + LCG_C).mod(LCG_M)

    /**
     * Посев для этой партии. У новой игры поле нулевое, и без подмешивания даты старта
     * все партии шли бы по одному и тому же сценарию торгов.
     */
    private fun seedOf(state: GameState): Long {
        if (state.auctionSeed != 0L) return state.auctionSeed
        val fromDate = state.startDateMillis.mod(LCG_M)
        return if (fromDate != 0L) fromDate else 1L
    }

    private fun roll(seed: Long, bound: Int): Pair<Long, Int> {
        val s = nextSeed(seed)
        return s to (s % bound).toInt()
    }

    private fun rollUnit(seed: Long): Pair<Long, Double> {
        val s = nextSeed(seed)
        return s to s.toDouble() / LCG_M
    }

    // ===================== показания для интерфейса =====================

    /** Сколько игровых часов осталось до конца торгов. */
    fun hoursLeft(a: Auction, nowGameH: Double): Double = (a.endsGameH - nowGameH).coerceAtLeast(0.0)

    /** Доля пройденного времени торгов — для полоски. */
    fun timeFraction(a: Auction, nowGameH: Double): Float {
        val total = a.endsGameH - a.startGameH
        if (total <= 0.0) return 1f
        return ((nowGameH - a.startGameH) / total).coerceIn(0.0, 1.0).toFloat()
    }

    /** Кто сейчас ведёт: игрок, соперник или ещё никто. */
    fun leaderTitle(a: Auction): String = when {
        a.bids == 0 -> "стартовая цена"
        a.playerLeads -> "ведёте вы"
        else -> rivalName(a.rivalNameIdx)
    }

    /** «2 д 6 ч» — оставшееся время в игровых сутках и часах. */
    fun formatLeft(hours: Double): String {
        val h = ceil(hours).toInt().coerceAtLeast(0)
        val d = h / 24
        val rest = h % 24
        return if (d > 0) "$d д $rest ч" else "$rest ч"
    }
}
