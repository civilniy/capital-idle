package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * Тема меняет оформление, но не содержимое.
 *
 * Раньше здесь сверялись координаты и размеры блоков. Теперь новой теме разрешено менять
 * отступы, скругления и размеры — иначе она и выглядела как старая с другими оттенками.
 * Значит сверять нужно другое: **набор и порядок элементов**. Экран должен остаться
 * узнаваемым — те же блоки в той же последовательности.
 *
 * Сравниваются подписи в порядке обхода дерева. Чисто значковые строки (эмодзи, которые
 * в новой теме заменены векторной иконкой) отбрасываются: значок — это оформление,
 * а не содержимое.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class ThemeStructureParityTest {

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

    /** Блоки со всех экранов в том порядке, в каком они стоят в игре. */
    private val blocks: List<@Composable () -> Unit> = listOf(
        { CapitalHeader("28.02.36 · 19:00", "USD", "128") },
        { SummaryCellsRow(312, 44, "$ 999 999 999") },
        { MarketBar(state) },
        { PressureSlot(0.24, 44.0) },
        { ScheduleBlock(state) { _, _, _ -> } },
        { JobCard(state, Jobs.all[1], cur, {}, {}) },
        { CategoryCard(state, 0, Industries.all[0], cur) {} },
        { EnterpriseCard(state, Industries.all[0], 0, 0, state.enterprises[0][0], cur, {}, {}, {}) },
        { CardFace(188_935_178.0, 127_588_552.0, cur, "Иван Разумовский", CardTier.entries[2]) },
        { CourseCard(state, Education.branches[0].courses[0], cur) {} },
        { NetworkItemCard(Network.all[0].title, Network.all[0].info, true, false, "$ 1 000") {} },
        {
            AutoInvestCard(
                on = true, target = AutoInvest.target(state), unlocked = AutoInvest.available(state),
                reserve = state.autoInvestReserve, amount = AutoInvest.amount(state),
                cur = cur
            )
        },
        { PassiveCard(state, Asset.entries[0], cur, {}, {}, {}) },
        { StockCard(state, 0, Exchange.stocks[0], cur, List(40) { 100.0 + it }, 1, {}, {}) },
        { RankRow(RankRowData(2L, "Bernard Arnault", 2.33e11, "LVMH", true, false, 1.4), cur) },
        { MilestoneRow("Первый миллион", "$ 1 000 000", 5L, done = true, current = false) },
        { PrestigeButton(true, 128L) },
        { ProfileTabsRow(0) {} },
        { MoneyCellsRow("$ 188 935 178", "$ 2 400 000", "+$ 127 588 552", false, true) },
        {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShowcaseSlot(Lifestyle.home.items[3], Modifier.weight(1f))
                ShowcaseSlot(Lifestyle.car.items[3], Modifier.weight(1f))
                ShowcaseSlot(Lifestyle.tech.items[2], Modifier.weight(1f))
            }
        },
        {
            LifeItemCard(Lifestyle.home.items[3], owned = true, isBase = false, canBuy = false,
                cur = cur, onBuy = {}, onSell = {})
        },
        { ExperienceCard(Lifestyle.experiences[0], true, false, cur) {} },
        { CollectionSummary(220, 3, 4_120_000.0, 812_000.0, cur) },
        {
            CollectibleCard(Collectibles.all[0], 1_240_000.0, owned = true, paid = 900_000.0,
                profit = 340_000.0, canBuy = false, cur = cur, onBuy = {}, onSell = {})
        },
        { HintCard(GameState(), "inv") {} },
        { BottomBar(state, "main") {} }
    )

    /** Подписи в порядке обхода дерева; чисто значковые строки отброшены. */
    private fun texts(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.children.forEach { walk(it) }
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out.filter { s -> s.any { it.isLetterOrDigit() } }
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
                    blocks.forEach { it() }
                }
            }
        }

        val glass = texts()
        compose.runOnUiThread { theme = AppTheme.MATTE }
        compose.waitForIdle()
        val matte = texts()

        assertEquals(
            "набор и порядок элементов должны совпадать в обеих темах",
            glass.joinToString("\n"), matte.joinToString("\n")
        )
    }

    @Test
    fun `набор и порядок элементов не зависят от темы`() = check(1f)

    @Test
    fun `набор и порядок элементов не зависят от темы при крупном шрифте`() =
        check(Screenshots.LARGE_FONT)
}
