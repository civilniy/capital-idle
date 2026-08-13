package ru.capital.idle.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Иконки интерфейса: плотный круг насыщенного цвета с белым символом внутри.
 *
 * Не бледная подложка с цветным символом — именно плотная фигура. Тёплый цвет с малой
 * прозрачностью на почти чёрном даёт коричневую грязь (#FFB02E на 13% поверх #0B0B0D —
 * это #2D2417), поэтому подложек акцентом в новой теме нет вовсе, а цвет живёт в круге.
 *
 * Формы перенесены из эталона оформления как есть: те же 24×24 пути, что и в макете.
 * Разбирает их `PathParser` из `compose.ui.graphics.vector` — часть Compose, а не
 * сторонняя библиотека; рисование идёт обычным `drawPath` на `Canvas`.
 *
 * Применяются только в новой теме: в «Стекле» на тех же местах остаются эмодзи.
 */
enum class AppIcon(internal val svg: String) {
    CASE("M10 4h4a2 2 0 0 1 2 2v1h3a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3V6a2 2 0 0 1 2-2zm0 3h4V6h-4v1z"),
    CAP("M12 3 1 8l11 5 9-4.1V15h2V8L12 3zM5 13.2V17c0 1.7 3.1 3 7 3s7-1.3 7-3v-3.8l-7 3.2-7-3.2z"),
    CHART("M4 20h16v-2H4v2zM6 16h3V8H6v8zm5 0h3V4h-3v12zm5 0h3v-5h-3v5z"),
    GLOBE("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm6.9 9h-3a15 15 0 0 0-1.2-5.2A8 8 0 0 1 18.9 11zM12 4.2c.8 1.2 1.6 3.3 1.8 6.8h-3.6c.2-3.5 1-5.6 1.8-6.8zM5.1 11a8 8 0 0 1 4.2-5.2A15 15 0 0 0 8.1 11h-3zm0 2h3a15 15 0 0 0 1.2 5.2A8 8 0 0 1 5.1 13zm6.9 6.8c-.8-1.2-1.6-3.3-1.8-6.8h3.6c-.2 3.5-1 5.6-1.8 6.8zm2.7-1.6a15 15 0 0 0 1.2-5.2h3a8 8 0 0 1-4.2 5.2z"),
    USER("M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-4.4 0-8 2.2-8 5v3h16v-3c0-2.8-3.6-5-8-5z"),
    GEAR("M19.4 13a7.8 7.8 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-1.7-1L14.9 3h-4l-.4 2.6c-.6.2-1.2.6-1.7 1l-2.4-1-2 3.4L6.6 11a7.8 7.8 0 0 0 0 2l-2 1.6 2 3.4 2.4-1c.5.4 1.1.8 1.7 1l.4 2.6h4l.4-2.6c.6-.2 1.2-.6 1.7-1l2.4 1 2-3.4-2.2-1.6zM12.9 15a3 3 0 1 1 0-6 3 3 0 0 1 0 6z"),
    BOLT("M13 2 4 14h6l-1 8 9-12h-6l1-8z"),
    UP("M4 18l6-6 4 4 6-7v6h2V5h-8v2h5l-5 5.8L10 9l-7 7 1 2z"),
    STAR("m12 2 3 6.5 7 .9-5 4.8 1.3 7L12 17.8 5.7 21.2 7 14.2 2 9.4l7-.9L12 2z"),
    GEM("M6 3h12l4 6-10 12L2 9l4-6z"),
    COIN("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 15.4V19h-2v-1.6c-1.6-.3-2.8-1.3-2.9-2.9h1.9c.1.8.8 1.4 2 1.4s1.9-.5 1.9-1.3c0-.7-.5-1-2.1-1.4-2-.5-3.4-1.1-3.4-2.9 0-1.5 1.2-2.5 2.6-2.8V6h2v1.5c1.6.3 2.6 1.3 2.7 2.7h-1.9c-.1-.8-.7-1.3-1.7-1.3-1.1 0-1.7.5-1.7 1.2 0 .6.5.9 2.1 1.3 2 .5 3.4 1.2 3.4 3 0 1.6-1.2 2.6-2.9 3z"),
    SHOP("M4 4h16l1 5a3 3 0 0 1-5 2.2A3 3 0 0 1 12 12a3 3 0 0 1-4-.8A3 3 0 0 1 3 9l1-5zm1 9.7V20h14v-6.3a5 5 0 0 1-3-.5 5 5 0 0 1-4 .6 5 5 0 0 1-4-.6 5 5 0 0 1-3 .5z"),
    FOOD("M8 2v8a3 3 0 0 1-2 2.8V22H4V12.8A3 3 0 0 1 2 10V2h2v7h1V2h2v7h1V2zm10 0c2 0 3 3 3 7s-1 5-2 5.4V22h-2V2h1z"),
    FACTORY("M2 20h20v2H2v-2zm1-2V9l6 4V9l6 4V4h5v14H3z"),
    TRUCK("M3 6h11v9H3V6zm12 3h3.5l2.5 3v3h-6V9zM6.5 20a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6zm11 0a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z"),
    CLOCK("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 10.6 4 2.3-1 1.7-5-2.9V6h2v6.6z"),
    BANK("M12 2 2 8v2h20V8L12 2zM4 11v8H2v2h20v-2h-2v-8h-2v8h-3v-8h-2v8H8v-8H6v8H4v-8z"),
    HOME("M12 3 2 12h3v8h5v-5h4v5h5v-8h3L12 3z"),
    CHECK("M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"),
    LOCK("M12 1a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2h-1V6a5 5 0 0 0-5-5zm0 2a3 3 0 0 1 3 3v3H9V6a3 3 0 0 1 3-3z"),
    FUND("M3 3v18h18v-2H5V3H3zm5 12h2v-5H8v5zm4 0h2V7h-2v8zm4 0h2v-3h-2v3z");
}

/** Разобранные пути 24×24. Разбор один на приложение: строка постоянная, путь не меняется. */
private val SHAPES: Map<AppIcon, Path> by lazy(LazyThreadSafetyMode.NONE) {
    AppIcon.entries.associateWith { PathParser().parsePathString(it.svg).toPath() }
}

/** Сторона квадрата, в котором заданы пути эталона. */
private const val VIEW_BOX = 24f

/** Доля диаметра круга, которую занимает символ: 19dp из 40dp (в плитке 15 из 30). */
private const val SYMBOL_OF_BADGE = 19f / 40f

/**
 * Иконка: плотный круг [tint] с символом внутри.
 *
 * Символ белый; на янтарном круге — тёмный, иначе он бы не читался. Недоступный элемент
 * рисуется серым кругом с приглушённым символом: это тот же значок, только выключенный.
 */
@Composable
fun AppIconBadge(icon: AppIcon, tint: Color, diameter: Dp = Modern.iconCircle, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val off = tint == p.mute
    val circle = if (off) p.innerFill else tint
    val symbol = when {
        off -> p.mute
        tint == p.money -> p.onMoney
        else -> Color.White
    }
    Canvas(modifier.size(diameter)) {
        drawIconBadge(icon, circle, symbol, size.minDimension, Offset.Zero)
    }
}

/** Круг и символ поверх него. */
fun DrawScope.drawIconBadge(
    icon: AppIcon, circle: Color, symbol: Color, diameter: Float, topLeft: Offset
) {
    val r = diameter / 2f
    drawCircle(circle, radius = r, center = topLeft + Offset(r, r))
    val s = diameter * SYMBOL_OF_BADGE
    drawIconSymbol(icon, symbol, s, topLeft + Offset(r - s / 2f, r - s / 2f))
}

/** Символ без круга: путь эталона, вписанный в квадрат [side] от угла [at]. */
fun DrawScope.drawIconSymbol(icon: AppIcon, color: Color, side: Float, at: Offset) {
    val path = SHAPES[icon] ?: return
    translate(at.x, at.y) {
        scale(side / VIEW_BOX, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

/**
 * Место под иконку: в старой теме — эмодзи, в новой — векторная иконка.
 *
 * Если для эмодзи нет подходящей иконки, эмодзи остаётся в обеих темах.
 */
@Composable
fun IconSlot(
    emoji: String,
    tint: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    /** Рисовать ли круг. Выключается там, где иконка и так лежит на цветной плашке. */
    badge: Boolean = true,
    /** Диаметр круга в новой теме: 40dp в строке, 30dp в плитке. */
    size: Dp = Modern.iconCircle,
    icon: AppIcon? = AppIcons.forEmoji(emoji)
) {
    val vector = modernLook && icon != null
    val symbolOnly = LocalPalette.current.onMoney
    Box(modifier) {
        if (vector) {
            if (badge) AppIconBadge(icon!!, tint, size)
            else Canvas(Modifier.size(size)) {
                drawIconSymbol(icon!!, symbolOnly, this.size.minDimension, Offset.Zero)
            }
        } else {
            // В старой теме здесь должен остаться ровно тот Text, что стоял до темы:
            // без maxLines и softWrap. Однажды я их добавила — и при системном шрифте 1.5
            // поехали девять эталонов торгов и коллекции.
            Text(emoji, fontSize = fontSize)
        }
    }
}

/**
 * Значок нижней навигации.
 *
 * В новой теме активная вкладка получает янтарную пилюлю 38×28 с тёмным символом,
 * неактивная — тот же символ приглушённым цветом и без подложки. В старой теме
 * на этом месте остаётся эмодзи ровно того же кегля, что и было.
 */
@Composable
fun NavIcon(emoji: String, active: Boolean, modifier: Modifier = Modifier) {
    val icon = AppIcons.forEmoji(emoji)
    val p = LocalPalette.current
    if (!modernLook || icon == null) {
        Box(modifier) { Text(emoji, fontSize = 16.sp) }
        return
    }
    Box(
        modifier.size(width = 38.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) p.money else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(19.dp)) {
            drawIconSymbol(icon, if (active) p.onMoney else p.mute, size.minDimension, Offset.Zero)
        }
    }
}

/**
 * Ведущая иконка строки — там, где раньше значка не было вовсе.
 * В старой теме не рисует ничего: на этих местах у неё пусто.
 */
@Composable
fun LeadingIcon(icon: AppIcon, tint: Color, size: Dp = Modern.iconCircle, gap: Dp = 13.dp) {
    if (!modernLook) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconBadge(icon, tint, size)
        Spacer(Modifier.width(gap))
    }
}

/**
 * Какое эмодзи какой иконкой заменяется в новой теме.
 *
 * Здесь только значки ИНТЕРФЕЙСА — разделы, состояния, категории имущества, предметы
 * коллекции. Эмодзи, которым в наборе нет пары, остаются эмодзи в обеих темах.
 */
object AppIcons {
    private val map: Map<String, AppIcon> = buildMap {
        // разделы нижней навигации
        put("\uD83D\uDCBC", AppIcon.CASE)
        put("\uD83C\uDF93", AppIcon.CAP)
        put("\uD83D\uDCC8", AppIcon.CHART)
        put("\uD83C\uDF0D", AppIcon.GLOBE)
        put("\uD83D\uDC64", AppIcon.USER)
        // события и состояния
        put("\uD83D\uDCC9", AppIcon.FUND)
        put("\uD83E\uDD1D", AppIcon.USER)
        put("\u267B\uFE0F", AppIcon.UP)
        put("\u267B", AppIcon.UP)
        put("\uD83D\uDD12", AppIcon.LOCK)
        put("\u2699", AppIcon.GEAR)
        // имущество
        put("\uD83C\uDFDA", AppIcon.HOME)
        put("\uD83C\uDFE2", AppIcon.HOME)
        put("\uD83C\uDFD8", AppIcon.HOME)
        put("\uD83C\uDFE1", AppIcon.HOME)
        put("\uD83C\uDF03", AppIcon.HOME)
        put("\uD83C\uDFD9", AppIcon.HOME)
        put("\uD83C\uDFDD", AppIcon.HOME)
        put("\uD83C\uDFF0", AppIcon.HOME)
        put("\uD83D\uDEB6", AppIcon.USER)
        put("\uD83D\uDE97", AppIcon.TRUCK)
        put("\uD83D\uDE99", AppIcon.TRUCK)
        put("\uD83C\uDFCE", AppIcon.TRUCK)
        put("\uD83D\uDEE5", AppIcon.TRUCK)
        put("\u2708\uFE0F", AppIcon.TRUCK)
        put("\u2708", AppIcon.TRUCK)
        put("\u231A", AppIcon.CLOCK)
        put("\uD83D\uDC54", AppIcon.USER)
        put("\uD83D\uDC8E", AppIcon.GEM)
        // коллекция и впечатления
        put("\uD83D\uDDBC", AppIcon.STAR)
        put("\uD83C\uDFA8", AppIcon.STAR)
        put("\uD83D\uDD8C", AppIcon.STAR)
        put("\uD83C\uDFBB", AppIcon.STAR)
        put("\uD83D\uDCDC", AppIcon.STAR)
        put("\uD83C\uDFFA", AppIcon.BANK)
        put("\uD83D\uDDFF", AppIcon.BANK)
        put("\uD83C\uDFDB", AppIcon.BANK)
        put("\uD83D\uDC51", AppIcon.GEM)
        put("\uD83C\uDF11", AppIcon.GEM)
        put("\uD83E\uDD96", AppIcon.GEM)
        put("\uD83C\uDFD6", AppIcon.STAR)
        put("\uD83C\uDFB0", AppIcon.STAR)
        put("\uD83C\uDFAD", AppIcon.STAR)
        put("\uD83E\uDD81", AppIcon.STAR)
        put("\uD83D\uDE80", AppIcon.UP)
    }

    /** Иконка для эмодзи или `null`, если замены нет и эмодзи остаётся как есть. */
    fun forEmoji(emoji: String): AppIcon? = map[emoji.trim()]

    /** Иконка отрасли — по её идентификатору. */
    fun forIndustry(id: String): AppIcon = when (id) {
        "trade" -> AppIcon.SHOP
        "food" -> AppIcon.FOOD
        "serv" -> AppIcon.USER
        "prod" -> AppIcon.FACTORY
        "log" -> AppIcon.TRUCK
        else -> AppIcon.BOLT          // it
    }

    /** Иконка инструмента накоплений — по порядковому номеру. */
    fun forAsset(ordinal: Int): AppIcon = when (ordinal) {
        0 -> AppIcon.BANK             // депозит
        1 -> AppIcon.COIN             // облигации
        else -> AppIcon.HOME          // недвижимость
    }
}
