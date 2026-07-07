package ru.capital.idle.core.game

/**
 * Валюты отображения. Капитал хранится в долларах (база), курс пересчитывает на лету.
 * Курсы зашиты на июнь 2026 и легко обновляются.
 */
enum class Currency(val code: String, val symbol: String, val ratePerUsd: Double) {
    USD("USD", "$", 1.0),
    RUB("RUB", "\u20BD", 73.7),
    EUR("EUR", "\u20AC", 0.92),
    CNY("CNY", "\u00A5", 7.2);

    companion object {
        fun fromCode(code: String): Currency = entries.firstOrNull { it.code == code } ?: USD
        fun next(code: String): Currency {
            val cur = fromCode(code)
            return entries[(cur.ordinal + 1) % entries.size]
        }
    }
}
