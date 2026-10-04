package com.mikejhill.voxlog.core.data.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.mikejhill.voxlog.core.data.database.entity.LabelEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteLabelCrossRef
import kotlinx.coroutines.flow.Flow

/** A note row together with its labels. */
data class NoteWithLabels(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteLabelCrossRef::class, parentColumn = "note_id", entityColumn = "label_id"),
    )
    val labels: List<LabelEntity>,
)

/** Reads and writes notes and their label links. */
@Dao
interface NoteDao {
    /** Streams notes newest first, optionally restricted to one category. */
    @Transaction
    @Query(
        "SELECT * FROM notes WHERE (:categoryId IS NULL OR category_id = :categoryId) " +
            "AND status != 'RECORDING' ORDER BY created_at DESC",
    )
    fun observeNotes(categoryId: String?): Flow<List<NoteWithLabels>>

    /** Streams a single note, emitting null once it is deleted. */
    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: String): Flow<NoteWithLabels?>

    /** Returns a single note, or null. */
    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: String): NoteWithLabels?

    /** Returns every note with labels, oldest first; used by export and sync. */
    @Transaction
    @Query("SELECT * FROM notes ORDER BY created_at ASC")
    suspend fun getAllNotes(): List<NoteWithLabels>

    /** Returns the notes with the given ids in no particular order. */
    @Transaction
    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getNotes(ids: List<String>): List<NoteWithLabels>

    /** Returns notes whose status is one of [statuses]; used for crash recovery. */
    @Query("SELECT * FROM notes WHERE status IN (:statuses)")
    suspend fun getNotesWithStatus(statuses: List<String>): List<NoteEntity>

    /** Inserts a new note row. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    /** Replaces an existing note row. */
    @Update
    suspend fun update(note: NoteEntity)

    /** Deletes a note; label links and embeddings cascade. */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    /** Moves every note in [fromCategoryId] to [toCategoryId]. */
    @Query("UPDATE notes SET category_id = :toCategoryId, updated_at = :updatedAt WHERE category_id = :fromCategoryId")
    suspend fun moveAllToCategory(fromCategoryId: String, toCategoryId: String, updatedAt: Long)

    /** Records transcription progress without touching other columns. */
    @Query("UPDATE notes SET transcription_progress = :progressPercent WHERE id = :id")
    suspend fun updateTranscriptionProgress(id: String, progressPercent: Int)

    /** Adds label links, ignoring ones that already exist. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabelLinks(links: List<NoteLabelCrossRef>)

    /** Removes every label link of a note. */
    @Query("DELETE FROM note_labels WHERE note_id = :noteId")
    suspend fun deleteLabelLinks(noteId: String)

    /** Replaces the label set of a note atomically. */
    @Transaction
    suspend fun replaceLabels(noteId: String, labelIds: Collection<String>) {
        deleteLabelLinks(noteId)
        insertLabelLinks(labelIds.map { NoteLabelCrossRef(noteId, it) })
    }
}
