package ru.capital.idle.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.theme.*

// Экран пользуется общими слотами темы из ui/theme. Раньше здесь лежала своя копия тех же
// значений — при переключении темы она осталась бы прежней, поэтому копия убрана.

@Composable
fun InvestScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.stockHistory.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)

    // отображаемые свободные наличные обновляются раз в реальную секунду (чтобы цифра не мельтешила)
    var displayedMoney by remember { mutableStateOf(state.money) }
    LaunchedEffect(Unit) {
        while (true) {
            displayedMoney = vm.state.value.money
            kotlinx.coroutines.delay(1000)
        }
    }
    val passiveDay = GameMath.invPerDay(state)
    val tierBonus = CardTier.entries.getOrElse(state.activatedCardTier) { CardTier.CLASSIC }.passiveBonus

    // тот же фон, что и на остальных экранах: раньше он был вписан здесь копией
    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
        Spacer(Modifier.height(8.dp))
        Text("ИНВЕСТИЦИИ", color = Heading, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        HintCard(state = state, hintId = "inv", onDismiss = { vm.markHintSeen(it) })

        Row(
            Modifier.fillMaxWidth().clip(cardShape(18.dp)).background(GlassFill).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("СВОБОДНЫЕ НАЛИЧНЫЕ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
                Text(GameMath.formatMoney(displayedMoney, cur), color = TextMain,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ПАССИВНЫЙ ДОХОД", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("+${GameMath.formatAmount(passiveDay, cur)} ${cur.symbol}/день", color = GreenAccent,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (tierBonus > 0.0) {
                    Text("карта ${CardTier.entries[state.activatedCardTier].title}: +${(tierBonus * 100).toInt()}%",
                        color = Gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        AutoInvestCard(
            on = state.autoInvestOn,
            target = AutoInvest.target(state),
            unlocked = AutoInvest.available(state),
            reserve = state.autoInvestReserve,
            amount = AutoInvest.amount(state),
            cur = cur,
            onToggle = { vm.toggleAutoInvest() },
            onPick = { vm.setAutoInvestAsset(it) },
            onReserve = { vm.stepAutoInvestReserve(it) }
        )

        Spacer(Modifier.height(16.dp))
        Text("НАКОПЛЕНИЯ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Asset.entries.forEach { a ->
            PassiveCard(state, a, cur,
                onInvest = { frac -> vm.invest(a, frac) },
                onSell = { vm.sellAsset(a) },
                onToggleCap = { vm.toggleCapitalize(a) })
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("ФОНДЫ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        val event = vm.activeStockEvent(state)
        if (event != null) {
            val (title, sub) = Exchange.newsTitle(event)
            val left = (event.hoursLeft).coerceAtLeast(0)
            Row(
                Modifier.fillMaxWidth().clip(bannerShape(16.dp))
                    .background(if (event.good) EventGoodFill else EventBadFill)
                    .padding(13.dp),
                verticalAlignment = Alignment.Top
            ) {
                IconSlot(
                    emoji = if (event.good) "\uD83D\uDCC8" else "\uD83D\uDCC9",
                    tint = if (event.good) GreenAccent else RedAccent, fontSize = 18.sp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, color = if (event.good) GreenAccent else RedAccent,
                        fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                    Text(sub, color = Mute, fontSize = 11.sp, lineHeight = 15.sp)
                    Text("${Exchange.stocks[event.stockIndex].ticker} · влияние ещё $left ч",
                        color = Mute, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Exchange.stocks.forEachIndexed { i, stock ->
            StockCard(
                state = state, index = i, stock = stock, cur = cur,
                history = history.getOrElse(i) { emptyList() },
                eventDir = when {
                    event?.stockIndex == i && event.good -> 1
                    event?.stockIndex == i && !event.good -> -1
                    else -> 0
                },
                onBuy = { frac -> vm.buyStock(i, frac) },
                onSell = { vm.sellStock(i) }
            )
            Spacer(Modifier.height(10.dp))
        }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ===================== накопления =====================

/**
 * Автовклад: раз в игровой день переносит с карты во вклад всё сверх резерва.
 *
 * Чистая функция от данных, без `GameViewModel`: так её можно снять скриншотом
 * во всех состояниях (см. CLAUDE.md).
 *
 * Подписи намеренно разведены с капитализацией: там «доход остаётся во вкладе», здесь
 * «деньги с карты уходят во вклад». Это разные механики, и они работают вместе.
 */
@Composable
internal fun AutoInvestCard(
    on: Boolean,
    target: Asset?,
    unlocked: List<Asset>,
    reserve: Double,
    amount: Double,
    cur: Currency,
    onToggle: () -> Unit = {},
    onPick: (Asset) -> Unit = {},
    onReserve: (Int) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().clip(cardShape(18.dp)).background(GlassFill).padding(15.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("АВТОВКЛАД", color = if (on) Gold else Mute,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(3.dp))
                Text("раз в игровой день переносит деньги с карты во вклад",
                    color = Mute, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.width(9.dp))
            CapToggle(on)
        }

        if (!on) return@Column

        if (unlocked.isEmpty()) {
            Spacer(Modifier.height(11.dp))
            Text("нет открытых инструментов — сначала пройдите «Бухгалтерию»",
                color = RedAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        Text("КУДА", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        // Ряд кнопок — тот же, что «50% · На все · Вывести» в карточках накоплений ниже:
        // та же форма, тот же зазор, та же высота. В кнопке только название инструмента —
        // подписи и значки в треть ширины не помещаются. Тремя строками, как было раньше,
        // выбор занимал втрое больше места.
        // высота ряда — по самой высокой кнопке: «Недвижимость» при крупном шрифте встаёт
        // в две строки, и без этого соседние кнопки остались бы ниже её
        Row(
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Asset.entries.forEach { a ->
                PickBtn(
                    label = a.title,
                    picked = a == target,
                    // закрытый образованием инструмент виден, но приглушён и не нажимается
                    enabled = a in unlocked,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { onPick(a) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("РЕЗЕРВ НА КАРТЕ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepBtn("−") { onReserve(-1) }
            Text(
                GameMath.formatMoney(reserve, cur),
                color = TextMain, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            StepBtn("+") { onReserve(1) }
        }
        Spacer(Modifier.height(4.dp))
        Text("эта сумма всегда остаётся на карте — на покупки",
            color = Mute, fontSize = 10.sp, lineHeight = 13.sp)

        Spacer(Modifier.height(11.dp))
        // Итог — всегда одна и та же карточка, без сменных состояний.
        //
        // Раньше на её месте появлялось предупреждение «не сработает: …». Показывалось оно
        // ровно в тот миг, когда автовклад списал деньги и на карте остался один резерв, —
        // то есть на долю секунды раз в игровой день. Прочитать его было нельзя, а мельтешило
        // оно постоянно. Ноль в сумме говорит то же самое и стоит на месте.
        Column(
            Modifier.fillMaxWidth().clip(tileShape(12.dp)).background(GlassInner)
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            // сумма отдельной строкой: до миллиарда деньги показываются целиком
            // (правило полноты чисел, CLAUDE.md), и в одну строку с подписью они не влезают
            Text("следующим днём уйдёт", color = Mute, fontSize = 11.sp)
            Spacer(Modifier.height(2.dp))
            Text(GameMath.formatMoney(amount, cur), color = bigNumber(GreenAccent),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** Квадратная кнопка шага резерва. */
@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(tileShape(12.dp)).background(GlassBtn)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
internal fun PassiveCard(
    state: GameState, a: Asset, cur: Currency,
    onInvest: (Double) -> Unit, onSell: () -> Unit, onToggleCap: () -> Unit
) {
    val unlocked = a.reqCourse in state.eduDone
    val i = a.ordinal
    val value = state.investValues.getOrElse(i) { 0.0 }
    val baseRate = Investments.rate(a, state.eduDone)
    val tierBonus = CardTier.entries.getOrElse(state.activatedCardTier) { CardTier.CLASSIC }.passiveBonus
    val rate = baseRate * (1.0 + tierBonus)
    val capOn = state.capitalizeMask and (1 shl i) != 0
    val hasMoney = state.money >= 1.0
    val hasValue = value > 0.0

    if (!unlocked) {
        Column(Modifier.fillMaxWidth().clip(cardShape(18.dp)).background(GlassFill).padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                LeadingIcon(AppIcons.forAsset(i), Mute)
                Text(a.title, color = Mute, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                Text(GameMath.decimal(rate * 100) + "%/день", color = Mute,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text("требуется: ${Education.byId(a.reqCourse)?.title}", color = RedAccent,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        return
    }

    Column(Modifier.fillMaxWidth().clip(cardShape(18.dp)).background(GlassFill).padding(15.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                LeadingIcon(AppIcons.forAsset(i), if (hasValue) GreenAccent else Mute)
                Text(a.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Tag(a.riskText(), riskColor(a.riskText()))
            }
            Text(GameMath.decimal(rate * 100) + "%/день", color = GreenAccent,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        // строка вложения без риска (риск ушёл в плашку)
        Text("вложено ${GameMath.formatMoney(value, cur)} · +${GameMath.formatAmount(value * rate, cur)} ${cur.symbol}/день",
            color = if (hasValue) GreenAccent else Mute, fontFamily = FontFamily.Monospace, fontSize = 11.sp)

        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBtn("50%", BtnState.NEUTRAL, hasMoney, Modifier.weight(1f)) { onInvest(0.5) }
            ActionBtn("На все", BtnState.NEUTRAL, hasMoney, Modifier.weight(1f)) { onInvest(-1.0) }
            ActionBtn("Вывести", BtnState.SELL, hasValue, Modifier.weight(1f)) { onSell() }
        }

        Spacer(Modifier.height(11.dp))
        Row(
            Modifier.fillMaxWidth().clip(tileShape(12.dp)).background(GlassInner)
                .clickable { onToggleCap() }.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapToggle(capOn)
            Spacer(Modifier.width(9.dp))
            Text(
                if (capOn) "Капитализация вкл · доход остаётся во вкладе"
                else "Капитализация выкл · доход идёт на карту",
                color = Mute, fontSize = 11.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun CapToggle(on: Boolean) {
    val track = if (on) GreenAccent.copy(alpha = 0.55f) else ToggleOff
    Box(
        Modifier.width(36.dp).height(20.dp).clip(btnShape(10.dp)).background(track),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(2.dp).size(16.dp).clip(chipShape(8.dp)).background(TextMain))
    }
}

// ===================== биржа =====================

@Composable
internal fun StockCard(
    state: GameState, index: Int, stock: Stock, cur: Currency,
    history: List<Double>, eventDir: Int,
    onBuy: (Double) -> Unit, onSell: () -> Unit
) {
    val unlocked = stock.reqCourse in state.eduDone
    val price = state.stockPrices.getOrElse(index) { stock.basePrice }
    val qty = state.stockQty.getOrElse(index) { 0.0 }
    val avg = state.stockAvg.getOrElse(index) { 0.0 }
    val hasMoney = state.money >= price

    Column(Modifier.fillMaxWidth().clip(cardShape(18.dp)).background(GlassFill).padding(15.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LeadingIcon(AppIcon.CHART, if (unlocked) Study else Mute)
            Column(Modifier.weight(1f)) {
                // Плашка события приходит и уходит сама, по ходу торгов. Вместе с названием
                // и дивидендами она при крупном шрифте в строку не помещается, и строка
                // вырастала бы на глазах — поэтому место держится по самой полной раскладке.
                val rulers = ArrayList<@Composable () -> Unit>()
                listOf(-1, 0, 1).forEach { dir -> rulers += { StockTitleRow(stock, unlocked, dir) } }
                SteadyHeight(rulers = rulers, modifier = Modifier.fillMaxWidth()) {
                    StockTitleRow(stock, unlocked, eventDir)
                }
                Text("${stock.ticker} · ${stock.info}", color = Mute, fontSize = 9.5.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(GameMath.formatMoney(price, cur), color = if (unlocked) TextMain else Mute,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val ref = history.getOrNull((history.size - 25).coerceAtLeast(0))
                if (unlocked && ref != null && ref > 0) {
                    val ch = (price - ref) / ref * 100
                    Text(
                        (if (ch >= 0) "\u25B2 +" else "\u25BC ") + GameMath.decimal(ch) + "% за день",
                        color = if (ch >= 0) GreenAccent else RedAccent,
                        fontFamily = FontFamily.Monospace, fontSize = 9.5.sp
                    )
                }
            }
        }

        if (!unlocked) {
            Spacer(Modifier.height(3.dp))
            Text("требуется: ${Education.byId(stock.reqCourse)?.title}", color = RedAccent,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            return@Column
        }

        if (history.size >= 2) {
            Spacer(Modifier.height(9.dp))
            val up = history.last() >= history.first()
            // цвет линии берётся до Canvas: внутри лямбды рисования читать цвета темы нельзя
            val lineColor = if (up) GreenAccent else RedAccent
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                val mn = history.min()
                val mx = history.max()
                val rg = (mx - mn).takeIf { it > 0 } ?: 1.0
                val path = Path()
                history.forEachIndexed { idx, v ->
                    val x = idx.toFloat() / (history.size - 1) * size.width
                    val y = size.height - 4f - ((v - mn) / rg * (size.height - 8f)).toFloat()
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        Spacer(Modifier.height(9.dp))
        if (qty > 0) {
            val pl = (price - avg) * qty
            val divDay = qty * price * stock.divPerDay
            Text(
                "у вас ${GameMath.format(qty)} шт · вход ${GameMath.formatMoney(avg, cur)} · " +
                    (if (pl >= 0) "+" else "-") + GameMath.formatMoney(kotlin.math.abs(pl), cur),
                color = if (pl >= 0) GreenAccent else RedAccent,
                fontFamily = FontFamily.Monospace, fontSize = 10.5.sp
            )
            if (stock.divPerDay > 0.0) {
                Text("дивиденды +${GameMath.formatAmount(divDay, cur)} ${cur.symbol}/день",
                    color = GreenAccent, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }
        } else {
            Text("позиции нет", color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            if (stock.divPerDay > 0.0) {
                Text("платит дивиденды держателям", color = Mute,
                    fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }
        }

        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBtn("50%", BtnState.NEUTRAL, hasMoney, Modifier.weight(1f)) { onBuy(0.5) }
            ActionBtn("На все", BtnState.NEUTRAL, hasMoney, Modifier.weight(1f)) { onBuy(-1.0) }
            ActionBtn("Продать", BtnState.SELL, qty > 0, Modifier.weight(1f)) { onSell() }
        }
    }
}

/**
 * Строка названия бумаги: название, дивиденды и плашка события.
 *
 * Вынесена отдельно, чтобы ею же мерить раскладки без события и с событием — тексты и порядок
 * прежние, `eventDir` тот же, что у карточки: 1 — хайп, -1 — обвал, 0 — спокойно.
 */
@Composable
private fun StockTitleRow(stock: Stock, unlocked: Boolean, eventDir: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stock.title, color = if (unlocked) TextMain else Mute,
            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        if (stock.divPerDay > 0.0) Tag("дивиденды ${fmtPct(stock.divPerDay * 100.0)}", Study)
        if (eventDir == 1) Tag("\u25B2 хайп", GreenAccent)
        if (eventDir == -1) Tag("\u25BC обвал", RedAccent)
    }
}

/**
 * Кнопка выбора инструмента для автовклада.
 *
 * Форма, отступы и кегль — как у [ActionBtn]: игрок узнаёт знакомый элемент, а не встречает
 * ещё один вид кнопки. Отличается только выделение выбранного.
 *
 * Выделение зависит от темы. «Стекло» подсвечивает выбранный янтарной заливкой с янтарной
 * подписью — как и было. В новой теме поверхности янтарём не красятся: выбранный стоит
 * на светло-сером вложенном фоне с белой подписью, ровно как активный подраздел
 * в переключателях, а невыбранные сливаются с карточкой.
 */
@Composable
private fun PickBtn(
    label: String,
    picked: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        picked -> legacy(AccentStrong, GlassInner)
        !enabled -> legacy(GlassBtnOff, GlassFill)
        else -> legacy(GlassBtn, GlassFill)
    }
    val fg = when {
        picked -> legacy(Gold, TextMain)
        !enabled -> legacy(GlassBtnOffText, Mute.copy(alpha = 0.55f))
        else -> Mute
    }
    Box(
        modifier
            .clip(tileShape(13.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            textAlign = TextAlign.Center)
    }
}

// ===================== общие кнопки =====================

private enum class BtnState { NEUTRAL, SELL }

@Composable
private fun ActionBtn(label: String, kind: BtnState, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = when {
        !enabled -> GlassBtnOff
        kind == BtnState.SELL -> GlassSell
        else -> GlassBtn
    }
    val fg = when {
        !enabled -> GlassBtnOffText
        kind == BtnState.SELL -> GlassSellText
        else -> TextMain
    }
    Box(
        modifier
            .clip(tileShape(13.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier.padding(start = 6.dp).clip(chipShape(6.dp))
            .background(color.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 3.7.dp)
    ) {
        Text(text, color = color, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 8.5.sp,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

private fun fmtPct(p: Double): String {
    val s = if (p < 1.0) GameMath.decimal(p, 2) else GameMath.decimal(p)
    return s.replace('.', ',') + "%"
}

/** Цвет плашки риска: стабильно — серый, низкий — цвет дохода, средний — предупреждение. */
@Composable
private fun riskColor(risk: String): Color = when (risk) {
    "низкий риск" -> GreenAccent
    "средний риск" -> Warn
    else -> Mute   // стабильно
}
