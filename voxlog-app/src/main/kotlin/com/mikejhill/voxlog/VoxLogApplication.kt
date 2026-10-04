package com.mikejhill.voxlog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mikejhill.voxlog.core.data.di.ApplicationScope
import com.mikejhill.voxlog.core.data.model.ModelDownloadScheduler
import com.mikejhill.voxlog.core.data.pipeline.CaptureRecovery
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.shortcuts.ShortcutPublisher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Application entry point. Startup work is deliberately minimal and asynchronous so that a voice
 * shortcut reaches the microphone as fast as possible.
 */
@HiltAndroidApp
class VoxLogApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var captureRecovery: CaptureRecovery

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var modelDownloadScheduler: ModelDownloadScheduler

    @Inject
    lateinit var shortcutPublisher: ShortcutPublisher

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            captureRecovery.recoverInterruptedRecordings()
            val settings = settingsRepository.current()
            // Fetch models in the background so the first transcription doesn't wait on a download.
            modelDownloadScheduler.downloadSpeechModel(settings.speechModelId)
            if (settings.isSemanticSearchEnabled) modelDownloadScheduler.downloadEmbeddingModel()
        }
        shortcutPublisher.start()
    }
}
