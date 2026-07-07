package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.capital.idle.core.game.Onboarding
import ru.capital.idle.ui.theme.*

/** Группа «Развитие»: Курсы + Окружение. Единый стеклянный фон на весь экран. */
@Composable
fun DevScreen(vm: GameViewModel, onLocked: (String) -> Unit, startInner: String = "courses") {
    val state by vm.state.collectAsStateWithLifecycle()
    var inner by remember { mutableStateOf(startInner) }
    androidx.compose.runtime.LaunchedEffect(startInner) { inner = startInner }
    val netUnlocked = Onboarding.unlocked(state, "net")
    if (inner == "net" && !netUnlocked) inner = "courses"

    GlassBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(8.dp))
                Text("РАЗВИТИЕ", color = Gold, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(GlassFill).padding(4.dp)
                ) {
                    GlassTab("Курсы", inner == "courses", false, Modifier.weight(1f)) { inner = "courses" }
                    GlassTab("Окружение", inner == "net", !netUnlocked, Modifier.weight(1f)) {
                        if (netUnlocked) inner = "net" else onLocked("net")
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                if (inner == "courses") EducationScreen(vm) else NetworkScreen(vm)
            }
        }
    }
}
