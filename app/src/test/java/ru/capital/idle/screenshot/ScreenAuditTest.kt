package ru.capital.idle.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * Это не регрессионные тесты вёрстки (те лежат в Profile/CollectionLayoutScreenshotTest),
 * а материал для ревью глазами. Картинки складываются в screenshots/audit/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = Screenshots.DEVICE)
class ScreenAuditTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Снять экран целиком. Автопрокрутка часов Compose выключена: экраны живые,
     * с бегущим временем и анимациями, и без этого правило не дождалось бы покоя.
     */
    private fun shot(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
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
    }

    private fun screen(name: String, state: GameState, fontScale: Float = 1f,
                       content: @Composable (GameViewModel) -> Unit) {
        val vm = AuditStates.viewModelWith(state)
        shot(name, fontScale) { content(vm) }
    }

    // ===================== пробный набор =====================
    // Проверяем, что экран целиком вообще рендерится в Robolectric,
    // прежде чем разворачивать осмотр на все разделы.

    @Test
    fun `приветствие`() {
        shot("welcome") { WelcomeScreen(onDone = {}) }
    }

    @Test
    fun `профиль в начале игры`() {
        screen("profile_early", AuditStates.early) { ProfileScreen(vm = it) }
    }

    @Test
    fun `профиль в поздней игре`() {
        screen("profile_late", AuditStates.late) { ProfileScreen(vm = it) }
    }

    @Test
    fun `главный экран в середине игры`() {
        screen("main_mid", AuditStates.mid) { GameScreen(vm = it) }
    }
}
