package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import java.util.Locale

/**
 * Форматирование чисел. Разделитель разрядов — неразрывный пробел (U+00A0),
 * десятичный разделитель — запятая.
 */
class GameMathFormatTest {

    /** Неразрывный пробел — разделитель разрядов в groupDigits. */
    private val NB = "\u00A0"

    @Test
    fun `до десяти показывается дробь, дальше — целое с разрядами`() {
        assertEquals("0", GameMath.format(0.0))
        assertEquals("5", GameMath.format(5.0))
        assertEquals("5,5", GameMath.format(5.5))
        assertEquals("9,9", GameMath.format(9.9))
        // с десяти дробная часть отбрасывается
        assertEquals("10", GameMath.format(10.7))
        assertEquals("1${NB}234", GameMath.format(1_234.0))
        assertEquals("1${NB}234${NB}567", GameMath.format(1_234_567.9))
    }

    @Test
    fun `до миллиарда число целиком, дальше — суффикс`() {
        assertEquals("999${NB}999${NB}999", GameMath.format(999_999_999.0))
        assertEquals("1,0B", GameMath.format(1_000_000_000.0))
        assertEquals("2,4B", GameMath.format(2_400_000_000.0))
        assertEquals("12,3B", GameMath.format(12_345_678_900.0))
        assertEquals("1,5T", GameMath.format(1.5e12))
        // от сотни в мантиссе дробь уходит
        assertEquals("123B", GameMath.format(1.23e11))
    }

    @Test
    fun `отрицательные суммы форматируются со знаком`() {
        assertEquals("-1${NB}500", GameMath.format(-1_500.0))
        assertEquals("-2,5B", GameMath.format(-2_500_000_000.0))
        assertEquals("-5,5", GameMath.format(-5.5))
    }

    @Test
    fun `компактный формат всегда с суффиксом от тысяч`() {
        assertEquals("999", GameMath.formatShort(999.0))
        assertEquals("1,0K", GameMath.formatShort(1_000.0))
        assertEquals("1,5K", GameMath.formatShort(1_500.0))
        assertEquals("250K", GameMath.formatShort(250_000.0))
        assertEquals("2,4M", GameMath.formatShort(2_400_000.0))
        assertEquals("1,2B", GameMath.formatShort(1_200_000_000.0))
    }

    @Test
    fun `полное число и деньги в выбранной валюте`() {
        assertEquals("1${NB}234${NB}567", GameMath.formatFull(1_234_567L))
        assertEquals("0", GameMath.formatFull(0L))

        assertEquals("$ 1${NB}000", GameMath.formatMoney(1_000.0, Currency.USD))
        // 100 $ по курсу 73.7 = 7 370 ₽
        assertEquals("₽ 7${NB}370", GameMath.formatMoney(100.0, Currency.RUB))
        assertEquals("7${NB}370", GameMath.formatAmount(100.0, Currency.RUB))
        assertEquals("€ 920", GameMath.formatMoney(1_000.0, Currency.EUR))
    }

    @Test
    fun `граница сокращения — ровно миллиард`() {
        // до миллиарда суммы показываются целиком: в игре про деньги важно видеть настоящее число
        assertEquals("999${NB}999${NB}999", GameMath.format(999_999_999.0))
        assertEquals("$ 999${NB}999${NB}999", GameMath.formatMoney(999_999_999.0, Currency.USD))
        // ровно с миллиарда включается сокращение
        assertEquals("1,0B", GameMath.format(1_000_000_000.0))
        assertEquals("$ 1,0B", GameMath.formatMoney(1_000_000_000.0, Currency.USD))
        // соседние значения по обе стороны границы
        assertEquals("999${NB}999${NB}998", GameMath.format(999_999_998.0))
        assertEquals("1,0B", GameMath.format(1_000_000_001.0))
    }

    @Test
    fun `разделитель остаётся запятой при любой системной локали`() {
        val saved = Locale.getDefault()
        try {
            // английская локаль: без принудительной замены здесь была бы точка
            Locale.setDefault(Locale.US)
            assertEquals("5,5", GameMath.format(5.5))
            assertEquals("2,4B", GameMath.format(2_400_000_000.0))
            assertEquals("1,5K", GameMath.formatShort(1_500.0))
            assertEquals("12,3", GameMath.decimal(12.34))
            assertEquals("1,50", GameMath.decimal(1.5, 2))
            assertEquals("1,5 млрд", GameMath.formatRank(1_500_000_000L))

            // немецкая: здесь запятая и так родная — результат обязан совпасть
            Locale.setDefault(Locale.GERMANY)
            assertEquals("5,5", GameMath.format(5.5))
            assertEquals("2,4B", GameMath.format(2_400_000_000.0))
            assertEquals("1,5 млрд", GameMath.formatRank(1_500_000_000L))

            // русская: то же самое
            Locale.setDefault(Locale("ru", "RU"))
            assertEquals("5,5", GameMath.format(5.5))
            assertEquals("2,4B", GameMath.format(2_400_000_000.0))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `цифры и разряды не зависят от локали с другой системой счисления`() {
        val saved = Locale.getDefault()
        try {
            // локаль с восточноарабскими цифрами: без Locale.ROOT числа стали бы нечитаемыми
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("5,5", GameMath.format(5.5))
            assertEquals("1${NB}234", GameMath.format(1_234.0))
            assertEquals("2,4B", GameMath.format(2_400_000_000.0))
            assertEquals("12,3", GameMath.decimal(12.34))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `валюта выбирается по коду и переключается по кругу`() {
        assertEquals(Currency.RUB, Currency.fromCode("RUB"))
        assertEquals(Currency.USD, Currency.fromCode("нет такой"))
        assertEquals(Currency.RUB, Currency.next("USD"))
        assertEquals(Currency.USD, Currency.next("CNY"))
    }
}
