package ru.capital.idle

import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries

/** Общие помощники для тестов игровой логики. */
object GameTestFixtures {

    /** Точность сравнения денежных величин. */
    const val EPS = 1e-9

    /** Состояние с предприятиями в одной отрасли (остальные отрасли пустые). */
    fun withEnterprises(
        state: GameState,
        indIndex: Int,
        vararg enterprises: Enterprise
    ): GameState {
        val lists = MutableList(Industries.count) { emptyList<Enterprise>() }
        lists[indIndex] = enterprises.toList()
        return state.copy(enterprises = lists)
    }
}
