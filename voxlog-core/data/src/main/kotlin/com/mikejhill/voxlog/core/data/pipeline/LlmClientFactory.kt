package com.mikejhill.voxlog.core.data.pipeline

import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.SecretNames
import com.mikejhill.voxlog.core.datastore.SecretStore
import com.mikejhill.voxlog.engine.llm.AnthropicLlmClient
import com.mikejhill.voxlog.engine.llm.LlmClient
import com.mikejhill.voxlog.engine.llm.OpenAiCompatibleLlmClient
import javax.inject.Inject

/** Builds the [LlmClient] for the configured provider, or null when post-processing is off. */
interface LlmClientFactory {
    /** Returns a client for [settings], or null if no provider is configured or a required key is missing. */
    suspend fun create(settings: AppSettings): LlmClient?

    /** Returns a client usable only for [LlmClient.listModels], before a model has been chosen. */
    suspend fun createForModelListing(settings: AppSettings): LlmClient?
}

/** [LlmClientFactory] that reads the API key from the encrypted [SecretStore]. */
class SecretStoreLlmClientFactory @Inject constructor(private val secretStore: SecretStore) : LlmClientFactory {
    override suspend fun create(settings: AppSettings): LlmClient? = build(settings, settings.postProcessing.model.ifBlank { null })

    override suspend fun createForModelListing(settings: AppSettings): LlmClient? =
        build(settings, settings.postProcessing.model.ifBlank { LISTING_PLACEHOLDER_MODEL })

    private suspend fun build(settings: AppSettings, model: String?): LlmClient? {
        val config = settings.postProcessing
        val apiKey = secretStore.read(SecretNames.LLM_API_KEY)
        return when (config.provider) {
            LlmProviderType.NONE -> null

            LlmProviderType.ANTHROPIC -> apiKey?.let { AnthropicLlmClient(it, model ?: AnthropicLlmClient.DEFAULT_MODEL) }

            LlmProviderType.OPENAI ->
                if (apiKey == null ||
                    model == null
                ) {
                    null
                } else {
                    OpenAiCompatibleLlmClient(OpenAiCompatibleLlmClient.OPENAI_BASE_URL, apiKey, model)
                }

            LlmProviderType.OPENAI_COMPATIBLE ->
                if (config.customBaseUrl.isBlank() ||
                    model == null
                ) {
                    null
                } else {
                    OpenAiCompatibleLlmClient(config.customBaseUrl, apiKey, model)
                }
        }
    }

    private companion object {
        /** Never sent with a completion; model listing ignores the model. */
        const val LISTING_PLACEHOLDER_MODEL = "unused"
    }
}
