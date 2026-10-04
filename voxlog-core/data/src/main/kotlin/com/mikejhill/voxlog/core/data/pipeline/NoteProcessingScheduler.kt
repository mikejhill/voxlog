package com.mikejhill.voxlog.core.data.pipeline

import com.mikejhill.voxlog.core.model.NoteId

/** Queues background work for notes. Every call returns immediately; nothing runs on the caller's thread. */
interface NoteProcessingScheduler {
    /** Transcribe → post-process → embed → sync, for a just-finished voice recording. */
    fun scheduleAfterVoiceCapture(noteId: NoteId)

    /** Post-process → embed → sync, for a just-saved text note. */
    fun scheduleAfterTextCapture(noteId: NoteId)

    /** Re-embed → sync, after the user edits a note. */
    fun scheduleAfterEdit(noteId: NoteId)

    /** Re-run post-processing → embed → sync on demand. */
    fun scheduleReprocess(noteId: NoteId)

    /** Mirror all notes to the sync folder. */
    fun scheduleSync()
}
