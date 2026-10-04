package com.mikejhill.voxlog.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikejhill.voxlog.core.data.model.ModelDownloadScheduler
import com.mikejhill.voxlog.core.data.model.ModelFileState
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.data.pipeline.LlmClientFactory
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.storage.UserFilesRepository
import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.HookSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.PostProcessingSettings
import com.mikejhill.voxlog.core.datastore.SecretNames
import com.mikejhill.voxlog.core.datastore.SecretStore
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.datastore.ThemeMode
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.engine.llm.LlmException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the settings screen renders. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val categories: List<Category> = emptyList(),
    val speechModelStates: Map<String, ModelFileState> = emptyMap(),
    val embeddingModelState: ModelFileState = ModelFileState.Missing,
    val hasApiKey: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val message: SettingsMessage? = null,
)

/** Reads and writes global settings, model downloads, AI provider configuration, hooks, sync and export. */
@Suppress("TooManyFunctions") // One function per setting keeps each call site obvious.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secretStore: SecretStore,
    private val modelRepository: ModelRepository,
    private val modelDownloadScheduler: ModelDownloadScheduler,
    private val userFilesRepository: UserFilesRepository,
    private val llmClientFactory: LlmClientFactory,
    categoryRepository: CategoryRepository,
) : ViewModel(),
    SettingsActions {
    private val transient = MutableStateFlow(SettingsUiState())

    /** Screen state. */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        categoryRepository.observeCategories(),
        modelRepository.fileStates,
        transient,
    ) { settings, categories, _, extra ->
        extra.copy(
            settings = settings,
            categories = categories,
            speechModelStates = ModelCatalog.speechModels.associate { it.id to modelRepository.stateOf(it.file) },
            embeddingModelState = embeddingState(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    init {
        viewModelScope.launch { transient.update { it.copy(hasApiKey = secretStore.read(SecretNames.LLM_API_KEY) != null) } }
    }

    /** Sets the category new captures go into when no shortcut specifies one. */
    override fun setDefaultCategory(id: CategoryId) = update { it.copy(defaultCategoryId = id.value) }

    /** Enables or disables tagging notes with location. */
    override fun setLocationCapture(isEnabled: Boolean) = update { it.copy(isLocationCaptureEnabled = isEnabled) }

    /** Chooses precise (GPS) versus approximate location. */
    override fun setPreciseLocation(isPrecise: Boolean) = update { it.copy(isPreciseLocationEnabled = isPrecise) }

    /** Selects a speech model and downloads it if needed. */
    override fun selectSpeechModel(id: String) {
        update { it.copy(speechModelId = id) }
        modelDownloadScheduler.downloadSpeechModel(id)
    }

    /** Deletes a downloaded speech model to free space. */
    override fun deleteSpeechModel(id: String) = modelRepository.delete(ModelCatalog.speechModel(id).file)

    /** Enables semantic search and downloads its model. */
    override fun setSemanticSearch(isEnabled: Boolean) {
        update { it.copy(isSemanticSearchEnabled = isEnabled) }
        if (isEnabled) modelDownloadScheduler.downloadEmbeddingModel()
    }

    /** Sets the app theme. */
    override fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    /** Toggles wallpaper-based colors. */
    override fun setDynamicColor(isEnabled: Boolean) = update { it.copy(isDynamicColorEnabled = isEnabled) }

    /** Applies a change to the AI post-processing configuration. */
    override fun updatePostProcessing(transform: (PostProcessingSettings) -> PostProcessingSettings) =
        update { it.copy(postProcessing = transform(it.postProcessing)) }

    /** Selects the provider; NONE turns post-processing off entirely. */
    override fun setProvider(provider: LlmProviderType) = updatePostProcessing { it.copy(provider = provider) }

    /** Stores (or clears, when blank) the provider API key in the encrypted secret store. */
    override fun setApiKey(key: String) = launch {
        secretStore.write(SecretNames.LLM_API_KEY, key.trim().ifBlank { null })
        transient.update { it.copy(hasApiKey = key.isNotBlank()) }
    }

    /** Fetches the provider's model list for the picker. */
    override fun loadAvailableModels() = launch {
        val client = llmClientFactory.createForModelListing(uiState.value.settings)
        if (client == null) {
            showMessage(SettingsMessage.Kind.PROVIDER_NOT_CONFIGURED)
            return@launch
        }
        try {
            transient.update { it.copy(availableModels = client.listModels()) }
        } catch (exception: LlmException) {
            showMessage(SettingsMessage.Kind.MODEL_LIST_FAILED, exception.message)
        }
    }

    /** Adds or replaces a hook. */
    override fun saveHook(hook: HookSettings) = update { settings ->
        val hooks = settings.hooks.filterNot { it.id == hook.id } + hook
        settings.copy(hooks = hooks.sortedBy { it.name.lowercase() })
    }

    /** Creates an empty hook template. */
    override fun newHook(): HookSettings = HookSettings(id = UUID.randomUUID().toString(), name = "", url = "")

    /** Removes a hook. */
    override fun deleteHook(id: String) = update { settings -> settings.copy(hooks = settings.hooks.filterNot { it.id == id }) }

    /** Sets the sync folder chosen with the system folder picker. */
    override fun setSyncFolder(uri: Uri) = launch {
        userFilesRepository.setSyncFolder(uri)
        showMessage(SettingsMessage.Kind.SYNC_FOLDER_SET)
    }

    /** Stops syncing to the folder. */
    override fun clearSyncFolder() = launch { userFilesRepository.clearSyncFolder() }

    /** Triggers a sync. */
    override fun syncNow() {
        userFilesRepository.syncNow()
        showMessage(SettingsMessage.Kind.SYNC_STARTED)
    }

    /** Exports everything to the file chosen with the system file picker. */
    override fun exportTo(uri: Uri) = launch {
        try {
            userFilesRepository.exportTo(uri)
            showMessage(SettingsMessage.Kind.EXPORT_COMPLETE)
        } catch (exception: IOException) {
            showMessage(SettingsMessage.Kind.EXPORT_FAILED, exception.message)
        }
    }

    /** Clears the transient snackbar message after it is shown. */
    override fun onMessageShown() = transient.update { it.copy(message = null) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun showMessage(kind: SettingsMessage.Kind, detail: String? = null) =
        transient.update { it.copy(message = SettingsMessage(kind, detail)) }

    private fun embeddingState(): ModelFileState {
        val model = ModelCatalog.embeddingModel
        val modelState = modelRepository.stateOf(model.modelFile)
        val vocabularyState = modelRepository.stateOf(model.vocabularyFile)
        return if (modelState == ModelFileState.Ready && vocabularyState != ModelFileState.Ready) vocabularyState else modelState
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
