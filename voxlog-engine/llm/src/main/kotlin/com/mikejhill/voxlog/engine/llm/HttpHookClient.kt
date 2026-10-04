package com.mikejhill.voxlog.engine.llm

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The JSON body POSTed to a custom hook. Documented in `docs/hooks.md`; changes here are a
 * contract change and must bump [HookPayload.SCHEMA_VERSION].
 */
@Serializable
data class HookPayload(@SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION, val event: String, val note: HookNote) {
    /** Contract version. */
    companion object {
        /** Current payload schema version. */
        const val SCHEMA_VERSION: Int = 1

        /** Event name sent after a note is first saved and transcribed. */
        const val EVENT_NOTE_CREATED: String = "note.created"
    }
}

/** Note fields exposed to hooks. Timestamps are ISO-8601 UTC. */
@Serializable
data class HookNote(
    val id: String,
    val title: String,
    val text: String,
    @SerialName("raw_transcript") val rawTranscript: String?,
    val category: String,
    val labels: List<String>,
    @SerialName("capture_method") val captureMethod: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("duration_millis") val durationMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
)

/** Optional patch a hook may return; every field is optional. */
@Serializable
data class HookPatch(
    val text: String? = null,
    val title: String? = null,
    @SerialName("add_labels") val addLabels: List<String> = emptyList(),
    val category: String? = null,
)

/** A configured hook endpoint. */
data class HookEndpoint(val url: String, val headers: Map<String, String>, val shouldIncludeAudio: Boolean)

/** Calls user-defined HTTP hooks and parses their optional [HookPatch] response. */
class HttpHookClient(private val httpClient: OkHttpClient = OkHttpClient()) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * POSTs [payload] (as multipart with an `audio` part when [audioFile] is given and the endpoint
     * asks for audio) and returns the patch, or null when the hook replies with an empty body.
     */
    suspend fun invoke(endpoint: HookEndpoint, payload: HookPayload, audioFile: File?): HookPatch? = withContext(Dispatchers.IO) {
        val payloadJson = json.encodeToString(HookPayload.serializer(), payload)
        val request = Request.Builder()
            .url(endpoint.url)
            .apply { endpoint.headers.forEach { (name, value) -> header(name, value) } }
            .post(requestBody(payloadJson, audioFile.takeIf { endpoint.shouldIncludeAudio }))
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw LlmException("Hook ${endpoint.url} returned HTTP ${response.code}", response.code >= HTTP_SERVER_ERROR)
                }
                parsePatch(response.body.string())
            }
        } catch (exception: IOException) {
            throw LlmException("Hook ${endpoint.url} unreachable", isRetryable = true, exception)
        }
    }

    private fun requestBody(payloadJson: String, audioFile: File?): RequestBody {
        if (audioFile == null) return payloadJson.toRequestBody(JSON_MEDIA_TYPE)
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload", null, payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody(AUDIO_MEDIA_TYPE))
            .build()
    }

    private fun parsePatch(body: String): HookPatch? {
        if (body.isBlank()) return null
        return try {
            json.decodeFromString(HookPatch.serializer(), body)
        } catch (exception: SerializationException) {
            throw LlmException("Hook response was not a valid patch", isRetryable = false, exception)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val AUDIO_MEDIA_TYPE = "audio/mp4".toMediaType()
        const val HTTP_SERVER_ERROR = 500
    }
}
