package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    CollectionSummary(day = day, owned = owned, value = value, profit = profit, cur = cur)

    Spacer(Modifier.height(12.dp))
    CollectionSetsBlock(
        rows = Collectibles.sets.map { SetProgress(it, Collectibles.ownedInSet(state, it)) }
    )

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

/** Набор и то, сколько его предметов уже в коллекции. Считается снаружи — блок остаётся чистым. */
internal data class SetProgress(val set: CollectibleSet, val owned: Int)

/**
 * Блок наборов над каталогом: что с чем собирать и сколько статуса это даст.
 *
 * Собранный набор отличается от несобранного сразу тремя признаками — золотой рамкой,
 * акцентной заливкой и залитым бейджем бонуса: одного цвета мало, если смотреть боком
 * или при включённой крупной системной подписи.
 */
@Composable
internal fun CollectionSetsBlock(rows: List<SetProgress>) {
    val doneCount = rows.count { it.owned >= Collectibles.sizeOf(it.set) && it.owned > 0 }
    val earned = rows.filter { it.owned >= Collectibles.sizeOf(it.set) && it.owned > 0 }
        .sumOf { it.set.bonus }
    val possible = rows.sumOf { it.set.bonus }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text("НАБОРЫ · бонус за полный сбор", color = Mute, fontSize = 11.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("$doneCount / ${rows.size}", color = if (doneCount > 0) Gold else Mute,
            fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
    Spacer(Modifier.height(6.dp))

    rows.forEach { row ->
        SetCard(row)
        Spacer(Modifier.height(6.dp))
    }

    Text(
        "Получено $earned из $possible очков статуса за наборы. Предмет может входить сразу " +
            "в несколько наборов — одна покупка закрывает две цели.",
        color = Mute, fontSize = 10.sp, lineHeight = 14.sp
    )
}

@Composable
private fun SetCard(row: SetProgress) {
    val total = Collectibles.sizeOf(row.set)
    val done = total > 0 && row.owned >= total
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (done) GlassAccent else GlassFill)
            .then(
                if (done) Modifier.border(1.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(row.set.emoji, fontSize = 20.sp, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓ ${row.set.title}" else row.set.title,
                    color = if (done) Gold else TextMain,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text("${row.owned} / $total", color = if (done) Gold else Mute,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
            }
            Text(
                if (done) "набор собран" else row.set.info,
                color = if (done) GoldDim else Mute.copy(alpha = 0.75f),
                fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 2
            )
            Spacer(Modifier.height(6.dp))
            SetProgressBar(owned = row.owned, total = total, done = done)
        }
        Spacer(Modifier.width(4.dp))
        Column(
            Modifier.clip(RoundedCornerShape(9.dp)).background(if (done) Gold else GlassInner)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("+${row.set.bonus}", color = if (done) CoinText else Mute,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp, maxLines = 1)
            Text("СТАТУСА", color = if (done) CoinText else Mute.copy(alpha = 0.7f),
                fontSize = 7.5.sp, letterSpacing = 0.5.sp, maxLines = 1)
        }
    }
}

/** Полоска прогресса набора: делится на клетки по числу предметов, чтобы «3 из 5» читалось и без цифр. */
@Composable
private fun SetProgressBar(owned: Int, total: Int, done: Boolean) {
    if (total <= 0) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            i >= owned -> GlassInner
                            done -> Gold
                            else -> GoldDim
                        }
                    )
            )
        }
    }
}

/** Шапка раздела: сколько собрано, оценка коллекции и суммарная прибыль. */
@Composable
internal fun CollectionSummary(day: Int, owned: Int, value: Double, profit: Double, cur: Currency) {
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
internal fun CollectibleCard(
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
                // две отдельные строки: на дорогих предметах числа длинные,
                // и одной строкой «куплено за X · +Y» рвётся посередине
                Text(
                    "куплено за ${GameMath.formatMoney(paid, cur)}",
                    color = Mute, fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, maxLines = 1
                )
                Text(
                    (if (profit >= 0) "+" else "-") + GameMath.formatMoney(kotlin.math.abs(profit), cur),
                    color = if (profit >= 0) GreenAccent else RedAccent,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, maxLines = 1
                )
            } else {
                Text(item.info, color = Mute.copy(alpha = 0.75f), fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 2)
            }
        }
        if (owned) {
            Column(horizontalAlignment = Alignment.End) {
                Text(GameMath.formatMoney(price, cur), color = TextMain,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, maxLines = 1)
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
