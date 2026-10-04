package com.mikejhill.voxlog.feature.capture.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.mikejhill.voxlog.core.designsystem.component.CategoryChip
import com.mikejhill.voxlog.core.designsystem.component.CategoryPickerSheet
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.feature.capture.ComposerUiState
import com.mikejhill.voxlog.feature.capture.R

/**
 * Full-screen editor for typed notes. The body is focused with the keyboard up immediately; the
 * title is prefilled with the capture timestamp and can be changed before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextComposerScreen(
    state: ComposerUiState,
    categories: List<Category>,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onCategoryChange: (CategoryId) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bodyFocus = remember { FocusRequester() }
    var isPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bodyFocus.requestFocus() }
    val category = categories.firstOrNull { it.id == state.categoryId }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (category !=
                        null
                    ) {
                        CategoryChip(category.name, category.colorArgb, category.iconName, onClick = { isPickerOpen = true })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.capture_close))
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = Spacing.small).testTag("saveTextNote"),
                    ) {
                        Text(stringResource(R.string.capture_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            TextField(
                value = state.title,
                onValueChange = onTitleChange,
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text(stringResource(R.string.capture_title_placeholder)) },
                singleLine = true,
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth().testTag("titleField"),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.large))
            TextField(
                value = state.text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.capture_body_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = transparentFieldColors(),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(bodyFocus)
                    .testTag("bodyField"),
            )
        }
    }
    if (isPickerOpen) {
        CategoryPickerSheet(
            categories = categories,
            selectedId = state.categoryId,
            onSelect = {
                onCategoryChange(it)
                isPickerOpen = false
            },
            onDismiss = { isPickerOpen = false },
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@PreviewLightDark
@Composable
private fun TextComposerPreview() {
    VoxLogTheme(isDynamicColorEnabled = false) {
        TextComposerScreen(
            state = ComposerUiState(
                title = "2026-10-04T08:15:00-05:00",
                text = "Ideas for the weekend…",
                categoryId = CategoryId("journal"),
            ),
            categories = RecordScreenSamples.categories,
            onTitleChange = {},
            onTextChange = {},
            onCategoryChange = {},
            onSave = {},
            onClose = {},
        )
    }
}
