package ru.capital.idle.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import ru.capital.idle.ui.theme.AppTheme
import ru.capital.idle.ui.theme.Bg
import ru.capital.idle.ui.theme.LocalPalette

/**
 * Общая обвязка скриншот-тестов вёрстки.
 *
 * Рендер идёт на обычной JVM через Robolectric — ни устройства, ни эмулятора не нужно.
 * Конфигурация экрана задаётся в каждом тесте через @Config(qualifiers = DEVICE).
 */
object Screenshots {

    /**
     * Экран тестового устройства OnePlus CPH2653 (1080×2400): при плотности 440dpi это 393×873dp.
     * Раскладку определяет именно ширина в dp, поэтому она и задана точно.
     *
     * Плотность указана бакетом xxhdpi, а не «440dpi»: ресурсные квалификаторы Android
     * принимают только стандартные бакеты. На геометрию это не влияет — меняется лишь
     * разрешение PNG (1179px вместо 1080px по ширине).
     *
     * Локаль задана русской: снимки на сервере должны совпадать с тем, что видно
     * на телефоне. Числа от локали не зависят (см. правила отображения в CLAUDE.md),
     * но перенос строк и подбор шрифта — зависят.
     *
     * Если устройство сообщает другую ширину в dp, поменять нужно только эту строку
     * и перезаписать эталоны (см. раздел про скриншот-тесты в CLAUDE.md).
     */
    const val DEVICE = "ru-rRU-w393dp-h873dp-xxhdpi"

    /**
     * То же устройство, но с высоким холстом — для блоков, которые на телефоне листаются.
     *
     * Снимок обрезается высотой окна: длинная лента при обычной высоте упирается в 873dp,
     * и всё, что ниже, в картинку не попадает. Свёрнутая и развёрнутая ленты при крупном
     * шрифте выходили от этого **байт в байт одинаковыми** — проверка превращалась
     * в бессмыслицу. Ширина остаётся опорной: раскладку определяет именно она.
     */
    const val DEVICE_TALL = "ru-rRU-w393dp-h2000dp-xxhdpi"

    /** Масштаб системного шрифта «крупный» — на нём вёрстка ломается чаще всего. */
    const val LARGE_FONT = 1.5f

    const val DIR = "src/test/screenshots"

    /**
     * Допуск сравнения: 0.1% пикселей. Гасит шум сглаживания текста между машинами,
     * но перенос строки или вылезшее число задевают сотни пикселей и всё равно ловятся.
     */
    val OPTIONS = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
    )
}

/**
 * Снять компонент на фоне приложения и сверить с эталоном.
 *
 * @param name имя файла эталона без расширения
 * @param fontScale масштаб системного шрифта; 1.0 — обычный
 */
fun ComposeContentTestRule.captureOnBackground(
    name: String,
    fontScale: Float = 1f,
    theme: AppTheme = AppTheme.GLASS,
    content: @Composable () -> Unit
) {
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = fontScale),
            LocalPalette provides theme.palette
        ) {
            // тот же отступ по краям, что и на экранах игры — иначе ширина не совпадёт с реальной
            Box(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
    onRoot().captureRoboImage(
        filePath = "${Screenshots.DIR}/$name.png",
        roborazziOptions = Screenshots.OPTIONS
    )
}
