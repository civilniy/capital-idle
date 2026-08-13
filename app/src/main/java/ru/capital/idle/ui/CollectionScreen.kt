package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    // итог торгов: лот мог закрыться и пока игра была свёрнута — тогда это единственное место,
    // где игрок узнаёт, достался ему предмет или ушёл
    val auctionResult by vm.auctionResult.collectAsStateWithLifecycle()
    auctionResult?.let { ended ->
        AuctionResultCard(ended = ended, cur = cur, onDismiss = { vm.dismissAuctionResult() })
        Spacer(Modifier.height(8.dp))
    }
    AuctionBlock(
        view = AuctionView.of(state),
        cur = cur,
        onBid = { amount -> vm.placeBid(amount) }
    )

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
            canBuy = Auctions.canBuyInCatalog(state, c.id),
            inCatalog = Auctions.inCatalog(c.id),
            cur = cur,
            onBuy = { vm.buyCollectible(c.id) },
            onSell = { vm.sellCollectible(c.id) }
        )
        Spacer(Modifier.height(6.dp))
    }
}

// ===================== торги =====================

/**
 * Всё, что нужно нарисовать блоку торгов, посчитано заранее. Блок остаётся чистой
 * функцией от данных — его можно снять скриншотом без ViewModel.
 *
 * @param item лот или null, если торгов сейчас нет
 * @param unlocked есть ли у игрока доступ к этому уровню торгов
 * @param nextInH через сколько игровых часов начнутся следующие торги (когда лота нет)
 * @param hasLotsLeft осталось ли что выставлять; когда собрано всё, следующего лота не будет
 */
internal data class AuctionView(
    val item: Collectible?,
    val tier: AuctionTier,
    val unlocked: Boolean,
    val reputation: Double,
    val status: Int,
    val bid: Double,
    val bids: Int,
    val playerLeads: Boolean,
    val leader: String,
    val hoursLeft: Double,
    val timeFraction: Float,
    val minBid: Double,
    val boldBid: Double,
    val money: Double,
    val catalogPrice: Double,
    val nextInH: Double,
    val hasLotsLeft: Boolean
) {
    companion object {
        fun of(state: GameState): AuctionView {
            val a = state.auction
            val item = a?.let { Collectibles.byId(it.itemId) }
            val tier = a?.tier ?: AuctionTier.OPEN
            return AuctionView(
                item = item,
                tier = tier,
                unlocked = tier.unlocked(state),
                reputation = GameMath.reputationShown(state),
                status = Lifestyle.socialStatus(state),
                bid = a?.bid ?: 0.0,
                bids = a?.bids ?: 0,
                playerLeads = a?.playerLeads == true,
                leader = a?.let { Auctions.leaderTitle(it) }.orEmpty(),
                hoursLeft = a?.let { Auctions.hoursLeft(it, state.gameHours) } ?: 0.0,
                timeFraction = a?.let { Auctions.timeFraction(it, state.gameHours) } ?: 0f,
                minBid = a?.let { Auctions.minBid(it) } ?: 0.0,
                boldBid = a?.let { Auctions.boldBid(it) } ?: 0.0,
                money = state.money,
                catalogPrice = item?.let { Collectibles.priceIn(it, state) } ?: 0.0,
                nextInH = (state.auctionNextGameH - state.gameHours).coerceAtLeast(0.0),
                hasLotsLeft = Auctions.hasLotsLeft(state)
            )
        }
    }
}

/**
 * Итог закончившихся торгов. Показывается, пока игрок его не закроет: лот мог завершиться,
 * пока игра была свёрнута, и другого случая узнать исход не будет.
 */
