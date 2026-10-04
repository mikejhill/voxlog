package com.mikejhill.voxlog.engine.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleLlmClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun client(apiKey: String? = "sk-test") = OpenAiCompatibleLlmClient(server.url("/v1").toString(), apiKey, "test-model")

    @Test
    fun `sends a strict json_schema chat completion and returns the message content`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"choices":[{"message":{"content":"{\"title\":\"x\"}"}}]}""").build())

        val reply = client().completeJson("system", "user", buildJsonObject { put("type", "object") })

        assertThat(reply).isEqualTo("""{"title":"x"}""")
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/chat/completions")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer sk-test")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("test-model")
        assertThat(body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
    }

    @Test
    fun `omits authorization for keyless local endpoints and sorts models`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"data":[{"id":"b"},{"id":"a"}]}""").build())

        val models = client(apiKey = null).listModels()

        assertThat(models).containsExactly("a", "b").inOrder()
        assertThat(server.takeRequest().headers["Authorization"]).isNull()
    }

    @Test
    fun `rate limits are retryable and client errors are not`() {
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(MockResponse.Builder().code(400).build())
        val schema = buildJsonObject {}

        val rateLimited = assertThrows(LlmException::class.java) { runBlocking { client().completeJson("s", "u", schema) } }
        val badRequest = assertThrows(LlmException::class.java) { runBlocking { client().completeJson("s", "u", schema) } }

        assertThat(rateLimited.isRetryable).isTrue()
        assertThat(badRequest.isRetryable).isFalse()
    }
}
