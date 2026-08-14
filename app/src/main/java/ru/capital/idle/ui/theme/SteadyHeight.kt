package ru.capital.idle.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Место, высота которого не зависит от того, что в нём сейчас показано.
 *
 * Нужно там, где содержимое подменяется само по себе, без действий игрока: у сменных
 * раскладок разная высота, и весь список ниже прыгает по вертикали. Пример — итог автовклада
 * на экране «Инвест»: раз в игровой день деньги списываются, баланс падает до резерва, вместо
 * строки «следующим днём уйдёт» на мгновение встаёт предупреждение, и через миг всё
 * возвращается обратно. Игровой день — 24 реальные секунды, так что экран дёргается постоянно.
 *
 * Здесь измеряются все возможные раскладки, высота берётся по самой высокой, а рисуется
 * только текущая. Мерки ([rulers]) измеряются, но не размещаются, поэтому на экране их нет.
 * Из дерева доступности они убраны отдельно: неразмещённый узел из него не выпадает сам,
 * и без этого экранный диктор читал бы каждую мерку — «следующим днём уйдёт» и все причины
 * разом. Это же ловил бы и поиск по тексту в тестах.
 *
 * Ширину задаёт видимое содержимое — мерки её не растягивают.
 *
 * Резервировать место стоит не везде. Блок, который появляется один раз и остаётся (плашка
 * давления элит, баннер биржевого события), лучше оставить как есть: постоянная пустая дыра
 * под него хуже разового сдвига — так уже решили в PR #15.
 *
 * @param rulers раскладки, которые здесь могут оказаться; каждая только измеряется
 * @param verticalAlignment где стоит содержимое в отложенном месте. Сверху — когда лишняя
 *   строка была бы переносом текущей (так у названия бумаги и у итога автовклада).
 *   По центру — когда место стоит в ряду и должно совпадать с соседями по строке.
 * @param content то, что показывается сейчас
 */
@Composable
fun SteadyHeight(
    rulers: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit
) {
    // мерки меряются в тех же constraints, что и содержимое (propagateMinConstraints),
    // иначе перенос строки в мерке случился бы не там же, где в настоящей раскладке
    val silent: List<@Composable () -> Unit> = rulers.map { ruler ->
        { Box(Modifier.clearAndSetSemantics {}, propagateMinConstraints = true) { ruler() } }
    }
    Layout(contents = listOf(content) + silent, modifier = modifier) { groups, constraints ->
        val measured = groups.map { group -> group.map { it.measure(constraints) } }
        val shown = measured.first()
        val height = measured.flatten().maxOfOrNull { it.height } ?: 0
        val width = (shown.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val boxHeight = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, boxHeight) {
            shown.forEach { it.place(0, verticalAlignment.align(it.height, boxHeight)) }
        }
    }
}
