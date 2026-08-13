package ru.capital.idle.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Оформление интерфейса: набор цветов и правил рисования.
 *
 * Тем две, и переключаются они целиком. Раньше цвета лежали глобальными константами
 * и подставлялись по экранам напрямую; теперь константы стали читалками из этого набора
 * (см. `Color.kt` и `Glass.kt`), а сам набор приходит через [LocalPalette]. Имена слотов
 * оставлены прежними там, где смысл не менялся, — чтобы правка не разъехалась по всем
 * экранам и старая тема осталась прежней до пикселя.
 *
 * Слоты названы по СМЫСЛУ, а не по цвету: `income`, `business`, `study` — а не «зелёный»
 * и «синий». В старой теме несколько смыслов сходятся в один цвет (доход и бизнес оба
 * зелёные), в новой они разведены. Именно поэтому слотов больше, чем было констант.
 */
@Immutable
class Palette(
    /** Идентификатор темы, он же ключ хранения. */
    val id: String,

    // ===== нейтральные слои: фон → карточка → вложенный элемент =====
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    /** Волосяная линия. В новой теме — единственная роль обводки: делить однородные строки. */
    val line: Color,
    val divider: Color,

    // ===== стеклянные (в новой теме — плотные) заливки =====
    val cardFill: Color,
    val innerFill: Color,
    val accentFill: Color,
    val accentStrong: Color,
    val btnFill: Color,
    val btnOffFill: Color,
    val btnOffText: Color,
    val sellFill: Color,
    val sellText: Color,
    val trackFill: Color,
    val toggleOff: Color,

    // ===== текст =====
    val textMain: Color,
    val textSecondary: Color,
    val mute: Color,

    // ===== акценты, по одному смыслу на каждый =====
    /** Деньги и бренд. */
    val money: Color,
    val moneyDim: Color,
    /** Текст поверх денежной (янтарной) заливки. */
    val onMoney: Color,
    /** Заголовки разделов и капитал. */
    val heading: Color,
    /** Доход и работа по найму. */
    val income: Color,
    /** Бизнес, производство, рост. */
    val business: Color,
    /** Сон, отдых, статус. */
    val rest: Color,
    /** Учёба и информация. */
    val study: Color,
    /** Расход, убыток, риск. */
    val expense: Color,
    /**
     * Предупреждение и средний уровень (недосып, частичная готовность).
     * В старой теме это был золотой; в новой золотой значит только деньги, и роль берёт коралл.
     */
    val warn: Color,
    /**
     * Лучший вариант и своё дело: «вы лично», управляющий на 100%, бум на рынке.
     * В старой теме тоже золотой, в новой — цвет бизнеса.
     */
    val best: Color,
    /**
     * Социальный статус: репутация, очки статуса за имущество и впечатления.
     * В старой теме это тот же зелёный, что и доход; в новой — сирень.
     */
    val status: Color,
    /**
     * Пройденное обучение. В старой теме зелёный, в новой — цвет учёбы.
     */
    val learned: Color,

    // ===== тонированные подложки (акцент на 11–13%) =====
    val moneyFill: Color,
    val incomeFill: Color,
    val businessFill: Color,
    val studyFill: Color,
    val expenseFill: Color,
    val eventGoodFill: Color,
    val eventBadFill: Color,

    // ===== диалоги и затемнения =====
    val dialogBg: Color,
    val scrim: Color,
    val overlayBg: Color,

    // ===== фон экрана =====
    val bgBase: Color,
    val spotMoney: Color,
    val spotStudy: Color,
    val spotIncome: Color,

    // ===== правила рисования =====
    /**
     * Рисовать ли обводки у карточек, плиток и кнопок. В новой теме слои разделяет
     * разница фона, а не рамка, поэтому здесь `false`.
     */
    val outlines: Boolean,
    /** Заменять ли эмодзи векторными иконками. */
    val vectorIcons: Boolean,
    /**
     * Держаться ли прежних цветов там, где старая тема сводила несколько смыслов в один цвет.
     *
     * В «Стекле» полосы распорядка золотые все три, плашка рынка серая, ведущих иконок
     * у строк нет. Разводить это по смыслам можно только в новой теме — старая не меняется.
     * См. [legacy].
     */
    val legacyColors: Boolean,
    /**
     * Красить ли крупные суммы акцентным цветом. В новой теме цвет живёт в фигурах —
     * иконках, полосах, подложках и мелких подписях, — а крупные числа набираются
     * основным цветом текста. Исключение (капитал и заголовки) задано отдельными слотами.
     */
    val colorNumbers: Boolean
)

/**
 * «Стекло» — тема по умолчанию: полупрозрачные слои поверх тёмного фона с цветными пятнами.
 *
 * Значения ровно те же, что лежали константами в `Color.kt`/`Glass.kt` и вписанные
 * литералами по экранам. Ни одно из них не меняется — это условие правки.
 */
