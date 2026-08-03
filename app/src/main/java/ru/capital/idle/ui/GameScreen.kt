package ru.capital.idle.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.theme.*

@Composable
fun GameScreen(vm: GameViewModel, resetTick: Int = 0, onNavigate: (String) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val offlineGain by vm.offlineGain.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)
    val reward = GameMath.tapReward(state) * Prestige.negotiatorMult(state)

    // отображаемые баланс, капитал и доход обновляются раз в реальную секунду, чтобы цифры не мельтешили.
    // Реальные значения считаются непрерывно из state; здесь только сглаживаем показ.
    val realBalance = state.money - state.debt
    var displayedBalance by remember { mutableStateOf(realBalance) }
    var displayedWorth by remember { mutableStateOf(GameMath.netWorth(state)) }
    var netDay by remember {
        mutableStateOf(GameMath.netIncomePerDay(state))
    }
    LaunchedEffect(Unit) {
        while (true) {
            val s = vm.state.value
            displayedBalance = s.money - s.debt
            displayedWorth = GameMath.netWorth(s)
            netDay = GameMath.netIncomePerDay(s)
            kotlinx.coroutines.delay(1000)
        }
    }
    var jobHint by remember { mutableStateOf<String?>(null) }
    var openedCategory by remember { mutableStateOf<Int?>(null) }
    // повторный тап по «Капитал» в нижнем меню закрывает экран категории и возвращает на главную
    LaunchedEffect(resetTick) { if (resetTick > 0) openedCategory = null }

    GlassBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp)
    ) {
        // шапка: логотип · дата по центру · валюта и слитки
        CapitalHeader(
            dateText = GameMath.gameDateTime(state),
            currencyCode = cur.code,
            bullionText = GameMath.format(state.bullion.toDouble()),
            onTitleLongPress = { vm.hardReset() },   // DEV-сброс (убрать перед релизом)
            onCurrency = { vm.cycleCurrency() },
            onCurrencyLong = { vm.devAddMoney() }
        )

        Spacer(Modifier.height(8.dp))

        SummaryCellsRow(
            status = Lifestyle.socialStatus(state),
            reputation = state.reputation.toInt(),
            worthText = "${cur.symbol} ${GameMath.format(displayedWorth * cur.ratePerUsd)}",
            onNavigate = onNavigate
        )

        Spacer(Modifier.height(8.dp))

        // гид онбординга
        if (Onboarding.guideActive(state)) {
            GuideCard(state, cur)
            Spacer(Modifier.height(8.dp))
        }

        // баннер удвоения за рекламу — над плашкой рынка
        AdBoostBanner(
            state = state,
            canGrant = vm.canGrantBoost(state),
            unlockInMs = vm.boostUnlockInMs(state),
            onWatch = {
                // TODO: здесь вызвать показ rewarded-видео RuStore. По колбэку награды — vm.grantAdBoost()
                vm.grantAdBoost()
            }
        )
        Spacer(Modifier.height(8.dp))

        // рынок (открывается на шаге 6)
        if (Onboarding.showMarket(state)) {
            MarketBar(state)
            Spacer(Modifier.height(8.dp))
        }

        // карта: пока новый тир не активирован, показываем активированный (старый) тир
        val earnedTier = CardTier.forWorth(GameMath.netWorth(state))
        val activatedTier = CardTier.entries.getOrElse(state.activatedCardTier) { CardTier.CLASSIC }
        val pending = earnedTier.ordinal > activatedTier.ordinal
        CardWithActivation(
            shownTier = activatedTier,
            earnedTier = earnedTier,
            pending = pending,
            onActivate = { vm.activateCardTier() }
        ) { tierToDraw ->
            CreditCard(
                money = displayedBalance,
                incomePerDay = netDay,
                reward = reward,
                currency = cur,
                playerName = state.playerName,
                tier = tierToDraw,
                onTap = {
                    vm.tap()
                    // мгновенный отклик на тап: не ждём секундного цикла
                    val s = vm.state.value
                    displayedBalance = s.money - s.debt
                    displayedWorth = GameMath.netWorth(s)
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // давление элит — отдельной плашкой под картой (после $1 млрд)
        val pressure = GameMath.pressureShown(state)
        if (pressure > 0.0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x26D9694F))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Давление элит: доход -${(pressure * 100).toInt()}%",
                    color = RedAccent, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                Text(
                    if (state.reputation > 10) "репутация смягчает" else "поднимайте репутацию",
                    color = Mute, fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            // та же награда, что и во всплывашке: разовая сумма, символ валюты впереди
            "нажмите на карту · подработка +${GameMath.formatMoney(reward, cur)}",
            color = Mute, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // распорядок дня (открывается на шаге 3)
        if (Onboarding.showSchedule(state)) {
            ScheduleBlock(state, onChange = { sl, w, b -> vm.setSchedule(sl, w, b) })
            Spacer(Modifier.height(14.dp))
        }

        // работа (открывается на шаге 2)
        if (Onboarding.showJobs(state)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("РАБОТА ПО НАЙМУ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
                if (state.jobId.isEmpty())
                    Text("вы без работы", color = RedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            // подсказка о необходимости уволиться — сразу под заголовком, в поле зрения
            if (jobHint != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                        .background(GlassFill)
                        .clickable { jobHint = null }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u2139", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(jobHint!!, color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("понятно", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
            }
            Jobs.all.forEach { job ->
                JobCard(state, job, cur,
                    onClick = {
                        if (state.jobId.isEmpty()) { vm.setJob(job.id); jobHint = null }
                        else jobHint = "Сначала увольтесь с текущей работы, потом устройтесь на новую"
                    },
                    onQuit = { vm.setJob(""); jobHint = null })
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        // отрасли (открываются на шаге 5)
        if (Onboarding.showIndustries(state)) {
            val need = GameMath.bizNeedHours(state)
            val eff = GameMath.mgmtEff(state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ВАШИ ОТРАСЛИ", color = Mute, fontSize = 11.sp, letterSpacing = 2.sp)
                if (need > 0) Text(
                    "часы: ${state.bizH}/${need}ч · ${(eff * 100).toInt()}%",
                    color = if (eff >= 1.0) GreenAccent else RedAccent,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Industries.all.forEachIndexed { i, ind ->
                CategoryCard(state, i, ind, cur) { openedCategory = i }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // полноэкранный экран категории
    openedCategory?.let { idx ->
        CategoryScreen(
            state = state, index = idx, cur = cur, vm = vm,
            onClose = { openedCategory = null }
        )
    }

    if (offlineGain.first > 0.0) {
        OfflineDialog(amount = offlineGain.first, missed = offlineGain.second,
            awaySec = offlineGain.third, currency = cur, onDismiss = { vm.clearOfflineGain() })
    }
    }
}

// ===================== блоки =====================

@Composable
private fun GuideCard(state: GameState, cur: Currency) {
    val step = Onboarding.steps.getOrNull(state.tutorialStep) ?: return
    val progress = Onboarding.progressText(state, cur)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassAccent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Gold),
            contentAlignment = Alignment.Center
        ) {
            Text("${state.tutorialStep + 1}", color = CoinText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(step.title, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(step.text, color = TextMain, fontSize = 11.5.sp, lineHeight = 16.sp)
            if (progress.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(progress, color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AdBoostBanner(
    state: GameState,
    canGrant: Boolean,
    unlockInMs: Long,
    onWatch: () -> Unit
) {
    // пересчёт раз в секунду: остаток буста и таймер до добора
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { nowTick = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }
    val remainMs = (state.boostEndsAtMillis - nowTick).coerceAtLeast(0L)
    val active = remainMs > 0L
    val hoursLeft = remainMs / 3_600_000.0          // 0..4
    val unlockMs = (remainMs - 3 * 3_600_000L).coerceAtLeast(0L)
    val canWatch = remainMs <= 3 * 3_600_000L        // добор доступен при запасе ≤ 3ч

    val accent = if (active) GreenAccent else Gold
    val bg = if (active) Color(0x1F5FBF7A) else Color(0x1FE8B54A)

    // клик по всему баннеру — только когда буста нет (вариант Б)
    val rowMod = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(bg)
        .then(if (!active) Modifier.clickable(onClick = onWatch) else Modifier)
        .padding(12.dp)

    Row(rowMod, verticalAlignment = Alignment.CenterVertically) {
        // иконка: play (нет буста) или молния (активен)
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (active) BoltIcon(accent) else PlayIcon(accent)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (active) "\u00D72 активно \u00B7 осталось ${fmtClock(remainMs)}"
                else "Удвоить доход (\u00D72 на 1 час)",
                color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp
            )
            Spacer(Modifier.height(6.dp))
            BoostBars(hoursLeft)
        }
        Spacer(Modifier.width(11.dp))
        // кнопка действия (в активном состоянии кликабельна только она)
        val btnText = when {
            !active -> "СМОТРЕТЬ"
            canWatch -> "+1 ЧАС"
            else -> "через ${fmtClock(unlockMs)}"
        }
        val btnEnabled = !active || canWatch
        val btnBg = if (btnEnabled) Gold else Color(0x14FFFFFF)
        val btnFg = if (btnEnabled) Color(0xFF2A2410) else Color(0xFF54565E)
        Box(
            Modifier
                .width(86.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(btnBg)
                .then(if (active && btnEnabled) Modifier.clickable(onClick = onWatch) else Modifier)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(btnText, color = btnFg, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** Полоса запаса: 4 секции, дробная зелёная заливка (hoursLeft из 4). */
@Composable
private fun BoostBars(hoursLeft: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 4) {
            val fill = (hoursLeft - i).coerceIn(0.0, 1.0).toFloat()
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color(0x1AFFFFFF))
            ) {
                if (fill > 0f) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp)).background(GreenAccent))
                }
            }
        }
    }
}

/** Часы:минуты или минуты:секунды из миллисекунд. */
private fun fmtClock(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d", h, m)
    else String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
}

@Composable
private fun PlayIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.5f)
            lineTo(size.width * 0.25f, size.height * 0.82f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun BoltIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.55f, h * 0.08f)
            lineTo(w * 0.22f, h * 0.55f)
            lineTo(w * 0.46f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.92f)
            lineTo(w * 0.78f, h * 0.42f)
            lineTo(w * 0.54f, h * 0.42f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun MarketBar(state: GameState) {
    val phase = state.phase
    val color = when (phase) {
        MarketPhase.GROWTH -> GreenAccent
        MarketPhase.BOOM -> Gold
        MarketPhase.CRISIS -> RedAccent
        MarketPhase.RECOVERY -> Color(0xFF6A9BD8)
    }
    val mult = GameMath.crisisMult(state)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            "Рынок: ${phase.title}" + if (phase.sale < 1.0) " · бизнесы дешевле" else "",
            color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text("×${GameMath.decimal(mult, 2)}",
            color = if (mult >= 1.0) GreenAccent else RedAccent,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ScheduleBlock(state: GameState, onChange: (Int, Int, Int) -> Unit) {
    val eff = GameMath.awakeEff(state)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassFill)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Распорядок дня", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${state.sleepH}/${state.workH}/${state.bizH}/${state.studyHCalc}",
                color = Mute, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))

        val carBonus = Lifestyle.carExtraHours(state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Сон: ${state.sleepH}ч", color = Color(0xFFA98BD8), fontSize = 10.sp)
            Text(
                if (eff >= 1.0) "выспались · 100%" else "недосып · ${(eff * 100).toInt()}%",
                color = if (eff >= 1.0) GreenAccent else if (eff >= 0.85) Gold else RedAccent,
                fontSize = 10.sp
            )
        }
        GoldSlider(state.sleepH.toFloat(), 3f..9f, 5) { onChange(it.toInt(), state.workH, state.bizH) }

        val maxWork = (state.dayBudget - state.bizH).coerceAtLeast(1)
        Text("Работа по найму: ${state.workH}ч", color = GreenAccent, fontSize = 10.sp)
        GoldSlider(state.workH.toFloat(), 0f..maxWork.toFloat(), (maxWork - 1).coerceAtLeast(0)) {
            onChange(state.sleepH, it.toInt(), state.bizH)
        }

        val maxBiz = (state.dayBudget - state.workH).coerceAtLeast(1)
        DayPlanLabels(bizH = state.bizH, studyH = state.studyHCalc, carBonus = carBonus)
        GoldSlider(state.bizH.toFloat(), 0f..maxBiz.toFloat(), (maxBiz - 1).coerceAtLeast(0)) {
            onChange(state.sleepH, state.workH, it.toInt())
        }
    }
}

@Composable
private fun GoldSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range, steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Gold, activeTrackColor = GoldDim,
            inactiveTrackColor = Panel2, activeTickColor = Gold, inactiveTickColor = LineColor
        ),
        modifier = Modifier.height(26.dp)
    )
}

@Composable
private fun JobCard(state: GameState, job: Job, cur: Currency, onClick: () -> Unit, onQuit: () -> Unit) {
    val ok = job.reqCourse == null || job.reqCourse in state.eduDone
    val current = state.jobId == job.id
    val daily = job.ratePerHour * state.workH * GameMath.awakeEff(state) * Prestige.negotiatorMult(state)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (current) GlassAccent else GlassFill)
            .clickable(enabled = ok && !current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title, color = if (ok) TextMain else Mute, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Text("· вы здесь", color = GreenAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("${GameMath.formatAmount(job.ratePerHour, cur)} ${cur.symbol}/час" +
                if (!current && ok) " · нанимает" else "",
                color = Mute, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            if (!ok) Text("нужно: ${Education.byId(job.reqCourse!!)?.title}",
                color = RedAccent, fontSize = 10.sp)
        }
        if (current) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${GameMath.formatAmount(daily, cur)} ${cur.symbol}/день",
                    color = Gold, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassBtn)
                        .clickable(onClick = onQuit)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("уволиться", color = Mute, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(state: GameState, index: Int, ind: Industry, cur: Currency, onOpen: () -> Unit) {
    val list = state.enterprises.getOrElse(index) { emptyList() }
    val count = list.size
    // показываем все отрасли (запертые — с замком), чтобы была видна прогрессия

    val gross = list.sumOf { GameMath.enterpriseGrossPerDay(state, ind, it) }
    val salaryHere = list.sumOf { it.manager?.salaryPerDay ?: 0.0 }
    val dayNet = gross - salaryHere

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (count > 0) GlassAccent else GlassFill)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(ind.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (count > 0)
                Text((if (dayNet >= 0) "+" else "-") + "${GameMath.formatAmount(kotlin.math.abs(dayNet), cur)} ${cur.symbol}/день · предприятий $count/${BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY}",
                    color = if (dayNet >= 0) GreenAccent else RedAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            else {
                val gate = GameMath.openGate(state, index)
                when {
                    !gate.eduOk -> Text("\uD83D\uDD12 нужно образование: ${Education.byId(gate.needEdu!!)?.title}",
                        color = RedAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    !gate.statusOk -> Text("\uD83D\uDD12 нужен статус ${gate.needStatus} · у вас ${gate.haveStatus}",
                        color = RedAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    else -> Text("от ${GameMath.formatMoney(GameMath.openEnterpriseCost(state, index), cur)} · нажмите, чтобы открыть",
                        color = Mute, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
        Text("\u203A", color = Mute, fontSize = 22.sp)
    }
}

@Composable
private fun CategoryScreen(state: GameState, index: Int, cur: Currency, vm: GameViewModel, onClose: () -> Unit) {
    val ind = Industries.all[index]
    val list = state.enterprises.getOrElse(index) { emptyList() }
    var mgrSheetFor by remember { mutableStateOf<Int?>(null) }   // индекс предприятия для шторки управляющих
    var showCreateDialog by remember { mutableStateOf(false) }    // диалог названия при создании
    var renameFor by remember { mutableStateOf<Int?>(null) }      // индекс предприятия для переименования

    val gate = GameMath.openGate(state, index)
    val openCost = GameMath.openEnterpriseCost(state, index)

    GlassBackground {
    Column(
        Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(8.dp)) {
                Text("\u2039 назад", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(ind.title, color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(10.dp))

        // сводка по этой категории
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val manualHere = list.count { it.isManual }
            val hoursHere = manualHere * BusinessConfig.HOURS_PER_MANUAL_ENTERPRISE
            val salaryHere = list.sumOf { it.manager?.salaryPerDay ?: 0.0 }
            val grossHere = list.sumOf { GameMath.enterpriseGrossPerDay(state, ind, it) }
            val netHere = grossHere - salaryHere
            SummaryCell("${hoursHere}ч", "ВАШИ ЧАСЫ", TextMain, Modifier.weight(0.9f))
            SummaryCell(GameMath.formatMoney(salaryHere, cur), "ЗАРПЛАТЫ /ДЕНЬ", RedAccent, Modifier.weight(0.9f))
            SummaryCell(
                (if (netHere >= 0) "+" else "-") + GameMath.formatMoney(kotlin.math.abs(netHere), cur),
                "ЧИСТЫЙ /ДЕНЬ", if (netHere >= 0) GreenAccent else RedAccent, Modifier.weight(1.7f))
        }
        Spacer(Modifier.height(12.dp))

        // насыщение рынка отрасли
        val satur = (GameMath.industrySaturation(state, index) * 100).toInt()
        if (list.size > 1) {
            Text("Насыщение рынка: отдача с каждого предприятия ${satur}%. Чем больше одинаковых, тем меньше доход с каждого.",
                color = if (satur < 100) Color(0xFF6A9BD8) else Mute, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        // кнопка открыть новое
        val openEnabled = gate.ok && state.money >= openCost
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(if (openEnabled) GlassAccent else GlassFill)
                .clickable(enabled = openEnabled) { showCreateDialog = true }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    !gate.eduOk -> "нужно образование: ${Education.byId(gate.needEdu!!)?.title}"
                    !gate.statusOk -> "нужен статус ${gate.needStatus} (у вас ${gate.haveStatus}) · поднимите образ жизни"
                    !gate.limitOk -> "максимум предприятий (${BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY})"
                    else -> "+ Открыть предприятие (${list.size}/${BusinessConfig.MAX_ENTERPRISES_PER_INDUSTRY}) · ${GameMath.formatMoney(openCost, cur)}"
                },
                color = if (openEnabled) Gold else Mute, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        // индикатор нехватки часов: личные предприятия проседают, если часов не хватает
        val needHrs = GameMath.bizNeedHours(state)
        val effPct = (GameMath.mgmtEff(state) * 100).toInt()
        if (needHrs > state.bizH) {
            Spacer(Modifier.height(6.dp))
            Text("Личным предприятиям нужно ${needHrs}ч, а у вас ${state.bizH}ч на бизнес — доход личных просел до ${effPct}%. Поставьте управляющих или добавьте часы в распорядке.",
                color = RedAccent, fontSize = 10.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))

        // список предприятий
        list.forEachIndexed { j, e ->
            EnterpriseCard(state, ind, index, j, e, cur,
                onUpgrade = { vm.upgradeEnterprise(index, j) },
                onManager = { mgrSheetFor = j },
                onRename = { renameFor = j })
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(20.dp))
    }

    // диалог названия при создании
    if (showCreateDialog) {
        EnterpriseNameDialog(
            title = "Название предприятия",
            subtitle = "${ind.title} · ${ind.levels[0].name}",
            initial = EnterpriseNames.random(ind.id),
            industryId = ind.id,
            confirmText = "Создать",
            onConfirm = { name -> vm.openEnterprise(index, name); showCreateDialog = false },
            onDismiss = { showCreateDialog = false }
        )
    }

    // диалог переименования
    renameFor?.let { j ->
        val e = list.getOrNull(j)
        if (e != null) EnterpriseNameDialog(
            title = "Переименовать",
            subtitle = "${ind.title} · ${ind.levels.getOrElse(e.level) { ind.levels.last() }.name}",
            initial = e.name.ifEmpty { EnterpriseNames.random(ind.id) },
            industryId = ind.id,
            confirmText = "Сохранить",
            onConfirm = { name -> vm.renameEnterprise(index, j, name); renameFor = null },
            onDismiss = { renameFor = null }
        )
    }

    // шторка управляющих
    mgrSheetFor?.let { j ->
        val e = list.getOrNull(j)
        if (e != null) ManagerSheet(
            current = e.manager,
            cur = cur,
            onPick = { ord -> vm.assignManager(index, j, ord); mgrSheetFor = null },
            onFire = { vm.fireManager(index, j); mgrSheetFor = null },
            onDismiss = { mgrSheetFor = null }
        )
    }
    }
}

/**
 * На сколько подпись тира приподнята над строкой номера карты. Ровно столько,
 * сколько давал прежний оверлей с отступом 56dp от низа карты, — правка вёрстки
 * не должна менять вид при обычном шрифте.
 */
private val TIER_TITLE_LIFT = (-6.33).dp

/** Декоративный номер на карте. Строка постоянная, поэтому её ширину можно измерить заранее. */
private const val CARD_NUMBER = "\u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 4040"

/**
 * С какого масштаба системного шрифта дата переезжает на свою строку.
 *
 * Ровно единица, без запаса: при обычном шрифте датой остаётся около 10dp свободной ширины,
 * так что уже ближайший системный шаг (1.15) её обрезает. Порог «строго больше 1» держит
 * обычный шрифт в прежней однострочной шапке и уводит дату вниз при любом увеличении.
 * Решать замером было бы точнее, но ширину валютной плашки и слитков пришлось бы
 * пересобирать из отступов вручную, а цена ошибки там — обрезанная дата.
 */
private const val HEADER_COMPACT_FONT_SCALE = 1f

/** Кегль подписи под числом в сводке и наименьший её размер на экране. */
private const val SUMMARY_LABEL_SP = 7.5f
private const val SUMMARY_LABEL_MIN_DP = 6f

/** Подписи третьей строки распорядка: часы бизнеса слева, учёба и транспорт справа. */
@Composable
internal fun DayPlanLabels(bizH: Int, studyH: Int, carBonus: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Свой бизнес: ${bizH}ч", color = Gold, fontSize = 10.sp)
        Text(
            "Учёба: ${studyH}ч" + if (carBonus > 0) " · транспорт +${carBonus}ч" else "",
            color = Color(0xFF6A9BD8), fontSize = 10.sp
        )
    }
}

/**
 * Шапка экрана «Капитал»: заголовок, игровая дата, валюта и слитки.
 * Вынесена отдельно, чтобы вёрстку шапки можно было снять скриншотом без ViewModel.
 */
@Composable
internal fun CapitalHeader(
    dateText: String,
    currencyCode: String,
    bullionText: String,
    onTitleLongPress: () -> Unit = {},
    onCurrency: () -> Unit = {},
    onCurrencyLong: () -> Unit = {}
) {
    // При заметно увеличенном системном шрифте заголовок, валюта и слитки съедают почти всю
    // ширину: даты остаётся полоска в считаные десятки dp, и она рвалась на три строки посреди
    // числа — «28.02» / «.36 ·» / «19:00». Тогда дата уходит на свою строку, где помещается
    // целиком. Порог сравнивает масштаб шрифта, а не ширину: при обычном шрифте ветка та же,
    // что и была, вплоть до пикселя.
    val dateOnOwnLine = LocalDensity.current.fontScale > HEADER_COMPACT_FONT_SCALE

    @Composable
    fun chips() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CurrencyChip(code = currencyCode, onClick = onCurrency, onLongPress = onCurrencyLong)
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(GlassFill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoldBar(size = 13.dp)
                Spacer(Modifier.width(5.dp))
                Text(bullionText, color = Gold,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    @Composable
    fun title() {
        Text("КАПИТАЛ", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 2.sp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onTitleLongPress() })
            })
    }

    @Composable
    fun date(modifier: Modifier) {
        Text(dateText, color = Mute,
            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            maxLines = 1, softWrap = false,
            modifier = modifier, textAlign = TextAlign.Center)
    }

    if (dateOnOwnLine) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                title()
                Spacer(Modifier.weight(1f))
                chips()
            }
            Spacer(Modifier.height(4.dp))
            date(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            title()
            date(Modifier.weight(1f))
            chips()
        }
    }
}

/** Сводка под шапкой: статус и репутация узкие, капитал широкий (полное число до миллиарда). */
@Composable
internal fun SummaryCellsRow(
    status: Int, reputation: Int, worthText: String, onNavigate: (String) -> Unit = {}
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCell("$status", "СТАТУС", TextMain, Modifier.weight(0.9f),
            onClick = { onNavigate("profile") })
        SummaryCell("$reputation", "РЕПУТАЦИЯ", GreenAccent, Modifier.weight(0.9f),
            onClick = { onNavigate("network") })
        SummaryCell(worthText, "КАПИТАЛ", Gold, Modifier.weight(1.7f),
            onClick = { onNavigate("rank") })
    }
}

@Composable
private fun SummaryCell(value: String, label: String, color: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column((if (onClick != null) modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            else modifier.clip(RoundedCornerShape(12.dp)))
            .background(GlassFill).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        // подпись ужимается по самому длинному слову: «РЕПУТАЦИЯ» при крупном системном
        // шрифте переносила последнюю букву на вторую строку — перенос посреди слова.
        // Многословные подписи («ЗАРПЛАТЫ /ДЕНЬ») по-прежнему могут занять две строки
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val style = TextStyle(fontSize = SUMMARY_LABEL_SP.sp, letterSpacing = 0.5.sp)
            val longestWord = label.split(' ').maxByOrNull { it.length }.orEmpty()
            val sp = fitFontSp(longestWord, style, constraints.maxWidth,
                SUMMARY_LABEL_SP, SUMMARY_LABEL_MIN_DP)
            Text(label, color = Mute, fontSize = sp.sp, letterSpacing = 0.5.sp,
                lineHeight = (sp + 1.5f).sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun EnterpriseNameDialog(
    title: String, subtitle: String, initial: String, industryId: String,
    confirmText: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1D1F25))
                .padding(18.dp)
        ) {
            Text(title, color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(subtitle, color = Gold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= EnterpriseNames.MAX_NAME_LEN) text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(Panel2)
                        .padding(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(Panel2)
                        .clickable { text = EnterpriseNames.random(industryId) },
                    contentAlignment = Alignment.Center
                ) { Text("\uD83C\uDFB2", fontSize = 22.sp) }   // игральная кость
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(Panel2)
                        .clickable(onClick = onDismiss).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Отмена", color = Mute, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(Gold)
                        .clickable { onConfirm(text) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(confirmText, color = Color(0xFF2A2410), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Text("кубик подбирает случайное название · можно вписать своё",
                color = Mute, fontSize = 10.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EnterpriseCard(
    state: GameState, ind: Industry, index: Int, entIndex: Int, e: Enterprise, cur: Currency,
    onUpgrade: () -> Unit, onManager: () -> Unit, onRename: () -> Unit
) {
    val lvl = ind.levels.getOrElse(e.level) { ind.levels.last() }
    val income = GameMath.enterpriseGrossPerDay(state, ind, e)   // со всеми множителями (рынок, часы, престиж)
    val salary = e.manager?.salaryPerDay ?: 0.0
    val net = income - salary   // чистый вклад предприятия
    val nextLv = e.level + 1
    val hasNext = nextLv < ind.levels.size
    val nextLevel = if (hasNext) ind.levels[nextLv] else null
    val eduOk = nextLevel?.reqCourse == null || nextLevel.reqCourse in state.eduDone
    val upCost = if (hasNext) GameMath.upgradeEnterpriseCost(state, index, entIndex) else 0.0
    val canUp = hasNext && eduOk && state.money >= upCost

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (e.isManual) GlassAccent else GlassFill)
            .padding(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                val displayName = e.name.ifEmpty { lvl.name }
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onRename)) {
                    Text(displayName, color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    Text("\u270E", color = Mute, fontSize = 11.sp)   // карандаш: можно переименовать
                }
                Text(lvl.name, color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1)
                Text("уровень ${e.level + 1}/${ind.levels.size}",
                    color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (net >= 0) "+" else "-") + "${GameMath.formatAmount(kotlin.math.abs(net), cur)} ${cur.symbol}/день",
                    color = if (net >= 0) GreenAccent else RedAccent,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (e.isManual) "вы лично · 100%" else "${e.manager!!.title} · ${(e.efficiency * 100).toInt()}%",
                    color = if (e.isManual) Gold else Mute, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (e.isManual) {
            Text(
                "\u23F1 занимает ${BusinessConfig.HOURS_PER_MANUAL_ENTERPRISE} ваших часа \u00B7 выручка ${GameMath.formatAmount(income, cur)} ${cur.symbol}/день",
                color = Gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
        } else {
            Text(
                "\uD83D\uDCBC ${e.manager!!.title} \u00B7 выручка ${GameMath.formatAmount(income, cur)} ${cur.symbol}/день",
                color = Color(0xFF6A9BD8), fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
            Text(
                "зарплата ${GameMath.formatAmount(salary, cur)} ${cur.symbol}/день",
                color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            // улучшить
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (canUp) GlassAccent else GlassBtnOff)
                    .clickable(enabled = canUp, onClick = onUpgrade).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (!hasNext) "макс. уровень"
                    else if (!eduOk) "нужно: ${Education.byId(nextLevel!!.reqCourse!!)?.title}"
                    else "Улучшить · ${GameMath.formatMoney(upCost, cur)}",
                    color = if (canUp) Gold else Mute, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1
                )
            }
            // управляющий
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(Color(0x265FBF7A))
                    .clickable(onClick = onManager).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (e.isManual) "Поставить управляющего" else "Сменить управляющего",
                    color = GreenAccent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ManagerSheet(
    current: Manager?, cur: Currency,
    onPick: (Int) -> Unit, onFire: () -> Unit, onDismiss: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp))
                .background(Panel).clickable(enabled = false) {}.padding(16.dp)
        ) {
            Text("Назначить управляющего", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                if (current != null) "Сейчас управляет: ${current.title}. Можно сменить или снять."
                else "Чем выше эффективность, тем больше зарплата.",
                color = Mute, fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            Manager.entries.forEachIndexed { i, m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel2)
                        .clickable { onPick(i) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("эффективность ${(m.eff * 100).toInt()}%", color = Mute,
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${(m.eff * 100).toInt()}%",
                            color = if (m.eff >= 1.0) Gold else if (m.eff >= 0.8) GreenAccent else Mute,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${GameMath.formatMoney(m.salaryPerDay, cur)}/день", color = RedAccent,
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (current != null) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x26D9694F))
                        .clickable(onClick = onFire).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Снять управляющего (вернуть себе)", color = RedAccent,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(8.dp),
                contentAlignment = Alignment.Center) {
                Text("отмена", color = Mute, fontSize = 12.sp)
            }
        }
    }
}

// ===================== Банковская карта (дизайн сохранён) =====================

@Composable
private fun CardWithActivation(
    shownTier: CardTier,
    earnedTier: CardTier,
    pending: Boolean,
    onActivate: () -> Unit,
    card: @Composable (CardTier) -> Unit
) {
    // фаза: ждём активации (блюр + поздравление) → тап → анимация проявления новой карты
    var activating by remember { mutableStateOf(false) }
    val tierForCard = if (activating) earnedTier else shownTier

    // блюр старой карты, пока ждём активации
    val blur by animateFloatAsState(
        targetValue = if (pending && !activating) 10f else 0f,
        animationSpec = tween(700), label = "cardBlur"
    )
    // проявление новой карты после тапа
    val reveal by animateFloatAsState(
        targetValue = if (activating) 1f else 0f,
        animationSpec = tween(900), label = "cardReveal"
    )
    // золотое свечение в момент активации
    val glowAlpha by animateFloatAsState(
        targetValue = if (activating && reveal < 1f) 0.5f else 0f,
        animationSpec = tween(1100), label = "cardGlow"
    )

    LaunchedEffect(activating) {
        if (activating) {
            kotlinx.coroutines.delay(950)
            onActivate()        // фиксируем тир в состоянии
            activating = false  // дальше карта показывается обычным образом (уже новый тир по состоянию)
        }
    }

    Box(Modifier.fillMaxWidth()) {
        // сама карта (с блюром, если ждём активацию)
        Box(
            Modifier
                .then(if (glowAlpha > 0f) Modifier.shadow(20.dp, RoundedCornerShape(18.dp),
                    ambientColor = Gold.copy(alpha = glowAlpha), spotColor = Gold.copy(alpha = glowAlpha)) else Modifier)
                .then(if (blur > 0f) Modifier.blur(blur.dp) else Modifier)
                .then(if (activating) Modifier.graphicsLayer { alpha = 0.4f + 0.6f * reveal } else Modifier)
        ) {
            card(tierForCard)
        }

        // оверлей поздравления поверх заблокированной карты
        if (pending && !activating) {
            Box(
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x77000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text("\u2726", color = Gold, fontSize = 30.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Новый уровень привилегий", color = TextMain,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text(earnedTier.title, color = Gold, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 2.sp)
                    if (earnedTier.passiveBonus > 0.0) {
                        Spacer(Modifier.height(2.dp))
                        Text("+${(earnedTier.passiveBonus * 100).toInt()}% к доходности накоплений",
                            color = GreenAccent, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Gold)
                            .clickable { activating = true }
                            .padding(horizontal = 22.dp, vertical = 11.dp)
                    ) {
                        Text("Активировать карту", color = Color(0xFF2A2410),
                            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreditCard(
    money: Double, incomePerDay: Double, reward: Double, currency: Currency,
    playerName: String, tier: CardTier, onTap: () -> Unit
) {
    var popAccum by remember { mutableStateOf(0.0) }
    var popTick by remember { mutableStateOf(0) }
    val rewardNow by rememberUpdatedState(reward)

    val accent = Color(tier.accent)
    val txt = Color(tier.textColor)
    val gradient = tier.gradient.map { Color(it) }
    val kant = if (tier.kant != 0L) Color(tier.kant) else null
    val glow = if (tier.glow != 0L) Color(tier.glow) else null

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 218.dp)
            .then(if (glow != null) Modifier.shadow(18.dp, RoundedCornerShape(18.dp),
                ambientColor = glow, spotColor = glow) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(gradient))
            .then(if (kant != null) Modifier.border(1.dp, kant, RoundedCornerShape(18.dp)) else Modifier)
            .drawBehind { drawCardPattern(tier.pattern, Color(tier.patternColor)) }
            .pointerInput(Unit) {
                detectTapGestures {
                    onTap()
                    popAccum += rewardNow
                    popTick++
                }
            }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text("AURUM BANK", color = accent, fontSize = 15.sp, letterSpacing = 2.sp)
            }

            CardChip(accent)

            Column {
                Text(if (money < 0) "ДОЛГ" else "ДОСТУПНО", color = accent.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 2.sp)
                BalanceWithTapPop(
                    money = money, accum = popAccum, tick = popTick, currency = currency,
                    moneyColor = if (money < 0) RedAccent else txt,
                    onExpire = { popAccum = 0.0 }
                )
                Text((if (incomePerDay >= 0) "+" else "-") + "${GameMath.formatAmount(kotlin.math.abs(incomePerDay), currency)} ${currency.symbol}/день " + (if (incomePerDay >= 0) "поступает" else "убыток"),
                    color = if (incomePerDay >= 0) GreenAccent else RedAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }

            // \u041f\u043e\u0434\u043f\u0438\u0441\u044c \u0442\u0438\u0440\u0430 \u0441\u0442\u043e\u0438\u0442 \u0432 \u043f\u043e\u0442\u043e\u043a\u0435, \u0430 \u043d\u0435 \u043e\u0432\u0435\u0440\u043b\u0435\u0435\u043c \u043d\u0430 \u0444\u0438\u043a\u0441\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u043e\u0439 \u0432\u044b\u0441\u043e\u0442\u0435: \u043f\u0440\u0438 \u043a\u0440\u0443\u043f\u043d\u043e\u043c
            // \u0441\u0438\u0441\u0442\u0435\u043c\u043d\u043e\u043c \u0448\u0440\u0438\u0444\u0442\u0435 \u0441\u043e\u0434\u0435\u0440\u0436\u0438\u043c\u043e\u0435 \u043a\u0430\u0440\u0442\u044b \u0443\u0435\u0437\u0436\u0430\u043b\u043e \u0432\u043d\u0438\u0437, \u0438 \u043e\u0432\u0435\u0440\u043b\u0435\u0439 \u043f\u0435\u0447\u0430\u0442\u0430\u043b\u0441\u044f \u043f\u043e\u0432\u0435\u0440\u0445 \u043d\u043e\u043c\u0435\u0440\u0430.
            // \u0415\u0441\u043b\u0438 \u0440\u044f\u0434\u043e\u043c \u0441 \u043d\u043e\u043c\u0435\u0440\u043e\u043c \u0435\u0439 \u0443\u0436\u0435 \u043d\u0435 \u0445\u0432\u0430\u0442\u0430\u0435\u0442 \u0448\u0438\u0440\u0438\u043d\u044b \u2014 \u0443\u0445\u043e\u0434\u0438\u0442 \u043d\u0430 \u0441\u0432\u043e\u044e \u0441\u0442\u0440\u043e\u043a\u0443 \u0432\u044b\u0448\u0435:
            // \u0443\u0436\u0438\u043c\u0430\u0442\u044c \u0435\u0451 \u043d\u0435\u043a\u0443\u0434\u0430, \u0440\u0430\u0437\u0440\u044f\u0434\u043a\u0430 \u0432 3sp \u0441\u044a\u0435\u0434\u0430\u0435\u0442 \u0431\u043e\u043b\u044c\u0448\u0435, \u0447\u0435\u043c \u0441\u0430\u043c\u0438 \u0431\u0443\u043a\u0432\u044b.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val numberStyle = TextStyle(fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, letterSpacing = 2.sp)
                val tierStyle = TextStyle(fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                val nameStyle = TextStyle(fontSize = 15.sp, letterSpacing = 1.sp)
                val gapPx = with(LocalDensity.current) { 10.dp.toPx() }
                // левая колонка шире номера, если имя игрока длиннее: в диалоге до 18 знаков,
                // и по такому имени она и меряется. Считать только по номеру значило бы
                // оставить подпись тира в строке, где ей уже не хватает места
                val leftWidth = maxOf(
                    measurer.measure(CARD_NUMBER, numberStyle).size.width,
                    if (playerName.isBlank()) 0
                    else measurer.measure(playerName.uppercase(java.util.Locale.ROOT), nameStyle).size.width
                )
                val sameLine = leftWidth +
                    measurer.measure(tier.title, tierStyle).size.width + gapPx <= constraints.maxWidth

                Column(Modifier.fillMaxWidth()) {
                    if (!sameLine) {
                        Text(
                            tier.title, color = accent, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 3.sp,
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.align(Alignment.End).padding(end = 2.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(CARD_NUMBER,
                                color = accent.copy(alpha = 0.85f), fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp, letterSpacing = 2.sp, maxLines = 1, softWrap = false)
                            Spacer(Modifier.height(3.dp))
                            if (playerName.isNotBlank()) {
                                Text(playerName.uppercase(java.util.Locale.ROOT), color = txt, fontSize = 15.sp, letterSpacing = 1.sp,
                                    maxLines = 1, lineHeight = 18.sp)
                            }
                        }
                        if (sameLine) {
                            Text(
                                tier.title, color = accent, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 3.sp,
                                maxLines = 1, softWrap = false,
                                modifier = Modifier
                                    .align(Alignment.Top)
                                    // \u043f\u043e\u0434\u044a\u0451\u043c \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u0430\u0432\u043b\u0438\u0432\u0430\u0435\u0442 \u0440\u043e\u0432\u043d\u043e \u0442\u043e \u043f\u043e\u043b\u043e\u0436\u0435\u043d\u0438\u0435, \u0432 \u043a\u043e\u0442\u043e\u0440\u043e\u043c \u043f\u043e\u0434\u043f\u0438\u0441\u044c
                                    // \u0441\u0442\u043e\u044f\u043b\u0430 \u043e\u0432\u0435\u0440\u043b\u0435\u0435\u043c \u043f\u0440\u0438 \u043e\u0431\u044b\u0447\u043d\u043e\u043c \u0448\u0440\u0438\u0444\u0442\u0435 \u2014 \u0447\u0443\u0442\u044c \u0432\u044b\u0448\u0435 \u0441\u0442\u0440\u043e\u043a\u0438 \u043d\u043e\u043c\u0435\u0440\u0430
                                    .offset(y = TIER_TITLE_LIFT)
                                    // 2dp \u043a \u043e\u0442\u0441\u0442\u0443\u043f\u0443 \u043a\u043e\u043b\u043e\u043d\u043a\u0438: \u0433\u0430\u0441\u0438\u0442 \u0445\u0432\u043e\u0441\u0442 letterSpacing \u0441\u043f\u0440\u0430\u0432\u0430,
                                    // \u0447\u0442\u043e\u0431\u044b \u0432\u0438\u0434\u0438\u043c\u044b\u0439 \u043a\u0440\u0430\u0439 \u0431\u0443\u043a\u0432 \u0432\u0441\u0442\u0430\u043b \u0432\u0440\u043e\u0432\u0435\u043d\u044c \u0441 \u0438\u043a\u043e\u043d\u043a\u043e\u0439 \u0438 \u0440\u043e\u043c\u0431\u0430\u043c\u0438
                                    .padding(end = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // правые элементы — оверлеи с общим правым отступом (end = 16dp), стоят по одной вертикали.
        // Вертикаль каждого регулируется своим top/bottom.
        ContactlessIcon(accent,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 16.dp))
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 16.dp)) {
            PaySymbol()
        }
    }
}

/** Фоновый узор карты, отрисовка на Canvas. */
private fun DrawScope.drawCardPattern(pattern: CardPattern, color: Color) {
    when (pattern) {
        CardPattern.NONE -> {}
        CardPattern.DIAGONAL -> {
            val gap = 7.dp.toPx()
            val shift = size.height * 0.5f
            var xx = -shift
            while (xx < size.width) {
                drawLine(color, Offset(xx, 0f), Offset(xx + shift, size.height), 1f)
                xx += gap
            }
        }
        CardPattern.GUILLOCHE -> {
            // переплетающиеся синусоиды
            val rows = 9
            val step = size.height / rows
            for (r in 0..rows) {
                val baseY = r * step
                val path = Path().apply {
                    moveTo(0f, baseY)
                    var x = 0f
                    val wl = size.width / 4f
                    while (x <= size.width) {
                        val y = baseY + kotlin.math.sin(x / wl * Math.PI).toFloat() * step * 0.45f
                        lineTo(x, y)
                        x += 8f
                    }
                }
                drawPath(path, color, style = Stroke(width = 1f))
            }
        }
        CardPattern.ARCS -> {
            // концентрические дуги из правого верхнего угла
            val cx = size.width * 0.85f
            val cy = size.height * 0.18f
            var r = 14.dp.toPx()
            val gap = 14.dp.toPx()
            while (r < size.width) {
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(width = 1f))
                r += gap
            }
        }
        CardPattern.DOTS -> {
            val gap = 12.dp.toPx()
            val rad = 0.8.dp.toPx()
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) {
                    drawCircle(color, radius = rad, center = Offset(x, y))
                    x += gap
                }
                y += gap
            }
        }
        CardPattern.HONEYCOMB -> {
            // редкие концентрические дуги-сияние для топ-тира
            val cx = size.width * 0.8f
            val cy = size.height * 0.2f
            var r = 16.dp.toPx()
            val gap = 18.dp.toPx()
            while (r < size.width * 1.1f) {
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(width = 1.2f))
                r += gap
            }
        }
    }
}

@Composable
private fun CardChip(accent: Color = Color(0xFFE8B54A)) {
    Box(
        Modifier
            .size(width = 42.dp, height = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF3D886), Color(0xFFC79A36))))
            .drawBehind {
                val c = Color(0x33000000)
                val lw = 1.dp.toPx()
                drawLine(c, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), lw)
                drawLine(c, Offset(size.width * 0.34f, 0f), Offset(size.width * 0.34f, size.height), lw)
                drawLine(c, Offset(size.width * 0.66f, 0f), Offset(size.width * 0.66f, size.height), lw)
            }
    )
}

@Composable
private fun ContactlessIcon(accent: Color = Mute, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 22.dp, height = 26.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val cy = size.height / 2f
        val fracs = floatArrayOf(0.19f, 0.35f, 0.50f, 0.65f)
        // правый выступ самой большой дуги совпадает с правым краем холста (прижато вправо)
        val rMax = size.height * fracs.max()
        val cx = size.width - rMax
        for (f in fracs) {
            val r = size.height * f
            drawArc(
                color = accent, startAngle = -42f, sweepAngle = 84f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = stroke
            )
        }
    }
}

@Composable
private fun PaySymbol() {
    // фирменный знак: два ромба (красный + золотой). Ширина = реальный правый край ромба,
    // чтобы при выносе в оверлей правый край совпадал с рамкой (прижато вправо).
    Box(Modifier.size(width = 49.dp, height = 32.dp)) {
        Box(
            Modifier.size(30.dp).offset(y = 1.dp)
                .rotate(45f).clip(RoundedCornerShape(5.dp))
                .background(RedAccent.copy(alpha = 0.95f))
        )
        Box(
            Modifier.size(30.dp).offset(x = 19.dp, y = 1.dp)
                .rotate(45f).clip(RoundedCornerShape(5.dp))
                .background(Gold.copy(alpha = 0.62f))
        )
    }
}

@Composable
private fun CurrencyChip(code: String, onClick: () -> Unit, onLongPress: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x14FFFFFF))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })  // long = DEV-чит денег
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(code, color = Mute, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun GoldBar(size: Dp = 14.dp) {
    Canvas(Modifier.size(width = size * 1.413f, height = size)) {
        val w = this.size.width
        val h = this.size.height
        val face0 = Path().apply {
            moveTo(w * 0.6695f, 0f); lineTo(w * 0.1271f, h * 0.4426f)
            lineTo(w * 0.3305f, h * 0.6086f); lineTo(w * 0.8729f, h * 0.166f); close()
        }
        drawPath(face0, Color(0xFFCEA141))
        val face1 = Path().apply {
            moveTo(w * 1.0f, h * 0.4467f); lineTo(w * 0.322f, h * 1.0f)
            lineTo(w * 0.3305f, h * 0.6086f); lineTo(w * 0.8729f, h * 0.166f); close()
        }
        drawPath(face1, Color(0xFFA27F33))
        val face2 = Path().apply {
            moveTo(0f, h * 0.7372f); lineTo(w * 0.322f, h * 1.0f)
            lineTo(w * 0.3305f, h * 0.6086f); lineTo(w * 0.1271f, h * 0.4426f); close()
        }
        drawPath(face2, Color(0xFF8D6E2D))
    }
}

/**
 * Баланс на карте и надбавка за тап рядом с ним.
 *
 * Надбавка — это «степень» к балансу: она стоит в той же строке справа, выровнена по верху
 * и вдвое мельче, чтобы читалась как индекс, а не как второе равноправное число.
 *
 * Кегль подбирается под ширину карты, потому что оба числа по правилу полноты (CLAUDE.md)
 * показываются целиком: в худшем случае это «$ 999 999 999» и «+$ 999 999 999» разом.
 * Место под надбавку заложено в расчёт ВСЕГДА, даже когда её нет — иначе при каждом тапе
 * менялся бы кегль баланса, и карта дёргалась бы.
 */
@Composable
internal fun BalanceWithTapPop(
    money: Double,
    accum: Double,
    tick: Int,
    currency: Currency,
    moneyColor: Color,
    onExpire: () -> Unit
) {
    val balanceText = GameMath.formatMoney(money, currency)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // ширина в sp-единицах: делим на плотность и на масштаб системного шрифта
        val widthSp = with(density) { maxWidth.toPx() / fontScale / density.density }
        // Резерв под надбавку: не меньше самой длинной возможной строки «+$ 999 999 999».
        // Опираться только на длину баланса нельзя — после крупной покупки на счету может
        // остаться мелочь, а накопленный тап дать девятизначную сумму, и она бы обрезалась.
        // Знак «+» добавляется к длине баланса на случай отрицательного баланса при долге.
        val popLen = maxOf(balanceText.length + 1, MAX_TAP_POP_LEN)
        val balanceSp = remember(widthSp, balanceText.length) {
            (widthSp / (0.62f * (balanceText.length + popLen * TAP_POP_RATIO)))
                .coerceIn(13f, 30f)
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(
                balanceText, color = moneyColor,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = balanceSp.sp, maxLines = 1, softWrap = false
            )
            TapPop(
                accum = accum, tick = tick, currency = currency,
                // не крупнее строки дохода на этой же карте: надбавка — индекс к балансу,
                // а не второе главное число. Пропорция к балансу тоже сохраняется,
                // поэтому при крупном системном шрифте надбавка мельчает вместе с ним.
                fontSize = minOf(TAP_POP_MAX_SP, balanceSp * TAP_POP_RATIO).sp,
                onExpire = onExpire
            )
        }
    }
}

/** Во сколько раз надбавка мельче баланса. */
private const val TAP_POP_RATIO = 0.5f

/** Потолок кегля надбавки: чуть мельче строки «+N $/день» (12sp) на той же карте. */
private const val TAP_POP_MAX_SP = 11.5f

/** Длина самой длинной возможной надбавки: «+$ 999 999 999» — знак, валюта, пробел и 11 знаков числа. */
private const val MAX_TAP_POP_LEN = 14

/**
 * Всплывающая награда за тап.
 *
 * Это разовая сумма, а не поток, поэтому символ валюты стоит ПЕРЕД числом (см. CLAUDE.md).
 * Высоту строки не увеличивает: кегль меньше, чем у баланса рядом.
 */
@Composable
internal fun TapPop(
    accum: Double,
    tick: Int,
    currency: Currency,
    fontSize: TextUnit,
    onExpire: () -> Unit
) {
    if (accum <= 0.0) return
    val anim = remember(tick) { Animatable(0f) }
    LaunchedEffect(tick) {
        anim.snapTo(0f)
        anim.animateTo(1f, animationSpec = tween(800))
        onExpire()
    }
    val alpha = when {
        anim.value < 0.15f -> anim.value / 0.15f          // быстрое появление
        anim.value < 0.70f -> 1f                          // держим
        else -> 1f - (anim.value - 0.70f) / 0.30f         // тает
    }
    Text(
        "+" + GameMath.formatMoney(accum, currency),
        color = GreenAccent, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, fontSize = fontSize,
        // суммы до миллиарда показываются целиком, поэтому перенос запрещён структурно
        maxLines = 1, softWrap = false,
        modifier = Modifier
            .padding(start = 4.dp)
            .offset(y = -(anim.value * 6).dp)
            .alpha(alpha)
    )
}

private fun formatDuration(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 && m > 0 -> "$h ч $m мин"
        h > 0 -> "$h ч"
        m > 0 -> "$m мин"
        else -> "меньше минуты"
    }
}

@Composable
private fun OfflineDialog(amount: Double, missed: Double, awaySec: Double, currency: Currency, onDismiss: () -> Unit) {
    // время, за которое доход реально копился (вся продуктивная часть сейфа)
    val productiveSec = awaySec.coerceAtMost(
        GameConfig.OFFLINE_FULL_SEC + GameConfig.OFFLINE_HALF_SEC
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE0101116))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1D1F25))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("С возвращением", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text("Вас не было ${formatDuration(awaySec)}", color = Mute, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(GameMath.formatMoney(amount, currency), color = TextMain,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text("доход сейфа за ${formatDuration(productiveSec)}", color = GreenAccent, fontSize = 12.sp)
            if (missed > 0.5) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Сейф переполнился! Упущено ${GameMath.formatMoney(missed, currency)}",
                    color = RedAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
                Text(
                    "Заходите чаще или расширьте сейф за слитки",
                    color = Mute, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gold)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Забрать", color = CoinText, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}
