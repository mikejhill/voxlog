package com.mikejhill.voxlog.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A note's semantic embedding. [textHash] identifies the text that was embedded so stale vectors
 * can be detected after edits; [vector] holds little-endian float32 values.
 */
@Entity(
    tableName = "note_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NoteEmbeddingEntity(
    @PrimaryKey @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "text_hash") val textHash: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vector: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is NoteEmbeddingEntity &&
        noteId == other.noteId &&
        modelId == other.modelId &&
        textHash == other.textHash &&
        vector.contentEquals(other.vector)

    override fun hashCode(): Int = noteId.hashCode() * HASH_PRIME + vector.contentHashCode()

    private companion object {
        const val HASH_PRIME = 31
    }
}
