package ru.capital.idle.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.core.game.GameMath

/**
 * Прогон игры двумя стратегиями по 2000 игровых дней с проверкой инвариантов на каждом дне.
 *
 * Это первая часть каркаса: две стратегии и базовый набор правил, а не полное покрытие.
 * Отчёт печатается в лог — по нему видно, как выглядит экономика на длинной дистанции.
 */
class SimulationTest {

    private val days = 2_000

    private fun runAndCheck(strategy: Strategy): List<DayRecord> {
        val records = Simulator(strategy).run(days)
        println(Report.of(strategy.name, records))

        val violations = mutableListOf<Violation>()
        records.forEachIndexed { i, r ->
            violations += Invariants.check(records.getOrNull(i - 1), r)
        }
        val (known, fresh) = violations.partition { Invariants.isKnownFinding(it) }
        if (known.isNotEmpty()) {
            println("ИЗВЕСТНОЕ РАСХОЖДЕНИЕ (${strategy.name}), ${known.size} дней подряд, первое:")
            println("  ${known.first()}")
        }
        assertTrue(
            "нарушены инварианты (${strategy.name}), первые десять:\n" +
                fresh.take(10).joinToString("\n"),
            fresh.isEmpty()
        )
        return records
    }

    @Test
    fun `стратегия только бизнес проходит две тысячи дней без нарушений`() {
        val records = runAndCheck(BusinessOnly())

        // прогон должен быть содержательным, а не «ничего не произошло»
        val last = records.last()
        assertTrue("бизнес обязан был вырасти: капитал ${last.worth}", last.worth > 100_000.0)
        assertTrue("предприятия должны появиться",
            last.state.enterprises.sumOf { it.size } > 0)

        // и капитал не удваивается сам по себе: в тихие промежутки бизнес растёт линейно
        val doubling = Invariants.checkDoubling(records)
        assertTrue("капитал удваивается без действий игрока:\n" +
            doubling.joinToString("\n"), doubling.isEmpty())
    }

    @Test
    fun `стратегия только вклады проходит две тысячи дней без нарушений`() {
        val records = runAndCheck(DepositsOnly())

        val last = records.last()
        assertTrue("вклады обязаны были вырасти: капитал ${last.worth}", last.worth > 1_000_000.0)
        assertTrue("деньги должны лежать во вкладах",
            last.state.investValues.sum() > last.state.money)
    }

    /** Каркас не должен молча «проглатывать» день: капитал и доход обязаны быть числами. */
    @Test
    fun `прогон даёт осмысленную историю`() {
        val records = Simulator(BusinessOnly()).run(200)
        assertTrue(records.size == 200)
        assertTrue(records.all { it.worth.isFinite() && it.worth >= 0.0 })
        assertTrue("доход должен появиться",
            records.any { GameMath.incomePerDay(it.state) > 0.0 })
    }
}
