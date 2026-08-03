package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.theme.*

@Composable
fun ProfileScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)
    var inner by remember { mutableStateOf(0) }   // 0 имущество · 1 светская жизнь · 2 хроника · 3 цифры · 4 коллекция
    var showRename by remember { mutableStateOf(false) }   // диалог смены имени

    val inDebt = state.debt > 0.0
    val upkeep = Lifestyle.dailyUpkeep(state)
    val gross = GameMath.incomePerDay(state)
    val net = gross - upkeep
    val status = Lifestyle.socialStatus(state)

    GlassBackground {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("ПРОФИЛЬ", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        HintCard(state = state, hintId = "prof", onDismiss = { vm.markHintSeen(it) })

        // ШАПКА-ПУЛЬТ
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (inDebt) Color(0x26D9694F) else GlassAccent)
                .padding(15.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showRename = true }
            ) {
                Text(state.playerName.uppercase(java.util.Locale.ROOT), color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text("✎", color = Mute, fontSize = 12.sp)   // карандаш: имя можно изменить
            }
            Text(
                Lifestyle.titles[state.lastTitleIdx.coerceIn(Lifestyle.titles.indices)].name.uppercase(java.util.Locale.ROOT),
                color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp
            )

            // шкала статуса
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(GlassInner)
            ) {
                val frac = (status / 800f).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(99.dp))
                        .background(Brush.horizontalGradient(listOf(GoldDim, Gold)))
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("СОЦИАЛЬНЫЙ СТАТУС", color = Mute, fontSize = 10.sp, letterSpacing = 1.sp)
                Text("$status", color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            // три числа
            Spacer(Modifier.height(12.dp))
            MoneyCellsRow(
                moneyStr = if (inDebt) "-${GameMath.formatMoney(state.debt, cur)}"
                    else GameMath.formatMoney(state.money, cur),
                upkeepStr = GameMath.formatMoney(upkeep, cur),
                netStr = (if (net >= 0) "+" else "-") + GameMath.formatMoney(kotlin.math.abs(net), cur),
                inDebt = inDebt, netPositive = net >= 0
            )

            // витрина
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShowcaseSlot(Lifestyle.home.items[state.ownedHome], Modifier.weight(1f))
                ShowcaseSlot(Lifestyle.car.items[state.ownedCar], Modifier.weight(1f))
                ShowcaseSlot(Lifestyle.tech.items[state.ownedTech], Modifier.weight(1f))
            }
        }

        // тревога долга
        if (inDebt) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color(0x26D9694F))
                    .padding(13.dp)
            ) {
                Text("\u26A0 Вы живёте не по средствам", color = RedAccent,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Содержание ${GameMath.formatMoney(upkeep, cur)}/день съедает доход. " +
                        "Долг растёт каждый день. Продайте имущество, чтобы выбраться.",
                    color = TextMain, fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ProfileTabsRow(selected = inner, onSelect = { inner = it })

        Spacer(Modifier.height(10.dp))

        when (inner) {
            0 -> {
                Lifestyle.categories.forEach { cat ->
                    Text(cat.title, color = Mute, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val curIdx = Lifestyle.ownedIndex(state, cat.id)
                    cat.items.forEachIndexed { i, item ->
                        if (i < curIdx || i > curIdx + 1) return@forEachIndexed
                        LifeItemCard(
                            item = item, owned = i == curIdx, isBase = i == 0,
                            canBuy = i == curIdx + 1 && state.money >= item.cost,
                            cur = cur,
                            onBuy = { vm.buyLifeItem(cat.id) },
                            onSell = { vm.sellLifeItem(cat.id) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            1 -> {
                Text("РАЗОВЫЕ ВПЕЧАТЛЕНИЯ · статус навсегда", color = Mute, fontSize = 11.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Lifestyle.experiences.forEach { exp ->
                    val done = exp.id in state.experiencesDone
                    ExperienceCard(exp, done, state.money >= exp.cost, cur) { vm.buyExperience(exp.id) }
                    Spacer(Modifier.height(6.dp))
                }
            }
            2 -> ChronicleBlock(state)
            4 -> CollectionSection(vm = vm, state = state, cur = cur)
            else -> StatsBlock(state, cur)
        }
        Spacer(Modifier.height(16.dp))
    }
    }

    if (showRename) {
        PlayerNameDialog(
            initial = state.playerName,
            onConfirm = { vm.renamePlayer(it); showRename = false },
            onDismiss = { showRename = false }
        )
    }
}

/** Диалог смены имени игрока в стеклянном стиле (как EnterpriseNameDialog). */
@Composable
private fun PlayerNameDialog(
    initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1D1F25))
                .padding(18.dp)
        ) {
            Text("КАК ВАС ЗОВУТ", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text("имя видно в профиле и рейтинге", color = Gold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 18) text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Panel2)
                    .padding(12.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(Panel2)
                        .clickable(onClick = onDismiss).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Отмена", color = Mute, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                val canSave = text.isNotBlank()
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (canSave) Gold else Panel2)
                        .clickable(enabled = canSave) { onConfirm(text) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Сохранить", color = if (canSave) Color(0xFF2A2410) else Mute,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun MoneyCell(value: String, label: String, color: Color,
                      valueSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(11.dp)).background(GlassInner).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = valueSize, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Mute, fontSize = 7.5.sp, letterSpacing = 0.3.sp, lineHeight = 9.sp, maxLines = 2)
    }
}

/** Текст, ужимающийся по ширине в одну строку. */

@Composable
private fun ShowcaseSlot(item: Lifestyle.Item, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(GlassInner).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(item.emoji, fontSize = 24.sp)
        Spacer(Modifier.height(4.dp))
        Text(item.title, color = Mute, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2)
    }
}

/** Кегль подписи вкладки при обычном шрифте и наименьший её размер на экране. */
private const val TAB_MAX_SP = 11.5f
private const val TAB_MIN_DP = 8.5f

/**
 * Ряд вкладок профиля. Вынесен из ProfileScreen отдельной функцией, чтобы его можно было
 * снимать скриншот-тестом: пять подписей в один ряд — самое хрупкое место этого экрана.
 *
 * Кегль подгоняется под ширину сегмента по самой длинной подписи — тем же приёмом,
 * что и плитки сводки, иначе «Имущество» уезжает на вторую строку.
 */
@Composable
internal fun ProfileTabsRow(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Имущество" to 0, "Отдых" to 1, "Коллекция" to 4, "Хроника" to 2, "Цифры" to 3)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // ширина одного сегмента: (полная ширина − отступы ряда) / число вкладок − отступы вкладки
        val cellWidthPx = with(density) {
            ((maxWidth.toPx() - 8.dp.toPx()) / tabs.size - 4.dp.toPx()).toInt()
        }
        val longest = tabs.maxByOrNull { it.first.length }!!.first
        // кегль подбирается настоящим замером самой длинной подписи; нижняя граница задана
        // в экранных единицах, а не в sp — при системном шрифте 1.5 те же sp рисуются
        // в полтора раза крупнее, и общий для всех масштабов порог обрезал «Имущество»
        val tabFont = fitFontSp(
            longest, TextStyle(fontSize = TAB_MAX_SP.sp), cellWidthPx, TAB_MAX_SP, TAB_MIN_DP
        ).sp
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(GlassFill).padding(4.dp)
        ) {
            tabs.forEach { (label, idx) ->
                GlassTab(
                    label = label, on = selected == idx, locked = false,
                    modifier = Modifier.weight(1f),
                    fontSize = tabFont, maxLines = 1, horizontalPadding = 2.dp
                ) { onSelect(idx) }
            }
        }
    }
}

