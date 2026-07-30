package ru.capital.idle.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.Currency
import ru.capital.idle.ui.CollectibleCard
import ru.capital.idle.ui.CollectionSummary

/**
 * Вёрстка карточек коллекции. Главный риск здесь — длинные числа:
 * предметы стоят до $50 млрд, а на потолке роста — до $400 млрд,
 * и в рублях это триллионы.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class CollectionLayoutScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Самый дорогой предмет каталога — античный храм за $50 млрд. */
    private val top = Collectibles.all.maxByOrNull { it.basePrice }!!

    /** Его цена на потолке роста: $400 млрд. */
    private val topCapped = Collectibles.priceAt(top, Collectibles.capReachedOnDay(top))

    @Test
    fun `карточка за миллиарды с прибылью`() {
        compose.captureOnBackground("collectible_billions_profit") {
            CollectibleCard(
                item = top, price = topCapped, owned = true,
                paid = top.basePrice, profit = topCapped - top.basePrice,
                canBuy = false, cur = Currency.RUB, onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `карточка за миллиарды с убытком`() {
        compose.captureOnBackground("collectible_billions_loss") {
            CollectibleCard(
                item = top, price = top.basePrice, owned = true,
                paid = topCapped, profit = top.basePrice - topCapped,
                canBuy = false, cur = Currency.RUB, onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `карточка за миллиарды при крупном системном шрифте`() {
        compose.captureOnBackground("collectible_billions_large_font", fontScale = Screenshots.LARGE_FONT) {
            CollectibleCard(
                item = top, price = topCapped, owned = true,
                paid = top.basePrice, profit = topCapped - top.basePrice,
                canBuy = false, cur = Currency.RUB, onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `карточка не купленного предмета с самым длинным названием`() {
        val longest = Collectibles.all.maxByOrNull { it.title.length }!!
        compose.captureOnBackground("collectible_longest_title") {
            CollectibleCard(
                item = longest, price = Collectibles.priceAt(longest, 0), owned = false,
                paid = 0.0, profit = 0.0,
                canBuy = true, cur = Currency.RUB, onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `карточка недоступного предмета — цена приглушена`() {
        compose.captureOnBackground("collectible_cannot_buy") {
            CollectibleCard(
                item = top, price = topCapped, owned = false,
                paid = 0.0, profit = 0.0,
                canBuy = false, cur = Currency.RUB, onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `сводка коллекции на триллионах`() {
        compose.captureOnBackground("collection_summary_trillions") {
            CollectionSummary(
                day = 3_500, owned = Collectibles.all.size,
                value = 6.1e11, profit = 4.9e11, cur = Currency.RUB
            )
        }
    }

    @Test
    fun `сводка коллекции с убытком`() {
        compose.captureOnBackground("collection_summary_loss") {
            CollectionSummary(
                day = 12, owned = 2,
                value = 1.4e8, profit = -3.2e7, cur = Currency.RUB
            )
        }
    }

    @Test
    fun `сводка коллекции при крупном системном шрифте`() {
        compose.captureOnBackground("collection_summary_large_font", fontScale = Screenshots.LARGE_FONT) {
            CollectionSummary(
                day = 3_500, owned = Collectibles.all.size,
                value = 6.1e11, profit = 4.9e11, cur = Currency.RUB
            )
        }
    }
}
