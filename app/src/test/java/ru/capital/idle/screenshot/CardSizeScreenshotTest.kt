package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.CardTier
import ru.capital.idle.core.game.Currency
import ru.capital.idle.ui.CARD_ASPECT_RATIO
import ru.capital.idle.ui.CreditCard
import ru.capital.idle.ui.PressureSlot
import ru.capital.idle.ui.theme.Bg

/**
 * Карта на главном экране: постоянные пропорции и высота, не зависящая от содержимого.
 *
 * Раньше высоту карты определял текст — длинный баланс делал её выше короткого, и всё под
 * картой прыгало. Теперь высота считается от ширины по формату ISO/IEC 7810 ID-1,
 * а содержимое подстраивается под карту.
 *
 * Правило теста Compose: `setContent` вызывается один раз, поэтому варианты гоняются
 * через состояние, а не повторной установкой содержимого.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class CardSizeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Балансы из задачи: предельно длинный, короткий с суффиксом и обычный восьмизначный. */
    private val balances = listOf(
        "max" to 999_999_999.0,
        "short" to 1_200_000_000.0,
        "mid" to 25_963_353.0
    )

    private val money = mutableDoubleStateOf(999_999_999.0)
    private val reward = mutableDoubleStateOf(0.0)
    private val fontScale = mutableFloatStateOf(1f)
    private val pressure = mutableDoubleStateOf(0.0)
    private val reserved = mutableStateOf(true)
    private val withPressureSlot = mutableStateOf(false)

    @Composable
    private fun Screen() {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.floatValue)
        ) {
            // тот же отступ по краям, что и на главном экране
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    CreditCard(
                        money = money.doubleValue, incomePerDay = 413_000_000.0,
                        reward = reward.doubleValue, currency = Currency.USD,
                        playerName = "Владимир", tier = CardTier.entries.last(), onTap = {}
                    )
                    if (withPressureSlot.value) {
                        Spacer(Modifier.height(8.dp))
                        PressureSlot(
                            pressure = pressure.doubleValue, reputation = 42.0,
                            reserved = reserved.value
                        )
                    }
                }
            }
        }
    }

    /** Только плашка давления — без карты, чтобы мерить её место отдельно. */
    @Composable
    private fun SlotOnly() {
        Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Column(Modifier.fillMaxWidth()) {
                PressureSlot(
                    pressure = pressure.doubleValue, reputation = 42.0, reserved = reserved.value
                )
            }
        }
    }

    private fun rootHeight(): Int {
        compose.waitForIdle()
        return compose.onRoot().fetchSemanticsNode().size.height
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            filePath = "${Screenshots.DIR}/$name.png",
            roborazziOptions = Screenshots.OPTIONS
        )
    }

    // ===================== пропорции =====================

    @Test
    fun `константа пропорций — формат банковской карты`() {
        // ISO/IEC 7810 ID-1: 85.60 × 53.98 мм
        assertEquals(85.60f / 53.98f, CARD_ASPECT_RATIO, 0.001f)
    }

    @Test
    fun `высота карты равна ширине, делённой на константу пропорций`() {
        compose.setContent { Screen() }
        val root = compose.onRoot().fetchSemanticsNode().size
        val sidePad = with(compose.density) { 28.dp.roundToPx() }
        val vertPad = with(compose.density) { 20.dp.roundToPx() }
        val cardW = root.width - sidePad
        val cardH = root.height - vertPad
        assertEquals(
            "высота должна считаться от ширины по CARD_ASPECT_RATIO",
            (cardW / CARD_ASPECT_RATIO).toDouble(), cardH.toDouble(), 2.0
        )
    }

    // ===================== высота не зависит от содержимого =====================

    @Test
    fun `высота карты одинакова при любом балансе и надбавке`() {
        compose.setContent { Screen() }
        val heights = LinkedHashMap<String, Int>()
        listOf(0.0, 999_999_999.0).forEach { r ->
            reward.doubleValue = r
            balances.forEach { (name, v) ->
                money.doubleValue = v
                heights["$name/надбавка=$r"] = rootHeight()
            }
        }
        assertEquals("высоты разошлись: $heights", 1, heights.values.toSet().size)
    }

    @Test
    fun `высота карты одинакова и при крупном системном шрифте`() {
        compose.setContent { Screen() }
        fontScale.floatValue = Screenshots.LARGE_FONT
        reward.doubleValue = 999_999_999.0
        val heights = LinkedHashMap<String, Int>()
        balances.forEach { (name, v) ->
            money.doubleValue = v
            heights[name] = rootHeight()
        }
        assertEquals("высоты разошлись: $heights", 1, heights.values.toSet().size)
    }

    @Test
    fun `системный шрифт не растягивает карту`() {
        compose.setContent { Screen() }
        money.doubleValue = 25_963_353.0
        val normal = rootHeight()
        fontScale.floatValue = Screenshots.LARGE_FONT
        val large = rootHeight()
        assertEquals("высота задана шириной, а ширина от шрифта не зависит", normal, large)
    }

    // ===================== плашка давления не двигает разметку =====================

    @Test
    fun `появление и исчезновение давления не меняет высоту отведённого места`() {
        compose.setContent { SlotOnly() }
        reserved.value = true
        pressure.doubleValue = 0.31
        val shown = rootHeight()
        pressure.doubleValue = 0.0
        val hidden = rootHeight()
        assertEquals(
            "место под плашку обязано быть одинаковым: иначе экран под ней прыгает",
            shown, hidden
        )
    }

    @Test
    fun `до порога капитала место под плашку не занимается`() {
        compose.setContent { SlotOnly() }
        pressure.doubleValue = 0.0
        reserved.value = true
        val withSlot = rootHeight()
        reserved.value = false
        val withoutSlot = rootHeight()
        assertTrue("у новичка на экране не должно висеть пустое место", withoutSlot < withSlot)
    }

    // ===================== снимки =====================

    @Test
    fun `карта при разных балансах, с надбавкой и без`() {
        compose.setContent { Screen() }
        listOf(0.0 to "plain", 999_999_999.0 to "pop").forEach { (r, suffix) ->
            reward.doubleValue = r
            listOf(1f to "", Screenshots.LARGE_FONT to "_large_font").forEach { (fs, tail) ->
                fontScale.floatValue = fs
                balances.forEach { (name, v) ->
                    money.doubleValue = v
                    capture("card_${name}_$suffix$tail")
                }
            }
        }
    }

    @Test
    fun `карта с плашкой давления и с пустым местом под неё`() {
        compose.setContent { Screen() }
        withPressureSlot.value = true
        reserved.value = true
        money.doubleValue = 25_963_353.0

        pressure.doubleValue = 0.31
        capture("card_with_pressure")
        pressure.doubleValue = 0.0
        capture("card_pressure_reserved")

        fontScale.floatValue = Screenshots.LARGE_FONT
        pressure.doubleValue = 0.31
        capture("card_with_pressure_large_font")
    }
}
