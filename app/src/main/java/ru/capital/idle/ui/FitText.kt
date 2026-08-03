package ru.capital.idle.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Подобрать кегль так, чтобы текст влез в заданную ширину.
 *
 * Ширина измеряется настоящим замерщиком, а не прикидкой «символ ≈ 0.62 кегля»: именно такая
 * прикидка промахивалась на широких прописных кириллических буквах, из-за чего подписи
 * обрезались при увеличенном системном шрифте. Ширина строки линейна по кеглю
 * (letterSpacing тоже задан в sp), поэтому одного замера достаточно.
 *
 * @param style стиль с кеглем `maxSp` — им же текст и рисуется, если влезает
 * @param maxWidthPx доступная ширина в пикселях (из `BoxWithConstraints`)
 * @param maxSp кегль при обычном шрифте — больше него не увеличиваем никогда
 * @param minDp нижняя граница в *экранных* единицах: при системном шрифте 1.5 те же sp
 *   рисуются в полтора раза крупнее, и общий для всех масштабов порог в sp был бы слишком щедрым
 */
@Composable
internal fun fitFontSp(
    text: String, style: TextStyle, maxWidthPx: Int, maxSp: Float, minDp: Float
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val ref = measurer.measure(text, style).size.width
    if (ref <= 0 || maxWidthPx <= 0) return maxSp
    val minSp = minDp / density.fontScale
    return (maxSp * maxWidthPx / ref).coerceIn(minOf(minSp, maxSp), maxSp)
}
