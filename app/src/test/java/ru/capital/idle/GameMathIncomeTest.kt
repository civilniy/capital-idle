package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.GameTestFixtures.withEnterprises
import ru.capital.idle.core.game.BusinessConfig
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Investments
import ru.capital.idle.core.game.Manager
import ru.capital.idle.core.game.MarketPhase
import ru.capital.idle.core.game.Pressure

/**
 * Доход: зарплата, предприятия, множители бизнеса, пассив, итоговый чистый поток.
 * Числа взяты из текущего баланса и фиксируют его как есть.
 */
class GameMathIncomeTest {

    private val trade = Industries.all[0]

    // ===================== зарплата =====================

    @Test
    fun `без работы зарплаты нет`() {
        assertEquals(0.0, GameMath.salaryPerDay(GameState()), EPS)
        // несуществующий id работы тоже даёт ноль, а не падение
        assertEquals(0.0, GameMath.salaryPerDay(GameState(jobId = "no-such-job")), EPS)
    }

    @Test
    fun `зарплата курьера = ставка × часы × бодрость`() {
        val s = GameState(jobId = "courier")   // 0.8 $/ч, 10 рабочих часов, бодрость 1.0
        assertEquals(8.0, GameMath.salaryPerDay(s), EPS)

        // недосып 5ч режет бодрость до 0.75
        assertEquals(6.0, GameMath.salaryPerDay(s.copy(sleepH = 5)), EPS)
        // больше часов — больше денег
        assertEquals(12.0, GameMath.salaryPerDay(s.copy(workH = 15)), EPS)
    }

    @Test
    fun `престиж-переговорщик умножает зарплату на 1 плюс 0,45 за уровень`() {
        val s = GameState(jobId = "director")   // 110 $/ч × 10ч = 1100
        assertEquals(1_100.0, GameMath.salaryPerDay(s), EPS)
        assertEquals(1_595.0, GameMath.salaryPerDay(s.copy(pNegotiator = 1)), EPS)
        assertEquals(2_090.0, GameMath.salaryPerDay(s.copy(pNegotiator = 2)), EPS)
    }

    // ===================== часы управления =====================

    @Test
    fun `личные предприятия требуют по 3 часа, управляющие — нисколько`() {
        val base = GameState()
        val manual = Enterprise(level = 0, managerOrdinal = -1)
        val managed = Enterprise(level = 0, managerOrdinal = Manager.PRO.ordinal)

        assertEquals(0, GameMath.bizNeedHours(base))
        assertEquals(6, GameMath.bizNeedHours(withEnterprises(base, 0, manual, manual)))
        assertEquals(3, GameMath.bizNeedHours(withEnterprises(base, 0, manual, managed)))
        assertEquals(0, GameMath.bizNeedHours(withEnterprises(base, 0, managed, managed)))
    }

    @Test
    fun `эффективность управления = выделенные часы делить на нужные, не выше 1`() {
        val manual = Enterprise()
        // без предприятий эффективность полная
        assertEquals(1.0, GameMath.mgmtEff(GameState()), EPS)

        val two = withEnterprises(GameState(), 0, manual, manual)   // нужно 6 часов
        assertEquals(0.0, GameMath.mgmtEff(two.copy(bizH = 0)), EPS)
        assertEquals(0.5, GameMath.mgmtEff(two.copy(bizH = 3)), EPS)
        assertEquals(1.0, GameMath.mgmtEff(two.copy(bizH = 6)), EPS)
        // избыточные часы не дают бонуса
        assertEquals(1.0, GameMath.mgmtEff(two.copy(bizH = 12)), EPS)
    }

    // ===================== доход предприятия =====================

