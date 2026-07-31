package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Currency
import ru.capital.idle.ui.BalanceWithTapPop
import ru.capital.idle.ui.theme.Bg
import ru.capital.idle.ui.theme.TextMain

/**
 * Баланс на карте и надбавка за тап рядом с ним.
 *
 * Худший случай: оба числа максимальные несокращённые — «$ 999 999 999» и «+$ 999 999 999».
 * По правилу полноты (CLAUDE.md) сокращать их нельзя, поэтому кегль подстраивается
 * под ширину карты, а перенос запрещён структурно.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class TapPopScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Максимум, который показывается без сокращения. */
    private val maxFullNumber = 999_999_999.0

    /** Столько же, но в рублях: делим на курс, иначе строка уйдёт в суффикс. */
    private fun maxFullIn(cur: Currency) = maxFullNumber / cur.ratePerUsd

    /** Ширина карты повторяет реальную: отступ экрана 14dp плюс внутренний 16dp. */
    @Composable
    private fun OnCard(fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale)
        ) {
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 30.dp, vertical = 10.dp)) {
                content()
            }
        }
    }

    /**
     * Снять баланс с надбавкой в середине показа: к концу надбавка тает до прозрачности,
     * поэтому автопрокрутку часов приходится выключать.
     */
    private fun shot(name: String, money: Double, accum: Double, cur: Currency, fontScale: Float = 1f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OnCard(fontScale) {
                BalanceWithTapPop(
                    money = money, accum = accum, tick = 1, currency = cur,
                    moneyColor = TextMain, onExpire = {}
                )
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage(
            filePath = "${Screenshots.DIR}/$name.png",
            roborazziOptions = Screenshots.OPTIONS
        )
    }

    // ===================== худший случай: оба числа максимальные =====================

    @Test
    fun `оба числа максимальные в долларах`() {
        shot("balance_pop_max_usd", maxFullNumber, maxFullNumber, Currency.USD)
    }

    @Test
    fun `оба числа максимальные в долларах при крупном шрифте`() {
        shot("balance_pop_max_usd_large_font", maxFullNumber, maxFullNumber,
            Currency.USD, Screenshots.LARGE_FONT)
    }

    @Test
    fun `оба числа максимальные в рублях`() {
        val v = maxFullIn(Currency.RUB)
        shot("balance_pop_max_rub", v, v, Currency.RUB)
    }

    @Test
    fun `оба числа максимальные в рублях при крупном шрифте`() {
        val v = maxFullIn(Currency.RUB)
        shot("balance_pop_max_rub_large_font", v, v, Currency.RUB, Screenshots.LARGE_FONT)
    }

    // ===================== обычные состояния =====================

    @Test
    fun `баланс без надбавки`() {
        shot("balance_no_pop", maxFullNumber, 0.0, Currency.USD)
    }

    @Test
    fun `небольшой баланс с надбавкой`() {
        shot("balance_pop_small", 1_234.0, 12.0, Currency.RUB)
    }

    @Test
    fun `баланс и надбавка за миллиардом`() {
        shot("balance_pop_billions", 3_400_000_000_000.0, 4_470_000_000.0, Currency.RUB)
    }

    // ===================== высота не должна прыгать =====================

    @Test
    fun `появление надбавки не меняет высоту строки баланса`() {
        val accum = mutableDoubleStateOf(0.0)
        val tick = mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OnCard(1f) {
                BalanceWithTapPop(
                    money = maxFullNumber, accum = accum.doubleValue, tick = tick.intValue,
                    currency = Currency.USD, moneyColor = TextMain, onExpire = {}
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val withoutPop = compose.onRoot().fetchSemanticsNode().size.height

        // надбавка появилась
        accum.doubleValue = maxFullNumber
        tick.intValue = 1
        compose.mainClock.advanceTimeBy(300)
        val withPop = compose.onRoot().fetchSemanticsNode().size.height

        assertEquals(
            "высота карты не должна меняться при появлении надбавки",
            withoutPop, withPop
        )
    }

    @Test
    fun `появление надбавки не меняет высоту и при крупном шрифте`() {
        val accum = mutableDoubleStateOf(0.0)
        val tick = mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OnCard(Screenshots.LARGE_FONT) {
                BalanceWithTapPop(
                    money = maxFullNumber, accum = accum.doubleValue, tick = tick.intValue,
                    currency = Currency.USD, moneyColor = TextMain, onExpire = {}
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val withoutPop = compose.onRoot().fetchSemanticsNode().size.height

        accum.doubleValue = maxFullNumber
        tick.intValue = 1
        compose.mainClock.advanceTimeBy(300)
        val withPop = compose.onRoot().fetchSemanticsNode().size.height

        assertEquals(withoutPop, withPop)
    }
}
