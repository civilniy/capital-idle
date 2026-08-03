package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Onboarding
import ru.capital.idle.ui.theme.*

/** Одноразовая подсказка нового раздела. Показывается, пока игрок не нажмёт «Понятно» или крестик. */
@Composable
fun HintCard(state: GameState, hintId: String, onDismiss: (String) -> Unit) {
    if (hintId in state.hintsSeen) return
    val hint = Onboarding.hints[hintId] ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassAccent)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        // шапка: заголовок + крестик закрытия
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(hint.title, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            // зазор до крестика: при крупном системном шрифте заголовок занимает две строки
            // и упирался в кнопку вплотную
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassInner)
                    .clickable { onDismiss(hintId) }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("\u2715", color = Mute, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(hint.text, color = TextMain, fontSize = 11.5.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(11.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Gold)
                .clickable { onDismiss(hintId) }
                .padding(horizontal = 18.dp, vertical = 9.dp)
                .align(Alignment.End)
        ) {
            Text("Понятно", color = CoinText, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
}


/** Внутренний переключатель раздела (общий для групп). */
@Composable
fun InnerTab(label: String, on: Boolean, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Panel2 else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (if (locked) "\uD83D\uDD12 " else "") + label,
            color = if (locked) Mute.copy(alpha = 0.6f) else if (on) Gold else Mute,
            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 11.5.sp, lineHeight = 13.sp, maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}