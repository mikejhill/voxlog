package com.mikejhill.voxlog.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikejhill.voxlog.core.datastore.HookSettings
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.model.Category

/** Lets the user pick a model from the provider's list or type any model id. */
@Composable
fun ModelPickerDialog(current: String, available: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, label = { Text("Model id") })
                if (available.isEmpty()) {
                    Text("Loading models from the provider…", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(available) { model ->
                            ListItem(headlineContent = { Text(model) }, modifier = Modifier.clickable { onSelect(model) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(typed.trim()) }, enabled = typed.isNotBlank()) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Edits one HTTP hook: name, URL, headers (one `Name: value` per line), audio upload, enabled
 * state and the categories it applies to (none selected = all categories).
 */
@Composable
fun HookEditorDialog(
    hook: HookSettings,
    categories: List<Category>,
    onSave: (HookSettings) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(hook.name) }
    var url by remember { mutableStateOf(hook.url) }
    var headers by remember { mutableStateOf(hook.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var shouldIncludeAudio by remember { mutableStateOf(hook.shouldIncludeAudio) }
    var isEnabled by remember { mutableStateOf(hook.isEnabled) }
    var categoryIds by remember { mutableStateOf(hook.categoryIds) }
    val isUrlValid = url.startsWith("https://") || url.startsWith("http://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hook.name.isBlank()) "New hook" else "Edit hook") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, {
                    url = it
                }, label = {
                    Text("URL")
                }, singleLine = true, isError = url.isNotEmpty() && !isUrlValid, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(headers, {
                    headers = it
                }, label = { Text("Headers (Name: value per line)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                CheckRow("Send audio file", shouldIncludeAudio) { shouldIncludeAudio = it }
                CheckRow("Enabled", isEnabled) { isEnabled = it }
                Text("Categories (none = all)", style = MaterialTheme.typography.labelLarge)
                categories.forEach { category ->
                    CheckRow(category.name, category.id.value in categoryIds) { isChecked ->
                        categoryIds = if (isChecked) categoryIds + category.id.value else categoryIds - category.id.value
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isUrlValid,
                onClick = {
                    onSave(
                        hook.copy(
                            name = name.trim(),
                            url = url.trim(),
                            headers = parseHeaders(headers),
                            shouldIncludeAudio = shouldIncludeAudio,
                            isEnabled = isEnabled,
                            categoryIds = categoryIds,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            if (hook.url.isNotBlank()) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CheckRow(title: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Checkbox(checked = isChecked, onCheckedChange = null) },
        modifier = Modifier.clickable { onCheckedChange(!isChecked) },
    )
}

/** Parses `Name: value` lines, ignoring blank or malformed lines. */
internal fun parseHeaders(text: String): Map<String, String> = text.lines()
    .mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
    .filter { (name, _) -> name.isNotEmpty() }
    .toMap()
