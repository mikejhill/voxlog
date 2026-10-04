package com.mikejhill.voxlog.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikejhill.voxlog.core.data.database.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes labels. */
@Dao
interface LabelDao {
    /** Streams labels alphabetically. */
    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE ASC")
    fun observeLabels(): Flow<List<LabelEntity>>

    /** Returns all labels. */
    @Query("SELECT * FROM labels")
    suspend fun getLabels(): List<LabelEntity>

    /** Finds a label by name, ignoring case. */
    @Query("SELECT * FROM labels WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): LabelEntity?

    /** Inserts or replaces a label. */
    @Upsert
    suspend fun upsert(label: LabelEntity)

    /** Deletes a label; links to notes cascade. */
    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: String)
}
