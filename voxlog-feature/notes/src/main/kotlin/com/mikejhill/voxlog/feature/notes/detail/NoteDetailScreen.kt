package com.mikejhill.voxlog.feature.notes.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.mikejhill.voxlog.core.designsystem.component.CategoryChip
import com.mikejhill.voxlog.core.designsystem.component.CategoryPickerSheet
import com.mikejhill.voxlog.core.designsystem.theme.LocalClock
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.feature.notes.R
import com.mikejhill.voxlog.feature.notes.format.NoteFormatting
import com.mikejhill.voxlog.feature.notes.sample.SampleNotes

/** Callbacks from the detail screen. */
data class NoteDetailActions(
    val onBack: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onTextChange: (String) -> Unit,
    val onCategoryChange: (CategoryId) -> Unit,
    val onToggleLabel: (LabelId) -> Unit,
    val onAddLabel: (String) -> Unit,
    val onDeleteAudio: () -> Unit,
    val onDeleteNote: () -> Unit,
    val onReprocess: () -> Unit,
    val onShare: (Note) -> Unit,
)

/** Shows and edits one note. Every field saves automatically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(state: NoteDetailUiState, actions: NoteDetailActions, modifier: Modifier = Modifier, showBackButton: Boolean = true) {
    var pendingConfirmation by remember { mutableStateOf<Confirmation?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.notes_back))
                        }
                    }
                },
                actions = {
                    if (state is NoteDetailUiState.Content) {
                        OverflowMenu(state, actions, onConfirm = { pendingConfirmation = it })
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NoteDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                NoteDetailUiState.NotFound -> Text(stringResource(R.string.notes_not_found), Modifier.align(Alignment.Center))
                is NoteDetailUiState.Content -> DetailContent(state, actions)
            }
        }
    }
    pendingConfirmation?.let { confirmation ->
        ConfirmDialog(
            confirmation = confirmation,
            onConfirm = {
                pendingConfirmation = null
                when (confirmation) {
                    Confirmation.DELETE_AUDIO -> actions.onDeleteAudio()
                    Confirmation.DELETE_NOTE -> actions.onDeleteNote()
                }
            },
            onDismiss = { pendingConfirmation = null },
        )
    }
}

/** Irreversible actions that need explicit confirmation. */
private enum class Confirmation { DELETE_AUDIO, DELETE_NOTE }

@Composable
private fun OverflowMenu(state: NoteDetailUiState.Content, actions: NoteDetailActions, onConfirm: (Confirmation) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { isExpanded = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.notes_more)) }
    DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.notes_share)) }, onClick = {
            isExpanded = false
            actions.onShare(state.note)
        })
        DropdownMenuItem(text = { Text(stringResource(R.string.notes_reprocess)) }, onClick = {
            isExpanded = false
            actions.onReprocess()
        })
        if (state.audioFile != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.notes_delete_audio)) }, onClick = {
                isExpanded = false
                onConfirm(Confirmation.DELETE_AUDIO)
            })
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.notes_delete)) }, onClick = {
            isExpanded = false
            onConfirm(Confirmation.DELETE_NOTE)
        })
    }
}

