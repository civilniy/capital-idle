package ru.capital.idle.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.ui.LifeItemCard
import ru.capital.idle.ui.MoneyCellsRow
import ru.capital.idle.ui.ProfileTabsRow

/**
 * Вёрстка экрана профиля на крайних значениях.
 * Тесты регрессионные: если подпись вкладки снова уедет на вторую строку
 * или число вылезет за плитку, сверка с эталоном упадёт.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class ProfileLayoutScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // ===================== ряд вкладок =====================

    @Test
    fun `вкладки профиля — все четыре в одну строку`() {
        compose.captureOnBackground("profile_tabs") {
            ProfileTabsRow(selected = 0, onSelect = {})
        }
    }

    @Test
    fun `вкладки профиля — выбрана самая длинная подпись`() {
        compose.captureOnBackground("profile_tabs_collection_selected") {
            // активная вкладка рисуется ExtraBold, то есть шире обычной
            ProfileTabsRow(selected = 4, onSelect = {})
        }
    }

    /**
     * ИЗВЕСТНОЕ РАСХОЖДЕНИЕ: при масштабе шрифта 1.5 подпись «Имущество» обрезается
     * до «Имуществ» — кегль упирается в нижнюю границу 8.5sp и текст перестаёт помещаться.
     * Эталон снят как есть и фиксирует текущее поведение, а не желаемое.
     * Когда дефект будут чинить (нижняя граница кегля либо две строки при крупном шрифте) —
     * эталон нужно перезаписать.
     */
    @Test
    fun `вкладки профиля при крупном системном шрифте`() {
        compose.captureOnBackground("profile_tabs_large_font", fontScale = Screenshots.LARGE_FONT) {
            ProfileTabsRow(selected = 0, onSelect = {})
        }
    }

    // ===================== три числа шапки =====================

    @Test
    fun `плитки денег на триллионах в рублях`() {
        val cur = Currency.RUB
        compose.captureOnBackground("profile_money_cells_trillions") {
            MoneyCellsRow(
                moneyStr = GameMath.formatMoney(1.5e12, cur),
                upkeepStr = GameMath.formatMoney(8.4e9, cur),
                netStr = "+" + GameMath.formatMoney(2.7e11, cur),
                inDebt = false, netPositive = true
            )
        }
    }

    @Test
    fun `плитки денег с долгом и отрицательным доходом`() {
        val cur = Currency.RUB
        compose.captureOnBackground("profile_money_cells_debt") {
            MoneyCellsRow(
                moneyStr = "-" + GameMath.formatMoney(4.2e8, cur),
                upkeepStr = GameMath.formatMoney(1.9e8, cur),
                netStr = "-" + GameMath.formatMoney(1.8e8, cur),
                inDebt = true, netPositive = false
            )
        }
    }

    @Test
    fun `плитки денег при крупном системном шрифте`() {
        val cur = Currency.RUB
        compose.captureOnBackground("profile_money_cells_large_font", fontScale = Screenshots.LARGE_FONT) {
            MoneyCellsRow(
                moneyStr = GameMath.formatMoney(1.5e12, cur),
                upkeepStr = GameMath.formatMoney(8.4e9, cur),
                netStr = "+" + GameMath.formatMoney(2.7e11, cur),
                inDebt = false, netPositive = true
            )
        }
    }

    // ===================== карточка имущества =====================

    @Test
    fun `карточка имущества с самым длинным названием и ценой`() {
        // «Остров в Карибском море» — самый дорогой дом, $80 млрд и содержание $200 млн
        val item = Lifestyle.home.items.maxByOrNull { it.cost }!!
        compose.captureOnBackground("life_item_longest_name") {
            LifeItemCard(
                item = item, owned = false, isBase = false, canBuy = true, cur = Currency.RUB,
                onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `карточка имущества купленного — кнопка продажи`() {
        val item = Lifestyle.car.items.maxByOrNull { it.cost }!!   // Gulfstream G700
        compose.captureOnBackground("life_item_owned") {
            LifeItemCard(
                item = item, owned = true, isBase = false, canBuy = false, cur = Currency.RUB,
                onBuy = {}, onSell = {}
            )
        }
    }

    @Test
    fun `карточка имущества при крупном системном шрифте`() {
        val item = Lifestyle.home.items.maxByOrNull { it.cost }!!
        compose.captureOnBackground("life_item_large_font", fontScale = Screenshots.LARGE_FONT) {
            LifeItemCard(
                item = item, owned = false, isBase = false, canBuy = true, cur = Currency.RUB,
                onBuy = {}, onSell = {}
            )
        }
    }
}
