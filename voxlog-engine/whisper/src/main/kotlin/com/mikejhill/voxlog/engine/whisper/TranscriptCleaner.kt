package com.mikejhill.voxlog.engine.whisper

/** Post-processing applied to raw Whisper output before it is stored. */
object TranscriptCleaner {
    /** Whisper's non-speech annotations, e.g. `[BLANK_AUDIO]`, `[MUSIC]`, `(wind blowing)`. */
    private val NON_SPEECH_TAG = Regex("""\[[A-Z _]+]|\((?:[a-z]+ ?){1,4}\)""")
    private val MULTIPLE_SPACES = Regex(" {2,}")

    /** Removes non-speech tags and collapses the whitespace they leave behind. */
    fun removeNonSpeechTags(text: String): String = text.replace(NON_SPEECH_TAG, "").replace(MULTIPLE_SPACES, " ")
}
