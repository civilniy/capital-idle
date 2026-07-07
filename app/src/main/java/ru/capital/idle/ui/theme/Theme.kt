package ru.capital.idle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = CoinText,
    background = Bg,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain
)

@Composable
fun CapitalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
