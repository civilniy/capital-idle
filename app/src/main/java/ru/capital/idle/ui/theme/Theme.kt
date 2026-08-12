package ru.capital.idle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Обёртка всего интерфейса: подставляет палитру выбранной темы.
 *
 * @param themeId ключ темы из сохранения. Неизвестный ключ — тема по умолчанию («Стекло»),
 *   поэтому старое сохранение открывается в прежнем оформлении.
 */
@Composable
fun CapitalTheme(themeId: String = ThemeIds.GLASS, content: @Composable () -> Unit) {
    val palette = AppTheme.byId(themeId).palette
    val colors = remember(palette) {
        darkColorScheme(
            primary = palette.money,
            onPrimary = palette.onMoney,
            background = palette.bg,
            onBackground = palette.textMain,
            surface = palette.panel,
            onSurface = palette.textMain
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content
        )
    }
}
