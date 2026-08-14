package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.Exchange
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.MarketPhase
import ru.capital.idle.ui.MarketBar
import ru.capital.idle.ui.StockCard
import ru.capital.idle.ui.theme.Bg

/**
 * Элементы, которые меняются сами по себе, не меняют высоту.
 *
 * Опасны именно они: то, что переключается по нажатию игрока, сдвигает список один раз и
 * в ответ на его же действие. А фаза рынка и биржевое событие приходят сами, посреди чтения
 * экрана, и содержимое ниже уезжает без всякого повода со стороны игрока.
 *
 * Итог автовклада — самый частый такой случай, он проверяется отдельно в `AutoInvestHeightTest`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE_ENDLESS)
class SelfChangingHeightTest {

    @get:Rule
    val compose = createComposeRule()

    private val phase = mutableIntStateOf(0)
    private val eventDir = mutableIntStateOf(0)
    private val fontScale = mutableFloatStateOf(1f)
    private val showMarket = mutableStateOf(true)

    private val investor = GameState(
        money = 5_000_000.0,
        eduDone = setOf("school", "acc", "uni", "mba", "lead", "crisis")
    )

    @Composable
    private fun Screen() {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.floatValue)
        ) {
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    val s = investor.copy(phaseIndex = phase.intValue)
                    if (showMarket.value) MarketBar(s)
                    Exchange.stocks.forEachIndexed { i, stock ->
                        StockCard(
                            state = s, index = i, stock = stock, cur = Currency.USD,
                            history = emptyList(),
                            // событие всегда на первой бумаге: остальные стоят рядом для сравнения
                            eventDir = if (i == 0) eventDir.intValue else 0,
                            onBuy = {}, onSell = {}
                        )
                    }
                }
            }
        }
    }

    private fun height(): Int {
        compose.waitForIdle()
        return compose.onRoot().fetchSemanticsNode().size.height
    }

    /**
     * Фаза рынка сменяется по игровому времени. У «Кризиса» к подписи добавляется
     * «· бизнесы дешевле» — при крупном шрифте это лишняя строка, если ей дать перенос.
     */
    @Test
    fun `смена фазы рынка не меняет высоту`() {
        compose.setContent { Screen() }
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            val heights = MarketPhase.entries.associate { p ->
                phase.intValue = p.ordinal
                p.title to height()
            }
            assertEquals("плашка рынка поехала (шрифт $fs): $heights", 1, heights.values.distinct().size)
        }
        phase.intValue = 0
    }

    /**
     * Биржевое событие приходит и уходит само: у бумаги появляется плашка «хайп» или «обвал».
     * Она стоит в одной строке с названием — строка не должна от неё вырасти.
     */
    @Test
    fun `плашка биржевого события не меняет высоту`() {
        compose.setContent { Screen() }
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            val heights = listOf(-1 to "обвал", 0 to "спокойно", 1 to "хайп").associate { (dir, name) ->
                eventDir.intValue = dir
                name to height()
            }
            assertEquals("карточка бумаги поехала (шрифт $fs): $heights", 1, heights.values.distinct().size)
        }
        eventDir.intValue = 0
    }
}
