package com.mikejhill.voxlog.feature.notes.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.mikejhill.voxlog.core.designsystem.component.CategoryIcons
import com.mikejhill.voxlog.core.designsystem.component.EmptyState
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.LabelMatchMode
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.feature.notes.R
import com.mikejhill.voxlog.feature.notes.sample.SampleNotes
import kotlinx.coroutines.launch

/** Callbacks from the notes list to the host. */
data class NotesListActions(
    val onNoteClick: (NoteId) -> Unit,
    val onRecordVoice: (CategoryId?) -> Unit,
    val onWriteText: (CategoryId?) -> Unit,
    val onSearch: () -> Unit,
    val onSettings: () -> Unit,
    val onSelectCategory: (CategoryId?) -> Unit,
    val onToggleLabel: (LabelId) -> Unit,
    val onLabelMatchModeChange: (LabelMatchMode) -> Unit,
    val onClearFilters: () -> Unit,
    val onRequestDelete: (NoteId) -> Unit,
    val onUndoDelete: (NoteId) -> Unit,
    val onCommitDelete: (NoteId) -> Unit,
)

/**
 * Home screen: filter chips for category and labels, the note list, and capture-first FABs
 * (a large voice button and a smaller text button). New captures go into the selected category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(state: NotesListUiState, selectedNoteId: NoteId?, actions: NotesListActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.notes_deleted)
    val undoLabel = stringResource(R.string.notes_undo)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val activeCategory = (state as? NotesListUiState.Content)?.filter?.categoryId
    val onDelete: (NoteId) -> Unit = { noteId ->
        actions.onRequestDelete(noteId)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                deletedMessage,
                undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete(noteId) else actions.onCommitDelete(noteId)
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                actions = {
                    IconButton(onClick = actions.onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.notes_search)) }
                    IconButton(onClick = actions.onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.notes_settings)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CaptureButtons(onRecordVoice = { actions.onRecordVoice(activeCategory) }, onWriteText = { actions.onWriteText(activeCategory) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            NotesListUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is NotesListUiState.Content -> Column(Modifier.padding(padding)) {
                FilterBar(state, actions)
                NotesContent(state, selectedNoteId, actions, onDelete)
            }
        }
    }
}

@Composable
private fun CaptureButtons(onRecordVoice: () -> Unit, onWriteText: () -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        SmallFloatingActionButton(onClick = onWriteText, modifier = Modifier.testTag("writeTextFab")) {
            Icon(Icons.Outlined.EditNote, stringResource(R.string.notes_write_text))
        }
        ExtendedFloatingActionButton(
            onClick = onRecordVoice,
            icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
            text = { Text(stringResource(R.string.notes_record)) },
            modifier = Modifier.testTag("recordVoiceFab"),
        )
    }
}

@Composable
private fun FilterBar(state: NotesListUiState.Content, actions: NotesListActions) {
    Column(Modifier.padding(vertical = Spacing.small)) {
        CategoryFilterRow(state, actions.onSelectCategory)
        if (state.labels.isNotEmpty()) LabelFilterRow(state, actions)
    }
}

@Composable
private fun CategoryFilterRow(state: NotesListUiState.Content, onSelectCategory: (CategoryId?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        FilterChip(
            selected = state.filter.categoryId == null,
            onClick = { onSelectCategory(null) },
            label = { Text(stringResource(R.string.notes_all_categories)) },
        )
        state.categories.forEach { category ->
            FilterChip(
                selected = state.filter.categoryId == category.id,
                onClick = { onSelectCategory(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(CategoryIcons.forName(category.iconName), contentDescription = null, tint = Color(category.colorArgb))
                },
            )
        }
    }
}

@Composable
private fun LabelFilterRow(state: NotesListUiState.Content, actions: NotesListActions) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.filter.labelIds.size > 1) LabelMatchModeToggle(state.filter.labelMatchMode, actions.onLabelMatchModeChange)
        state.labels.forEach { label ->
            FilterChip(
                selected = label.id in state.filter.labelIds,
                onClick = { actions.onToggleLabel(label.id) },
                label = { Text("#${label.name}") },
            )
        }
    }
}

@Composable
private fun LabelMatchModeToggle(mode: LabelMatchMode, onChange: (LabelMatchMode) -> Unit) {
    val isAll = mode == LabelMatchMode.ALL
    TextButton(onClick = { onChange(if (isAll) LabelMatchMode.ANY else LabelMatchMode.ALL) }) {
        Text(stringResource(if (isAll) R.string.notes_match_all else R.string.notes_match_any))
    }
}

@Composable
private fun NotesContent(state: NotesListUiState.Content, selectedNoteId: NoteId?, actions: NotesListActions, onDelete: (NoteId) -> Unit) {
    if (state.notes.isEmpty()) {
        if (state.isFiltered) {
            EmptyState(
                icon = Icons.Outlined.FilterAltOff,
                title = stringResource(R.string.notes_no_matches_title),
                message = stringResource(R.string.notes_no_matches_message),
                action = { TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.notes_clear_filters)) } },
            )
        } else {
            EmptyState(
                icon = Icons.Outlined.AutoStories,
                title = stringResource(R.string.notes_empty_title),
                message = stringResource(R.string.notes_empty_message),
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.large, end = Spacing.large, bottom = LIST_BOTTOM_PADDING),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        modifier = Modifier.testTag("notesList"),
    ) {
        items(state.notes, key = { it.id.value }) { note ->
            SwipeToDelete(onDelete = { onDelete(note.id) }) {
                NoteCard(
                    note = note,
                    category = state.categoriesById[note.categoryId],
                    labels = note.labelIds.mapNotNull { state.labelsById[it] }.sortedBy { it.name },
                    isSelected = note.id == selectedNoteId,
                    onClick = { actions.onNoteClick(note.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private val LIST_BOTTOM_PADDING = 160.dp

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(Modifier.fillMaxSize().padding(horizontal = Spacing.extraLarge), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.notes_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) { content() }
}

@PreviewLightDark
@Composable
private fun NotesListPreview() {
    VoxLogTheme(isDynamicColorEnabled = false) {
        NotesListScreen(state = SampleNotes.contentState, selectedNoteId = null, actions = SampleNotes.noOpActions)
    }
}
