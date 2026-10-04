package com.mikejhill.voxlog.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class DefaultNoteTitleTest {
    @Test
    fun `formats ISO-8601 with the capture-time offset truncated to seconds`() {
        val title = DefaultNoteTitle.forCapture(Instant.parse("2026-10-04T13:15:07.999Z"), ZoneOffset.ofHours(-5))

        assertThat(title).isEqualTo("2026-10-04T08:15:07-05:00")
    }

    @Test
    fun `uses the zone's offset at the capture instant across daylight saving changes`() {
        val chicago = ZoneId.of("America/Chicago")

        assertThat(DefaultNoteTitle.forCapture(Instant.parse("2026-07-01T12:00:00Z"), chicago)).endsWith("-05:00")
        assertThat(DefaultNoteTitle.forCapture(Instant.parse("2026-12-01T12:00:00Z"), chicago)).endsWith("-06:00")
    }

    @Test
    fun `renders UTC with a Z designator`() {
        assertThat(DefaultNoteTitle.forCapture(Instant.parse("2026-10-04T13:15:00Z"), ZoneOffset.UTC)).isEqualTo("2026-10-04T13:15:00Z")
    }
}
