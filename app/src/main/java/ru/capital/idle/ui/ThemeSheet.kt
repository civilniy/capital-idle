package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.capital.idle.ui.theme.*

/** Кнопка настроек оформления — шестерёнка в правом верхнем углу профиля. */
@Composable
internal fun SettingsButton(onClick: () -> Unit) {
    Box(
        Modifier.clip(btnShape(11.dp)).clickable(onClick = onClick).padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        IconSlot("⚙", tint = Gold, fontSize = 18.sp, icon = AppIcon.GEAR)
    }
}

/**
 * Выбор оформления. Тем две, и различаются они по смыслу, а не по номеру:
 * «Стекло» — полупрозрачные слои с пятнами на фоне, «Матовая» — плотные слои без рамок.
 *
 * Чистая функция от данных: текущий ключ приходит параметром, выбор уходит колбэком,
 * поэтому лист снимается скриншотом без `GameViewModel`.
 */
@Composable
internal fun ThemeSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Scrim).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp))
                .background(Panel).clickable(enabled = false) {}.padding(16.dp)
        ) {
            Text("Оформление", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text("Меняются только цвета и значки. Расположение блоков одинаковое.",
                color = Mute, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(12.dp))

            AppTheme.entries.forEach { theme ->
                val picked = theme.id == current
                Row(
                    Modifier.fillMaxWidth().clip(tileShape(12.dp))
                        .background(if (picked) GlassAccent else Panel2)
                        .clickable { onPick(theme.id) }
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        // выбранная тема: в «Стекле» янтарная подпись, в новой — белая,
                        // потому что выделение там держится на светлой поверхности, а не на цвете
                        Text(theme.title, color = if (picked) legacy(Gold, TextMain) else TextMain,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(theme.note, color = Mute, fontSize = 10.5.sp, lineHeight = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    if (picked) Text("✓", color = legacy(Gold, TextMain),
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(8.dp),
                contentAlignment = Alignment.Center) {
                Text("закрыть", color = Mute, fontSize = 12.sp)
            }
        }
    }
}
