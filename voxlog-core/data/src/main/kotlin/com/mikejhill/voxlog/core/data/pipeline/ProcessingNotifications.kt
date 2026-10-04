package com.mikejhill.voxlog.core.data.pipeline

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mikejhill.voxlog.core.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Low-importance notifications shown while notes are processed in the background. */
@Singleton
class ProcessingNotifications @Inject constructor(@param:ApplicationContext private val context: Context) {
    init {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.processing_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.processing_channel_description)
            },
        )
    }

    /** Builds the transcription progress notification. */
    fun transcriptionNotification(progressPercent: Int): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_transcribe)
        .setContentTitle(context.getString(R.string.transcribing_title))
        .setProgress(PERCENT, progressPercent, progressPercent == 0)
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .build()

    /** Updates the visible transcription progress, if the user allows notifications. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers the POST_NOTIFICATIONS grant on API 33+.
    fun updateTranscriptionProgress(progressPercent: Int) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return
        notificationManager.notify(TRANSCRIPTION_NOTIFICATION_ID, transcriptionNotification(progressPercent))
    }

    /** Channel and notification ids. */
    companion object {
        /** Channel for background processing. */
        const val CHANNEL_ID: String = "processing"

        /** Notification id used by the transcription foreground worker. */
        const val TRANSCRIPTION_NOTIFICATION_ID: Int = 2001

        private const val PERCENT = 100
    }
}
