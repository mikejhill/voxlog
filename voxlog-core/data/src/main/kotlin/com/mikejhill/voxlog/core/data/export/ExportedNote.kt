package com.mikejhill.voxlog.core.data.export

import com.mikejhill.voxlog.core.data.database.dao.NoteWithLabels
import com.mikejhill.voxlog.core.data.database.entity.CategoryEntity
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Versioned export document. Field names are part of the public export format (docs/usage/export.md). */
@Serializable
data class ExportDocument(
    @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("exported_at") val exportedAt: String,
    val categories: List<String>,
    val labels: List<String>,
    val notes: List<ExportedNote>,
) {
    /** Format constants. */
    companion object {
        /** Bumped on any incompatible change to the export format. */
        const val SCHEMA_VERSION: Int = 1
    }
}

/** One note in an export. Timestamps are ISO-8601 UTC; [audioFile] is relative to the export root. */
@Serializable
data class ExportedNote(
    val id: String,
    val title: String,
    val text: String,
    @SerialName("raw_transcript") val rawTranscript: String?,
    val category: String,
    val labels: List<String>,
    @SerialName("capture_method") val captureMethod: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("duration_millis") val durationMillis: Long?,
    @SerialName("audio_file") val audioFile: String?,
    val latitude: Double?,
    val longitude: Double?,
    @SerialName("place_name") val placeName: String?,
    val language: String?,
) {
    /** Conversion from database rows. */
    companion object {
        /** Column order for CSV exports. */
        val CSV_HEADER: List<String> = listOf(
            "id", "title", "text", "raw_transcript", "category", "labels", "capture_method", "created_at",
            "updated_at", "duration_millis", "audio_file", "latitude", "longitude", "place_name", "language",
        )

        /** Builds an export row from a note and the category lookup. */
        fun from(row: NoteWithLabels, categoriesById: Map<String, CategoryEntity>): ExportedNote = with(row.note) {
            ExportedNote(
                id = id,
                title = title,
                text = text,
                rawTranscript = rawTranscript,
                category = categoriesById[categoryId]?.name ?: categoryId,
                labels = row.labels.map { it.name }.sorted(),
                captureMethod = captureMethod,
                createdAt = Instant.ofEpochMilli(createdAtEpochMillis).toString(),
                updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis).toString(),
                durationMillis = durationMillis,
                audioFile = audioFileName?.let { "audio/$it" },
                latitude = latitude,
                longitude = longitude,
                placeName = placeName,
                language = language,
            )
        }
    }

    /** Values in [CSV_HEADER] order. */
    fun csvValues(): List<String> = listOf(
        id, title, text, rawTranscript.orEmpty(), category, labels.joinToString(";"), captureMethod, createdAt,
        updatedAt, durationMillis?.toString().orEmpty(), audioFile.orEmpty(), latitude?.toString().orEmpty(),
        longitude?.toString().orEmpty(), placeName.orEmpty(), language.orEmpty(),
    )
}
