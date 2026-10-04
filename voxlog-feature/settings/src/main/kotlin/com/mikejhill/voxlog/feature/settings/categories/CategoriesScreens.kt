package com.mikejhill.voxlog.feature.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikejhill.voxlog.core.designsystem.component.CategoryColorPalette
import com.mikejhill.voxlog.core.designsystem.component.CategoryIcons
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.CategorySettings
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.feature.settings.R
import com.mikejhill.voxlog.feature.settings.ui.SectionHeader
import com.mikejhill.voxlog.feature.settings.ui.SwitchRow
import com.mikejhill.voxlog.feature.settings.ui.TextEditDialog

/** Lists categories; the system category is marked and cannot be deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onCreate: (String) -> Unit,
    onOpen: (CategoryId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCreating by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                isCreating = true
            }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.settings_new_category)) })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(state.categories, key = { it.id.value }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = {
                        Text(
                            if (category.settings.shouldSaveAudio) {
                                stringResource(
                                    R.string.settings_keeps_audio,
                                )
                            } else {
                                stringResource(R.string.settings_text_only)
                            },
                        )
                    },
                    leadingContent = { Icon(CategoryIcons.forName(category.iconName), null, tint = Color(category.colorArgb)) },
                    trailingContent = {
                        if (category.isSystem) Icon(Icons.Outlined.Lock, stringResource(R.string.settings_system_category))
                    },
                    modifier = Modifier.clickable { onOpen(category.id) },
                )
            }
        }
    }
    if (isCreating) {
        TextEditDialog(
            title = stringResource(R.string.settings_new_category),
            initialValue = "",
            onConfirm = {
                onCreate(it)
                isCreating = false
            },
            onDismiss = { isCreating = false },
        )
    }
}

/** Edits one category's appearance, capture settings, default labels and shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    category: Category,
    labels: List<Label>,
    onSave: (Category) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRenaming by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(category.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back)) }
                },
                actions = {
                    if (!category.isSystem) {
                        IconButton(onClick = { isRenaming = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.settings_rename)) }
                        IconButton(onClick = {
                            isConfirmingDelete = true
                        }) { Icon(Icons.Outlined.Delete, stringResource(R.string.settings_delete_category)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (category.isSystem) SystemCategoryNotice()
            SectionHeader(stringResource(R.string.settings_appearance))
            ColorPicker(category.colorArgb) { onSave(category.copy(colorArgb = it)) }
            IconPicker(category.iconName, category.colorArgb) { onSave(category.copy(iconName = it)) }
            CaptureSettingsSection(category.settings) { onSave(category.copy(settings = it)) }
            DefaultLabelsSection(category.settings, labels) { onSave(category.copy(settings = it)) }
            ShortcutsSection(category)
            Box(Modifier.size(Spacing.huge))
        }
    }
    if (isRenaming) {
        TextEditDialog(stringResource(R.string.settings_rename_category), category.name, onConfirm = {
            if (it.isNotBlank()) onSave(category.copy(name = it.trim()))
            isRenaming = false
        }, onDismiss = { isRenaming = false })
    }
    if (isConfirmingDelete) {
        DeleteCategoryDialog(category.name, onConfirm = onDelete, onDismiss = { isConfirmingDelete = false })
    }
}

@Composable
private fun SystemCategoryNotice() {
    Text(
        stringResource(R.string.settings_this_built_in_category_can_t_be_renamed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Spacing.large),
    )
}

@Composable
private fun CaptureSettingsSection(settings: CategorySettings, onChange: (CategorySettings) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_when_a_note_is_captured_here))
        SwitchRow(
            stringResource(R.string.settings_save_audio),
            settings.shouldSaveAudio,
            { onChange(settings.copy(shouldSaveAudio = it)) },
            summary = stringResource(R.string.settings_off_keeps_only_the_transcript_applies_to),
        )
        SwitchRow(stringResource(R.string.settings_clean_up_transcript_with_ai), settings.shouldAutoCleanup, {
            onChange(settings.copy(shouldAutoCleanup = it))
        })
        SwitchRow(stringResource(R.string.settings_auto_name_with_ai), settings.shouldAutoName, {
            onChange(settings.copy(shouldAutoName = it))
        })
        SwitchRow(stringResource(R.string.settings_auto_label_with_ai), settings.shouldAutoLabel, {
            onChange(settings.copy(shouldAutoLabel = it))
        })
    }
}

@Composable
private fun DefaultLabelsSection(settings: CategorySettings, labels: List<Label>, onChange: (CategorySettings) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.settings_default_labels))
        FlowRow(Modifier.padding(horizontal = Spacing.large), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            if (labels.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_labels_yet_add_labels_from_a_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            labels.forEach { label ->
                val isSelected = label.id in settings.defaultLabelIds
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) settings.defaultLabelIds - label.id else settings.defaultLabelIds + label.id
                        onChange(settings.copy(defaultLabelIds = updated))
                    },
                    label = { Text("#${label.name}") },
                )
            }
        }
    }
}

@Composable
private fun ShortcutsSection(category: Category) {
    val context = LocalContext.current
    Column {
        SectionHeader(stringResource(R.string.settings_home_screen_shortcuts))
        Column(Modifier.padding(horizontal = Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            if (ShortcutPinner.isSupported(context)) {
                OutlinedButton(onClick = {
                    ShortcutPinner.requestPin(context, category, ShortcutKind.VOICE)
                }) { Text(stringResource(R.string.settings_add_voice_shortcut)) }
                OutlinedButton(onClick = {
                    ShortcutPinner.requestPin(context, category, ShortcutKind.TEXT)
                }) { Text(stringResource(R.string.settings_add_text_shortcut)) }
            } else {
                Text(stringResource(R.string.settings_your_launcher_doesn_t_support_pinned_sho), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DeleteCategoryDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_named, name)) },
        text = { Text(stringResource(R.string.settings_notes_in_this_category_move_to_uncategor)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(Modifier.padding(horizontal = Spacing.large), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        CategoryColorPalette.forEach { color ->
            Box(
                Modifier
                    .size(Spacing.minimumTouchTarget)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(if (color == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(color) },
            )
        }
    }
}

@Composable
private fun IconPicker(selected: String, colorArgb: Long, onSelect: (String) -> Unit) {
    FlowRow(Modifier.padding(Spacing.large), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        CategoryIcons.all.forEach { (name, icon) ->
            Box(
                Modifier
                    .size(Spacing.minimumTouchTarget)
                    .clip(CircleShape)
                    .background(if (name == selected) Color(colorArgb).copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = name,
                    tint = if (name ==
                        selected
                    ) {
                        Color(colorArgb)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Lists labels with rename and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    labels: List<Label>,
    onRename: (Label, String) -> Unit,
    onDelete: (Label) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<Label?>(null) }
    var deleting by remember { mutableStateOf<Label?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_labels)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (labels.isEmpty()) {
                item {
                    Text(stringResource(R.string.settings_no_labels_yet_add_labels_from_any_note), Modifier.padding(Spacing.large))
                }
            }
            items(labels, key = { it.id.value }) { label ->
                ListItem(
                    headlineContent = { Text("#${label.name}") },
                    trailingContent = {
                        IconButton(onClick = {
                            deleting = label
                        }) { Icon(Icons.Outlined.Delete, stringResource(R.string.settings_delete_label)) }
                    },
                    modifier = Modifier.clickable { renaming = label },
                )
            }
        }
    }
    renaming?.let { label ->
        TextEditDialog(stringResource(R.string.settings_rename_label), label.name, onConfirm = {
            onRename(label, it)
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { label ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.settings_delete_label_named, label.name)) },
            text = { Text(stringResource(R.string.settings_the_label_is_removed_from_every_note_not)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(label)
                    deleting = null
                }) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