@Composable
internal fun AuctionResultCard(ended: Auctions.Ended, cur: Currency, onDismiss: () -> Unit) {
    val item = Collectibles.byId(ended.itemId)
    val won = ended.won
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (won) GlassAccent else GlassFill)
            .then(
                if (won) Modifier.glassOutline(Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSlot(item?.emoji.orEmpty(), tint = if (won) Gold else Mute, fontSize = 20.sp,
            modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                if (won) "Лот ваш" else "Лот ушёл",
                color = if (won) Gold else RedAccent,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1
            )
            Text(
                item?.title.orEmpty(), color = TextMain, fontSize = 10.5.sp,
                lineHeight = 13.sp, maxLines = 2
            )
            Text(
                (if (won) "куплено за " else "ушёл за ") + GameMath.formatMoney(ended.price, cur),
                color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).background(GlassBtn)
                .clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text("ясно", color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

/**
 * Раздел активных торгов. Показывает лот, текущую ставку, кто ведёт и сколько времени осталось.
 *
 * Три состояния карточки: торгов нет, торги идут и доступны, торги идут но уровень закрыт.
 * Закрытый уровень не прячем — видно, какой предмет уходит и чего не хватает для допуска.
 */
@Composable
internal fun AuctionBlock(view: AuctionView, cur: Currency, onBid: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text("ТОРГИ", color = Mute, fontSize = 11.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        if (view.item != null) {
            Text(view.tier.title, color = if (view.unlocked) Gold else Mute,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
        }
    }
    Spacer(Modifier.height(6.dp))

    when {
        view.item == null -> AuctionIdleCard(view.nextInH, view.hasLotsLeft)
        !view.unlocked -> AuctionLockedCard(view)
        else -> AuctionLiveCard(view, cur, onBid)
    }
}

/**
 * Торгов нет — два разных случая, и путать их нельзя.
 *
 * Пока в коллекции есть незакрытые предметы, лот будет, и таймер честный. Когда собрано
 * всё, выставлять нечего и следующего лота не будет никогда — обещать время в этом случае
 * значило бы врать, поэтому таймер убирается совсем.
 */
@Composable
private fun AuctionIdleCard(nextInH: Double, hasLotsLeft: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (hasLotsLeft) GlassFill else GlassAccent)
            .then(
                if (hasLotsLeft) Modifier
                else Modifier.glassOutline(Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            )
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Text(
            if (hasLotsLeft) "Зал пуст" else "✓ Все предметы собраны",
            color = if (hasLotsLeft) TextMain else Gold,
            fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (hasLotsLeft)
                "Следующий лот выставят через ${Auctions.formatLeft(nextInH)}. " +
                    "Самые редкие предметы в каталоге не продаются — только здесь."
            else
                "Торги закрыты: выставлять больше нечего. Если продать предмет из коллекции, " +
                    "он снова сможет попасть на торги.",
            color = if (hasLotsLeft) Mute else GoldDim, fontSize = 10.sp, lineHeight = 14.sp
        )
    }
}

/** Торги идут, но уровень закрыт: видно лот и чего не хватает для допуска. */
@Composable
private fun AuctionLockedCard(view: AuctionView) {
    val item = view.item ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GlassFill)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSlot("🔒", tint = Mute, fontSize = 18.sp, modifier = Modifier.width(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(item.title, color = Mute, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, maxLines = 1)
                Text("вас не пускают в зал", color = Mute.copy(alpha = 0.75f), fontSize = 9.5.sp)
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GateCell("РЕПУТАЦИЯ", GameMath.decimal(view.reputation, 0),
                view.tier.reqReputation.toInt().toString(),
                view.reputation >= view.tier.reqReputation, Modifier.weight(1f))
            GateCell("СТАТУС", view.status.toString(), view.tier.reqStatus.toString(),
                view.status >= view.tier.reqStatus, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GateCell(label: String, have: String, need: String, ok: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(GlassInner)
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Text("$have / $need", color = if (ok) GreenAccent else TextMain,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Mute, fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

/** Торги идут и доступны: ставка, лидер, время, кнопки. */
@Composable
private fun AuctionLiveCard(view: AuctionView, cur: Currency, onBid: (Double) -> Unit) {
    val item = view.item ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (view.playerLeads) GlassAccent else GlassFill)
            .then(
                if (view.playerLeads) Modifier.glassOutline(Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSlot(item.emoji, tint = Gold, fontSize = 22.sp, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(item.title, color = TextMain, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, maxLines = 1)
                Text(
                    "${item.rarity.title} · ставок ${view.bids} · " +
                        "в каталоге ${if (Auctions.inCatalog(item.id)) GameMath.formatMoney(view.catalogPrice, cur) else "не продаётся"}",
                    color = Mute, fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 2
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuctionStat("СТАВКА", GameMath.formatMoney(view.bid, cur), TextMain, Modifier.weight(1f))
            AuctionStat(
                "ВЕДЁТ", view.leader,
                if (view.playerLeads) GreenAccent else RedAccent, Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(GlassInner)
            ) {
                Box(
                    Modifier.fillMaxWidth(1f - view.timeFraction).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(if (view.playerLeads) Gold else GoldDim)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("осталось ${Auctions.formatLeft(view.hoursLeft)}", color = Mute,
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1)
        }

        Spacer(Modifier.height(11.dp))
        if (view.playerLeads) {
            Text(
                "Ваша ставка принята. Деньги удержаны и вернутся полностью, если зал перебьёт.",
                color = GoldDim, fontSize = 10.sp, lineHeight = 14.sp
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BidButton("ПЕРЕБИТЬ", view.minBid, view.money >= view.minBid, cur,
                    Modifier.weight(1f)) { onBid(view.minBid) }
                BidButton("УВЕРЕННО", view.boldBid, view.money >= view.boldBid, cur,
                    Modifier.weight(1f)) { onBid(view.boldBid) }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Зал отвечает не сразу и до своего предела — предел не объявляют. " +
                    "Крупная ставка может закрыть торги, но переплатой.",
                color = Mute, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun AuctionStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(GlassInner)
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Text(value, color = color, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Mute, fontSize = 9.sp, letterSpacing = 1.sp, maxLines = 1)
    }
}

@Composable
private fun BidButton(
    label: String, amount: Double, enabled: Boolean, cur: Currency,
    modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(11.dp))
            .background(if (enabled) Gold else GlassBtnOff)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(GameMath.formatMoney(amount, cur),
            color = if (enabled) CoinText else GlassBtnOffText,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp, maxLines = 1)
        Text(label, color = if (enabled) CoinText else GlassBtnOffText,
            fontSize = 8.5.sp, letterSpacing = 1.sp, maxLines = 1)
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
        // подпись нарочно короткая: с разрядкой 2sp длинный заголовок при системном
        // шрифте 1.5 не помещается в строку рядом со счётчиком
        Text("НАБОРЫ", color = Mute, fontSize = 11.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("собрано $doneCount / ${rows.size}", color = if (doneCount > 0) Gold else Mute,
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
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
                if (done) Modifier.glassOutline(Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSlot(row.set.emoji, tint = if (done) Gold else Mute, fontSize = 20.sp,
            modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            // счётчик стоит не рядом с названием, а в строке полоски: при крупном системном
            // шрифте «✓ Имена в истории» и «5 / 5» в одной строке уже не помещаются
            Text(
                if (done) "✓ ${row.set.title}" else row.set.title,
                color = if (done) Gold else TextMain,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1
            )
            Text(
                if (done) "набор собран" else row.set.info,
                color = if (done) GoldDim else Mute.copy(alpha = 0.75f),
                fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SetProgressBar(owned = row.owned, total = total, done = done,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text("${row.owned} / $total", color = if (done) Gold else Mute,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(
            Modifier.clip(RoundedCornerShape(9.dp))
                .background(if (done) legacy(Gold, Status) else GlassInner)
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
private fun SetProgressBar(owned: Int, total: Int, done: Boolean, modifier: Modifier = Modifier) {
    if (total <= 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
                bigNumber(if (profit >= 0) GreenAccent else RedAccent),
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
    canBuy: Boolean, cur: Currency, onBuy: () -> Unit, onSell: () -> Unit,
    /** Продаётся ли предмет в каталоге. Уникальные лоты — только с торгов. */
    inCatalog: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (owned) GlassAccent else GlassFill)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSlot(item.emoji, tint = if (owned) Gold else Mute, fontSize = 22.sp,
            modifier = Modifier.width(34.dp))
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
        } else if (!inCatalog) {
            // предмет ушёл с рынка: цену показываем как ориентир, купить можно только на торгах
            Column(
                Modifier.clip(RoundedCornerShape(9.dp)).background(GlassBtnOff)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(GameMath.formatMoney(price, cur), color = GlassBtnOffText,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp, maxLines = 1)
                Text("только с торгов", color = GlassBtnOffText,
                    fontSize = 8.5.sp, maxLines = 1)
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
