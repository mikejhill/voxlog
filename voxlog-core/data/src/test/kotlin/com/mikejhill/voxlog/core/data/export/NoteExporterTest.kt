package com.mikejhill.voxlog.core.data.export

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.testing.TestDataGraph
import com.mikejhill.voxlog.core.testing.TestTime
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteExporterTest {
    private val scope = TestScope()
    private val graph = TestDataGraph(scope)
    private val exporter = NoteExporter(
        graph.database.noteDao(),
        graph.database.categoryDao(),
        graph.database.labelDao(),
        graph.audioFileStore,
        TestTime.CLOCK,
    )

    @After
    fun tearDown() = graph.close()

    @Test
    fun `zip contains json, csv and retained audio`() = scope.runTest {
        val id = graph.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "Exported", "body"))
        val row = graph.database.noteDao().getNote(id.value)!!.note
        graph.database.noteDao().update(row.copy(audioFileName = "clip.m4a"))
        graph.audioFileStore.audioFile("clip.m4a").writeBytes(byteArrayOf(7, 7, 7))
        val output = ByteArrayOutputStream()

        exporter.writeZip(output)

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes() }
        }
        assertThat(entries.keys).containsExactly("notes.json", "notes.csv", "audio/clip.m4a")
        assertThat(entries.getValue("notes.json").decodeToString()).contains("\"audio_file\": \"audio/clip.m4a\"")
        assertThat(entries.getValue("notes.csv").decodeToString()).contains("Exported")
        assertThat(entries.getValue("audio/clip.m4a").toList()).containsExactly(7.toByte(), 7.toByte(), 7.toByte())
    }

    @Test
    fun `in-progress recordings are excluded and categories are named`() = scope.runTest {
        val journal = graph.categories.createCategory("Journal", 0, "book")
        graph.notes.createTextNote(TextNoteDraft(journal.id, "Kept", "x"))
        graph.notes.beginVoiceNote(
            com.mikejhill.voxlog.core.data.repository.VoiceCaptureRequest(com.mikejhill.voxlog.core.model.NoteId.random(), journal.id),
        )

        val document = exporter.buildDocument()

        assertThat(document.notes.map { it.title }).containsExactly("Kept")
        assertThat(document.notes.single().category).isEqualTo("Journal")
        assertThat(document.categories).containsExactly("Uncategorized", "Journal").inOrder()
        assertThat(document.exportedAt).isEqualTo("2026-10-04T13:15:00Z")
    }
}
