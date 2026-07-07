package ru.capital.idle.data

import android.content.Context
import ru.capital.idle.core.game.GameState
import java.io.File

/**
 * Хранилище прогресса.
 * Источник истины — JSON-файл во внутреннем хранилище (save.json): он переживает
 * любые изменения схемы Room (в т.ч. fallbackToDestructiveMigration), поэтому
 * обновления игры не обнуляют прогресс. Room используется как вторичный кэш.
 */
class GameRepository(private val dao: GameDao, context: Context) {

    private val appContext = context.applicationContext
    private val saveFile: File get() = File(appContext.filesDir, "save.json")
    private val tmpFile: File get() = File(appContext.filesDir, "save.json.tmp")

    suspend fun load(): GameState? {
        // 1) сначала пробуем JSON-файл (главный источник, устойчив к версиям БД)
        runCatching {
            if (saveFile.exists()) {
                val json = saveFile.readText()
                SaveFile.fromJson(json)?.let { return it.toState() }
            }
        }
        // 2) если файла нет или он битый — берём из Room (первый запуск, старая установка)
        return dao.get()?.toState()
    }

    suspend fun save(state: GameState) {
        val entity = state.toEntity()
        // Room (вторичный кэш)
        runCatching { dao.upsert(entity) }
        // JSON-файл (главный источник): атомарная запись через временный файл
        runCatching {
            val json = SaveFile.toJson(entity)
            tmpFile.writeText(json)
            if (tmpFile.exists()) {
                if (saveFile.exists()) saveFile.delete()
                tmpFile.renameTo(saveFile)
            }
        }
    }
}
