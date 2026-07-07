package ru.capital.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.capital.idle.ui.theme.*

@Composable
fun WelcomeScreen(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(70.dp))
        Text("КАПИТАЛ", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Постройте состояние с нуля и поднимитесь на вершину мирового рейтинга богатейших.",
            color = Mute, fontSize = 13.sp, textAlign = TextAlign.Center,
            lineHeight = 19.sp, modifier = Modifier.widthIn(max = 300.dp)
        )

        Spacer(Modifier.height(34.dp))

        Text("КАК ВАС ЗОВУТ", color = Mute, fontSize = 10.sp, letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .border(1.dp, LineColor, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            if (name.isEmpty()) {
                Text("ваше имя", color = Mute, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
            }
            BasicTextField(
                value = name,
                onValueChange = { input ->
                    if (input.length <= 18) {
                        // первая буква каждого слова всегда заглавная
                        val sb = StringBuilder()
                        var newWord = true
                        for (ch in input.replace("\n", "")) {
                            sb.append(if (newWord && ch.isLetter()) ch.uppercaseChar() else ch)
                            newWord = ch == ' '
                        }
                        name = sb.toString()
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 18.sp),
                cursorBrush = SolidColor(Gold),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("${name.length} / 18", color = Mute, fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Gold)
                .clickable { onDone(name) }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Начать игру", color = CoinText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("пропустить", color = Mute, fontSize = 13.sp,
            modifier = Modifier.clickable { onDone("") }.padding(8.dp))
    }
}
