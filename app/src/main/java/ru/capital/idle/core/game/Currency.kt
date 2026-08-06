package ru.capital.idle.core.game

/**
 * Валюты отображения. Капитал хранится в долларах (база), курс пересчитывает на лету.
 *
 * **Курсы — игровая условность, а не рыночные котировки, и обновлять их по бирже не нужно.**
 * У рубля стоит ровно 100 именно поэтому: при рыночных 73,7 круглые долларовые величины
 * превращались в некрасивые рублёвые — веха «Первый миллион» показывалась как 73 700 000 ₽.
 * При сотне круглое остаётся круглым в обеих валютах, а это игра про деньги: числа в ней
 * читают чаще, чем считают.
 *
 * На экономику курс не влияет вообще: цены, доходы и все балансовые расчёты ведутся
 * в долларах, а курс применяется только в `GameMath.formatMoney` / `formatAmount`
 * в самый последний момент, при выводе на экран.
 */
enum class Currency(val code: String, val symbol: String, val ratePerUsd: Double) {
    USD("USD", "$", 1.0),
    RUB("RUB", "\u20BD", 100.0),
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
