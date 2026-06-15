package io.github.olegdaniv.qakit.sample

import android.app.Application
import io.github.olegdaniv.qakit.core.QaKit
import io.github.olegdaniv.qakit.core.network.NetworkInterceptorConfig
import okhttp3.OkHttpClient

/**
 * Демо-Application: ініціалізує android-qa-kit один раз при старті процесу.
 */
class App : Application() {

    /** Спільний OkHttp клієнт з підключеним QA network interceptor (Chucker). */
    lateinit var httpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()

        QaKit.init(this) {
            shakeToOpen = true
            logBufferSize = 500
            networkConfig = NetworkInterceptorConfig(
                headersToRedact = setOf("Authorization", "X-Api-Key"),
            )
        }

        httpClient = OkHttpClient.Builder()
            .apply { QaKit.networkInterceptor?.let { addInterceptor(it) } }
            .build()
    }
}