    @Test
    fun `доход предприятия = доход в час × 12 рабочих часов × эффективность управляющего`() {
        assertEquals(12.0, Industries.WORK_HOURS, EPS)

        // Лоток: 1.28 $/ч, лично (эффективность 1.0)
        assertEquals(15.36, GameMath.enterpriseIncomePerDay(trade, Enterprise()), EPS)
        // Студент даёт 60% отдачи
        val student = Enterprise(managerOrdinal = Manager.STUDENT.ordinal)
        assertEquals(15.36 * 0.60, GameMath.enterpriseIncomePerDay(trade, student), EPS)
        // Топ-менеджер даёт 110%
        val top = Enterprise(managerOrdinal = Manager.TOP.ordinal)
        assertEquals(15.36 * 1.10, GameMath.enterpriseIncomePerDay(trade, top), EPS)

        // уровень вне лестницы падает на последнюю ступень, а не роняет игру
        val overflow = Enterprise(level = 99)
        assertEquals(trade.levels.last().incomePerHour * 12.0,
            GameMath.enterpriseIncomePerDay(trade, overflow), EPS)
    }

    // ===================== множители =====================

    @Test
    fun `кризис смягчается антикризисным курсом`() {
        val s = GameState()
        assertEquals(1.0, GameMath.crisisMult(s.copy(phaseIndex = MarketPhase.GROWTH.ordinal)), EPS)
        assertEquals(1.6, GameMath.crisisMult(s.copy(phaseIndex = MarketPhase.BOOM.ordinal)), EPS)
        assertEquals(0.85, GameMath.crisisMult(s.copy(phaseIndex = MarketPhase.RECOVERY.ordinal)), EPS)

        val crisis = s.copy(phaseIndex = MarketPhase.CRISIS.ordinal)
        assertEquals(0.45, GameMath.crisisMult(crisis), EPS)
        assertEquals(0.65, GameMath.crisisMult(crisis.copy(eduDone = setOf("crisis"))), EPS)
        // курс не действует вне кризиса
        assertEquals(1.6, GameMath.crisisMult(
            s.copy(phaseIndex = MarketPhase.BOOM.ordinal, eduDone = setOf("crisis"))), EPS)
    }

    @Test
    fun `общий множитель бизнеса складывается из рынка, лидерства, репутации и престижа`() {
        val s = GameState()
        // нулевая репутация = 70% дохода
        assertEquals(0.70, GameMath.bizGlobalMult(s), EPS)
        // 100 репутации = 100%
        assertEquals(1.00, GameMath.bizGlobalMult(s.copy(reputation = 100.0)), EPS)
        // репутация берётся ЦЕЛОЙ: 49.9 считается как 49
        assertEquals(0.70 + 0.30 * 0.49, GameMath.bizGlobalMult(s.copy(reputation = 49.9)), EPS)
        // лидерство +15%
        assertEquals(0.70 * 1.15, GameMath.bizGlobalMult(s.copy(eduDone = setOf("lead"))), EPS)
        // престиж дохода +40% за уровень
        assertEquals(0.70 * 1.80, GameMath.bizGlobalMult(s.copy(pIncome = 2)), EPS)
        // бум ×1.6
        assertEquals(0.70 * 1.6,
            GameMath.bizGlobalMult(s.copy(phaseIndex = MarketPhase.BOOM.ordinal)), EPS)
    }

    @Test
    fun `насыщение отрасли режет суммарную отдачу как n в степени 0,85`() {
        assertEquals(1.0, BusinessConfig.saturationMult(0), EPS)
        assertEquals(1.0, BusinessConfig.saturationMult(1), EPS)
        assertEquals(Math.pow(2.0, 0.85) / 2.0, BusinessConfig.saturationMult(2), EPS)
        assertEquals(0.9012, BusinessConfig.saturationMult(2), 1e-4)
        // 10 предприятий в отрасли дают лишь ~7.08 «единиц» вместо 10
        assertEquals(0.7079, BusinessConfig.saturationMult(10), 1e-4)
        // отдача на предприятие монотонно падает
        for (n in 1 until BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY) {
            assertTrue("насыщение должно падать при n=$n",
                BusinessConfig.saturationMult(n + 1) < BusinessConfig.saturationMult(n) + EPS)
        }
    }

