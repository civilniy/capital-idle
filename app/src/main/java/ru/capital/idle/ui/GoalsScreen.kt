package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.theme.*

@Composable
fun GoalsScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)
    val worth = GameMath.netWorth(state)               // полный капитал, как в рейтинге
    val ratio = Milestones.ratioOverTop1(worth)

    val next = Milestones.all.getOrNull(state.milestonesClaimed)
    val prevThreshold = if (state.milestonesClaimed > 0)
        Milestones.all[state.milestonesClaimed - 1].thresholdUsd else 0.0
    val progress = if (next != null)
        ((worth - prevThreshold) / (next.thresholdUsd - prevThreshold)).coerceIn(0.0, 1.0).toFloat()
    else 1f

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            HintCard(state = state, hintId = "goals", onDismiss = { vm.markHintSeen(it) })

            // акцентная стеклянная карточка
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassAccent)
                    .padding(16.dp)
            ) {
                Text("ВЫ БОГАЧЕ №1 В МИРЕ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("\u00D7" + GameMath.format(ratio), color = Gold,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                Text(
                    if (ratio >= 1.0) "вы обошли ${GameConfig.TOP1_NAME} (${GameMath.formatMoney(GameConfig.TOP1_USD, cur)})"
                    else "${GameConfig.TOP1_NAME}: ${GameMath.formatMoney(GameConfig.TOP1_USD, cur)}",
                    color = Mute, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(GlassInner)) {
                    Box(
                        Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(Brush.linearGradient(listOf(GoldDim, Gold)))
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (next != null)
                        "до «${next.name}» (${GameMath.formatMoney(next.thresholdUsd, cur)}) осталось ${GameMath.formatMoney((next.thresholdUsd - worth).coerceAtLeast(0.0), cur)}"
                    else "все вехи пройдены",
                    color = Mute, fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("ВЕХИ БОГАТСТВА", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))

            Milestones.all.forEachIndexed { i, m ->
                val isDone = i < state.milestonesClaimed
                val isCur = i == state.milestonesClaimed
                MilestoneRow(
                    name = m.name,
                    value = GameMath.formatMoney(m.thresholdUsd, cur),
                    reward = m.rewardBullion,
                    done = isDone, current = isCur
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun MilestoneRow(name: String, value: String, reward: Long, done: Boolean, current: Boolean) {
    // текущая веха — золотистый акцент, остальные — стекло
    val bg = if (current) GlassAccent else GlassFill
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                .background(if (done) Business else if (current) Gold else GlassInner),
            contentAlignment = Alignment.Center
        ) {
            Text(if (done) "\u2713" else if (current) "\u25CF" else "\u25CB",
                color = if (done) BgBase else if (current) CoinText else Mute,
                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (done || current) TextMain else Mute,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(value, color = Mute, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Text("+$reward слитк.", color = Gold, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(7.dp))
}
