package ru.capital.idle.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Chronicle
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.EnterpriseNames
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Museum
import ru.capital.idle.ui.CHRONICLE_PREVIEW
import ru.capital.idle.ui.ChronicleSection
import ru.capital.idle.ui.HistoryTab
import ru.capital.idle.ui.ProfileTabsRow

/**
 * Вкладка «История»: цифры за все жизни, под ними хроника.
 *
 * Хроника по умолчанию свёрнута до [CHRONICLE_PREVIEW] записей — статистика остаётся
 * главным содержимым вкладки. Снимается и лента, и вкладка целиком: важно, что заголовок
 * хроники стоит под всеми блоками статистики, а не между ними.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class HistoryTabScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Предельно длинное название предприятия: 22 знака, дальше диалог ввод не пускает. */
    private val longestName = "Всё по чертежу и точка"

    /**
     * Лента из разнородных записей: длинные строки, предельное название предприятия,
     * повторы одной отрасли — ровно то, ради чего название в запись и добавили.
     */
    private fun chronicle(n: Int): List<String> {
        val samples = listOf(
            "biz" to "prod:1:$longestName",
            "biz" to "prod:1:Литьё и точка",
            "biz" to "trade:1:Лоток у метро",
            "edu" to "mba",
            "own" to "car:7",
            "exp" to "space",
            "auc+" to "temple:5.2E10",
            "ms" to "3",
            "title" to "6",
            "job" to "director"
        )
        return List(n) { i ->
            val (code, param) = samples[i % samples.size]
            Chronicle.entry(60 - i, code, param)
        }
    }

    private fun stateWith(records: Int, lives: Int = 0) = GameState(
        gameHours = 24.0 * 60,
        statAllTimeEarned = 251_000_000_000.0,
        statTaps = 128_400,
        statTapEarned = 4_200_000.0,
        statBestDayIncome = 999_999_999.0,
        statBullionEarned = 13_228,
        statBizLevels = 34,
        statLifeItems = 17,
        statDaysPrevLives = 420,
        reputation = 84.0,
        chronicle = chronicle(records),
        museum = List(lives) { Museum.entry(it + 1, 180, 4.2e11, 6, 5, 7) }
    )

    // ===================== лента =====================

    private fun section(name: String, records: Int, expanded: Boolean, fontScale: Float = 1f) {
        compose.captureOnBackground(name, fontScale = fontScale) {
            Column(Modifier.fillMaxWidth()) {
                ChronicleSection(stateWith(records), expanded = expanded, onToggle = {})
            }
        }
    }

    @Test
    fun `лента свёрнута`() = section("history_chronicle_collapsed", 40, expanded = false)

    @Test
    fun `лента свёрнута при крупном шрифте`() =
        section("history_chronicle_collapsed_large_font", 40, expanded = false,
            fontScale = Screenshots.LARGE_FONT)

    @Test
    fun `лента развёрнута`() = section("history_chronicle_expanded", 40, expanded = true)

    @Test
    fun `лента развёрнута при крупном шрифте`() =
        section("history_chronicle_expanded_large_font", 40, expanded = true,
            fontScale = Screenshots.LARGE_FONT)

    /** Новая игра: событий ещё нет, кнопки разворота тоже быть не должно. */
    @Test
    fun `пустая лента`() {
        compose.captureOnBackground("history_chronicle_empty") {
            Column(Modifier.fillMaxWidth()) {
                ChronicleSection(GameState(), expanded = false, onToggle = {})
            }
        }
    }

    @Test
    fun `пустая лента при крупном шрифте`() {
        compose.captureOnBackground("history_chronicle_empty_large_font",
            fontScale = Screenshots.LARGE_FONT) {
            Column(Modifier.fillMaxWidth()) {
                ChronicleSection(GameState(), expanded = false, onToggle = {})
            }
        }
    }

    /** Ровно столько записей, сколько показывается: кнопка не нужна. */
    @Test
    fun `лента ровно в предел без кнопки`() =
        section("history_chronicle_exact", CHRONICLE_PREVIEW, expanded = false)

    /** Прошлые жизни идут отдельными карточками над лентой. */
    @Test
    fun `лента с прошлыми жизнями`() {
        compose.captureOnBackground("history_chronicle_museum") {
            Column(Modifier.fillMaxWidth()) {
                ChronicleSection(stateWith(3, lives = 2), expanded = false, onToggle = {})
            }
        }
    }

    // ===================== вкладка целиком =====================

    @Test
    fun `вкладка целиком — статистика, под ней хроника`() {
        compose.captureOnBackground("history_tab") {
            Column(Modifier.fillMaxWidth()) {
                HistoryTab(stateWith(40), Currency.USD)
            }
        }
    }

    // ===================== ряд вкладок =====================

    /**
     * Четыре подписи должны стоять в одну строку каждая. Проверяется высотами узлов:
     * подпись, которой не хватило ширины, переносится и становится вдвое выше остальных.
     */
    private fun assertTabsOnOneLine(shot: String, fontScale: Float) {
        compose.captureOnBackground(shot, fontScale = fontScale) {
            ProfileTabsRow(selected = 0, onSelect = {})
        }
        val heights = listOf("Имущество", "Отдых", "Коллекция", "История").associateWith {
            compose.onNodeWithText(it).fetchSemanticsNode().size.height
        }
        assertEquals("подписи вкладок разной высоты — значит какая-то перенеслась: $heights",
            1, heights.values.toSet().size)
    }

    @Test
    fun `подписи вкладок помещаются в одну строку`() =
        assertTabsOnOneLine("profile_tabs_four", 1f)

    @Test
    fun `подписи вкладок помещаются в одну строку при крупном шрифте`() =
        assertTabsOnOneLine("profile_tabs_four_large_font", Screenshots.LARGE_FONT)

    /** Названий вкладок ровно четыре, «Хроники» среди них больше нет. */
    @Test
    fun `вкладки хроники больше нет`() {
        compose.captureOnBackground("profile_tabs_no_chronicle") {
            ProfileTabsRow(selected = 3, onSelect = {})
        }
        assertEquals(0, compose.onAllNodesWithText("Хроника").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Цифры").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("История").fetchSemanticsNodes().size)
    }

    /** Предел длины названия предприятия не должен незаметно вырасти. */
    @Test
    fun `предельное название в тесте совпадает с пределом ввода`() {
        assertEquals(EnterpriseNames.MAX_NAME_LEN, longestName.length)
    }
}
