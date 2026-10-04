package com.mikejhill.voxlog.engine.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Which built-in post-processing tasks to run for one note. */
data class PostProcessingTasks(
    val shouldCleanup: Boolean,
    val shouldAutoLabel: Boolean,
    val shouldAutoCategorize: Boolean,
    val shouldAutoName: Boolean,
) {
    /** True when at least one task is enabled. */
    val hasAnyTask: Boolean
        get() = shouldCleanup || shouldAutoLabel || shouldAutoCategorize || shouldAutoName
}

/**
 * Inputs to one post-processing call.
 *
 * @property existingLabels label names the model should prefer when labeling.
 * @property categoryNames candidate categories for auto-categorization.
 * @property extraInstructions user-supplied guidance appended to the cleanup instructions.
 */
data class PostProcessingRequest(
    val text: String,
    val tasks: PostProcessingTasks,
    val existingLabels: List<String>,
    val categoryNames: List<String>,
    val extraInstructions: String,
)

/** Model output; fields are null when the corresponding task was not requested. */
@Serializable
data class PostProcessingResult(
    @SerialName("cleaned_text") val cleanedText: String? = null,
    val title: String? = null,
    val labels: List<String>? = null,
    val category: String? = null,
)

/**
 * Runs every enabled built-in task in a single LLM call that returns one JSON object, keeping
 * latency and cost to one request per note.
 */
class NotePostProcessor(private val client: LlmClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Processes [request]; returns an empty result when no task is enabled. */
    suspend fun process(request: PostProcessingRequest): PostProcessingResult {
        if (!request.tasks.hasAnyTask) return PostProcessingResult()
        val reply = client.completeJson(systemPrompt(request), request.text, schemaFor(request.tasks))
        return try {
            json.decodeFromString(PostProcessingResult.serializer(), reply)
        } catch (exception: SerializationException) {
            throw LlmException("Model reply was not valid JSON", isRetryable = true, exception)
        }
    }

    /** Builds the instruction block describing each requested task. */
    internal fun systemPrompt(request: PostProcessingRequest): String = buildString {
        appendLine("You process personal journal notes, often dictated by voice and transcribed automatically.")
        appendLine("The user message is the note. Reply only with the JSON object described by the schema.")
        if (request.tasks.shouldCleanup) {
            appendLine(
                "cleaned_text: fix transcription errors, punctuation, capitalization and paragraphing. " +
                    "Remove filler words and false starts. Keep the author's voice, meaning, facts and numbers " +
                    "exactly; do not summarize or add content.",
            )
            request.extraInstructions.takeIf { it.isNotBlank() }?.let { appendLine("Additional guidance: $it") }
        }
        if (request.tasks.shouldAutoName) {
            appendLine("title: a short, specific title of at most eight words, no trailing punctuation.")
        }
        if (request.tasks.shouldAutoLabel) {
            appendLine(
                "labels: zero to five short labels. Prefer these existing labels when they fit: " +
                    request.existingLabels.joinToString(", ").ifEmpty { "(none yet)" } + ".",
            )
        }
        if (request.tasks.shouldAutoCategorize) {
            appendLine(
                "category: exactly one of these categories, or null if none fits: " +
                    request.categoryNames.joinToString(", ") + ".",
            )
        }
    }

    /** Builds a strict JSON schema containing only the requested fields. */
    internal fun schemaFor(tasks: PostProcessingTasks): JsonObject {
        val properties = buildMap {
            if (tasks.shouldCleanup) put("cleaned_text", typeSchema("string"))
            if (tasks.shouldAutoName) put("title", typeSchema("string"))
            if (tasks.shouldAutoLabel) {
                put(
                    "labels",
                    buildJsonObject {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                    },
                )
            }
            if (tasks.shouldAutoCategorize) {
                put(
                    "category",
                    buildJsonObject {
                        put(
                            "type",
                            buildJsonArray {
                                add(stringValue("string"))
                                add(stringValue("null"))
                            },
                        )
                    },
                )
            }
        }
        return buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("required", buildJsonArray { properties.keys.forEach { add(stringValue(it)) } })
            put("additionalProperties", false)
        }
    }

    private fun typeSchema(type: String) = buildJsonObject { put("type", type) }

    private fun stringValue(value: String) = JsonPrimitive(value)
}
