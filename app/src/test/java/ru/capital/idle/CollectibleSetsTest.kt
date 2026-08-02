package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.core.game.Collectibles
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle

/**
 * Наборы коллекции: полнота, бонус, вклад в социальный статус.
 * Числа фиксируют текущий состав наборов как есть — это характеризующие тесты.
 */
class CollectibleSetsTest {

    /** Состояние с уже купленными предметами: цена покупки для наборов не важна. */
    private fun owning(vararg ids: String) =
        GameState(collectibles = ids.associateWith { 1.0 })

    private val gallery = Collectibles.setById("gallery")!!
    private val nature = Collectibles.setById("nature")!!
    private val ancient = Collectibles.setById("ancient")!!
    private val names = Collectibles.setById("names")!!

    // ===================== состав =====================

    @Test
    fun `четыре набора, все предметы существуют и каждый где-то участвует`() {
        assertEquals(4, Collectibles.sets.size)
        assertEquals(4, Collectibles.sets.map { it.id }.toSet().size)

        // ни одного битого идентификатора: itemsOf отбрасывает неизвестные,
        // поэтому размер после разбора обязан совпасть с объявленным
        Collectibles.sets.forEach { s ->
            assertEquals("${s.id}: в наборе есть несуществующий предмет",
                s.itemIds.size, Collectibles.itemsOf(s).size)
            assertTrue("${s.id}: набор должен быть непустым", s.itemIds.isNotEmpty())
            assertTrue("${s.id}: бонус должен быть положительным", s.bonus > 0)
        }

        // каждый предмет каталога входит хотя бы в один набор — мёртвых покупок нет
        Collectibles.all.forEach { c ->
            assertTrue("${c.id} не входит ни в один набор", Collectibles.setsWith(c.id).isNotEmpty())
        }

        assertEquals(listOf("litho", "canvas", "lost"), gallery.itemIds)
        assertEquals(3, Collectibles.sizeOf(gallery))
        assertEquals(5, Collectibles.sizeOf(names))

        assertNotNull(Collectibles.setById("ancient"))
        assertNull(Collectibles.setById("нет-такого"))
    }

    @Test
    fun `предмет может входить сразу в несколько наборов`() {
        // «Полотно импрессиониста» и «Утраченный шедевр» — это и живопись, и вещи с именем автора
        assertEquals(setOf("gallery", "names"), Collectibles.setsWith("canvas").map { it.id }.toSet())
        assertEquals(setOf("gallery", "names"), Collectibles.setsWith("lost").map { it.id }.toSet())
        // а метеорит — только в одном
        assertEquals(listOf("nature"), Collectibles.setsWith("meteor").map { it.id })
        assertTrue(Collectibles.setsWith("нет-такого").isEmpty())

        // одна покупка двигает прогресс обоих наборов сразу
        val st = owning("canvas")
        assertEquals(1, Collectibles.ownedInSet(st, gallery))
        assertEquals(1, Collectibles.ownedInSet(st, names))
    }

    // ===================== полнота =====================

    @Test
    fun `набор считается собранным только когда есть все его предметы`() {
        val empty = GameState()
        assertFalse(Collectibles.isComplete(empty, nature))
        assertEquals(0, Collectibles.ownedInSet(empty, nature))

        val two = owning("diamond", "rex")
        assertFalse("двух предметов из трёх мало", Collectibles.isComplete(two, nature))
        assertEquals(2, Collectibles.ownedInSet(two, nature))

        val full = owning("diamond", "rex", "meteor")
        assertTrue(Collectibles.isComplete(full, nature))
        assertEquals(3, Collectibles.ownedInSet(full, nature))

        // лишние предметы полноту не ломают
        val withExtra = owning("diamond", "rex", "meteor", "litho", "неизвестный")
        assertTrue(Collectibles.isComplete(withExtra, nature))
        assertEquals(3, Collectibles.ownedInSet(withExtra, nature))

        // продажа одного предмета разбирает набор обратно
        val sold = Collectibles.sell(full.copy(money = 0.0), "meteor")!!
        assertFalse(Collectibles.isComplete(sold, nature))
    }

    @Test
    fun `собранные наборы считаются по отдельности`() {
        val st = owning("diamond", "rex", "meteor", "litho", "canvas", "lost")
        assertEquals(2, Collectibles.completedSets(st))
        assertTrue(Collectibles.isComplete(st, nature))
        assertTrue(Collectibles.isComplete(st, gallery))
        assertFalse(Collectibles.isComplete(st, ancient))
        // «Имена в истории» пока держатся на двух предметах из пяти
        assertFalse(Collectibles.isComplete(st, names))
        assertEquals(2, Collectibles.ownedInSet(st, names))
    }

