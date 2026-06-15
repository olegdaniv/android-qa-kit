package io.github.olegdaniv.qakit.core.anr

import android.os.Debug
import android.os.Handler
import android.os.Looper

/**
 * Live-детектор ANR: фоновий потік періодично постить «пінг» у головний
 * [Looper] і, якщо той не виконується за [timeoutMs], вважає головний потік
 * заблокованим і повідомляє стектрейс main-потоку.
 *
 * Це евристика (не системний ANR від ОС, для якого є
 * [android.app.ApplicationExitInfo]), але ловить фриз **наживо** на всіх
 * версіях Android і показує, де саме завис головний потік.
 *
 * Використання (зазвичай через [QaKit]):
 * ```kotlin
 * AnrWatchdog.timeoutMs = 5_000
 * AnrWatchdog.install { durationMs, stack -> /* record */ }
 * AnrWatchdog.uninstall()
 * ```
 */
object AnrWatchdog {

    /** Поріг блокування головного потоку (мс), після якого фіксуємо ANR. */
    var timeoutMs: Long = 5_000L

    /** Інтервал перевірки чи відновився потік після ANR (мс). */
    private const val RECHECK_INTERVAL_MS = 500L

    private var watcher: Watcher? = null

    /** True якщо watchdog зараз працює. */
    val isRunning: Boolean get() = watcher != null

    /**
     * Запускає watchdog.
     *
     * @param onAnr колбек (потік watchdog'а): тривалість блокування + стек main-потоку.
     *              Спрацьовує один раз на епізод — повторно лише після відновлення.
     */
    fun install(onAnr: (durationMs: Long, mainThreadStack: String) -> Unit) {
        uninstall()
        watcher = Watcher(timeoutMs, onAnr).apply {
            isDaemon = true
            start()
        }
    }

    /** Зупиняє watchdog. Безпечно викликати якщо не запущений. */
    fun uninstall() {
        watcher?.shutdown()
        watcher = null
    }

    private class Watcher(
        private val timeoutMs: Long,
        private val onAnr: (Long, String) -> Unit,
    ) : Thread("qa-anr-watchdog") {

        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile private var ticked = true
        @Volatile private var stopped = false

        private val tick = Runnable { ticked = true }

        override fun run() {
            while (!stopped && !isInterrupted) {
                ticked = false
                mainHandler.post(tick)

                if (!sleepFor(timeoutMs)) return

                // Не спрацьовуємо під відлагодженням — кроки в дебагері блокують main.
                if (!ticked && !Debug.isDebuggerConnected() && !Debug.waitingForDebugger()) {
                    val stack = mainThreadStack()
                    onAnr(timeoutMs, stack)

                    // Чекаємо відновлення, щоб не спамити одну й ту саму подію.
                    while (!ticked && !stopped) {
                        if (!sleepFor(RECHECK_INTERVAL_MS)) return
                    }
                }
            }
        }

        /** Спить, повертає false якщо потік перервали (час завершуватись). */
        private fun sleepFor(ms: Long): Boolean = try {
            sleep(ms)
            true
        } catch (e: InterruptedException) {
            false
        }

        private fun mainThreadStack(): String =
            Looper.getMainLooper().thread.stackTrace
                .joinToString("\n") { "\tat $it" }

        fun shutdown() {
            stopped = true
            interrupt()
        }
    }
}
