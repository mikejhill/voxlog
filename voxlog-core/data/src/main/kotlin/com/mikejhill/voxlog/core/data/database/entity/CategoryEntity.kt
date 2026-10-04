package com.mikejhill.voxlog.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Row form of a category including its per-category settings. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "should_save_audio") val shouldSaveAudio: Boolean,
    @ColumnInfo(name = "default_label_ids") val defaultLabelIds: List<String>,
    @ColumnInfo(name = "should_auto_cleanup") val shouldAutoCleanup: Boolean,
    @ColumnInfo(name = "should_auto_label") val shouldAutoLabel: Boolean,
    @ColumnInfo(name = "should_auto_name") val shouldAutoName: Boolean,
)
