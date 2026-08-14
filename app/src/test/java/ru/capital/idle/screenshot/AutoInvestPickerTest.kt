package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Asset
import ru.capital.idle.core.game.AutoInvest
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameState
import ru.capital.idle.ui.AutoInvestCard
import ru.capital.idle.ui.theme.AppTheme
import ru.capital.idle.ui.theme.Bg
import ru.capital.idle.ui.theme.LocalPalette

/**
 * Выбор инструмента в автовкладе — ряд из трёх кнопок.
 *
 * Раньше инструменты шли списком в три строки и съедали втрое больше места. Теперь это
 * тот же ряд кнопок, что «50% · На все · Вывести» в карточках накоплений ниже по экрану.
 * Здесь проверяется, что кнопки действительно стоят в одну строку, что закрытый образованием
 * инструмент виден, но не нажимается, и что блок стал ниже, чем был.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class AutoInvestPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        /**
         * Высота блока до переделки — снята с эталонов на main: `autoinvest_all_assets.png`
         * это 1099 px, при плотности xxhdpi (×3) и рамке снимка в 10dp сверху и снизу.
         * Здесь и ниже сравнивается тот же корень с той же рамкой, поэтому числа сопоставимы.
         */
        const val WAS_DP = 1099f / 3f

        /** То же при увеличенном шрифте: `autoinvest_all_assets_large_font.png` — 1434 px. */
        const val WAS_LARGE_DP = 1434f / 3f
    }

    private val shown = mutableStateOf(
        GameState(
            money = 999_999_999.0, eduDone = setOf("school", "acc", "uni"),
            autoInvestOn = true, autoInvestReserve = 10_000.0
        )
    )
    private val fontScale = mutableFloatStateOf(1f)
    private val theme = mutableStateOf(AppTheme.GLASS)

    @Composable
    private fun Card() {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.floatValue),
            LocalPalette provides theme.value.palette
        ) {
            // та же рамка, что и у снимков этого блока
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    val s = shown.value
                    AutoInvestCard(
                        on = true,
                        target = AutoInvest.target(s),
                        unlocked = AutoInvest.available(s),
                        reserve = s.autoInvestReserve,
                        amount = AutoInvest.amount(s),
                        cur = Currency.USD
                    )
                }
            }
        }
    }

    private fun boundsOf(text: String) =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun rootDp(): Float {
        compose.waitForIdle()
        return compose.onRoot().fetchSemanticsNode().size.height / compose.density.density
    }

    // ===================== ряд =====================

    @Test
    fun `три инструмента стоят в одну строку`() {
        compose.setContent { Card() }
        listOf(AppTheme.GLASS, AppTheme.MATTE).forEach { t ->
            theme.value = t
            listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
                fontScale.floatValue = fs
                compose.waitForIdle()
                val boxes = Asset.entries.map { boundsOf(it.title) }
                val where = "${t.title}, шрифт $fs"

                // одна строка: верх и низ у всех трёх совпадают
                assertEquals("верх кнопок разошёлся ($where)", 1, boxes.map { it.top }.distinct().size)
                assertEquals("низ кнопок разошёлся ($where)", 1, boxes.map { it.bottom }.distinct().size)

                // и идут слева направо, в порядке инструментов
                boxes.zipWithNext { a, b ->
                    assertTrue("кнопки идут не по порядку ($where)", a.right <= b.left)
                }
            }
        }
    }

    @Test
    fun `закрытый инструмент виден, но не нажимается`() {
        compose.setContent { Card() }
        listOf(AppTheme.GLASS, AppTheme.MATTE).forEach { t ->
            theme.value = t
            shown.value = shown.value.copy(eduDone = setOf("school", "acc"))
            compose.waitForIdle()

            compose.onNodeWithText(Asset.DEPOSIT.title).assertIsEnabled()
            compose.onNodeWithText(Asset.BONDS.title).assertIsEnabled()
            // «Недвижимость» требует «Университет»: кнопка на месте, но мёртвая
            compose.onNodeWithText(Asset.REALTY.title).assertIsNotEnabled()

            shown.value = shown.value.copy(eduDone = setOf("school", "acc", "uni"))
            compose.waitForIdle()
            compose.onNodeWithText(Asset.REALTY.title).assertIsEnabled()
        }
    }

    // ===================== высота =====================

    /**
     * Блок стал ниже. Сравнение идёт с числами, снятыми с эталонов до переделки: тот же
     * состав (три открытых инструмента), та же рамка снимка, то же устройство.
     */
    @Test
    fun `блок автовклада стал ниже, чем был`() {
        compose.setContent { Card() }
        theme.value = AppTheme.GLASS

        fontScale.floatValue = 1f
        val now = rootDp()
        assertTrue("блок не стал ниже: было ${WAS_DP}dp, стало ${now}dp", now < WAS_DP)

        fontScale.floatValue = Screenshots.LARGE_FONT
        val nowLarge = rootDp()
        assertTrue(
            "блок не стал ниже при крупном шрифте: было ${WAS_LARGE_DP}dp, стало ${nowLarge}dp",
            nowLarge < WAS_LARGE_DP
        )
    }
}
