package com.mikejhill.voxlog.core.data.export

import kotlinx.serialization.json.Json

/** Renders [ExportDocument]s as JSON, RFC 4180 CSV and Markdown with YAML front matter. */
object ExportFormatter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** Pretty-printed JSON. */
    fun toJson(document: ExportDocument): String = json.encodeToString(ExportDocument.serializer(), document)

    /** CSV with a header row; fields are quoted when needed and line breaks are preserved. */
    fun toCsv(notes: List<ExportedNote>): String = buildString {
        appendCsvRow(ExportedNote.CSV_HEADER)
        notes.forEach { appendCsvRow(it.csvValues()) }
    }

    /** One Markdown file per note, readable in any editor and compatible with Obsidian. */
    fun toMarkdown(note: ExportedNote): String = buildString {
        appendLine("---")
        appendLine("id: ${note.id}")
        appendLine("title: ${yamlString(note.title)}")
        appendLine("category: ${yamlString(note.category)}")
        appendLine("labels: [${note.labels.joinToString(", ") { yamlString(it) }}]")
        appendLine("capture_method: ${note.captureMethod}")
        appendLine("created_at: ${note.createdAt}")
        appendLine("updated_at: ${note.updatedAt}")
        note.durationMillis?.let { appendLine("duration_millis: $it") }
        note.audioFile?.let { appendLine("audio_file: ${yamlString("../../../$it")}") }
        if (note.latitude != null && note.longitude != null) {
            appendLine("location: [${note.latitude}, ${note.longitude}]")
        }
        note.placeName?.let { appendLine("place_name: ${yamlString(it)}") }
        appendLine("---")
        appendLine()
        appendLine(note.text)
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        append(values.joinToString(",") { csvField(it) })
        append("\r\n")
    }

    private fun csvField(value: String): String =
        if (value.any { it in CSV_SPECIAL_CHARACTERS }) "\"${value.replace("\"", "\"\"")}\"" else value

    private const val CSV_SPECIAL_CHARACTERS = ",\"\n\r"

    private fun yamlString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
