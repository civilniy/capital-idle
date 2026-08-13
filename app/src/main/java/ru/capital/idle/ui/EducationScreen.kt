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
import ru.capital.idle.core.game.*
import ru.capital.idle.ui.theme.*
import kotlin.math.ceil

@Composable
fun EducationScreen(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val cur = Currency.fromCode(state.currencyCode)
    val branchColors = listOf(Gold, Study, Rest)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Учёба идёт в выделенные часы дня. Сейчас: ${state.studyHCalc}ч/день" +
                if ("mentor" in state.netOwned) " · наставник ×1.5" else "",
            color = Mute, fontSize = 11.sp
        )
        Spacer(Modifier.height(12.dp))

        Education.branches.forEachIndexed { bi, branch ->
            GroupCard {
                Text(branch.title, color = branchColors.getOrElse(bi) { Gold },
                    fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = dpOf(0.dp, Modern.cardPadH), top = dpOf(0.dp, 8.dp)))
                Spacer(Modifier.height(6.dp))
                branch.courses.forEachIndexed { i, c ->
                    CourseCard(state, c, cur, onStudy = { vm.startStudy(c.id) })
                    RowSeparator(6.dp, last = i == branch.courses.lastIndex)
                }
            }
            Spacer(Modifier.height(dpOf(10.dp, Modern.cardGap)))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun CourseCard(state: GameState, c: Course, cur: Currency, onStudy: () -> Unit) {
    val isDone = c.id in state.eduDone
    val locked = c.reqCourse != null && c.reqCourse !in state.eduDone
    val current = state.studyingId == c.id
    val busy = state.studyingId.isNotEmpty()
    val canStart = !isDone && !locked && !busy && state.money >= c.cost
    val perDay = GameMath.studyHoursPerDay(state).coerceAtLeast(0.05)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(cardShape(14.dp))
            .background(
                when {
                    isDone -> StudyFill            // изучено — акцент раздела учёбы
                    current -> GlassAccent         // идёт учёба — золотистый акцент
                    else -> rowFill(GlassFill)
                }
            )
            .padding(horizontal = dpOf(14.dp, Modern.cardPadH), vertical = dpOf(12.dp, Modern.rowPadV)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LeadingIcon(AppIcon.GRADUATION, if (isDone) Learned else if (locked) Mute else Study)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(c.title, color = if (locked) Mute else TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                c.info + if (locked) " · нужно: ${Education.byId(c.reqCourse!!)?.title}" else "",
                color = if (locked) RedAccent else Mute, fontSize = 10.sp, lineHeight = 14.sp
            )
            if (current) {
                Spacer(Modifier.height(6.dp))
                val progress = (state.studyProgress / c.durationHours).coerceIn(0.0, 1.0).toFloat()
                Box(Modifier.fillMaxWidth().height(dpOf(5.dp, Modern.barHeight))
                    .clip(barShape(99.dp)).background(GlassInner)) {
                    Box(
                        Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .clip(barShape(99.dp)).background(Study)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                isDone -> Text("✓ изучено", color = Learned, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                current -> Text(
                    "~${ceil((c.durationHours - state.studyProgress) / perDay).toInt()} дн.",
                    color = Mute, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
                else -> {
                    Text(GameMath.formatMoney(c.cost, cur), color = if (canStart) Gold else Mute,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${c.durationHours.toInt()} уч. ч (~${ceil(c.durationHours / perDay).toInt()} дн.)",
                        color = Mute, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(btnShape(9.dp))
                            .background(if (canStart) Gold else GlassBtnOff)
                            .clickable(enabled = canStart, onClick = onStudy)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            when {
                                busy && !isDone && !locked -> "идёт учёба"
                                state.money < c.cost && !locked -> "не хватает"
                                else -> "Учиться"
                            },
                            color = if (canStart) CoinText else GlassBtnOffText,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
