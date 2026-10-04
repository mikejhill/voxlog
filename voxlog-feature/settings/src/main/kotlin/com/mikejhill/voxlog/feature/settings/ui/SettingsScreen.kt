package com.mikejhill.voxlog.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikejhill.voxlog.core.data.model.ModelFileState
import com.mikejhill.voxlog.core.datastore.HookSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.ThemeMode
import com.mikejhill.voxlog.core.designsystem.component.CategoryPickerSheet
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.feature.settings.SettingsActions
import com.mikejhill.voxlog.feature.settings.SettingsUiState

/** Global settings, grouped from most to least frequently used. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, navigation: SettingsNavigation, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = navigation.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).testTag("settingsList")) {
            item { CaptureSection(state, actions, navigation) }
            item { SpeechSection(state, actions) }
            item { SearchSection(state, actions) }
            item { AiSection(state, actions) }
            item { HooksSection(state, actions) }
            item { StorageSection(state, actions) }
            item { AppearanceSection(state, actions) }
            item { AboutSection(navigation.appVersion) }
        }
    }
}

@Composable
private fun CaptureSection(state: SettingsUiState, actions: SettingsActions, navigation: SettingsNavigation) {
    var isPickingDefault by remember { mutableStateOf(false) }
    val defaultCategory = state.categories.firstOrNull { it.id.value == state.settings.defaultCategoryId }
    SectionHeader("Capture")
    ClickRow("Default category", summary = defaultCategory?.name ?: "Uncategorized", onClick = { isPickingDefault = true })
    ClickRow(
        "Categories",
        summary = "Add categories, per-category settings and home-screen shortcuts",
        onClick = navigation.onManageCategories,
        trailing = { Chevron() },
    )
    ClickRow("Labels", summary = "Rename or delete labels", onClick = navigation.onManageLabels, trailing = { Chevron() })
    SwitchRow(
        "Save location",
        isChecked = state.settings.isLocationCaptureEnabled,
        summary = "Tag new notes with where they were captured",
        onCheckedChange = { isEnabled ->
            if (isEnabled) navigation.onRequestLocationPermission(state.settings.isPreciseLocationEnabled)
            actions.setLocationCapture(isEnabled)
        },
    )
    SwitchRow(
        "Precise location",
        isChecked = state.settings.isPreciseLocationEnabled,
        isEnabled = state.settings.isLocationCaptureEnabled,
        summary = "Use GPS instead of approximate network location",
        onCheckedChange = { isPrecise ->
            if (isPrecise) navigation.onRequestLocationPermission(true)
            actions.setPreciseLocation(isPrecise)
        },
    )
    if (isPickingDefault) {
        CategoryPickerSheet(
            categories = state.categories,
            selectedId = defaultCategory?.id,
            onSelect = {
                actions.setDefaultCategory(it)
                isPickingDefault = false
            },
            onDismiss = { isPickingDefault = false },
        )
    }
}

@Composable
private fun SpeechSection(state: SettingsUiState, actions: SettingsActions) {
    SectionHeader("Speech to text (on device)")
    ModelCatalog.speechModels.forEach { model ->
        val modelState = state.speechModelStates[model.id] ?: ModelFileState.Missing
        RadioRow(
            title = model.displayName,
            summary = "${model.file.sizeBytes / BYTES_PER_MEGABYTE} MB · ${describe(modelState)}",
            isSelected = state.settings.speechModelId == model.id,
            onSelect = { actions.selectSpeechModel(model.id) },
            trailing = {
                if (modelState == ModelFileState.Ready && state.settings.speechModelId != model.id) {
                    IconButton(onClick = { actions.deleteSpeechModel(model.id) }) { Icon(Icons.Outlined.Delete, "Delete model") }
                } else {
                    ModelStateIcon(modelState)
                }
            },
        )
    }
}

@Composable
private fun SearchSection(state: SettingsUiState, actions: SettingsActions) {
    SectionHeader("Search")
    SwitchRow(
        "Semantic search",
        isChecked = state.settings.isSemanticSearchEnabled,
        summary = "Also find related notes by meaning · ${ModelCatalog.embeddingModel.modelFile.sizeBytes / BYTES_PER_MEGABYTE} MB · " +
            describe(state.embeddingModelState),
        onCheckedChange = actions::setSemanticSearch,
    )
}

@Composable
private fun AiSection(state: SettingsUiState, actions: SettingsActions) {
    val config = state.settings.postProcessing
    SectionHeader("AI processing (optional)")
    LlmProviderType.entries.forEach { provider ->
        RadioRow(
            title = providerName(provider),
            isSelected = config.provider == provider,
            onSelect = { actions.setProvider(provider) },
        )
    }
    if (config.provider == LlmProviderType.NONE) return
    TextValueRow(
        "API key",
        if (state.hasApiKey) "set" else "",
        actions::setApiKey,
        isSecret = true,
        placeholder = "Stored encrypted on this device",
    )
    if (config.provider == LlmProviderType.OPENAI_COMPATIBLE) {
        TextValueRow("Base URL", config.customBaseUrl, { url ->
            actions.updatePostProcessing { it.copy(customBaseUrl = url.trim()) }
        }, placeholder = "https://my-gateway.example/v1")
    }
    ModelPickerRow(state, actions)
    SwitchRow("Clean up transcripts", config.shouldCleanupGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldCleanupGlobally = value) }
    }, summary = "Fix punctuation and filler words in voice notes")
    SwitchRow("Auto-name", config.shouldAutoNameGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldAutoNameGlobally = value) }
    }, summary = "Replace timestamp titles with a short summary")
    SwitchRow("Auto-label", config.shouldAutoLabelGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldAutoLabelGlobally = value) }
    })
    SwitchRow("Auto-categorize", config.shouldAutoCategorizeGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldAutoCategorizeGlobally = value) }
    }, summary = "Only for notes in Uncategorized")
    TextValueRow("Cleanup instructions", config.cleanupInstructions, { value ->
        actions.updatePostProcessing { it.copy(cleanupInstructions = value) }
    }, isMultiline = true, placeholder = "Optional extra guidance")
}

@Composable
private fun ModelPickerRow(state: SettingsUiState, actions: SettingsActions) {
    var isPicking by remember { mutableStateOf(false) }
    val config = state.settings.postProcessing
    ClickRow("Model", summary = config.model.ifBlank { "Not set" }, onClick = {
        actions.loadAvailableModels()
        isPicking = true
    })
    if (isPicking) {
        ModelPickerDialog(
            current = config.model,
            available = state.availableModels,
            onSelect = { model ->
                actions.updatePostProcessing { it.copy(model = model) }
                isPicking = false
            },
            onDismiss = { isPicking = false },
        )
    }
}

@Composable
private fun HooksSection(state: SettingsUiState, actions: SettingsActions) {
    var editing by remember { mutableStateOf<HookSettings?>(null) }
    SectionHeader("Custom hooks")
    state.settings.hooks.forEach { hook ->
        ClickRow(
            hook.name.ifBlank {
                hook.url
            },
            summary = hook.url + if (!hook.isEnabled) " · disabled" else "",
            onClick = { editing = hook },
        )
    }
    ClickRow("Add hook", summary = "POST each new note to your own endpoint", onClick = { editing = actions.newHook() })
    editing?.let { hook ->
        HookEditorDialog(
            hook = hook,
            categories = state.categories,
            onSave = {
                actions.saveHook(it)
                editing = null
            },
            onDelete = {
                actions.deleteHook(hook.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun StorageSection(state: SettingsUiState, actions: SettingsActions) {
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(actions::setSyncFolder) }
    val exportPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(actions::exportTo) }
    SectionHeader("Storage & sync")
    val folder = state.settings.syncFolderUri
    ClickRow(
        "Sync folder",
        summary = folder?.let { it.toUri().lastPathSegment ?: it } ?: "Off — notes stay on this device only",
        onClick = { folderPicker.launch(null) },
    )
    if (folder != null) {
        ClickRow("Sync now", onClick = actions::syncNow)
        ClickRow("Stop syncing", summary = "Files already in the folder are kept", onClick = actions::clearSyncFolder)
    }
    ClickRow("Export everything", summary = "ZIP with notes.json, notes.csv and audio files", onClick = {
        exportPicker.launch("voxlog-export.zip")
    })
}

@Composable
private fun AppearanceSection(state: SettingsUiState, actions: SettingsActions) {
    SectionHeader("Appearance")
    ThemeMode.entries.forEach { mode ->
        RadioRow(themeName(mode), isSelected = state.settings.themeMode == mode, onSelect = { actions.setThemeMode(mode) })
    }
    SwitchRow("Wallpaper colors", state.settings.isDynamicColorEnabled, actions::setDynamicColor, summary = "Material You dynamic color")
}

@Composable
private fun AboutSection(appVersion: String) {
    SectionHeader("About")
    ClickRow("VoxLog $appVersion", summary = "Free and open source · no accounts · no tracking", onClick = {})
}

@Composable
private fun ModelStateIcon(state: ModelFileState) {
    when (state) {
        ModelFileState.Ready -> Icon(Icons.Outlined.CheckCircle, "Downloaded")

        is ModelFileState.Downloading -> CircularProgressIndicator(progress = {
            state.progressPercent / 100f
        }, modifier = Modifier.padding(4.dp))

        else -> Icon(Icons.Outlined.Download, "Not downloaded")
    }
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)

private fun describe(state: ModelFileState): String = when (state) {
    ModelFileState.Missing -> "not downloaded"
    is ModelFileState.Downloading -> "downloading ${state.progressPercent}%"
    ModelFileState.Ready -> "ready"
    is ModelFileState.Failed -> "download failed — tap to retry"
}

private fun providerName(provider: LlmProviderType): String = when (provider) {
    LlmProviderType.NONE -> "Off (note-taking only)"
    LlmProviderType.ANTHROPIC -> "Anthropic (Claude)"
    LlmProviderType.OPENAI -> "OpenAI"
    LlmProviderType.OPENAI_COMPATIBLE -> "Custom OpenAI-compatible endpoint"
}

private fun themeName(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private const val BYTES_PER_MEGABYTE = 1_000_000
