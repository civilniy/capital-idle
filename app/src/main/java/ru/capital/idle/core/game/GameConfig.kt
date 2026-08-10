package ru.capital.idle.core.game

object GameConfig {
    // Ориентир №1 мирового рейтинга (Forbes Real-Time, июнь 2026).
    const val TOP1_USD = 788_800_000_000.0
    const val TOP1_NAME = "Илон Маск"
    const val TRILLIONAIRE_USD = 1_000_000_000_000.0

    // Оффлайн-«сейф»: две фазы по реальному времени.
    //   первые FULL_SEC — эффективность EFF_FULL; следующие HALF_SEC — EFF_HALF; дальше ноль.
    const val OFFLINE_EFF_FULL = 0.25            // первые 30 мин: 25% дохода
    const val OFFLINE_EFF_HALF = 0.10            // следующие 30 мин: 10% дохода
    const val OFFLINE_FULL_SEC = 30.0 * 60.0
    const val OFFLINE_HALF_SEC = 30.0 * 60.0
    const val OFFLINE_SAFE_BONUS_SEC = 15.0 * 60.0 // +15 мин к каждой фазе за уровень престиж-апгрейда

    // Долг растёт на 2% в игровой день, пока вы живёте не по средствам.
    const val DEBT_RATE_PER_DAY = 0.02

    // Отдача тапа падает с ростом дохода. До порога тап работает в полную силу,
    // выше — каждое удесятерение дохода режет отдачу вдвое (см. GameMath.tapEfficiency).
    const val TAP_FULL_INCOME_PER_DAY = 10_000.0
    const val TAP_DECAY_PER_DECADE = 2.0
}
