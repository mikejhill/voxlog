package com.mikejhill.voxlog.core.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mikejhill.voxlog.core.data.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/** Reads and writes categories. */
@Dao
interface CategoryDao {
    /** Streams categories in display order, system category first. */
    @Query("SELECT * FROM categories ORDER BY is_system DESC, sort_order ASC, name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    /** Returns one category, or null. */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: String): CategoryEntity?

    /** Returns all categories. */
    @Query("SELECT * FROM categories")
    suspend fun getCategories(): List<CategoryEntity>

    /** Inserts or replaces a category. */
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    /** Deletes a non-system category. System rows are protected by the WHERE clause. */
    @Query("DELETE FROM categories WHERE id = :id AND is_system = 0")
    suspend fun deleteUserCategory(id: String): Int
}