val GlassPalette = Palette(
    id = ThemeIds.GLASS,

    bg = Color(0xFF15161A),
    panel = Color(0xFF1D1F25),
    panel2 = Color(0xFF24262E),
    line = Color(0xFF2E3038),
    divider = Color(0x14FFFFFF),

    cardFill = Color(0x10FFFFFF),
    innerFill = Color(0x0AFFFFFF),
    accentFill = Color(0x14E8B54A),
    accentStrong = Color(0x2EE8B54A),
    btnFill = Color(0x14FFFFFF),
    btnOffFill = Color(0x06FFFFFF),
    btnOffText = Color(0xFF54565E),
    sellFill = Color(0x2ED9694F),
    sellText = Color(0xFFECA08C),
    trackFill = Color(0x1AFFFFFF),
    toggleOff = Color(0x24FFFFFF),

    textMain = Color(0xFFE9E6DF),
    textSecondary = Color(0xFFE9E6DF),
    mute = Color(0xFF8B8C93),

    money = Color(0xFFE8B54A),
    moneyDim = Color(0xFF9C7A2E),
    onMoney = Color(0xFF2A2410),
    heading = Color(0xFFE8B54A),
    income = Color(0xFF5FBF7A),
    business = Color(0xFF5FBF7A),
    rest = Color(0xFFA98BD8),
    study = Color(0xFF6A9BD8),
    expense = Color(0xFFD9694F),
    warn = Color(0xFFE8B54A),
    best = Color(0xFFE8B54A),
    status = Color(0xFF5FBF7A),
    learned = Color(0xFF5FBF7A),

    moneyFill = Color(0x1FE8B54A),
    incomeFill = Color(0x1F5FBF7A),
    businessFill = Color(0x265FBF7A),
    studyFill = Color(0x265FBF7A),
    expenseFill = Color(0x26D9694F),
    eventGoodFill = Color(0x2615251C),
    eventBadFill = Color(0x262A1A18),

    dialogBg = Color(0xFF1D1F25),
    scrim = Color(0x99000000),
    overlayBg = Color(0xE0101116),

    bgBase = Color(0xFF14151A),
    spotMoney = Color(0x0DE8B54A),
    spotStudy = Color(0x0F6A9BD8),
    spotIncome = Color(0x0A5FBF7A),

    outlines = true,
    vectorIcons = false,
    legacyColors = true,
    colorNumbers = true
)

/**
 * «Матовая» — плотные слои без обводок и с векторными иконками.
 *
 * Три ступени фона (экран → карточка → вложенный элемент) разделяют слои сами,
 * поэтому рамок нет. Акценты разведены по смыслам: доход и бизнес больше не один
 * и тот же зелёный, сон и учёба — не один и тот же сиреневый оттенок «прочего».
 */
val MattePalette = Palette(
    id = ThemeIds.MATTE,

    bg = Color(0xFF0E0F13),
    panel = Color(0xFF181A20),
    panel2 = Color(0xFF22252D),
    line = Color(0x0FFFFFFF),          // белый на 6%
    divider = Color(0x0FFFFFFF),

    cardFill = Color(0xFF181A20),
    innerFill = Color(0xFF22252D),
    accentFill = Color(0x1FFFB02E),    // янтарь на 12%
    accentStrong = Color(0x2EFFB02E),
    btnFill = Color(0xFF22252D),
    btnOffFill = Color(0xFF15171C),
    btnOffText = Color(0xFF5A5C64),
    sellFill = Color(0x24FF6B5A),
    sellText = Color(0xFFFF9C90),
    trackFill = Color(0xFF22252D),
    toggleOff = Color(0xFF2E323C),

    textMain = Color(0xFFF2F1EC),
    textSecondary = Color(0xFFCDCED4),
    mute = Color(0xFF7E8088),

    money = Color(0xFFFFB02E),
    moneyDim = Color(0xFF8A6526),
    onMoney = Color(0xFF241802),
    heading = Color(0xFFFFC24D),
    income = Color(0xFF8CD62B),        // лайм
    business = Color(0xFF25D0A4),      // мята
    rest = Color(0xFF9B7FE8),          // сирень
    study = Color(0xFF4DA3FF),         // небесный
    expense = Color(0xFFFF6B5A),       // коралл
    warn = Color(0xFFFF6B5A),
    best = Color(0xFF25D0A4),
    status = Color(0xFF9B7FE8),
    learned = Color(0xFF4DA3FF),

    moneyFill = Color(0x1FFFB02E),
    incomeFill = Color(0x1F8CD62B),
    businessFill = Color(0x2125D0A4),
    studyFill = Color(0x214DA3FF),
    expenseFill = Color(0x21FF6B5A),
    eventGoodFill = Color(0x1F8CD62B),
    eventBadFill = Color(0x21FF6B5A),

    dialogBg = Color(0xFF181A20),
    scrim = Color(0xB3000000),
    overlayBg = Color(0xE60E0F13),

    bgBase = Color(0xFF0E0F13),
    // плоский фон: цветных пятен в матовой теме нет
    spotMoney = Color(0x00FFB02E),
    spotStudy = Color(0x004DA3FF),
    spotIncome = Color(0x008CD62B),

    outlines = false,
    vectorIcons = true,
    legacyColors = false,
    colorNumbers = false
)

/** Ключи тем: они же хранятся в сохранении. */
object ThemeIds {
    const val GLASS = "glass"
    const val MATTE = "matte"
}

/**
 * Тема как выбор игрока: ключ, название и пояснение.
 * Названия различают темы по смыслу, а не по номеру.
 */
enum class AppTheme(
    val id: String,
    val title: String,
    val note: String,
    val palette: Palette
) {
    GLASS(ThemeIds.GLASS, "Стекло", "Полупрозрачные слои, мягкие пятна на фоне, эмодзи", GlassPalette),
    MATTE(ThemeIds.MATTE, "Матовая", "Плотные слои без рамок, разведённые цвета, векторные иконки", MattePalette);

    companion object {
        /** Тема по ключу. Неизвестный ключ (в том числе из старого сохранения) — «Стекло». */
        fun byId(id: String): AppTheme = entries.firstOrNull { it.id == id } ?: GLASS
    }
}

/**
 * Действующая палитра. Значение по умолчанию — старая тема, поэтому любой кусок интерфейса,
 * снятый без обёртки (скриншот-тесты, превью), рисуется ровно как раньше.
 */
val LocalPalette = staticCompositionLocalOf { GlassPalette }
