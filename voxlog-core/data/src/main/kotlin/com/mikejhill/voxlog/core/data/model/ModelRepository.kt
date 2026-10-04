package com.mikejhill.voxlog.core.data.model

import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.model.ModelFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Download state of one model file. */
sealed interface ModelFileState {
    /** Not on the device. */
    data object Missing : ModelFileState

    /** Downloading; [progressPercent] is 0–100. */
    data class Downloading(val progressPercent: Int) : ModelFileState

    /** Downloaded and verified. */
    data object Ready : ModelFileState

    /** The last download failed. */
    data class Failed(val reason: String) : ModelFileState
}

/**
 * Downloads pinned model files over HTTPS, verifies their SHA-256 and exposes per-file state.
 * Files are written to a temporary name and renamed only after verification, so a partial or
 * tampered download is never used.
 */
@Singleton
class ModelRepository @Inject constructor(private val audioFileStore: AudioFileStore) {
    private val httpClient = OkHttpClient()
    private val states = MutableStateFlow<Map<String, ModelFileState>>(emptyMap())

    /** Per-file-name download states. */
    val fileStates: StateFlow<Map<String, ModelFileState>> = states.asStateFlow()

    /** Where [model] lives on disk once downloaded. */
    fun fileFor(model: ModelFile): File = File(audioFileStore.modelsDirectory, model.fileName)

    /** Whether [model] has been downloaded. */
    fun isAvailable(model: ModelFile): Boolean = fileFor(model).let { it.exists() && it.length() == model.sizeBytes }

    /** Current state of [model], derived from disk when no download has run this session. */
    fun stateOf(model: ModelFile): ModelFileState =
        states.value[model.fileName] ?: if (isAvailable(model)) ModelFileState.Ready else ModelFileState.Missing

    /** Downloads [model] if needed and returns the verified file. */
    suspend fun ensureDownloaded(model: ModelFile): File = withContext(Dispatchers.IO) {
        val target = fileFor(model)
        if (isAvailable(model)) return@withContext target
        val partial = File(target.parentFile, target.name + ".part")
        try {
            download(model, partial)
            verifyChecksum(model, partial)
            if (!partial.renameTo(target)) throw IOException("Could not move ${partial.name} into place")
            setState(model, ModelFileState.Ready)
            target
        } catch (exception: IOException) {
            partial.delete()
            setState(model, ModelFileState.Failed(exception.message ?: "Download failed"))
            throw exception
        }
    }

    /** Deletes a downloaded model to free space. */
    fun delete(model: ModelFile) {
        fileFor(model).delete()
        setState(model, ModelFileState.Missing)
    }

    private suspend fun download(model: ModelFile, destination: File) {
        setState(model, ModelFileState.Downloading(0))
        httpClient.newCall(Request.Builder().url(model.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} downloading ${model.fileName}")
            response.body.byteStream().use { input ->
                destination.outputStream().use { output -> copyWithProgress(input, output, model) }
            }
        }
    }

    /** Copies [input] to [output], publishing whole-percent progress and honoring cancellation. */
    private suspend fun copyWithProgress(input: InputStream, output: OutputStream, model: ModelFile) {
        val buffer = ByteArray(BUFFER_BYTES)
        var copied = 0L
        var lastPercent = -1
        var read = input.read(buffer)
        while (read >= 0) {
            currentCoroutineContext().ensureActive()
            output.write(buffer, 0, read)
            copied += read
            val percent = (copied * PERCENT / model.sizeBytes).toInt().coerceAtMost(PERCENT)
            if (percent != lastPercent) {
                lastPercent = percent
                setState(model, ModelFileState.Downloading(percent))
            }
            read = input.read(buffer)
        }
    }

    private fun verifyChecksum(model: ModelFile, file: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(model.sha256, ignoreCase = true)) throw IOException("Checksum mismatch for ${model.fileName}")
    }

    private fun setState(model: ModelFile, state: ModelFileState) = states.update { it + (model.fileName to state) }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PERCENT = 100
    }
}
