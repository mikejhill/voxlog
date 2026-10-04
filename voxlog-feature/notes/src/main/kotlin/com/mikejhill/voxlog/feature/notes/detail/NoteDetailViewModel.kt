package com.mikejhill.voxlog.feature.notes.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikejhill.voxlog.core.data.di.ApplicationScope
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.repository.LabelRepository
import com.mikejhill.voxlog.core.data.repository.NoteRepository
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteId
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Detail screen state. */
sealed interface NoteDetailUiState {
    /** Loading. */
    data object Loading : NoteDetailUiState

    /** The note was deleted or never existed. */
    data object NotFound : NoteDetailUiState

    /** Loaded. */
    data class Content(val note: Note, val categories: List<Category>, val labels: List<Label>, val audioFile: File?) : NoteDetailUiState {
        /** The note's category, if it still exists. */
        val category: Category?
            get() = categories.firstOrNull { it.id == note.categoryId }

        /** The note's labels in display order. */
        val noteLabels: List<Label>
            get() = labels.filter { it.id in note.labelIds }.sortedBy { it.name }
    }
}

/**
 * Edits one note. Title and text edits are saved automatically after a short pause. Saves run in
 * the application scope, so a pending edit still lands if the user navigates away mid-debounce.
 */
@HiltViewModel(assistedFactory = NoteDetailViewModel.Factory::class)
class NoteDetailViewModel @AssistedInject constructor(
    @Assisted noteIdValue: String,
    private val noteRepository: NoteRepository,
    categoryRepository: CategoryRepository,
    private val labelRepository: LabelRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val noteId = NoteId(noteIdValue)
    private var titleSaveJob: Job? = null
    private var textSaveJob: Job? = null

    /** Screen state. */
    val uiState: StateFlow<NoteDetailUiState> = combine(
        noteRepository.observeNote(noteId),
        categoryRepository.observeCategories(),
        labelRepository.observeLabels(),
    ) { note, categories, labels ->
        if (note ==
            null
        ) {
            NoteDetailUiState.NotFound
        } else {
            NoteDetailUiState.Content(note, categories, labels, noteRepository.audioFileFor(note))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NoteDetailUiState.Loading)

    /** Debounced title save. */
    fun onTitleChanged(title: String) {
        titleSaveJob?.cancel()
        titleSaveJob = applicationScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            noteRepository.updateTitle(noteId, title)
        }
    }

    /** Debounced text save. */
    fun onTextChanged(text: String) {
        textSaveJob?.cancel()
        textSaveJob = applicationScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            noteRepository.updateText(noteId, text)
        }
    }

    /** Moves the note to another category. */
    fun moveToCategory(categoryId: CategoryId) = launch { noteRepository.moveToCategory(noteId, categoryId) }

    /** Adds or removes a label. */
    fun toggleLabel(labelId: LabelId) = launch {
        val note = noteRepository.getNote(noteId) ?: return@launch
        val updated = if (labelId in note.labelIds) note.labelIds - labelId else note.labelIds + labelId
        noteRepository.setLabels(noteId, updated)
    }

    /** Creates a label by name (or reuses an existing one) and attaches it. */
    fun addLabel(name: String) = launch {
        if (name.isBlank()) return@launch
        val label = labelRepository.getOrCreate(name)
        val note = noteRepository.getNote(noteId) ?: return@launch
        noteRepository.setLabels(noteId, note.labelIds + label.id)
    }

    /** Permanently deletes retained audio; the text stays. */
    fun deleteAudio() = launch { noteRepository.deleteAudio(noteId) }

    /** Permanently deletes the note. */
    fun deleteNote() = launch { noteRepository.deleteNote(noteId) }

    /** Re-runs AI processing and hooks. */
    fun reprocess() = launch { noteRepository.reprocess(noteId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /** Assisted factory so the screen can pass the note id. */
    @AssistedFactory
    interface Factory {
        /** Creates a view model for the note whose id is [noteId]. Takes a String because Dagger cannot inject value classes. */
        fun create(noteId: String): NoteDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SAVE_DEBOUNCE_MILLIS = 400L
    }
}
