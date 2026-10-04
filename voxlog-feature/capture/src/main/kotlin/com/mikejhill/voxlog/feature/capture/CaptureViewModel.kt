package com.mikejhill.voxlog.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.repository.NoteRepository
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.DefaultNoteTitle
import com.mikejhill.voxlog.feature.capture.recording.RecordingState
import com.mikejhill.voxlog.feature.capture.recording.RecordingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state of the text composer. */
data class ComposerUiState(
    val title: String = "",
    val text: String = "",
    val categoryId: CategoryId = CategoryId.UNCATEGORIZED,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Text notes need a body; the title falls back to the timestamp. */
    val canSave: Boolean
        get() = text.isNotBlank() && !isSaving && !isSaved
}

/** Drives both capture screens: the live recording and the text composer. */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val recordingStateHolder: RecordingStateHolder,
    categoryRepository: CategoryRepository,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val composer = MutableStateFlow(ComposerUiState())

    /** Live recording state from the foreground service. */
    val recordingState: StateFlow<RecordingState> = recordingStateHolder.state

    /** Categories for the picker. */
    val categories: StateFlow<List<Category>> = categoryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Composer state. */
    val composerState: StateFlow<ComposerUiState> = composer.asStateFlow()

    /** Resolves the category for a new capture: the explicit one, else the configured default. */
    suspend fun resolveCategory(requested: String?): CategoryId =
        requested?.let(::CategoryId) ?: settingsRepository.current().defaultCategory

    /** Clears a finished or failed previous capture so its result screen is not shown again. */
    fun resetFinishedRecording() {
        recordingStateHolder.update { if (it is RecordingState.Recording) it else RecordingState.Idle }
    }

    /** Prepares the composer with a timestamp title the user can overwrite. */
    fun startComposer(categoryId: CategoryId) {
        composer.value = ComposerUiState(title = DefaultNoteTitle.forCapture(clock.instant(), clock.zone), categoryId = categoryId)
    }

    /** Updates the composer title. */
    fun onTitleChanged(title: String) = composer.update { it.copy(title = title) }

    /** Updates the composer body. */
    fun onTextChanged(text: String) = composer.update { it.copy(text = text) }

    /** Changes the composer category. */
    fun onComposerCategoryChanged(categoryId: CategoryId) = composer.update { it.copy(categoryId = categoryId) }

    /** Saves the typed note; [shortcutId] records which launcher shortcut created it. */
    fun saveTextNote(shortcutId: String?) {
        val state = composer.value
        if (!state.canSave) return
        composer.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val defaultTitle = DefaultNoteTitle.forCapture(clock.instant(), clock.zone)
            noteRepository.createTextNote(
                TextNoteDraft(
                    categoryId = state.categoryId,
                    // An untouched timestamp title stays a DEFAULT title so auto-naming may still improve it.
                    title = state.title.takeUnless {
                        it.isBlank() ||
                            it.take(TIMESTAMP_PREFIX_LENGTH) == defaultTitle.take(TIMESTAMP_PREFIX_LENGTH)
                    },
                    text = state.text,
                    captureShortcutId = shortcutId,
                ),
            )
            composer.update { it.copy(isSaving = false, isSaved = true) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** `yyyy-MM-ddTHH:mm` — enough to recognize an unedited default title. */
        const val TIMESTAMP_PREFIX_LENGTH = 16
    }
}
