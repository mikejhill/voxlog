package com.mikejhill.voxlog.engine.whisper

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SpeechTranscriber] backed by whisper.cpp. Audio is decoded in [WavChunkReader] chunks; the tail
 * of each chunk's text is fed to the next as an initial prompt to keep sentences coherent.
 * Calls are serialized because a whisper context is not thread-safe.
 */
class WhisperSpeechTranscriber(private val threadCount: Int = defaultThreadCount()) : SpeechTranscriber {
    private val mutex = Mutex()
    private var loadedModelPath: String? = null
    private var contextPointer: Long = 0

    override suspend fun transcribe(
        audioFile: File,
        modelFile: File,
        language: String?,
        onPartialResult: suspend (String) -> Unit,
        onProgress: (Int) -> Unit,
    ): TranscriptionResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val context = ensureContext(modelFile)
            val reader = WavChunkReader(audioFile)
            val chunkCount = reader.chunkCount.coerceAtLeast(1)
            val text = StringBuilder()
            var detectedLanguage: String? = language
            reader.readChunks().forEachIndexed { chunkIndex, samples ->
                ensureActive()
                val listener = NativeProgressListener { percent ->
                    onProgress((chunkIndex * PERCENT + percent) / chunkCount)
                }
                val prompt = text.takeLast(PROMPT_CONTEXT_CHARS).toString().ifBlank { null }
                val output = WhisperNative.transcribe(context, samples, language, prompt, threadCount, listener)
                val chunkText = output[0] ?: throw TranscriptionException("Decoding failed at chunk $chunkIndex")
                detectedLanguage = detectedLanguage ?: output[1]
                text.append(TranscriptCleaner.removeNonSpeechTags(chunkText))
                onPartialResult(text.toString().trim())
            }
            onProgress(PERCENT)
            TranscriptionResult(text.toString().trim(), detectedLanguage)
        }
    }

    private fun ensureContext(modelFile: File): Long {
        if (loadedModelPath == modelFile.absolutePath && contextPointer != 0L) return contextPointer
        if (contextPointer != 0L) WhisperNative.freeContext(contextPointer)
        contextPointer = WhisperNative.initContext(modelFile.absolutePath)
        if (contextPointer == 0L) {
            loadedModelPath = null
            throw TranscriptionException("Could not load speech model ${modelFile.name}")
        }
        loadedModelPath = modelFile.absolutePath
        return contextPointer
    }

    private companion object {
        const val PERCENT = 100
        const val PROMPT_CONTEXT_CHARS = 200
        const val MAX_THREADS = 4

        /** Uses the big cores without starving the UI: at most four threads. */
        fun defaultThreadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)
    }
}
