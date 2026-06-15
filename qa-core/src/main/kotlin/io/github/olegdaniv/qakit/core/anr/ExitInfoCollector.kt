package io.github.olegdaniv.qakit.core.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.github.olegdaniv.qakit.core.error.ErrorEntry
import io.github.olegdaniv.qakit.core.error.ErrorKind

/**
 * Зчитує справжню історію завершень процесу через
 * [ActivityManager.getHistoricalProcessExitReasons] (API 30+) і повертає
 * ANR та native-краші як [ErrorEntry] — з реальним системним трейсом.
 *
 * На відміну від [AnrWatchdog] це не евристика, а дані від ОС, але видно їх
 * лише **після перезапуску** процесу. Дедуплікація — за timestamp останнього
 * обробленого завершення (зберігається в SharedPreferences).
 */
internal object ExitInfoCollector {

    private const val PREFS = "qa-kit"
    private const val KEY_LAST_TS = "last_exit_ts"

    /** Скільки рядків системного трейсу зберігати (вони бувають дуже великі). */
    private const val MAX_TRACE_LINES = 200

    /**
     * Збирає нові ANR / native-краші з минулих сесій.
     * No-op до API 30. Викликати з фонового потоку — читає трейси з диску.
     *
     * @param record куди віддавати знайдені записи.
     */
    fun collect(context: Context, record: (ErrorEntry) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        collectApi30(context.applicationContext, record)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collectApi30(context: Context, record: (ErrorEntry) -> Unit) {
        val am = context.getSystemService<ActivityManager>() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTs = prefs.getLong(KEY_LAST_TS, 0L)

        val infos = runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, /* pid = */ 0, /* maxNum = */ 0)
        }.getOrNull().orEmpty()

        var newestTs = lastTs

        // Список відсортований від найновішого; обробляємо лише новіші за lastTs.
        for (info in infos) {
            if (info.timestamp <= lastTs) continue
            newestTs = maxOf(newestTs, info.timestamp)

            val kind = info.kind() ?: continue
            record(
                ErrorEntry(
                    message = "${kind.tag}: ${info.describe()}",
                    stackTrace = info.readTrace(),
                    thread = "system",
                    timestamp = info.timestamp,
                    kind = kind,
                )
            )
        }

        if (newestTs > lastTs) {
            prefs.edit().putLong(KEY_LAST_TS, newestTs).apply()
        }
    }

    /** Цікавлять лише ANR і native-краші — JVM-краші вже ловить crash handler наживо. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.kind(): ErrorKind? = when (reason) {
        ApplicationExitInfo.REASON_ANR -> ErrorKind.ANR
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ErrorKind.CRASH
        else -> null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.describe(): String =
        (description ?: "").ifBlank { "code $status" }

    /** Системний trace доступний для ANR і native-крашів; обрізаємо за розміром. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.readTrace(): String = runCatching {
        traceInputStream?.bufferedReader()?.use { reader ->
            reader.lineSequence().take(MAX_TRACE_LINES).joinToString("\n")
        }
    }.getOrNull() ?: "(трейс недоступний)"
}
