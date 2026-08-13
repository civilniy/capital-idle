package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.Network
import ru.capital.idle.ui.theme.*

@Composable
fun NetworkScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        HintCard(state = state, hintId = "net", onDismiss = { vm.markHintSeen(it) })

        // репутация
        Row(
            Modifier
                .fillMaxWidth()
                .clip(cardShape(14.dp))
                .background(GlassFill)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("РЕПУТАЦИЯ", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(dpOf(6.dp, Modern.barHeight))
                .clip(barShape(99.dp)).background(GlassInner)) {
                Box(
                    Modifier.fillMaxWidth((state.reputation / 100.0).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight().clip(barShape(99.dp)).background(Rest)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("${state.reputation.toInt()}/100", color = Mute,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Репутация снижает давление элит на доход (после \$1 млрд) до -70%.",
            color = Mute, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))

        GroupCard {
            Network.all.forEachIndexed { i, item ->
                NetworkItemCard(
                    title = item.title, info = item.info,
                    owned = item.id in state.netOwned,
                    canBuy = item.id !in state.netOwned && state.money >= item.cost,
                    costText = GameMath.formatMoney(item.cost, cur),
                    onBuy = { vm.buyNetItem(item.id) }
                )
                RowSeparator(8.dp, last = i == Network.all.lastIndex)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Строка окружения. Вынесена из экрана без изменений разметки — чтобы её можно было
 * снять скриншотом в обеих темах, не собирая вокруг `GameViewModel` (см. CLAUDE.md).
 */
@Composable
internal fun NetworkItemCard(
    title: String, info: String, owned: Boolean, canBuy: Boolean,
    costText: String, onBuy: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(cardShape(14.dp))
            .background(if (owned) GlassAccent else rowFill(GlassFill))
            .padding(horizontal = dpOf(14.dp, Modern.cardPadH), vertical = dpOf(12.dp, Modern.rowPadV)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(AppIcon.PERSON, if (owned) Gold else Mute)
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(info, color = Mute, fontSize = 10.sp)
        }
        if (owned) {
            Text("активно", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else {
            Box(
                Modifier
                    .clip(btnShape(11.dp))
                    .background(if (canBuy) Gold else GlassBtnOff)
                    .clickable(enabled = canBuy, onClick = onBuy)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(costText,
                    color = if (canBuy) CoinText else GlassBtnOffText,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}
