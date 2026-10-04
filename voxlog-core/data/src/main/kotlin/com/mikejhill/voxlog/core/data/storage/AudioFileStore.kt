package com.mikejhill.voxlog.core.data.storage

import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns VoxLog's on-disk file layout:
 * - `files/recordings/` raw WAV captures awaiting transcription (deleted afterwards)
 * - `files/audio/` retained, compressed audio referenced by notes
 * - `files/models/` downloaded speech and embedding models
 */
@Singleton
class AudioFileStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** Directory for in-progress and untranscribed WAV recordings. */
    val recordingsDirectory: File
        get() = File(context.filesDir, "recordings").apply { mkdirs() }

    /** Directory for retained audio. */
    val audioDirectory: File
        get() = File(context.filesDir, "audio").apply { mkdirs() }

    /** Directory for downloaded models. */
    val modelsDirectory: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    /** The WAV path for a note's raw recording. */
    fun recordingFile(fileName: String): File = File(recordingsDirectory, fileName)

    /** The path of a retained audio file. */
    fun audioFile(fileName: String): File = File(audioDirectory, fileName)

    /** Bytes available for recordings, including cache the system can clear on demand. */
    fun availableBytes(): Long {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return storageManager.getAllocatableBytes(storageManager.getUuidForPath(context.filesDir))
    }
}
