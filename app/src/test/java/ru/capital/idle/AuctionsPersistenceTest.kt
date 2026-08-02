package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.Auction
import ru.capital.idle.core.game.AuctionTier
import ru.capital.idle.core.game.Auctions
import ru.capital.idle.core.game.GameState
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Торги через слои хранения: GameState -> GameEntity -> GameState.
 * Активный лот пишется одной строкой, поэтому проверяем каждое поле — потеря любого
 * из них поменяла бы исход торгов после перезапуска.
 */
class AuctionsPersistenceTest {

    private val lot = Auction(
        itemId = "crown", tierOrdinal = AuctionTier.CLOSED.ordinal,
        startGameH = 480.0, endsGameH = 552.0,
        bid = 5_120_000_000.0, bids = 4, playerBids = 2,
        playerLeads = true, playerEscrow = 5_120_000_000.0,
        rivalNameIdx = 3, rivalLimit = 6_034_500_000.75,
        rivalStepFrac = 0.0725, rivalReplies = true, rivalReplyAtGameH = 519.5
    )

    @Test
    fun `отсутствие торгов сохраняется пустой строкой`() {
        val e = GameState().toEntity()
        assertEquals("", e.auctionRaw)
        assertNull(e.toState().auction)
    }

    @Test
    fun `активный лот переживает круг записи-чтения целиком`() {
        val src = GameState(auction = lot, auctionNextGameH = 672.0, auctionSeed = 987_654_321L)
        val back = src.toEntity().toState()

        assertEquals(lot, back.auction)
        assertEquals(672.0, back.auctionNextGameH, EPS)
        assertEquals(987_654_321L, back.auctionSeed)
    }

    @Test
    fun `дробные параметры зала не теряют точности`() {
        val back = GameState(auction = lot).toEntity().toState().auction!!
        assertEquals(lot.rivalLimit, back.rivalLimit, 1e-9)
        assertEquals(lot.rivalStepFrac, back.rivalStepFrac, 1e-12)
        assertEquals(lot.rivalReplyAtGameH, back.rivalReplyAtGameH, 1e-9)
    }

    @Test
    fun `флаг молчания зала не путается с временем ответа`() {
        val silent = lot.copy(rivalReplies = false, rivalReplyAtGameH = -90.5)
        val back = GameState(auction = silent).toEntity().toState().auction!!
        assertEquals(false, back.rivalReplies)
        assertEquals(-90.5, back.rivalReplyAtGameH, EPS)
    }

    @Test
    fun `сейв без торгов читается как отсутствие торгов`() {
        // старый файл: ключей торгов в нём нет вовсе
        val old = GameState(money = 5_000.0).toEntity().copy(auctionRaw = "")
        val st = old.toState()
        assertNull(st.auction)
        assertEquals(0.0, st.auctionNextGameH, EPS)
        assertEquals(5_000.0, st.money, EPS)
    }

    @Test
    fun `битая строка лота не роняет загрузку`() {
        listOf("", "мусор", "crown|1|2", "crown|x|y|z|1|2|3|4|5|6|7|8|9|10").forEach { raw ->
            val st = GameState().toEntity().copy(auctionRaw = raw).toState()
            assertNull("строка «$raw» не должна давать лот", st.auction)
        }
    }

    @Test
    fun `лот с исчезнувшим предметом отбрасывается`() {
        // каталог мог измениться между версиями — торговать несуществующим нельзя
        val raw = GameState(auction = lot.copy(itemId = "исчез")).toEntity().auctionRaw
        assertTrue(raw.isNotEmpty())
        val st = GameState().toEntity().copy(auctionRaw = raw).toState()
        assertNull(st.auction)
    }

    @Test
    fun `после перезапуска торги идут так же, как без него`() {
        // открытый лот: доступ к закрытым торгам здесь ни при чём, проверяем сам круг записи
        var st = GameState(money = 1e7, gameHours = 480.0, auction = lot.copy(
            itemId = "canvas", tierOrdinal = AuctionTier.OPEN.ordinal,
            bids = 0, playerBids = 0, playerLeads = false, playerEscrow = 0.0,
            bid = 1_000.0, rivalLimit = 5_000.0, rivalReplies = false
        ))
        st = Auctions.bid(st, 1_000.0)!!

        val direct = Auctions.advance(st, 600.0)
        val reloaded = Auctions.advance(st.toEntity().toState(), 600.0)

        assertNotNull(direct.ended)
        assertEquals(direct.ended, reloaded.ended)
        assertEquals(direct.state.money, reloaded.state.money, EPS)
        assertEquals(direct.state.collectibles, reloaded.state.collectibles)
        assertEquals(direct.state.auctionNextGameH, reloaded.state.auctionNextGameH, EPS)
    }
}
