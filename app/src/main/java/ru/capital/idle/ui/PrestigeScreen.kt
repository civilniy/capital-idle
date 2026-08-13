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
import androidx.compose.ui.text.style.TextAlign
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
                        .clip(cardShape(16.dp))
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
                    .clip(cardShape(18.dp))
                    .background(GlassAccent)
                    .padding(16.dp)
            ) {
                Text("СЛИТКИ В КОШЕЛЬКЕ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
                Text(GameMath.formatFull(state.bullion), color = Gold,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                Spacer(Modifier.height(12.dp))
                PrestigeButton(canPrestige = canPrestige, gain = gain) { vm.prestige() }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Сброс: деньги, работа, отрасли, инвестиции, календарь. Остаются навсегда: слитки, покупки престижа, дипломы, репутация и окружение.",
                    color = Mute, fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("ПЕРМАНЕНТНЫЕ АПГРЕЙДЫ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))

            GroupCard {
            for (u in PrestigeUpgrade.entries) {
                val lvl = Prestige.levelOf(state, u)
                val cost = u.costAt(lvl)
                val can = state.bullion >= cost
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape(14.dp))
                        .background(rowFill(GlassFill))
                        .padding(horizontal = dpOf(14.dp, Modern.cardPadH),
                            vertical = dpOf(14.dp, Modern.rowPadV)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LeadingIcon(AppIcon.ARROW_UP, if (can) Gold else Mute)
                    Column(Modifier.weight(1f)) {
                        Text(u.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("${Prestige.effectText(state, u)}   ·   ур. $lvl", color = Mute,
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(btnShape(11.dp))
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
                RowSeparator(8.dp, last = u == PrestigeUpgrade.entries.last())
            }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Кнопка перерождения. Вынесена отдельно, чтобы её вёрстку можно было снять скриншотом
 * без ViewModel: именно здесь при крупном системном шрифте рвалась строка с наградой.
 */
@Composable
internal fun PrestigeButton(canPrestige: Boolean, gain: Long, onClick: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(tileShape(13.dp))
            .background(if (canPrestige) Gold else GlassBtnOff)
            .clickable(enabled = canPrestige, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            // неразрывный пробел перед «слитков»: при крупном системном шрифте строка
            // переносится, и слово отрывалось от числа награды
            if (canPrestige) "Начать жизнь заново   +${GameMath.formatFull(gain)}\u00A0слитков"
            else "Заработайте больше для слитков",
            color = if (canPrestige) CoinText else GlassBtnOffText,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
