package ru.capital.idle.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Геометрия оформления: скругления, отступы, толщина полос и размеры иконок.
 *
 * Тема — это не только цвета. Ощущение современного интерфейса дают скругления, воздух
 * и группировка, поэтому у новой темы свои размеры. Старая тема их не меняет: каждая
 * читалка принимает ПРЕЖНЕЕ значение места и отдаёт его, пока действует старая тема.
 *
 * Отсюда вид вызовов: `cardShape(18.dp)` — «здесь было 18, в новой теме будет по макету».
 * Так правка не требует таблицы соответствий и не может незаметно изменить старую тему.
 */
object Modern {
    /** Карточки и группы строк. */
    val cardRadius = 22.dp
    /** Плитки. */
    val tileRadius = 18.dp
    /** Баннеры. */
    val bannerRadius = 20.dp
    /** Кнопки. */
    val buttonRadius = 13.dp
    /** Ряд подразделов. */
    val subtabRadius = 16.dp
    /** Выделенная строка списка. */
    val selectedRadius = 14.dp
    /** Мелкие плашки внутри строк (бейджи, шаги). */
    val chipRadius = 9.dp

    /** Горизонтальный отступ внутри карточки. */
    val cardPadH = 16.dp
    /** Вертикальный отступ внутри карточки. */
    val cardPadV = 14.dp
    /** Зазор между карточками. */
    val cardGap = 12.dp
    /** Отступ строки группы сверху и снизу от разделителя. */
    val rowPadV = 13.dp

    /** Полосы прогресса. */
    val barHeight = 7.dp
    val barRadius = 4.dp

    /** Иконка в строке: круг и символ. */
    val iconCircle = 40.dp
    /** Иконка в плитке: круг поменьше. */
    val tileIconCircle = 30.dp
}

/** Действует ли новое оформление (геометрия по макету). */
val modernLook: Boolean
    @Composable @ReadOnlyComposable get() = !LocalPalette.current.legacyColors

/** Размер: прежний в старой теме, по макету в новой. */
@Composable
@ReadOnlyComposable
fun dpOf(previous: Dp, modern: Dp): Dp = if (modernLook) modern else previous

/** Скругление карточки или группы строк. */
@Composable
@ReadOnlyComposable
fun cardShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.cardRadius))

/** Скругление плитки. */
@Composable
@ReadOnlyComposable
fun tileShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.tileRadius))

/** Скругление баннера. */
@Composable
@ReadOnlyComposable
fun bannerShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.bannerRadius))

/** Скругление выделенной строки списка. */
@Composable
@ReadOnlyComposable
fun selectedShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.selectedRadius))

/** Скругление кнопки. */
@Composable
@ReadOnlyComposable
fun btnShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.buttonRadius))

/** Скругление мелкой плашки внутри строки. */
@Composable
@ReadOnlyComposable
fun chipShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.chipRadius))

/** Скругление полосы прогресса. */
@Composable
@ReadOnlyComposable
fun barShape(previous: Dp) = RoundedCornerShape(dpOf(previous, Modern.barRadius))

/**
 * Заливка строки внутри группы.
 *
 * В старой теме у каждой строки своя плашка. В новой строки живут в одной карточке
 * и собственного фона не имеют — фон даёт карточка группы, а строки делит волосяная линия.
 * Акцентные строки (купленное, текущее) свой цвет сохраняют в обеих темах.
 */
@Composable
@ReadOnlyComposable
fun rowFill(previous: Color): Color = if (modernLook) Color.Transparent else previous

/**
 * Группа однородных строк.
 *
 * В новой теме — одна карточка с волосяными разделителями между строками; заголовок группы
 * кладётся внутрь неё первым элементом. В старой — просто колонка, то есть ровно то, что
 * было: заголовок и стопка отдельных плашек, ничего не добавляется и не отнимается.
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (modernLook) {
        Column(
            modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Modern.cardRadius))
                .background(LocalPalette.current.cardFill)
                .padding(vertical = 4.dp),
            content = content
        )
    } else {
        Column(modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Что стоит между строками группы: волосяная линия в новой теме, прежний зазор в старой.
 *
 * @param previousGap зазор, который стоял здесь раньше — он же остаётся в старой теме
 * @param last последняя строка: после неё разделитель не нужен (зазор в старой теме — нужен,
 *   он там стоял и отделял список от следующего блока)
 */
@Composable
fun RowSeparator(previousGap: Dp, last: Boolean = false) {
    if (!modernLook) {
        Spacer(Modifier.height(previousGap))
    } else if (!last) {
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = Modern.cardPadH)
                .height(1.dp)
                .background(LocalPalette.current.divider)
        )
    }
}
