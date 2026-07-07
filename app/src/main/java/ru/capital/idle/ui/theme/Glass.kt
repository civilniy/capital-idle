package ru.capital.idle.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Единый стеклянный стиль интерфейса (как на вкладке Инвестиции).
 * Тёмные полупрозрачные слои поверх фона с мягкими цветными пятнами.
 */

// заливки стекла
val GlassFill = Color(0x10FFFFFF)        // основная карточка — едва светлее фона
val GlassInner = Color(0x0AFFFFFF)       // вложенный слой
val GlassAccent = Color(0x14E8B54A)      // акцентная (золотистая) карточка — «ваше место»
val GlassBtn = Color(0x14FFFFFF)         // активная нейтральная кнопка
val GlassBtnOff = Color(0x06FFFFFF)      // недоступная кнопка
val GlassBtnOffText = Color(0xFF54565E)
val GlassSell = Color(0x2ED9694F)        // действие изъятия
val GlassSellText = Color(0xFFECA08C)

/** Фон экрана с мягкими цветными пятнами (тёмный). Помести контент внутрь content {}. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF14151A))) {
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0x0DE8B54A), Color(0x00E8B54A)),
                center = Offset(140f, 200f), radius = 700f
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0x0F6A9BD8), Color(0x006A9BD8)),
                center = Offset(950f, 700f), radius = 800f
            )
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0x0A5FBF7A), Color(0x005FBF7A)),
                center = Offset(300f, 1700f), radius = 850f
            )
        ))
        content()
    }
}

/** Стеклянный переключатель раздела: активная вкладка светлее, текст золотой. Общий для всех групп. */
@Composable
fun GlassTab(label: String, on: Boolean, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) GlassBtn else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (if (locked) "\uD83D\uDD12 " else "") + label,
            color = if (locked) Mute.copy(alpha = 0.6f) else if (on) Gold else Mute,
            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 11.5.sp, lineHeight = 13.sp, maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}
