package com.mikejhill.voxlog.core.model

/**
 * A mutually exclusive bucket for notes. Every note belongs to exactly one category.
 *
 * @property isSystem true only for the immutable "Uncategorized" category, which cannot be
 * renamed or deleted but whose [settings] remain editable.
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val colorArgb: Long,
    val iconName: String,
    val isSystem: Boolean,
    val sortOrder: Int,
    val settings: CategorySettings,
)

/**
 * Per-category behavior. Capture-time settings ([shouldSaveAudio], [defaultLabelIds]) are applied
 * when a note is first saved into the category and are not re-applied when a note is moved.
 */
data class CategorySettings(
    val shouldSaveAudio: Boolean = true,
    val defaultLabelIds: Set<LabelId> = emptySet(),
    val shouldAutoCleanup: Boolean = false,
    val shouldAutoLabel: Boolean = false,
    val shouldAutoName: Boolean = false,
)

/** A free-form tag; a note may carry any number of labels across categories. */
data class Label(val id: LabelId, val name: String, val colorArgb: Long)
