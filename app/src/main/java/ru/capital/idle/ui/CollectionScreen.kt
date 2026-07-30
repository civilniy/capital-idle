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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.theme.*

/**
 * Раздел «Коллекция»: искусство и редкие объекты.
 * В отличие от имущества, предметы покупаются в любом порядке и держатся все сразу;
 * содержания не требуют, дохода не дают — дорожают сами со временем.
 */
@Composable
fun CollectionSection(vm: GameViewModel, state: GameState, cur: Currency) {
    val day = Collectibles.dayOf(state)
    val owned = Collectibles.ownedCount(state)
    val value = Collectibles.portfolioValue(state)
    val profit = Collectibles.totalProfit(state)

    // сводка по коллекции
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassAccent).padding(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("КОЛЛЕКЦИЯ", color = Gold, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, letterSpacing = 1.sp)
            Text("$owned / ${Collectibles.all.size}", color = TextMain,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CollectStat("ОЦЕНКА", GameMath.formatMoney(value, cur), TextMain, Modifier.weight(1f))
            CollectStat(
                if (profit >= 0) "ПРИБЫЛЬ" else "УБЫТОК",
                (if (profit >= 0) "+" else "-") + GameMath.formatMoney(kotlin.math.abs(profit), cur),
                if (profit >= 0) GreenAccent else RedAccent,
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Предметы дорожают каждый игровой день. Содержания не требуют и дохода не приносят — " +
                "это вложение и очки статуса. Сейчас идёт день ${day + 1}.",
            color = Mute, fontSize = 10.5.sp, lineHeight = 15.sp
        )
    }

    Spacer(Modifier.height(12.dp))
    Text("КАТАЛОГ · цена растёт со временем", color = Mute, fontSize = 11.sp,
        letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))

    Collectibles.all.forEach { c ->
        CollectibleCard(
            item = c,
            price = Collectibles.priceIn(c, state),
            owned = Collectibles.owns(state, c.id),
            paid = Collectibles.paidFor(state, c.id),
            profit = Collectibles.profit(state, c),
            canBuy = Collectibles.canBuy(state, c.id),
            cur = cur,
            onBuy = { vm.buyCollectible(c.id) },
            onSell = { vm.sellCollectible(c.id) }
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CollectStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(GlassInner).padding(vertical = 9.dp, horizontal = 10.dp)
    ) {
        Text(value, color = color, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Mute, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun CollectibleCard(
    item: Collectible, price: Double, owned: Boolean, paid: Double, profit: Double,
    canBuy: Boolean, cur: Currency, onBuy: () -> Unit, onSell: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (owned) GlassAccent else GlassFill)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.emoji, fontSize = 22.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(item.title, color = if (owned || canBuy) TextMain else Mute,
                fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${item.rarity.title} · +${item.status} статуса", color = Mute, fontSize = 10.sp)
            if (owned) {
                Text(
                    "куплено за ${GameMath.formatMoney(paid, cur)} · " +
                        (if (profit >= 0) "+" else "-") + GameMath.formatMoney(kotlin.math.abs(profit), cur),
                    color = if (profit >= 0) GreenAccent else RedAccent,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp
                )
            } else {
                Text(item.info, color = Mute.copy(alpha = 0.75f), fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 2)
            }
        }
        if (owned) {
            Column(horizontalAlignment = Alignment.End) {
                Text(GameMath.formatMoney(price, cur), color = TextMain,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(GlassSell)
                        .clickable(onClick = onSell).padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("продать", color = GlassSellText,
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(9.dp))
                    .background(if (canBuy) Gold else GlassBtnOff)
                    .clickable(enabled = canBuy, onClick = onBuy)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(GameMath.formatMoney(price, cur),
                    color = if (canBuy) CoinText else GlassBtnOffText,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
        }
    }
}
