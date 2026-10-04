package com.mikejhill.voxlog.core.model

import java.util.UUID

/** Stable identifier of a [Note]. */
@JvmInline
value class NoteId(val value: String) {
    /** Factory for new identifiers. */
    companion object {
        /** Creates a new random identifier. */
        fun random(): NoteId = NoteId(UUID.randomUUID().toString())
    }
}

/** Stable identifier of a [Category]. */
@JvmInline
value class CategoryId(val value: String) {
    /** Well-known identifiers and factory for new ones. */
    companion object {
        /** The immutable system category every note falls back to. */
        val UNCATEGORIZED: CategoryId = CategoryId("uncategorized")

        /** Creates a new random identifier. */
        fun random(): CategoryId = CategoryId(UUID.randomUUID().toString())
    }
}

/** Stable identifier of a [Label]. */
@JvmInline
value class LabelId(val value: String) {
    /** Factory for new identifiers. */
    companion object {
        /** Creates a new random identifier. */
        fun random(): LabelId = LabelId(UUID.randomUUID().toString())
    }
}
