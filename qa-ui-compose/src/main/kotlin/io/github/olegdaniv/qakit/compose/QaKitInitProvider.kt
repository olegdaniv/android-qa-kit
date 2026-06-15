package io.github.olegdaniv.qakit.compose

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import io.github.olegdaniv.qakit.core.QaKit

/**
 * No-content provider, що автоматично запускається при старті процесу (ще до
 * Application.onCreate) і реєструє спосіб відкриття QA панелі у [QaKit].
 *
 * Завдяки цьому застосунок може просто викликати [QaKit.openPanel] без прямої
 * залежності на цей debug-only модуль. У release (qa-no-op) провайдера немає —
 * panel opener не реєструється і [QaKit.openPanel] стає no-op.
 */
class QaKitInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        QaKit.registerPanelOpener { ctx ->
            val intent = QaPanelActivity.intent(ctx)
            if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
