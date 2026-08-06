package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ru.capital.idle.core.game.Currency
import ru.capital.idle.core.game.GameMath
import ru.capital.idle.core.game.GameState
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Набор валют: доллар и рубль, больше ничего.
 *
 * Евро и юань из игры убраны, но в сохранениях игроков их коды остались. Здесь проверяется
 * и сам набор, и то, что старый код никого не роняет: `fromCode` отдаёт на него доллар,
 * а загрузка приводит код к существующему — иначе `EUR` дожил бы до следующей записи файла.
 */
class CurrencySetTest {

    @Test
    fun `валют ровно две — доллар и рубль`() {
        assertEquals(listOf("USD", "RUB"), Currency.entries.map { it.code })
        assertEquals("$", Currency.USD.symbol)
        assertEquals("₽", Currency.RUB.symbol)
    }

    /** Фишка в шапке зовёт `next` по кругу — на двух значениях это простое чередование. */
    @Test
    fun `переключатель чередует доллар и рубль в обе стороны`() {
        assertEquals(Currency.RUB, Currency.next("USD"))
        assertEquals(Currency.USD, Currency.next("RUB"))

        // два нажатия возвращают на место, и так сколько угодно раз
        var code = "USD"
        repeat(6) { code = Currency.next(code).code }
        assertEquals("USD", code)
    }

    @Test
    fun `коды удалённых валют читаются как доллар`() {
        assertEquals(Currency.USD, Currency.fromCode("EUR"))
        assertEquals(Currency.USD, Currency.fromCode("CNY"))
        assertEquals(Currency.USD, Currency.fromCode(""))
        assertEquals(Currency.USD, Currency.fromCode("нет такой"))
    }

    /** С кодом удалённой валюты переключатель тоже работает: доллар, а следом рубль. */
    @Test
    fun `переключение из удалённой валюты ведёт в рубль`() {
        assertEquals(Currency.RUB, Currency.next("EUR"))
        assertEquals(Currency.RUB, Currency.next("CNY"))
    }

    @Test
    fun `форматирование с кодом удалённой валюты даёт доллары`() {
        val cur = Currency.fromCode("EUR")
        // \u00A0 — неразрывный пробел, им format разделяет разряды
        assertEquals("$ 1\u00A0000", GameMath.formatMoney(1_000.0, cur))
    }

    // ===================== старые сохранения =====================

    /** Файл, записанный, когда евро ещё было в списке. */
    private fun jsonWithCurrency(code: String): String {
        val o = org.json.JSONObject(SaveFile.toJson(GameState(money = 4_200.0).toEntity()))
        o.put("currencyCode", code)
        return o.toString()
    }

    @Test
    fun `сохранение с кодом EUR загружается и даёт доллар`() {
        val entity = SaveFile.fromJson(jsonWithCurrency("EUR"))
        assertNotNull("файл со старым кодом валюты должен читаться", entity)

        val state = entity!!.toState()
        assertEquals("USD", state.currencyCode)
        assertEquals(Currency.USD, Currency.fromCode(state.currencyCode))
        assertEquals("остальное состояние не задето", 4_200.0, state.money, GameTestFixtures.EPS)
    }

    @Test
    fun `сохранение с кодом CNY тоже даёт доллар`() {
        val state = SaveFile.fromJson(jsonWithCurrency("CNY"))!!.toState()
        assertEquals("USD", state.currencyCode)
    }

    /**
     * Главное: устаревший код не должен пережить следующее сохранение. Приводим его при
     * загрузке, поэтому в файл уходит уже `USD`, а не `EUR`.
     */
    @Test
    fun `в следующую запись файла уходит существующий код`() {
        val loaded = SaveFile.fromJson(jsonWithCurrency("EUR"))!!.toState()
        val written = SaveFile.toJson(loaded.toEntity())

        assertEquals("USD", org.json.JSONObject(written).getString("currencyCode"))
        // и после второго круга код остаётся живым
        assertEquals("USD", SaveFile.fromJson(written)!!.toState().currencyCode)
    }

    @Test
    fun `живой код валюты загрузка не трогает`() {
        val state = SaveFile.fromJson(jsonWithCurrency("RUB"))!!.toState()
        assertEquals("RUB", state.currencyCode)
    }
}
