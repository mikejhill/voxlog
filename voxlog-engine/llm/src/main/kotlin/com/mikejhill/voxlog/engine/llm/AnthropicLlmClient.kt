package com.mikejhill.voxlog.engine.llm

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * [LlmClient] for the Anthropic Messages API using the official Java SDK. Uses structured outputs
 * (`output_config.format`) so replies always match the requested schema.
 */
class AnthropicLlmClient(
    apiKey: String,
    private val model: String,
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build(),
) : LlmClient {
    override suspend fun completeJson(systemPrompt: String, userPrompt: String, schema: JsonObject): String = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(systemPrompt)
            .addUserMessage(userPrompt)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(toSdkSchema(schema)).build())
                    .build(),
            )
            .build()
        val message = callSdk { client.messages().create(params) }
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw LlmException("The model declined to process this note", isRetryable = false)
        }
        message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
    }

    override suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        callSdk { client.models().list().autoPager().map { it.id() } }
    }

    private fun toSdkSchema(schema: JsonObject): JsonOutputFormat.Schema {
        val builder = JsonOutputFormat.Schema.builder()
        schema.forEach { (key, value) -> builder.putAdditionalProperty(key, JsonValue.from(JsonValueConverter.toPlain(value))) }
        return builder.build()
    }

    private inline fun <T> callSdk(block: () -> T): T = try {
        block()
    } catch (exception: RateLimitException) {
        throw LlmException("Rate limited by Anthropic", isRetryable = true, exception)
    } catch (exception: InternalServerException) {
        throw LlmException("Anthropic is temporarily unavailable", isRetryable = true, exception)
    } catch (exception: AnthropicServiceException) {
        throw LlmException("Anthropic rejected the request (${exception.statusCode()})", isRetryable = false, exception)
    } catch (exception: AnthropicIoException) {
        throw LlmException("Network error talking to Anthropic", isRetryable = true, exception)
    }

    /** Defaults for the post-processing call. */
    companion object {
        /** Model pre-filled in Settings; the user can pick any model their key allows. */
        const val DEFAULT_MODEL: String = "claude-opus-5-5"

        private const val MAX_TOKENS = 16_000L
    }
}
