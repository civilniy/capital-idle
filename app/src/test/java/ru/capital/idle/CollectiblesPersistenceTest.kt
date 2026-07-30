package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.GameState
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Сохранение и загрузка коллекции: GameState -> GameEntity -> GameState.
 * Формат хранения — строка "id:цена,id:цена" по соглашению проекта.
 */
class CollectiblesPersistenceTest {

    @Test
    fun `пустая коллекция сохраняется пустой строкой`() {
        val e = GameState().toEntity()
        assertEquals("", e.collectiblesRaw)
        assertEquals(emptyMap<String, Double>(), e.toState().collectibles)
    }

    @Test
    fun `коллекция переживает круг записи-чтения вместе с ценами покупки`() {
        val src = GameState(collectibles = mapOf("litho" to 62_500.0, "crown" to 4_470_000_000.0))
        val e = src.toEntity()

        // формат: id:цена через запятую, порядок стабильный (по id)
        assertEquals("crown:4.47E9,litho:62500.0", e.collectiblesRaw)

        val back = e.toState()
        assertEquals(src.collectibles, back.collectibles)
        assertEquals(62_500.0, Collectibles.paidFor(back, "litho"), EPS)
        assertEquals(4_470_000_000.0, Collectibles.paidFor(back, "crown"), EPS)
        assertEquals(2, Collectibles.ownedCount(back))
    }

    @Test
    fun `дробная цена покупки не теряет точности`() {
        val paid = 62_512.345678
        val back = GameState(collectibles = mapOf("litho" to paid)).toEntity().toState()
        assertEquals(paid, Collectibles.paidFor(back, "litho"), 1e-9)
    }

    @Test
    fun `сейв без коллекции читается как пустая коллекция`() {
        // поле по умолчанию пустое — как у сохранения, сделанного до появления системы
        val e = GameState().toEntity().copy(collectiblesRaw = "")
        assertTrue(e.toState().collectibles.isEmpty())
    }

    @Test
    fun `мусор и исчезнувшие предметы отбрасываются при загрузке`() {
        val e = GameState().toEntity().copy(
            collectiblesRaw = "litho:62500.0,сломано,temple:абв,исчезнувший:100.0,crown:1.0"
        )
        val back = e.toState()
        // остаются только записи с существующим id и разбираемой ценой
        assertEquals(mapOf("litho" to 62_500.0, "crown" to 1.0), back.collectibles)
    }

    @Test
    fun `купленный предмет доживает до следующего запуска`() {
        val bought = Collectibles.buy(GameState(money = 100_000.0, gameHours = 100 * 24.0), "litho")!!
        val reloaded = bought.toEntity().toState()

        assertTrue(Collectibles.owns(reloaded, "litho"))
        assertEquals(62_500.0, Collectibles.paidFor(reloaded, "litho"), EPS)
        assertEquals(bought.money, reloaded.money, EPS)
        // прибыль после перезапуска считается от той же уплаченной цены
        val later = reloaded.copy(gameHours = 400 * 24.0)
        assertEquals(37_500.0, Collectibles.totalProfit(later), EPS)
    }
}
