package io.github.olegdaniv.qakit.core.error

import android.content.Context
import io.github.olegdaniv.qakit.core.logger.QaLogger
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Зловлена помилка — зберігається в буфері та відображається в QA панелі.
 */
data class ErrorEntry(
    val message: String,
    val stackTrace: String,
    val thread: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCrash: Boolean = false,   // true = uncaught, app впав
)

/**
 * Глобальний перехоплювач помилок.
 *
 * Перехоплює два типи:
 * - [Thread.UncaughtExceptionHandler] — fatal crash (app зупиняється)
 * - Ручний виклик [record] — handled exceptions (наприклад в catch-блоці)
 *
 * Помилки персистяться на диск (JSONL), тож **краш видно після перезапуску
 * процесу** — запис відбувається синхронно в момент крашу.
 *
 * Використання:
 * ```kotlin
 * // Application.onCreate()
 * GlobalErrorHandler.install(context)
 *
 * // В catch-блоці (non-fatal)
 * GlobalErrorHandler.record(exception)
 *
 * // В UI — підписатись на нові помилки
 * GlobalErrorHandler.addListener { error -> showSnackbar(error.message) }
 *
 * // Всі помилки для QA екрану
 * GlobalErrorHandler.entries
 * ```
 */
object GlobalErrorHandler {

    /** Максимальна кількість помилок у буфері. */
    var bufferSize: Int = 100

    private val _entries = CopyOnWriteArrayList<ErrorEntry>()
    val entries: List<ErrorEntry> get() = _entries

    private val listeners = CopyOnWriteArrayList<(ErrorEntry) -> Unit>()

    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false

    /** Файлове сховище. null = персистентність вимкнена (тільки в пам'яті). */
    private var store: ErrorStore? = null

    /**
     * Встановлює глобальний обробник.
     * Викликати один раз в [android.app.Application.onCreate].
     *
     * @param context контекст застосунку — потрібен для персистентності помилок.
     * @param rethrow якщо true — після запису передає crash далі
     *                системному обробнику (app все одно впаде, але зафіксується).
     * @param persist якщо true — помилки зберігаються на диск і переживають
     *                перезапуск процесу (краш видно при наступному старті).
     */
    fun install(context: Context, rethrow: Boolean = true, persist: Boolean = true) {
        if (isInstalled) return
        isInstalled = true

        if (persist) {
            store = ErrorStore(File(context.applicationContext.filesDir, STORE_PATH)).also { s ->
                // Підвантажуємо помилки з минулих сесій (включно з крашами).
                _entries.addAll(s.load(bufferSize))
            }
        }

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(
                throwable = throwable,
                thread = thread.name,
                isCrash = true,
            )
            if (rethrow) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Записати handled exception вручну.
     *
     * ```kotlin
     * try {
     *     riskyCall()
     * } catch (e: Exception) {
     *     GlobalErrorHandler.record(e)
     * }
     * ```
     */
    fun record(
        throwable: Throwable,
        thread: String = Thread.currentThread().name,
        isCrash: Boolean = false,
    ) {
        val entry = ErrorEntry(
            message = throwable.message ?: throwable::class.simpleName ?: "Unknown error",
            stackTrace = throwable.stackTraceToString(),
            thread = thread,
            isCrash = isCrash,
        )
        add(entry)

        // Також пишемо в QaLogger щоб помилки були видні в загальному лог-буфері
        QaLogger.e(
            tag = if (isCrash) "CRASH" else "Error",
            message = entry.message,
            t = throwable,
        )
    }

    /**
     * Підписатись на нові помилки — для показу Snackbar/Toast в UI.
     *
     * Увага: listener викликається з будь-якого потоку.
     * Для UI-операцій переключайся на Main dispatcher.
     */
    fun addListener(listener: (ErrorEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ErrorEntry) -> Unit) {
        listeners.remove(listener)
    }

    /** Очистити буфер і файлове сховище (кнопка "Clear errors" в QA панелі). */
    fun clear() {
        _entries.clear()
        store?.clear()
    }

    // --- Internal ---

    private fun add(entry: ErrorEntry) {
        val trimmed = _entries.size >= bufferSize
        if (trimmed) _entries.removeFirstOrNull()
        _entries.add(entry)
        persist(trimmed)
        listeners.forEach { it(entry) }
    }

    private fun persist(trimmed: Boolean) {
        val s = store ?: return
        // Якщо буфер не витіснявся і файл не розрісся — швидкий append останнього.
        // Інакше компактимо: перезаписуємо файл поточним набором.
        if (!trimmed && s.lineCount() < bufferSize * 2) {
            s.append(_entries.last())
        } else {
            s.rewrite(_entries)
        }
    }

    private const val STORE_PATH = "qa-kit/errors.jsonl"
}
