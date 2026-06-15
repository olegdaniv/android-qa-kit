package io.github.olegdaniv.qakit.core

import android.app.Application
import android.content.Context
import io.github.olegdaniv.qakit.core.QaKit.init
import io.github.olegdaniv.qakit.core.anr.AnrWatchdog
import io.github.olegdaniv.qakit.core.anr.ExitInfoCollector
import io.github.olegdaniv.qakit.core.error.ErrorEntry
import io.github.olegdaniv.qakit.core.error.ErrorKind
import io.github.olegdaniv.qakit.core.error.GlobalErrorHandler
import io.github.olegdaniv.qakit.core.lifecycle.ActivityLifecycleLogger
import io.github.olegdaniv.qakit.core.logger.QaLogger
import io.github.olegdaniv.qakit.core.perf.PerformanceMonitor
import io.github.olegdaniv.qakit.core.perf.PerfThresholds
import io.github.olegdaniv.qakit.core.model.DeviceInfo
import io.github.olegdaniv.qakit.core.network.NetworkInterceptorConfig
import io.github.olegdaniv.qakit.core.network.QaNetworkInterceptor
import io.github.olegdaniv.qakit.core.shake.ShakeDetector
import io.github.olegdaniv.qakit.core.shake.ShakeDetector.ShakeSensitivity
import okhttp3.Interceptor

/**
 * DSL конфігурація для [QaKit.init].
 */
class QaKitConfig {
    /** Виводити логи в Logcat (через Timber.DebugTree). За замовч. true. */
    var logcatEnabled: Boolean = true

    /** Максимальна кількість логів у буфері. */
    var logBufferSize: Int = 500

    /** Максимальна кількість помилок у буфері. */
    var errorBufferSize: Int = 100

    /**
     * Зберігати помилки на диск, щоб краш було видно після перезапуску процесу.
     * За замовч. true.
     */
    var persistErrors: Boolean = true

    /**
     * Live-детектор ANR (watchdog): фіксує зависання головного потоку наживо.
     * За замовч. true.
     */
    var anrDetection: Boolean = true

    /** Поріг блокування головного потоку (мс) для [anrDetection]. За замовч. 5000. */
    var anrTimeoutMs: Long = 5_000L

    /**
     * Зчитувати справжні ANR / native-краші з [android.app.ApplicationExitInfo]
     * при старті (API 30+). За замовч. true.
     */
    var collectExitInfo: Boolean = true

    /**
     * Збирати метрики продуктивності (jank/кадри, пам'ять, час старту).
     * За замовч. true.
     */
    var performanceMonitoring: Boolean = true

    /**
     * Пороги «здоров'я» метрик продуктивності (рейтинг GOOD/WARNING/BAD у табі Perf).
     * Дефолти — за Android vitals; можна перевизначити.
     */
    var perfThresholds: PerfThresholds = PerfThresholds()

    /** Автоматично логувати lifecycle-події Activity. За замовч. true. */
    var lifecycleLogging: Boolean = true

    /**
     * Логувати також Fragment lifecycle-події (для androidx FragmentActivity).
     * Діє лише коли [lifecycleLogging] == true. За замовч. true.
     */
    var fragmentLifecycleLogging: Boolean = true

    /** Відкривати QA панель при shake. */
    var shakeToOpen: Boolean = true

    /** Чутливість shake жесту. */
    var shakeSensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM

    /** Налаштування network interceptor. null = не створювати interceptor. */
    var networkConfig: NetworkInterceptorConfig? = NetworkInterceptorConfig()

    /** Колбек що викликається при shake — підключається ззовні (з UI шару). */
    internal var onShake: (() -> Unit)? = null
}

/**
 * Головний entry point android-qa-kit.
 *
 * Ініціалізація (один раз у [Application.onCreate]):
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         QaKit.init(this) {
 *             shakeToOpen      = true
 *             shakeSensitivity = ShakeSensitivity.MEDIUM
 *             logBufferSize    = 500
 *             networkConfig    = NetworkInterceptorConfig(
 *                 headersToRedact = setOf("Authorization", "X-Token"),
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * Підключення shake до QA панелі (з UI шару):
 * ```kotlin
 * QaKit.onShake { startActivity(QaPanelActivity.intent(context)) }
 * ```
 *
 * OkHttp interceptor:
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(QaKit.networkInterceptor!!)
 *     .build()
 * ```
 */
object QaKit {

    private var _config = QaKitConfig()
    private var _application: Application? = null
    private var _deviceInfo: DeviceInfo? = null
    private var _networkInterceptor: Interceptor? = null
    private var panelOpener: ((Context) -> Unit)? = null
    private var isInitialized = false

    /** True якщо UI-шар (qa-ui-compose / qa-ui-view) зареєстрував відкриття панелі. */
    val isPanelAvailable: Boolean get() = panelOpener != null

