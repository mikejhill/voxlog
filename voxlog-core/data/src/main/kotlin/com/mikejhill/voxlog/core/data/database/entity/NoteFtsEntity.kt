package com.mikejhill.voxlog.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS4 index over note text. Room keeps it in sync with [NoteEntity] through
 * triggers, so it never needs to be written directly.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(val title: String, val text: String, @ColumnInfo(name = "raw_transcript") val rawTranscript: String?)
