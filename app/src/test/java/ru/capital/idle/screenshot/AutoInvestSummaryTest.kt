package ru.capital.idle.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.AutoInvest
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.ui.AutoInvestCard

/**
 * Итог автовклада — всегда одна и та же карточка «следующим днём уйдёт».
 *
 * Раньше на её месте возникало предупреждение «не сработает: …» — ровно в тот миг, когда
 * автовклад списал деньги и на карте остался один резерв. Это доля секунды раз в игровой день:
 * прочитать нельзя, а мельтешит постоянно. Теперь в такие моменты в карточке стоит ноль,
 * и он говорит то же самое.
 *
 * Здесь перебираются сочетания баланса, резерва и долга и проверяется оба утверждения:
 * карточка на месте всегда, а когда переводить нечего — в ней ноль.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE_ENDLESS)
class AutoInvestSummaryTest {

    @get:Rule
    val compose = createComposeRule()

    private val shown = mutableStateOf(GameState())
    private val currency = mutableStateOf(Currency.USD)

    private fun saver(money: Double, reserve: Double, debt: Double = 0.0) = GameState(
        money = money, debt = debt, eduDone = setOf("school", "acc"),
        autoInvestOn = true, autoInvestReserve = reserve
    )

    /** Баланс и резерв по обе стороны от порога, включая края и долг. */
    private fun cases(): List<Pair<String, GameState>> {
        val out = ArrayList<Pair<String, GameState>>()
        listOf(0.0, 10_000.0, 1e9).forEach { reserve ->
            listOf(0.0, 1.0, reserve, reserve + 1.0, reserve * 3, 1e12).forEach { money ->
                out += "резерв $reserve, на карте $money" to saver(money, reserve)
            }
            out += "резерв $reserve, долг" to saver(reserve * 3, reserve, debt = 250_000.0)
        }
        return out
    }

    @Composable
    private fun Card() {
        val s = shown.value
        Column(Modifier.fillMaxWidth()) {
            AutoInvestCard(
                on = s.autoInvestOn,
                target = AutoInvest.target(s),
                unlocked = AutoInvest.available(s),
                reserve = s.autoInvestReserve,
                amount = AutoInvest.amount(s),
                cur = currency.value
            )
        }
    }

    private fun show(s: GameState) {
        shown.value = s
        compose.waitForIdle()
    }

    private fun countOf(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    // ===================== карточка вместо предупреждения =====================

    @Test
    fun `при любом балансе и резерве стоит карточка, а не предупреждение`() {
        compose.setContent { Card() }
        listOf(Currency.USD, Currency.RUB).forEach { cur ->
            currency.value = cur
            cases().forEach { (name, s) ->
                show(s)
                assertEquals("карточка пропала ($name, ${cur.code})", 1, countOf("следующим днём уйдёт"))
                assertEquals(
                    "вернулось предупреждение ($name, ${cur.code})",
                    0, countOf("не сработает", substring = true)
                )
            }
        }
    }

    // ===================== ноль вместо причины =====================

    @Test
    fun `когда переводить нечего, в карточке ноль`() {
        compose.setContent { Card() }
        listOf(Currency.USD, Currency.RUB).forEach { cur ->
            currency.value = cur
            val zero = GameMath.formatMoney(0.0, cur)
            // резерв ненулевой, поэтому «ноль» в карточке — единственный на экране
            val nothingToMove = listOf(
                "только что списалось" to saver(money = 10_000.0, reserve = 10_000.0),
                "на карте меньше резерва" to saver(money = 1.0, reserve = 10_000.0),
                "долг" to saver(money = 30_000.0, reserve = 10_000.0, debt = 250_000.0)
            )
            nothingToMove.forEach { (name, s) ->
                assertEquals("сам перевод нулевой ($name)", 0.0, AutoInvest.amount(s), 1e-9)
                show(s)
                assertEquals("в карточке не ноль ($name, ${cur.code})", 1, countOf(zero))
            }

            // а когда есть что переводить — в ней сумма, а не ноль
            show(saver(money = 30_000.0, reserve = 10_000.0))
            assertEquals("ноль там, где перевод есть (${cur.code})", 0, countOf(zero))
            assertEquals(
                "сумма перевода не показана (${cur.code})",
                1, countOf(GameMath.formatMoney(20_000.0, cur))
            )
        }
    }
}
