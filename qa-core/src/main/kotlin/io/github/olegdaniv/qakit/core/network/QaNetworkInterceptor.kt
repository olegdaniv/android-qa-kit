package io.github.olegdaniv.qakit.core.network

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import okhttp3.Interceptor

/**
 * Налаштування для [QaNetworkInterceptor].
 *
 * @param maxContentLength максимальний розмір body який зберігається (bytes)
 * @param headersToRedact заголовки які будуть приховані в UI (токени, ключі)
 * @param retentionPeriod як довго зберігати запити
 * @param showNotification показувати нотифікацію про HTTP активність
 */
data class NetworkInterceptorConfig(
    val maxContentLength: Long = 250_000L,
    val headersToRedact: Set<String> = setOf("Authorization", "X-Api-Key"),
    val retentionPeriod: RetentionPeriod = RetentionPeriod.ONE_HOUR,
    val showNotification: Boolean = true,
)

enum class RetentionPeriod {
    ONE_HOUR, ONE_DAY, ONE_WEEK;

    internal fun toChuckerPeriod(): RetentionManager.Period = when (this) {
        ONE_HOUR -> RetentionManager.Period.ONE_HOUR
        ONE_DAY -> RetentionManager.Period.ONE_DAY
        ONE_WEEK -> RetentionManager.Period.ONE_WEEK
    }
}

/**
 * Обгортка над [ChuckerInterceptor] для використання в OkHttp клієнті.
 *
 * Використання:
 * ```kotlin
 * val okHttpClient = OkHttpClient.Builder()
 *     .addInterceptor(QaNetworkInterceptor.create(context))
 *     .build()
 * ```
 *
 * З кастомним конфігом:
 * ```kotlin
 * val interceptor = QaNetworkInterceptor.create(
 *     context = context,
 *     config = NetworkInterceptorConfig(
 *         headersToRedact = setOf("Authorization", "X-Token"),
 *         showNotification = false,
 *     )
 * )
 * ```
 *
 * Для Retrofit:
 * ```kotlin
 * Retrofit.Builder()
 *     .client(okHttpClient)
 *     .build()
 * ```
 */
object QaNetworkInterceptor {

    /**
     * Створює налаштований [ChuckerInterceptor] готовий до підключення в OkHttp.
     *
     * @param context будь-який Context (використовується applicationContext)
     * @param config  налаштування — можна не передавати, defaults розумні
     */
    fun create(
        context: Context,
        config: NetworkInterceptorConfig = NetworkInterceptorConfig(),
    ): Interceptor {
        val collector = ChuckerCollector(
            context = context.applicationContext,
            showNotification = config.showNotification,
            retentionPeriod = config.retentionPeriod.toChuckerPeriod(),
        )

        return ChuckerInterceptor.Builder(context.applicationContext)
            .collector(collector)
            .maxContentLength(config.maxContentLength)
            .redactHeaders(config.headersToRedact)
            .alwaysReadResponseBody(true)
            .build()
    }
}
