package ru.capital.idle.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Слои интерфейса.
 *
 * В теме «Стекло» это тёмные полупрозрачные заливки поверх фона с мягкими цветными пятнами.
 * В «Матовой» те же слоты — плотные цвета трёх ступеней (фон → карточка → вложенный элемент),
 * и разделяет слои именно разница фона, а не обводка. Значения обеих тем лежат в `Palette.kt`.
 */

// заливки слоёв
val GlassFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.cardFill
val GlassInner: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.innerFill
val GlassAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentFill
val GlassBtn: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.btnFill
val GlassBtnOff: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.btnOffFill
val GlassBtnOffText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.btnOffText
val GlassSell: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sellFill
val GlassSellText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sellText

// тонированные подложки: акцент на 11–13% вместо серого
val MoneyFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.moneyFill
val IncomeFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.incomeFill
val BusinessFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.businessFill
val StudyFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.studyFill
val ExpenseFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.expenseFill
val AccentStrong: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentStrong
val EventGoodFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.eventGoodFill
val EventBadFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.eventBadFill

// полосы, переключатели, разделители и затемнения
val TrackFill: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.trackFill
val ToggleOff: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.toggleOff
val DividerLine: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.divider
val Scrim: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.scrim
val OverlayBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.overlayBg
val BgBase: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.bgBase

/** Рисуются ли обводки в текущей теме. В матовой слои разделяет разница фона. */
val Outlined: Boolean @Composable @ReadOnlyComposable get() = LocalPalette.current.outlines

/**
 * Обводка, которую рисует только тема со стеклом.
 *
 * В матовой теме рамок у карточек, плиток и кнопок нет: их роль берёт на себя разница
 * фона между тремя ступенями. Волосяной разделитель внутри списков — не эта обводка,
 * он рисуется отдельной полоской цвета [DividerLine].
 */
@Composable
fun Modifier.glassOutline(color: Color, shape: Shape, width: Dp = 1.dp): Modifier =
    if (Outlined) this.border(width, color, shape) else this

/** Фон экрана. В «Стекле» — мягкие цветные пятна, в «Матовой» пятна прозрачны и фон плоский. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(p.bgBase)) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(p.spotMoney, p.spotMoney.copy(alpha = 0f)),
                center = Offset(140f, 200f), radius = 700f
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(p.spotStudy, p.spotStudy.copy(alpha = 0f)),
                center = Offset(950f, 700f), radius = 800f
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(p.spotIncome, p.spotIncome.copy(alpha = 0f)),
                center = Offset(300f, 1700f), radius = 850f
            )
        ))
        content()
    }
}

/**
 * Переключатель раздела: активная вкладка светлее, текст золотой. Общий для всех групп.
 *
 * Размер шрифта, число строк и боковой отступ можно поджать — это нужно ряду из пяти вкладок
 * в профиле, где подпись «Имущество» иначе не помещается. Значения по умолчанию прежние,
 * поэтому ряды из двух-трёх вкладок выглядят ровно как раньше.
 */
@Composable
fun GlassTab(
    label: String,
    on: Boolean,
    locked: Boolean,
    modifier: Modifier,
    fontSize: TextUnit = 11.5.sp,
    maxLines: Int = 2,
    horizontalPadding: Dp = 4.dp,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) legacy(GlassBtn, MoneyFill) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (if (locked) "🔒 " else "") + label,
            color = if (locked) Mute.copy(alpha = 0.6f) else if (on) Gold else Mute,
            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = fontSize, lineHeight = (fontSize.value + 1.5f).sp, maxLines = maxLines,
            textAlign = TextAlign.Center
        )
    }
}
