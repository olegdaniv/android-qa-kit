package io.github.olegdaniv.qakit.core.error

import io.github.olegdaniv.qakit.core.logger.LogLevel
import io.github.olegdaniv.qakit.core.logger.LogEntry
import io.github.olegdaniv.qakit.core.logger.QaLogger
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
 * Використання:
 * ```kotlin
 * // Application.onCreate()
 * GlobalErrorHandler.install()
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

    /**
     * Встановлює глобальний обробник.
     * Викликати один раз в [android.app.Application.onCreate].
     *
     * @param rethrow якщо true — після запису передає crash далі
     *                системному обробнику (app все одно впаде, але зафіксується).
     */
    fun install(rethrow: Boolean = true) {
        if (isInstalled) return
        isInstalled = true

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

    /** Очистити буфер (кнопка "Clear errors" в QA панелі). */
    fun clear() = _entries.clear()

    // --- Internal ---

    private fun add(entry: ErrorEntry) {
        if (_entries.size >= bufferSize) {
            _entries.removeFirstOrNull()
        }
        _entries.add(entry)
        listeners.forEach { it(entry) }
    }
}
