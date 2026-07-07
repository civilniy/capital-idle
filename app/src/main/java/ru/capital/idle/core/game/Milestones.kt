package ru.capital.idle.core.game

/** Вехи богатства с наградой в слитках. Монотонны по порогу. */
object Milestones {
    data class Milestone(val name: String, val thresholdUsd: Double, val rewardBullion: Long)

    val all = listOf(
        Milestone("Первый миллион", 1e6, 5),
        Milestone("Первый миллиард", 1e9, 15),
        Milestone("Топ-1000 мира", 4.2e9, 25),
        Milestone("Богаче №1", GameConfig.TOP1_USD, 60),
        Milestone("Триллионер", 1e12, 120),
        Milestone("ВВП Германии", 4.5e12, 300),
        Milestone("ВВП США", 29e12, 800),
        Milestone("ВВП всего мира", 110e12, 2000),
    )

    fun ratioOverTop1(moneyUsd: Double): Double = moneyUsd / GameConfig.TOP1_USD
}
