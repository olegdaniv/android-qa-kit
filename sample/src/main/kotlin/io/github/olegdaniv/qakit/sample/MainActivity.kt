package io.github.olegdaniv.qakit.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.olegdaniv.qakit.core.QaKit
import io.github.olegdaniv.qakit.core.error.GlobalErrorHandler
import io.github.olegdaniv.qakit.core.logger.QaLogger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    private val logCounter = AtomicInteger()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {

                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    SampleScreen(
                        onOpenPanel = { QaKit.openPanel(this) },
                        onEmitLog = ::emitLog,
                        onRecordError = ::recordError,
                        onNetworkRequest = ::makeNetworkRequest,
                        onOpenFragments = ::openFragmentDemo,
                        onJank = ::generateJank,
                        onAnr = ::simulateAnr,
                        onCrash = ::simulateCrash,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }

    /** Генерує лог із рівнем, що циклічно змінюється. */
    private fun emitLog() {
        val n = logCounter.incrementAndGet()
        when (n % 5) {
            0 -> QaLogger.v("Sample", "verbose повідомлення #$n")
            1 -> QaLogger.d("Network", "GET /users → 200 (#$n)")
            2 -> QaLogger.i("Auth", "користувач увійшов (#$n)")
            3 -> QaLogger.w("Cache", "промах кешу (#$n)")
            else -> QaLogger.e("Sync", "помилка синхронізації (#$n)")
        }
        toast("Лог #$n записано")
    }

    /** Записує handled-помилку (без падіння застосунку). */
    private fun recordError() {
        GlobalErrorHandler.record(
            IllegalStateException("Демо handled-помилка о ${System.currentTimeMillis()}")
        )
        toast("Помилку записано")
    }

    /** Реальний HTTP-запит — потрапляє в Chucker. */
    private fun makeNetworkRequest() {
        val client = (application as App).httpClient
        val request = Request.Builder()
            .url("https://httpbin.org/get?source=qa-kit")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                QaLogger.e("Network", "Запит впав: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { QaLogger.d("Network", "Відповідь: HTTP ${it.code}") }
            }
        })
        toast("Запит надіслано — дивись Chucker")
    }

    /** Відкриває демо-екран на Fragment'ах — для перевірки Fragment lifecycle-логів. */
    private fun openFragmentDemo() {
        startActivity(FragmentDemoActivity.intent(this))
    }

    /** Серія коротких стелів main-потоку — створює janky-кадри (таб Perf). */
    private fun generateJank() {
        val handler = Handler(Looper.getMainLooper())
        repeat(20) { i ->
            handler.postDelayed({
                val end = System.currentTimeMillis() + 70
                @Suppress("ControlFlowWithEmptyBody")
                while (System.currentTimeMillis() < end) { /* навантаження main */ }
            }, i * 100L)
        }
        toast("Генерую jank ~2 с — гортай і дивись Perf")
    }

    /** Блокує головний потік — watchdog зафіксує ANR. */
    private fun simulateAnr() {
        toast("Блокую main-потік на 8 с…")
        try {
            Thread.sleep(8_000)
        } catch (_: InterruptedException) {
        }
    }

    /** Симулює неперехоплений виняток — GlobalErrorHandler його зафіксує. */
    private fun simulateCrash() {
        throw RuntimeException("Демо краш з android-qa-kit sample")
    }

    private fun toast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

@Composable
private fun SampleScreen(
    onOpenPanel: () -> Unit,
    onEmitLog: () -> Unit,
    onRecordError: () -> Unit,
    onNetworkRequest: () -> Unit,
    onOpenFragments: () -> Unit,
    onJank: () -> Unit,
    onAnr: () -> Unit,
    onCrash: () -> Unit,
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("android-qa-kit", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Натисни, щоб згенерувати дані, потім відкрий QA панель " +
                    "(або просто потруси телефон).",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Button(onClick = onOpenPanel, modifier = Modifier.fillMaxWidth()) {
            Text(if (QaKit.isPanelAvailable) "Відкрити QA панель" else "QA панель недоступна (release)")
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("Згенерувати тестові дані:", style = MaterialTheme.typography.titleMedium)

        Button(onClick = onEmitLog, modifier = Modifier.fillMaxWidth()) {
            Text("Записати лог")
        }
        Button(onClick = onNetworkRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Мережевий запит")
        }
        Button(onClick = onOpenFragments, modifier = Modifier.fillMaxWidth()) {
            Text("Відкрити екран з фрагментами")
        }
        Button(onClick = onJank, modifier = Modifier.fillMaxWidth()) {
            Text("Згенерувати jank")
        }
        Button(onClick = onAnr, modifier = Modifier.fillMaxWidth()) {
            Text("Симулювати ANR (блокує UI 8 с)")
        }
        Button(onClick = onRecordError, modifier = Modifier.fillMaxWidth()) {
            Text("Записати помилку (handled)")
        }
        Button(
            onClick = onCrash,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
        ) {
            Text("Симулювати краш")
        }
    }
}