    /** Поточна конфігурація. */
    val config: QaKitConfig get() = _config

    /**
     * DeviceInfo — лениво ініціалізується при першому зверненні.
     * Потребує [init] перед використанням.
     */
    val deviceInfo: DeviceInfo
        get() = _deviceInfo ?: error("QaKit not initialized. Call QaKit.init() first.")

    /**
     * OkHttp interceptor — null якщо [QaKitConfig.networkConfig] == null
     * або [init] не був викликаний.
     */
    val networkInterceptor: Interceptor? get() = _networkInterceptor

    /**
     * Ініціалізує всі компоненти бібліотеки.
     * Викликати один раз в [Application.onCreate].
     *
     * @param application контекст застосунку
     * @param block       DSL конфігурація
     */
    fun init(application: Application, block: QaKitConfig.() -> Unit = {}) {
        if (isInitialized) return
        isInitialized = true

        _application = application
        _config = QaKitConfig().apply(block)

        // Logger
        QaLogger.bufferSize = _config.logBufferSize
        QaLogger.install(debugTree = _config.logcatEnabled)
        QaLogger.d("QaKit", "Initializing android-qa-kit...")

        // Error handler
        GlobalErrorHandler.bufferSize = _config.errorBufferSize
        GlobalErrorHandler.install(context = application, persist = _config.persistErrors)

        // ANR — live watchdog
        if (_config.anrDetection) {
            AnrWatchdog.timeoutMs = _config.anrTimeoutMs
            AnrWatchdog.install { durationMs, mainThreadStack ->
                GlobalErrorHandler.record(
                    ErrorEntry(
                        message = "ANR: головний потік заблоковано >${durationMs}мс",
                        stackTrace = mainThreadStack,
                        thread = "main",
                        kind = ErrorKind.ANR,
                    )
                )
            }
        }

        // ANR / native-краші з минулих сесій (ApplicationExitInfo, API 30+).
        // Фоновий потік — читання трейсів з диску.
        if (_config.collectExitInfo) {
            Thread({
                ExitInfoCollector.collect(application) { GlobalErrorHandler.record(it) }
            }, "qa-exit-info").apply { isDaemon = true }.start()
        }

        // Lifecycle logging
        if (_config.lifecycleLogging) {
            ActivityLifecycleLogger.install(
                application = application,
                logFragments = _config.fragmentLifecycleLogging,
            )
        }

        // Performance metrics (jank/кадри, пам'ять, час старту)
        if (_config.performanceMonitoring) {
            PerformanceMonitor.thresholds = _config.perfThresholds
            PerformanceMonitor.install(application)
        }

        // Device info
        _deviceInfo = DeviceInfo.collect(application)

        // Network interceptor
        _networkInterceptor = _config.networkConfig?.let {
            QaNetworkInterceptor.create(application, it)
        }

        // Shake — за замовчуванням відкриває QA панель, якщо UI-шар підключений
        if (_config.shakeToOpen) {
            ShakeDetector.sensitivity = _config.shakeSensitivity
            ShakeDetector.install(application) {
                _config.onShake?.invoke() ?: openPanel(application)
            }
        }

        QaLogger.d("QaKit", "Initialized. Device: ${_deviceInfo?.deviceName}")
    }

    /**
     * Реєструє колбек для відкриття QA панелі при shake.
     * Викликати після [init], зазвичай в Activity або Application.
     *
     * ```kotlin
     * QaKit.onShake {
     *     startActivity(QaPanelActivity.intent(this))
     * }
     * ```
     */
    fun onShake(block: () -> Unit) {
        _config.onShake = block
    }

    /**
     * Реєструє спосіб відкриття QA панелі. Викликається автоматично UI-шаром
     * (qa-ui-compose / qa-ui-view) при старті застосунку. У release (qa-no-op)
     * нічого не реєструється — [openPanel] стає no-op.
     */
    fun registerPanelOpener(block: (Context) -> Unit) {
        panelOpener = block
    }

    /**
     * Відкриває QA панель. Безпечно викликати з будь-якого білда:
     * у release (без UI-шару) просто нічого не відбувається.
     *
     * ```kotlin
     * Button(onClick = { QaKit.openPanel(context) }) { Text("QA Panel") }
     * ```
     */
    fun openPanel(context: Context) {
        panelOpener?.invoke(context)
    }

    /**
     * Зупиняє shake детектор.
     * Корисно наприклад для release builds через no-op.
     */
    fun uninstall() {
        ShakeDetector.uninstall()
        AnrWatchdog.uninstall()
        PerformanceMonitor.uninstall()
        _application?.let(ActivityLifecycleLogger::uninstall)
        _application = null
        isInitialized = false
    }
}
