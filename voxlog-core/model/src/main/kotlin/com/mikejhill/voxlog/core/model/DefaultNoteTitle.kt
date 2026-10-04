package com.mikejhill.voxlog.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Builds the default title for a new note: ISO-8601 with the capture-time UTC offset. */
object DefaultNoteTitle {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * Formats [capturedAt] in [zone], truncated to whole seconds,
     * e.g. `2026-10-04T08:15:00-05:00`.
     */
    fun forCapture(capturedAt: Instant, zone: ZoneId): String = capturedAt
        .truncatedTo(ChronoUnit.SECONDS)
        .atZone(zone)
        .toOffsetDateTime()
        .format(FORMATTER)
}
