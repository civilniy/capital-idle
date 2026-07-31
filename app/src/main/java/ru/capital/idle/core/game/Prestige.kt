package ru.capital.idle.core.game

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** Перманентные престиж-апгрейды. */
enum class PrestigeUpgrade(val title: String, val baseCost: Long, val growth: Double) {
    INCOME("Множитель дохода бизнесов", 8, 1.55),
    NEGOTIATOR("Переговорщик", 5, 1.55),
    START("Стартовый капитал", 12, 1.9),
    STUDY("Быстрая учёба", 10, 1.8),
    SAFE("Вместительный сейф", 6, 1.7);

    fun costAt(level: Int): Long = floor(baseCost * growth.pow(level)).toLong()
}

object Prestige {
    fun levelOf(state: GameState, u: PrestigeUpgrade): Int = when (u) {
        PrestigeUpgrade.INCOME -> state.pIncome
        PrestigeUpgrade.NEGOTIATOR -> state.pNegotiator
        PrestigeUpgrade.START -> state.pStart
        PrestigeUpgrade.STUDY -> state.pStudy
        PrestigeUpgrade.SAFE -> state.pSafe
    }

    fun incomeMult(state: GameState): Double = 1.0 + 0.40 * state.pIncome
    fun negotiatorMult(state: GameState): Double = 1.0 + 0.45 * state.pNegotiator
    fun startMoney(pStart: Int): Double = if (pStart <= 0) 0.0 else 1000.0 * 8.0.pow(pStart - 1)
    fun studyMult(state: GameState): Double = 1.0 + 0.25 * state.pStudy

    /** Слитки за перерождение от заработанного за заход. */
    fun gainFrom(totalEarned: Double): Long =
        floor(25.0 * sqrt((totalEarned / 1e8).coerceAtLeast(0.0))).toLong()

    /** Суммарная длительность сейфа (обе фазы) в минутах. */
    fun safeTotalMinutes(state: GameState): Int {
        val bonus = state.pSafe * GameConfig.OFFLINE_SAFE_BONUS_SEC * 2  // бонус к обеим фазам
        return ((GameConfig.OFFLINE_FULL_SEC + GameConfig.OFFLINE_HALF_SEC + bonus) / 60.0).toInt()
    }
    /** Длительность полной фазы в минутах (для текста). */
    fun safeFullMinutes(state: GameState): Int =
        ((GameConfig.OFFLINE_FULL_SEC + state.pSafe * GameConfig.OFFLINE_SAFE_BONUS_SEC) / 60.0).toInt()

    fun effectText(state: GameState, u: PrestigeUpgrade): String = when (u) {
        PrestigeUpgrade.INCOME -> "+${state.pIncome * 40}% к бизнесам"
        PrestigeUpgrade.NEGOTIATOR -> "+${state.pNegotiator * 45}% к зарплате и тапу"
        PrestigeUpgrade.START -> "старт ${GameMath.format(startMoney(state.pStart))} $"
        PrestigeUpgrade.STUDY -> "учёба ×${GameMath.decimal(studyMult(state), 2)}"
        PrestigeUpgrade.SAFE -> "офлайн-доход: всего ${safeTotalMinutes(state)} мин (${safeFullMinutes(state)} мин выше)"
    }
}
