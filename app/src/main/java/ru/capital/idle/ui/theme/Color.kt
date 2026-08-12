package ru.capital.idle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Цвета интерфейса. Раньше это были глобальные константы; теперь — читалки из действующей
 * палитры ([LocalPalette]), поэтому весь интерфейс переключается вместе с темой.
 *
 * Имена оставлены прежними: так правка не расползлась по всем экранам, а старая тема
 * осталась прежней до пикселя — её значения лежат в [GlassPalette].
 *
 * Читать их можно только внутри `@Composable`. Если цвет нужен в обычной функции или
 * в лямбде рисования (`Canvas`, `drawBehind`), возьмите его в composable-коде и передайте
 * параметром — так уже сделано с узором карты и графиком котировок.
 */

val Gold: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.money
val GoldDim: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.moneyDim
val Bg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bg
val Panel: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.panel
val Panel2: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.panel2
val LineColor: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line
val TextMain: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textMain
val Mute: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.mute
val CoinText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onMoney

/** Доход и работа по найму. */
val GreenAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.income
/** Расход, убыток, риск. */
val RedAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.expense

/** Заголовок раздела и капитал. */
val Heading: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.heading
/** Бизнес, производство, рост. */
val Business: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.business
/** Сон, отдых, статус. */
val Rest: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.rest
/** Учёба и информация. */
val Study: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.study

/** Предупреждение, средний уровень. */
val Warn: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.warn
/** Лучший вариант и своё дело. */
val Best: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.best

/**
 * Цвета банковской карты.
 *
 * Карта выглядит одинаково в обеих темах, поэтому её цвета заданы числами и не зависят
 * от палитры: тема меняет всё вокруг карты, но не саму карту.
 */
object CardColors {
    val gold = Color(0xFFE8B54A)
    val income = Color(0xFF5FBF7A)
    val expense = Color(0xFFD9694F)
    val text = Color(0xFFE9E6DF)
    val mute = Color(0xFF8B8C93)
    val onGold = Color(0xFF2A2410)
    /** Логотип платёжной системы. */
    val red = Color(0xffEB001B)
}

/** Логотип платёжной системы на карте — часть карты, а не темы. */
val Red = CardColors.red

/**
 * Цвет крупной денежной суммы.
 *
 * В старой теме сумма красится тем же акцентом, что и раньше. В новой цвет живёт в фигурах,
 * а крупные числа набираются основным цветом текста — смысл несёт подпись рядом и подложка.
 * Капитал и заголовки разделов из этого правила выведены: у них свои слоты.
 */
@Composable
@ReadOnlyComposable
fun bigNumber(accent: Color): Color =
    if (LocalPalette.current.colorNumbers) accent else LocalPalette.current.textMain
