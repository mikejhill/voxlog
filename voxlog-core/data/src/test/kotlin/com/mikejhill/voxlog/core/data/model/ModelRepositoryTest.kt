package com.mikejhill.voxlog.core.data.model

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.model.ModelFile
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelRepositoryTest {
    private val server = MockWebServer()
    private val repository = ModelRepository(AudioFileStore(ApplicationProvider.getApplicationContext()))
    private val content = ByteArray(10_000) { (it % 251).toByte() }
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun model(name: String, checksum: String = sha256) =
        ModelFile(name, server.url("/$name").toString(), checksum, content.size.toLong())

    @Test
    fun `downloads, verifies and reports ready`() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(content)).build())
        val model = model("good.bin")

        val file = repository.ensureDownloaded(model)

        assertThat(file.readBytes()).isEqualTo(content)
        assertThat(repository.stateOf(model)).isEqualTo(ModelFileState.Ready)
        assertThat(repository.isAvailable(model)).isTrue()
    }

    @Test
    fun `a checksum mismatch is rejected and nothing is kept`() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(content)).build())
        val model = model("tampered.bin", checksum = "0".repeat(64))

        assertThrows(IOException::class.java) { runBlocking { repository.ensureDownloaded(model) } }

        assertThat(repository.isAvailable(model)).isFalse()
        assertThat(repository.stateOf(model)).isInstanceOf(ModelFileState.Failed::class.java)
        assertThat(repository.fileFor(model).parentFile!!.listFiles()!!.map { it.name }).doesNotContain("tampered.bin.part")
    }

    @Test
    fun `an existing file is not downloaded again and can be deleted`() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(content)).build())
        val model = model("once.bin")
        repository.ensureDownloaded(model)

        repository.ensureDownloaded(model)
        assertThat(server.requestCount).isEqualTo(1)

        repository.delete(model)
        assertThat(repository.stateOf(model)).isEqualTo(ModelFileState.Missing)
    }

    @Test
    fun `http errors fail the download`() {
        server.enqueue(MockResponse.Builder().code(404).build())

        assertThrows(IOException::class.java) { runBlocking { repository.ensureDownloaded(model("missing.bin")) } }
    }
}