    @Test
    fun `суммарный доход отрасли = сумма сырых доходов × насыщение`() {
        val manual = Enterprise()
        val one = withEnterprises(GameState(bizH = 3), 0, manual)
        val two = withEnterprises(GameState(bizH = 6), 0, manual, manual)

        // Лоток лично, полные часы, репутация 0: 15.36 × 1.0 × 0.70
        assertEquals(10.752, GameMath.bizPerDay(one), EPS)
        assertEquals(2 * 10.752 * BusinessConfig.saturationMult(2), GameMath.bizPerDay(two), EPS)

        // нехватка часов управления бьёт по личным предприятиям напрямую:
        // 3 часа на двоих из нужных 6 = половина дохода
        val starved = two.copy(bizH = 3)
        assertEquals(2 * 10.752 * 0.5 * BusinessConfig.saturationMult(2), GameMath.bizPerDay(starved), EPS)

        // без предприятий бизнес-дохода нет
        assertEquals(0.0, GameMath.bizPerDay(GameState()), EPS)
    }

    @Test
    fun `чистый доход предприятия = валовый минус зарплата управляющего`() {
        val managed = Enterprise(level = 0, managerOrdinal = Manager.MANAGER.ordinal)
        val s = withEnterprises(GameState(), 0, managed)

        // 1.28 × 12 × 0.80 (менеджер) × 0.70 (репутация 0) = 8.6016
        assertEquals(8.6016, GameMath.enterpriseGrossPerDay(s, trade, managed), EPS)
        // зарплата менеджера 180 $/день — на первой ступени это глубокий минус
        assertEquals(8.6016 - 180.0, GameMath.enterpriseNetPerDay(s, trade, managed), EPS)
        assertEquals(180.0, GameMath.managersSalaryPerDay(s), EPS)
        // лично управляемое предприятие зарплаты не стоит
        assertEquals(0.0, GameMath.managersSalaryPerDay(withEnterprises(GameState(), 0, Enterprise())), EPS)
    }

    // ===================== стабильность показанных чисел =====================

    /**
     * Поздняя игра: все отрасли развиты, капитал такой, что давление элит уже работает.
     * Доход здесь огромный — именно в этом режиме прирост за тик заметен и цифры дрожали.
     */
    private fun richState(money: Double) = GameState(
        money = money, bizH = 6, reputation = 41.4, pIncome = 9,
        enterprises = List(Industries.count) { i ->
            List(BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY) {
                Enterprise(Industries.all[i].levels.lastIndex, Manager.TOP.ordinal)
            }
        }
    )

    @Test
    fun `прирост денег за один тик не меняет показанный доход отрасли`() {
        val money = 1_000_000_000_000.0
        val base = richState(money)
        // за тик (100 мс) начисляется доход одного игрового дня, делённый на 240
        val perTick = GameMath.incomePerDay(base) / 240.0
        assertTrue("тик должен быть заметной суммой, иначе тест ничего не проверяет", perTick > 1_000_000.0)

        assertEquals(GameMath.bizPerDay(base), GameMath.bizPerDay(base.copy(money = money + perTick)), EPS)
        // и за несколько тиков подряд тоже — иначе цифры дрожали бы раз в полсекунды
        assertEquals(GameMath.bizPerDay(base), GameMath.bizPerDay(base.copy(money = money + perTick * 5)), EPS)
    }

    @Test
    fun `дрейф репутации между секундами не меняет показанный доход`() {
        val base = richState(1_000_000_000_000.0)
        // репутация растёт непрерывно; пока целая часть та же, доход обязан стоять на месте
        assertEquals(GameMath.bizPerDay(base), GameMath.bizPerDay(base.copy(reputation = 41.9)), EPS)
        // с переходом через целое значение доход меняется
        assertTrue(GameMath.bizPerDay(base.copy(reputation = 42.0)) > GameMath.bizPerDay(base))
    }

    @Test
    fun `существенный рост капитала давление всё же меняет`() {
        val small = richState(1_000_000_000.0)
        val big = richState(1_000_000_000_000.0)
        assertTrue("давление обязано расти с капиталом",
            GameMath.pressureShown(big) > GameMath.pressureShown(small))
        // а доход отрасли — падать
        assertTrue(GameMath.bizPerDay(big) < GameMath.bizPerDay(small))
    }

