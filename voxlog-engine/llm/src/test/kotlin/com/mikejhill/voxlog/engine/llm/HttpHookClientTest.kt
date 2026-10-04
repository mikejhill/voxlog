package com.mikejhill.voxlog.engine.llm

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpHookClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val payload = HookPayload(
        event = HookPayload.EVENT_NOTE_CREATED,
        note = HookNote(
            id = "id",
            title = "Title",
            text = "Text",
            rawTranscript = "raw",
            category = "Journal",
            labels = listOf("a"),
            captureMethod = "VOICE_RECORDING",
            createdAt = "2026-10-04T13:15:00Z",
            durationMillis = 1000,
            latitude = null,
            longitude = null,
        ),
    )

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun endpoint(shouldIncludeAudio: Boolean = false, headers: Map<String, String> = emptyMap()) =
        HookEndpoint(server.url("/hook").toString(), headers, shouldIncludeAudio)

    @Test
    fun `posts versioned JSON with custom headers and returns the patch`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"title":"New","add_labels":["x"]}""").build())

        val patch = HttpHookClient().invoke(endpoint(headers = mapOf("X-Token" to "t")), payload, null)

        assertThat(patch).isEqualTo(HookPatch(title = "New", addLabels = listOf("x")))
        val request = server.takeRequest()
        val body = request.body!!.utf8()
        assertThat(request.headers["X-Token"]).isEqualTo("t")
        assertThat(body).contains(""""schema_version":1""")
        assertThat(body).contains(""""event":"note.created"""")
    }

    @Test
    fun `empty response body means no changes`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertThat(HttpHookClient().invoke(endpoint(), payload, null)).isNull()
    }

    @Test
    fun `audio is sent as multipart only when the endpoint asks for it`() = runTest {
        val audio = File(temporaryFolder.root, "a.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        server.enqueue(MockResponse.Builder().build())
        server.enqueue(MockResponse.Builder().build())

        HttpHookClient().invoke(endpoint(shouldIncludeAudio = true), payload, audio)
        HttpHookClient().invoke(endpoint(shouldIncludeAudio = false), payload, audio)

        assertThat(server.takeRequest().headers["Content-Type"]).startsWith("multipart/form-data")
        assertThat(server.takeRequest().headers["Content-Type"]).startsWith("application/json")
    }
}
