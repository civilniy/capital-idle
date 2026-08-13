package ru.capital.idle.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Векторные иконки интерфейса. Рисуются на `Canvas` через `drawPath` — без сторонних библиотек
 * и без растровых ресурсов, поэтому масштабируются под любой размер и красятся любым цветом.
 *
 * Применяются ТОЛЬКО в матовой теме: в «Стекле» на тех же местах остаются эмодзи, как было.
 *
 * Иконка — цветной символ на круглой подложке того же цвета на 16%. Пропорция подложки
 * к символу задана спецификацией оформления: круг 40dp, символ 19dp — то есть символ
 * занимает 19/40 диаметра. Диаметр параметризован, потому что в вёрстке иконка встаёт
 * ровно в тот прямоугольник, который раньше занимало эмодзи (см. [IconSlot]): менять
 * размеры и высоты элементов при переключении темы нельзя.
 */
enum class AppIcon {
    BRIEFCASE, GRADUATION, CHART, CHART_DOWN, GLOBE, PERSON,
    SHOP, CAFE, FACTORY, TRUCK, BANK,
    CLOCK, STAR, GEM, COIN, LOCK, CHECK, BOLT, ARROW_UP, HOME, GEAR
}

/** Доля диаметра круглой подложки, которую занимает символ: 19dp из 40dp. */
private const val SYMBOL_OF_BADGE = 19f / 40f

/** Насколько плотна круглая подложка под символом. */
private const val BADGE_ALPHA = 0.16f

/**
 * Иконка на круглой подложке.
 *
 * @param diameter диаметр подложки; символ внутри — [SYMBOL_OF_BADGE] от него
 */
@Composable
fun AppIconBadge(icon: AppIcon, tint: Color, diameter: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter)) {
        drawIconBadge(icon, tint, size.minDimension, Offset.Zero)
    }
}

/**
 * Место под иконку: в старой теме — эмодзи, в новой — векторная иконка того же размера.
 *
 * Размер задаётся невидимым эмодзи-мерилом: иконка кладётся поверх и занимает ровно тот же
 * прямоугольник. Так блок не меняет ни высоту, ни положение при переключении темы — и при
 * увеличенном системном шрифте тоже, потому что мерило растёт вместе с ним.
 *
 * Если для эмодзи нет подходящей векторной иконки (предметы коллекции — это изображение
 * конкретной вещи, а не значок интерфейса), эмодзи остаётся в обеих темах.
 */
@Composable
fun IconSlot(
    emoji: String,
    tint: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    /** Рисовать ли круглую подложку. Выключается там, где иконка и так лежит на цветной плашке. */
    badge: Boolean = true,
    icon: AppIcon? = AppIcons.forEmoji(emoji)
) {
    val vector = LocalPalette.current.vectorIcons && icon != null
    Box(modifier) {
        // мерило: занимает ровно столько же, сколько занимало эмодзи
        Text(
            emoji, fontSize = fontSize, maxLines = 1, softWrap = false,
            modifier = if (vector) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier
        )
        if (vector) {
            Canvas(Modifier.matchParentSize()) {
                val d = size.minDimension
                val at = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                if (badge) drawIconBadge(icon!!, tint, d, at)
                else drawIconSymbol(icon!!, tint, d * 0.86f, at + Offset(d * 0.07f, d * 0.07f))
            }
        }
    }
}

/**
 * Ведущая иконка строки — там, где раньше значка не было вовсе.
 *
 * В старой теме не рисует НИЧЕГО: на этих местах у неё пусто, и добавлять ей значки нельзя.
 *
 * По высоте иконка НУЛЕВАЯ: измеряется в ноль и рисуется поверх, центрируясь по строке
 * (родитель выравнивает нулевой по высоте элемент по центру). Иначе строка подросла бы
 * ровно там, где содержимого меньше, чем иконка, — а вёрстка при переключении темы
 * меняться не должна. Ширину иконка занимает по-настоящему: текст рядом становится уже.
 */
@Composable
fun LeadingIcon(icon: AppIcon, tint: Color, size: Dp = 26.dp, gap: Dp = 8.dp) {
    if (!LocalPalette.current.vectorIcons) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(0.dp).wrapContentHeight(unbounded = true)) {
            AppIconBadge(icon, tint, size)
        }
        Spacer(Modifier.width(gap))
    }
}

/** Круглая подложка и символ поверх неё. */
fun DrawScope.drawIconBadge(icon: AppIcon, tint: Color, diameter: Float, topLeft: Offset) {
    val r = diameter / 2f
    drawCircle(tint.copy(alpha = BADGE_ALPHA), radius = r, center = topLeft + Offset(r, r))
    val s = diameter * SYMBOL_OF_BADGE
    drawIconSymbol(icon, tint, s, topLeft + Offset(r - s / 2f, r - s / 2f))
}

