package com.mikejhill.voxlog.core.data.pipeline

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.audio.WavHeader
import com.mikejhill.voxlog.core.data.repository.VoiceCaptureRequest
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.testing.TestDataGraph
import com.mikejhill.voxlog.engine.whisper.WhisperAudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureRecoveryTest {
    private val scope = TestScope()
    private val harness = TestDataGraph(scope)
    private val recovery = CaptureRecovery(harness.database.noteDao(), harness.audioFileStore, harness.scheduler)

    @After
    fun tearDown() = harness.close()

    @Test
    fun `an interrupted recording is repaired, timed and queued for transcription`() = scope.runTest {
        val id = NoteId.random()
        harness.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))
        // Simulate a crash: header still says 0 bytes, but two seconds of audio were written.
        harness.notes.recordingFileFor(id).writeBytes(WavHeader.build(0) + ByteArray(WhisperAudioFormat.BYTES_PER_SECOND * 2))

        val recovered = recovery.recoverInterruptedRecordings()

        assertThat(recovered).isEqualTo(1)
        val note = harness.notes.getNote(id)!!
        assertThat(note.status).isEqualTo(NoteStatus.TRANSCRIBING)
        assertThat(note.durationMillis).isEqualTo(2_000)
        assertThat(harness.scheduler.calls).containsExactly("voice:${id.value}")
        val header = harness.notes.recordingFileFor(id).readBytes()
        val dataSize = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertThat(dataSize).isEqualTo(WhisperAudioFormat.BYTES_PER_SECOND * 2)
    }

    @Test
    fun `a recording with no audio is marked failed`() = scope.runTest {
        val id = NoteId.random()
        harness.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))

        recovery.recoverInterruptedRecordings()

        assertThat(harness.notes.getNote(id)!!.status).isEqualTo(NoteStatus.FAILED)
        assertThat(harness.scheduler.calls).isEmpty()
    }
}
