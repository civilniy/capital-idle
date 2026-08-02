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
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.Currency
import ru.capital.idle.ui.CollectibleCard
import ru.capital.idle.ui.CollectionSetsBlock
import ru.capital.idle.ui.CollectionSummary
import ru.capital.idle.ui.SetProgress

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

    // ===================== блок наборов =====================

    /** Прогресс наборов при заданном списке купленных предметов. */
    private fun rows(owned: Set<String>) = Collectibles.sets.map { s ->
        SetProgress(s, s.itemIds.count { it in owned })
    }

    /**
     * Блок наборов отдаёт несколько соседей подряд, а не одну карточку,
     * поэтому в снимке его нужно положить в Column — как и на самом экране.
     */
    private fun setsShot(name: String, owned: Set<String>, fontScale: Float = 1f) {
        compose.captureOnBackground(name, fontScale = fontScale) {
            Column(Modifier.fillMaxWidth()) { CollectionSetsBlock(rows(owned)) }
        }
    }

    /**
     * Частичный сбор: «Галерея» уже закрыта, «Дар природы» на двух предметах из трёх,
     * «Имена в истории» — на двух из пяти (те же полотно и шедевр из «Галереи»),
     * «Древний мир» пуст. Один снимок показывает сразу все четыре состояния строки.
     */
    private val partial = setOf("litho", "canvas", "lost", "diamond", "rex")

    @Test
    fun `наборы — ничего не собрано`() {
        setsShot("collection_sets_empty", emptySet())
    }

    @Test
    fun `наборы — ничего не собрано при крупном системном шрифте`() {
        setsShot("collection_sets_empty_large_font", emptySet(), Screenshots.LARGE_FONT)
    }

    @Test
    fun `наборы — собрано частично`() {
        setsShot("collection_sets_partial", partial)
    }

    @Test
    fun `наборы — собрано частично при крупном системном шрифте`() {
        setsShot("collection_sets_partial_large_font", partial, Screenshots.LARGE_FONT)
    }

    @Test
    fun `наборы — собрано всё`() {
        setsShot("collection_sets_full", Collectibles.all.map { it.id }.toSet())
    }

    @Test
    fun `наборы — собрано всё при крупном системном шрифте`() {
        setsShot("collection_sets_full_large_font",
            Collectibles.all.map { it.id }.toSet(), Screenshots.LARGE_FONT)
    }
}
