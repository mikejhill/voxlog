package com.mikejhill.voxlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.engine.whisper.WhisperSpeechTranscriber
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real whisper.cpp native library on a synthesized speech fixture. Downloads the tiny
 * English model on first run (≈32 MB, checksum-verified), so the device needs network access.
 */
@RunWith(AndroidJUnit4::class)
class WhisperTranscriptionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun transcribesSpeechFixtureOnDevice() = runBlocking {
        val model = ModelRepository(AudioFileStore(context)).ensureDownloaded(ModelCatalog.speechModel("tiny.en").file)
        val audio = File(context.cacheDir, "speech-fixture.wav")
        testContext.assets.open("speech-fixture.wav").use { input -> audio.outputStream().use { input.copyTo(it) } }
        val progress = mutableListOf<Int>()

        val result = WhisperSpeechTranscriber().transcribe(audio, model, language = "en", onProgress = { progress += it })

        val text = result.text.lowercase()
        assertThat(text).contains("blood pressure")
        assertThat(text).contains("fox")
        assertThat(progress.last()).isEqualTo(100)
    }
}
