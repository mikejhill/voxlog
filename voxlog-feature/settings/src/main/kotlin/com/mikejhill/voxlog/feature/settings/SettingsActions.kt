package com.mikejhill.voxlog.feature.settings

import android.net.Uri
import com.mikejhill.voxlog.core.datastore.HookSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.PostProcessingSettings
import com.mikejhill.voxlog.core.datastore.ThemeMode
import com.mikejhill.voxlog.core.model.CategoryId

/** User actions on the settings screen. Implemented by [SettingsViewModel]; screens receive only this interface. */
@Suppress("TooManyFunctions") // One action per setting keeps each call site obvious.
interface SettingsActions {
    /** Sets the category new captures go into when no shortcut specifies one. */
    fun setDefaultCategory(id: CategoryId)

    /** Enables or disables tagging notes with location. */
    fun setLocationCapture(isEnabled: Boolean)

    /** Chooses precise (GPS) versus approximate location. */
    fun setPreciseLocation(isPrecise: Boolean)

    /** Selects a speech model and downloads it if needed. */
    fun selectSpeechModel(id: String)

    /** Deletes a downloaded speech model to free space. */
    fun deleteSpeechModel(id: String)

    /** Enables semantic search and downloads its model. */
    fun setSemanticSearch(isEnabled: Boolean)

    /** Sets the app theme. */
    fun setThemeMode(mode: ThemeMode)

    /** Toggles wallpaper-based colors. */
    fun setDynamicColor(isEnabled: Boolean)

    /** Applies a change to the AI post-processing configuration. */
    fun updatePostProcessing(transform: (PostProcessingSettings) -> PostProcessingSettings)

    /** Selects the provider; NONE turns post-processing off entirely. */
    fun setProvider(provider: LlmProviderType)

    /** Stores (or clears, when blank) the provider API key in the encrypted secret store. */
    fun setApiKey(key: String)

    /** Fetches the provider's model list for the picker. */
    fun loadAvailableModels()

    /** Adds or replaces a hook. */
    fun saveHook(hook: HookSettings)

    /** Creates an empty hook template. */
    fun newHook(): HookSettings

    /** Removes a hook. */
    fun deleteHook(id: String)

    /** Sets the sync folder chosen with the system folder picker. */
    fun setSyncFolder(uri: Uri)

    /** Stops syncing to the folder. */
    fun clearSyncFolder()

    /** Triggers a sync. */
    fun syncNow()

    /** Exports everything to the file chosen with the system file picker. */
    fun exportTo(uri: Uri)

    /** Clears the transient snackbar message after it is shown. */
    fun onMessageShown()
}
