package com.mikejhill.voxlog.core.data.sync

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.export.NoteExporter
import com.mikejhill.voxlog.core.testing.TestDataGraph
import com.mikejhill.voxlog.core.testing.TestTime
import java.io.IOException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderSyncerTest {
    private val scope = TestScope()
    private val graph = TestDataGraph(scope)
    private val syncer = FolderSyncer(
        ApplicationProvider.getApplicationContext(),
        graph.settings,
        NoteExporter(
            graph.database.noteDao(),
            graph.database.categoryDao(),
            graph.database.labelDao(),
            graph.audioFileStore,
            TestTime.CLOCK,
        ),
        graph.audioFileStore,
    )

    @After
    fun tearDown() = graph.close()

    @Test
    fun `sync without a configured folder is a no-op`() = scope.runTest {
        syncer.syncAll()
        assertThat(graph.scheduler.calls).isEmpty()
    }

    @Test
    fun `inaccessible folder fails explicitly instead of silently dropping the export`() = scope.runTest {
        graph.settings.update { it.copy(syncFolderUri = "content://missing.provider/tree/notes") }
        val failure = runCatching { syncer.syncAll() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IOException::class.java)
        assertThat(failure?.message).isEqualTo("Sync folder is not writable")
    }
}
