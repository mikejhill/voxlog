package com.mikejhill.voxlog.engine.whisper

import java.io.File

/** Converts recorded speech into text entirely on device. */
interface SpeechTranscriber {
    /**
     * Transcribes a 16 kHz mono 16-bit PCM WAV file (see [WhisperAudioFormat]).
     *
     * @param modelFile the downloaded ggml model to use.
     * @param language ISO-639-1 code, or null to auto-detect (multilingual models only).
     * @param onPartialResult invoked after each chunk with the text so far, so callers can persist
     * partial transcripts of long recordings.
     * @param onProgress invoked with overall progress from 0 to 100.
     */
    suspend fun transcribe(
        audioFile: File,
        modelFile: File,
        language: String?,
        onPartialResult: suspend (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ): TranscriptionResult
}

/** Final speech-to-text output. */
data class TranscriptionResult(val text: String, val language: String?)

/** Thrown when the native engine cannot load a model or decode audio. */
class TranscriptionException(message: String) : Exception(message)

/** The PCM format VoxLog records in and Whisper consumes. */
object WhisperAudioFormat {
    /** Samples per second. */
    const val SAMPLE_RATE_HZ: Int = 16_000

    /** Mono. */
    const val CHANNEL_COUNT: Int = 1

    /** Signed 16-bit little-endian samples. */
    const val BITS_PER_SAMPLE: Int = 16

    /** Bytes of PCM data per second of audio. */
    const val BYTES_PER_SECOND: Int = SAMPLE_RATE_HZ * CHANNEL_COUNT * BITS_PER_SAMPLE / Byte.SIZE_BITS
}
