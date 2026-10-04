package com.mikejhill.voxlog.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.feature.notes.detail.NoteDetailActions
import com.mikejhill.voxlog.feature.notes.detail.NoteDetailScreen
import com.mikejhill.voxlog.feature.notes.detail.NoteDetailViewModel
import com.mikejhill.voxlog.feature.notes.list.NotesListActions
import com.mikejhill.voxlog.feature.notes.list.NotesListScreen
import com.mikejhill.voxlog.feature.notes.list.NotesListViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Type-safe route of the notes home; [noteId] opens a note directly (deep links, search results). */
@Serializable
data class NotesRoute(val noteId: String? = null)

/** Host-provided navigation callbacks for the notes feature. */
data class NotesNavigationCallbacks(
    val onRecordVoice: (CategoryId?) -> Unit,
    val onWriteText: (CategoryId?) -> Unit,
    val onSearch: () -> Unit,
    val onSettings: () -> Unit,
    val onShare: (Note) -> Unit,
)

/** Adds the notes home (adaptive list-detail) to the host's navigation graph. */
fun NavGraphBuilder.notesScreen(callbacks: NotesNavigationCallbacks) {
    composable<NotesRoute> { entry ->
        val initialNoteId = entry.arguments?.getString("noteId")
        NotesHome(initialNoteId = initialNoteId, callbacks = callbacks)
    }
}

/**
 * The notes home. On phones it shows the list, then the detail full screen; on tablets and
 * unfolded foldables both panes sit side by side.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NotesHome(initialNoteId: String?, callbacks: NotesNavigationCallbacks) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialNoteId) {
        initialNoteId?.let { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it) }
    }
    BackHandler(navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }
    val selectedNoteId = navigator.currentDestination?.contentKey
    val isDetailBesideList = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                NotesListPane(
                    selectedNoteId = selectedNoteId,
                    callbacks = callbacks,
                    onNoteClick = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id.value) } },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val noteId = selectedNoteId ?: return@AnimatedPane
                NoteDetailPane(
                    noteId = noteId,
                    isBesideList = isDetailBesideList,
                    callbacks = callbacks,
                    onBack = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
    )
}

@Composable
private fun NotesListPane(
    selectedNoteId: String?,
    callbacks: NotesNavigationCallbacks,
    onNoteClick: (NoteId) -> Unit,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotesListScreen(
        state = state,
        selectedNoteId = selectedNoteId?.let(::NoteId),
        actions = NotesListActions(
            onNoteClick = onNoteClick,
            onRecordVoice = callbacks.onRecordVoice,
            onWriteText = callbacks.onWriteText,
            onSearch = callbacks.onSearch,
            onSettings = callbacks.onSettings,
            onSelectCategory = viewModel::selectCategory,
            onToggleLabel = viewModel::toggleLabel,
            onLabelMatchModeChange = viewModel::setLabelMatchMode,
            onClearFilters = viewModel::clearFilters,
            onRequestDelete = viewModel::requestDeletion,
            onUndoDelete = viewModel::undoDeletion,
            onCommitDelete = viewModel::commitDeletion,
        ),
    )
}

@Composable
private fun NoteDetailPane(
    noteId: String,
    isBesideList: Boolean,
    callbacks: NotesNavigationCallbacks,
    onBack: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel<NoteDetailViewModel, NoteDetailViewModel.Factory>(key = noteId) { factory ->
        factory.create(noteId)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NoteDetailScreen(
        state = state,
        showBackButton = !isBesideList,
        actions = NoteDetailActions(
            onBack = onBack,
            onTitleChange = viewModel::onTitleChanged,
            onTextChange = viewModel::onTextChanged,
            onCategoryChange = viewModel::moveToCategory,
            onToggleLabel = viewModel::toggleLabel,
            onAddLabel = viewModel::addLabel,
            onDeleteAudio = viewModel::deleteAudio,
            onDeleteNote = {
                viewModel.deleteNote()
                onBack()
            },
            onReprocess = viewModel::reprocess,
            onShare = callbacks.onShare,
        ),
    )
}
