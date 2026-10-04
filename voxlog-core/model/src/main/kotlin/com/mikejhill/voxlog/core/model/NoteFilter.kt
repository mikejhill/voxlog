package com.mikejhill.voxlog.core.model

/**
 * Narrows the set of visible notes. An empty filter matches every note.
 *
 * @property categoryId restricts to one category, or null for all categories.
 * @property labelIds restricts to notes carrying these labels, combined using [labelMatchMode].
 */
data class NoteFilter(
    val categoryId: CategoryId? = null,
    val labelIds: Set<LabelId> = emptySet(),
    val labelMatchMode: LabelMatchMode = LabelMatchMode.ANY,
) {
    /** Returns true when [note] satisfies every constraint in this filter. */
    fun matches(note: Note): Boolean {
        val isCategoryMatch = categoryId == null || note.categoryId == categoryId
        val isLabelMatch =
            when {
                labelIds.isEmpty() -> true
                labelMatchMode == LabelMatchMode.ANY -> note.labelIds.any { it in labelIds }
                else -> note.labelIds.containsAll(labelIds)
            }
        return isCategoryMatch && isLabelMatch
    }
}

/** How multiple selected labels combine in a [NoteFilter]. */
enum class LabelMatchMode {
    /** A note matches when it has at least one selected label. */
    ANY,

    /** A note matches only when it has every selected label. */
    ALL,
}