@Composable
private fun DetailContent(state: NoteDetailUiState.Content, actions: NoteDetailActions) {
    val note = state.note
    var title by rememberSaveable(note.id.value) { mutableStateOf(note.title) }
    var text by rememberSaveable(note.id.value) { mutableStateOf(note.text) }
    var isTextDirty by rememberSaveable(note.id.value) { mutableStateOf(false) }
    var isTitleDirty by rememberSaveable(note.id.value) { mutableStateOf(false) }
    // Follow background updates (live transcription, AI cleanup, auto-naming) until the user edits that field.
    LaunchedEffect(note.text) { if (!isTextDirty) text = note.text }
    LaunchedEffect(note.title) { if (!isTitleDirty) title = note.title }
    var isCategoryPickerOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        state.category?.let { CategoryChip(it.name, it.colorArgb, it.iconName, onClick = { isCategoryPickerOpen = true }) }
        TextField(
            value = title,
            onValueChange = {
                title = it
                isTitleDirty = true
                actions.onTitleChange(it)
            },
            textStyle = MaterialTheme.typography.headlineSmall,
            colors = transparentColors(),
            modifier = Modifier.fillMaxWidth().testTag("detailTitle"),
        )
        if (note.status == NoteStatus.TRANSCRIBING) {
            Text(
                stringResource(R.string.notes_transcribing, note.transcriptionProgressPercent ?: 0),
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(progress = { (note.transcriptionProgressPercent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
        }
        state.audioFile?.let { AudioPlayer(it) }
        TextField(
            value = text,
            onValueChange = {
                text = it
                isTextDirty = true
                actions.onTextChange(it)
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(stringResource(R.string.notes_body_placeholder)) },
            colors = transparentColors(),
            modifier = Modifier.fillMaxWidth().testTag("detailBody"),
        )
        HorizontalDivider()
        LabelsSection(state, actions)
        HorizontalDivider()
        MetadataSection(note)
    }
    if (isCategoryPickerOpen) {
        CategoryPickerSheet(
            categories = state.categories,
            selectedId = note.categoryId,
            onSelect = {
                actions.onCategoryChange(it)
                isCategoryPickerOpen = false
            },
            onDismiss = { isCategoryPickerOpen = false },
        )
    }
}

@Composable
private fun LabelsSection(state: NoteDetailUiState.Content, actions: NoteDetailActions) {
    var isAdding by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(R.string.notes_labels), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            state.labels.forEach { label ->
                FilterChip(
                    selected = label.id in state.note.labelIds,
                    onClick = { actions.onToggleLabel(label.id) },
                    label = { Text("#${label.name}") },
                )
            }
            AssistChip(
                onClick = { isAdding = true },
                label = { Text(stringResource(R.string.notes_add_label)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
            )
        }
    }
    if (isAdding) {
        AlertDialog(
            onDismissRequest = { isAdding = false },
            title = { Text(stringResource(R.string.notes_add_label)) },
            text = { OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onAddLabel(newLabel)
                    newLabel = ""
                    isAdding = false
                }) { Text(stringResource(R.string.notes_add)) }
            },
            dismissButton = { TextButton(onClick = { isAdding = false }) { Text(stringResource(R.string.notes_cancel)) } },
        )
    }
}

@Composable
private fun MetadataSection(note: Note) {
    val zone = LocalClock.current.zone
    Column(Modifier.padding(bottom = Spacing.huge), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(stringResource(R.string.notes_details), style = MaterialTheme.typography.titleSmall)
        MetadataLine(R.string.notes_meta_created, NoteFormatting.fullDateTime(note.createdAt, zone))
        MetadataLine(R.string.notes_meta_updated, NoteFormatting.fullDateTime(note.updatedAt, zone))
        MetadataLine(
            R.string.notes_meta_source,
            stringResource(
                if (note.captureMethod ==
                    CaptureMethod.VOICE_RECORDING
                ) {
                    R.string.notes_voice_note
                } else {
                    R.string.notes_text_note
                },
            ),
        )
        note.durationMillis?.let { MetadataLine(R.string.notes_meta_length, NoteFormatting.duration(it)) }
        MetadataLine(R.string.notes_meta_words, note.wordCount.toString())
        note.location?.let { location ->
            MetadataLine(R.string.notes_meta_location, location.placeName ?: "%.5f, %.5f".format(location.latitude, location.longitude))
        }
        note.language?.let { MetadataLine(R.string.notes_meta_language, it) }
        note.processingLog?.let { MetadataLine(R.string.notes_meta_processing, it) }
    }
}

@Composable
private fun MetadataLine(labelRes: Int, value: String) {
    Text(
        "${stringResource(labelRes)}: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConfirmDialog(confirmation: Confirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (titleRes, messageRes) = when (confirmation) {
        Confirmation.DELETE_AUDIO -> R.string.notes_delete_audio to R.string.notes_delete_audio_message
        Confirmation.DELETE_NOTE -> R.string.notes_delete to R.string.notes_delete_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.notes_delete_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_cancel)) } },
    )
}

@Composable
private fun transparentColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@PreviewLightDark
@Composable
private fun NoteDetailPreview() {
    VoxLogTheme(isDynamicColorEnabled = false) {
        NoteDetailScreen(
            state = NoteDetailUiState.Content(SampleNotes.notes.first(), SampleNotes.categories, SampleNotes.labels, audioFile = null),
            actions = NoteDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
