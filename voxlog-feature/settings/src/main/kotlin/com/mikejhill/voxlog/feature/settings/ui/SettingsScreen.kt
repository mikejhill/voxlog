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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mikejhill.voxlog.core.data.model.ModelFileState
import com.mikejhill.voxlog.core.datastore.HookSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.ThemeMode
import com.mikejhill.voxlog.core.designsystem.component.CategoryPickerSheet
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.feature.settings.R
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
                title = { Text(stringResource(R.string.settings_settings)) },
                navigationIcon = {
                    IconButton(onClick = navigation.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
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
    SectionHeader(stringResource(R.string.settings_capture))
    ClickRow(
        stringResource(R.string.settings_default_category),
        summary =
            defaultCategory?.name ?: stringResource(R.string.settings_uncategorized),
        onClick = { isPickingDefault = true },
    )
    ClickRow(
        stringResource(R.string.settings_categories),
        summary = stringResource(R.string.settings_add_categories_per_category_settings_and),
        onClick = navigation.onManageCategories,
        trailing = { Chevron() },
    )
    ClickRow(
        stringResource(
            R.string.settings_labels,
        ),
        summary = stringResource(R.string.settings_rename_or_delete_labels),
        onClick = navigation.onManageLabels,
        trailing = {
            Chevron()
        },
    )
    SwitchRow(
        stringResource(R.string.settings_save_location),
        isChecked = state.settings.isLocationCaptureEnabled,
        summary = stringResource(R.string.settings_tag_new_notes_with_where_they_were_captu),
        onCheckedChange = { isEnabled ->
            if (isEnabled) navigation.onRequestLocationPermission(state.settings.isPreciseLocationEnabled)
            actions.setLocationCapture(isEnabled)
        },
    )
    SwitchRow(
        stringResource(R.string.settings_precise_location),
        isChecked = state.settings.isPreciseLocationEnabled,
        isEnabled = state.settings.isLocationCaptureEnabled,
        summary = stringResource(R.string.settings_use_gps_instead_of_approximate_network_l),
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
    SectionHeader(stringResource(R.string.settings_speech_to_text_on_device))
    ModelCatalog.speechModels.forEach { model ->
        val modelState = state.speechModelStates[model.id] ?: ModelFileState.Missing
        RadioRow(
            title = model.displayName,
            summary = "${model.file.sizeBytes / BYTES_PER_MEGABYTE} MB · ${describe(modelState)}",
            isSelected = state.settings.speechModelId == model.id,
            onSelect = { actions.selectSpeechModel(model.id) },
            trailing = {
                if (modelState == ModelFileState.Ready && state.settings.speechModelId != model.id) {
                    IconButton(onClick = {
                        actions.deleteSpeechModel(model.id)
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.settings_delete_model)) }
                } else {
                    ModelStateIcon(modelState)
                }
            },
        )
    }
}

@Composable
private fun SearchSection(state: SettingsUiState, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_search))
    SwitchRow(
        stringResource(R.string.settings_semantic_search),
        isChecked = state.settings.isSemanticSearchEnabled,
        summary = stringResource(
            R.string.settings_semantic_search_summary,
            ModelCatalog.embeddingModel.modelFile.sizeBytes / BYTES_PER_MEGABYTE,
            describe(state.embeddingModelState),
        ),
        onCheckedChange = actions::setSemanticSearch,
    )
}

@Composable
private fun AiSection(state: SettingsUiState, actions: SettingsActions) {
    val config = state.settings.postProcessing
    SectionHeader(stringResource(R.string.settings_ai_processing_optional))
    LlmProviderType.entries.forEach { provider ->
        RadioRow(
            title = providerName(provider),
            isSelected = config.provider == provider,
            onSelect = { actions.setProvider(provider) },
        )
    }
    if (config.provider == LlmProviderType.NONE) return
    TextValueRow(
        stringResource(R.string.settings_api_key),
        if (state.hasApiKey) "set" else "",
        actions::setApiKey,
        isSecret = true,
        placeholder = stringResource(R.string.settings_stored_encrypted_on_this_device),
    )
    if (config.provider == LlmProviderType.OPENAI_COMPATIBLE) {
        TextValueRow(stringResource(R.string.settings_base_url), config.customBaseUrl, { url ->
            actions.updatePostProcessing { it.copy(customBaseUrl = url.trim()) }
        }, placeholder = "https://my-gateway.example/v1")
    }
    ModelPickerRow(state, actions)
    SwitchRow(stringResource(R.string.settings_clean_up_transcripts), config.shouldCleanupGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldCleanupGlobally = value) }
    }, summary = stringResource(R.string.settings_fix_punctuation_and_filler_words_in_voic))
    SwitchRow(stringResource(R.string.settings_auto_name), config.shouldAutoNameGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldAutoNameGlobally = value) }
    }, summary = stringResource(R.string.settings_replace_timestamp_titles_with_a_short_su))
    SwitchRow(stringResource(R.string.settings_auto_label), config.shouldAutoLabelGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldAutoLabelGlobally = value) }
    })
    SwitchRow(stringResource(R.string.settings_auto_categorize), config.shouldAutoCategorizeGlobally, { value ->
        actions.updatePostProcessing { it.copy(shouldAutoCategorizeGlobally = value) }
    }, summary = stringResource(R.string.settings_only_for_notes_in_uncategorized))
    TextValueRow(stringResource(R.string.settings_cleanup_instructions), config.cleanupInstructions, { value ->
        actions.updatePostProcessing { it.copy(cleanupInstructions = value) }
    }, isMultiline = true, placeholder = stringResource(R.string.settings_optional_extra_guidance))
}

