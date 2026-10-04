package com.mikejhill.voxlog.core.data.export

import com.mikejhill.voxlog.core.data.database.dao.CategoryDao
import com.mikejhill.voxlog.core.data.database.dao.LabelDao
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import java.io.OutputStream
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds a complete export of every note's text and metadata plus retained audio files. */
class NoteExporter @Inject constructor(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val labelDao: LabelDao,
    private val audioFileStore: AudioFileStore,
    private val clock: Clock,
) {
    /** Snapshot of all data as an [ExportDocument]. Recordings still in progress are excluded. */
    suspend fun buildDocument(): ExportDocument {
        val categories = categoryDao.getCategories().associateBy { it.id }
        val notes = noteDao.getAllNotes()
            .filter { it.note.status != "RECORDING" }
            .map { ExportedNote.from(it, categories) }
        return ExportDocument(
            exportedAt = clock.instant().toString(),
            categories = categories.values.sortedBy { it.sortOrder }.map { it.name },
            labels = labelDao.getLabels().map { it.name }.sorted(),
            notes = notes,
        )
    }

    /**
     * Writes a ZIP containing `notes.json`, `notes.csv` and `audio/` to [output]. Both JSON and CSV
     * name each note's audio file (relative path) when one exists.
     */
    suspend fun writeZip(output: OutputStream) = withContext(Dispatchers.IO) {
        val document = buildDocument()
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putTextEntry("notes.json", ExportFormatter.toJson(document))
            zip.putTextEntry("notes.csv", ExportFormatter.toCsv(document.notes))
            document.notes.mapNotNull { it.audioFile }.forEach { relativePath ->
                val source = audioFileStore.audioFile(relativePath.removePrefix("audio/"))
                if (!source.exists()) return@forEach
                zip.putNextEntry(ZipEntry(relativePath).apply { time = source.lastModified() })
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun ZipOutputStream.putTextEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.encodeToByteArray())
        closeEntry()
    }
}
