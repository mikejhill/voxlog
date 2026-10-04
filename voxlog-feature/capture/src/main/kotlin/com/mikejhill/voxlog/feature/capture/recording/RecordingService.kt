package com.mikejhill.voxlog.feature.capture.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.tracing.trace
import com.mikejhill.voxlog.core.data.audio.WavRecorder
import com.mikejhill.voxlog.core.data.repository.NoteRepository
import com.mikejhill.voxlog.core.data.repository.VoiceCaptureRequest
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.CaptureIntents
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.engine.whisper.WhisperAudioFormat
import com.mikejhill.voxlog.feature.capture.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service (type `microphone`) that owns the recorder, so recording continues with the
 * screen off or the app in the background, and only ever stops when the user presses Stop (or
 * storage is about to run out). The microphone is opened before anything is written to the
 * database to minimize time-to-first-sample.
 */
@AndroidEntryPoint
class RecordingService : Service() {
    @Inject
    lateinit var noteRepository: NoteRepository

    @Inject
    lateinit var stateHolder: RecordingStateHolder

    @Inject
    lateinit var audioFileStore: AudioFileStore

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: WavRecorder? = null
    private var persistJob: Job? = null
    private var monitorJob: Job? = null
    private var activeNoteId: NoteId? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(
                requestedCategoryId = intent.getStringExtra(CaptureIntents.EXTRA_CATEGORY_ID)?.let(::CategoryId),
                shortcutId = intent.getStringExtra(CaptureIntents.EXTRA_SHORTCUT_ID),
            )

            ACTION_STOP -> stop()

            ACTION_DISCARD -> discard()

