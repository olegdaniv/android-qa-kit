package io.github.olegdaniv.qakit.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chuckerteam.chucker.api.Chucker
import com.google.android.material.tabs.TabLayout
import io.github.olegdaniv.qakit.core.QaKit
import io.github.olegdaniv.qakit.core.error.ErrorEntry
import io.github.olegdaniv.qakit.core.error.ErrorKind
import io.github.olegdaniv.qakit.core.error.GlobalErrorHandler
import io.github.olegdaniv.qakit.core.logger.LogEntry
import io.github.olegdaniv.qakit.core.logger.LogLevel
import io.github.olegdaniv.qakit.core.logger.QaLogger
import io.github.olegdaniv.qakit.core.perf.PerfRating
import io.github.olegdaniv.qakit.core.perf.PerformanceMonitor
import io.github.olegdaniv.qakit.core.perf.PerformanceSnapshot

/**
 * View/XML-варіант QA панелі (без Compose) для legacy-застосунків.
 * Самодостатня [AppCompatActivity] — запускати через [QaKit.openPanel].
 *
 * ```kotlin
 * startActivity(QaPanelActivity.intent(context))
 * ```
 */
class QaPanelActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private var selectedTab = 0

    // Live-слухачі — знімаються в onDestroy.
    private val logListener: (LogEntry) -> Unit = { refreshIfVisible(TAB_LOGS) }
    private val errorListener: (ErrorEntry) -> Unit = { refreshIfVisible(TAB_ERRORS) }
    private val perfListener: (PerformanceSnapshot) -> Unit = { refreshIfVisible(TAB_PERF) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // Header: title + Close
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(8), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "QA Kit"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "Close"
                setOnClickListener { finish() }
            })
        })

        // Tabs
        root.addView(TabLayout(this).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            TABS.forEach { addTab(newTab().setText(it)) }
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        })

        content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        root.addView(content)

        setContentView(root)

        QaLogger.addListener(logListener)
        GlobalErrorHandler.addListener(errorListener)
        PerformanceMonitor.addListener(perfListener)

        showTab(0)
    }

    override fun onDestroy() {
        QaLogger.removeListener(logListener)
        GlobalErrorHandler.removeListener(errorListener)
        PerformanceMonitor.removeListener(perfListener)
        super.onDestroy()
    }

    private fun refreshIfVisible(tab: Int) {
        if (selectedTab == tab) runOnUiThread { showTab(tab) }
    }

    private fun showTab(index: Int) {
        selectedTab = index
        content.removeAllViews()
        val view = when (index) {
            TAB_LOGS -> logsView()
            TAB_ERRORS -> errorsView()
            TAB_DEVICE -> deviceView()
            TAB_NETWORK -> networkView()
            else -> perfView()
        }
        content.addView(view)
    }

    // --- Tabs ---

    private fun logsView(): View {
        val entries = QaLogger.entries.asReversed()
        return listTab(
            count = entries.size,
            empty = "Логів поки немає.",
            onClear = { QaLogger.clear(); showTab(TAB_LOGS) },
        ) { container ->
            entries.forEach { e ->
                container.addView(TextView(this).apply {
                    text = "${e.level.name.take(1)}  ${e.formattedTime}  ${e.tag}\n${e.message}"
                    setTextColor(e.level.color())
                    typeface = Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                })
            }
        }
    }

    private fun errorsView(): View {
        val entries = GlobalErrorHandler.entries.asReversed()
        return listTab(
            count = entries.size,
            empty = "Помилок поки немає.",
            onClear = { GlobalErrorHandler.clear(); showTab(TAB_ERRORS) },
        ) { container ->
            entries.forEach { e ->
                container.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    addView(TextView(context).apply {
                        text = e.kind.label()
                        setTextColor(e.kind.color())
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = e.message
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = "thread: ${e.thread}\n" +
                            e.stackTrace.lineSequence().take(6).joinToString("\n")
                        typeface = Typeface.MONOSPACE
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    })
                })
            }
        }
    }

    private fun deviceView(): View {
        val info = runCatching { QaKit.deviceInfo }.getOrNull()
            ?: return emptyView("DeviceInfo недоступний.\nВиклич QaKit.init() в Application.")
        return scrollColumn { col ->
            listOf(
                "App" to info.appName,
                "Package" to info.packageName,
                "Version" to info.appVersion,
                "Build type" to info.buildType,
                "Device" to info.deviceName,
                "Android" to "${info.androidVersion} (API ${info.sdkInt})",
                "Board" to info.board,
                "ABIs" to info.supportedAbis.joinToString(),
                "Memory" to info.memoryUsage,
                "Low memory" to info.isLowMemory.toString(),
                "Screen" to info.screenInfo,
                "Density" to "${info.screenDensity}x (${info.screenDensityDpi} dpi)",
            ).forEach { (label, value) -> col.addView(infoRow(label, value)) }
        }
    }

    private fun networkView(): View = scrollColumn { col ->
        col.addView(TextView(this).apply {
            text = "Мережеві запити збираються через Chucker (OkHttp interceptor)."
        })
        col.addView(Button(this).apply {
            text = "Відкрити Chucker"
            setOnClickListener { startActivity(Chucker.getLaunchIntent(this@QaPanelActivity)) }
        })
    }

    private fun perfView(): View {
        val s = PerformanceMonitor.snapshot()
        return scrollColumn { col ->
            col.addView(sectionTitle("Старт"))
            col.addView(infoRow("Cold start (process → init)", "${s.coldStartMs} ms", s.coldStartRating))
            col.addView(infoRow("Time to first frame", s.timeToFirstFrameMs?.let { "$it ms" } ?: "вимірюється…", s.ttfRating))

            col.addView(sectionTitle("Кадри / Jank"))
            col.addView(infoRow("Оброблено кадрів", s.framesTracked.toString()))
            col.addView(infoRow("Janky кадрів", "${s.jankyFrames} (${"%.1f".format(s.jankPercent)}%)", s.jankRating))
            col.addView(infoRow("Найдовший кадр", "${"%.1f".format(s.worstFrameMs)} ms", s.worstFrameRating))
            col.addView(Button(this).apply {
                text = "Reset jank"
                setOnClickListener { PerformanceMonitor.reset(); showTab(TAB_PERF) }
            })

            col.addView(sectionTitle("Пам'ять"))
            col.addView(infoRow("Java heap", "${s.usedHeapMb} MB / ${s.maxHeapMb} MB", s.heapRating))
            col.addView(infoRow("Пік Java heap", "${s.peakUsedHeapMb} MB"))
            col.addView(infoRow("Native heap", "${s.nativeHeapMb} MB"))
        }
    }

    // --- UI helpers ---

    /** Список із шапкою "Записів: N" + Clear та прокручуваним контейнером. */
    private inline fun listTab(
        count: Int,
        empty: String,
        crossinline onClear: () -> Unit,
        fill: (LinearLayout) -> Unit,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Записів: $count"
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "Clear"
                setOnClickListener { onClear() }
            })
        })

        if (count == 0) {
            addView(emptyView(empty))
        } else {
            addView(scrollColumn { fill(it) })
        }
    }

    private inline fun scrollColumn(fill: (LinearLayout) -> Unit): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        fill(column)
        return ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            addView(column)
        }
    }

    private fun infoRow(label: String, value: String, rating: PerfRating? = null): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            addView(TextView(context).apply {
                text = if (rating != null) "$value  ● ${rating.name}" else value
                if (rating != null) setTextColor(rating.color())
                typeface = Typeface.MONOSPACE
            })
        }

    private fun sectionTitle(title: String): View = TextView(this).apply {
        text = title
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun emptyView(message: String): View = TextView(this).apply {
        text = message
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(48), dp(32), dp(32))
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAB_LOGS = 0
        private const val TAB_ERRORS = 1
        private const val TAB_DEVICE = 2
        private const val TAB_NETWORK = 3
        private const val TAB_PERF = 4
        private val TABS = listOf("Logs", "Errors", "Device", "Network", "Perf")

        fun intent(context: Context): Intent =
            Intent(context, QaPanelActivity::class.java)
    }
}

private fun LogLevel.color(): Int = when (this) {
    LogLevel.VERBOSE -> Color.parseColor("#9E9E9E")
    LogLevel.DEBUG -> Color.parseColor("#1976D2")
    LogLevel.INFO -> Color.parseColor("#388E3C")
    LogLevel.WARNING -> Color.parseColor("#F57C00")
    LogLevel.ERROR -> Color.parseColor("#D32F2F")
}

private fun ErrorKind.label(): String = when (this) {
    ErrorKind.CRASH -> "💥 CRASH"
    ErrorKind.ANR -> "🐢 ANR"
    ErrorKind.HANDLED -> "⚠️ Handled"
}

private fun ErrorKind.color(): Int = when (this) {
    ErrorKind.CRASH -> Color.parseColor("#D32F2F")
    ErrorKind.ANR -> Color.parseColor("#7B1FA2")
    ErrorKind.HANDLED -> Color.parseColor("#F57C00")
}

private fun PerfRating.color(): Int = when (this) {
    PerfRating.GOOD -> Color.parseColor("#388E3C")
    PerfRating.WARNING -> Color.parseColor("#F57C00")
    PerfRating.BAD -> Color.parseColor("#D32F2F")
}
