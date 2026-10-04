package com.mikejhill.voxlog.core.datastore

import com.mikejhill.voxlog.core.model.CategoryId
import kotlinx.serialization.Serializable

/**
 * Global, device-wide preferences. Persisted as JSON in DataStore; every field has a default so
 * older files always decode after new fields are added.
 */
@Serializable
data class AppSettings(
    val defaultCategoryId: String = CategoryId.UNCATEGORIZED.value,
    val isLocationCaptureEnabled: Boolean = false,
    val isPreciseLocationEnabled: Boolean = false,
    val speechModelId: String = DEFAULT_SPEECH_MODEL_ID,
    val isSemanticSearchEnabled: Boolean = true,
    val syncFolderUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDynamicColorEnabled: Boolean = true,
    val postProcessing: PostProcessingSettings = PostProcessingSettings(),
    val hooks: List<HookSettings> = emptyList(),
    val hasCompletedOnboarding: Boolean = false,
) {
    /** Typed accessor for [defaultCategoryId]. */
    val defaultCategory: CategoryId
        get() = CategoryId(defaultCategoryId)

    /** Defaults shared with other modules. */
    companion object {
        /** Speech model used until the user picks another one. */
        const val DEFAULT_SPEECH_MODEL_ID: String = "base.en"
    }
}

/** App theme preference. */
@Serializable
enum class ThemeMode {
    /** Follow the system light/dark setting. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark. */
    DARK,
}

/**
 * Optional AI post-processing. With [provider] set to [LlmProviderType.NONE] the app is a pure
 * note-taking tool and no network calls are made for processing.
 */
@Serializable
data class PostProcessingSettings(
    val provider: LlmProviderType = LlmProviderType.NONE,
    val model: String = "",
    val customBaseUrl: String = "",
    val shouldCleanupGlobally: Boolean = false,
    val shouldAutoLabelGlobally: Boolean = false,
    val shouldAutoCategorizeGlobally: Boolean = false,
    val shouldAutoNameGlobally: Boolean = false,
    val cleanupInstructions: String = "",
)

/**
 * A custom HTTP hook called after a note is captured. See `docs/hooks.md` for the contract.
 *
 * @property categoryIds categories the hook applies to; empty means every category (global).
 */
@Serializable
data class HookSettings(
    val id: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val shouldIncludeAudio: Boolean = false,
    val isEnabled: Boolean = true,
    val categoryIds: Set<String> = emptySet(),
) {
    /** Whether this hook runs for notes captured into [categoryId]. */
    fun appliesTo(categoryId: String): Boolean = isEnabled && (categoryIds.isEmpty() || categoryId in categoryIds)
}

/** Which LLM API post-processing talks to. */
@Serializable
enum class LlmProviderType {
    /** Post-processing disabled. */
    NONE,

    /** Anthropic Messages API. */
    ANTHROPIC,

    /** OpenAI API. */
    OPENAI,

    /** Any OpenAI-compatible endpoint at a custom base URL (LiteLLM, Ollama, own gateway). */
    OPENAI_COMPATIBLE,
}
