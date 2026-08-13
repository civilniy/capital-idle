package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.BottomBar
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.*
import ru.capital.idle.ui.theme.AppTheme
import ru.capital.idle.ui.theme.Bg
import ru.capital.idle.ui.theme.LocalPalette

/**
 * Тема меняет цвета и значки — и только их.
 *
 * Каждый блок кладётся в помеченный прямоугольник, снимаются его положение и размер,
 * затем тема переключается прямо в живой композиции и замер повторяется. Любое смещение
 * по вертикали или изменение размера — ошибка: об этом сказано в задаче прямым текстом.
 *
 * Особенно важны места, где эмодзи заменяется векторной иконкой: иконка встаёт в тот же
 * прямоугольник, который занимало эмодзи (невидимое эмодзи-мерило внутри `IconSlot`),
 * поэтому высота строки не должна зависеть от темы — в том числе при крупном шрифте.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class ThemeLayoutParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var theme by mutableStateOf(AppTheme.GLASS)

    private val cur = Currency.USD

    private val state: GameState = GameMath.withDayShown(
        GameMath.withPressure(
            GameState(
                money = 188_935_178.0, bizH = 6, workH = 8, gameHours = 24.0 * 220,
                jobId = Jobs.all[1].id, eduDone = setOf("school", "acc", "uni"),
                reputation = 44.0,
                enterprises = MutableList(Industries.count) { emptyList<Enterprise>() }.also {
                    it[0] = listOf(Enterprise(level = 3, name = "Магазин у дома",
                        invested = 54_925.0, earned = 812_400.0))
                },
                investValues = List(Investments.COUNT) { 5_000_000.0 },
                investCosts = List(Investments.COUNT) { 4_000_000.0 },
                autoInvestOn = true, autoInvestReserve = 10_000.0,
                peakNetWorth = 2e9, milestonesClaimed = 4, bullion = 128L,
                playerName = "Иван Разумовский"
            )
        )
    )

    /** Блоки со всех экранов; ключ — метка, по которой снимается прямоугольник. */
    private val blocks: List<Pair<String, @Composable () -> Unit>> = listOf(
        "header" to { CapitalHeader("28.02.36 · 19:00", "USD", "128") },
        "summary" to { SummaryCellsRow(312, 44, "$ 999 999 999") },
        "market" to { MarketBar(state) },
        "pressure" to { PressureSlot(0.24, 44.0) },
        "schedule" to { ScheduleBlock(state) { _, _, _ -> } },
        "job" to { JobCard(state, Jobs.all[1], cur, {}, {}) },
        "category" to { CategoryCard(state, 0, Industries.all[0], cur) {} },
        "enterprise" to {
            EnterpriseCard(state, Industries.all[0], 0, 0, state.enterprises[0][0], cur, {}, {}, {})
        },
        "card" to {
            CardFace(188_935_178.0, 127_588_552.0, cur, "Иван Разумовский", CardTier.entries[2])
        },
        "course" to { CourseCard(state, Education.branches[0].courses[0], cur) {} },
        "network" to {
            NetworkItemCard(Network.all[0].title, Network.all[0].info, true, false, "$ 1 000") {}
        },
        "autoinvest" to {
            AutoInvestCard(
                on = true, target = AutoInvest.target(state), unlocked = AutoInvest.available(state),
                reserve = state.autoInvestReserve, amount = AutoInvest.amount(state),
                blocked = AutoInvest.blockedReason(state), cur = cur
            )
        },
        "passive" to { PassiveCard(state, Asset.entries[0], cur, {}, {}, {}) },
        "stock" to {
            StockCard(state, 0, Exchange.stocks[0], cur, List(40) { 100.0 + it }, 1, {}, {})
        },
        "rank" to { RankRow(RankRowData(2L, "Bernard Arnault", 2.33e11, "LVMH", true, false, 1.4), cur) },
        "milestone" to { MilestoneRow("Первый миллион", "$ 1 000 000", 5L, done = true, current = false) },
        "prestige" to { PrestigeButton(true, 128L) },
        "profile_tabs" to { ProfileTabsRow(0) {} },
        "money_cells" to { MoneyCellsRow("$ 188 935 178", "$ 2 400 000", "+$ 127 588 552", false, true) },
        "showcase" to {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShowcaseSlot(Lifestyle.home.items[3], Modifier.weight(1f))
                ShowcaseSlot(Lifestyle.car.items[3], Modifier.weight(1f))
                ShowcaseSlot(Lifestyle.tech.items[2], Modifier.weight(1f))
            }
        },
        "life_item" to {
            LifeItemCard(Lifestyle.home.items[3], owned = true, isBase = false, canBuy = false,
                cur = cur, onBuy = {}, onSell = {})
        },
        "experience" to { ExperienceCard(Lifestyle.experiences[0], true, false, cur) {} },
        "collection" to { CollectionSummary(220, 3, 4_120_000.0, 812_000.0, cur) },
        "collectible" to {
            CollectibleCard(Collectibles.all[0], 1_240_000.0, owned = true, paid = 900_000.0,
                profit = 340_000.0, canBuy = false, cur = cur, onBuy = {}, onSell = {})
        },
        "hint" to { HintCard(GameState(), "inv") {} },
        "bottom_bar" to { BottomBar(state, "main") {} }
    )

    private fun measure(): Map<String, String> = blocks.associate { (tag, _) ->
        val r = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        tag to "left=${r.left} top=${r.top} width=${r.width} height=${r.height}"
    }

    private fun check(fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
                LocalPalette provides theme.palette
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Bg)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    blocks.forEach { (tag, content) ->
                        Box(Modifier.testTag(tag)) { content() }
                    }
                }
            }
        }

        val glass = measure()
        compose.runOnUiThread { theme = AppTheme.MATTE }
        compose.waitForIdle()
        val matte = measure()

        glass.keys.forEach { tag ->
            assertEquals("блок «$tag» сдвинулся или изменил размер", glass[tag], matte[tag])
        }
    }

    @Test
    fun `положение и размер блоков не зависят от темы`() = check(1f)

    @Test
    fun `положение и размер блоков не зависят от темы при крупном шрифте`() =
        check(Screenshots.LARGE_FONT)
}
