package com.mikejhill.voxlog.core.data.pipeline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mikejhill.voxlog.core.model.NoteId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NoteProcessingScheduler] built on WorkManager. Each note has one unique work chain so repeated
 * edits replace rather than pile up. Transcriptions run one at a time through a shared queue.
 */
@Singleton
class WorkManagerNoteProcessingScheduler @Inject constructor(@param:ApplicationContext private val context: Context) :
    NoteProcessingScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun scheduleAfterVoiceCapture(noteId: NoteId) {
        // Transcriptions share one queue so concurrent recordings never load two models at once.
        workManager.beginUniqueWork(TRANSCRIPTION_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, transcription(noteId))
            .then(postProcessing(noteId))
            .then(embedding(noteId))
            .then(sync())
            .enqueue()
    }

    override fun scheduleAfterTextCapture(noteId: NoteId) {
        workManager.beginUniqueWork(noteWorkName(noteId), ExistingWorkPolicy.REPLACE, postProcessing(noteId))
            .then(embedding(noteId))
            .then(sync())
            .enqueue()
    }

    override fun scheduleAfterEdit(noteId: NoteId) {
        workManager.beginUniqueWork(editWorkName(noteId), ExistingWorkPolicy.REPLACE, embedding(noteId, delay = EDIT_DEBOUNCE))
            .then(sync())
            .enqueue()
    }

    override fun scheduleReprocess(noteId: NoteId) = scheduleAfterTextCapture(noteId)

    override fun scheduleSync() {
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, sync(delay = SYNC_DEBOUNCE))
    }

    private fun transcription(noteId: NoteId): OneTimeWorkRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
        .setInputData(workDataOf(KEY_NOTE_ID to noteId.value))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(BACKOFF_SECONDS))
        .addTag(TAG_TRANSCRIPTION)
        .build()

    private fun postProcessing(noteId: NoteId): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PostProcessingWorker>()
        .setInputData(workDataOf(KEY_NOTE_ID to noteId.value))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(BACKOFF_SECONDS))
        .build()

    private fun embedding(noteId: NoteId, delay: Duration = Duration.ZERO): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<EmbeddingWorker>()
            .setInputData(workDataOf(KEY_NOTE_ID to noteId.value))
            .setInitialDelay(delay)
            .build()

    private fun sync(delay: Duration = Duration.ZERO): OneTimeWorkRequest = OneTimeWorkRequestBuilder<FolderSyncWorker>()
        .setInitialDelay(delay)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
        .build()

    private fun noteWorkName(noteId: NoteId) = "note-${noteId.value}"

    private fun editWorkName(noteId: NoteId) = "edit-${noteId.value}"

    /** Work names and tags observable by the UI and tests. */
    companion object {
        /** Unique work name of the serial transcription queue. */
        const val TRANSCRIPTION_QUEUE: String = "transcription-queue"

        /** Tag on every transcription request. */
        const val TAG_TRANSCRIPTION: String = "transcription"

        private const val SYNC_WORK_NAME = "folder-sync"
        private const val BACKOFF_SECONDS = 30L
        private val EDIT_DEBOUNCE: Duration = Duration.ofSeconds(3)
        private val SYNC_DEBOUNCE: Duration = Duration.ofSeconds(5)
    }
}
