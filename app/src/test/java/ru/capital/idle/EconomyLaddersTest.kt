package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.GameTestFixtures.withEnterprises
import ru.capital.idle.core.game.BusinessConfig
import ru.capital.idle.core.game.Education
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Jobs
import ru.capital.idle.core.game.MarketPhase
import ru.capital.idle.core.game.Pressure

/**
 * Лестницы отраслей, стоимость открытия и улучшения предприятий, ворота доступа.
 */
class EconomyLaddersTest {

    private val tradeIdx = 0
    private val foodIdx = 1
    private val servIdx = 2

    @Test
    fun `лестницы отраслей монотонны по цене и доходу`() {
        assertEquals(6, Industries.count)
        Industries.all.forEach { ind ->
            assertTrue("${ind.id}: пустая лестница", ind.levels.isNotEmpty())
            ind.levels.zipWithNext().forEach { (a, b) ->
                assertTrue("${ind.id}: цена ${b.name} не выше ${a.name}", b.cost > a.cost)
                assertTrue("${ind.id}: доход ${b.name} не выше ${a.name}", b.incomePerHour > a.incomePerHour)
            }
        }
    }

    @Test
    fun `отрасли идут от дешёвой к дорогой и требуют всё более высокий статус`() {
        val firstCosts = Industries.all.map { it.levels[0].cost }
        firstCosts.zipWithNext().forEach { (a, b) ->
            assertTrue("вход в отрасли должен дорожать: $a -> $b", b > a)
        }
        assertEquals(0, Industries.statusGateFor("trade"))
        assertEquals(0, Industries.statusGateFor("food"))
        assertEquals(8, Industries.statusGateFor("serv"))
        assertEquals(25, Industries.statusGateFor("prod"))
        assertEquals(55, Industries.statusGateFor("log"))
        assertEquals(90, Industries.statusGateFor("it"))
        // неизвестная отрасль порогом не блокируется
        assertEquals(0, Industries.statusGateFor("unknown"))
    }

    @Test
    fun `цена открытия растёт в 1,6 раза за каждое предприятие в отрасли`() {
        assertEquals(1.6, BusinessConfig.OPEN_PRICE_GROWTH, EPS)
        val s = GameState()
        val e = Enterprise()

        // Лоток стоит 200 $
        assertEquals(200.0, GameMath.openEnterpriseCost(s, tradeIdx), EPS)
        assertEquals(320.0, GameMath.openEnterpriseCost(withEnterprises(s, tradeIdx, e), tradeIdx), 1e-6)
        assertEquals(512.0, GameMath.openEnterpriseCost(withEnterprises(s, tradeIdx, e, e), tradeIdx), 1e-6)
        assertEquals(819.2, GameMath.openEnterpriseCost(withEnterprises(s, tradeIdx, e, e, e), tradeIdx), 1e-6)
    }

    @Test
    fun `образование и фаза рынка удешевляют открытие`() {
        val s = GameState()
        // курсы продаж -5%
        assertEquals(190.0, GameMath.openEnterpriseCost(s.copy(eduDone = setOf("sales")), tradeIdx), EPS)
        // менеджмент -10%
        assertEquals(180.0, GameMath.openEnterpriseCost(s.copy(eduDone = setOf("mgmt")), tradeIdx), EPS)
        // вместе скидки перемножаются: 0.95 × 0.90
        assertEquals(171.0, GameMath.openEnterpriseCost(s.copy(eduDone = setOf("sales", "mgmt")), tradeIdx), EPS)
        // кризис распродаёт бизнесы за 60% цены, бум наоборот дорожает
        assertEquals(120.0,
            GameMath.openEnterpriseCost(s.copy(phaseIndex = MarketPhase.CRISIS.ordinal), tradeIdx), EPS)
        assertEquals(250.0,
            GameMath.openEnterpriseCost(s.copy(phaseIndex = MarketPhase.BOOM.ordinal), tradeIdx), EPS)
    }

    @Test
    fun `цена улучшения — это цена следующей ступени, на вершине улучшать нечего`() {
        val trade = Industries.all[tradeIdx]
        val s = withEnterprises(GameState(), tradeIdx, Enterprise(level = 0))
        assertEquals(1_300.0, GameMath.upgradeEnterpriseCost(s, tradeIdx, 0), EPS)
        assertEquals(trade.levels[1].cost, GameMath.upgradeEnterpriseCost(s, tradeIdx, 0), EPS)

        // цена улучшения от числа предприятий в отрасли НЕ зависит (в отличие от открытия)
        val many = withEnterprises(GameState(), tradeIdx,
            Enterprise(level = 0), Enterprise(level = 0), Enterprise(level = 0))
        assertEquals(1_300.0, GameMath.upgradeEnterpriseCost(many, tradeIdx, 2), EPS)

        // последняя ступень лестницы
        val top = withEnterprises(GameState(), tradeIdx, Enterprise(level = trade.levels.lastIndex))
        assertEquals(Double.MAX_VALUE, GameMath.upgradeEnterpriseCost(top, tradeIdx, 0), EPS)
        // несуществующее предприятие
        assertEquals(Double.MAX_VALUE, GameMath.upgradeEnterpriseCost(s, tradeIdx, 5), EPS)
    }

