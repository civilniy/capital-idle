package ru.capital.idle.screenshot

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
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
import ru.capital.idle.ui.PassiveCard
import ru.capital.idle.ui.theme.AppTheme
import ru.capital.idle.ui.theme.Bg
import ru.capital.idle.ui.theme.LocalPalette

/**
 * Кнопки автовклада — те же, что в карточках накоплений.
 *
 * Ряд выбора инструмента и ряд «50% · На все · Вывести» собраны одним компонентом, поэтому
 * совпадать у них обязано всё: высота, скругление, наличие фона. Здесь это и проверяется —
 * причём по картинке, а не по исходнику: скругление в коде можно задать одинаково,
 * а нарисовать по-разному, если тема подставит другое значение.
 *
 * Плюс два свойства самого ряда выбора: три кнопки одной ширины и подпись, которая
 * помещается в кнопку целиком — «Недвижимость» длиннее трети строки при увеличенном шрифте.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE_ENDLESS)
class InvestButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    private val theme = mutableStateOf(AppTheme.GLASS)
    private val fontScale = mutableFloatStateOf(1f)
    private val locked = mutableStateOf(false)

    private fun saver(all: Boolean) = GameState(
        money = 5_000_000.0,
        eduDone = if (all) setOf("school", "acc", "uni") else setOf("school", "acc"),
        autoInvestOn = true, autoInvestReserve = 10_000.0,
        autoInvestAsset = Asset.BONDS.ordinal,
        investValues = listOf(1_000_000.0, 0.0, 0.0)
    )

    /** Блок автовклада и соседняя карточка накоплений — рядом, как на экране. */
    @Composable
    private fun Cards() {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.floatValue),
            LocalPalette provides theme.value.palette
        ) {
            val s = saver(all = !locked.value)
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    AutoInvestCard(
                        on = true,
                        target = AutoInvest.target(s),
                        unlocked = AutoInvest.available(s),
                        reserve = s.autoInvestReserve,
                        amount = AutoInvest.amount(s),
                        cur = Currency.USD
                    )
                    Spacer(Modifier.height(16.dp))
                    PassiveCard(s, Asset.DEPOSIT, Currency.USD,
                        onInvest = {}, onSell = {}, onToggleCap = {})
                }
            }
        }
    }

    private fun boundsOf(text: String, unmerged: Boolean = false): Rect =
        compose.onNodeWithText(text, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    /** Кнопки выбора инструмента слева направо. */
    private fun pickButtons(): List<Rect> = Asset.entries.map { boundsOf(it.title) }

    /** Кнопки накоплений — образец, по которому равняется всё остальное. */
    private fun savingsButtons(): List<Rect> = listOf("50%", "На все", "Вывести").map { boundsOf(it) }

    private fun eachThemeAndScale(body: (String) -> Unit) {
        compose.setContent { Cards() }
        listOf(AppTheme.GLASS, AppTheme.MATTE).forEach { t ->
            theme.value = t
            listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
                fontScale.floatValue = fs
                compose.waitForIdle()
                body("${t.title}, шрифт $fs")
            }
        }
    }

    private fun dp(px: Float) = px / compose.density.density

    // ===================== ширина =====================

    @Test
    fun `три кнопки выбора одной ширины`() = eachThemeAndScale { where ->
        val widths = pickButtons().map { Math.round(it.width) }
        assertEquals("ширина кнопок разошлась ($where): $widths", 1, widths.distinct().size)

        // и промежутки между ними тоже одинаковые
        val gaps = pickButtons().zipWithNext { a, b -> Math.round(b.left - a.right) }
        assertEquals("промежутки разошлись ($where): $gaps", 1, gaps.distinct().size)
    }

    // ===================== один и тот же компонент =====================

    @Test
    fun `высота кнопок выбора совпадает с кнопками накоплений`() = eachThemeAndScale { where ->
        val pick = pickButtons().map { Math.round(it.height) }.distinct()
        val savings = savingsButtons().map { Math.round(it.height) }.distinct()
        assertEquals("высота кнопок выбора неодинакова ($where): $pick", 1, pick.size)
        assertEquals("высота кнопок накоплений неодинакова ($where): $savings", 1, savings.size)
        assertEquals("ряды разной высоты ($where)", savings.first(), pick.first())
    }

    /**
     * Скругление у обоих рядов одно и то же.
     *
     * Проверяется углом на картинке: у кнопки с тем же радиусом угол «съеден» одинаково,
     * и маска закрашенных пикселей совпадает. Обе кнопки лежат на одинаковой подложке
     * карточки и залиты одним цветом, поэтому маски сравнимы напрямую.
     */
    @Test
    fun `скругление кнопок выбора совпадает с кнопками накоплений`() = eachThemeAndScale { where ->
        val img = shot("corners")
        // цвет карточки — рядом с кнопкой, чуть выше её верхнего края
        val card = img.getPixel(
            Math.round(pickButtons()[0].left) + 2,
            Math.round(pickButtons()[0].top) - 4
        )
        fun corner(r: Rect): List<Boolean> {
            val x0 = Math.round(r.left)
            val y0 = Math.round(r.top)
            return (0 until CORNER_PX).flatMap { dy ->
                (0 until CORNER_PX).map { dx -> img.getPixel(x0 + dx, y0 + dy) != card }
            }
        }
        // невыбранная кнопка выбора и соседняя кнопка накоплений: обе обычного фона
        val pick = corner(pickButtons()[0])
        val savings = corner(savingsButtons()[0])
        val diff = pick.zip(savings).count { (a, b) -> a != b }
        assertTrue("углы кнопок разной формы ($where): расходится $diff точек", diff <= 2)
    }

    // ===================== фон в любом состоянии =====================

    @Test
    fun `у всех трёх кнопок выбора есть фон`() {
        compose.setContent { Cards() }
        listOf(AppTheme.GLASS, AppTheme.MATTE).forEach { t ->
            theme.value = t
            listOf(false, true).forEach { lock ->
                locked.value = lock
                listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
                    fontScale.floatValue = fs
                    compose.waitForIdle()
                    val where = "${t.title}, шрифт $fs, " +
                        if (lock) "недвижимость закрыта" else "всё открыто"
                    val img = shot("fills")
                    // цвет карточки берётся рядом с кнопками — у левого края, между рядами
                    val cardFill = img.getPixel(
                        Math.round(pickButtons()[0].left) + 2,
                        Math.round(pickButtons()[0].top) - 4
                    )
                    Asset.entries.forEachIndexed { i, a ->
                        val r = pickButtons()[i]
                        val px = img.getPixel(
                            Math.round(r.left) + Math.round(6 * compose.density.density),
                            Math.round(r.center.y)
                        )
                        assertTrue(
                            "у кнопки «${a.title}» нет фона ($where)",
                            px != cardFill
                        )
                    }
                }
            }
        }
        locked.value = false
    }

    // ===================== подпись помещается =====================

    /**
     * «Недвижимость» — самое длинное название в самой узкой кнопке.
     *
     * Перенос запрещён структурно (`softWrap = false`), поэтому не влезшая подпись
     * не перенеслась бы, а обрезалась — и ширина строки совпала бы с шириной кнопки.
     * Отсюда проверка: строка обязана быть заметно уже кнопки.
     */
    @Test
    fun `подпись помещается в кнопку целиком`() = eachThemeAndScale { where ->
        Asset.entries.forEach { a ->
            val button = boundsOf(a.title)
            val label = boundsOf(a.title, unmerged = true)
            assertTrue(
                "«${a.title}» не помещается ($where): подпись ${dp(label.width)}dp " +
                    "при кнопке ${dp(button.width)}dp",
                label.width <= button.width - MIN_SIDE_PAD_DP * compose.density.density
            )
            // и стоит одной строкой: две строки были бы вдвое выше
            val single = boundsOf(Asset.DEPOSIT.title, unmerged = true).height
            assertTrue(
                "«${a.title}» встала в две строки ($where)",
                label.height <= single * 1.4f
            )
        }
    }

    // ===================== снимок для замеров =====================

    /** Снимок для замеров по пикселям. Эталоном не становится и в репозиторий не попадает. */
    private fun shot(name: String): android.graphics.Bitmap {
        val path = "build/tmp/invest-buttons/$name.png"
        compose.onRoot().captureRoboImage(
            filePath = path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        )
        return BitmapFactory.decodeFile(path)
    }

    private companion object {
        /** Сколько точек угла сравнивать: скругление у кнопок 13–18dp, хватает и десяти. */
        const val CORNER_PX = 10

        /** Минимальный воздух между подписью и краем кнопки. */
        const val MIN_SIDE_PAD_DP = 6
    }
}