/**
 * Три числа шапки профиля: на счету / содержание / чистый доход.
 * Кегль общий для всех трёх плиток и считается по самой длинной строке — на триллионах
 * и на отрицательных значениях это единственное, что удерживает числа в одну строку.
 */
@Composable
internal fun MoneyCellsRow(
    moneyStr: String, upkeepStr: String, netStr: String,
    inDebt: Boolean, netPositive: Boolean
) {
    val maxLen = maxOf(moneyStr.length, upkeepStr.length, netStr.length)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // ширина одной плитки ≈ (полная ширина − 2 промежутка) / 3
        val cellWidthSp = with(density) {
            ((maxWidth.toPx() - 20.dp.toPx()) / 3f - 20.dp.toPx()) / fontScale / density.density
        }
        val sharedSize = remember(maxLen, cellWidthSp) {
            (cellWidthSp / (maxLen * 0.62f)).coerceIn(7f, 15f).sp
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyCell(moneyStr, if (inDebt) "ДОЛГ" else "НА СЧЕТУ",
                if (inDebt) RedAccent else TextMain, sharedSize, Modifier.weight(1f))
            MoneyCell(upkeepStr, "СОДЕРЖАНИЕ /ДЕНЬ", RedAccent, sharedSize, Modifier.weight(1f))
            MoneyCell(netStr, "ЧИСТЫЙ ДОХОД /ДЕНЬ",
                if (netPositive) GreenAccent else RedAccent, sharedSize, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun LifeItemCard(
    item: Lifestyle.Item, owned: Boolean, isBase: Boolean, canBuy: Boolean, cur: Currency,
    onBuy: () -> Unit, onSell: () -> Unit
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
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (item.status > 0)
                Text("+${item.status} статуса", color = GreenAccent, fontSize = 10.sp)
            if (item.upkeep > 0)
                Text("содержание ${GameMath.formatMoney(item.upkeep, cur)}/день",
                    color = RedAccent, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        if (owned) {
            if (isBase) Text("базовое", color = Mute, fontSize = 11.sp)
            else Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(GlassBtn)
                    .clickable(onClick = onSell).padding(horizontal = 10.dp, vertical = 7.dp)
            ) { Text("продать", color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(9.dp))
                    .background(if (canBuy) Gold else GlassBtnOff)
                    .clickable(enabled = canBuy, onClick = onBuy)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(GameMath.formatMoney(item.cost, cur), color = if (canBuy) CoinText else GlassBtnOffText,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ExperienceCard(exp: Lifestyle.Experience, done: Boolean, canBuy: Boolean, cur: Currency, onBuy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (done) GlassAccent else GlassFill).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(exp.emoji, fontSize = 22.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(exp.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("+${exp.status} статуса · разовая трата", color = Mute, fontSize = 10.sp)
        }
        if (done) Text("\u2713", color = GreenAccent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        else Box(
            Modifier.clip(RoundedCornerShape(9.dp)).background(if (canBuy) Gold else GlassBtnOff)
                .clickable(enabled = canBuy, onClick = onBuy).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(GameMath.formatMoney(exp.cost, cur), color = if (canBuy) CoinText else GlassBtnOffText,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChronicleBlock(state: GameState) {
    // музей прошлых жизней
    state.museum.mapNotNull { Museum.parse(it) }.forEach { life ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassAccent)
                .padding(13.dp)
        ) {
            Text("Жизнь ${life.num} · ${life.title}", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Spacer(Modifier.height(3.dp))
            Text("${life.days} дней · ${GameMath.format(life.earned)} $ · ${life.home} · ${life.car}",
                color = Mute, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
    }
    Text("ТЕКУЩАЯ ЖИЗНЬ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    if (state.chronicle.isEmpty()) Text("Пока тихо. Всё впереди.", color = Mute, fontSize = 12.sp)
    state.chronicle.mapNotNull { Chronicle.render(it) }.forEach { (day, text) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
            Text("день $day", color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.width(64.dp).padding(top = 2.dp))
            Text(text, color = TextMain, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun StatsBlock(state: GameState, cur: Currency) {
    val curDays = (state.gameHours / 24.0).toInt() + 1
    val totalDays = state.statDaysPrevLives + curDays
    val lives = state.museum.size + 1

    // героическая цифра
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassAccent)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(GameMath.formatMoney(state.statAllTimeEarned, cur), color = Gold,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text("ЗАРАБОТАНО ЗА ВСЕ ЖИЗНИ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp)
    }
    Spacer(Modifier.height(10.dp))

    StatGrid(listOf(
        "$lives" to "ЖИЗНЕЙ ПРОЖИТО",
        "$totalDays" to "ИГРОВЫХ ДНЕЙ СУММАРНО",
        GameMath.formatMoney(state.statBestDayIncome, cur) to "ЛУЧШИЙ ДОХОД ЗА ДЕНЬ",
        GameMath.formatFull(state.statBullionEarned) to "СЛИТКОВ ПОЛУЧЕНО ВСЕГО",
    ))

    Text("РУКИ И ГОЛОВА", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp))
    StatGrid(listOf(
        GameMath.formatFull(state.statTaps) to "ТАПОВ СДЕЛАНО",
        GameMath.formatMoney(state.statTapEarned, cur) to "ЗАРАБОТАНО ТАПАМИ",
        "${state.eduDone.size} / ${Education.allCourses.size}" to "ДИПЛОМОВ ПОЛУЧЕНО",
        "${state.statBizLevels}" to "УРОВНЕЙ БИЗНЕСА ОТКРЫТО",
    ))

    Text("ЖИЗНЬ И СТАТУС", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp))
    StatGrid(listOf(
        "${state.statLifeItems}" to "ВЕЩЕЙ КУПЛЕНО",
        "${state.milestonesClaimed} / ${Milestones.all.size}" to "ВЕХ ДОСТИГНУТО",
        Lifestyle.titles[state.statBestTitle.coerceIn(Lifestyle.titles.indices)].name to "ВЫСШИЙ ТИТУЛ",
        "${state.reputation.toInt()}" to "РЕПУТАЦИЯ",
    ))
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { (v, l) -> StatTile(v, l, Modifier.weight(1f)) }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(GlassFill).padding(12.dp)
    ) {
        Text(value, color = TextMain, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Mute, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp)
    }
}
