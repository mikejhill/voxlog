package com.mikejhill.voxlog.feature.notes.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.repository.LabelRepository
import com.mikejhill.voxlog.core.data.repository.NoteRepository
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.LabelMatchMode
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.NoteId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the notes list renders. */
sealed interface NotesListUiState {
    /** First load in progress. */
    data object Loading : NotesListUiState

    /** Loaded; [notes] may be empty. */
    data class Content(val notes: List<Note>, val categories: List<Category>, val labels: List<Label>, val filter: NoteFilter) :
        NotesListUiState {
        /** Lookup for rendering category badges. */
        val categoriesById: Map<CategoryId, Category> = categories.associateBy { it.id }

        /** Lookup for rendering label tags. */
        val labelsById: Map<LabelId, Label> = labels.associateBy { it.id }

        /** Whether any filter is active, to choose between "no notes yet" and "no matches". */
        val isFiltered: Boolean
            get() = filter != NoteFilter()
    }
}

/** Home screen state: the filtered note list plus category and label filters. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    categoryRepository: CategoryRepository,
    labelRepository: LabelRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(NoteFilter())
    private val pendingDeletions = MutableStateFlow<Set<NoteId>>(emptySet())

    /** Screen state. */
    val uiState: StateFlow<NotesListUiState> = combine(
        filter.flatMapLatest { noteRepository.observeNotes(it) },
        categoryRepository.observeCategories(),
        labelRepository.observeLabels(),
        filter,
        pendingDeletions,
    ) { notes, categories, labels, activeFilter, hidden ->
        NotesListUiState.Content(notes.filterNot { it.id in hidden }, categories, labels, activeFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NotesListUiState.Loading)

    /** Shows one category, or all when [categoryId] is null. */
    fun selectCategory(categoryId: CategoryId?) = filter.update { it.copy(categoryId = categoryId) }

    /** Toggles a label in the label filter. */
    fun toggleLabel(labelId: LabelId) = filter.update {
        it.copy(labelIds = if (labelId in it.labelIds) it.labelIds - labelId else it.labelIds + labelId)
    }

    /** Switches between matching any or all selected labels. */
    fun setLabelMatchMode(mode: LabelMatchMode) = filter.update { it.copy(labelMatchMode = mode) }

    /** Clears every filter. */
    fun clearFilters() = filter.update { NoteFilter() }

    /**
     * Hides a note immediately and deletes it only when [commitDeletion] runs (after the undo
     * snackbar is dismissed). If the app dies first, the note simply survives: deletion is never lossy by accident.
     */
    fun requestDeletion(noteId: NoteId) = pendingDeletions.update { it + noteId }

    /** Restores a note hidden by [requestDeletion]. */
    fun undoDeletion(noteId: NoteId) = pendingDeletions.update { it - noteId }

    /** Permanently deletes a note hidden by [requestDeletion]. */
    fun commitDeletion(noteId: NoteId) {
        if (noteId !in pendingDeletions.value) return
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
            pendingDeletions.update { it - noteId }
        }
    }

    /** Moves a note to another category. */
    fun moveNote(noteId: NoteId, categoryId: CategoryId) {
        viewModelScope.launch { noteRepository.moveToCategory(noteId, categoryId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
