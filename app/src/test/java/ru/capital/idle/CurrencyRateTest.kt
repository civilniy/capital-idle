package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.capital.idle.GameTestFixtures.EPS
import ru.capital.idle.GameTestFixtures.withEnterprises
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Manager
import ru.capital.idle.core.game.Milestones
import kotlin.math.abs
import kotlin.math.floor

/**
 * Курс валют — только для показа.
 *
 * У рубля он ровно 100: это игровая условность, а не котировка. Смысл ровно один —
 * круглые долларовые величины должны оставаться круглыми и в рублях. И столь же важно,
 * что выбранная валюта не двигает ни одной игровой цифры.
 */
class CurrencyRateTest {

    /** Неразрывный пробел — им `format` разделяет разряды. */
    private val NB = ' '

    @Test
    fun `у рубля курс ровно сто`() {
        assertEquals(100.0, Currency.RUB.ratePerUsd, EPS)
    }

    @Test
    fun `миллион долларов показывается как сто миллионов рублей`() {
        assertEquals("₽ 100${NB}000${NB}000", GameMath.formatMoney(1_000_000.0, Currency.RUB))
        assertEquals("100${NB}000${NB}000", GameMath.formatAmount(1_000_000.0, Currency.RUB))
    }

    /**
     * Пороги вех в рублях остаются такими же круглыми, как в долларах.
     *
     * Проверяется не «круглость» сама по себе — порог «Богаче №1» и в долларах некруглый
     * (788,8 млрд — реальный капитал первого номера списка). Проверяется, что пересчёт
     * **не добавляет** ни одной значащей цифры: умножение на сотню сдвигает разряд и только.
     * Курс 73,7 добавлял их сразу три — из 1e6 получалось 73 700 000.
     */
    @Test
    fun `пересчёт порогов вех не добавляет значащих цифр`() {
        val spoiled = Milestones.all.filter { ms ->
            val rub = ms.thresholdUsd * Currency.RUB.ratePerUsd
            rub != floor(rub) || significantDigits(rub) > significantDigits(ms.thresholdUsd)
        }
        assertEquals("пересчёт испортил пороги: ${spoiled.map { it.name }}",
            emptyList<String>(), spoiled.map { it.name })
    }

    /** Сколько значащих цифр в числе: 1e6 → 1, 4,2e9 → 2, 788,8e9 → 4. */
    private fun significantDigits(v: Double): Int {
        if (v == 0.0) return 1
        var x = abs(v)
        while (x < 1.0) x *= 10.0
        var digits = 1
        while (x != floor(x) && digits < 18) { x *= 10.0; digits++ }
        var whole = x.toLong()
        while (whole % 10L == 0L && whole > 0L) whole /= 10L
        return whole.toString().length
    }

    /**
     * Круглые пороги остаются круглыми на экране. Миллион долларов — ровно тот случай,
     * ради которого курс и поменяли: при 73,7 веха показывалась как 73 700 000 ₽.
     */
    @Test
    fun `круглые пороги вех остаются круглыми на экране`() {
        val round = mapOf(
            1e6 to ("$ 1${NB}000${NB}000" to "₽ 100${NB}000${NB}000"),
            // от сотни и выше дробная часть не показывается — см. GameMath.format
            1e9 to ("$ 1,0B" to "₽ 100B"),
            1e12 to ("$ 1,0T" to "₽ 100T")
        )
        round.forEach { (usd, expected) ->
            val (inUsd, inRub) = expected
            assertEquals(inUsd, GameMath.formatMoney(usd, Currency.USD))
            assertEquals(inRub, GameMath.formatMoney(usd, Currency.RUB))
        }
        // все три — настоящие пороги вех, а не выдуманные числа
        assertEquals(
            listOf(true, true, true),
            round.keys.map { v -> Milestones.all.any { it.thresholdUsd == v } }
        )
    }

    // ===================== курс не трогает экономику =====================