            ACTION_CHANGE_CATEGORY -> intent.getStringExtra(CaptureIntents.EXTRA_CATEGORY_ID)?.let { id ->
                stateHolder.update { state -> (state as? RecordingState.Recording)?.copy(categoryId = CategoryId(id)) ?: state }
            }
        }
        return START_NOT_STICKY
    }

    private fun start(requestedCategoryId: CategoryId?, shortcutId: String?) = trace(TRACE_START) {
        if (recorder != null) return@trace
        val noteId = NoteId.random()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            failAndStop(getString(R.string.capture_error_permission))
            return@trace
        }
        val wavRecorder = WavRecorder(noteRepository.recordingFileFor(noteId))
        try {
            wavRecorder.start()
        } catch (exception: IllegalStateException) {
            failAndStop(exception.message ?: getString(R.string.capture_error_microphone))
            return@trace
        }
        recorder = wavRecorder
        activeNoteId = noteId
        val initialCategoryId = requestedCategoryId ?: CategoryId.UNCATEGORIZED
        stateHolder.update {
            RecordingState.Recording(noteId, initialCategoryId, SystemClock.elapsedRealtime(), 0f, remainingStorageMinutes())
        }
        // Persist the RECORDING row in parallel with capture; the mic is already live.
        persistJob = scope.launch(Dispatchers.IO) {
            val categoryId = requestedCategoryId ?: settingsRepository.current().defaultCategory
            if (categoryId != initialCategoryId) {
                stateHolder.update { (it as? RecordingState.Recording)?.copy(categoryId = categoryId) ?: it }
            }
            noteRepository.beginVoiceNote(VoiceCaptureRequest(noteId, categoryId, shortcutId))
        }
        monitorJob = scope.launch { monitor(wavRecorder) }
    }

    private suspend fun monitor(wavRecorder: WavRecorder) {
        launchLevelCollector(wavRecorder)
        while (scope.isActive) {
            val minutesLeft = remainingStorageMinutes()
            stateHolder.update { (it as? RecordingState.Recording)?.copy(remainingStorageMinutes = minutesLeft) ?: it }
            if (audioFileStore.availableBytes() < MINIMUM_FREE_BYTES) {
                stop()
                return
            }
            delay(STORAGE_CHECK_INTERVAL_MILLIS)
        }
    }

    private fun launchLevelCollector(wavRecorder: WavRecorder) = scope.launch {
        wavRecorder.inputLevel.collect { level ->
            stateHolder.update { (it as? RecordingState.Recording)?.copy(inputLevel = level) ?: it }
        }
    }

    private fun stop() {
        val wavRecorder = recorder ?: return
        val noteId = activeNoteId ?: return
        val categoryId = (stateHolder.state.value as? RecordingState.Recording)?.categoryId ?: CategoryId.UNCATEGORIZED
        recorder = null
        monitorJob?.cancel()
        scope.launch {
            val durationMillis = trace(TRACE_STOP) { wavRecorder.stop() }
            persistJob?.join()
            noteRepository.completeVoiceNote(noteId, durationMillis, categoryId)
            stateHolder.update { RecordingState.Saved(noteId) }
            finish()
        }
    }

    private fun discard() {
        val wavRecorder = recorder ?: return
        val noteId = activeNoteId ?: return
        recorder = null
        monitorJob?.cancel()
        scope.launch {
            wavRecorder.stop()
            persistJob?.join()
            noteRepository.deleteNote(noteId)
            noteRepository.recordingFileFor(noteId).delete()
            stateHolder.update { RecordingState.Discarded }
            finish()
        }
    }

    private fun failAndStop(reason: String) {
        stateHolder.update { RecordingState.Failed(reason) }
        finish()
    }

    private fun finish() {
        activeNoteId = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun remainingStorageMinutes(): Long = audioFileStore.availableBytes() / WhisperAudioFormat.BYTES_PER_SECOND / SECONDS_PER_MINUTE

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(CaptureIntents.ACTION_RECORD_VOICE).setPackage(packageName).putExtra(EXTRA_RESUME_ONLY, true),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_stop, getString(R.string.capture_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Intents understood by the service. */
    companion object {
        private const val ACTION_START = "com.mikejhill.voxlog.recording.START"
        private const val ACTION_STOP = "com.mikejhill.voxlog.recording.STOP"
        private const val ACTION_DISCARD = "com.mikejhill.voxlog.recording.DISCARD"
        private const val ACTION_CHANGE_CATEGORY = "com.mikejhill.voxlog.recording.CHANGE_CATEGORY"

        /** Extra on the notification tap intent: show the active recording instead of starting a new one. */
        const val EXTRA_RESUME_ONLY: String = "com.mikejhill.voxlog.extra.RESUME_ONLY"

        /** Trace section from start request to the first audio buffer being captured. */
        const val TRACE_START: String = "VoxLog.recordingStart"

        /** Trace section for finalizing audio on stop. */
        const val TRACE_STOP: String = "VoxLog.recordingStop"

        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
        private const val STORAGE_CHECK_INTERVAL_MILLIS = 5_000L
        private const val MINIMUM_FREE_BYTES = 20L * 1024 * 1024
        private const val SECONDS_PER_MINUTE = 60

        /** Starts recording into [categoryId], or the default category when null. */
        fun start(context: Context, categoryId: CategoryId?, shortcutId: String?) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(CaptureIntents.EXTRA_CATEGORY_ID, categoryId?.value)
                .putExtra(CaptureIntents.EXTRA_SHORTCUT_ID, shortcutId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops and saves the active recording. */
        fun stop(context: Context) = send(context, ACTION_STOP)

        /** Stops and deletes the active recording. */
        fun discard(context: Context) = send(context, ACTION_DISCARD)

        /** Changes the category the active recording will be saved into. */
        fun changeCategory(context: Context, categoryId: CategoryId) {
            context.startService(
                Intent(context, RecordingService::class.java)
                    .setAction(ACTION_CHANGE_CATEGORY)
                    .putExtra(CaptureIntents.EXTRA_CATEGORY_ID, categoryId.value),
            )
        }

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, RecordingService::class.java).setAction(action))
        }
    }
}
