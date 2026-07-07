package ru.capital.idle.core.game

import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * Оценка места игрока в мировом рейтинге богатства по его капиталу (в долларах).
 * Стартуем с последнего места среди всего населения Земли и поднимаемся к №1.
 *
 * Опорные точки взяты из публичного распределения богатства (медиана, топ-10%,
 * число миллионеров и миллиардеров, порог топ-1000, состояние №1). Между точками
 * интерполируем в лог-лог масштабе, так как и капитал, и ранг охватывают много порядков.
 */
object RankModel {

    const val POPULATION = 8_300_000_000L
    const val TOP1_USD = GameConfig.TOP1_USD

    // (капитал в долларах, примерно столько людей богаче)
    private val anchors = listOf(
        1.0 to 8.3e9,
        1_000.0 to 5.0e9,
        10_000.0 to 3.5e9,        // около медианы мирового богатства
        100_000.0 to 1.0e9,       // верхние ~12%
        1_000_000.0 to 6.0e7,     // долларовые миллионеры (~60 млн)
        10_000_000.0 to 4.0e6,
        100_000_000.0 to 1.5e5,
        1_000_000_000.0 to 3_400.0, // миллиардеры
        4_200_000_000.0 to 1_000.0, // порог топ-1000
        TOP1_USD to 1.0             // №1
    )

    /** Примерное место игрока в мире при данном капитале. 1 = богатейший. */
    fun rankForWealth(usd: Double): Long {
        if (usd <= anchors.first().first) return POPULATION
        if (usd >= anchors.last().first) return 1L
        for (i in 0 until anchors.size - 1) {
            val (w1, r1) = anchors[i]
            val (w2, r2) = anchors[i + 1]
            if (usd in w1..w2) {
                val t = (ln(usd) - ln(w1)) / (ln(w2) - ln(w1))
                val r = exp(ln(r1) + t * (ln(r2) - ln(r1)))
                return r.roundToLong().coerceAtLeast(1L)
            }
        }
        return 1L
    }

    /** Обогнал ли игрок текущего №1. */
    fun hasSurpassedTop(usd: Double): Boolean = usd >= TOP1_USD
}