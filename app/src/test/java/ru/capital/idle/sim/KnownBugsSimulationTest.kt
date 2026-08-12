package ru.capital.idle.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.core.game.Onboarding

/**
 * Проверка самого каркаса на известных ошибках.
 *
 * Инструмент, который ничего не находит, бесполезен, а доверять ему можно только показав,
 * что он видит настоящие ошибки. Здесь берутся три известные — и каждая воспроизводится
 * так, как она выглядела в игре: не подделкой числа, а подстановкой прежней формулы.
 *
 * Две из трёх уже исправлены (PR #28), поэтому в этих тестах прежнее поведение подставляется
 * явно: симулятору говорят считать счётчик без буста, а инвариантам — брать титул и пороги
 * от валового дохода. Третья ошибка живая, и ловится она на самом обычном прогоне.
 *
 * Тесты падают, если каркас перестанет замечать ошибку.
 */
class KnownBugsSimulationTest {

    private val days = 1_200

    /** Буст, включённый на весь прогон: без него ошибка со счётчиком не проявляется. */
    private fun withBoost() = GameState(
        onboarded = true, tutorialStep = Onboarding.DONE,
        boostEndsAtMillis = System.currentTimeMillis() + 10L * 365 * 24 * 3_600_000L
    )

    // ===================== 1. счётчик всех жизней без буста =====================

    @Test
    fun `каркас ловит отставание счётчика всех жизней при бусте`() {
        val records = Simulator(
            BusinessOnly(), start = withBoost(), statAllTimeWithoutBoost = true
        ).run(days)

        val hits = records.flatMapIndexed { i, r ->
            Invariants.check(records.getOrNull(i - 1), r).filter { it.rule == "счётчики заработка" }
        }
        assertTrue("каркас не заметил, что statAllTimeEarned отстаёт вдвое", hits.isNotEmpty())
        println("ПОЙМАНО · счётчик всех жизней без буста · ${hits.first()}")

        // и отставание именно двукратное
        val last = records.last().state
        val ratio = last.totalEarned / last.statAllTimeEarned
        assertTrue("отношение счётчиков $ratio, ожидалось около двух", ratio > 1.9 && ratio < 2.1)

        // с исправленной формулой того же прогона нарушений нет
        val fixed = Simulator(BusinessOnly(), start = withBoost()).run(days)
        val clean = fixed.flatMapIndexed { i, r ->
            Invariants.check(fixed.getOrNull(i - 1), r).filter { it.rule == "счётчики заработка" }
        }
        assertTrue("на исправленном коде правило срабатывать не должно: $clean", clean.isEmpty())
    }

    // ===================== 2. титул и разделы от валового дохода =====================

    /** Прежнее правило: пороги сравнивались с `totalEarned`. */
    private val oldGates = mapOf("world" to 1_000_000.0, "net" to 5_000.0, "pres" to 1_000_000_000.0)

    @Test
    fun `каркас ловит титул и пороги, посчитанные от валового дохода`() {
        val records = Simulator(BusinessOnly()).run(days)

        val hits = records.flatMapIndexed { i, r ->
            Invariants.check(
                records.getOrNull(i - 1), r,
                titleOf = { Lifestyle.titleIndex(it.totalEarned) },
                unlockedBy = { st, id -> st.totalEarned >= (oldGates[id] ?: 0.0) }
            ).filter { it.rule == "титул" || it.rule == "порог раздела" }
        }
        assertTrue("каркас не заметил расхождения титула и капитала", hits.isNotEmpty())
        println("ПОЙМАНО · титул и пороги от валового дохода · ${hits.first()}")

        // Валовой доход и капитал — разные величины, и по ходу игры они то и дело меняются
        // местами: в начале капитал обгоняет доход (купленный бизнес считается по цене
        // ступеней лестницы), позже доход уходит вперёд. Именно поэтому титул, посчитанный
        // от одного, расходится с числом на главном экране в обе стороны
        val last = records.last().state
        println("  расхождений за прогон: ${hits.size}; " +
            "на последнем дне валовой доход ${last.totalEarned}, капитал ${last.peakNetWorth}")
    }

    // ===================== 3. вклады растут по неограниченной экспоненте =====================

    /**
     * Живая ошибка. Капитализация прибавляет к телу вклада долю от него же каждый игровой
     * день, потолка нет — тело растёт геометрически. Ловится на продолжении обычного
     * прогона: игрок перестаёт что-либо делать, а капитал всё равно удваивается.
     */
    @Test
    fun `каркас ловит неограниченный рост вкладов`() {
        val grown = Simulator(DepositsOnly()).run(1_000).last().state
        val idle = Simulator(Idle(), start = grown).run(400)

        val doubling = Invariants.checkDoubling(idle, window = 200)
        assertTrue("каркас не заметил удвоения капитала без действий игрока", doubling.isNotEmpty())
        println("ПОЙМАНО · неограниченный рост вкладов · ${doubling.first()}")

        // счёт идёт на порядки: за 400 дней без единого действия
        val from = idle.first().worth
        val to = idle.last().worth
        println("  капитал без действий игрока: %s -> %s за 400 дней"
            .format(from, to))
        assertTrue("капитал должен был вырасти в разы: $from -> $to", to > from * 2.0)
    }

    /** Для сравнения: у бизнеса такого нет — доход линеен, и правило молчит. */
    @Test
    fun `у бизнеса капитал сам по себе не удваивается`() {
        val grown = Simulator(BusinessOnly()).run(1_000).last().state
        val idle = Simulator(Idle(), start = grown).run(400)
        val doubling = Invariants.checkDoubling(idle, window = 200)
        assertTrue("бизнес не должен удваиваться сам: $doubling", doubling.isEmpty())
    }
}
