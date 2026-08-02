package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.Auction
import ru.capital.idle.core.game.AuctionTier
import ru.capital.idle.core.game.Auctions
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle

/**
 * Торги: перебивание, победа, поражение, ворота доступа и возврат из оффлайна.
 *
 * Вся случайность разыгрывается один раз при старте лота и лежит в состоянии,
 * поэтому здесь торги собираются вручную с нужными параметрами — тесты не зависят
 * ни от ГПСЧ, ни от порядка вызовов.
 */
class AuctionsTest {

    /** Лот с заданным пределом зала. Игровые часы состояния — 100.0, торги идут до 172.0. */
    private fun lot(
        itemId: String = "canvas",
        tier: AuctionTier = AuctionTier.OPEN,
        bid: Double = 1_000.0,
        bids: Int = 0,
        playerBids: Int = 0,
        playerLeads: Boolean = false,
        escrow: Double = 0.0,
        limit: Double = 5_000.0,
        step: Double = 0.10,
        replies: Boolean = false,
        replyAt: Double = 100.0,
        endsAt: Double = 172.0
    ) = Auction(
        itemId = itemId, tierOrdinal = tier.ordinal,
        startGameH = 100.0, endsGameH = endsAt,
        bid = bid, bids = bids, playerBids = playerBids,
        playerLeads = playerLeads, playerEscrow = escrow,
        rivalNameIdx = 0, rivalLimit = limit, rivalStepFrac = step,
        rivalReplies = replies, rivalReplyAtGameH = replyAt
    )

    private fun state(money: Double = 1e9, auction: Auction? = lot(), gameH: Double = 100.0) =
        GameState(money = money, gameHours = gameH, auction = auction)

    /**
     * Состояние с доступом на закрытые торги: особняк (85) + Porsche (60) + Patek (55)
     * + Дубай и Монако (35) = 235 статуса при репутации 75.
     */
    private fun elite(money: Double = 1e12) = GameState(
        money = money, gameHours = 100.0, reputation = 75.0,
        ownedHomes = setOf(0, 4), ownedCars = setOf(0, 5), ownedTechs = setOf(0, 4),
        experiencesDone = setOf("dubai", "monaco")
    )

    // ===================== доступность предметов =====================

    @Test
    fun `пять уникальных предметов ушли с рынка и продаются только на торгах`() {
        assertEquals(setOf("rex", "meteor", "crown", "lost", "temple"), Auctions.auctionOnly)
        Auctions.auctionOnly.forEach { id ->
            assertNotNull("предмета $id нет в каталоге коллекции", Collectibles.byId(id))
            assertFalse(Auctions.inCatalog(id))
            assertEquals(AuctionTier.CLOSED, Auctions.tierOf(id))
        }
        // остальные семь как продавались, так и продаются
        val rest = Collectibles.all.map { it.id } - Auctions.auctionOnly
        assertEquals(7, rest.size)
        rest.forEach {
            assertTrue(Auctions.inCatalog(it))
            assertEquals(AuctionTier.OPEN, Auctions.tierOf(it))
        }

        // денег хватает, но уникальный предмет в каталоге не купить
        val rich = GameState(money = 1e12)
        assertTrue(Collectibles.canBuy(rich, "temple"))          // сам предмет не изменился
        assertFalse(Auctions.canBuyInCatalog(rich, "temple"))    // но каталог его не отдаёт
        assertTrue(Auctions.canBuyInCatalog(rich, "litho"))
    }

    @Test
    fun `лоты не повторяют уже собранное`() {
        val st = GameState(collectibles = mapOf("litho" to 1.0, "temple" to 1.0))
        assertTrue(Auctions.lotPool(st, AuctionTier.OPEN).none { it.id == "litho" })
        assertTrue(Auctions.lotPool(st, AuctionTier.CLOSED).none { it.id == "temple" })
        assertEquals(6, Auctions.lotPool(st, AuctionTier.OPEN).size)
        assertEquals(4, Auctions.lotPool(st, AuctionTier.CLOSED).size)
    }

