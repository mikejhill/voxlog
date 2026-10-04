package com.mikejhill.voxlog.core.data.export

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.sync.FolderSyncer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ExportFormatterTest {
    private val note = ExportedNote(
        id = "1a2b3c4d-0000-0000-0000-000000000000",
        title = "Grocery list, \"urgent\"",
        text = "milk\neggs",
        rawTranscript = null,
        category = "Errands",
        labels = listOf("home", "shopping"),
        captureMethod = "VOICE_RECORDING",
        createdAt = "2026-10-04T13:15:00Z",
        updatedAt = "2026-10-04T13:16:00Z",
        durationMillis = 4_000,
        audioFile = "audio/1a2b.m4a",
        latitude = 1.5,
        longitude = -2.5,
        placeName = null,
        language = "en",
    )

    @Test
    fun `csv quotes fields with commas, quotes and newlines and names the audio file`() {
        val lines = ExportFormatter.toCsv(listOf(note)).split("\r\n")

        assertThat(lines[0]).startsWith("id,title,text,")
        assertThat(lines[0]).contains("audio_file")
        assertThat(lines[1]).contains("\"Grocery list, \"\"urgent\"\"\"")
        assertThat(lines[1]).contains("\"milk\neggs\"")
        assertThat(lines[1]).contains("home;shopping")
        assertThat(lines[1]).contains("audio/1a2b.m4a")
    }

    @Test
    fun `json export is versioned and includes the audio file`() {
        val document =
            ExportDocument(exportedAt = "2026-10-04T14:00:00Z", categories = listOf("Errands"), labels = emptyList(), notes = listOf(note))

        val json = Json.parseToJsonElement(ExportFormatter.toJson(document)).jsonObject

        assertThat(json["schema_version"]!!.jsonPrimitive.content).isEqualTo("1")
        val exported = json["notes"]!!.jsonArray.single().jsonObject
        assertThat(exported["audio_file"]!!.jsonPrimitive.content).isEqualTo("audio/1a2b.m4a")
        assertThat(exported["created_at"]!!.jsonPrimitive.content).isEqualTo("2026-10-04T13:15:00Z")
    }

    @Test
    fun `markdown has yaml front matter with escaped strings and the body`() {
        val markdown = ExportFormatter.toMarkdown(note)

        assertThat(markdown).startsWith("---\nid: 1a2b3c4d")
        assertThat(markdown).contains("title: \"Grocery list, \\\"urgent\\\"\"")
        assertThat(markdown).contains("labels: [\"home\", \"shopping\"]")
        assertThat(markdown).contains("location: [1.5, -2.5]")
        assertThat(markdown).endsWith("---\n\nmilk\neggs\n")
    }

    @Test
    fun `sync file paths are dated, slugged and carry an id suffix`() {
        assertThat(FolderSyncer.relativePathFor(note)).isEqualTo("2026/10/2026-10-04-grocery-list-urgent-1a2b3c4d.md")
        assertThat(FolderSyncer.relativePathFor(note.copy(title = "!!!"))).isEqualTo("2026/10/2026-10-04-note-1a2b3c4d.md")
    }

    @Test
    fun `sync slugs cannot escape folders and trim separators after truncation`() {
        assertThat(FolderSyncer.relativePathFor(note.copy(title = "../../HELLO\\world")))
            .isEqualTo("2026/10/2026-10-04-hello-world-1a2b3c4d.md")
        assertThat(FolderSyncer.relativePathFor(note.copy(title = "a".repeat(39) + " long title")))
            .isEqualTo("2026/10/2026-10-04-${"a".repeat(39)}-1a2b3c4d.md")
        assertThat(FolderSyncer.relativePathFor(note.copy(title = "日本語", id = "abc")))
            .isEqualTo("2026/10/2026-10-04-note-abc.md")
        assertThat(FolderSyncer.relativePathFor(note.copy(title = "A".repeat(60))))
            .isEqualTo("2026/10/2026-10-04-${"a".repeat(40)}-1a2b3c4d.md")
    }
}
