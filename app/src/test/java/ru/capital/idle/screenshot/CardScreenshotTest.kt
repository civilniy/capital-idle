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
import androidx.compose.ui.test.onAllNodesWithText
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
import ru.capital.idle.ui.CardFace
import ru.capital.idle.ui.PressureSlot
import ru.capital.idle.ui.theme.Bg

/**
 * Карта на главном экране и плашка давления под ней.
 *
 * Высота карты задана нижней границей `heightIn(min = 218.dp)` и дальше определяется
 * содержимым: длинное имя, крупный системный шрифт — карта вырастает, и всё помещается.
 *
 * Правило теста Compose: `setContent` вызывается один раз, поэтому варианты гоняются
 * через состояние, а не повторной установкой содержимого.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class CardScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Балансы: предельно длинный, короткий с суффиксом и обычный восьмизначный. */
    private val balances = listOf(
        "max" to 999_999_999.0,
        "short" to 1_200_000_000.0,
        "mid" to 25_963_353.0
    )

    /**
     * Имена: обычное, длинное с дефисом и предельное. Предел — 18 знаков,
     * `GameViewModel` режет ввод именно по нему (`name.trim().take(18)`).
     */
    private val names = listOf(
        "обычное" to "Владимир",
        "длинное" to "Мстислав-Радомир",
        "предельное" to "Владислав-Мстислав"
    )

    private val money = mutableDoubleStateOf(999_999_999.0)
    private val reward = mutableDoubleStateOf(0.0)
    private val popTick = mutableIntStateOf(0)
    private val fontScale = mutableFloatStateOf(1f)
    private val pressure = mutableDoubleStateOf(0.0)
    private val reserved = mutableStateOf(true)
    private val withPressureSlot = mutableStateOf(false)
    private val playerName = mutableStateOf("Владимир")

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
                        currency = Currency.USD, playerName = playerName.value,
                        tier = CardTier.entries.last(),
                        popAccum = reward.doubleValue, popTick = popTick.intValue
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

    // ===================== геометрия =====================

    /** Верх карты: снимок добавляет свой вертикальный отступ, карта начинается под ним. */
    private fun cardTopPx() = with(compose.density) { 10.dp.toPx() }

    private fun cardBottomPx() = rootHeight() - with(compose.density) { 10.dp.toPx() }

    /** Верх фирменного знака: 49×32dp, прижат к низу карты с отступом 16dp. */
    private fun logoTopPx() = cardBottomPx() - with(compose.density) { (16 + 32).dp.toPx() }

    private fun px2dp(v: Float) = v / compose.density.density

    /** Верх и высота узла с таким текстом, в пикселях от верха корня. */
    private fun textBox(text: String): Pair<Float, Int> {
        compose.waitForIdle()
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        return node.positionInRoot.y to node.size.height
    }

    /**
     * Ниже этой высоты строка считается несуществующей.
     *
     * Проверять только наличие узла в дереве мало: если содержимому не хватит места,
     * `Column` не вытолкнет нижние строки за край, а померяет их с нулевым запасом —
     * узел останется, а на экране строки не будет. Порог взят с запасом вниз: имя рисуется
     * кеглем 15sp с межстрочным 18sp, так что 8dp ниже любой нормальной строки.
     */
    private val MIN_LINE_DP = 8f

    // ===================== имя владельца =====================

    /**
     * Строка с именем владельца есть на карте и нарисована.
     *
     * Её пропажу не поймала ни одна проверка: снимки карты сверяли размер и пропорции,
     * а они сходились и без этой строки. Строка имени в потоке последняя и исчезает первой,
     * поэтому проверка на неё стоит отдельно от всего остального.
     */
    @Test
    fun `строка с именем владельца есть на карте`() {
        compose.setContent { Screen() }
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            names.forEach { (kind, name) ->
                playerName.value = name
                balances.forEach { (balKind, v) ->
                    money.doubleValue = v
                    val (_, h) = textBox(name.uppercase(java.util.Locale.ROOT))
                    assertTrue(
                        "имя владельца ($kind, $balKind, шрифт $fs) нарисовано " +
                            "высотой ${px2dp(h.toFloat())}dp",
                        px2dp(h.toFloat()) >= MIN_LINE_DP
                    )
                }
            }
        }
    }

    /** Пустое имя строкой на карте не показывается — это отсутствие данных, а не потеря. */
    @Test
    fun `без имени строки нет`() {
        compose.setContent { Screen() }
        playerName.value = ""
        compose.waitForIdle()
        assertTrue(
            "пустое имя не должно рисоваться строкой",
            compose.onAllNodesWithText("ВЛАДИМИР").fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * Подпись тира прижата вправо, фирменный знак — тоже. Если подпись опускается до знака,
     * они печатаются друг поверх друга.
     */
    @Test
    fun `подпись тира не доходит до фирменного знака`() {
        compose.setContent { Screen() }
        val hits = LinkedHashMap<String, String>()
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            names.forEach { (kind, name) ->
                playerName.value = name
                val (top, h) = textBox(CardTier.entries.last().title)
                if (top + h > logoTopPx()) {
                    hits["$kind/шрифт=$fs"] = "заходит на %.1fdp".format(
                        java.util.Locale.ROOT, px2dp(top + h - logoTopPx())
                    )
                }
            }
        }
        assertTrue("подпись тира наезжает на фирменный знак: $hits", hits.isEmpty())
    }

    /** Карта растёт под содержимое: нижняя граница — не потолок. */
    @Test
    fun `карта не ниже нижней границы`() {
        compose.setContent { Screen() }
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            names.forEach { (_, name) ->
                playerName.value = name
                val h = px2dp(cardBottomPx() - cardTopPx())
                assertTrue("карта высотой ${h}dp при шрифте $fs", h >= 218f - 0.5f)
            }
        }
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
    fun `карта при разных балансах без надбавки`() {
        compose.setContent { Screen() }
        listOf(1f to "", Screenshots.LARGE_FONT to "_large_font").forEach { (fs, tail) ->
            fontScale.floatValue = fs
            balances.forEach { (name, v) ->
                money.doubleValue = v
                capture("card_${name}_plain$tail")
            }
        }
    }

    /**
     * Надбавка показывается 800 мс и к концу тает, поэтому часы останавливаются, а анимация
     * перезапускается перед каждым снимком — иначе к третьему кадру она уже невидима.
     * Лишний кадр после смены состояния нужен, чтобы снимок брался с уже отрисованного экрана,
     * а не с предыдущего.
     */
    @Test
    fun `карта с надбавкой за тап`() {
        compose.mainClock.autoAdvance = false
        reward.doubleValue = 999_999_999.0
        compose.setContent { Screen() }
        listOf(1f to "", Screenshots.LARGE_FONT to "_large_font").forEach { (fs, tail) ->
            fontScale.floatValue = fs
            balances.forEach { (name, v) ->
                money.doubleValue = v
                popTick.intValue += 1
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(300)   // середина показа: надбавка непрозрачна
                compose.mainClock.advanceTimeByFrame()
                compose.onRoot().captureRoboImage(
                    filePath = "${Screenshots.DIR}/card_${name}_pop$tail.png",
                    roborazziOptions = Screenshots.OPTIONS
                )
            }
        }
    }

    /** Предельное имя в 18 знаков: левая колонка шире всего, подписи тира теснее всего. */
    @Test
    fun `карта с предельно длинным именем владельца`() {
        compose.setContent { Screen() }
        playerName.value = names.last().second
        money.doubleValue = 25_963_353.0
        capture("card_longest_name")
        fontScale.floatValue = Screenshots.LARGE_FONT
        capture("card_longest_name_large_font")
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