    @Test
    fun `имена соперников влезают в ячейку «ведёт»`() {
        // ячейка однострочная: имя длиннее лимита обрежется без многоточия
        Auctions.rivalNames.forEach { n ->
            assertTrue("«$n» — ${n.length} знаков при лимите ${Auctions.MAX_RIVAL_NAME_LEN}",
                n.length <= Auctions.MAX_RIVAL_NAME_LEN)
            assertTrue(n.isNotBlank())
        }
        // «ведёте вы» показывается в той же ячейке и тоже обязано влезать
        assertTrue("ведёте вы".length <= Auctions.MAX_RIVAL_NAME_LEN)
        // индекс соперника растёт без границ — имя должно находиться по кругу
        assertEquals(Auctions.rivalNames[0], Auctions.rivalName(Auctions.rivalNames.size))
        assertEquals(Auctions.rivalNames[1], Auctions.rivalName(Auctions.rivalNames.size * 3 + 1))
    }

    // ===================== ворота доступа =====================

    @Test
    fun `открытые торги доступны всем, закрытые требуют репутации и статуса`() {
        val fresh = GameState()
        assertTrue(AuctionTier.OPEN.unlocked(fresh))
        assertFalse(AuctionTier.CLOSED.unlocked(fresh))

        assertEquals(60.0, AuctionTier.CLOSED.reqReputation, EPS)
        assertEquals(220, AuctionTier.CLOSED.reqStatus)

        // репутации хватает, статуса нет
        val loud = GameState(reputation = 90.0)
        assertTrue(Lifestyle.socialStatus(loud) < 220)
        assertFalse(AuctionTier.CLOSED.unlocked(loud))

        // статуса хватает, репутации нет
        val quiet = elite().copy(reputation = 10.0)
        assertTrue(Lifestyle.socialStatus(quiet) >= 220)
        assertFalse(AuctionTier.CLOSED.unlocked(quiet))

        // и то и другое
        assertTrue(AuctionTier.CLOSED.unlocked(elite()))
    }

    @Test
    fun `без доступа ставку на закрытый лот сделать нельзя`() {
        val locked = state(auction = lot(itemId = "temple", tier = AuctionTier.CLOSED))
        assertFalse(AuctionTier.CLOSED.unlocked(locked))
        assertFalse(Auctions.canBid(locked, 1_000.0))
        assertNull(Auctions.bid(locked, 1_000.0))

        // с доступом — можно
        val allowed = elite().copy(auction = lot(itemId = "temple", tier = AuctionTier.CLOSED))
        assertTrue(Auctions.canBid(allowed, 1_000.0))
        assertNotNull(Auctions.bid(allowed, 1_000.0))
    }

    // ===================== ставки =====================

    @Test
    fun `первая ставка идёт по стартовой цене, дальше — минимальный перебив`() {
        val first = lot(bid = 1_000.0, bids = 0)
        assertEquals(1_000.0, Auctions.minBid(first), EPS)

        val after = lot(bid = 1_000.0, bids = 1)
        assertEquals(1_050.0, Auctions.minBid(after), EPS)   // +5%
        assertEquals(1_313.0, Auctions.boldBid(after), EPS)  // ceil(1050 × 1.25)
    }

    @Test
    fun `ставка блокирует деньги и делает игрока лидером`() {
        val st = state(money = 10_000.0)
        val after = Auctions.bid(st, 1_000.0)!!

        assertEquals(9_000.0, after.money, EPS)
        val a = after.auction!!
        assertTrue(a.playerLeads)
        assertEquals(1_000.0, a.bid, EPS)
        assertEquals(1_000.0, a.playerEscrow, EPS)
        assertEquals(1, a.bids)
        assertEquals(1, a.playerBids)
        // зал получил время на ответ
        assertTrue(a.rivalReplyAtGameH > st.gameHours)
    }

    @Test
    fun `нельзя поставить ниже минимума, больше чем есть денег и перебить самого себя`() {
        val st = state(money = 10_000.0, auction = lot(bid = 1_000.0, bids = 1))
        assertFalse(Auctions.canBid(st, 1_049.0))
        assertNull(Auctions.bid(st, 1_049.0))

        val poor = state(money = 500.0)
        assertFalse(Auctions.canBid(poor, 1_000.0))
        assertNull(Auctions.bid(poor, 1_000.0))

        val leading = state(auction = lot(bid = 1_000.0, bids = 1, playerLeads = true, escrow = 1_000.0))
        assertFalse(Auctions.canBid(leading, 5_000.0))
        assertNull(Auctions.bid(leading, 5_000.0))

        // и без торгов ставить не во что
        assertNull(Auctions.bid(GameState(), 100.0))
    }

