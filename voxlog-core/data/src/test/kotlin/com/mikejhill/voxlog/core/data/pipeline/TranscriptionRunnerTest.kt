package com.mikejhill.voxlog.core.data.pipeline

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.audio.WavHeader
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.data.repository.VoiceCaptureRequest
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.testing.TestDataGraph
import com.mikejhill.voxlog.core.testing.TestTime
import com.mikejhill.voxlog.engine.whisper.SpeechTranscriber
import com.mikejhill.voxlog.engine.whisper.TranscriptionResult
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranscriptionRunnerTest {
    private val scope = TestScope()
    private val graph = TestDataGraph(scope)
    private val models = ModelRepository(graph.audioFileStore)
    private val transcriber = FakeSpeechTranscriber()
    private val runner = TranscriptionRunner(
        graph.database.noteDao(),
        transcriber,
        models,
        graph.settings,
        graph.audioFileStore,
        TestTime.CLOCK,
    )

    @After
    fun tearDown() = graph.close()

    @Test
    fun `missing notes and text notes do not invoke transcription`() = scope.runTest {
        assertThat(runner.run(NoteId("missing")) {}).isFalse()
        val id = graph.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, null, "typed"))
        assertThat(runner.run(id) {}).isFalse()
        assertThat(transcriber.calls).isEqualTo(0)
    }

    @Test
    fun `missing recording marks the note failed without downloading a model`() = scope.runTest {
        val id = voiceNote(hasRecording = false)
        assertThat(runner.run(id) {}).isFalse()
        val row = graph.database.noteDao().getNote(id.value)!!.note
        assertThat(row.status).isEqualTo(NoteStatus.FAILED.name)
        assertThat(row.processingLog).isEqualTo("Recording file missing")
        assertThat(transcriber.calls).isEqualTo(0)
    }

    @Test
    fun `partial text and progress persist before final transcript replaces them`() = scope.runTest {
        val id = voiceNote()
        placeModel("tiny.en")
        val progress = mutableListOf<Int>()
        transcriber.afterPartial = {
            val row = graph.database.noteDao().getNote(id.value)!!.note
            assertThat(row.text).isEqualTo("partial words")
            assertThat(row.rawTranscript).isEqualTo("partial words")
        }
        assertThat(runner.run(id) { progress += it }).isTrue()
        val row = graph.database.noteDao().getNote(id.value)!!.note
        assertThat(row.text).isEqualTo("final words")
        assertThat(row.rawTranscript).isEqualTo("final words")
        assertThat(row.language).isEqualTo("en")
        assertThat(row.status).isEqualTo(NoteStatus.PROCESSING.name)
        assertThat(row.audioFileName).isNull()
        assertThat(row.recordingFileName).isNull()
        assertThat(row.transcriptionProgressPercent).isNull()
        assertThat(row.updatedAtEpochMillis).isEqualTo(TestTime.CLOCK.millis())
        assertThat(graph.notes.recordingFileFor(id).exists()).isFalse()
        assertThat(transcriber.language).isEqualTo("en")
        assertThat(progress).containsExactly(50, 100).inOrder()
    }

    @Test
    fun `user edits made during transcription survive and multilingual models autodetect`() = scope.runTest {
        val id = voiceNote()
        placeModel("base")
        transcriber.afterPartial = { graph.notes.updateText(id, "user edit") }
        assertThat(runner.run(id) {}).isTrue()
        assertThat(graph.notes.getNote(id)!!.text).isEqualTo("user edit")
        assertThat(graph.notes.getNote(id)!!.rawTranscript).isEqualTo("final words")
        assertThat(transcriber.language).isNull()
    }

    @Test
    fun `deletion during transcription does not recreate the note`() = scope.runTest {
        val id = voiceNote()
        placeModel("tiny.en")
        transcriber.afterPartial = { graph.notes.deleteNote(id) }
        assertThat(runner.run(id) {}).isFalse()
        assertThat(graph.notes.getNote(id)).isNull()
    }

    private suspend fun voiceNote(hasRecording: Boolean = true): NoteId {
        val id = NoteId.random()
        graph.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))
        graph.notes.completeVoiceNote(id, 1_000, CategoryId.UNCATEGORIZED)
        val row = graph.database.noteDao().getNote(id.value)!!.note
        graph.database.noteDao().update(row.copy(shouldRetainAudio = false))
        if (hasRecording) graph.notes.recordingFileFor(id).writeBytes(WavHeader.build(0))
        return id
    }

    private suspend fun placeModel(id: String) {
        graph.settings.update { it.copy(speechModelId = id) }
        val model = ModelCatalog.speechModel(id).file
        RandomAccessFile(models.fileFor(model), "rw").use { it.setLength(model.sizeBytes) }
        transcriber.expectedModel = models.fileFor(model)
    }

    private class FakeSpeechTranscriber : SpeechTranscriber {
        var calls = 0
        var language: String? = null
        var expectedModel: File? = null
        var afterPartial: suspend () -> Unit = {}

        override suspend fun transcribe(
            audioFile: File,
            modelFile: File,
            language: String?,
            onPartialResult: suspend (String) -> Unit,
            onProgress: (Int) -> Unit,
        ): TranscriptionResult {
            calls++
            this.language = language
            assertThat(audioFile.exists()).isTrue()
            assertThat(modelFile).isEqualTo(expectedModel)
            onPartialResult("partial words")
            onProgress(50)
            afterPartial()
            onProgress(100)
            return TranscriptionResult("final words", "en")
        }
    }
}
