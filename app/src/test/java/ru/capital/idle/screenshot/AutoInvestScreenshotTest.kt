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
import ru.capital.idle.core.game.Asset
import ru.capital.idle.core.game.AutoInvest
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameState
import ru.capital.idle.ui.AutoInvestCard
import ru.capital.idle.ui.theme.AppTheme

/**
 * Карточка автовклада на экране «Инвест».
 *
 * Состояний немного, но они разной высоты: выключенный автовклад — две строки, включённый —
 * с выбором инструмента, резервом и итогом. Итог всегда один и тот же — «следующим днём
 * уйдёт», и когда переводить нечего, в нём стоит ноль. Плюс крупный шрифт: суммы в резерве
 * длинные (правило полноты чисел из CLAUDE.md — до миллиарда они показываются целиком),
 * и подписи не должны наезжать друг на друга.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class AutoInvestScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Игрок с открытыми депозитом и облигациями. */
    private fun saver(
        on: Boolean = true,
        reserve: Double = 10_000.0,
        money: Double = 999_999_999.0,
        debt: Double = 0.0,
        edu: Set<String> = setOf("school", "acc")
    ) = GameState(
        money = money, debt = debt, eduDone = edu,
        autoInvestOn = on, autoInvestReserve = reserve
    )

    private fun shot(
        name: String, s: GameState, cur: Currency = Currency.USD, fontScale: Float = 1f,
        theme: AppTheme = AppTheme.GLASS
    ) {
        compose.captureOnBackground(name, fontScale = fontScale, theme = theme) {
            Column(Modifier.fillMaxWidth()) {
                AutoInvestCard(
                    on = s.autoInvestOn,
                    target = AutoInvest.target(s),
                    unlocked = AutoInvest.available(s),
                    reserve = s.autoInvestReserve,
                    amount = AutoInvest.amount(s),
                    cur = cur
                )
            }
        }
    }

    @Test
    fun `автовклад выключен`() = shot("autoinvest_off", saver(on = false))

    @Test
    fun `автовклад выключен при крупном шрифте`() =
        shot("autoinvest_off_large_font", saver(on = false), fontScale = Screenshots.LARGE_FONT)

    @Test
    fun `автовклад включён`() = shot("autoinvest_on", saver())

    @Test
    fun `автовклад включён при крупном шрифте`() =
        shot("autoinvest_on_large_font", saver(), fontScale = Screenshots.LARGE_FONT)

    @Test
    fun `автовклад включён при долге`() = shot("autoinvest_debt", saver(debt = 250_000.0))

    @Test
    fun `автовклад включён при долге и крупном шрифте`() =
        shot("autoinvest_debt_large_font", saver(debt = 250_000.0),
            fontScale = Screenshots.LARGE_FONT)

    // ===================== края =====================

    /** Три инструмента: подписи в ряду теснее всего. */
    @Test
    fun `автовклад со всеми инструментами`() =
        shot("autoinvest_all_assets", saver(edu = setOf("school", "acc", "uni")))

    @Test
    fun `автовклад со всеми инструментами при крупном шрифте`() =
        shot("autoinvest_all_assets_large_font", saver(edu = setOf("school", "acc", "uni")),
            fontScale = Screenshots.LARGE_FONT)

    /** Самый длинный резерв и самая длинная сумма — в рублях они ещё на два разряда длиннее. */
    @Test
    fun `автовклад с предельными суммами в рублях`() =
        shot("autoinvest_long_rub", saver(reserve = 1e9, money = 1e12), Currency.RUB)

    /** На карте меньше резерва — переводить нечего, в карточке ноль. */
    @Test
    fun `автовклад когда на карте меньше резерва`() =
        shot("autoinvest_below_reserve", saver(reserve = 1e9, money = 1_000.0))

    /**
     * Две стороны одного игрового дня: деньги набежали — и деньги только что списались.
     *
     * Второй снимок — и есть блок при нулевом переводе: на карте ровно резерв, переводить
     * нечего. Различаются снимки только числом в итоге; ни предупреждения, ни сдвига
     * содержимого между ними нет. Высота сверяется числом в `AutoInvestHeightTest`.
     */
    @Test
    fun `день, когда деньги набежали`() = shot("autoinvest_day_full", saver(money = 30_000.0))

    @Test
    fun `день, когда деньги только что списались`() =
        shot("autoinvest_day_drained", saver(money = 10_000.0))

    @Test
    fun `день, когда деньги набежали, при крупном шрифте`() =
        shot("autoinvest_day_full_large_font", saver(money = 30_000.0),
            fontScale = Screenshots.LARGE_FONT)

    @Test
    fun `день, когда деньги только что списались, при крупном шрифте`() =
        shot("autoinvest_day_drained_large_font", saver(money = 10_000.0),
            fontScale = Screenshots.LARGE_FONT)

    // ===================== выбор инструмента =====================

    /**
     * Ряд из трёх кнопок: выбран каждый из инструментов и случай, когда «Недвижимость»
     * ещё не открыта образованием — кнопка на месте, но приглушена.
     *
     * Снято в обеих темах: выделение у них разное. «Стекло» подсвечивает выбранный янтарём,
     * как и раньше; новая тема — светло-серой поверхностью с белой подписью, потому что
     * янтарём в ней поверхности не красятся. Крупный шрифт здесь важнее обычного:
     * «Недвижимость» — самое длинное название в самой узкой кнопке.
     */
    private fun pick(name: String, asset: Asset, locked: Boolean, fs: Float, theme: AppTheme) =
        shot(
            name,
            saver(edu = if (locked) setOf("school", "acc") else setOf("school", "acc", "uni"))
                .copy(autoInvestAsset = asset.ordinal),
            fontScale = fs, theme = theme
        )

    @Test
    fun `выбран депозит`() =
        pick("autoinvest_pick_deposit", Asset.DEPOSIT, locked = false, fs = 1f, theme = AppTheme.GLASS)

    @Test
    fun `выбраны облигации`() =
        pick("autoinvest_pick_bonds", Asset.BONDS, locked = false, fs = 1f, theme = AppTheme.GLASS)

    @Test
    fun `выбрана недвижимость`() =
        pick("autoinvest_pick_realty", Asset.REALTY, locked = false, fs = 1f, theme = AppTheme.GLASS)

    @Test
    fun `недвижимость ещё не открыта`() =
        pick("autoinvest_pick_locked", Asset.BONDS, locked = true, fs = 1f, theme = AppTheme.GLASS)

    @Test
    fun `выбран депозит при крупном шрифте`() =
        pick("autoinvest_pick_deposit_large_font", Asset.DEPOSIT, locked = false, fs = Screenshots.LARGE_FONT, theme = AppTheme.GLASS)

    @Test
    fun `выбраны облигации при крупном шрифте`() =
        pick("autoinvest_pick_bonds_large_font", Asset.BONDS, locked = false, fs = Screenshots.LARGE_FONT, theme = AppTheme.GLASS)

    @Test
    fun `выбрана недвижимость при крупном шрифте`() =
        pick("autoinvest_pick_realty_large_font", Asset.REALTY, locked = false, fs = Screenshots.LARGE_FONT, theme = AppTheme.GLASS)

    @Test
    fun `недвижимость ещё не открыта при крупном шрифте`() =
        pick("autoinvest_pick_locked_large_font", Asset.BONDS, locked = true, fs = Screenshots.LARGE_FONT, theme = AppTheme.GLASS)

    @Test
    fun `выбран депозит в новой теме`() =
        pick("matte_autoinvest_pick_deposit", Asset.DEPOSIT, locked = false, fs = 1f, theme = AppTheme.MATTE)

    @Test
    fun `выбраны облигации в новой теме`() =
        pick("matte_autoinvest_pick_bonds", Asset.BONDS, locked = false, fs = 1f, theme = AppTheme.MATTE)

    @Test
    fun `выбрана недвижимость в новой теме`() =
        pick("matte_autoinvest_pick_realty", Asset.REALTY, locked = false, fs = 1f, theme = AppTheme.MATTE)

    @Test
    fun `недвижимость ещё не открыта в новой теме`() =
        pick("matte_autoinvest_pick_locked", Asset.BONDS, locked = true, fs = 1f, theme = AppTheme.MATTE)

    @Test
    fun `выбран депозит в новой теме при крупном шрифте`() =
        pick("matte_autoinvest_pick_deposit_large_font", Asset.DEPOSIT, locked = false, fs = Screenshots.LARGE_FONT, theme = AppTheme.MATTE)

    @Test
    fun `выбраны облигации в новой теме при крупном шрифте`() =
        pick("matte_autoinvest_pick_bonds_large_font", Asset.BONDS, locked = false, fs = Screenshots.LARGE_FONT, theme = AppTheme.MATTE)

    @Test
    fun `выбрана недвижимость в новой теме при крупном шрифте`() =
        pick("matte_autoinvest_pick_realty_large_font", Asset.REALTY, locked = false, fs = Screenshots.LARGE_FONT, theme = AppTheme.MATTE)

    @Test
    fun `недвижимость ещё не открыта в новой теме при крупном шрифте`() =
        pick("matte_autoinvest_pick_locked_large_font", Asset.BONDS, locked = true, fs = Screenshots.LARGE_FONT, theme = AppTheme.MATTE)

    /** Инструменты ещё не открыты. */
    @Test
    fun `автовклад без открытых инструментов`() =
        shot("autoinvest_no_assets", saver(edu = setOf("school")))

    /** Закреплён не лучший инструмент: подсветка должна стоять на нём. */
    @Test
    fun `автовклад с закреплённым депозитом`() =
        shot("autoinvest_pinned_deposit",
            saver(edu = setOf("school", "acc", "uni")).copy(autoInvestAsset = Asset.DEPOSIT.ordinal))
}