    @Test
    fun `огрубление денег почти не меняет само давление`() {
        // сглаживание визуальное: отклонение от давления по точной сумме должно быть мизерным
        listOf(1.5e9, 4.7e10, 1.0e12, 3.9e13).forEach { money ->
            val exact = Pressure.value(money, 41.0)
            val shown = GameMath.pressureShown(richState(money))
            assertEquals("капитал $money", exact, shown, 0.001)
        }
    }

    @Test
    fun `огрубление денег идёт ступенями по три значащих цифры`() {
        assertEquals(1_000_000_000.0, GameMath.pressureMoney(1_009_999_999.0), EPS)
        assertEquals(1_010_000_000.0, GameMath.pressureMoney(1_010_000_001.0), EPS)
        assertEquals(3_450_000_000_000.0, GameMath.pressureMoney(3_456_789_000_000.0), EPS)
        // ноль и отрицательные проходят насквозь — давления там нет
        assertEquals(0.0, GameMath.pressureMoney(0.0), EPS)
        assertEquals(-5.0, GameMath.pressureMoney(-5.0), EPS)
    }

    // ===================== пассив и итог =====================

    @Test
    fun `пассивный доход растёт от MBA и тира карты`() {
        val values = listOf(1_000.0, 0.0, 0.0)   // депозит 0.2% в день
        assertEquals(2.0, Investments.incomePerDay(values, emptySet()), EPS)
        assertEquals(2.4, Investments.incomePerDay(values, setOf("mba")), EPS)

        val s = GameState(investValues = values)
        assertEquals(2.0, GameMath.invPerDay(s), EPS)
        // GOLD (ordinal 2) даёт +10% к пассиву
        assertEquals(2.2, GameMath.invPerDay(s.copy(activatedCardTier = 2)), EPS)
        // тир вне диапазона откатывается на CLASSIC
        assertEquals(2.0, GameMath.invPerDay(s.copy(activatedCardTier = 99)), EPS)
    }

    @Test
    fun `итоговый доход = зарплата плюс бизнес плюс пассив, чистый — минус содержание`() {
        val s = withEnterprises(
            GameState(jobId = "courier", bizH = 3, investValues = listOf(1_000.0, 0.0, 0.0)),
            0, Enterprise()
        )
        val expected = 8.0 + 10.752 + 2.0
        assertEquals(expected, GameMath.incomePerDay(s), EPS)

        // хрущёвка стоит 120 $/день содержания
        val withHome = s.copy(ownedHomes = Lifestyle.ladderSet(1))
        assertEquals(expected - 120.0, GameMath.netIncomePerDay(withHome), EPS)
    }

    @Test
    fun `рекламный буст удваивает чистый поток, включая убыток`() {
        val s = GameState(jobId = "courier", ownedHomes = Lifestyle.ladderSet(1))   // доход 8, содержание 120
        assertEquals(1.0, GameMath.boostMult(s), EPS)
        assertEquals(-112.0, GameMath.netIncomePerDay(s), EPS)

        val boosted = s.copy(boostEndsAtMillis = System.currentTimeMillis() + 600_000L)
        assertEquals(2.0, GameMath.boostMult(boosted), EPS)
        // ВНИМАНИЕ: буст умножает уже вычтенный поток, поэтому минус тоже удваивается
        assertEquals(-224.0, GameMath.netIncomePerDay(boosted), EPS)
        assertTrue(GameMath.boostRemainingMs(boosted) > 0L)
        assertEquals(0L, GameMath.boostRemainingMs(s))
    }

    @Test
    fun `тап даёт полчаса дневного дохода, но не меньше доллара`() {
        // бедный игрок всё равно получает $1
        assertEquals(1.0, GameMath.tapReward(GameState()), EPS)
        assertEquals(1.0, GameMath.tapReward(GameState(jobId = "courier")), EPS)

        // недвижимость 48 000 под 1% в день = 480 $/день -> 480/48
        val rich = GameState(investValues = listOf(0.0, 0.0, 48_000.0))
        assertEquals(480.0, GameMath.incomePerDay(rich), EPS)
        assertEquals(10.0, GameMath.tapReward(rich), EPS)
    }
}
