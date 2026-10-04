package com.mikejhill.voxlog.engine.whisper

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavChunkReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun wavWithSamples(samples: ShortArray): File {
        val buffer = ByteBuffer.allocate(WavChunkReader.HEADER_SIZE_BYTES + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(WavChunkReader.HEADER_SIZE_BYTES)
        samples.forEach { buffer.putShort(it) }
        return File(temporaryFolder.root, "test.wav").apply { writeBytes(buffer.array()) }
    }

    @Test
    fun `converts 16-bit PCM to normalized floats`() {
        val file = wavWithSamples(shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, 16_384))

        val samples = WavChunkReader(file).readChunks().single()

        assertThat(samples[0]).isEqualTo(0f)
        assertThat(samples[1]).isWithin(1e-4f).of(1f)
        assertThat(samples[2]).isEqualTo(-1f)
        assertThat(samples[3]).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `splits long recordings into fixed-length chunks`() {
        val oneSecond = WhisperAudioFormat.SAMPLE_RATE_HZ
        val file = wavWithSamples(ShortArray(oneSecond * 5 / 2))

        val reader = WavChunkReader(file, chunkDurationSeconds = 1)
        val chunks = reader.readChunks().toList()

        assertThat(reader.chunkCount).isEqualTo(3)
        assertThat(chunks.map { it.size }).containsExactly(oneSecond, oneSecond, oneSecond / 2).inOrder()
        assertThat(reader.sampleCount).isEqualTo(oneSecond * 5L / 2)
    }

    @Test
    fun `header-only file yields no chunks`() {
        val reader = WavChunkReader(wavWithSamples(ShortArray(0)))

        assertThat(reader.readChunks().toList()).isEmpty()
        assertThat(reader.chunkCount).isEqualTo(0)
    }
}
