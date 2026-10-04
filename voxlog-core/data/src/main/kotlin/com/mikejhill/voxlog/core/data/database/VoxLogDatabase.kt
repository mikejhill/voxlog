package com.mikejhill.voxlog.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikejhill.voxlog.core.data.database.dao.CategoryDao
import com.mikejhill.voxlog.core.data.database.dao.LabelDao
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.database.dao.SearchDao
import com.mikejhill.voxlog.core.data.database.entity.CategoryEntity
import com.mikejhill.voxlog.core.data.database.entity.LabelEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteEmbeddingEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteFtsEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteLabelCrossRef
import com.mikejhill.voxlog.core.model.CategoryId
import kotlinx.serialization.json.Json

/** The single on-device database. Schema JSON is exported to `schemas/` for migration tests. */
@Database(
    entities = [
        NoteEntity::class,
        CategoryEntity::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        NoteFtsEntity::class,
        NoteEmbeddingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(StringListConverter::class)
abstract class VoxLogDatabase : RoomDatabase() {
    /** Note access. */
    abstract fun noteDao(): NoteDao

    /** Category access. */
    abstract fun categoryDao(): CategoryDao

    /** Label access. */
    abstract fun labelDao(): LabelDao

    /** Full-text and vector search access. */
    abstract fun searchDao(): SearchDao

    /** Database file name and seeding callback. */
    companion object {
        /** File name inside the app's database directory. */
        const val FILE_NAME = "voxlog.db"

        /** Seeds the immutable Uncategorized category on first creation. */
        val SEED_CALLBACK: Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (id, name, color_argb, icon_name, is_system, sort_order, " +
                        "should_save_audio, default_label_ids, should_auto_cleanup, should_auto_label, " +
                        "should_auto_name) VALUES (?, 'Uncategorized', ?, 'inbox', 1, 0, 1, '[]', 0, 0, 0)",
                    arrayOf<Any>(CategoryId.UNCATEGORIZED.value, UNCATEGORIZED_COLOR_ARGB),
                )
            }
        }

        private const val UNCATEGORIZED_COLOR_ARGB = 0xFF78909CL
    }
}

/** Stores string lists as JSON arrays. */
class StringListConverter {
    /** Serializes [values] to a JSON array. */
    @TypeConverter
    fun toJson(values: List<String>): String = Json.encodeToString(values)

    /** Parses a JSON array produced by [toJson]. */
    @TypeConverter
    fun fromJson(json: String): List<String> = Json.decodeFromString(json)
}
