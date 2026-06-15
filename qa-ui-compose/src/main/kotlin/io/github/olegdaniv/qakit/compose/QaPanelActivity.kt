package io.github.olegdaniv.qakit.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

/**
 * Хост-активіті для QA панелі. Запускати через [QaKit.openPanel] —
 * напряму звертатись не обов'язково.
 *
 * ```kotlin
 * startActivity(QaPanelActivity.intent(context))
 * ```
 */
class QaPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                QaPanelScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, QaPanelActivity::class.java)
    }
}
