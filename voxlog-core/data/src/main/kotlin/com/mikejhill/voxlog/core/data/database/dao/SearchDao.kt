package com.mikejhill.voxlog.core.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikejhill.voxlog.core.data.database.entity.NoteEmbeddingEntity

/** One full-text hit with the raw FTS4 `matchinfo('pcnalx')` blob used for BM25 ranking. */
data class FullTextMatch(@ColumnInfo(name = "note_id") val noteId: String, @ColumnInfo(name = "match_info") val matchInfo: ByteArray) {
    override fun equals(other: Any?): Boolean = other is FullTextMatch && noteId == other.noteId && matchInfo.contentEquals(other.matchInfo)

    override fun hashCode(): Int = noteId.hashCode()
}

/** Full-text and vector lookups backing hybrid search. */
@Dao
interface SearchDao {
    /** Runs an FTS4 MATCH query over title, text and raw transcript. */
    @Query(
        "SELECT notes.id AS note_id, matchinfo(notes_fts, 'pcnalx') AS match_info FROM notes " +
            "JOIN notes_fts ON notes.rowid = notes_fts.rowid WHERE notes_fts MATCH :matchExpression",
    )
    suspend fun matchFullText(matchExpression: String): List<FullTextMatch>

    /** Returns all embeddings produced by [modelId]. */
    @Query("SELECT * FROM note_embeddings WHERE model_id = :modelId")
    suspend fun getEmbeddings(modelId: String): List<NoteEmbeddingEntity>

    /** Returns the stored embedding for a note, if any. */
    @Query("SELECT * FROM note_embeddings WHERE note_id = :noteId")
    suspend fun getEmbedding(noteId: String): NoteEmbeddingEntity?

    /** Inserts or replaces a note's embedding. */
    @Upsert
    suspend fun upsertEmbedding(embedding: NoteEmbeddingEntity)
}
