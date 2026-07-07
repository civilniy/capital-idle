package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.RankModel
import ru.capital.idle.core.game.RankingData
import ru.capital.idle.ui.theme.*

private data class RankRowData(
    val rank: Long,
    val name: String,
    val worthUsd: Double,
    val source: String,
    val real: Boolean,
    val isPlayer: Boolean,
    val deltaPct: Double = 0.0
)

@Composable
fun RankingScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)
    // полный капитал (наличные + активы), обновляется раз в игровой день
    val gameDay = (state.gameHours / 24.0).toInt()
    val money = remember(gameDay) { GameMath.netWorth(state) }
    val playerRank = RankModel.rankForWealth(money)
    val playerLabel = state.playerName.ifBlank { "ВЛАДЕЛЕЦ КАПИТАЛА" }
    val firstWorth = RankingData.full.first().netWorthUsd
    val lastWorth = RankingData.full.last().netWorthUsd
    val inTop = money >= lastWorth

    // живой рейтинг: состояния дрейфуют детерминированно по игровым дням
    val driftedSorted = remember(gameDay) {
        RankingData.full.mapIndexed { i, p ->
            val w = p.netWorthUsd * LiveDrift.mult(i, gameDay)
            val prev = p.netWorthUsd * LiveDrift.mult(i, gameDay - 1)
            Triple(p, w, (w - prev) / prev * 100.0)
        }.sortedByDescending { it.second }
    }
    val rows = remember(gameDay, inTop) {
        val out = ArrayList<RankRowData>(driftedSorted.size + 1)
        var rank = 1L
        var inserted = false
        for ((p, w, d) in driftedSorted) {
            if (inTop && !inserted && money >= w) {
                out.add(RankRowData(rank++, playerLabel, money, "", true, true))
                inserted = true
            }
            out.add(RankRowData(rank++, p.name, w, p.source, p.real, false, d))
        }
        if (inTop && !inserted) out.add(RankRowData(rank, playerLabel, money, "", true, true))
        out
    }

    // место игрока — единый источник: позиция его строки в отсортированном списке (или оценка вне топа)
    val playerListRank = rows.firstOrNull { it.isPlayer }?.rank ?: 1L

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            HintCard(state = state, hintId = "rank", onDismiss = { vm.markHintSeen(it) })

            // сводка игрока — акцентная стеклянная карточка
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassAccent)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ВАШЕ МЕСТО", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(
                        if (money >= firstWorth) "№1 в мире"
                        else if (inTop) "$playerListRank"
                        else "${GameMath.formatFull(playerRank)}",
                        color = Gold, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp
                    )
                    Text(
                        if (inTop) "вы в топ-1000 · обновляется раз в день"
                        else "из ${GameMath.formatFull(RankModel.POPULATION)} человек · обновляется раз в день",
                        color = Mute, fontSize = 11.sp
                    )
                }
                Text(GameMath.formatMoney(money, cur), color = TextMain,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(rows) { row -> RankRow(row, cur) }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun RankRow(row: RankRowData, cur: Currency) {
    val rankColor = when {
        row.isPlayer -> Gold
        row.rank == 1L -> Gold
        row.rank <= 3L -> TextMain
        else -> Mute
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (row.isPlayer) GlassAccent else GlassFill)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${row.rank}",
            color = rankColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, modifier = Modifier.width(58.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    row.isPlayer -> row.name
                    row.name.isNotEmpty() -> row.name
                    else -> "Миллиардер"
                },
                color = if (row.isPlayer) Gold else if (row.name.isNotEmpty() || row.isPlayer) TextMain else Mute,
                fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
            if (row.source.isNotEmpty()) {
                Text(row.source, color = Mute, fontSize = 10.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!row.isPlayer && kotlin.math.abs(row.deltaPct) >= 0.05) {
                Text(
                    (if (row.deltaPct > 0) "\u25B2 +" else "\u25BC ") +
                        String.format("%.1f%%", row.deltaPct),
                    color = if (row.deltaPct > 0) GreenAccent else RedAccent,
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp
                )
            }
            Text(GameMath.formatMoney(row.worthUsd, cur), color = if (row.isPlayer) Gold else GreenAccent,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Детерминированный дрейф состояний: две синусоиды на персону, без хранения. */
private object LiveDrift {
    private fun hash(i: Int, k: Int): Double {
        val x = kotlin.math.sin(i * 12.9898 + k * 78.233) * 43758.5453
        return x - kotlin.math.floor(x)
    }
    fun mult(i: Int, day: Int): Double {
        if (day < 0) return 1.0
        val amp = 0.008 + hash(i, 1) * 0.022
        val f1 = 0.35 + hash(i, 2) * 0.5
        val f2 = 0.05 + hash(i, 3) * 0.12
        val ph = hash(i, 4) * 6.283
        return 1.0 + amp * kotlin.math.sin(day * f1 + ph) +
            amp * 0.6 * kotlin.math.sin(day * f2 + ph * 1.7)
    }
}
