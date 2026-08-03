package ru.capital.idle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Подобрать наибольший кегль, при котором текст влезает в заданную ширину одной строкой.
 *
 * Ширина берётся настоящим замером на каждом шаге, а не прикидкой «символ ≈ 0.62 кегля»
 * и не пересчётом от одного замера. Прикидка промахивается на широких прописных кириллице,
 * а линейный пересчёт — из-за того, что ширины глифов округляются до целых пикселей:
 * на девяти буквах набегает несколько процентов, и подпись всё равно обрезается.
 *
 * @param style стиль, которым текст рисуется на самом деле — включая начертание:
 *   ExtraBold заметно шире обычного, и замер обычным даёт слишком крупный кегль
 * @param maxWidthPx доступная ширина в пикселях (из `BoxWithConstraints`)
 * @param maxSp кегль при обычном шрифте — больше него не увеличиваем никогда
 * @param minDp нижняя граница в *экранных* единицах: при системном шрифте 1.5 те же sp
 *   рисуются в полтора раза крупнее, и общий для всех масштабов порог в sp был бы слишком щедрым
 */
@Composable
internal fun fitFontSp(
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxSp: Float,
    minDp: Float,
    stepSp: Float = 0.25f
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    if (maxWidthPx <= 0 || text.isEmpty()) return maxSp
    val minSp = minOf(minDp / density.fontScale, maxSp)
    var sp = maxSp
    while (sp > minSp) {
        if (measurer.measure(text, style.copy(fontSize = sp.sp)).size.width <= maxWidthPx) return sp
        sp -= stepSp
    }
    return minSp
}