/**
 * Символ иконки без подложки: рисуется в квадрате [side] от угла [at].
 * Все пути заданы в долях квадрата, поэтому масштабируются без потерь.
 */
fun DrawScope.drawIconSymbol(icon: AppIcon, color: Color, side: Float, at: Offset) {
    val s = side
    fun x(f: Float) = at.x + f * s
    fun y(f: Float) = at.y + f * s
    fun path(block: Path.() -> Unit) = Path().apply(block)
    fun fill(p: Path) = drawPath(p, color)
    fun line(w: Float) = Stroke(width = w * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun poly(vararg pts: Float): Path = path {
        moveTo(x(pts[0]), y(pts[1]))
        var i = 2
        while (i < pts.size) { lineTo(x(pts[i]), y(pts[i + 1])); i += 2 }
        close()
    }
    fun stroke(width: Float, vararg pts: Float) {
        val p = Path()
        p.moveTo(x(pts[0]), y(pts[1]))
        var i = 2
        while (i < pts.size) { p.lineTo(x(pts[i]), y(pts[i + 1])); i += 2 }
        drawPath(p, color, style = line(width))
    }
    fun bar(l: Float, t: Float, rr: Float, b: Float) = fill(poly(l, t, rr, t, rr, b, l, b))

    when (icon) {
        // ===== разделы =====
        AppIcon.BRIEFCASE -> {
            stroke(0.09f, 0.34f, 0.26f, 0.34f, 0.14f, 0.66f, 0.14f, 0.66f, 0.26f)
            bar(0.04f, 0.28f, 0.96f, 0.92f)
            drawPath(poly(0.42f, 0.50f, 0.58f, 0.50f, 0.58f, 0.62f, 0.42f, 0.62f), color.copy(alpha = 0.35f))
        }
        AppIcon.GRADUATION -> {
            fill(poly(0.50f, 0.12f, 0.98f, 0.38f, 0.50f, 0.64f, 0.02f, 0.38f))
            stroke(0.09f, 0.20f, 0.47f, 0.20f, 0.76f, 0.50f, 0.88f, 0.80f, 0.76f, 0.80f, 0.47f)
        }
        AppIcon.CHART -> {
            bar(0.10f, 0.62f, 0.30f, 0.94f)
            bar(0.40f, 0.42f, 0.60f, 0.94f)
            bar(0.70f, 0.18f, 0.90f, 0.94f)
        }
        AppIcon.CHART_DOWN -> {
            bar(0.10f, 0.18f, 0.30f, 0.94f)
            bar(0.40f, 0.42f, 0.60f, 0.94f)
            bar(0.70f, 0.62f, 0.90f, 0.94f)
        }
        AppIcon.GLOBE -> {
            val c = at + Offset(0.5f * s, 0.5f * s)
            drawCircle(color, radius = 0.45f * s, center = c, style = line(0.09f))
            drawOval(color, topLeft = at + Offset(0.29f * s, 0.05f * s),
                size = Size(0.42f * s, 0.90f * s), style = line(0.08f))
            stroke(0.08f, 0.07f, 0.50f, 0.93f, 0.50f)
        }
        AppIcon.PERSON -> {
            drawCircle(color, radius = 0.20f * s, center = at + Offset(0.5f * s, 0.28f * s))
            drawArc(
                color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = at + Offset(0.14f * s, 0.56f * s), size = Size(0.72f * s, 0.68f * s)
            )
        }

        // ===== отрасли =====
        AppIcon.SHOP -> {
            fill(poly(0.06f, 0.20f, 0.94f, 0.20f, 0.88f, 0.42f, 0.12f, 0.42f))
            bar(0.12f, 0.42f, 0.88f, 0.94f)
            drawPath(poly(0.38f, 0.58f, 0.62f, 0.58f, 0.62f, 0.94f, 0.38f, 0.94f), color.copy(alpha = 0.35f))
        }
        AppIcon.CAFE -> {
            bar(0.12f, 0.36f, 0.68f, 0.82f)
            drawArc(
                color, startAngle = -70f, sweepAngle = 140f, useCenter = false,
                topLeft = at + Offset(0.56f * s, 0.40f * s), size = Size(0.34f * s, 0.30f * s),
                style = line(0.08f)
            )
            stroke(0.07f, 0.28f, 0.08f, 0.28f, 0.24f)
            stroke(0.07f, 0.50f, 0.08f, 0.50f, 0.24f)
            bar(0.06f, 0.86f, 0.78f, 0.96f)
        }
        AppIcon.FACTORY -> {
            fill(poly(0.06f, 0.94f, 0.06f, 0.52f, 0.34f, 0.72f, 0.34f, 0.52f, 0.62f, 0.72f, 0.62f, 0.52f, 0.94f, 0.52f, 0.94f, 0.94f))
            bar(0.70f, 0.14f, 0.86f, 0.52f)
        }
        AppIcon.TRUCK -> {
            bar(0.04f, 0.34f, 0.56f, 0.72f)
            fill(poly(0.58f, 0.46f, 0.80f, 0.46f, 0.96f, 0.60f, 0.96f, 0.72f, 0.58f, 0.72f))
            drawCircle(color, radius = 0.11f * s, center = at + Offset(0.26f * s, 0.80f * s))
            drawCircle(color, radius = 0.11f * s, center = at + Offset(0.78f * s, 0.80f * s))
        }
        AppIcon.BANK -> {
            fill(poly(0.50f, 0.08f, 0.98f, 0.34f, 0.02f, 0.34f))
            bar(0.14f, 0.42f, 0.26f, 0.82f)
            bar(0.44f, 0.42f, 0.56f, 0.82f)
            bar(0.74f, 0.42f, 0.86f, 0.82f)
            bar(0.04f, 0.86f, 0.96f, 0.96f)
        }

        // ===== состояния и величины =====
        AppIcon.CLOCK -> {
            drawCircle(color, radius = 0.44f * s, center = at + Offset(0.5f * s, 0.5f * s), style = line(0.09f))
            stroke(0.09f, 0.50f, 0.26f, 0.50f, 0.52f, 0.72f, 0.62f)
        }
        AppIcon.STAR -> fill(starPath(at, s, 5, 0.48f, 0.20f))
        AppIcon.GEM -> {
            fill(poly(0.26f, 0.20f, 0.74f, 0.20f, 0.96f, 0.44f, 0.50f, 0.92f, 0.04f, 0.44f))
            drawPath(poly(0.26f, 0.20f, 0.74f, 0.20f, 0.62f, 0.44f, 0.38f, 0.44f), color.copy(alpha = 0.3f))
        }
        AppIcon.COIN -> {
            drawCircle(color, radius = 0.44f * s, center = at + Offset(0.5f * s, 0.5f * s), style = line(0.10f))
            stroke(0.10f, 0.50f, 0.24f, 0.50f, 0.76f)
            stroke(0.09f, 0.36f, 0.36f, 0.64f, 0.36f)
            stroke(0.09f, 0.36f, 0.64f, 0.64f, 0.64f)
        }
        AppIcon.LOCK -> {
            drawArc(
                color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = at + Offset(0.24f * s, 0.10f * s), size = Size(0.52f * s, 0.52f * s),
                style = line(0.10f)
            )
            bar(0.14f, 0.44f, 0.86f, 0.92f)
        }
        AppIcon.CHECK -> stroke(0.14f, 0.12f, 0.52f, 0.40f, 0.80f, 0.90f, 0.20f)
        AppIcon.BOLT -> fill(poly(0.62f, 0.04f, 0.22f, 0.56f, 0.46f, 0.56f, 0.38f, 0.96f, 0.80f, 0.42f, 0.54f, 0.42f))
        AppIcon.ARROW_UP -> {
            fill(poly(0.50f, 0.06f, 0.92f, 0.48f, 0.66f, 0.48f, 0.66f, 0.94f, 0.34f, 0.94f, 0.34f, 0.48f, 0.08f, 0.48f))
        }
        AppIcon.HOME -> {
            fill(poly(0.50f, 0.06f, 0.98f, 0.46f, 0.02f, 0.46f))
            bar(0.16f, 0.46f, 0.84f, 0.94f)
            drawPath(poly(0.40f, 0.64f, 0.60f, 0.64f, 0.60f, 0.94f, 0.40f, 0.94f), color.copy(alpha = 0.35f))
        }
        AppIcon.GEAR -> fill(gearPath(at, s, teeth = 8, outer = 0.48f, inner = 0.33f, hole = 0.15f))
    }
}

/** Звезда с [points] лучами: внешний и внутренний радиусы в долях квадрата. */
private fun starPath(at: Offset, s: Float, points: Int, outer: Float, inner: Float): Path {
    val cx = at.x + 0.5f * s
    val cy = at.y + 0.5f * s
    val p = Path()
    for (i in 0 until points * 2) {
        val r = (if (i % 2 == 0) outer else inner) * s
        val a = -PI / 2 + i * PI / points
        val px = cx + (r * cos(a)).toFloat()
        val py = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
    return p
}

/**
 * Шестерёнка: зубцы по кругу и отверстие в середине.
 *
 * Отверстие — второй контур того же пути с чётно-нечётной заливкой, а не вырезание
 * поверх: вырезать пришлось бы цветом фона, а фон у иконки бывает разный.
 */
private fun gearPath(at: Offset, s: Float, teeth: Int, outer: Float, inner: Float, hole: Float): Path {
    val cx = at.x + 0.5f * s
    val cy = at.y + 0.5f * s
    val p = Path()
    p.fillType = PathFillType.EvenOdd
    val step = 2 * PI / teeth
    val hw = step * 0.22
    var first = true
    for (i in 0 until teeth) {
        val a = i * step
        val pts = listOf(
            inner to a - step / 2 + hw,
            outer to a - hw,
            outer to a + hw,
            inner to a + step / 2 - hw
        )
        for ((r, ang) in pts) {
            val px = cx + (r * s * cos(ang)).toFloat()
            val py = cy + (r * s * sin(ang)).toFloat()
            if (first) { p.moveTo(px, py); first = false } else p.lineTo(px, py)
        }
    }
    p.close()
    p.addOval(androidx.compose.ui.geometry.Rect(cx - hole * s, cy - hole * s, cx + hole * s, cy + hole * s))
    return p
}

/**
 * Какое эмодзи какой иконкой заменяется в матовой теме.
 *
 * Здесь только значки ИНТЕРФЕЙСА — разделы, состояния, категории имущества. Эмодзи предметов
 * коллекции (скрипка, амфора, метеорит) — изображение конкретной вещи, а не значок, и остаются
 * эмодзи в обеих темах: двадцать разных предметов под одной иконкой «звезда» читались бы хуже.
 */
object AppIcons {
    private val map: Map<String, AppIcon> = buildMap {
        // разделы нижней навигации
        put("💼", AppIcon.BRIEFCASE)   // 💼 портфель
        put("🎓", AppIcon.GRADUATION)  // 🎓 шапочка выпускника
        put("📈", AppIcon.CHART)       // 📈 диаграмма
        put("🌍", AppIcon.GLOBE)       // 🌍 глобус
        put("👤", AppIcon.PERSON)      // 👤 человек
        // события и состояния
        put("📉", AppIcon.CHART_DOWN)  // 📉
        put("🤝", AppIcon.PERSON)      // 🤝 окружение
        put("♻️", AppIcon.ARROW_UP)    // ♻️ перерождение
        put("♻", AppIcon.ARROW_UP)
        put("🔒", AppIcon.LOCK)        // 🔒
        // категории имущества
        put("🏚", AppIcon.HOME)        // 🏚
        put("🏢", AppIcon.HOME)        // 🏢
        put("🏘", AppIcon.HOME)        // 🏘
        put("🏡", AppIcon.HOME)        // 🏡
        put("🌃", AppIcon.HOME)        // 🌃
        put("🏙", AppIcon.HOME)        // 🏙
        put("🏝", AppIcon.HOME)        // 🏝
        put("🏰", AppIcon.HOME)        // 🏰
        put("🚶", AppIcon.PERSON)      // 🚶 пешком
        put("🚗", AppIcon.TRUCK)       // 🚗
        put("🚙", AppIcon.TRUCK)       // 🚙
        put("🏎", AppIcon.TRUCK)       // 🏎
        put("🛥", AppIcon.TRUCK)       // 🛥
        put("✈️", AppIcon.TRUCK)       // ✈️
        put("✈", AppIcon.TRUCK)
        put("⌚", AppIcon.CLOCK)             // ⌚
        put("👔", AppIcon.PERSON)      // 👔
        put("💎", AppIcon.GEM)         // 💎
        // коллекция и впечатления: искусство — звезда, древности — здание, драгоценности — самоцвет
        put("🖼", AppIcon.STAR)
        put("🎨", AppIcon.STAR)
        put("🖌", AppIcon.STAR)
        put("🎻", AppIcon.STAR)
        put("📜", AppIcon.STAR)
        put("🏺", AppIcon.BANK)
        put("🗿", AppIcon.BANK)
        put("🏛", AppIcon.BANK)
        put("👑", AppIcon.GEM)
        put("🌑", AppIcon.GEM)
        put("🦖", AppIcon.GEM)
        put("🏖", AppIcon.STAR)
        put("🎰", AppIcon.STAR)
        put("🎭", AppIcon.STAR)
        put("🦁", AppIcon.STAR)
        put("🚀", AppIcon.ARROW_UP)
    }

    /** Иконка отрасли — по её идентификатору. */
    fun forIndustry(id: String): AppIcon = when (id) {
        "trade" -> AppIcon.SHOP
        "food" -> AppIcon.CAFE
        "serv" -> AppIcon.PERSON
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

    /** Иконка для эмодзи или `null`, если замены нет и эмодзи остаётся как есть. */
    fun forEmoji(emoji: String): AppIcon? = map[emoji.trim()]
}
