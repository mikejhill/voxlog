package com.mikejhill.voxlog.feature.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.mikejhill.voxlog.core.model.CaptureIntents

/** Quick Settings tile that starts a voice note from anywhere, including the lock screen shade. */
class RecordTileService : TileService() {
    override fun onClick() {
        val intent = Intent(CaptureIntents.ACTION_RECORD_VOICE)
            .setPackage(packageName)
            .putExtra(CaptureIntents.EXTRA_SHORTCUT_ID, SHORTCUT_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    // The PendingIntent overload only exists on API 34+; older devices must use the Intent overload.
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseLegacy(intent: Intent) = startActivityAndCollapse(intent)

    private companion object {
        const val SHORTCUT_ID = "quick-settings-tile"
    }
}
