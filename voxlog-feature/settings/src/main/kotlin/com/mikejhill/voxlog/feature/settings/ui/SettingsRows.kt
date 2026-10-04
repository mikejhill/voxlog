package com.mikejhill.voxlog.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.feature.settings.R

/** A section heading in the settings list. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.large, end = Spacing.large, top = Spacing.extraLarge, bottom = Spacing.small),
    )
}

/** A row with a trailing switch; the whole row is the touch target. */
@Composable
fun SwitchRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    isEnabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = isChecked, onCheckedChange = null, enabled = isEnabled) },
        modifier = modifier.toggleable(value = isChecked, enabled = isEnabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/** A tappable row that opens something. */
@Composable
fun ClickRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = trailing,
        modifier = modifier.clickable(onClick = onClick),
    )
}

/** One option in a single-choice group. */
@Composable
fun RadioRow(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
        trailingContent = trailing,
        modifier = modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect),
    )
}

/** A row showing a text value that opens an edit dialog. Secret values are masked. */
@Composable
fun TextValueRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    placeholder: String = "",
    isMultiline: Boolean = false,
) {
    var isEditing by remember { mutableStateOf(false) }
    ClickRow(
        title = title,
        summary = when {
            value.isBlank() -> placeholder.ifBlank { stringResource(R.string.settings_not_set) }
            isSecret -> "••••••••"
            else -> value
        },
        onClick = { isEditing = true },
        modifier = modifier,
    )
    if (isEditing) {
        TextEditDialog(
            title = title,
            initialValue = if (isSecret) "" else value,
            isSecret = isSecret,
            isMultiline = isMultiline,
            onConfirm = {
                onValueChange(it)
                isEditing = false
            },
            onDismiss = { isEditing = false },
        )
    }
}

/** A dialog with one text field. */
@Composable
fun TextEditDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    isSecret: Boolean = false,
    isMultiline: Boolean = false,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = !isMultiline,
                minLines = if (isMultiline) MULTILINE_MIN_LINES else 1,
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.settings_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private const val MULTILINE_MIN_LINES = 4
