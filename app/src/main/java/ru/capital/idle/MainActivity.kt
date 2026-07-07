package ru.capital.idle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.capital.idle.core.game.GameState
import ru.capital.idle.core.game.Onboarding
import ru.capital.idle.ui.*
import ru.capital.idle.ui.theme.*

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CapitalTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            // уход в фон (сворачивание / другое приложение): стоп онлайн-цикла, переход в оффлайн
                            Lifecycle.Event.ON_STOP -> vm.onAppBackground()
                            // возврат на передний план: пересчёт оффлайн-дохода + рестарт цикла
                            Lifecycle.Event.ON_START -> vm.onAppForeground()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                }

                val state by vm.state.collectAsStateWithLifecycle()
                if (!state.onboarded) {
                    WelcomeScreen(onDone = { vm.finishOnboarding(it) })
                } else {
                    MainScaffold(vm = vm, state = state)
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: GameViewModel, state: GameState) {
    var tab by remember { mutableStateOf("main") }
    if (!Onboarding.unlocked(state, tab)) tab = "main"

    // плашка «почему закрыто»
    var lockInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(lockInfo) {
        if (lockInfo != null) { delay(2800); lockInfo = null }
    }
    val onLocked: (String) -> Unit = { id -> lockInfo = Onboarding.lockText(id) }

    // «пинок» для сброса вложенных оверлеев на главной (например, экрана категории предприятий)
    var mainResetTick by remember { mutableStateOf(0) }
    // желаемые под-вкладки при переходе из сводки на главной (Развитие→Окружение, Мир→Рейтинг)
    var devInner by remember { mutableStateOf("courses") }
    var worldInner by remember { mutableStateOf("rank") }

    // анонсы открытий
    val announceQueue by vm.announceQueue.collectAsStateWithLifecycle()
    val announce = announceQueue.firstOrNull()
    LaunchedEffect(announce) {
        if (announce != null) { delay(2600); vm.dismissAnnounce() }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    "main" -> GameScreen(vm = vm, resetTick = mainResetTick,
                        onNavigate = { dest ->
                            when (dest) {
                                "profile" -> tab = "profile"
                                "network" -> { devInner = "net"; tab = "dev" }
                                "rank" -> { worldInner = "rank"; tab = "world" }
                            }
                            vm.markTabSeen(tab)
                        })
                    "dev" -> DevScreen(vm = vm, onLocked = onLocked, startInner = devInner)
                    "inv" -> InvestScreen(vm = vm)
                    "world" -> WorldScreen(vm = vm, onLocked = onLocked, startInner = worldInner)
                    else -> ProfileScreen(vm = vm)
                }
            }
            BottomBar(
                state = state, selected = tab,
                onSelect = { id ->
                    if (Onboarding.unlocked(state, id)) {
                        // повторный тап по «Капитал» сбрасывает вложенные оверлеи на главной
                        if (id == "main") mainResetTick++
                        // обычный заход через нижнее меню открывает дефолтную под-вкладку
                        if (id == "dev") devInner = "courses"
                        if (id == "world") worldInner = "rank"
                        tab = id
                        vm.markTabSeen(id)
                    } else onLocked(id)
                }
            )
        }

        // плашка условия открытия
        lockInfo?.let { (title, why) ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel2)
                    .border(1.dp, GoldDim, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("\uD83D\uDD12 $title", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Spacer(Modifier.height(3.dp))
                Text(why, color = TextMain, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        // анонс открытия раздела
        if (announce != null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2A2410), Panel2)))
                    .border(1.dp, Gold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Gold),
                    contentAlignment = Alignment.Center
                ) { Text(announce.icon, fontSize = 15.sp) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(announce.title, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text(announce.text, color = TextMain, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun BottomBar(state: GameState, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .navigationBarsPadding()
            .padding(vertical = 5.dp)
    ) {
        Onboarding.navGroups.forEach { g ->
            val un = Onboarding.unlocked(state, g.id)
            val isNew = un && g.id !in state.seenTabs
            Box(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(g.id) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (un) g.icon else "\uD83D\uDD12",
                        fontSize = 16.sp,
                        modifier = Modifier.alpha(if (un) 1f else 0.45f)
                    )
                    Text(
                        g.title,
                        color = when {
                            selected == g.id -> Gold
                            un -> Mute
                            else -> Mute.copy(alpha = 0.45f)
                        },
                        fontWeight = if (selected == g.id) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center
                    )
                }
                if (isNew) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Gold)
                    )
                }
            }
        }
    }
}
