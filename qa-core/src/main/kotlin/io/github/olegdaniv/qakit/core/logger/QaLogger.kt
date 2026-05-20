package io.github.olegdaniv.qakit.core.logger

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Severity рівні лога.
 */
enum class LogLevel { VERBOSE, DEBUG, INFO, WARNING, ERROR }

/**
 * Одна запис логу — зберігається в буфері для відображення в QA панелі.
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Головний логер бібліотеки.
 *
 * Використання:
 * ```kotlin
 * QaLogger.install()              // одноразово в Application.onCreate()
 *
 * QaLogger.d("MyTag", "hello")
 * QaLogger.e("MyTag", "oops", throwable)
 *
 * val logs = QaLogger.entries     // всі записи для QA екрану
 * QaLogger.clear()                // очистити буфер
 * ```
 */
object QaLogger {

    /** Максимальна кількість записів у буфері. Старіші витісняються автоматично. */
    var bufferSize: Int = 500

    private val _entries = CopyOnWriteArrayList<LogEntry>()

    /** Поточний буфер логів (thread-safe, read-only). */
    val entries: List<LogEntry> get() = _entries

    /** Слухачі — викликаються щоразу при новому записі. */
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    /**
     * Встановлює Timber дерево та активує буферизацію.
     * Викликати один раз в [android.app.Application.onCreate].
     *
     * @param debugTree якщо true — також встановлює стандартний Timber.DebugTree
     *                  (виводить у Logcat). Для release передай false.
     */
    fun install(debugTree: Boolean = true) {
        if (debugTree) Timber.plant(Timber.DebugTree())
        Timber.plant(BufferTree())
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    /** Очистити буфер (наприклад кнопка "Clear" в QA панелі). */
    fun clear() = _entries.clear()

    // --- Зручні методи логування ---

    fun v(tag: String, message: String) = Timber.tag(tag).v(message)
    fun d(tag: String, message: String) = Timber.tag(tag).d(message)
    fun i(tag: String, message: String) = Timber.tag(tag).i(message)
    fun w(tag: String, message: String, t: Throwable? = null) =
        if (t != null) Timber.tag(tag).w(t, message) else Timber.tag(tag).w(message)
    fun e(tag: String, message: String, t: Throwable? = null) =
        if (t != null) Timber.tag(tag).e(t, message) else Timber.tag(tag).e(message)

    // --- Internal ---

    private fun record(entry: LogEntry) {
        if (_entries.size >= bufferSize) {
            _entries.removeFirstOrNull()
        }
        _entries.add(entry)
        listeners.forEach { it(entry) }
    }

    private class BufferTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = priority.toLogLevel()
            val entry = LogEntry(
                level = level,
                tag = tag ?: "App",
                message = if (t != null) "$message\n${t.stackTraceToString()}" else message,
            )
            record(entry)
        }
    }
}

private fun Int.toLogLevel(): LogLevel = when (this) {
    android.util.Log.VERBOSE -> LogLevel.VERBOSE
    android.util.Log.DEBUG   -> LogLevel.DEBUG
    android.util.Log.INFO    -> LogLevel.INFO
    android.util.Log.WARN    -> LogLevel.WARNING
    else                     -> LogLevel.ERROR
}
