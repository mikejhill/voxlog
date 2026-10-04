package com.mikejhill.voxlog.core.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.mikejhill.voxlog.core.data.export.NoteExporter
import com.mikejhill.voxlog.core.data.pipeline.NoteProcessingScheduler
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Operations on user-chosen locations from the Storage Access Framework: export files and the sync folder. */
@Singleton
class UserFilesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exporter: NoteExporter,
    private val settingsRepository: SettingsRepository,
    private val scheduler: NoteProcessingScheduler,
) {
    /** Writes a full export ZIP to the document at [uri] (from `ACTION_CREATE_DOCUMENT`). */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Cannot open export file")
        output.use { exporter.writeZip(it) }
    }

    /** Persists access to [treeUri] (from `ACTION_OPEN_DOCUMENT_TREE`), saves it and starts a sync. */
    suspend fun setSyncFolder(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(treeUri, flags)
        settingsRepository.update { it.copy(syncFolderUri = treeUri.toString()) }
        scheduler.scheduleSync()
    }

    /** Stops syncing and releases the folder permission. Files already in the folder are left untouched. */
    suspend fun clearSyncFolder() {
        val current = settingsRepository.current().syncFolderUri ?: return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(current.toUri(), flags) }
        settingsRepository.update { it.copy(syncFolderUri = null) }
    }

    /** Queues an immediate sync. */
    fun syncNow() = scheduler.scheduleSync()
}
