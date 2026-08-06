package ru.capital.idle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.capital.idle.core.game.Chronicle
import ru.capital.idle.core.game.EnterpriseNames
import ru.capital.idle.core.game.Industries

/**
 * Хроника: запись событий и её разбор обратно в текст.
 *
 * Отдельного внимания стоит запись об открытии предприятия: в неё попадает название,
 * которое игрок вписывает сам, а значит — любые знаки, включая разделители формата.
 */
class ChronicleTest {

    private fun text(code: String, param: String): String? =
        Chronicle.render(Chronicle.entry(7, code, param))?.second

    @Test
    fun `запись об открытии показывает и ступень, и название`() {
        val prod = Industries.all.first { it.id == "prod" }
        assertEquals(
            "Бизнес: ${prod.levels[0].name} «Литьё и точка»",
            text("biz", "prod:1:Литьё и точка")
        )
    }

    @Test
    fun `два предприятия одной отрасли различаются в ленте`() {
        val a = text("biz", "prod:1:Литьё и точка")
        val b = text("biz", "prod:1:Всё по чертежу")
        assertNotNull(a)
        assertTrue("записи не должны совпадать — ради этого название и добавлено", a != b)
    }

    /** Записи, сделанные до появления названия: параметр «отрасль:open». */
    @Test
    fun `старая запись без названия читается по-прежнему`() {
        val prod = Industries.all.first { it.id == "prod" }
        assertEquals("Бизнес: открыто «${prod.levels[0].name}»", text("biz", "prod:open"))
    }

    @Test
    fun `двоеточие в названии не ломает разбор`() {
        assertEquals("Бизнес: Мастерская «Всё: по чертежу»", text("biz", "prod:1:Всё: по чертежу"))
    }

    @Test
    fun `разделители записи в названии не ломают ленту`() {
        // «|» разделяет поля записи, «;» — записи между собой в сохранении
        val rec = Chronicle.entry(7, "biz", "prod:1:Труба|дело;да")
        assertTrue("разделители обязаны быть экранированы", !rec.drop(2).contains(";"))
        assertEquals("Бизнес: Мастерская «Труба|дело;да»", Chronicle.render(rec)?.second)
    }

    @Test
    fun `предельно длинное название не теряется`() {
        val long = "Я".repeat(EnterpriseNames.MAX_NAME_LEN)
        assertEquals("Бизнес: Мастерская «$long»", text("biz", "prod:1:$long"))
    }

    @Test
    fun `день и неизвестный код разбираются как раньше`() {
        assertEquals(7, Chronicle.render(Chronicle.entry(7, "quit"))?.first)
        assertNull("неизвестный код записи не показывается", Chronicle.render("7|нет такого|"))
        assertNull("мусор вместо записи не роняет ленту", Chronicle.render("ерунда"))
    }

    @Test
    fun `прочие коды не задеты экранированием`() {
        assertEquals("Устроились: Курьер", text("job", "courier"))
        assertEquals("Диплом: MBA", text("edu", "mba"))
    }
}
