package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO
import ru.capital.idle.core.game.Asset
import ru.capital.idle.core.game.AutoInvest
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Jobs
import ru.capital.idle.core.game.Lifestyle
import ru.capital.idle.ui.AutoInvestCard
import ru.capital.idle.BottomBar
import ru.capital.idle.ui.JobCard
import ru.capital.idle.ui.LifeItemCard
import ru.capital.idle.ui.ThemeSheet
import ru.capital.idle.ui.theme.AppTheme
import ru.capital.idle.ui.theme.Bg
import ru.capital.idle.ui.theme.GlassTab
import ru.capital.idle.ui.theme.LocalPalette
import ru.capital.idle.ui.theme.MattePalette

/**
 * В новой теме янтарь — чернила, а не краска: выделенное не красится янтарной заливкой.
 *
 * Выделение в новой теме держится на светлой нейтральной поверхности (#26272B), как у активного
 * подраздела в переключателях. Исключение ровно одно — пилюля активной вкладки нижней
 * навигации: там янтарная заливка по замыслу.
 *
 * Проверка смотрит на пиксели, а не на исходник: важно то, что видно на экране. Ищется самый
 * длинный сплошной отрезок янтаря в строке пикселей — так заливка отличается от всего
 * остального. Штрих буквы — считаные пиксели; круг иконки — 40dp; пилюля навигации — 38dp;
 * а выделенная строка или кнопка залита во всю свою ширину, это сотня dp и больше. Отсюда
 * порог [SELECTION_FILL_DP]: он шире любого круга и любой пилюли, но втрое уже самой узкой
 * выделяемой кнопки.
 *
 * Что янтарной заливкой остаётся намеренно и в набор не входит:
 * — круги иконок (у денег и у купленного имущества они янтарные — так задуманы значки);
 * — кнопки действия («Учиться», «СМОТРЕТЬ», цена покупки): это главная кнопка, одно из мест,
 *   где янтарь по правилам новой темы допустим.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE_ENDLESS)
class AmberFillTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        /** Янтарь новой темы. */
        val AMBER = MattePalette.money

        /**
         * Сплошной янтарь такой ширины — это уже залитая поверхность.
         * Шире круга иконки (40dp) и пилюли навигации (38dp), уже самой узкой кнопки выбора.
         */
        const val SELECTION_FILL_DP = 60

        /** Ширина янтарной пилюли активной вкладки — по ней проверяется, что мерило живо. */
        const val NAV_PILL_DP = 38
    }

    private val rich = GameState(
        money = 5_000_000.0, jobId = "seller", reputation = 40.0,
        eduDone = setOf("school", "sales", "acc", "uni"),
        autoInvestOn = true, autoInvestReserve = 10_000.0
    )

    /**
     * Самый длинный сплошной отрезок янтаря в одной строке пикселей.
     *
     * Снимок пишется во временный файл и читается обратно: так же, как это делают
     * скриншот-тесты. Эталоном он не становится и в репозиторий не попадает.
     */
    private fun longestAmberRun(name: String): Int {
        val path = "build/tmp/amber-fill/$name.png"
        // снимок пишется всегда, какой бы задачей ни шёл прогон: это рабочий файл, а не эталон
        compose.onRoot().captureRoboImage(
            filePath = path,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        )
        val img = ImageIO.read(File(path))
        var longest = 0
        for (y in 0 until img.height) {
            var run = 0
            for (x in 0 until img.width) {
                if (img.getRGB(x, y) and 0xFFFFFF == amberRgb) {
                    run++
                    if (run > longest) longest = run
                } else run = 0
            }
        }
        return longest
    }

    /** Янтарь новой темы числом, как его пишет PNG. */
    private val amberRgb: Int = (AMBER.value shr 32).toInt() and 0xFFFFFF

    private fun matte(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides AppTheme.MATTE.palette) {
                Box(Modifier.fillMaxWidth().background(Bg).padding(10.dp)) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun fillRunDp(name: String): Float = longestAmberRun(name) / compose.density.density

    // ===================== выделение =====================

    @Test
    fun `выбранный инструмент автовклада не залит янтарём`() {
        matte {
            AutoInvestCard(
                on = true, target = Asset.BONDS, unlocked = AutoInvest.available(rich),
                reserve = rich.autoInvestReserve, amount = AutoInvest.amount(rich), cur = Currency.USD
            )
        }
        assertNoAmberFill("выбор инструмента", "picker")
    }

    @Test
    fun `текущая вакансия не залита янтарём`() {
        matte {
            JobCard(rich, Jobs.all.first { it.id == "seller" }, Currency.USD, onClick = {}, onQuit = {})
        }
        assertNoAmberFill("текущая вакансия", "job")
    }

    @Test
    fun `купленное жильё не залито янтарём`() {
        matte {
            LifeItemCard(
                item = Lifestyle.home.items[3], owned = true, isBase = false, canBuy = false,
                cur = Currency.USD, onBuy = {}, onSell = {}
            )
        }
        assertNoAmberFill("купленное жильё", "home")
    }

    @Test
    fun `активная вкладка подраздела не залита янтарём`() {
        matte {
            Row(Modifier.fillMaxWidth()) {
                GlassTab("Курсы", on = true, locked = false, modifier = Modifier.weight(1f)) {}
                GlassTab("Окружение", on = false, locked = false, modifier = Modifier.weight(1f)) {}
            }
        }
        assertNoAmberFill("активная вкладка подраздела", "tab")
    }

    @Test
    fun `выбранная тема в оформлении не залита янтарём`() {
        matte { ThemeSheet(current = AppTheme.MATTE.id, onPick = {}, onDismiss = {}) }
        assertNoAmberFill("выбранная тема", "theme")
    }

    private fun assertNoAmberFill(where: String, name: String) {
        val run = fillRunDp(name)
        assertTrue(
            "$where: янтарная заливка шириной ${run}dp — выделение красится цветом, а не поверхностью",
            run < SELECTION_FILL_DP
        )
    }

    // ===================== единственное исключение =====================

    /**
     * И заодно проверка, что мерило не сломано: если бы янтарь перестал находиться вовсе,
     * все проверки выше проходили бы вхолостую.
     */
    @Test
    fun `активная вкладка нижней навигации залита янтарём`() {
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides AppTheme.MATTE.palette) {
                Box(Modifier.fillMaxWidth().background(Bg)) {
                    BottomBar(state = rich, selected = "main", onSelect = {})
                }
            }
        }
        compose.waitForIdle()
        val run = fillRunDp("nav")
        assertTrue(
            "пилюля активной вкладки перестала быть янтарной: самый длинный отрезок ${run}dp",
            run in (NAV_PILL_DP - 4f)..(NAV_PILL_DP + 4f)
        )
    }
}
