package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Currency
import ru.capital.idle.ui.TapPop
import ru.capital.idle.ui.theme.Bg

/**
 * Всплывающая награда за тап по карте.
 *
 * Самое узкое место: сумма показывается целиком до миллиарда, то есть строка должна
 * вмещать «+$ 999 999 999» и не переноситься ни при каком масштабе шрифта.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class TapPopScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Максимум, который показывается без сокращения. */
    private val maxFullNumber = 999_999_999.0

    /**
     * Снять всплывашку в середине её анимации: к концу она тает до полной прозрачности,
     * поэтому автопрокрутку часов приходится выключать.
     */
    private fun shot(name: String, accum: Double, cur: Currency, fontScale: Float = 1f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale)
            ) {
                Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    TapPop(accum = accum, tick = 1, currency = cur, onExpire = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)   // середина показа: надпись полностью видна
        compose.onRoot().captureRoboImage(
            filePath = "${Screenshots.DIR}/$name.png",
            roborazziOptions = Screenshots.OPTIONS
        )
    }

    @Test
    fun `максимальная несокращённая сумма в долларах`() {
        shot("tappop_max_full_usd", maxFullNumber, Currency.USD)
    }

    @Test
    fun `максимальная несокращённая сумма в рублях`() {
        // в рублях та же сумма уже за миллиардом и показывается с суффиксом
        shot("tappop_max_full_rub", maxFullNumber, Currency.RUB)
    }

    @Test
    fun `максимальная несокращённая сумма при крупном шрифте`() {
        shot("tappop_max_full_large_font", maxFullNumber, Currency.USD, Screenshots.LARGE_FONT)
    }

    @Test
    fun `сокращённая сумма за миллиардом`() {
        shot("tappop_billions", 4_470_000_000.0, Currency.RUB)
    }

    @Test
    fun `мелкая награда в начале игры`() {
        shot("tappop_small", 12.0, Currency.RUB)
    }
}
