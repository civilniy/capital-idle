package ru.capital.idle.core.game

/**
 * Тип фонового узора карты. Отрисовывается на Canvas в UI.
 */
enum class CardPattern { NONE, DIAGONAL, GUILLOCHE, ARCS, DOTS, HONEYCOMB }

/**
 * Тир банковской карты. Определяется по полному капиталу (netWorth).
 * Цвета заданы как ARGB-Long, чтобы не тянуть Compose в core.game.
 */
enum class CardTier(
    val title: String,
    val thresholdUsd: Double,
    val gradient: List<Long>,
    val accent: Long,
    val textColor: Long,
    val pattern: CardPattern,
    val patternColor: Long,
    val kant: Long,        // 0 = без канта
    val glow: Long,        // 0 = без свечения
    val passiveBonus: Double  // прибавка к доходности пассива (депозит/облигации/недвижимость)
) {
    CLASSIC(
        "CLASSIC", 0.0,
        listOf(0xFF2A2D36, 0xFF1D1F25, 0xFF131419),
        accent = 0xFF8B8C93, textColor = 0xFFE9E6DF,
        pattern = CardPattern.DIAGONAL, patternColor = 0x05FFFFFF,
        kant = 0x00000000, glow = 0x00000000, passiveBonus = 0.0
    ),
    SILVER(
        "SILVER", 50_000.0,
        listOf(0xFF3A3D44, 0xFF2A2C33, 0xFF1E2025),
        accent = 0xFFB8BDC4, textColor = 0xFFEDEFF2,
        pattern = CardPattern.DIAGONAL, patternColor = 0x0CFFFFFF,
        kant = 0x14FFFFFF, glow = 0x00000000, passiveBonus = 0.05
    ),
    GOLD(
        "GOLD", 1_000_000.0,
        listOf(0xFF4A3A1C, 0xFF322611, 0xFF1F1808),
        accent = 0xFFE8B54A, textColor = 0xFFF5E3B8,
        pattern = CardPattern.GUILLOCHE, patternColor = 0x1AE8B54A,
        kant = 0x33E8B54A, glow = 0x00000000, passiveBonus = 0.10
    ),
    PLATINUM(
        "PLATINUM", 50_000_000.0,
        listOf(0xFF2E3A3D, 0xFF222B2E, 0xFF181E20),
        accent = 0xFF9CC4CC, textColor = 0xFFE0EEF0,
        pattern = CardPattern.ARCS, patternColor = 0x12C8E4E8,
        kant = 0x269CC4CC, glow = 0x00000000, passiveBonus = 0.20
    ),
    BLACK(
        "BLACK", 1_000_000_000.0,
        listOf(0xFF1A1A1C, 0xFF0E0E10, 0xFF060607),
        accent = 0xFFC8A24A, textColor = 0xFFEDE8DC,
        pattern = CardPattern.DOTS, patternColor = 0x17E8B54A,
        kant = 0x59E8B54A, glow = 0x00000000, passiveBonus = 0.35
    ),
    AURUM(
        "AURUM INFINITE", 100_000_000_000.0,
        listOf(0xFF3D2E14, 0xFF1A130A, 0xFF000000),
        accent = 0xFFFFD24A, textColor = 0xFFFFE9B0,
        pattern = CardPattern.HONEYCOMB, patternColor = 0x09FFD24A,
        kant = 0x80FFD24A, glow = 0x2EFFC83C, passiveBonus = 0.50
    );

    companion object {
        /** Тир по полному капиталу: самый высокий, чей порог достигнут. */
        fun forWorth(worthUsd: Double): CardTier =
            entries.lastOrNull { worthUsd >= it.thresholdUsd } ?: CLASSIC
    }
}
