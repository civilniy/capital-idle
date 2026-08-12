package ru.capital.idle

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.capital.idle.core.game.GameState
import ru.capital.idle.data.SaveFile
import ru.capital.idle.data.toEntity
import ru.capital.idle.data.toState

/**
 * Выбранное оформление хранится в состоянии и переживает запись-чтение.
 *
 * Ключ темы лежит строкой: `core/game` не знает про `ui`, а неизвестный ключ (в том числе
 * его отсутствие в старом файле) означает тему по умолчанию.
 */
class ThemeChoiceTest {

    private fun roundTrip(s: GameState): GameState =
        SaveFile.fromJson(SaveFile.toJson(s.toEntity()))!!.toState()

    @Test
    fun `по умолчанию выбрана текущая тема`() {
        assertEquals("glass", GameState().themeId)
    }

    @Test
    fun `выбранная тема переживает сохранение и загрузку`() {
        assertEquals("matte", roundTrip(GameState(themeId = "matte")).themeId)
        assertEquals("glass", roundTrip(GameState(themeId = "glass")).themeId)
    }

    @Test
    fun `в старом сохранении темы нет — открывается текущая`() {
        val o = JSONObject(SaveFile.toJson(GameState(themeId = "matte").toEntity()))
        o.remove("themeId")
        val back = SaveFile.fromJson(o.toString())!!.toState()
        assertEquals("старый файл не знает про темы", "glass", back.themeId)
    }

    @Test
    fun `тема не влияет на остальное состояние`() {
        val a = GameState(money = 1234.0, bullion = 7L)
        val b = a.copy(themeId = "matte")
        assertEquals(a.money, b.money, 0.0)
        assertEquals(a.copy(themeId = a.themeId), b.copy(themeId = a.themeId))
    }
}
