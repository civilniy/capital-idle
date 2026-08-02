package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.EnterpriseNames
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Банк названий предприятий: полнота, отсутствие дублей и то, что случайное
 * предложение берётся из банка нужной отрасли.
 *
 * Отдельно закреплено главное свойство при подмене банка: названия уже открытых
 * предприятий лежат в сохранении и от смены банка не меняются.
 */
class EnterpriseNamesTest {

    private val industryIds = Industries.all.map { it.id }

    @Test
    fun `банк заведён ровно на те отрасли, что есть в игре`() {
        assertEquals(listOf("trade", "food", "serv", "prod", "log", "it"), industryIds)
        industryIds.forEach { id ->
            assertTrue("для отрасли $id нет банка названий", EnterpriseNames.all(id).isNotEmpty())
        }
        // неизвестная отрасль — пустой список, а не падение
        assertTrue(EnterpriseNames.all("нет-такой").isEmpty())
    }

    @Test
    fun `в каждой отрасли ровно 50 названий`() {
        industryIds.forEach { id ->
            assertEquals("отрасль $id", 50, EnterpriseNames.all(id).size)
        }
        assertEquals(300, industryIds.sumOf { EnterpriseNames.all(it).size })
    }

    @Test
    fun `внутри отрасли нет повторов и пустых строк`() {
        industryIds.forEach { id ->
            val names = EnterpriseNames.all(id)
            val dupes = names.groupBy { it }.filterValues { it.size > 1 }.keys
            assertTrue("отрасль $id: повторы $dupes", dupes.isEmpty())
            names.forEach { n ->
                assertTrue("отрасль $id: пустое название", n.isNotBlank())
                assertEquals("отрасль $id: «$n» с лишними пробелами по краям", n.trim(), n)
            }
        }
    }

    @Test
    fun `в банке нет «е» там, где в русском языке «ё»`() {
        // в остальном интерфейсе «ё» используется, разнобой был бы заметен.
        // «За обе щеки» в список не входит: в этой идиоме форма винительного падежа —
        // «щеки́» с ударением на окончании, поэтому «ё» там и не должно быть
        val mustHaveYo = listOf(
            "Тяжелый", "довезем", "Довезем", "Везем", "прошел", "Платежный",
            "Зеленая", "Теплая", "Поварешка", "Уголек", "Все в наличии"
        )
        industryIds.flatMap { EnterpriseNames.all(it) }.forEach { name ->
            mustHaveYo.forEach { bad ->
                assertTrue("«$name» написано без ё: «$bad»", !name.contains(bad))
            }
        }
        // и сами варианты с ё в банке действительно есть
        val all = industryIds.flatMap { EnterpriseNames.all(it) }
        listOf("Тяжёлый цех", "Точно довезём", "Везём как можем", "Довезём сегодня",
            "Деплой прошёл", "Платёжный шлюз").forEach {
            assertTrue("в банке нет «$it»", it in all)
        }
    }

    @Test
    fun `random отдаёт название из банка своей отрасли`() {
        industryIds.forEach { id ->
            val bank = EnterpriseNames.all(id).toSet()
            // прогон достаточно длинный, чтобы поймать выход за границы банка
            repeat(500) {
                val name = EnterpriseNames.random(id)
                assertTrue("отрасль $id: «$name» не из банка", name in bank)
            }
        }
    }

    @Test
    fun `random по неизвестной отрасли не падает и берёт какое-то название из банка`() {
        val everything = industryIds.flatMap { EnterpriseNames.all(it) }.toSet()
        repeat(100) {
            assertTrue(EnterpriseNames.random("нет-такой") in everything)
        }
    }

    @Test
    fun `random за много прогонов задевает весь банк отрасли`() {
        // если бы выбор упирался в часть списка (например, из-за округления индекса),
        // хвост банка никогда бы не выпал
        val id = "trade"
        val seen = HashSet<String>()
        repeat(20_000) { seen += EnterpriseNames.random(id) }
        assertEquals(EnterpriseNames.all(id).toSet(), seen)
    }

    // ===================== старые сохранения =====================

    @Test
    fun `названия открытых предприятий берутся из сохранения, а не из банка`() {
        // названия из прежнего банка, которых в новом уже нет
        val old = listOf("Гудвин", "Кофеварыч", "ЧистоМой", "СтанокЪ", "ВезуТочно", "КодоФф")
        old.forEach { n ->
            assertTrue("«$n» не должно быть в новом банке — иначе тест ничего не проверяет",
                industryIds.none { n in EnterpriseNames.all(it) })
        }

        val src = GameState(
            enterprises = List(Industries.count) { i ->
                listOf(Enterprise(level = 2, managerOrdinal = -1, name = old[i]))
            }
        )

        val back = src.toEntity().toState()
        old.forEachIndexed { i, n ->
            assertEquals("отрасль ${industryIds[i]}", n, back.enterprises[i].single().name)
        }
        // уровень и управляющий тоже на месте — сейв читается целиком, а не только имена
        assertEquals(src.enterprises, back.enterprises)
    }

    @Test
    fun `предприятие из совсем старого сейва без названия остаётся безымянным`() {
        // в старом формате имени не было: подставлять из нового банка при загрузке нельзя,
        // иначе предприятие «переименуется» само
        val ents = List(Industries.count) { i ->
            if (i == 0) listOf(Enterprise(level = 1, managerOrdinal = 0, name = "")) else emptyList()
        }
        val back = GameState(enterprises = ents).toEntity().toState()
        assertEquals("", back.enterprises[0].single().name)
        assertEquals(1, back.enterprises[0].single().level)
        assertEquals(0, back.enterprises[0].single().managerOrdinal)

        // и подлинный старый формат записи — «уровень:управляющий» вообще без поля имени
        val legacy = GameState().toEntity().copy(enterprisesRaw = "1:0|3:-1;;;;;").toState()
        assertEquals(listOf(Enterprise(1, 0, ""), Enterprise(3, -1, "")), legacy.enterprises[0])
    }
}
