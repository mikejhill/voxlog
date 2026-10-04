package com.mikejhill.voxlog.engine.whisper

/** Receives whisper.cpp progress (0–100) for the chunk being decoded. Called from native code. */
fun interface NativeProgressListener {
    /** Reports [percent] complete for the current chunk. */
    fun onProgress(percent: Int)
}

/** JNI surface of `libvoxlog_whisper.so`; see `src/main/cpp/voxlog_whisper_jni.cpp`. */
internal object WhisperNative {
    init {
        System.loadLibrary("voxlog_whisper")
    }

    /** Loads a ggml model and returns an opaque context pointer, or 0 on failure. */
    external fun initContext(modelPath: String): Long

    /** Releases a context created by [initContext]. */
    external fun freeContext(contextPointer: Long)

    /**
     * Decodes [samples] (16 kHz mono floats) and returns `[text, detectedLanguage]`.
     * Elements are null when decoding failed.
     */
    @Suppress("LongParameterList")
    external fun transcribe(
        contextPointer: Long,
        samples: FloatArray,
        language: String?,
        initialPrompt: String?,
        threadCount: Int,
        progressListener: NativeProgressListener?,
    ): Array<String?>

    /** Returns the CPU features whisper.cpp was compiled with, for diagnostics. */
    external fun systemInfo(): String
}
