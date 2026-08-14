package ru.capital.idle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

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
 * только текущая. Мерки ([rulers]) в разметку не попадают: они измеряются, но не размещаются,
 * поэтому ни на экране, ни в дереве доступности их нет.
 *
 * Ширину задаёт видимое содержимое — мерки её не растягивают.
 *
 * Резервировать место стоит не везде. Блок, который появляется один раз и остаётся (плашка
 * давления элит, баннер биржевого события), лучше оставить как есть: постоянная пустая дыра
 * под него хуже разового сдвига — так уже решили в PR #15.
 *
 * @param rulers раскладки, которые здесь могут оказаться; каждая только измеряется
 * @param content то, что показывается сейчас
 */
@Composable
fun SteadyHeight(
    rulers: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(contents = listOf(content) + rulers, modifier = modifier) { groups, constraints ->
        val measured = groups.map { group -> group.map { it.measure(constraints) } }
        val shown = measured.first()
        val height = measured.flatten().maxOfOrNull { it.height } ?: 0
        val width = (shown.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            shown.forEach { it.place(0, 0) }
        }
    }
}
