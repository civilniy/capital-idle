package ru.capital.idle

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.ui.theme.GlassPalette
import ru.capital.idle.ui.theme.MattePalette

/**
 * Палитра новой темы — закрытый список.
 *
 * Шесть акцентов по смыслу, три ступени фона и три ступени текста. Ничего другого в теме
 * быть не должно: именно от «а вот тут ещё один синий» оформление и расползается.
 * Прозрачность значения не имеет — тонированная подложка это тот же акцент, только слабее.
 */
class MattePaletteTest {

    // ===== закрытый список =====
    private val amber = 0xFFB02E     // деньги, бренд, активная вкладка
    private val blue = 0x4A9BF5      // учёба, информация
    private val teal = 0x16C79A      // бизнес, рост
    private val green = 0x3DD068     // доход, работа по найму
    private val purple = 0xA78BFA    // сон, статус
    private val coral = 0xFF6B5A     // расход, риск
    private val pink = 0xF472B6      // коллекция, редкое

    private val accents = mapOf(
        amber to "янтарь", blue to "синий", teal to "бирюза",
        green to "зелёный", purple to "сирень", coral to "коралл", pink to "розовый"
    )

    // фон, карточка, вложенный элемент, третий уровень
    private val layers = mapOf(
        0x0B0B0D to "фон", 0x1A1B1E to "карточка",
        0x26272B to "вложенный", 0x323338 to "третий уровень"
    )
    private val texts = mapOf(0xF5F4F2 to "основной", 0xC4C5CA to "вторичный", 0x7C7E86 to "приглушённый")
    private val neutral = mapOf(0x000000 to "чёрный", 0xFFFFFF to "белый")

    private val allowed = accents + layers + texts + neutral

    /** Цвет без прозрачности: тонированная подложка — тот же цвет, только слабее. */
    private fun rgb(c: Color): Int = (c.value shr 32).toInt() and 0xFFFFFF

    private fun slots() = listOf(
        "bg" to MattePalette.bg, "panel" to MattePalette.panel, "panel2" to MattePalette.panel2,
        "line" to MattePalette.line, "divider" to MattePalette.divider,
        "cardFill" to MattePalette.cardFill, "innerFill" to MattePalette.innerFill,
        "accentFill" to MattePalette.accentFill, "accentStrong" to MattePalette.accentStrong,
        "btnFill" to MattePalette.btnFill, "btnOffFill" to MattePalette.btnOffFill,
        "btnOffText" to MattePalette.btnOffText,
        "sellFill" to MattePalette.sellFill, "sellText" to MattePalette.sellText,
        "trackFill" to MattePalette.trackFill, "toggleOff" to MattePalette.toggleOff,
        "textMain" to MattePalette.textMain, "textSecondary" to MattePalette.textSecondary,
        "mute" to MattePalette.mute,
        "money" to MattePalette.money, "moneyDim" to MattePalette.moneyDim,
        "onMoney" to MattePalette.onMoney, "heading" to MattePalette.heading,
        "income" to MattePalette.income, "business" to MattePalette.business,
        "rest" to MattePalette.rest, "study" to MattePalette.study,
        "expense" to MattePalette.expense, "warn" to MattePalette.warn,
        "best" to MattePalette.best, "status" to MattePalette.status,
        "learned" to MattePalette.learned, "rare" to MattePalette.rare,
        "surface3" to MattePalette.surface3,
        "moneyFill" to MattePalette.moneyFill, "incomeFill" to MattePalette.incomeFill,
        "businessFill" to MattePalette.businessFill, "studyFill" to MattePalette.studyFill,
        "expenseFill" to MattePalette.expenseFill,
        "eventGoodFill" to MattePalette.eventGoodFill, "eventBadFill" to MattePalette.eventBadFill,
        "dialogBg" to MattePalette.dialogBg, "scrim" to MattePalette.scrim,
        "overlayBg" to MattePalette.overlayBg, "bgBase" to MattePalette.bgBase,
        "spotMoney" to MattePalette.spotMoney, "spotStudy" to MattePalette.spotStudy,
        "spotIncome" to MattePalette.spotIncome
    )

