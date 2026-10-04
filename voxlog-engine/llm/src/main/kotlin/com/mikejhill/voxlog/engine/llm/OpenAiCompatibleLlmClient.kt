package com.mikejhill.voxlog.engine.llm

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * [LlmClient] for OpenAI and any OpenAI-compatible endpoint (LiteLLM, Ollama, vLLM, a personal
 * gateway). Talks to `{baseUrl}/chat/completions` with a strict `json_schema` response format.
 *
 * @param baseUrl API root including the version segment, e.g. `https://api.openai.com/v1`.
 * @param apiKey bearer token; may be null for local endpoints that need none.
 */
class OpenAiCompatibleLlmClient(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun completeJson(systemPrompt: String, userPrompt: String, schema: JsonObject): String {
        val body = buildJsonObject {
            put("model", model)
            put(
                "messages",
                buildJsonArray {
                    add(message("system", systemPrompt))
                    add(message("user", userPrompt))
                },
            )
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_schema")
                    put(
                        "json_schema",
                        buildJsonObject {
                            put("name", "note_processing")
                            put("strict", true)
                            put("schema", schema)
                        },
                    )
                },
            )
        }
        val response = execute(newRequest("chat/completions").post(body.toString().toRequestBody(JSON_MEDIA_TYPE)))
        val choice = response.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LlmException("Response had no choices", isRetryable = false)
        return choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw LlmException("Response had no content", isRetryable = false)
    }

    override suspend fun listModels(): List<String> {
        val response = execute(newRequest("models").get())
        return response.jsonObject["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?.sorted()
            .orEmpty()
    }

    private fun newRequest(path: String): Request.Builder = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path).apply {
        apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }

    private suspend fun execute(request: Request.Builder) = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request.build()).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    val isRetryable = response.code == HTTP_TOO_MANY_REQUESTS || response.code >= HTTP_SERVER_ERROR
                    throw LlmException("HTTP ${response.code} from $baseUrl", isRetryable)
                }
                json.parseToJsonElement(text)
            }
        } catch (exception: IOException) {
            throw LlmException("Network error talking to $baseUrl", isRetryable = true, exception)
        }
    }

    private fun message(role: String, content: String) = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    /** Well-known endpoints. */
    companion object {
        /** OpenAI's public API root. */
        const val OPENAI_BASE_URL: String = "https://api.openai.com/v1"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
