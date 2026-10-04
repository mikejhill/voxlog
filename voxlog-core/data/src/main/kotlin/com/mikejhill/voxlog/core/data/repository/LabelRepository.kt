package com.mikejhill.voxlog.core.data.repository

import androidx.room.withTransaction
import com.mikejhill.voxlog.core.data.database.VoxLogDatabase
import com.mikejhill.voxlog.core.data.database.toDomain
import com.mikejhill.voxlog.core.data.database.toEntity
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Manages labels. Names are unique ignoring case. */
interface LabelRepository {
    /** Streams labels alphabetically. */
    fun observeLabels(): Flow<List<Label>>

    /** Returns all labels. */
    suspend fun getLabels(): List<Label>

    /** Returns the label named [name] (ignoring case), creating it if needed. */
    suspend fun getOrCreate(name: String): Label

    /** Renames or recolors a label. */
    suspend fun updateLabel(label: Label)

    /** Deletes a label from every note and every category default. */
    suspend fun deleteLabel(id: LabelId)
}

/** Room-backed [LabelRepository]. */
@Singleton
class OfflineLabelRepository @Inject constructor(private val database: VoxLogDatabase) : LabelRepository {
    private val labelDao = database.labelDao()

    override fun observeLabels(): Flow<List<Label>> = labelDao.observeLabels().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getLabels(): List<Label> = labelDao.getLabels().map { it.toDomain() }

    override suspend fun getOrCreate(name: String): Label {
        val trimmed = name.trim()
        labelDao.findByName(trimmed)?.let { return it.toDomain() }
        val label = Label(LabelId.random(), trimmed, PALETTE[(trimmed.hashCode() and Int.MAX_VALUE) % PALETTE.size])
        labelDao.upsert(label.toEntity())
        return label
    }

    override suspend fun updateLabel(label: Label) = labelDao.upsert(label.toEntity())

    override suspend fun deleteLabel(id: LabelId) {
        database.withTransaction {
            val categoryDao = database.categoryDao()
            categoryDao.getCategories()
                .filter { id.value in it.defaultLabelIds }
                .forEach { categoryDao.upsert(it.copy(defaultLabelIds = it.defaultLabelIds - id.value)) }
            labelDao.delete(id.value)
        }
    }

    private companion object {
        /** Material tonal colors used for new labels. */
        val PALETTE = listOf(
            0xFF5C6BC0L,
            0xFF26A69AL,
            0xFFEF6C00L,
            0xFFAB47BCL,
            0xFF66BB6AL,
            0xFFEC407AL,
            0xFF29B6F6L,
            0xFF8D6E63L,
        )
    }
}
