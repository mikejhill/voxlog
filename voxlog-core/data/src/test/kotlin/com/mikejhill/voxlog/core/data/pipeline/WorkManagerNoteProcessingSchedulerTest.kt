package com.mikejhill.voxlog.core.data.pipeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.model.NoteId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerNoteProcessingSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler = WorkManagerNoteProcessingScheduler(context)

    @Before
    fun setUp() {
        // These tests only verify how work is queued, not what the workers do.
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    private fun workManager() = WorkManager.getInstance(context)

    @Test
    fun `voice captures queue a transcribe, post-process, embed and sync chain on the shared queue`() {
        scheduler.scheduleAfterVoiceCapture(NoteId("a"))

        val queue = workManager().getWorkInfosForUniqueWork(WorkManagerNoteProcessingScheduler.TRANSCRIPTION_QUEUE).get()
        val transcriptions = workManager().getWorkInfosByTag(WorkManagerNoteProcessingScheduler.TAG_TRANSCRIPTION).get()
        assertThat(transcriptions).hasSize(1)
        assertThat(queue).hasSize(4)
    }

    @Test
    fun `text captures skip transcription`() {
        scheduler.scheduleAfterTextCapture(NoteId("t"))

        assertThat(workManager().getWorkInfosByTag(WorkManagerNoteProcessingScheduler.TAG_TRANSCRIPTION).get()).isEmpty()
        assertThat(workManager().getWorkInfosForUniqueWork("note-t").get()).isNotEmpty()
    }

    @Test
    fun `repeated edits replace the pending re-index instead of piling up`() {
        scheduler.scheduleAfterEdit(NoteId("e"))
        scheduler.scheduleAfterEdit(NoteId("e"))

        val pending = workManager().getWorkInfosForUniqueWork("edit-e").get().filter {
            it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
        assertThat(pending).hasSize(2)
    }
}
