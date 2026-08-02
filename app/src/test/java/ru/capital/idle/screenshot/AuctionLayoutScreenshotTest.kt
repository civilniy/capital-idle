package ru.capital.idle.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.AuctionTier
import ru.capital.idle.core.game.Auctions
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.Currency
import ru.capital.idle.ui.AuctionBlock
import ru.capital.idle.ui.AuctionView

/**
 * Вёрстка раздела торгов.
 *
 * Числа берутся предельные: самый дорогой лот каталога на потолке роста стоит $400 млрд,
 * а в рублях это десятки триллионов — ставка, минимальный перебив и «уверенная» кнопка
 * должны оставаться в одну строку.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class AuctionLayoutScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Античный храм — самый дорогой лот и самое длинное «в каталоге не продаётся». */
    private val temple = Collectibles.byId("temple")!!

    /** Полотно импрессиониста — рядовой открытый лот. */
    private val canvas = Collectibles.byId("canvas")!!

    /** Самое длинное имя соперника: ячейка «ВЕДЁТ» однострочная, проверяем худший случай. */
    private val longestRival = Auctions.rivalNames.maxByOrNull { it.length }!!

    private fun view(
        item: ru.capital.idle.core.game.Collectible? = canvas,
        tier: AuctionTier = AuctionTier.OPEN,
        unlocked: Boolean = true,
        bid: Double = 62_000_000.0,
        bids: Int = 3,
        playerLeads: Boolean = false,
        leader: String = longestRival,
        hoursLeft: Double = 31.0,
        timeFraction: Float = 0.57f,
        money: Double = 999_999_999.0,
        reputation: Double = 41.0,
        status: Int = 138,
        nextInH: Double = 88.0
    ) = AuctionView(
        item = item, tier = tier, unlocked = unlocked,
        reputation = reputation, status = status,
        bid = bid, bids = bids, playerLeads = playerLeads, leader = leader,
        hoursLeft = hoursLeft, timeFraction = timeFraction,
        minBid = kotlin.math.ceil(bid * 1.05), boldBid = kotlin.math.ceil(bid * 1.05 * 1.25),
        money = money,
        catalogPrice = Collectibles.priceAt(item ?: canvas, 400),
        nextInH = nextInH
    )

    private fun shot(name: String, v: AuctionView, cur: Currency = Currency.RUB, fontScale: Float = 1f) {
        compose.captureOnBackground(name, fontScale = fontScale) {
            Column(Modifier.fillMaxWidth()) { AuctionBlock(view = v, cur = cur, onBid = {}) }
        }
    }

    // ===================== нет активных торгов =====================

    @Test
    fun `торгов нет`() {
        shot("auction_idle", view(item = null))
    }

    @Test
    fun `торгов нет при крупном шрифте`() {
        shot("auction_idle_large_font", view(item = null), fontScale = Screenshots.LARGE_FONT)
    }

    // ===================== идут торги =====================

    @Test
    fun `идут торги — ведёт соперник`() {
        shot("auction_live", view())
    }

    @Test
    fun `идут торги — ведёт соперник, крупный шрифт`() {
        shot("auction_live_large_font", view(), fontScale = Screenshots.LARGE_FONT)
    }

    /** Предельный случай: ставка и обе кнопки на самых длинных числах. */
    @Test
    fun `идут торги — ставка на пределе разрядности`() {
        val capped = Collectibles.priceAt(temple, Collectibles.capReachedOnDay(temple))
        shot(
            "auction_live_huge",
            view(item = temple, tier = AuctionTier.CLOSED, bid = capped, bids = 11, money = 9.9e14)
        )
    }

    @Test
    fun `идут торги — ставка на пределе разрядности, крупный шрифт`() {
        val capped = Collectibles.priceAt(temple, Collectibles.capReachedOnDay(temple))
        shot(
            "auction_live_huge_large_font",
            view(item = temple, tier = AuctionTier.CLOSED, bid = capped, bids = 11, money = 9.9e14),
            fontScale = Screenshots.LARGE_FONT
        )
    }

    // ===================== игрок ведёт =====================

    @Test
    fun `игрок ведёт`() {
        shot("auction_leading", view(playerLeads = true, leader = "ведёте вы", timeFraction = 0.2f))
    }

    @Test
    fun `игрок ведёт при крупном шрифте`() {
        shot(
            "auction_leading_large_font",
            view(playerLeads = true, leader = "ведёте вы", timeFraction = 0.2f),
            fontScale = Screenshots.LARGE_FONT
        )
    }

    // ===================== игрок проигрывает =====================

    @Test
    fun `игрок проигрывает — денег на перебив не хватает`() {
        // ставка выше наличных: обе кнопки должны погаснуть, а не обрезаться
        shot("auction_losing", view(bid = 740_000_000.0, bids = 7, money = 12_500_000.0,
            hoursLeft = 4.0, timeFraction = 0.94f))
    }

    @Test
    fun `игрок проигрывает при крупном шрифте`() {
        shot(
            "auction_losing_large_font",
            view(bid = 740_000_000.0, bids = 7, money = 12_500_000.0,
                hoursLeft = 4.0, timeFraction = 0.94f),
            fontScale = Screenshots.LARGE_FONT
        )
    }

    // ===================== закрытые торги недоступны =====================

    @Test
    fun `закрытые торги недоступны`() {
        shot("auction_locked", view(item = temple, tier = AuctionTier.CLOSED, unlocked = false))
    }

    @Test
    fun `закрытые торги недоступны при крупном шрифте`() {
        shot(
            "auction_locked_large_font",
            view(item = temple, tier = AuctionTier.CLOSED, unlocked = false),
            fontScale = Screenshots.LARGE_FONT
        )
    }
}
