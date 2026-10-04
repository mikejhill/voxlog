package com.mikejhill.voxlog.feature.notes.format

import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Locale-aware display formatting for note metadata. Storage stays UTC; display uses the device zone. */
object NoteFormatting {
    private const val MILLIS_PER_SECOND = 1000
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    /** `m:ss` or `h:mm:ss`. */
    fun duration(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /** "5 min. ago", "Yesterday", etc. */
    fun relativeTime(instant: Instant, now: Long): String = DateUtils.getRelativeTimeSpanString(
        instant.toEpochMilli(),
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

    /** Full local date and time, e.g. "Oct 4, 2026, 8:15:00 AM". */
    fun fullDateTime(instant: Instant, zone: ZoneId): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(zone).format(instant)
}
