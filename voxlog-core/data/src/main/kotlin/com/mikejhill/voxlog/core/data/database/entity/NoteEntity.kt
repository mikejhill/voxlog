package com.mikejhill.voxlog.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row form of a note. Timestamps are UTC epoch milliseconds.
 *
 * Capture-time category settings are snapshotted here ([shouldRetainAudio]) so that moving a note
 * between categories never changes how its original capture is processed.
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("category_id"), Index("created_at")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "capture_method") val captureMethod: String,
    val title: String,
    @ColumnInfo(name = "title_source") val titleSource: String,
    val text: String,
    @ColumnInfo(name = "raw_transcript") val rawTranscript: String?,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long?,
    @ColumnInfo(name = "audio_file_name") val audioFileName: String?,
    @ColumnInfo(name = "recording_file_name") val recordingFileName: String?,
    @ColumnInfo(name = "should_retain_audio") val shouldRetainAudio: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Float?,
    @ColumnInfo(name = "place_name") val placeName: String?,
    val language: String?,
    val status: String,
    @ColumnInfo(name = "transcription_progress") val transcriptionProgressPercent: Int?,
    @ColumnInfo(name = "capture_shortcut_id") val captureShortcutId: String?,
    @ColumnInfo(name = "processing_log") val processingLog: String?,
)
