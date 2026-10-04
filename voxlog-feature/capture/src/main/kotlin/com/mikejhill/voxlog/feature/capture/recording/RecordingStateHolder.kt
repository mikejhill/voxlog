package com.mikejhill.voxlog.feature.capture.recording

import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable state of the single active voice capture. */
sealed interface RecordingState {
    /** Nothing is being recorded. */
    data object Idle : RecordingState

    /**
     * The microphone is live.
     *
     * @property startedAtElapsedMillis `SystemClock.elapsedRealtime()` at start, for the timer.
     * @property remainingStorageMinutes estimated minutes of storage left; the only thing that can end a recording.
     */
    data class Recording(
        val noteId: NoteId,
        val categoryId: CategoryId,
        val startedAtElapsedMillis: Long,
        val inputLevel: Float,
        val remainingStorageMinutes: Long,
    ) : RecordingState

    /** The note has been saved; transcription continues in the background. */
    data class Saved(val noteId: NoteId) : RecordingState

    /** The user discarded the recording; nothing was saved. */
    data object Discarded : RecordingState

    /** Recording could not start (for example, the microphone is held by another app). */
    data class Failed(val reason: String) : RecordingState
}

/** Shares [RecordingState] between [RecordingService] and the recording screen. */
@Singleton
class RecordingStateHolder @Inject constructor() {
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)

    /** Current state. */
    val state: StateFlow<RecordingState> = mutableState.asStateFlow()

    /** Replaces the state. Only [RecordingService] writes. */
    internal fun update(transform: (RecordingState) -> RecordingState) {
        mutableState.value = transform(mutableState.value)
    }
}
