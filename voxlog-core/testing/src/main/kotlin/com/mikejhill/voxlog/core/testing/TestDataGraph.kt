package com.mikejhill.voxlog.core.testing

import androidx.test.core.app.ApplicationProvider
import com.mikejhill.voxlog.core.data.database.VoxLogDatabase
import com.mikejhill.voxlog.core.data.repository.OfflineCategoryRepository
import com.mikejhill.voxlog.core.data.repository.OfflineLabelRepository
import com.mikejhill.voxlog.core.data.repository.OfflineNoteRepository
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineScope

/**
 * Real repositories over an in-memory database, with fakes for every side effect (scheduling,
 * location, settings). Shared by data-layer and ViewModel tests so they exercise production logic.
 */
class TestDataGraph(applicationScope: CoroutineScope) {
    val database: VoxLogDatabase = TestDatabase.inMemory(
        ApplicationProvider.getApplicationContext(),
        requireNotNull(applicationScope.coroutineContext[ContinuationInterceptor]),
    )
    val scheduler = FakeNoteProcessingScheduler()
    val settings = FakeSettingsRepository()
    val location = FakeLocationCapture()
    val audioFileStore = AudioFileStore(ApplicationProvider.getApplicationContext())

    val notes = OfflineNoteRepository(
        noteDao = database.noteDao(),
        categoryDao = database.categoryDao(),
        settingsRepository = settings,
        audioFileStore = audioFileStore,
        locationCapture = location,
        scheduler = scheduler,
        clock = TestTime.CLOCK,
        applicationScope = applicationScope,
    )
    val categories = OfflineCategoryRepository(database, scheduler, TestTime.CLOCK)
    val labels = OfflineLabelRepository(database)

    /** Closes the database. */
    fun close() = database.close()
}
