package com.mikejhill.voxlog.engine.whisper

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptCleanerTest {
    @Test
    fun `removes bracketed non-speech annotations`() {
        assertThat(TranscriptCleaner.removeNonSpeechTags(" [BLANK_AUDIO]")).isEqualTo(" ")
        assertThat(TranscriptCleaner.removeNonSpeechTags("Hello [MUSIC] world").trim()).isEqualTo("Hello world")
    }

    @Test
    fun `removes short parenthesized sound descriptions`() {
        assertThat(TranscriptCleaner.removeNonSpeechTags("Okay (wind blowing) let's go")).isEqualTo("Okay let's go")
    }

    @Test
    fun `keeps ordinary speech including capitalized parentheses`() {
        val text = "Call Dana (Mom's friend) at 3 [about the trip]"

        assertThat(TranscriptCleaner.removeNonSpeechTags(text)).isEqualTo(text)
    }
}
