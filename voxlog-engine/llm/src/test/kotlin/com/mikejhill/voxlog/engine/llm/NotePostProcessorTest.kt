package com.mikejhill.voxlog.engine.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Test

class NotePostProcessorTest {
    private class RecordingClient(private val reply: String) : LlmClient {
        var calls = 0
        var lastSystemPrompt = ""

        override suspend fun completeJson(systemPrompt: String, userPrompt: String, schema: JsonObject): String {
            calls++
            lastSystemPrompt = systemPrompt
            return reply
        }

        override suspend fun listModels(): List<String> = emptyList()
    }

    private fun request(tasks: PostProcessingTasks) = PostProcessingRequest(
        text = "um so the meeting is at three",
        tasks = tasks,
        existingLabels = listOf("work"),
        categoryNames = listOf("Journal", "Work"),
        extraInstructions = "British spelling",
    )

    @Test
    fun `no enabled task means no network call`() = runTest {
        val client = RecordingClient("{}")

        val result = NotePostProcessor(client).process(request(PostProcessingTasks(false, false, false, false)))

        assertThat(client.calls).isEqualTo(0)
        assertThat(result).isEqualTo(PostProcessingResult())
    }

    @Test
    fun `all tasks share one call and parse into one result`() = runTest {
        val reply = """{"cleaned_text":"The meeting is at three.","title":"Meeting time","labels":["work"],"category":"Work"}"""
        val client = RecordingClient(reply)

        val result = NotePostProcessor(client).process(request(PostProcessingTasks(true, true, true, true)))

        assertThat(client.calls).isEqualTo(1)
        assertThat(result.cleanedText).isEqualTo("The meeting is at three.")
        assertThat(result.title).isEqualTo("Meeting time")
        assertThat(result.labels).containsExactly("work")
        assertThat(result.category).isEqualTo("Work")
    }

    @Test
    fun `schema contains only requested fields and all are required`() {
        val schema = NotePostProcessor(RecordingClient("{}")).schemaFor(PostProcessingTasks(true, false, false, true))

        assertThat(schema["properties"]!!.jsonObject.keys).containsExactly("cleaned_text", "title")
        assertThat(schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactly("cleaned_text", "title")
        assertThat(schema["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
    }

    @Test
    fun `prompt lists existing labels, categories and extra guidance`() = runTest {
        val client = RecordingClient("{}")

        NotePostProcessor(client).process(request(PostProcessingTasks(true, true, true, false)))

        assertThat(client.lastSystemPrompt).contains("work")
        assertThat(client.lastSystemPrompt).contains("Journal, Work")
        assertThat(client.lastSystemPrompt).contains("British spelling")
        assertThat(client.lastSystemPrompt).doesNotContain("title:")
    }

    @Test
    fun `invalid JSON is a retryable failure`() {
        val processor = NotePostProcessor(RecordingClient("not json"))

        val exception = assertThrows(LlmException::class.java) {
            runBlocking { processor.process(request(PostProcessingTasks(true, false, false, false))) }
        }

        assertThat(exception.isRetryable).isTrue()
    }
}
