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
    private val lime = 0x8CD62B      // доход, работа по найму
    private val mint = 0x25D0A4      // бизнес, производство, рост
    private val lilac = 0x9B7FE8     // сон, отдых, статус
    private val sky = 0x4DA3FF       // учёба, информация
    private val coral = 0xFF6B5A     // расход, убыток, риск

    private val accents = mapOf(
        amber to "янтарь", lime to "лайм", mint to "мята",
        lilac to "сирень", sky to "небесный", coral to "коралл"
    )

    // фон, карточка, вложенный элемент
    private val layers = mapOf(0x0E0F13 to "фон", 0x181A20 to "карточка", 0x22252D to "вложенный")
    private val texts = mapOf(0xF2F1EC to "основной", 0xCDCED4 to "вторичный", 0x7E8088 to "приглушённый")
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
        "learned" to MattePalette.learned,
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
        assertEquals("доход и работа по найму — лайм", lime, rgb(MattePalette.income))
        assertEquals("бизнес и рост — мята", mint, rgb(MattePalette.business))
        assertEquals("сон, отдых и статус — сирень", lilac, rgb(MattePalette.rest))
        assertEquals("статус — сирень", lilac, rgb(MattePalette.status))
        assertEquals("учёба — небесный", sky, rgb(MattePalette.study))
        assertEquals("пройденный курс — небесный", sky, rgb(MattePalette.learned))
        assertEquals("расход и риск — коралл", coral, rgb(MattePalette.expense))
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
