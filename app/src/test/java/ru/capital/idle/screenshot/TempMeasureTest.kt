package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import ru.capital.idle.core.game.CardTier
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.ui.CardFace
import ru.capital.idle.ui.theme.Bg

/** ВРЕМЕННЫЙ замер: высота строки баланса и высота карты. Удаляется после снятия чисел. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class TempMeasureTest {

    @get:Rule
    val compose = createComposeRule()

    private val money = mutableDoubleStateOf(999_999_999.0)
    private val fontScale = mutableFloatStateOf(1f)

    @Composable
    private fun Screen() {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.floatValue)
        ) {
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    CardFace(
                        money = money.doubleValue, incomePerDay = 413_000_000.0,
                        currency = Currency.USD, playerName = "Владимир",
                        tier = CardTier.entries.last()
                    )
                }
            }
        }
    }

    @Test
    fun `замер`() {
        compose.setContent { Screen() }
        val out = StringBuilder()
        listOf(1f, 1.5f).forEach { fs ->
            fontScale.floatValue = fs
            listOf(0.0, 1_200_000_000.0, 999_999_999.0, 32_400_000_000_000.0, 940_015_169.0).forEach { v ->
                money.doubleValue = v
                compose.waitForIdle()
                val text = GameMath.formatMoney(v, Currency.USD)
                val node = compose.onNodeWithText(text).fetchSemanticsNode()
                val root = compose.onRoot().fetchSemanticsNode().size.height
                val d = compose.density.density
                out.append("fs=$fs \"$text\" lineH=${node.size.height}px=${node.size.height / d}dp ")
                out.append("rootH=${root}px=${root / d}dp\n")
            }
        }
        assertEquals(out.toString(), 1, 2)
    }
}
