package com.mikejhill.voxlog.core.data.pipeline

import com.mikejhill.voxlog.core.data.audio.AacTranscoder
import com.mikejhill.voxlog.core.data.audio.WavHeader
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.engine.whisper.SpeechTranscriber
import java.io.File
import java.time.Clock
import javax.inject.Inject

/**
 * Turns a finished recording into text: transcribes with Whisper (persisting partial text as it
 * goes), then keeps compressed audio or deletes it according to the capture-time category setting.
 */
class TranscriptionRunner @Inject constructor(
    private val noteDao: NoteDao,
    private val transcriber: SpeechTranscriber,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val audioFileStore: AudioFileStore,
    private val clock: Clock,
) {
    /** Runs transcription for [noteId]. Returns false when there was nothing to transcribe. */
    suspend fun run(noteId: NoteId, onProgress: (Int) -> Unit): Boolean {
        val note = noteDao.getNote(noteId.value)?.note ?: return false
        val recordingName = note.recordingFileName ?: return false
        val recording = audioFileStore.recordingFile(recordingName)
        if (!recording.exists()) {
            noteDao.update(note.copy(status = NoteStatus.FAILED.name, processingLog = "Recording file missing"))
            return false
        }
        WavHeader.repair(recording)
        val speechModel = ModelCatalog.speechModel(settingsRepository.current().speechModelId)
        val modelFile = modelRepository.ensureDownloaded(speechModel.file)
        val result = transcriber.transcribe(
            audioFile = recording,
            modelFile = modelFile,
            language = if (speechModel.isEnglishOnly) "en" else null,
            onPartialResult = { partial -> savePartial(noteId, partial) },
            onProgress = { percent -> onProgress(percent) },
        )
        val retainedAudio = if (note.shouldRetainAudio) compress(recording, noteId) else null
        val latest = noteDao.getNote(noteId.value)?.note ?: return false
        noteDao.update(
            latest.copy(
                text = if (latest.text.isBlank() || latest.text == latest.rawTranscript) result.text else latest.text,
                rawTranscript = result.text,
                language = result.language,
                audioFileName = retainedAudio,
                recordingFileName = null,
                status = NoteStatus.PROCESSING.name,
                transcriptionProgressPercent = null,
                updatedAtEpochMillis = clock.millis(),
            ),
        )
        recording.delete()
        return true
    }

    private suspend fun savePartial(noteId: NoteId, partialText: String) {
        val latest = noteDao.getNote(noteId.value)?.note ?: return
        noteDao.update(latest.copy(text = partialText, rawTranscript = partialText))
    }

    private fun compress(recording: File, noteId: NoteId): String {
        val fileName = "${noteId.value}.m4a"
        AacTranscoder.transcode(recording, audioFileStore.audioFile(fileName))
        return fileName
    }
}
