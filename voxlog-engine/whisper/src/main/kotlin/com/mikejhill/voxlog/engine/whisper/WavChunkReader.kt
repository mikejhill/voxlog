package com.mikejhill.voxlog.engine.whisper

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a canonical 44-byte-header PCM WAV file in fixed-length chunks of normalized float samples,
 * so arbitrarily long recordings never need to fit in memory at once.
 */
class WavChunkReader(private val file: File, private val chunkDurationSeconds: Int = DEFAULT_CHUNK_SECONDS) {
    /** Total number of samples in the file's data section. */
    val sampleCount: Long
        get() = ((file.length() - HEADER_SIZE_BYTES).coerceAtLeast(0)) / BYTES_PER_SAMPLE

    /** Number of chunks [readChunks] will produce. */
    val chunkCount: Int
        get() {
            val samplesPerChunk = samplesPerChunk().toLong()
            return ((sampleCount + samplesPerChunk - 1) / samplesPerChunk).toInt()
        }

    /** Yields successive chunks of samples scaled to [-1, 1]. */
    fun readChunks(): Sequence<FloatArray> = sequence {
        RandomAccessFile(file, "r").use { input ->
            input.seek(HEADER_SIZE_BYTES.toLong())
            val buffer = ByteArray(samplesPerChunk() * BYTES_PER_SAMPLE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                yield(toFloatSamples(buffer, bytesRead))
            }
        }
    }

    private fun samplesPerChunk(): Int = chunkDurationSeconds * WhisperAudioFormat.SAMPLE_RATE_HZ

    /** Constants and the PCM-to-float conversion. */
    companion object {
        /** Size of the canonical RIFF/WAVE header written by VoxLog's recorder. */
        const val HEADER_SIZE_BYTES: Int = 44

        /** Five-minute chunks keep memory under ~20 MB while giving Whisper ample context. */
        const val DEFAULT_CHUNK_SECONDS: Int = 300

        private const val BYTES_PER_SAMPLE = 2
        private const val PCM_16_SCALE = 32_768f

        /** Converts little-endian 16-bit PCM bytes into floats in [-1, 1]. */
        fun toFloatSamples(bytes: ByteArray, length: Int): FloatArray {
            val shorts = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return FloatArray(shorts.remaining()) { index -> shorts.get(index) / PCM_16_SCALE }
        }
    }
}