    @Test
    fun `ставка на флажке продлевает торги — снайпинг не выигрывает сам по себе`() {
        // до конца 3 часа, меньше запаса антиснайпинга
        val st = state(money = 1e6, auction = lot(endsAt = 103.0))
        val after = Auctions.bid(st, 1_000.0)!!
        assertEquals(100.0 + Auctions.ANTISNIPE_H, after.auction!!.endsGameH, EPS)
        // запас заведомо больше самой долгой паузы зала: ответить он успевает всегда
        assertTrue(after.auction!!.rivalReplyAtGameH < after.auction!!.endsGameH)

        // а обычная ставка время не двигает
        val early = Auctions.bid(state(money = 1e6), 1_000.0)!!
        assertEquals(172.0, early.auction!!.endsGameH, EPS)
    }

    // ===================== перебивание =====================

    @Test
    fun `зал перебивает и возвращает залог полностью`() {
        var st = state(money = 10_000.0)
        st = Auctions.bid(st, 1_000.0)!!
        assertEquals(9_000.0, st.money, EPS)

        val replyAt = st.auction!!.rivalReplyAtGameH
        st = Auctions.advance(st, replyAt).state

        val a = st.auction!!
        assertFalse("зал должен был перебить", a.playerLeads)
        assertEquals(1_100.0, a.bid, EPS)             // +10% шага
        assertEquals(0.0, a.playerEscrow, EPS)
        assertEquals(10_000.0, st.money, EPS)         // деньги вернулись целиком
        assertEquals(2, a.bids)
        assertEquals(1, a.playerBids)
        // ставку перехватил следующий участник зала — видно, что соперник не один
        assertEquals(1, a.rivalNameIdx)
    }

    @Test
    fun `зал молчит, когда ставка выше его скрытого предела`() {
        var st = state(money = 1e6, auction = lot(limit = 2_000.0))
        st = Auctions.bid(st, 1_900.0)!!              // 1900 × 1.1 = 2090 > предела 2000
        st = Auctions.advance(st, st.auction!!.rivalReplyAtGameH).state

        val a = st.auction!!
        assertTrue("зал не должен был перебивать", a.playerLeads)
        assertEquals(1_900.0, a.bid, EPS)
        assertFalse("отвечать больше не будет", a.rivalReplies)
    }

    @Test
    fun `молчание зала — отдельный флаг, а не особое значение времени`() {
        // после сдвига меток при возврате из оффлайна время ответа становится отрицательным;
        // если бы «зал молчит» кодировалось значением -1, эти два случая слились бы
        var st = state(money = 10_000.0)
        st = Auctions.bid(st, 1_000.0)!!
        assertTrue(st.auction!!.rivalReplies)

        val shifted = Auctions.skipOffline(st, 1.0).state.auction!!
        assertTrue("зал ещё не отвечал — флаг обязан остаться", shifted.rivalReplies)
        assertTrue("а метка времени вполне может уехать в минус", shifted.rivalReplyAtGameH < 110.0)
    }

    // ===================== исходы =====================

    @Test
    fun `лидер к концу торгов забирает предмет по своей ставке`() {
        var st = state(money = 1e6, auction = lot(limit = 2_000.0))
        st = Auctions.bid(st, 1_900.0)!!
        val adv = Auctions.advance(st, 200.0)         // после endsGameH = 172

        val after = adv.state
        assertNull("торги должны закрыться", after.auction)
        assertTrue(Collectibles.owns(after, "canvas"))
        assertEquals(1_900.0, Collectibles.paidFor(after, "canvas"), EPS)
        // деньги списаны один раз — при ставке, повторно при победе не берутся
        assertEquals(1e6 - 1_900.0, after.money, EPS)

        assertEquals(Auctions.Ended("canvas", 1_900.0, won = true), adv.ended)
        // следующие торги назначены
        assertEquals(172.0 + Auctions.GAP_H, after.auctionNextGameH, EPS)
    }

