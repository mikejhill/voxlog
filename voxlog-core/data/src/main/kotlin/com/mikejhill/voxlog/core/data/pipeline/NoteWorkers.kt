package com.mikejhill.voxlog.core.data.pipeline

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.embedding.EmbeddingService
import com.mikejhill.voxlog.core.data.sync.FolderSyncer
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.engine.llm.LlmException
import com.mikejhill.voxlog.engine.whisper.TranscriptionException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Input key carrying the note id for per-note workers. */
internal const val KEY_NOTE_ID = "note_id"

private const val MAX_ATTEMPTS = 5

/** Reads the note id passed by [WorkManagerNoteProcessingScheduler]. */
private fun WorkerParameters.noteId(): NoteId = NoteId(requireNotNull(inputData.getString(KEY_NOTE_ID)))

/**
 * Transcribes a recording in a foreground worker so long recordings finish even if the app is
 * closed. Progress is written to the note row (for the UI) and to WorkManager progress.
 */
@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted private val parameters: WorkerParameters,
    private val runner: TranscriptionRunner,
    private val noteDao: NoteDao,
    private val notifications: ProcessingNotifications,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val noteId = parameters.noteId()
        setForeground(getForegroundInfo())
        return try {
            transcribeReportingProgress(noteId)
            Result.success()
        } catch (exception: IOException) {
            retryOrFail(noteId, "Transcription failed: ${exception.message}")
        } catch (exception: TranscriptionException) {
            markFailed(noteId, exception.message ?: "Transcription failed")
            Result.failure()
        }
    }

    /** Native progress callbacks only set a value; a coroutine persists it, so whisper never blocks on I/O. */
    private suspend fun transcribeReportingProgress(noteId: NoteId) = coroutineScope {
        val progress = MutableStateFlow(0)
        val reporter = launch {
            progress.collect { percent ->
                notifications.updateTranscriptionProgress(percent)
                noteDao.updateTranscriptionProgress(noteId.value, percent)
            }
        }
        try {
            runner.run(noteId) { percent -> progress.value = percent }
        } finally {
            reporter.cancel()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        ProcessingNotifications.TRANSCRIPTION_NOTIFICATION_ID,
        notifications.transcriptionNotification(0),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    private suspend fun retryOrFail(noteId: NoteId, reason: String): Result {
        if (runAttemptCount + 1 < MAX_ATTEMPTS) return Result.retry()
        markFailed(noteId, reason)
        return Result.failure()
    }

    private suspend fun markFailed(noteId: NoteId, reason: String) {
        val note = noteDao.getNote(noteId.value)?.note ?: return
        noteDao.update(note.copy(status = NoteStatus.FAILED.name, processingLog = reason, transcriptionProgressPercent = null))
    }
}

/** Runs optional AI post-processing and hooks. Always succeeds unless a retryable network error occurs. */
@HiltWorker
class PostProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted private val parameters: WorkerParameters,
    private val runner: PostProcessingRunner,
    private val noteDao: NoteDao,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val noteId = parameters.noteId()
        return try {
            runner.run(noteId)
            Result.success()
        } catch (exception: LlmException) {
            if (runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                // Give up quietly: the note is already saved and fully usable without processing.
                noteDao.getNote(noteId.value)?.note?.let {
                    noteDao.update(it.copy(status = NoteStatus.READY.name, processingLog = "AI processing failed: ${exception.message}"))
                }
                Result.success()
            }
        }
    }
}

/** Computes the semantic-search embedding for a note when the model is available. */
@HiltWorker
class EmbeddingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted private val parameters: WorkerParameters,
    private val embeddingService: EmbeddingService,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        embeddingService.embedNote(parameters.noteId())
        return Result.success()
    }
}

/** Mirrors notes and audio into the user's sync folder, if one is configured. */
@HiltWorker
class FolderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val folderSyncer: FolderSyncer,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        folderSyncer.syncAll()
        Result.success()
    } catch (_: IOException) {
        Result.retry()
    }
}
