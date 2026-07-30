package ru.capital.idle.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.capital.idle.core.game.GameState
import ru.capital.idle.ui.*

/**
 * Осмотр вёрстки: снимки экранов целиком в разных состояниях игры.
 *
 * Это не регрессионные тесты (те — в Profile/CollectionLayoutScreenshotTest), а материал
 * для просмотра глазами. Картинки складываются в screenshots/audit/.
 *
 * Снимок, который не удалось сделать, не роняет прогон: причина печатается в лог
 * строкой «НЕ УДАЛОСЬ СНЯТЬ», чтобы остальные экраны всё равно записались.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class ScreenAuditTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Снять экран целиком. Автопрокрутка часов Compose выключена: экраны живые,
     * с бегущим временем, и иначе правило не дождалось бы покоя.
     */
    private fun shot(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        runCatching {
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = base.density, fontScale = fontScale)
                ) { content() }
            }
            compose.mainClock.advanceTimeBy(200)
            compose.onRoot().captureRoboImage(
                filePath = "${Screenshots.DIR}/audit/$name.png",
                roborazziOptions = Screenshots.OPTIONS
            )
        }.onFailure { println("НЕ УДАЛОСЬ СНЯТЬ $name: ${it::class.simpleName}: ${it.message}") }
    }

    private fun screen(
        name: String, state: GameState, fontScale: Float = 1f,
        openTab: String? = null,
        content: @Composable (GameViewModel) -> Unit
    ) {
        runCatching {
            val vm = AuditStates.viewModelWith(state)
            compose.mainClock.autoAdvance = false
            compose.setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = base.density, fontScale = fontScale)
                ) { content(vm) }
            }
            compose.mainClock.advanceTimeBy(200)
            if (openTab != null) {
                compose.onNodeWithText(openTab).performClick()
                compose.mainClock.advanceTimeBy(200)
            }
            compose.onRoot().captureRoboImage(
                filePath = "${Screenshots.DIR}/audit/$name.png",
                roborazziOptions = Screenshots.OPTIONS
            )
        }.onFailure { println("НЕ УДАЛОСЬ СНЯТЬ $name: ${it::class.simpleName}: ${it.message}") }
    }

    // ===================== приветствие =====================

    @Test fun `приветствие`() = shot("welcome") { WelcomeScreen(onDone = {}) }

    @Test fun `приветствие крупный шрифт`() =
        shot("welcome_large_font", fontScale = Screenshots.LARGE_FONT) { WelcomeScreen(onDone = {}) }

    // ===================== Капитал (главный) =====================

    @Test fun `главный в начале`() =
        screen("main_early", AuditStates.early) { GameScreen(vm = it) }

    @Test fun `главный в середине`() =
        screen("main_mid", AuditStates.mid) { GameScreen(vm = it) }

    @Test fun `главный в поздней игре`() =
        screen("main_late", AuditStates.late) { GameScreen(vm = it) }

    @Test fun `главный в долгах`() =
        screen("main_debt", AuditStates.debt) { GameScreen(vm = it) }

    @Test fun `главный крупный шрифт`() =
        screen("main_late_large_font", AuditStates.late, fontScale = Screenshots.LARGE_FONT) {
            GameScreen(vm = it)
        }

    // ===================== Развитие =====================

    @Test fun `развитие курсы в начале`() =
        screen("dev_courses_early", AuditStates.early) { DevScreen(vm = it, onLocked = {}) }

    @Test fun `развитие курсы в середине`() =
        screen("dev_courses_mid", AuditStates.mid) { DevScreen(vm = it, onLocked = {}) }

    @Test fun `развитие курсы в поздней игре`() =
        screen("dev_courses_late", AuditStates.late) { DevScreen(vm = it, onLocked = {}) }

    @Test fun `развитие окружение пусто`() =
        screen("dev_network_early", AuditStates.early) {
            DevScreen(vm = it, onLocked = {}, startInner = "net")
        }

    @Test fun `развитие окружение всё куплено`() =
        screen("dev_network_late", AuditStates.late) {
            DevScreen(vm = it, onLocked = {}, startInner = "net")
        }

    @Test fun `развитие крупный шрифт`() =
        screen("dev_courses_large_font", AuditStates.mid, fontScale = Screenshots.LARGE_FONT) {
            DevScreen(vm = it, onLocked = {})
        }

    // ===================== Инвестиции =====================

    @Test fun `инвестиции пусто`() =
        screen("invest_early", AuditStates.early) { InvestScreen(vm = it) }

    @Test fun `инвестиции в середине`() =
        screen("invest_mid", AuditStates.mid) { InvestScreen(vm = it) }

    @Test fun `инвестиции в поздней игре`() =
        screen("invest_late", AuditStates.late) { InvestScreen(vm = it) }

    @Test fun `инвестиции крупный шрифт`() =
        screen("invest_late_large_font", AuditStates.late, fontScale = Screenshots.LARGE_FONT) {
            InvestScreen(vm = it)
        }

    // ===================== Мир =====================

    @Test fun `мир рейтинг в середине`() =
        screen("world_rank_mid", AuditStates.mid) { WorldScreen(vm = it, onLocked = {}) }

    @Test fun `мир рейтинг в поздней игре`() =
        screen("world_rank_late", AuditStates.late) { WorldScreen(vm = it, onLocked = {}) }

    @Test fun `мир цели в начале`() =
        screen("world_goals_early", AuditStates.early) {
            WorldScreen(vm = it, onLocked = {}, startInner = "goals")
        }

    @Test fun `мир цели в поздней игре`() =
        screen("world_goals_late", AuditStates.late) {
            WorldScreen(vm = it, onLocked = {}, startInner = "goals")
        }

    @Test fun `мир престиж в середине`() =
        screen("world_prestige_mid", AuditStates.mid) {
            WorldScreen(vm = it, onLocked = {}, startInner = "pres")
        }

    @Test fun `мир престиж в поздней игре`() =
        screen("world_prestige_late", AuditStates.late) {
            WorldScreen(vm = it, onLocked = {}, startInner = "pres")
        }

    @Test fun `мир рейтинг крупный шрифт`() =
        screen("world_rank_large_font", AuditStates.late, fontScale = Screenshots.LARGE_FONT) {
            WorldScreen(vm = it, onLocked = {})
        }

    // ===================== Профиль =====================

    @Test fun `профиль в начале`() =
        screen("profile_early", AuditStates.early) { ProfileScreen(vm = it) }

    @Test fun `профиль в середине`() =
        screen("profile_mid", AuditStates.mid) { ProfileScreen(vm = it) }

    @Test fun `профиль в поздней игре`() =
        screen("profile_late", AuditStates.late) { ProfileScreen(vm = it) }

    @Test fun `профиль в долгах`() =
        screen("profile_debt", AuditStates.debt) { ProfileScreen(vm = it) }

    @Test fun `профиль крупный шрифт`() =
        screen("profile_late_large_font", AuditStates.late, fontScale = Screenshots.LARGE_FONT) {
            ProfileScreen(vm = it)
        }

    @Test fun `профиль вкладка отдых`() =
        screen("profile_tab_leisure", AuditStates.mid, openTab = "Отдых") { ProfileScreen(vm = it) }

    @Test fun `профиль вкладка коллекция пусто`() =
        screen("profile_tab_collection_early", AuditStates.early, openTab = "Коллекция") {
            ProfileScreen(vm = it)
        }

    @Test fun `профиль вкладка коллекция всё куплено`() =
        screen("profile_tab_collection_late", AuditStates.late, openTab = "Коллекция") {
            ProfileScreen(vm = it)
        }

    @Test fun `профиль вкладка хроника пусто`() =
        screen("profile_tab_chronicle_early", AuditStates.early, openTab = "Хроника") {
            ProfileScreen(vm = it)
        }

    @Test fun `профиль вкладка хроника заполнена`() =
        screen("profile_tab_chronicle_mid", AuditStates.mid, openTab = "Хроника") {
            ProfileScreen(vm = it)
        }

    @Test fun `профиль вкладка цифры`() =
        screen("profile_tab_stats_late", AuditStates.late, openTab = "Цифры") { ProfileScreen(vm = it) }

    // ===================== отдельные экраны =====================

    @Test fun `образование в середине`() =
        screen("education_mid", AuditStates.mid) { EducationScreen(vm = it) }

    @Test fun `связи в поздней игре`() =
        screen("network_late", AuditStates.late) { NetworkScreen(vm = it) }

    @Test fun `цели в поздней игре`() =
        screen("goals_late", AuditStates.late) { GoalsScreen(vm = it) }

    @Test fun `рейтинг в поздней игре`() =
        screen("ranking_late", AuditStates.late) { RankingScreen(vm = it) }

    @Test fun `престиж в поздней игре`() =
        screen("prestige_late", AuditStates.late) { PrestigeScreen(vm = it) }

    @Test fun `престиж крупный шрифт`() =
        screen("prestige_large_font", AuditStates.late, fontScale = Screenshots.LARGE_FONT) {
            PrestigeScreen(vm = it)
        }
}