    @Test
    fun `в новой теме нет цветов вне закрытого списка`() {
        val alien = slots()
            .filter { (_, c) -> rgb(c) !in allowed }
            .joinToString("\n") { (name, c) -> "  $name = #%06X".format(rgb(c)) }
        assertEquals("цвета вне списка:\n$alien\n", "", alien)
    }

    @Test
    fun `каждый акцент стоит на своём смысле`() {
        assertEquals("деньги — янтарь", amber, rgb(MattePalette.money))
        assertEquals("капитал и заголовки — янтарь", amber, rgb(MattePalette.heading))
        assertEquals("доход и работа по найму — зелёный", green, rgb(MattePalette.income))
        assertEquals("бизнес и рост — бирюза", teal, rgb(MattePalette.business))
        assertEquals("сон и статус — сирень", purple, rgb(MattePalette.rest))
        assertEquals("статус — сирень", purple, rgb(MattePalette.status))
        assertEquals("учёба и информация — синий", blue, rgb(MattePalette.study))
        assertEquals("пройденный курс — синий", blue, rgb(MattePalette.learned))
        assertEquals("расход и риск — коралл", coral, rgb(MattePalette.expense))
        assertEquals("коллекция и редкое — розовый", pink, rgb(MattePalette.rare))
    }

    /**
     * Поверхности нейтральные.
     *
     * Тёплый цвет с малой прозрачностью на почти чёрном даёт коричневую грязь: #FFB02E
     * на 13% поверх #0B0B0D — это #2D2417. Поэтому подложек акцентом в новой теме нет
     * вовсе, и проверка меряет именно это: у любой поверхности разброс каналов мал.
     */
    @Test
    fun `ни одна поверхность не подкрашена акцентом`() {
        val surfaces = listOf(
            "bg" to MattePalette.bg, "panel" to MattePalette.panel, "panel2" to MattePalette.panel2,
            "surface3" to MattePalette.surface3,
            "cardFill" to MattePalette.cardFill, "innerFill" to MattePalette.innerFill,
            "accentFill" to MattePalette.accentFill, "accentStrong" to MattePalette.accentStrong,
            "btnFill" to MattePalette.btnFill, "btnOffFill" to MattePalette.btnOffFill,
            "sellFill" to MattePalette.sellFill, "trackFill" to MattePalette.trackFill,
            "toggleOff" to MattePalette.toggleOff,
            "moneyFill" to MattePalette.moneyFill, "incomeFill" to MattePalette.incomeFill,
            "businessFill" to MattePalette.businessFill, "studyFill" to MattePalette.studyFill,
            "expenseFill" to MattePalette.expenseFill,
            "eventGoodFill" to MattePalette.eventGoodFill, "eventBadFill" to MattePalette.eventBadFill,
            "dialogBg" to MattePalette.dialogBg, "bgBase" to MattePalette.bgBase
        )
        val tinted = surfaces.filter { (_, c) -> chroma(c) > 0.08f }
            .joinToString("\n") { (n, c) -> "  $n = #%06X, разброс %.0f%%".format(rgb(c), chroma(c) * 100) }
        assertEquals("подкрашенные поверхности:\n$tinted\n", "", tinted)
    }

    /** Разброс каналов: 0 у серого, 1 у чистого цвета. */
    private fun chroma(c: Color): Float {
        val v = rgb(c)
        val r = v shr 16 and 0xFF
        val g = v shr 8 and 0xFF
        val b = v and 0xFF
        return (maxOf(r, g, b) - minOf(r, g, b)) / 255f
    }

    @Test
    fun `слои различимы и идут по возрастанию`() {
        // фон темнее карточки, карточка темнее вложенного элемента
        fun lum(c: Color) = rgb(c).let { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        assertTrue("карточка светлее фона", lum(MattePalette.cardFill) > lum(MattePalette.bg))
        assertTrue("вложенный светлее карточки", lum(MattePalette.innerFill) > lum(MattePalette.cardFill))
    }

    @Test
    fun `старая тема не задета закрытым списком`() {
        // у «Стекла» свои значения, и проверка списка на неё не распространяется
        assertEquals(0xE8B54A, rgb(GlassPalette.money))
        assertEquals(0x5FBF7A, rgb(GlassPalette.income))
    }
}
