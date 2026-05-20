package io.github.olegdaniv.qakit.core

import android.app.Application
import io.github.olegdaniv.qakit.core.QaKit.init
import io.github.olegdaniv.qakit.core.error.GlobalErrorHandler
import io.github.olegdaniv.qakit.core.logger.QaLogger
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
    private var _deviceInfo: DeviceInfo? = null
    private var _networkInterceptor: Interceptor? = null
    private var isInitialized = false

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

        _config = QaKitConfig().apply(block)

        // Logger
        QaLogger.bufferSize = _config.logBufferSize
        QaLogger.install(debugTree = _config.logcatEnabled)
        QaLogger.d("QaKit", "Initializing android-qa-kit...")

        // Error handler
        GlobalErrorHandler.bufferSize = _config.errorBufferSize
        GlobalErrorHandler.install()

        // Device info
        _deviceInfo = DeviceInfo.collect(application)

        // Network interceptor
        _networkInterceptor = _config.networkConfig?.let {
            QaNetworkInterceptor.create(application, it)
        }

        // Shake
        if (_config.shakeToOpen) {
            ShakeDetector.sensitivity = _config.shakeSensitivity
            ShakeDetector.install(application) {
                _config.onShake?.invoke()
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
     * Зупиняє shake детектор.
     * Корисно наприклад для release builds через no-op.
     */
    fun uninstall() {
        ShakeDetector.uninstall()
        isInitialized = false
    }
}