@Composable
private fun ModelPickerRow(state: SettingsUiState, actions: SettingsActions) {
    var isPicking by remember { mutableStateOf(false) }
    val config = state.settings.postProcessing
    ClickRow(
        stringResource(R.string.settings_model),
        summary = config.model.ifBlank {
            stringResource(R.string.settings_not_set)
        },
        onClick = {
            actions.loadAvailableModels()
            isPicking = true
        },
    )
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
    SectionHeader(stringResource(R.string.settings_custom_hooks))
    state.settings.hooks.forEach { hook ->
        ClickRow(
            hook.name.ifBlank {
                hook.url
            },
            summary = hook.url + if (!hook.isEnabled) " · disabled" else "",
            onClick = { editing = hook },
        )
    }
    ClickRow(
        stringResource(
            R.string.settings_add_hook,
        ),
        summary = stringResource(R.string.settings_post_each_new_note_to_your_own_endpoint),
        onClick = {
            editing =
                actions.newHook()
        },
    )
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
    SectionHeader(stringResource(R.string.settings_storage_sync))
    val folder = state.settings.syncFolderUri
    ClickRow(
        stringResource(R.string.settings_sync_folder),
        summary = folder?.let { it.toUri().lastPathSegment ?: it } ?: stringResource(R.string.settings_off_notes_stay_on_this_device_only),
        onClick = { folderPicker.launch(null) },
    )
    if (folder != null) {
        ClickRow(stringResource(R.string.settings_sync_now), onClick = actions::syncNow)
        ClickRow(
            stringResource(R.string.settings_stop_syncing),
            summary = stringResource(R.string.settings_files_already_in_the_folder_are_kept),
            onClick = actions::clearSyncFolder,
        )
    }
    ClickRow(
        stringResource(
            R.string.settings_export_everything,
        ),
        summary = stringResource(R.string.settings_zip_with_notes_json_notes_csv_and_audio),
        onClick = {
            exportPicker.launch("voxlog-export.zip")
        },
    )
}

@Composable
private fun AppearanceSection(state: SettingsUiState, actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_appearance))
    ThemeMode.entries.forEach { mode ->
        RadioRow(themeName(mode), isSelected = state.settings.themeMode == mode, onSelect = { actions.setThemeMode(mode) })
    }
    SwitchRow(
        stringResource(R.string.settings_wallpaper_colors),
        state.settings.isDynamicColorEnabled,
        actions::setDynamicColor,
        summary = stringResource(R.string.settings_material_you_dynamic_color),
    )
}

@Composable
private fun AboutSection(appVersion: String) {
    SectionHeader(stringResource(R.string.settings_about))
    ClickRow(
        stringResource(
            R.string.settings_about_version,
            appVersion,
        ),
        summary = stringResource(R.string.settings_free_and_open_source_no_accounts_no_trac),
        onClick = {
        },
    )
}

@Composable
private fun ModelStateIcon(state: ModelFileState) {
    when (state) {
        ModelFileState.Ready -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.settings_downloaded))

        is ModelFileState.Downloading -> CircularProgressIndicator(progress = {
            state.progressPercent / 100f
        }, modifier = Modifier.padding(4.dp))

        else -> Icon(Icons.Outlined.Download, stringResource(R.string.settings_not_downloaded))
    }
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)

@Composable
private fun describe(state: ModelFileState): String = when (state) {
    ModelFileState.Missing -> stringResource(R.string.settings_model_missing)
    is ModelFileState.Downloading -> stringResource(R.string.settings_model_downloading, state.progressPercent)
    ModelFileState.Ready -> stringResource(R.string.settings_model_ready)
    is ModelFileState.Failed -> stringResource(R.string.settings_model_failed)
}

@Composable
private fun providerName(provider: LlmProviderType): String = when (provider) {
    LlmProviderType.NONE -> stringResource(R.string.settings_off_note_taking_only)
    LlmProviderType.ANTHROPIC -> stringResource(R.string.settings_anthropic_claude)
    LlmProviderType.OPENAI -> stringResource(R.string.settings_openai)
    LlmProviderType.OPENAI_COMPATIBLE -> stringResource(R.string.settings_custom_openai_compatible_endpoint)
}

@Composable
private fun themeName(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_follow_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_light)
    ThemeMode.DARK -> stringResource(R.string.settings_dark)
}

private const val BYTES_PER_MEGABYTE = 1_000_000
