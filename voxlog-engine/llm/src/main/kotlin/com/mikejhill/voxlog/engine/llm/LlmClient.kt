package com.mikejhill.voxlog.engine.llm

import kotlinx.serialization.json.JsonObject

/** A provider-neutral LLM endpoint that answers with JSON matching a schema. */
interface LlmClient {
    /**
     * Sends [systemPrompt] and [userPrompt] and returns the model's JSON reply as text.
     * The reply is constrained to [schema] where the provider supports structured outputs.
     */
    suspend fun completeJson(systemPrompt: String, userPrompt: String, schema: JsonObject): String

    /** Lists model ids the configured credentials can use, for the Settings picker. */
    suspend fun listModels(): List<String>
}

/** Raised when a provider call fails; [isRetryable] distinguishes rate limits and outages from bad input. */
class LlmException(message: String, val isRetryable: Boolean, cause: Throwable? = null) : Exception(message, cause)
