package com.mikejhill.voxlog.core.data.model

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mikejhill.voxlog.core.model.ModelCatalog
import com.mikejhill.voxlog.core.model.ModelFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Downloads model files in the background so downloads survive leaving the Settings screen. */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted private val parameters: WorkerParameters,
    private val modelRepository: ModelRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val fileNames = parameters.inputData.getNullableStringArray(KEY_FILE_NAMES).orEmpty().filterNotNull().toSet()
        val files = ModelDownloadScheduler.allModelFiles().filter { it.fileName in fileNames }
        return try {
            files.forEach { modelRepository.ensureDownloaded(it) }
            Result.success()
        } catch (_: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

private const val KEY_FILE_NAMES = "file_names"

/** Queues model downloads, requiring a network connection. */
@Singleton
class ModelDownloadScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** Downloads the speech model with [speechModelId]. */
    fun downloadSpeechModel(speechModelId: String) = enqueue(listOf(ModelCatalog.speechModel(speechModelId).file))

    /** Downloads the semantic-search embedding model. */
    fun downloadEmbeddingModel() = enqueue(listOf(ModelCatalog.embeddingModel.modelFile, ModelCatalog.embeddingModel.vocabularyFile))

    private fun enqueue(files: List<ModelFile>) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(KEY_FILE_NAMES to files.map { it.fileName }.toTypedArray()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("model-" + files.first().fileName, ExistingWorkPolicy.KEEP, request)
    }

    /** Catalog lookup shared with the worker. */
    companion object {
        /** Every downloadable model file. */
        fun allModelFiles(): List<ModelFile> = ModelCatalog.speechModels.map { it.file } +
            listOf(ModelCatalog.embeddingModel.modelFile, ModelCatalog.embeddingModel.vocabularyFile)
    }
}
