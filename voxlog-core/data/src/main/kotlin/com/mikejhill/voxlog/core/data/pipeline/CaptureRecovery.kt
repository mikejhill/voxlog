package com.mikejhill.voxlog.core.data.pipeline

import com.mikejhill.voxlog.core.data.audio.WavHeader
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.engine.whisper.WhisperAudioFormat
import javax.inject.Inject

/**
 * Runs once at process start. Any note still RECORDING belongs to a recording that was cut short by
 * a crash or a killed process: its WAV is repaired and transcription is queued, so nothing is lost.
 */
class CaptureRecovery @Inject constructor(
    private val noteDao: NoteDao,
    private val audioFileStore: AudioFileStore,
    private val scheduler: NoteProcessingScheduler,
) {
    /** Finalizes orphaned recordings. Returns the number of notes recovered. */
    suspend fun recoverInterruptedRecordings(): Int {
        val orphans = noteDao.getNotesWithStatus(listOf(NoteStatus.RECORDING.name))
        orphans.forEach { note ->
            val recording = note.recordingFileName?.let(audioFileStore::recordingFile)
            if (recording == null || !recording.exists() || recording.length() <= WavHeader.SIZE_BYTES) {
                noteDao.update(
                    note.copy(status = NoteStatus.FAILED.name, processingLog = "Recording was interrupted before any audio was saved"),
                )
                return@forEach
            }
            WavHeader.repair(recording)
            val durationMillis = (recording.length() - WavHeader.SIZE_BYTES) * MILLIS_PER_SECOND / WhisperAudioFormat.BYTES_PER_SECOND
            noteDao.update(
                note.copy(
                    durationMillis = durationMillis,
                    status = NoteStatus.TRANSCRIBING.name,
                    processingLog = "Recovered after the recording was interrupted",
                ),
            )
            scheduler.scheduleAfterVoiceCapture(NoteId(note.id))
        }
        return orphans.size
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