    // ===================== бонус =====================

    @Test
    fun `бонус начисляется целиком и только за полный набор`() {
        assertEquals(110, nature.bonus)
        assertEquals(140, gallery.bonus)
        assertEquals(170, ancient.bonus)
        assertEquals(180, names.bonus)

        val two = owning("diamond", "rex")
        assertEquals("за неполный набор бонуса нет", 0, Collectibles.setBonus(two, nature))
        assertEquals(0, Collectibles.setBonusPoints(two))

        val full = owning("diamond", "rex", "meteor")
        assertEquals(110, Collectibles.setBonus(full, nature))
        assertEquals(110, Collectibles.setBonusPoints(full))

        // два набора складываются
        val twoSets = owning("diamond", "rex", "meteor", "litho", "canvas", "lost")
        assertEquals(110 + 140, Collectibles.setBonusPoints(twoSets))
    }

    @Test
    fun `бонусы за наборы весомы, но не перевешивают статус самих предметов`() {
        val allItems = GameState(collectibles = Collectibles.all.associate { it.id to 1.0 })

        val itemsTotal = Collectibles.all.sumOf { it.status }
        val bonusTotal = Collectibles.sets.sumOf { it.bonus }
        assertEquals(816, itemsTotal)
        assertEquals(600, bonusTotal)
        // полный сбор даёт все четыре бонуса
        assertEquals(4, Collectibles.completedSets(allItems))
        assertEquals(bonusTotal, Collectibles.setBonusPoints(allItems))

        // наборы — надбавка сверху, а не более выгодная вторая валюта
        assertTrue("бонусы не должны перевешивать предметы", bonusTotal < itemsTotal)
        assertTrue("бонусы должны быть ощутимы", bonusTotal > itemsTotal / 2)
    }

    // ===================== вклад в статус =====================

    @Test
    fun `бонус за набор попадает в очки статуса коллекции и в социальный статус`() {
        val two = owning("diamond", "rex")
        // 38 + 70, набор ещё не собран
        assertEquals(108, Collectibles.itemStatusPoints(two))
        assertEquals(108, Collectibles.statusPoints(two))

        val full = owning("diamond", "rex", "meteor")
        assertEquals(38 + 70 + 95, Collectibles.itemStatusPoints(full))
        assertEquals(38 + 70 + 95 + 110, Collectibles.statusPoints(full))

        // и тот же бонус виден в общем социальном статусе
        val base = Lifestyle.socialStatus(GameState())
        assertEquals(base + 108, Lifestyle.socialStatus(two))
        assertEquals(base + 203 + 110, Lifestyle.socialStatus(full))
    }

    @Test
    fun `последний предмет набора добавляет и свой статус, и бонус разом`() {
        val before = owning("litho", "canvas")
        val after = owning("litho", "canvas", "lost")

        val lost = Collectibles.byId("lost")!!
        assertEquals(160, lost.status)
        // 160 за сам шедевр плюс 140 за закрытую «Галерею»
        assertEquals(
            Collectibles.statusPoints(before) + 160 + 140,
            Collectibles.statusPoints(after)
        )
        assertEquals(
            Lifestyle.socialStatus(before) + 160 + 140,
            Lifestyle.socialStatus(after)
        )
    }

    @Test
    fun `предмет из двух наборов закрывает оба и приносит оба бонуса`() {
        // всё, кроме «Полотна импрессиониста»: обе цели ждут одной покупки
        val before = owning("litho", "lost", "violin", "folio", "crown")
        assertEquals(0, Collectibles.completedSets(before))
        assertEquals(0, Collectibles.setBonusPoints(before))

        val after = before.copy(collectibles = before.collectibles + ("canvas" to 1.0))
        assertEquals(2, Collectibles.completedSets(after))
        assertEquals(140 + 180, Collectibles.setBonusPoints(after))
        // одна покупка: 52 очка за полотно и сразу два бонуса
        assertEquals(
            Collectibles.statusPoints(before) + 52 + 140 + 180,
            Collectibles.statusPoints(after)
        )
    }

    @Test
    fun `неизвестные предметы в сейве наборы не собирают`() {
        val st = GameState(collectibles = mapOf("diamond" to 1.0, "rex" to 1.0, "мусор" to 1.0))
        assertFalse(Collectibles.isComplete(st, nature))
        assertEquals(0, Collectibles.setBonusPoints(st))
        assertEquals(38 + 70, Collectibles.statusPoints(st))
    }
}
