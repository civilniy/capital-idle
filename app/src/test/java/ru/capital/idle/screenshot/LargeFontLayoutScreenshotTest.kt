package ru.capital.idle.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.CardTier
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameState
import ru.capital.idle.ui.CapitalHeader
import ru.capital.idle.ui.CreditCard
import ru.capital.idle.ui.DayPlanLabels
import ru.capital.idle.ui.HintCard
import ru.capital.idle.ui.PrestigeButton
import ru.capital.idle.ui.ProfileTabsRow
import ru.capital.idle.ui.SummaryCellsRow

/**
 * Находки осмотра вёрстки (PR #6), которые воспроизводятся при системном шрифте 1.5.
 *
 * Каждый случай снимается дважды — обычным шрифтом и увеличенным. Обычный нужен не меньше
 * увеличенного: правки не должны менять то, что игрок видит по умолчанию.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class LargeFontLayoutScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // ===================== находка 1: подпись тира наезжает на номер карты =====================

    private fun card(fontScale: Float) {
        compose.captureOnBackground(
            if (fontScale == 1f) "main_card_aurum" else "main_card_aurum_large_font",
            fontScale = fontScale
        ) {
            CreditCard(
                money = 251_000_000_000_000.0,
                incomePerDay = 413_000_000_000.0,
                reward = 1_000_000.0,
                currency = Currency.RUB,
                playerName = "Владимир",
                tier = CardTier.entries.last(),
                onTap = {}
            )
        }
    }

    @Test
    fun `карта высшего тира`() = card(1f)

    @Test
    fun `карта высшего тира при крупном шрифте`() = card(Screenshots.LARGE_FONT)

    /**
     * Имя игрока задаёт ширину левой колонки, когда оно длиннее номера: в диалоге
     * разрешено 18 знаков. Подпись тира не должна из-за этого обрезаться.
     */
    @Test
    fun `карта с самым длинным именем игрока`() {
        compose.captureOnBackground("main_card_long_name") {
            CreditCard(
                money = 251_000_000_000_000.0, incomePerDay = 413_000_000_000.0,
                reward = 1_000_000.0, currency = Currency.RUB,
                playerName = "Мстислав-Радомир", tier = CardTier.entries.last(), onTap = {}
            )
        }
    }

    // ===================== находка 2: дата в шапке рвётся =====================

    private fun header(fontScale: Float) {
        compose.captureOnBackground(
            if (fontScale == 1f) "main_header" else "main_header_large_font",
            fontScale = fontScale
        ) {
            CapitalHeader(dateText = "28.02.36 · 19:00", currencyCode = "RUB", bullionText = "13 228")
        }
    }

    @Test
    fun `шапка главного экрана`() = header(1f)

    @Test
    fun `шапка главного экрана при крупном шрифте`() = header(Screenshots.LARGE_FONT)

    /**
     * Ближайший системный шаг увеличения после обычного. Запаса ширины у даты около 10dp,
     * поэтому уже здесь она должна уходить на свою строку, а не обрезаться.
     */
    @Test
    fun `шапка главного экрана на ближайшем шаге увеличения`() {
        compose.captureOnBackground("main_header_font_115", fontScale = 1.15f) {
            CapitalHeader(dateText = "28.02.36 · 19:00", currencyCode = "RUB", bullionText = "13 228")
        }
    }

    // ===================== находка 3: «РЕПУТАЦИЯ» переносит букву =====================

    private fun summary(fontScale: Float) {
        compose.captureOnBackground(
            if (fontScale == 1f) "main_summary_cells" else "main_summary_cells_large_font",
            fontScale = fontScale
        ) {
            SummaryCellsRow(status = 1416, reputation = 100, worthText = "₽ 251T")
        }
    }

    @Test
    fun `сводка главного экрана`() = summary(1f)

    @Test
    fun `сводка главного экрана при крупном шрифте`() = summary(Screenshots.LARGE_FONT)

    // ===================== находка 5: подписи вкладок профиля обрезаются =====================

    @Test
    fun `вкладки профиля при крупном шрифте`() {
        compose.captureOnBackground("profile_tabs_large_font", fontScale = Screenshots.LARGE_FONT) {
            ProfileTabsRow(selected = 0, onSelect = {})
        }
    }

    // ===================== находки 8 и 9: престиж =====================

    private fun hint(fontScale: Float) {
        compose.captureOnBackground(
            if (fontScale == 1f) "prestige_hint" else "prestige_hint_large_font",
            fontScale = fontScale
        ) {
            Column(Modifier.fillMaxWidth()) {
                HintCard(state = GameState(), hintId = "pres", onDismiss = {})
            }
        }
    }

    @Test
    fun `подсказка престижа`() = hint(1f)

    @Test
    fun `подсказка престижа при крупном шрифте`() = hint(Screenshots.LARGE_FONT)

    private fun prestigeButton(fontScale: Float) {
        compose.captureOnBackground(
            if (fontScale == 1f) "prestige_button" else "prestige_button_large_font",
            fontScale = fontScale
        ) {
            PrestigeButton(canPrestige = true, gain = 13_228L)
        }
    }

    @Test
    fun `кнопка перерождения`() = prestigeButton(1f)

    @Test
    fun `кнопка перерождения при крупном шрифте`() = prestigeButton(Screenshots.LARGE_FONT)

    // ===================== находка 10: подписи распорядка сходятся =====================

    private fun dayPlan(fontScale: Float) {
        compose.captureOnBackground(
            if (fontScale == 1f) "main_day_plan" else "main_day_plan_large_font",
            fontScale = fontScale
        ) {
            // худший случай из отчёта: есть и часы бизнеса, и бонус транспорта
            DayPlanLabels(bizH = 16, studyH = 12, carBonus = 3)
        }
    }

    @Test
    fun `подписи распорядка дня`() = dayPlan(1f)

    @Test
    fun `подписи распорядка дня при крупном шрифте`() = dayPlan(Screenshots.LARGE_FONT)
}
