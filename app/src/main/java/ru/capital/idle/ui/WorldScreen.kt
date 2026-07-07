package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.Onboarding
import ru.capital.idle.ui.theme.*

/** Группа «Мир»: Рейтинг + Цели + Престиж. Единый стеклянный фон на весь экран. */
@Composable
fun WorldScreen(vm: GameViewModel, onLocked: (String) -> Unit, startInner: String = "rank") {
    val state by vm.state.collectAsStateWithLifecycle()
    var inner by remember { mutableStateOf(startInner) }
    androidx.compose.runtime.LaunchedEffect(startInner) { inner = startInner }
    val presUnlocked = Onboarding.unlocked(state, "pres")
    if (inner == "pres" && !presUnlocked) inner = "goals"

    GlassBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(8.dp))
                Text("МИР", color = Gold, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                // стеклянный переключатель вкладок
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(GlassFill).padding(4.dp)
                ) {
                    GlassTab("Рейтинг", inner == "rank", false, Modifier.weight(1f)) { inner = "rank" }
                    GlassTab("Цели", inner == "goals", false, Modifier.weight(1f)) { inner = "goals" }
                    GlassTab("Престиж", inner == "pres", !presUnlocked, Modifier.weight(1f)) {
                        if (presUnlocked) inner = "pres" else onLocked("pres")
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                when (inner) {
                    "goals" -> GoalsScreen(vm)
                    "rank" -> RankingScreen(vm)
                    else -> PrestigeScreen(vm)
                }
            }
        }
    }
}
