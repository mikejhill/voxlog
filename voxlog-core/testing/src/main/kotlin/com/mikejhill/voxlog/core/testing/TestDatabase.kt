package com.mikejhill.voxlog.core.testing

import android.content.Context
import androidx.room.Room
import com.mikejhill.voxlog.core.data.database.VoxLogDatabase
import kotlin.coroutines.CoroutineContext

/** Builds databases for tests. */
object TestDatabase {
    /**
     * An in-memory database seeded exactly like production (with the Uncategorized category).
     * Queries run on [queryContext] so tests can drive them with a virtual-time test dispatcher.
     */
    fun inMemory(context: Context, queryContext: CoroutineContext): VoxLogDatabase =
        Room.inMemoryDatabaseBuilder(context, VoxLogDatabase::class.java)
            .addCallback(VoxLogDatabase.SEED_CALLBACK)
            .setQueryCoroutineContext(queryContext)
            .allowMainThreadQueries()
            .build()
}
