package com.mikejhill.voxlog.core.data.repository

import androidx.room.withTransaction
import com.mikejhill.voxlog.core.data.database.VoxLogDatabase
import com.mikejhill.voxlog.core.data.database.toDomain
import com.mikejhill.voxlog.core.data.database.toEntity
import com.mikejhill.voxlog.core.data.pipeline.NoteProcessingScheduler
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.CategorySettings
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Manages categories. The system "Uncategorized" category can be edited but never renamed or deleted. */
interface CategoryRepository {
    /** Streams categories in display order. */
    fun observeCategories(): Flow<List<Category>>

    /** Returns one category, or null. */
    suspend fun getCategory(id: CategoryId): Category?

    /** Creates a category with default settings and returns it. */
    suspend fun createCategory(name: String, colorArgb: Long, iconName: String): Category

    /** Saves name, color, icon and settings changes. Name changes to the system category are ignored. */
    suspend fun updateCategory(category: Category)

    /** Deletes a user category, moving its notes to Uncategorized. */
    suspend fun deleteCategory(id: CategoryId)
}

/** Room-backed [CategoryRepository]. */
@Singleton
class OfflineCategoryRepository @Inject constructor(
    private val database: VoxLogDatabase,
    private val scheduler: NoteProcessingScheduler,
    private val clock: Clock,
) : CategoryRepository {
    private val categoryDao = database.categoryDao()

    override fun observeCategories(): Flow<List<Category>> = categoryDao.observeCategories().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCategory(id: CategoryId): Category? = categoryDao.getCategory(id.value)?.toDomain()

    override suspend fun createCategory(name: String, colorArgb: Long, iconName: String): Category {
        val nextSortOrder = (categoryDao.getCategories().maxOfOrNull { it.sortOrder } ?: 0) + 1
        val category = Category(
            id = CategoryId.random(),
            name = name.trim(),
            colorArgb = colorArgb,
            iconName = iconName,
            isSystem = false,
            sortOrder = nextSortOrder,
            settings = CategorySettings(),
        )
        categoryDao.upsert(category.toEntity())
        return category
    }

    override suspend fun updateCategory(category: Category) {
        val existing = categoryDao.getCategory(category.id.value)?.toDomain() ?: return
        val sanitized = if (existing.isSystem) {
            category.copy(name = existing.name, isSystem = true)
        } else {
            category.copy(name = category.name.trim(), isSystem = false)
        }
        categoryDao.upsert(sanitized.toEntity())
        scheduler.scheduleSync()
    }

    override suspend fun deleteCategory(id: CategoryId) {
        if (id == CategoryId.UNCATEGORIZED) return
        database.withTransaction {
            database.noteDao().moveAllToCategory(id.value, CategoryId.UNCATEGORIZED.value, clock.millis())
            categoryDao.deleteUserCategory(id.value)
        }
        scheduler.scheduleSync()
    }
}
