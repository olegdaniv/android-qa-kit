package io.github.olegdaniv.qakit.core.shake

import android.content.Context
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.squareup.seismic.ShakeDetector as SeismicDetector

/**
 * Обгортка над [com.squareup.seismic.ShakeDetector].
 *
 * Використання:
 * ```kotlin
 * // Application.onCreate()
 * ShakeDetector.install(context) {
 *     openQaPanel()
 * }
 *
 * // Якщо треба зупинити (наприклад у release build через no-op)
 * ShakeDetector.uninstall()
 * ```
 *
 * Чутливість:
 * ```kotlin
 * ShakeDetector.sensitivity = ShakeSensitivity.MEDIUM // за замовчуванням
 * ```
 */
object ShakeDetector {

    enum class ShakeSensitivity(internal val value: Int) {
        LIGHT(SeismicDetector.SENSITIVITY_LIGHT),
        MEDIUM(SeismicDetector.SENSITIVITY_MEDIUM),
        HARD(SeismicDetector.SENSITIVITY_HARD),
    }

    var sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM
        set(value) {
            field = value
            detector?.setSensitivity(value.value)
        }

    private var detector: SeismicDetector? = null
    private var sensorManager: SensorManager? = null

    /**
     * Починає слухати shake жести.
     *
     * @param context будь-який Context (використовується applicationContext)
     * @param onShake колбек — викликається на будь-якому потоці.
     *                Для UI-операцій переключайся на Main dispatcher.
     */
    fun install(context: Context, onShake: () -> Unit) {
        val appContext = context.applicationContext
        sensorManager = appContext.getSystemService<SensorManager>() ?: return

        detector = SeismicDetector { onShake() }.also { sd ->
            sd.setSensitivity(sensitivity.value)
            sd.start(sensorManager)
        }
    }

    /**
     * Зупиняє слухача сенсора.
     * Безпечно викликати якщо [install] не був викликаний.
     */
    fun uninstall() {
        detector?.stop()
        detector = null
        sensorManager = null
    }

    /** True якщо детектор зараз активний. */
    val isInstalled: Boolean get() = detector != null
}