    /**
     * Состояние с работой, предприятиями, вкладами и имуществом — чтобы в расчёт попали
     * все слагаемые дохода, а не один из них.
     */
    private fun richState(): GameState = withEnterprises(
        GameState(
            money = 5_000_000.0, bizH = 12, workH = 4, jobId = "director",
            eduDone = setOf("school", "sales", "mgmt", "acc", "uni"),
            investValues = List(ru.capital.idle.core.game.Investments.COUNT) { 250_000.0 },
            ownedHomes = setOf(0, 1, 2), ownedCars = setOf(0, 1), ownedTechs = setOf(0, 1),
            reputation = 40.0
        ),
        0,
        Enterprise(level = 3, invested = 100_000.0, earned = 40_000.0),
        Enterprise(level = 2, managerOrdinal = Manager.PRO.ordinal, invested = 20_000.0)
    )

    /**
     * Валюта живёт в состоянии как `currencyCode`, и переключение меняет только её.
     * Ни одна расчётная величина от этого сдвинуться не должна: все они в долларах.
     */
    @Test
    fun `переключение валюты не меняет ни одной игровой цифры`() {
        val inUsd = richState().copy(currencyCode = "USD")
        val inRub = inUsd.copy(currencyCode = "RUB")
        val trade = Industries.all[0]

        assertEquals("доход в день", GameMath.incomePerDay(inUsd), GameMath.incomePerDay(inRub), EPS)
        assertEquals("капитал", GameMath.netWorth(inUsd), GameMath.netWorth(inRub), EPS)
        assertEquals("бизнесы", GameMath.bizPerDay(inUsd), GameMath.bizPerDay(inRub), EPS)
        assertEquals("зарплата", GameMath.salaryPerDay(inUsd), GameMath.salaryPerDay(inRub), EPS)
        assertEquals("пассив", GameMath.invPerDay(inUsd), GameMath.invPerDay(inRub), EPS)
        assertEquals("награда за тап", GameMath.tapReward(inUsd), GameMath.tapReward(inRub), EPS)
        assertEquals("цена открытия",
            GameMath.openEnterpriseCost(inUsd, 0), GameMath.openEnterpriseCost(inRub, 0), EPS)
        assertEquals("цена улучшения",
            GameMath.upgradeEnterpriseCost(inUsd, 0, 0), GameMath.upgradeEnterpriseCost(inRub, 0, 0), EPS)
        assertEquals("окупаемость",
            GameMath.payback(inUsd, trade, inUsd.enterprises[0][0]).daysLeft,
            GameMath.payback(inRub, trade, inRub.enterprises[0][0]).daysLeft
        )
        assertEquals("оффлайн-доход",
            GameMath.offlineEarnings(inUsd, 3_600.0), GameMath.offlineEarnings(inRub, 3_600.0))
    }

    /**
     * И прогресс тоже: вехи, титулы и социальный статус считаются по долларовым порогам.
     * Если бы курс попал сюда, в рублях они брались бы в сто раз раньше.
     */
    @Test
    fun `прогресс не зависит от выбранной валюты`() {
        val inUsd = richState().copy(currencyCode = "USD", totalEarned = 2_000_000.0)
        val inRub = inUsd.copy(currencyCode = "RUB")

        assertEquals(
            ru.capital.idle.core.game.Lifestyle.titleIndex(inUsd.totalEarned),
            ru.capital.idle.core.game.Lifestyle.titleIndex(inRub.totalEarned)
        )
        assertEquals(
            ru.capital.idle.core.game.Lifestyle.socialStatus(inUsd),
            ru.capital.idle.core.game.Lifestyle.socialStatus(inRub)
        )
        assertEquals(
            Milestones.all.count { inUsd.totalEarned >= it.thresholdUsd },
            Milestones.all.count { inRub.totalEarned >= it.thresholdUsd }
        )
    }

    /** Смена курса влияет ровно на две функции — обе форматирующие. */
    @Test
    fun `курс применяется только при выводе на экран`() {
        val usd = 1_234.0
        assertEquals(usd * 100.0, GameMath.formatAmount(usd, Currency.RUB).digits(), EPS)
        assertEquals(usd, GameMath.formatAmount(usd, Currency.USD).digits(), EPS)
    }

    /** Число из отформатированной строки: убираем разделители разрядов. */
    private fun String.digits(): Double =
        filter { it.isDigit() }.toDouble()
}
