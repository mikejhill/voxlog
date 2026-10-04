package com.mikejhill.voxlog.core.data.sync

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.mikejhill.voxlog.core.data.export.ExportFormatter
import com.mikejhill.voxlog.core.data.export.ExportedNote
import com.mikejhill.voxlog.core.data.export.NoteExporter
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-way mirror of notes into a user-chosen folder via the Storage Access Framework, so any
 * provider that exposes a folder (Nextcloud, Syncthing, OneDrive, Google Drive apps) can sync it.
 *
 * Layout: `notes/YYYY/MM/<date>-<slug>-<id8>.md`, `audio/<file>`, `index.json`.
 * Only files VoxLog wrote (recognized by the id suffix) are ever deleted.
 */
@Singleton
class FolderSyncer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val exporter: NoteExporter,
    private val audioFileStore: AudioFileStore,
) {
    /** Mirrors every note. Does nothing when no folder is configured. */
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        val folderUri = settingsRepository.current().syncFolderUri ?: return@withContext
        val root = DocumentFile.fromTreeUri(context, folderUri.toUri())
            ?.takeIf { it.canWrite() }
            ?: throw IOException("Sync folder is not writable")
        val document = exporter.buildDocument()
        writeText(root, "index.json", "application/json", ExportFormatter.toJson(document))
        val notesDirectory = root.directory("notes")
        val expected = document.notes.associateBy { relativePathFor(it) }
        expected.forEach { (path, note) ->
            val (year, month, fileName) = path.split('/')
            writeText(notesDirectory.directory(year).directory(month), fileName, "text/markdown", ExportFormatter.toMarkdown(note))
        }
        deleteStaleNotes(notesDirectory, expected.keys.map { it.substringAfterLast('/') }.toSet())
        copyAudio(root.directory("audio"), document.notes)
    }

    private fun copyAudio(audioDirectory: DocumentFile, notes: List<ExportedNote>) {
        val existing = audioDirectory.listFiles().mapNotNull { it.name }.toSet()
        notes.mapNotNull { it.audioFile?.removePrefix("audio/") }
            .filterNot { it in existing }
            .forEach { name ->
                val source = audioFileStore.audioFile(name).takeIf { it.exists() } ?: return@forEach
                val target = audioDirectory.createFile("audio/mp4", name) ?: return@forEach
                context.contentResolver.openOutputStream(target.uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
            }
    }

    private fun deleteStaleNotes(notesDirectory: DocumentFile, keepFileNames: Set<String>) {
        notesDirectory.listFiles().filter { it.isDirectory }.forEach { year ->
            year.listFiles().filter { it.isDirectory }.forEach { month ->
                month.listFiles()
                    .filter { file -> file.name?.let { MANAGED_FILE.matches(it) && it !in keepFileNames } == true }
                    .forEach { it.delete() }
            }
        }
    }

    private fun writeText(directory: DocumentFile, name: String, mimeType: String, content: String) {
        val existing = directory.findFile(name)
        val bytes = content.encodeToByteArray()
        if (existing != null && existing.length() == bytes.size.toLong()) {
            val current = context.contentResolver.openInputStream(existing.uri)?.use { it.readBytes() }
            if (current.contentEquals(bytes)) return
        }
        val target = existing ?: directory.createFile(mimeType, name) ?: throw IOException("Cannot create $name")
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Cannot write $name")
    }

    private fun DocumentFile.directory(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory } ?: createDirectory(name) ?: throw IOException("Cannot create folder $name")

    /** File naming rules, exposed for tests. */
    companion object {
        private val MANAGED_FILE = Regex(".*-[0-9a-f]{8}\\.md")
        private const val SLUG_MAX_LENGTH = 40
        private const val ID_SUFFIX_LENGTH = 8

        /** Relative path under `notes/` for [note], e.g. `2026/10/2026-10-04-grocery-list-1a2b3c4d.md`. */
        fun relativePathFor(note: ExportedNote): String {
            val date = note.createdAt.take("yyyy-MM-dd".length)
            val slug = note.title.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(SLUG_MAX_LENGTH)
                .trim('-')
                .ifEmpty { "note" }
            val (year, month) = date.split('-')
            return "$year/$month/$date-$slug-${note.id.take(ID_SUFFIX_LENGTH)}.md"
        }
    }
}
