package ru.capital.idle.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.BottomBar
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.*
import ru.capital.idle.ui.theme.AppIcon
import ru.capital.idle.ui.theme.AppIconBadge
import ru.capital.idle.ui.theme.AppTheme
import ru.capital.idle.ui.theme.Gold
import ru.capital.idle.ui.theme.GroupCard
import ru.capital.idle.ui.theme.Modern
import ru.capital.idle.ui.theme.Mute
import ru.capital.idle.ui.theme.RowSeparator
import ru.capital.idle.ui.theme.ThemeIds

/**
 * Матовая тема на всех экранах.
 *
 * Снимаются те же чистые блоки, из которых собраны экраны игры, — по набору на экран
 * и на каждую подвкладку профиля, в обычном и увеличенном (1.5) системном шрифте.
 * Проверяется именно новое оформление: плотные слои без обводок, разведённые акценты
 * и векторные иконки вместо эмодзи.
 *
 * Старую тему эти снимки не трогают: её эталоны лежат в остальных тестах и не менялись.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class MatteThemeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) =
        compose.captureOnBackground(name, fontScale = fontScale, theme = AppTheme.MATTE) {
            Column(Modifier.fillMaxWidth()) { content() }
        }

    @Composable
    private fun gap() = Spacer(Modifier.height(8.dp))

    // ===================== данные =====================

    private val cur = Currency.USD

    /** Состояние в середине игры: есть работа, предприятия, вклады и статус. */
    private val rich: GameState = GameMath.withDayShown(
        GameMath.withPressure(
            GameState(
                money = 188_935_178.0, bizH = 6, workH = 8, sleepH = 8,
                gameHours = 24.0 * 220,
                jobId = Jobs.all[1].id,
                eduDone = setOf("school", "acc", "uni"),
                reputation = 44.0,
                enterprises = MutableList(Industries.count) { emptyList<Enterprise>() }.also {
                    it[0] = listOf(
                        Enterprise(level = 3, name = "Магазин у дома",
                            invested = 54_925.0, earned = 812_400.0),
                        Enterprise(level = 1, name = "Лавка", managerOrdinal = 1,
                            invested = 12_000.0, earned = 90_000.0, salaryPaid = 20_000.0)
                    )
                },
                investValues = List(Investments.COUNT) { 5_000_000.0 },
                investCosts = List(Investments.COUNT) { 4_000_000.0 },
                autoInvestOn = true, autoInvestReserve = 10_000.0,
                collectibles = mapOf(Collectibles.all[0].id to 120_000.0),
                peakNetWorth = 2e9, milestonesClaimed = 4, bullion = 128L,
                statAllTimeEarned = 4_512_900_000.0, statTaps = 9_120L,
                playerName = "Иван Разумовский", themeId = ThemeIds.MATTE
            )
        )
    )

    // ===================== главный =====================

    @Composable
    private fun mainPanel() {
        CapitalHeader(dateText = "28.02.36 · 19:00", currencyCode = "USD", bullionText = "128")
        gap()
        SummaryCellsRow(status = 312, reputation = 44, worthText = "$ 999 999 999")
        gap()
        MarketBar(rich)
        gap()
        PressureSlot(pressure = 0.24, reputation = 44.0)
        ScheduleBlock(rich) { _, _, _ -> }
        gap()
        GroupCard {
            Jobs.all.forEachIndexed { i, job ->
                JobCard(rich, job, cur, onClick = {}, onQuit = {})
                RowSeparator(6.dp, last = i == Jobs.all.lastIndex)
            }
        }
        gap()
        GroupCard {
            Industries.all.forEachIndexed { i, ind ->
                CategoryCard(rich, i, ind, cur) {}
                RowSeparator(6.dp, last = i == Industries.all.lastIndex)
            }
        }
        gap()
        EnterpriseCard(rich, Industries.all[0], 0, 0, rich.enterprises[0][0], cur, {}, {}, {})
    }

    @Test
    fun `главный экран`() = shot("matte_main") { mainPanel() }

    @Test
    fun `главный экран при крупном шрифте`() =
        shot("matte_main_large_font", Screenshots.LARGE_FONT) { mainPanel() }

    /** Карта выглядит одинаково в обеих темах — её цвета от палитры не зависят. */
    @Composable
    private fun cardPanel() {
        CardFace(
            money = 188_935_178.0, incomePerDay = 127_588_552.0, currency = cur,
            playerName = "Иван Разумовский", tier = CardTier.entries[2]
        )
    }

    @Test
    fun `карта в матовой теме`() = shot("matte_card") { cardPanel() }

    @Test
    fun `карта в матовой теме при крупном шрифте`() =
        shot("matte_card_large_font", Screenshots.LARGE_FONT) { cardPanel() }

    // ===================== развитие =====================

    @Composable
    private fun coursesPanel() {
        val branch = Education.branches[0]
        HintCard(GameState(), "inv") {}
        GroupCard {
            Text(branch.title, color = Gold, fontSize = 11.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Modern.cardPadH, top = 8.dp))
            gap()
            val shown = branch.courses.take(3)
            shown.forEachIndexed { i, c ->
                CourseCard(
                    if (i == 1) rich.copy(studyingId = c.id, studyProgress = 40.0) else rich,
                    c, cur
                ) {}
                RowSeparator(6.dp, last = i == shown.lastIndex)
            }
        }
    }

    @Test
    fun `развитие · курсы`() = shot("matte_dev_courses") { coursesPanel() }

    @Test
    fun `развитие · курсы при крупном шрифте`() =
        shot("matte_dev_courses_large_font", Screenshots.LARGE_FONT) { coursesPanel() }

    @Composable
    private fun networkPanel() {
        GroupCard {
            val shown = Network.all
            shown.forEachIndexed { i, item ->
                NetworkItemCard(
                    title = item.title, info = item.info,
                    owned = i == 0, canBuy = i == 1,
                    costText = GameMath.formatMoney(item.cost, cur), onBuy = {}
                )
                RowSeparator(8.dp, last = i == shown.lastIndex)
            }
        }
    }

    @Test
    fun `развитие · окружение`() = shot("matte_dev_net") { networkPanel() }

    @Test
    fun `развитие · окружение при крупном шрифте`() =
        shot("matte_dev_net_large_font", Screenshots.LARGE_FONT) { networkPanel() }

    // ===================== инвест =====================

    @Composable
    private fun investPanel() {
        AutoInvestCard(
            on = true, target = AutoInvest.target(rich), unlocked = AutoInvest.available(rich),
            reserve = rich.autoInvestReserve, amount = AutoInvest.amount(rich),
            cur = cur
        )
        gap()
        PassiveCard(rich, Asset.entries[0], cur, {}, {}, {})
        gap()
        StockCard(
            state = rich, index = 0, stock = Exchange.stocks[0], cur = cur,
            history = List(40) { 100.0 + it * 1.7 }, eventDir = 1, onBuy = {}, onSell = {}
        )
    }

    @Test
    fun `инвест`() = shot("matte_inv") { investPanel() }

    @Test
    fun `инвест при крупном шрифте`() =
        shot("matte_inv_large_font", Screenshots.LARGE_FONT) { investPanel() }

    // ===================== мир =====================

    @Composable
    private fun rankPanel() {
        RankRow(RankRowData(1L, "Иван Разумовский", 999_999_999.0, "", true, true), cur)
        RankRow(RankRowData(2L, "Bernard Arnault", 233_000_000_000.0, "LVMH", true, false, 1.4), cur)
        RankRow(RankRowData(3L, "Elon Musk", 195_000_000_000.0, "Tesla", true, false, -0.8), cur)
    }

    @Composable
    private fun goalsPanel() {
        GroupCard {
            val shown = Milestones.all.take(4)
            shown.forEachIndexed { i, m ->
                MilestoneRow(
                    name = m.name, value = GameMath.formatMoney(m.thresholdUsd, cur),
                    reward = m.rewardBullion, done = i == 0, current = i == 1,
                    last = i == shown.lastIndex
                )
            }
        }
    }

    @Composable
    private fun prestigePanel() {
        PrestigeButton(canPrestige = true, gain = 128L)
        gap()
        PrestigeButton(canPrestige = false, gain = 0L)
    }

    @Test
    fun `мир · рейтинг`() = shot("matte_world_rank") { rankPanel() }

    @Test
    fun `мир · рейтинг при крупном шрифте`() =
        shot("matte_world_rank_large_font", Screenshots.LARGE_FONT) { rankPanel() }

    @Test
    fun `мир · цели`() = shot("matte_world_goals") { goalsPanel() }

    @Test
    fun `мир · цели при крупном шрифте`() =
        shot("matte_world_goals_large_font", Screenshots.LARGE_FONT) { goalsPanel() }

    @Test
    fun `мир · престиж`() = shot("matte_world_pres") { prestigePanel() }

    @Test
    fun `мир · престиж при крупном шрифте`() =
        shot("matte_world_pres_large_font", Screenshots.LARGE_FONT) { prestigePanel() }

    // ===================== профиль =====================

    @Composable
    private fun profileHeader() {
        MoneyCellsRow(
            moneyStr = GameMath.formatMoney(188_935_178.0, cur),
            upkeepStr = GameMath.formatMoney(2_400_000.0, cur),
            netStr = "+" + GameMath.formatMoney(127_588_552.0, cur),
            inDebt = false, netPositive = true
        )
        gap()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShowcaseSlot(Lifestyle.home.items[3], Modifier.weight(1f))
            ShowcaseSlot(Lifestyle.car.items[3], Modifier.weight(1f))
            ShowcaseSlot(Lifestyle.tech.items[2], Modifier.weight(1f))
        }
        gap()
        ProfileTabsRow(selected = 0) {}
    }

    @Composable
    private fun profileItemsPanel() {
        profileHeader()
        gap()
        GroupCard {
            Text("ЖИЛЬЁ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Modern.cardPadH, top = 8.dp))
            gap()
            LifeItemCard(Lifestyle.home.items[3], owned = true, isBase = false, canBuy = false,
                cur = cur, onBuy = {}, onSell = {})
            RowSeparator(6.dp)
            LifeItemCard(Lifestyle.home.items[4], owned = false, isBase = false, canBuy = true,
                cur = cur, onBuy = {}, onSell = {})
            RowSeparator(6.dp, last = true)
        }
    }

    @Test
    fun `профиль · имущество`() = shot("matte_prof_items") { profileItemsPanel() }

    @Test
    fun `профиль · имущество при крупном шрифте`() =
        shot("matte_prof_items_large_font", Screenshots.LARGE_FONT) { profileItemsPanel() }

    @Composable
    private fun profileRestPanel() {
        ProfileTabsRow(selected = 1) {}
        gap()
        GroupCard {
            Lifestyle.experiences.forEachIndexed { i, exp ->
                ExperienceCard(exp, done = i == 0, canBuy = i == 1, cur = cur) {}
                RowSeparator(6.dp, last = i == Lifestyle.experiences.lastIndex)
            }
        }
    }

    @Test
    fun `профиль · отдых`() = shot("matte_prof_rest") { profileRestPanel() }

    @Test
    fun `профиль · отдых при крупном шрифте`() =
        shot("matte_prof_rest_large_font", Screenshots.LARGE_FONT) { profileRestPanel() }

    @Composable
    private fun profileCollectionPanel() {
        ProfileTabsRow(selected = 4) {}
        gap()
        CollectionSummary(day = 220, owned = 3, value = 4_120_000.0, profit = 812_000.0, cur = cur)
        gap()
        CollectionSetsBlock(
            rows = Collectibles.sets.take(2).mapIndexed { i, s ->
                SetProgress(s, if (i == 0) Collectibles.sizeOf(s) else 1)
            }
        )
        gap()
        GroupCard {
            Collectibles.all.take(3).forEachIndexed { i, c ->
                CollectibleCard(
                    item = c, price = 1_240_000.0, owned = i == 0,
                    paid = 900_000.0, profit = 340_000.0, canBuy = i == 1, cur = cur,
                    onBuy = {}, onSell = {}
                )
                RowSeparator(6.dp, last = i == 2)
            }
        }
    }

    @Test
    fun `профиль · коллекция`() = shot("matte_prof_collection") { profileCollectionPanel() }

    @Test
    fun `профиль · коллекция при крупном шрифте`() =
        shot("matte_prof_collection_large_font", Screenshots.LARGE_FONT) { profileCollectionPanel() }

    @Composable
    private fun profileHistoryPanel() {
        ProfileTabsRow(selected = 3) {}
        gap()
        HistoryTab(rich, cur)
    }

    @Test
    fun `профиль · история`() = shot("matte_prof_history") { profileHistoryPanel() }

    @Test
    fun `профиль · история при крупном шрифте`() =
        shot("matte_prof_history_large_font", Screenshots.LARGE_FONT) { profileHistoryPanel() }

    // ===================== нижняя навигация, лист тем и иконки =====================

    @Test
    fun `нижняя навигация`() = shot("matte_bottom_bar") {
        BottomBar(state = rich, selected = "main") {}
        BottomBar(state = rich, selected = "prof") {}
        BottomBar(state = GameState(), selected = "main") {}
    }

    @Test
    fun `нижняя навигация при крупном шрифте`() =
        shot("matte_bottom_bar_large_font", Screenshots.LARGE_FONT) {
            BottomBar(state = rich, selected = "main") {}
            BottomBar(state = GameState(), selected = "main") {}
        }

    @Test
    fun `лист выбора оформления`() = shot("matte_theme_sheet") {
        ThemeSheet(current = ThemeIds.MATTE, onPick = {}, onDismiss = {})
    }

    @Test
    fun `лист выбора оформления в старой теме`() =
        compose.captureOnBackground("glass_theme_sheet", theme = AppTheme.GLASS) {
            ThemeSheet(current = ThemeIds.GLASS, onPick = {}, onDismiss = {})
        }

    /**
     * Та же сводка в старой теме — для сверки, влезает ли предельный капитал.
     *
     * Иконка в плитке отнимает ширину у числа, а деньги до миллиарда показываются целиком
     * (правило полноты чисел, CLAUDE.md). Снимок нужен, чтобы отличить «обрезала иконка»
     * от «не влезало и раньше».
     */
    @Test
    fun `сводка с предельным капиталом в старой теме`() =
        compose.captureOnBackground("glass_summary_max_large_font",
            fontScale = Screenshots.LARGE_FONT, theme = AppTheme.GLASS) {
            Column(Modifier.fillMaxWidth()) {
                SummaryCellsRow(status = 312, reputation = 44, worthText = "$ 999 999 999")
            }
        }

    /** Весь набор иконок разом: если какая-то нарисована пустой, это видно сразу. */
    @Test
    fun `набор иконок`() = shot("matte_icons") {
        AppIcon.entries.toList().chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { AppIconBadge(it, tint = Gold, diameter = 40.dp) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
