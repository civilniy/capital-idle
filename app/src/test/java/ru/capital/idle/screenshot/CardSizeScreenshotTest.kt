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
import ru.capital.idle.ui.CARD_ASPECT_RATIO
import ru.capital.idle.ui.CARD_REFERENCE_HEIGHT_DP
import ru.capital.idle.ui.CARD_REFERENCE_WIDTH_DP
import ru.capital.idle.ui.CardFace
import ru.capital.idle.ui.PressureSlot
import ru.capital.idle.ui.theme.Bg

/**
 * Карта на главном экране: постоянные пропорции и высота, не зависящая от содержимого.
 *
 * Раньше высоту карты определял текст — длинный баланс делал её выше короткого, и всё под
 * картой прыгало. Теперь высота считается от ширины по константе проекта (опорный размер
 * карты, 365 × 218 dp), а содержимое подстраивается под карту.
 *
 * Отсюда же и проверки на узких экранах: раз высота считается от ширины, на экране уже
 * опорного карта ниже опорной — и содержимое обязано уменьшаться вместе с ней.
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

    // ===================== геометрия карты в корневых координатах =====================

    /** Верх карты: снимок добавляет свой вертикальный отступ, карта начинается под ним. */
    private fun cardTopPx() = with(compose.density) { 10.dp.toPx() }

    /**
     * Низ карты берётся из настоящего размера корня, а не из [CARD_REFERENCE_HEIGHT_DP]:
     * высота считается от ширины экрана, и на узком экране карта ниже опорной.
     */
    private fun cardBottomPx() = rootHeight() - with(compose.density) { 10.dp.toPx() }

    /** Внутреннее поле карты — под ним содержимому уже нельзя. */
    private fun contentBottomLimitPx() = cardBottomPx() - with(compose.density) { 16.dp.toPx() }

    /** Верх фирменного знака: 49×32dp, прижат к низу карты с отступом 16dp. */
    private fun logoTopPx() = cardBottomPx() - with(compose.density) { (16 + 32).dp.toPx() }

    private fun px2dp(v: Float) = v / compose.density.density

    /**
     * Верх и высота узла с таким текстом, в пикселях от верха корня.
     *
     * Берётся `positionInRoot` + `size`, а не `boundsInRoot`: второй обрезан родителями,
     * и у вылезшего за карту текста он показал бы край карты вместо настоящего края текста.
     */
    private fun textBox(text: String): Pair<Float, Int> {
        compose.waitForIdle()
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        return node.positionInRoot.y to node.size.height
    }

    /**
     * Ниже этой высоты строка считается несуществующей.
     *
     * `Column` при нехватке места не выталкивает нижние элементы за край, а меряет их
     * с нулевым запасом по высоте — строка остаётся в дереве, но рисуется в ноль пикселей.
     * Поэтому проверять «низ внутри карты» недостаточно: сжатая в ноль строка эту проверку
     * проходит, а на экране её нет. Порог взят с запасом вниз: самая мелкая строка карты —
     * подпись тира в 11sp, при увеличенном системном шрифте она сжимается `textScale`
     * примерно до 11dp экранной высоты, так что 8dp ниже любой нормальной строки.
     */
    private val MIN_LINE_DP = 8f

    // ===================== пропорции =====================

    @Test
    fun `константа пропорций совпадает с размером карты в макете`() {
        // 365 × 218 dp — ровно то, в чём карта нарисована. Внешним стандартом эту константу
        // подменять нельзя: от формата ISO/IEC 7810 (1.586) карта вырастала на 12dp
        assertEquals(
            CARD_REFERENCE_WIDTH_DP / CARD_REFERENCE_HEIGHT_DP, CARD_ASPECT_RATIO, 1e-6f
        )
        assertEquals(365f, CARD_REFERENCE_WIDTH_DP, 1e-6f)
        assertEquals(218f, CARD_REFERENCE_HEIGHT_DP, 1e-6f)
    }

    @Test
    fun `карта занимает ровно размер из макета`() {
        compose.setContent { Screen() }
        val root = compose.onRoot().fetchSemanticsNode().size
        val sidePad = with(compose.density) { 28.dp.roundToPx() }
        val vertPad = with(compose.density) { 20.dp.roundToPx() }
        val expectedW = with(compose.density) { CARD_REFERENCE_WIDTH_DP.dp.roundToPx() }
        val expectedH = with(compose.density) { CARD_REFERENCE_HEIGHT_DP.dp.roundToPx() }
        assertEquals("ширина карты", expectedW, root.width - sidePad)
        assertEquals("высота карты", expectedH.toDouble(), (root.height - vertPad).toDouble(), 1.0)
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

    // ===================== содержимое помещается в карту =====================

    /**
     * Отдельная проверка на саму строку с именем владельца: она есть на карте и нарисована.
     *
     * Её исчезновение не поймал ни один тест — снимки карты были сняты только на опорной
     * ширине экрана, а размер и пропорции карты сходились и без этой строки.
     *
     * Проверяется на всех трёх ширинах экрана, потому что именно ширина и определяет,
     * хватит ли карте высоты: строка имени в потоке последняя и обрезается первой.
     */
    @Test
    fun `строка с именем владельца есть на карте`() {
        compose.setContent { Screen() }
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            names.forEach { (kind, name) ->
                playerName.value = name
                val (_, h) = textBox(name.uppercase(java.util.Locale.ROOT))
                assertTrue(
                    "имя владельца ($kind, шрифт $fs) нарисовано высотой ${px2dp(h.toFloat())}dp",
                    px2dp(h.toFloat()) >= MIN_LINE_DP
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
    fun `строка с именем владельца есть на карте и на узком экране`() {
        compose.setContent { Screen() }
        assertScreenWidth(360f)
        playerName.value = names.last().second
        val (_, h) = textBox(names.last().second.uppercase(java.util.Locale.ROOT))
        assertTrue("имя владельца нарисовано высотой ${px2dp(h.toFloat())}dp",
            px2dp(h.toFloat()) >= MIN_LINE_DP)
    }

    /** Пустое имя строкой на карте не показывается — это не потеря, а отсутствие данных. */
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
     * Прогнать все состояния карты и собрать те, где содержимому не хватило места.
     * Значение — что именно случилось и сколько dp не хватило.
     */
    private fun overflowReport(): Map<String, String> {
        val bad = LinkedHashMap<String, String>()
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            names.forEach { (nameKind, name) ->
                playerName.value = name
                balances.forEach { (balKind, v) ->
                    money.doubleValue = v
                    val key = "$nameKind/$balKind/шрифт=$fs"
                    val cardH = px2dp(cardBottomPx() - cardTopPx())

                    val (nameTop, nameH) = textBox(name.uppercase(java.util.Locale.ROOT))
                    if (px2dp(nameH.toFloat()) < MIN_LINE_DP) {
                        bad["имя сжато: $key"] = "строка %.1fdp при карте %.1fdp".format(
                            java.util.Locale.ROOT, px2dp(nameH.toFloat()), cardH
                        )
                    } else if (nameTop + nameH > contentBottomLimitPx()) {
                        bad["имя за краем: $key"] = "карта %.1fdp, нужно %.1fdp".format(
                            java.util.Locale.ROOT, cardH, px2dp(nameTop + nameH - cardTopPx()) + 16f
                        )
                    }

                    val (tierTop, tierH) = textBox(CardTier.entries.last().title)
                    if (px2dp(tierH.toFloat()) < MIN_LINE_DP) {
                        bad["тир сжат: $key"] = "строка %.1fdp при карте %.1fdp".format(
                            java.util.Locale.ROOT, px2dp(tierH.toFloat()), cardH
                        )
                    } else if (tierTop + tierH > logoTopPx()) {
                        bad["тир на знаке: $key"] = "заходит на %.1fdp".format(
                            java.util.Locale.ROOT, px2dp(tierTop + tierH - logoTopPx())
                        )
                    }
                }
            }
        }
        return bad
    }

    /**
     * Строка с именем владельца — последняя в потоке карты, а подпись тира стоит над
     * фирменным знаком. Оба пропадают первыми, если содержимое перестало помещаться:
     * карта обрезает всё, что вышло за её край. Ни один тест этого не ловил.
     */
    @Test
    fun `содержимое помещается в карту на опорном экране`() {
        compose.setContent { Screen() }
        val bad = overflowReport()
        assertTrue("содержимое не помещается в карту: $bad", bad.isEmpty())
    }

    /**
     * Ширина экрана в dp меняется и от модели телефона, и от системной настройки размера
     * экрана. Высота карты считается от ширины, а содержимое внутри задано в dp и от
     * ширины не зависит — значит на узком экране карта может стать ниже содержимого.
     */
    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
    fun `содержимое помещается в карту на узком экране`() {
        compose.setContent { Screen() }
        assertScreenWidth(360f)
        val bad = overflowReport()
        assertTrue("содержимое не помещается в карту: $bad", bad.isEmpty())
    }

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h640dp-xxhdpi")
    fun `содержимое помещается в карту на самом узком экране`() {
        compose.setContent { Screen() }
        assertScreenWidth(320f)
        val bad = overflowReport()
        assertTrue("содержимое не помещается в карту: $bad", bad.isEmpty())
    }

    /**
     * Без этой проверки узкие тесты бессмысленны: если квалификатор ширины не применился,
     * они молча гоняют ту же опорную раскладку и всё «проходит».
     */
    private fun assertScreenWidth(expectedDp: Float) {
        compose.waitForIdle()
        val w = px2dp(compose.onRoot().fetchSemanticsNode().size.width.toFloat())
        assertEquals("ширина экрана в тесте", expectedDp, w, 1f)
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

    /** Карта на узком экране: высота считается от ширины, а содержимое задано в dp. */
    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h800dp-xxhdpi")
    fun `карта на узком экране`() {
        compose.setContent { Screen() }
        playerName.value = names.last().second
        money.doubleValue = 25_963_353.0
        capture("card_narrow_360")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w320dp-h640dp-xxhdpi")
    fun `карта на самом узком экране`() {
        compose.setContent { Screen() }
        playerName.value = names.last().second
        money.doubleValue = 25_963_353.0
        capture("card_narrow_320")
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