    @Test
    fun `ворота открытия отрасли учитывают лимит, образование и статус`() {
        val s = GameState()

        // Торговля доступна с нуля
        assertTrue(GameMath.canOpenEnterprise(s, tradeIdx))

        // Общепит требует среднее образование
        val foodGate = GameMath.openGate(s, foodIdx)
        assertFalse(foodGate.eduOk)
        assertEquals("school", foodGate.needEdu)
        assertTrue(foodGate.statusOk)
        assertFalse(GameMath.canOpenEnterprise(s, foodIdx))
        assertTrue(GameMath.canOpenEnterprise(s.copy(eduDone = setOf("school")), foodIdx))

        // Услуги дополнительно требуют 8 очков соц-статуса
        val educated = s.copy(eduDone = setOf("school"))
        val servGate = GameMath.openGate(educated, servIdx)
        assertTrue(servGate.eduOk)
        assertFalse(servGate.statusOk)
        assertEquals(8, servGate.needStatus)
        assertEquals(0, servGate.haveStatus)
        // часы Tissot дают ровно 8 статуса — порог берётся впритык
        val styled = educated.copy(ownedTechs = Lifestyle.ladderSet(1))
        assertTrue(GameMath.openGate(styled, servIdx).ok)
        assertEquals(8, GameMath.openGate(styled, servIdx).haveStatus)
    }

    @Test
    fun `в отрасли нельзя держать больше десяти предприятий`() {
        assertEquals(10, BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY)
        val full = withEnterprises(GameState(), tradeIdx, *Array(10) { Enterprise() })
        val gate = GameMath.openGate(full, tradeIdx)
        assertFalse(gate.limitOk)
        assertFalse(gate.ok)
        assertFalse(GameMath.canOpenEnterprise(full, tradeIdx))

        val almost = withEnterprises(GameState(), tradeIdx, *Array(9) { Enterprise() })
        assertTrue(GameMath.openGate(almost, tradeIdx).limitOk)
    }

    @Test
    fun `самая дешёвая следующая трата ищется по всем отраслям`() {
        val s = GameState()
        // с нуля дешевле всего открыть Лоток за 200
        assertEquals(200.0, GameMath.cheapestNextBiz(s)!!, EPS)

        // если Лоток уже открыт, сравниваются подорожавшее открытие (320)
        // и улучшение до Точки на рынке (1300) — побеждает открытие
        val one = withEnterprises(s, tradeIdx, Enterprise(level = 0))
        assertEquals(320.0, GameMath.cheapestNextBiz(one)!!, 1e-6)
    }

    @Test
    fun `стена прогресса — когда следующий рывок дороже 80 дней чистого дохода`() {
        // без дохода стены нет (её просто не с чем сравнивать)
        assertFalse(GameMath.atProgressWall(GameState()))

        // курьер зарабатывает 8 $/день, дешёвый следующий шаг — Лоток за 200: 25 дней, не стена
        val courier = GameState(jobId = "courier")
        assertEquals(8.0, GameMath.incomePerDay(courier), EPS)
        assertFalse(GameMath.atProgressWall(courier))

        // если весь бизнес развит до предела, идти некуда — считается стеной
        val maxed = GameState(
            jobId = "courier",
            enterprises = Industries.all.map { ind ->
                List(BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY) { Enterprise(level = ind.levels.lastIndex) }
            }
        )
        assertTrue(GameMath.atProgressWall(maxed))
    }

    @Test
    fun `давление элит включается после миллиарда и гасится репутацией`() {
        // до миллиарда давления нет
        assertEquals(0.0, Pressure.value(0.0, 0.0), EPS)
        assertEquals(0.0, Pressure.value(999_999_999.0, 0.0), EPS)

        // ровно на миллиарде: log10(2) × 0.12 × 1.6
        val atBillion = Pressure.value(1e9, 0.0)
        assertEquals(0.12 * Math.log10(2.0) * 1.6, atBillion, EPS)
        assertEquals(0.0578, atBillion, 1e-4)

        // растёт с капиталом
        assertTrue(Pressure.value(1e12, 0.0) > Pressure.value(1e10, 0.0))
        // и не превышает 65%
        assertTrue(Pressure.value(1e30, 0.0) <= 0.65 + EPS)

        // репутация гасит давление максимум на 70%
        val raw = Pressure.value(1e10, 0.0)
        assertEquals(raw * 0.30, Pressure.value(1e10, 140.0), EPS)
        assertEquals(raw * 0.30, Pressure.value(1e10, 1_000.0), EPS)
        assertEquals(raw * 0.50, Pressure.value(1e10, 70.0), EPS)
    }

    @Test
    fun `работы и курсы находятся по id, требования по цепочке согласованы`() {
        assertEquals("Курьер", Jobs.byIdOrNull("courier")?.title)
        assertEquals(110.0, Jobs.byIdOrNull("director")!!.ratePerHour, EPS)
        // пустой id = безработный
        assertNull(Jobs.byIdOrNull(""))

        // у каждой вакансии, кроме первой, есть требование, и оно — существующий курс
        Jobs.all.forEach { job ->
            job.reqCourse?.let { assertTrue("нет курса ${it}", Education.byId(it) != null) }
        }
        // требования курсов тоже указывают на существующие курсы
        Education.allCourses.forEach { c ->
            c.reqCourse?.let { assertTrue("нет курса ${it}", Education.byId(it) != null) }
        }
        // требования ступеней отраслей — тоже реальные курсы
        Industries.all.forEach { ind ->
            ind.levels.forEach { lvl ->
                lvl.reqCourse?.let { assertTrue("${ind.id}/${lvl.name}: нет курса $it", Education.byId(it) != null) }
            }
        }
    }
}
