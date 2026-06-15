package io.github.olegdaniv.qakit.compose

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chuckerteam.chucker.api.Chucker
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

private val TABS = listOf("Logs", "Errors", "Device", "Network", "Perf")

/**
 * Головний екран QA панелі з табами Logs / Errors / Device / Network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaPanelScreen(onClose: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QA Kit") },
                actions = {
                    TextButton(onClick = onClose) { Text("Close") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            when (selectedTab) {
                0 -> LogsTab()
                1 -> ErrorsTab()
                2 -> DeviceTab()
                3 -> NetworkTab()
                else -> PerfTab()
            }
        }
    }
}

@Composable
private fun LogsTab() {
    val logs = remember { mutableStateListOf<LogEntry>().apply { addAll(QaLogger.entries) } }
    DisposableEffect(Unit) {
        val listener: (LogEntry) -> Unit = { logs.add(it) }
        QaLogger.addListener(listener)
        onDispose { QaLogger.removeListener(listener) }
    }

    Column(Modifier.fillMaxSize()) {
        TabActions(
            count = logs.size,
            onClear = { QaLogger.clear(); logs.clear() },
        )
        if (logs.isEmpty()) {
            EmptyState("Логів поки немає.\nЗгенеруй їх з головного екрану.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(logs.asReversed()) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = entry.level.name.take(1),
                color = entry.level.color(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(entry.formattedTime, style = MaterialTheme.typography.labelMedium)
            Text(
                entry.tag,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            entry.message,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ErrorsTab() {
    val errors = remember { mutableStateListOf<ErrorEntry>().apply { addAll(GlobalErrorHandler.entries) } }
    DisposableEffect(Unit) {
        val listener: (ErrorEntry) -> Unit = { errors.add(it) }
        GlobalErrorHandler.addListener(listener)
        onDispose { GlobalErrorHandler.removeListener(listener) }
    }

    Column(Modifier.fillMaxSize()) {
        TabActions(
            count = errors.size,
            onClear = { GlobalErrorHandler.clear(); errors.clear() },
        )
        if (errors.isEmpty()) {
            EmptyState("Помилок поки немає.\nЗгенеруй їх з головного екрану.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(errors.asReversed()) { entry -> ErrorRow(entry) }
            }
        }
    }
}

@Composable
private fun ErrorRow(entry: ErrorEntry) {
    val (label, color) = when (entry.kind) {
        ErrorKind.CRASH -> "💥 CRASH" to Color(0xFFD32F2F)
        ErrorKind.ANR -> "🐢 ANR" to Color(0xFF7B1FA2)
        ErrorKind.HANDLED -> "⚠️ Handled" to Color(0xFFF57C00)
    }
    Card(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(entry.message, fontWeight = FontWeight.SemiBold)
            Text("thread: ${entry.thread}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                entry.stackTrace.lineSequence().take(6).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DeviceTab() {
    val info = remember { runCatching { QaKit.deviceInfo }.getOrNull() }
    if (info == null) {
        EmptyState("DeviceInfo недоступний.\nВиклич QaKit.init() в Application.")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(
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
            )
        ) { (label, value) -> InfoRow(label, value) }
    }
}

@Composable
private fun InfoRow(label: String, value: String, rating: PerfRating? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text(
            text = if (rating != null) "$value  ● ${rating.name}" else value,
            color = rating?.color() ?: MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

private fun PerfRating.color(): Color = when (this) {
    PerfRating.GOOD -> Color(0xFF388E3C)
    PerfRating.WARNING -> Color(0xFFF57C00)
    PerfRating.BAD -> Color(0xFFD32F2F)
}

@Composable
private fun NetworkTab() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Мережеві запити збираються через Chucker (OkHttp interceptor). " +
                "Зроби запит з головного екрану і відкрий список:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = { context.startActivity(Chucker.getLaunchIntent(context)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Відкрити Chucker")
        }
    }
}

@Composable
private fun PerfTab() {
    var snap by remember { mutableStateOf(PerformanceMonitor.snapshot()) }
    DisposableEffect(Unit) {
        val listener: (PerformanceSnapshot) -> Unit = { snap = it }
        PerformanceMonitor.addListener(listener)
        onDispose { PerformanceMonitor.removeListener(listener) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Старт", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        item {
            InfoRow("Cold start (process → init)", "${snap.coldStartMs} ms", snap.coldStartRating)
        }
        item {
            InfoRow(
                "Time to first frame",
                snap.timeToFirstFrameMs?.let { "$it ms" } ?: "вимірюється…",
                snap.ttfRating,
            )
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("Кадри / Jank", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        item { InfoRow("Оброблено кадрів", snap.framesTracked.toString()) }
        item {
            InfoRow(
                "Janky кадрів",
                "${snap.jankyFrames} (${"%.1f".format(snap.jankPercent)}%)",
                snap.jankRating,
            )
        }
        item {
            InfoRow("Найдовший кадр", "${"%.1f".format(snap.worstFrameMs)} ms", snap.worstFrameRating)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { PerformanceMonitor.reset() }) { Text("Reset jank") }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("Пам'ять", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        item {
            InfoRow("Java heap", "${snap.usedHeapMb} MB / ${snap.maxHeapMb} MB", snap.heapRating)
        }
        item { InfoRow("Пік Java heap", "${snap.peakUsedHeapMb} MB") }
        item { InfoRow("Native heap", "${snap.nativeHeapMb} MB") }
        item {
            Spacer(Modifier.height(8.dp))
            MemorySparkline(snap.memoryHistoryMb, snap.maxHeapMb)
        }
    }
}

@Composable
private fun MemorySparkline(history: List<Long>, maxHeapMb: Long) {
    if (history.isEmpty()) return
    val peak = (history.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val n = history.size
        if (n < 2) return@Canvas
        val stepX = size.width / (n - 1)
        val points = history.mapIndexed { i, v ->
            Offset(i * stepX, size.height * (1f - v.toFloat() / peak))
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f,
            )
        }
    }
    Text(
        "used-heap, останні ${history.size} семплів (пік ${peak} MB)",
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun TabActions(count: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Записів: $count", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onClear) { Text("Clear") }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun LogLevel.color(): Color = when (this) {
    LogLevel.VERBOSE -> Color(0xFF9E9E9E)
    LogLevel.DEBUG -> Color(0xFF1976D2)
    LogLevel.INFO -> Color(0xFF388E3C)
    LogLevel.WARNING -> Color(0xFFF57C00)
    LogLevel.ERROR -> Color(0xFFD32F2F)
}
