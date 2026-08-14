package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Asset
import ru.capital.idle.core.game.AutoInvest
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.Exchange
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.ui.AutoInvestCard
import ru.capital.idle.ui.PassiveCard
import ru.capital.idle.ui.StockCard
import ru.capital.idle.ui.theme.Bg

/**
 * Экран «Инвест» не подпрыгивает, когда срабатывает автовклад.
 *
 * Замер по видео с устройства: между кадрами менялось больше половины строк экрана. Причина —
 * две взаимозаменяемые области в блоке автовклада: карточка «следующим днём уйдёт» и
 * предупреждение «не сработает: …». Высота у них была разная, а менялись они сами по себе:
 * в момент списания баланс падает до резерва, через мгновение деньги набегают снова.
 * Игровой день — 24 реальные секунды, так что дёргалось постоянно.
 *
 * Предупреждение убрано совсем — теперь в такие моменты в карточке стоит ноль
 * (см. `AutoInvestSummaryTest`), — но проверка остаётся: она держит саму высоту содержимого,
 * а не конкретную раскладку. Строка суммы не имеет переноса, поэтому длина числа на высоту
 * влиять не должна ни при каком балансе.
 *
 * Здесь берутся все состояния этой области и проверяется, что суммарная высота содержимого
 * совпадает ТОЧНО. Ниже автовклада на экране стоят вклады и акции — они и уезжали, поэтому
 * собраны здесь же: меряется вся стопка, а не одна карточка.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE_ENDLESS)
class AutoInvestHeightTest {

    @get:Rule
    val compose = createComposeRule()

    private val reserve = 1_000_000.0

    /**
     * Состояния одного и того же игрока: между ними меняется только то, сработает ли
     * автовклад ближайшим днём. Так выглядит один игровой день изнутри.
     */
    private val states = listOf(
        "деньги набежали" to saver(money = reserve * 3),
        "только что списалось" to saver(money = reserve),
        "на карте меньше резерва" to saver(money = 1.0),
        "в долгах" to saver(money = reserve * 3, debt = 250_000.0)
    )

    private fun saver(money: Double, debt: Double = 0.0) = GameState(
        money = money, debt = debt, eduDone = setOf("school", "acc", "uni"),
        autoInvestOn = true, autoInvestReserve = reserve
    )

    private val shown = mutableStateOf(states.first().second)
    private val currency = mutableStateOf(Currency.USD)
    private val fontScale = mutableFloatStateOf(1f)

    /** То, что стоит на экране «Инвест» начиная с блока автовклада и ниже. */
    @Composable
    private fun ScreenBody() {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale.floatValue)
        ) {
            val s = shown.value
            val cur = currency.value
            // тот же отступ по краям, что и на экране
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    AutoInvestCard(
                        on = s.autoInvestOn,
                        target = AutoInvest.target(s),
                        unlocked = AutoInvest.available(s),
                        reserve = s.autoInvestReserve,
                        amount = AutoInvest.amount(s),
                        cur = cur
                    )
                    Spacer(Modifier.height(16.dp))
                    Asset.entries.forEach { a ->
                        PassiveCard(s, a, cur, onInvest = {}, onSell = {}, onToggleCap = {})
                        Spacer(Modifier.height(10.dp))
                    }
                    Exchange.stocks.forEachIndexed { i, stock ->
                        StockCard(
                            state = s, index = i, stock = stock, cur = cur,
                            history = emptyList(), eventDir = 0, onBuy = {}, onSell = {}
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    private fun heightOf(s: GameState): Int {
        shown.value = s
        compose.waitForIdle()
        return compose.onRoot().fetchSemanticsNode().size.height
    }

    // ===================== высота =====================

    @Test
    fun `высота содержимого одинакова во всех состояниях автовклада`() {
        compose.setContent { ScreenBody() }
        listOf(Currency.USD, Currency.RUB).forEach { cur ->
            currency.value = cur
            listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
                fontScale.floatValue = fs
                val heights = states.associate { (name, s) -> name to heightOf(s) }
                assertEquals(
                    "содержимое поехало (${cur.code}, шрифт $fs): $heights",
                    1, heights.values.distinct().size
                )
                // замер обязан помещаться в холст: обрезанная по окну стопка дала бы
                // одинаковые числа при любой вёрстке, и проверка стала бы вхолостую
                val canvasPx = Screenshots.ENDLESS_HEIGHT_DP * compose.density.density
                assertTrue(
                    "стопка упёрлась в холст (${cur.code}, шрифт $fs): $heights из $canvasPx",
                    heights.values.max() < canvasPx
                )
            }
        }
    }

    /**
     * Проверка не вхолостую: состояния действительно показывают разные числа. Иначе тест выше
     * сравнивал бы одинаковое с одинаковым и подпрыгивания не поймал бы.
     */
    @Test
    fun `состояния показывают разные суммы перевода`() {
        compose.setContent { ScreenBody() }
        listOf(1f, Screenshots.LARGE_FONT).forEach { fs ->
            fontScale.floatValue = fs
            val sums = states.map { (name, s) ->
                shown.value = s
                compose.waitForIdle()
                // карточка итога стоит в любом состоянии — меняется только число в ней
                assertTrue(
                    "карточка итога пропала ($name, шрифт $fs)",
                    compose.onAllNodesWithText("следующим днём уйдёт").fetchSemanticsNodes().size == 1
                )
                GameMath.formatMoney(AutoInvest.amount(s), currency.value)
            }
            assertTrue("суммы обязаны различаться (шрифт $fs): $sums", sums.distinct().size > 1)
        }
    }
}
