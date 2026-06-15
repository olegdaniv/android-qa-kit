package io.github.olegdaniv.qakit.core.error

import org.json.JSONObject
import java.io.File

/**
 * Простий файловий store помилок — по одному JSON-об'єкту на рядок (JSONL).
 *
 * Потрібен щоб **краш переживав перезапуск процесу**: запис відбувається
 * синхронно в момент крашу, тож після рестарту помилку видно в QA панелі.
 *
 * Не для конкурентного доступу з кількох процесів — лише з одного app-процесу.
 * Усі записи синхронізовані, читання толерантне до пошкоджених рядків.
 */
internal class ErrorStore(private val file: File) {

    private val lock = Any()

    /** Завантажити останні [max] записів. Пошкоджені рядки пропускаються. */
    fun load(max: Int): List<ErrorEntry> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        val parsed = runCatching {
            file.readLines().mapNotNull { line ->
                line.takeIf(String::isNotBlank)?.let(::fromJson)
            }
        }.getOrDefault(emptyList())
        if (parsed.size > max) parsed.takeLast(max) else parsed
    }

    /** Дописати один запис у кінець файлу (швидко, crash-safe). */
    fun append(entry: ErrorEntry) = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(toJson(entry) + "\n")
        }
    }

    /** Перезаписати файл поточним набором (компакція, щоб файл не ріс безмежно). */
    fun rewrite(entries: List<ErrorEntry>) = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(entries.joinToString("\n", postfix = "\n", transform = ::toJson))
        }
    }

    fun clear() = synchronized(lock) { runCatching { file.delete() } }

    /** Кількість рядків (записів) у файлі. */
    fun lineCount(): Int = synchronized(lock) {
        if (!file.exists()) 0 else runCatching { file.readLines().count(String::isNotBlank) }
            .getOrDefault(0)
    }

    private fun toJson(e: ErrorEntry): String = JSONObject().apply {
        put(KEY_MESSAGE, e.message)
        put(KEY_STACK, e.stackTrace)
        put(KEY_THREAD, e.thread)
        put(KEY_TIMESTAMP, e.timestamp)
        put(KEY_CRASH, e.isCrash)
    }.toString()

    private fun fromJson(line: String): ErrorEntry? = runCatching {
        val o = JSONObject(line)
        ErrorEntry(
            message = o.optString(KEY_MESSAGE),
            stackTrace = o.optString(KEY_STACK),
            thread = o.optString(KEY_THREAD),
            timestamp = o.optLong(KEY_TIMESTAMP, System.currentTimeMillis()),
            isCrash = o.optBoolean(KEY_CRASH, false),
        )
    }.getOrNull()

    private companion object {
        const val KEY_MESSAGE = "m"
        const val KEY_STACK = "s"
        const val KEY_THREAD = "t"
        const val KEY_TIMESTAMP = "ts"
        const val KEY_CRASH = "c"
    }
}
