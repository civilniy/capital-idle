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
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.Enterprise
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Industries
import ru.capital.idle.core.game.Manager
import ru.capital.idle.ui.EnterpriseCard

/**
 * Карточка предприятия со строкой окупаемости.
 *
 * Строка добавляет карточке две-три строки текста под кнопками, и главный риск здесь —
 * длина: суммы показываются целиком до миллиарда (правило 1 в CLAUDE.md), то есть самое
 * длинное число — `999 999 999`, а в рублях оно ещё длиннее. Плюс пометка о неполном учёте.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class EnterprisePaybackScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val trade = Industries.all[0]

    /**
     * Состояние с одним предприятием в торговле. Часы на бизнес заданы с запасом: без них
     * предприятие под личным управлением не приносит ничего, и окупаемость выглядела бы
     * одинаково у всех случаев.
     */
    private fun stateWith(e: Enterprise): GameState {
        val lists = MutableList(Industries.count) { emptyList<Enterprise>() }
        lists[0] = listOf(e)
        // давление элит хранится в состоянии, а не считается на лету: без withPressure
        // у капитала в триллион его бы не было, и выручка на карточке вышла бы завышенной
        return GameMath.withPressure(
            GameState(money = 1e12, bizH = 12, gameHours = 24.0 * 200, enterprises = lists)
        )
    }

    private fun shot(name: String, e: Enterprise, cur: Currency = Currency.USD, fontScale: Float = 1f) {
        compose.captureOnBackground(name, fontScale = fontScale) {
            Column(Modifier.fillMaxWidth()) {
                EnterpriseCard(
                    state = stateWith(e), ind = trade, index = 0, entIndex = 0, e = e, cur = cur,
                    onUpgrade = {}, onManager = {}, onRename = {}
                )
            }
        }
    }

    // ===================== ещё не окупилось =====================

    private val notYet = Enterprise(
        level = 3, name = "Магазин у дома",
        invested = 54_925.0, earned = 12_400.0, salaryPaid = 0.0
    )

    @Test
    fun `не окупилось`() = shot("biz_payback_pending", notYet)

    @Test
    fun `не окупилось при крупном шрифте`() =
        shot("biz_payback_pending_large_font", notYet, fontScale = Screenshots.LARGE_FONT)

    // ===================== окупилось =====================

    private val paidOff = Enterprise(
        level = 3, name = "Магазин у дома",
        invested = 54_925.0, earned = 137_312.0, salaryPaid = 0.0
    )

    @Test
    fun `окупилось`() = shot("biz_payback_done", paidOff)

    @Test
    fun `окупилось при крупном шрифте`() =
        shot("biz_payback_done_large_font", paidOff, fontScale = Screenshots.LARGE_FONT)

    // ===================== не окупается при управляющем =====================

    /** Топ-менеджер за 2200 в день на первой ступени: зарплата больше выручки. */
    private val stalled = Enterprise(
        level = 0, managerOrdinal = Manager.TOP.ordinal, name = "Лоток",
        invested = 200.0, earned = 90.0, salaryPaid = 4_400.0
    )

    @Test
    fun `не окупается при управляющем`() = shot("biz_payback_stalled", stalled)

    @Test
    fun `не окупается при управляющем при крупном шрифте`() =
        shot("biz_payback_stalled_large_font", stalled, fontScale = Screenshots.LARGE_FONT)

    // ===================== длинные суммы =====================

    /**
     * Самое длинное, что вообще показывается целиком: `999 999 999` и там, и там.
     * В рублях к каждому числу добавляется ещё два разряда.
     */
    private val huge = Enterprise(
        level = 8, managerOrdinal = Manager.TOP.ordinal, name = "Ритейл-империя",
        invested = 999_999_999.0, earned = 999_999_998.0, salaryPaid = 0.0
    )

    @Test
    fun `длинные суммы`() = shot("biz_payback_long", huge)

    @Test
    fun `длинные суммы в рублях`() = shot("biz_payback_long_rub", huge, cur = Currency.RUB)

    @Test
    fun `длинные суммы при крупном шрифте`() =
        shot("biz_payback_long_large_font", huge, fontScale = Screenshots.LARGE_FONT)

    // ===================== учёт неполный =====================

    /** Предприятие из сохранения, сделанного до появления учёта: накопители пустые. */
    private val migrated = Enterprise(
        level = 5, name = "Сеть магазинов",
        invested = 0.0, earned = 0.0, salaryPaid = 0.0, statsSinceDay = 128
    )

    @Test
    fun `учёт с текущего дня`() = shot("biz_payback_partial", migrated)

    @Test
    fun `учёт с текущего дня при крупном шрифте`() =
        shot("biz_payback_partial_large_font", migrated, fontScale = Screenshots.LARGE_FONT)

    /** То же предприятие после улучшения: вложения появились, окупаемость снова считается. */
    @Test
    fun `учёт неполный, но вложения уже есть`() = shot(
        "biz_payback_partial_upgraded",
        migrated.copy(level = 6, invested = 15_083_778.0, earned = 2_400_000.0)
    )
}