    @Test
    fun `перебитый игрок остаётся при деньгах и без предмета`() {
        var st = state(money = 10_000.0)
        st = Auctions.bid(st, 1_000.0)!!
        val adv = Auctions.advance(st, 200.0)

        val after = adv.state
        assertNull(after.auction)
        assertFalse(Collectibles.owns(after, "canvas"))
        assertEquals("проигрыш не стоит денег", 10_000.0, after.money, EPS)
        assertEquals(false, adv.ended?.won)
        assertEquals("canvas", adv.ended?.itemId)
    }

    @Test
    fun `лот, мимо которого игрок прошёл молча, не даёт уведомления`() {
        val st = state(money = 10_000.0)
        val adv = Auctions.advance(st, 200.0)
        assertNull("сообщать не о чем — игрок не торговался", adv.ended)
        assertFalse(Collectibles.owns(adv.state, "canvas"))
        assertEquals(10_000.0, adv.state.money, EPS)
    }

    @Test
    fun `переплатить можно — зал бросает торг задолго до предела игрока`() {
        // предел зала 1200, а игрок сразу даёт 5000: предмет его, но втридорога
        var st = state(money = 1e6, auction = lot(limit = 1_200.0))
        st = Auctions.bid(st, 5_000.0)!!
        val after = Auctions.advance(st, 200.0).state
        assertEquals(5_000.0, Collectibles.paidFor(after, "canvas"), EPS)
        // осторожная ставка взяла бы тот же предмет заметно дешевле
        var careful = state(money = 1e6, auction = lot(limit = 1_200.0))
        careful = Auctions.bid(careful, 1_100.0)!!    // 1100 × 1.1 = 1210 > 1200
        val cheap = Auctions.advance(careful, 200.0).state
        assertEquals(1_100.0, Collectibles.paidFor(cheap, "canvas"), EPS)
    }

    // ===================== ход времени =====================

    @Test
    fun `мелкие шаги и один большой дают одинаковый результат`() {
        var stepwise = state(money = 1e6)
        stepwise = Auctions.bid(stepwise, 1_000.0)!!
        val atOnce = Auctions.advance(stepwise, 260.0).state

        var s = stepwise
        var t = 100.0
        while (t <= 260.0) {
            s = Auctions.advance(s, t).state
            t += 0.5
        }
        // сравниваем всё, что торги могли изменить
        assertEquals(atOnce.money, s.money, EPS)
        assertEquals(atOnce.collectibles, s.collectibles)
        assertEquals(atOnce.auction?.itemId, s.auction?.itemId)
        assertEquals(atOnce.auction?.bid ?: 0.0, s.auction?.bid ?: 0.0, EPS)
        assertEquals(atOnce.auctionNextGameH, s.auctionNextGameH, EPS)
    }

    @Test
    fun `после паузы открываются новые торги`() {
        val st = GameState(money = 1e12, gameHours = 100.0, auctionNextGameH = 120.0, auctionSeed = 7L)
        assertNull(Auctions.advance(st, 119.0).state.auction)   // рано

        val opened = Auctions.advance(st, 120.0).state
        val a = opened.auction!!
        assertEquals(120.0, a.startGameH, EPS)
        assertEquals(120.0 + Auctions.LENGTH_H, a.endsGameH, EPS)
        assertEquals(0, a.bids)
        assertFalse(a.playerLeads)
        // стартовая цена — доля каталожной
        val item = Collectibles.byId(a.itemId)!!
        val price = Collectibles.priceIn(item, st)
        assertEquals(kotlin.math.floor(price * a.tier.startFraction), a.bid, EPS)
        // предел зала лежит в границах уровня
        assertTrue(a.rivalLimit >= price * a.tier.limitMin)
        assertTrue(a.rivalLimit <= price * a.tier.limitMax)
    }

    @Test
    fun `когда собрано всё, торговать нечем`() {
        val everything = GameState(
            money = 1e12, gameHours = 100.0, auctionNextGameH = 100.0,
            collectibles = Collectibles.all.associate { it.id to 1.0 }
        )
        val after = Auctions.advance(everything, 100.0)
        assertNull(after.state.auction)
        assertNull(Auctions.start(everything, 100.0))
        assertTrue(after.state.auctionNextGameH > 100.0)
    }

    // ===================== перерождение =====================

