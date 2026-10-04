package com.mikejhill.voxlog.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Row form of a label. Names are unique case-insensitively. */
@Entity(tableName = "labels", indices = [Index(value = ["name"], unique = true)])
data class LabelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
)

/** Many-to-many link between notes and labels; rows vanish with either side. */
@Entity(
    tableName = "note_labels",
    primaryKeys = ["note_id", "label_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["id"],
            childColumns = ["label_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("label_id")],
)
data class NoteLabelCrossRef(@ColumnInfo(name = "note_id") val noteId: String, @ColumnInfo(name = "label_id") val labelId: String)
