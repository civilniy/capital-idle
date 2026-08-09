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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.ui.CardFace
import ru.capital.idle.ui.PressureSlot
import ru.capital.idle.ui.theme.Bg

/**
 * Высота карты не зависит от длины баланса.
 *
 * Замер с устройства: 319 пикселей держались ровно, затем разом становились 322 — ровно
 * в тот кадр, когда баланс переключался с «$ 940 015 169» на «$ 1,7B». Кегль баланса
 * подбирается под ширину карты, у короткого числа он крупнее, строка выше, а карта тянется
 * за содержимым, потому что её высота задана снизу (`heightIn(min = 218.dp)`).
 *
 * Здесь берутся балансы по обе стороны от этого перехода — и самые крайние заодно —
 * и проверяется, что высота совпадает ТОЧНО, во всех сочетаниях надбавки, плашки давления
 * и масштаба шрифта.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class CardHeightScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Балансы из замера плюс границы: ноль, предельно длинный несокращённый и триллионы.
     * У каждого свой подобранный кегль — в этом и смысл набора.
     */
    private val balances = listOf(
        "long" to 940_015_169.0,        // «$ 940 015 169» — кегль мельче всех
        "short" to 1_700_000_000.0,     // «$ 1,7B» — кегль максимальный
        "zero" to 0.0,                  // «$ 0»
        "max" to 999_999_999.0,         // «$ 999 999 999» — предел полного показа
        "huge" to 32_400_000_000_000.0  // «$ 32,4T»
    )

    private val money = mutableDoubleStateOf(0.0)
    private val reward = mutableDoubleStateOf(0.0)
    private val popTick = mutableIntStateOf(0)
    private val fontScale = mutableFloatStateOf(1f)
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
                    CardFace(
                        money = money.doubleValue, incomePerDay = 413_000_000.0,
                        currency = Currency.USD, playerName = "Владимир",
                        tier = CardTier.entries.last(),
                        popAccum = reward.doubleValue, popTick = popTick.intValue
                    )
                    if (withPressureSlot.value) {
                        Spacer(Modifier.height(8.dp))
                        PressureSlot(pressure = 0.31, reputation = 42.0)
                    }
                }
            }
        }
    }

    private fun rootHeight(): Int {
        compose.waitForIdle()
        return compose.onRoot().fetchSemanticsNode().size.height
    }

    /**
     * Один прогон по всем балансам внутри одного сочетания (надбавка, плашка, шрифт).
     * Внутри сочетания меняется только баланс, поэтому разная высота корня означает
     * разную высоту карты.
     */
    private fun heightsByBalance(): Map<String, Int> =
        balances.associate { (name, v) ->
            money.doubleValue = v
            popTick.intValue += 1
            name to rootHeight()
        }

    // ===================== высота =====================

    @Test
    fun `высота карты одинакова при любом балансе`() {
        compose.setContent { Screen() }
        val report = LinkedHashMap<String, Map<String, Int>>()
        listOf(0.0 to "без надбавки", 999_999_999.0 to "с надбавкой").forEach { (rw, popName) ->
            reward.doubleValue = rw
            listOf(false to "без плашки", true to "с плашкой").forEach { (slot, slotName) ->
                withPressureSlot.value = slot
                listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
                    fontScale.floatValue = fs
                    report["$popName · $slotName · шрифт $fs"] = heightsByBalance()
                }
            }
        }
        report.forEach { (variant, heights) ->
            val distinct = heights.values.distinct()
            assertEquals("высота карты поехала ($variant): $heights", 1, distinct.size)
        }
    }

    /**
     * Проверка не вхолостую: кегль баланса действительно разный у этих чисел, иначе
     * тест выше сравнивал бы одинаковое с одинаковым и ничего не ловил.
     */
    @Test
    fun `кегль баланса у этих чисел разный`() {
        compose.setContent { Screen() }
        val widths = balances.associate { (name, v) ->
            money.doubleValue = v
            compose.waitForIdle()
            val text = GameMath.formatMoney(v, Currency.USD)
            name to compose.onNodeWithText(text).fetchSemanticsNode().size.height
        }
        assertTrue("высоты строк баланса обязаны различаться: $widths", widths.values.distinct().size > 1)
    }

    /**
     * Все элементы карты на месте. Имя владельца проверяется отдельно и первым: строка
     * с ним в потоке последняя и при нехватке места исчезает раньше всех — так уже было
     * в PR #15 и #16.
     */
    @Test
    fun `на карте есть все элементы, включая имя владельца`() {
        compose.setContent { Screen() }
        val expected = listOf("AURUM BANK", "ДОСТУПНО", "ВЛАДИМИР", CardTier.entries.last().title)
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            balances.forEach { (name, v) ->
                money.doubleValue = v
                compose.waitForIdle()
                expected.forEach { text ->
                    val h = compose.onNodeWithText(text).fetchSemanticsNode().size.height
                    assertTrue(
                        "«$text» не нарисован (баланс $name, шрифт $fs): высота ${h / compose.density.density}dp",
                        h / compose.density.density >= 8f
                    )
                }
                // и сам баланс
                val bal = GameMath.formatMoney(v, Currency.USD)
                assertTrue(
                    "баланс «$bal» не нарисован (шрифт $fs)",
                    compose.onNodeWithText(bal).fetchSemanticsNode().size.height > 0
                )
            }
        }
    }

    // ===================== снимки =====================

    /**
     * Снимки всех сочетаний. Надбавка показывается 800 мс и к концу тает, поэтому часы
     * останавливаются, а анимация перезапускается перед каждым кадром.
     */
    @Test
    fun `снимки карты во всех состояниях`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Screen() }
        listOf(0.0 to "plain", 999_999_999.0 to "pop").forEach { (rw, popName) ->
            reward.doubleValue = rw
            listOf(false to "", true to "_pressure").forEach { (slot, slotName) ->
                withPressureSlot.value = slot
                listOf(1f to "", Screenshots.LARGE_FONT to "_large_font").forEach { (fs, fsName) ->
                    fontScale.floatValue = fs
                    balances.forEach { (name, v) ->
                        money.doubleValue = v
                        popTick.intValue += 1
                        compose.mainClock.advanceTimeByFrame()
                        compose.mainClock.advanceTimeBy(300)   // середина показа надбавки
                        compose.mainClock.advanceTimeByFrame()
                        compose.onRoot().captureRoboImage(
                            filePath = "${Screenshots.DIR}/card_h_${name}_$popName$slotName$fsName.png",
                            roborazziOptions = Screenshots.OPTIONS
                        )
                    }
                }
            }
        }
    }
}