    @Test
    fun `торги прошлой жизни не должны переезжать через престиж`() {
        // GameViewModel.prestige() обнуляет auction и auctionNextGameH; проверяем, что
        // без этого залог и выигранный предмет действительно перетекли бы в новую жизнь
        var old = state(money = 1e6, auction = lot(limit = 2_000.0))
        old = Auctions.bid(old, 1_900.0)!!

        // так выглядит перерождение БЕЗ обнуления торгов: деньги, коллекция и часы сброшены
        val leaked = old.copy(
            money = 0.0, collectibles = emptyMap(), gameHours = 8.0, totalEarned = 0.0
        )
        val after = Auctions.advance(leaked, 200.0).state
        assertTrue("иначе предмет прошлой жизни достался бы новой",
            Collectibles.owns(after, "canvas"))

        // а с обнулением (как в prestige) переезжать нечему
        val clean = leaked.copy(auction = null, auctionNextGameH = 0.0)
        val cleanAfter = Auctions.advance(clean, 8.0).state
        assertFalse(Collectibles.owns(cleanAfter, "canvas"))
        assertEquals(0.0, cleanAfter.money, EPS)
    }

    // ===================== оффлайн =====================

    @Test
    fun `за время отсутствия зал перебивает — предмет уходит`() {
        var st = state(money = 10_000.0)
        st = Auctions.bid(st, 1_000.0)!!

        // ушли на 200 игровых часов: лот успел закрыться без нас
        val adv = Auctions.skipOffline(st, 200.0)
        assertFalse("предмет ушёл к сопернику", Collectibles.owns(adv.state, "canvas"))
        assertEquals("залог вернулся полностью", 10_000.0, adv.state.money, EPS)
        assertEquals(false, adv.ended?.won)
        assertEquals("canvas", adv.ended?.itemId)
        // тот лот закрыт; вместо него зал успел выставить следующий — и он ещё идёт
        assertTrue(adv.state.auction == null || adv.state.auction!!.itemId != "canvas")
        adv.state.auction?.let {
            assertEquals("новый лот торгуется с нуля", 0, it.bids)
            assertTrue("и не может быть уже закрытым", it.endsGameH > adv.state.gameHours)
        }
    }

    @Test
    fun `за время отсутствия выигранный лот достаётся игроку`() {
        var st = state(money = 1e6, auction = lot(limit = 2_000.0))
        st = Auctions.bid(st, 1_900.0)!!             // выше предела зала

        val adv = Auctions.skipOffline(st, 200.0)
        assertTrue(Collectibles.owns(adv.state, "canvas"))
        assertEquals(1_900.0, Collectibles.paidFor(adv.state, "canvas"), EPS)
        assertEquals(true, adv.ended?.won)
        // игровые часы оффлайн не идут — их сдвигать нельзя, двигаются только торги
        assertEquals(100.0, adv.state.gameHours, EPS)
    }

    @Test
    fun `короткое отсутствие торги не закрывает, но зал успевает ответить`() {
        var st = state(money = 10_000.0)
        st = Auctions.bid(st, 1_000.0)!!
        val delay = st.auction!!.rivalReplyAtGameH - st.gameHours

        val adv = Auctions.skipOffline(st, delay)
        val a = adv.state.auction
        assertNotNull("торги ещё идут", a)
        assertFalse(a!!.playerLeads)
        assertEquals(10_000.0, adv.state.money, EPS)
        assertNull("лот не закрылся — сообщать не о чем", adv.ended)
        // до конца торгов осталось меньше, чем было
        assertTrue(Auctions.hoursLeft(a, adv.state.gameHours) < 72.0)
    }

    @Test
    fun `нулевое и отрицательное отсутствие ничего не меняют`() {
        val st = Auctions.bid(state(money = 10_000.0), 1_000.0)!!
        assertEquals(st, Auctions.skipOffline(st, 0.0).state)
        assertEquals(st, Auctions.skipOffline(st, -5.0).state)
    }

    @Test
    fun `после долгого отсутствия лот не висит в прошлом, а открывается свежий`() {
        val st = GameState(money = 1e12, gameHours = 100.0, auctionNextGameH = 101.0, auctionSeed = 3L)
        // отсутствовали столько, что назначенные торги успели бы и начаться, и кончиться
        val after = Auctions.skipOffline(st, 10_000.0).state
        val a = after.auction!!
        assertEquals("торги должны начаться от текущего момента", 100.0, a.startGameH, EPS)
        assertTrue(a.endsGameH > 100.0)
    }
}
