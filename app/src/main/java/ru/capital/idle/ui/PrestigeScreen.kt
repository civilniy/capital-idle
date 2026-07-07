package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.Prestige
import ru.capital.idle.core.game.PrestigeUpgrade
import ru.capital.idle.ui.theme.*

@Composable
fun PrestigeScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val gain = Prestige.gainFrom(state.totalEarned)
    val canPrestige = gain >= 1

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(10.dp))
            HintCard(state = state, hintId = "pres", onDismiss = { vm.markHintSeen(it) })

            // подсказка «упёрлись в стену» — стеклянная с золотистым акцентом
            if (canPrestige && GameMath.atProgressWall(state)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassAccent)
                        .padding(14.dp)
                ) {
                    Text("\u26F0 Вы упёрлись в стену", color = Gold,
                        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Следующий рывок требует слишком долгого ожидания. Самое время для престижа: " +
                            "слитки усилят доход, и новый круг пойдёт заметно быстрее.",
                        color = TextMain, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // карточка слитков — акцентная стеклянная
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassAccent)
                    .padding(16.dp)
            ) {
                Text("СЛИТКИ В КОШЕЛЬКЕ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
                Text(GameMath.formatFull(state.bullion), color = Gold,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (canPrestige) Gold else GlassBtnOff)
                        .clickable(enabled = canPrestige) { vm.prestige() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (canPrestige) "Начать жизнь заново   +${GameMath.formatFull(gain)} слитков"
                        else "Заработайте больше для слитков",
                        color = if (canPrestige) CoinText else GlassBtnOffText,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Сброс: деньги, работа, отрасли, инвестиции, календарь. Остаются навсегда: слитки, покупки престижа, дипломы, репутация и окружение.",
                    color = Mute, fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("ПЕРМАНЕНТНЫЕ АПГРЕЙДЫ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))

            for (u in PrestigeUpgrade.entries) {
                val lvl = Prestige.levelOf(state, u)
                val cost = u.costAt(lvl)
                val can = state.bullion >= cost
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassFill)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("${Prestige.effectText(state, u)}   ·   ур. $lvl", color = Mute,
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (can) Gold else GlassBtnOff)
                            .clickable(enabled = can) { vm.buyPrestigeUpgrade(u) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${GameMath.formatFull(cost)} слит.",
                            color = if (can) CoinText else GlassBtnOffText,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
