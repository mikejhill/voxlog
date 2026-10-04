package com.mikejhill.voxlog.core.data.di

import android.content.Context
import androidx.room.Room
import com.mikejhill.voxlog.core.data.database.VoxLogDatabase
import com.mikejhill.voxlog.core.data.database.dao.CategoryDao
import com.mikejhill.voxlog.core.data.database.dao.LabelDao
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.database.dao.SearchDao
import com.mikejhill.voxlog.core.data.location.LocationCapture
import com.mikejhill.voxlog.core.data.location.PlatformLocationCapture
import com.mikejhill.voxlog.core.data.pipeline.NoteProcessingScheduler
import com.mikejhill.voxlog.core.data.pipeline.WorkManagerNoteProcessingScheduler
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.repository.LabelRepository
import com.mikejhill.voxlog.core.data.repository.NoteRepository
import com.mikejhill.voxlog.core.data.repository.OfflineCategoryRepository
import com.mikejhill.voxlog.core.data.repository.OfflineLabelRepository
import com.mikejhill.voxlog.core.data.repository.OfflineNoteRepository
import com.mikejhill.voxlog.core.data.search.HybridSearchRepository
import com.mikejhill.voxlog.core.data.search.SearchRepository
import com.mikejhill.voxlog.engine.llm.HttpHookClient
import com.mikejhill.voxlog.engine.whisper.SpeechTranscriber
import com.mikejhill.voxlog.engine.whisper.WhisperSpeechTranscriber
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Marks the process-wide [CoroutineScope] for fire-and-forget work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Hilt providers for storage, clocks and engines. */
@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {
    /** The single Room database. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoxLogDatabase =
        Room.databaseBuilder(context, VoxLogDatabase::class.java, VoxLogDatabase.FILE_NAME)
            .addCallback(VoxLogDatabase.SEED_CALLBACK)
            .build()

    /** Note DAO. */
    @Provides
    fun provideNoteDao(database: VoxLogDatabase): NoteDao = database.noteDao()

    /** Category DAO. */
    @Provides
    fun provideCategoryDao(database: VoxLogDatabase): CategoryDao = database.categoryDao()

    /** Label DAO. */
    @Provides
    fun provideLabelDao(database: VoxLogDatabase): LabelDao = database.labelDao()

    /** Search DAO. */
    @Provides
    fun provideSearchDao(database: VoxLogDatabase): SearchDao = database.searchDao()

    /** Wall clock in the device time zone; replaced by a fixed clock in tests. */
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** Process-wide scope that survives configuration changes and screen exits. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** On-device whisper.cpp transcriber, shared so the loaded model is reused. */
    @Provides
    @Singleton
    fun provideSpeechTranscriber(): SpeechTranscriber = WhisperSpeechTranscriber()

    /** Client for user-defined HTTP hooks. */
    @Provides
    @Singleton
    fun provideHttpHookClient(): HttpHookClient = HttpHookClient()
}

/** Hilt interface bindings for repositories and services. */
@Module
@InstallIn(SingletonComponent::class)
interface DataBindingsModule {
    /** Notes. */
    @Binds
    fun bindNoteRepository(repository: OfflineNoteRepository): NoteRepository

    /** Categories. */
    @Binds
    fun bindCategoryRepository(repository: OfflineCategoryRepository): CategoryRepository

    /** Labels. */
    @Binds
    fun bindLabelRepository(repository: OfflineLabelRepository): LabelRepository

    /** Hybrid search. */
    @Binds
    fun bindSearchRepository(repository: HybridSearchRepository): SearchRepository

    /** Location. */
    @Binds
    fun bindLocationCapture(capture: PlatformLocationCapture): LocationCapture

    /** Background processing. */
    @Binds
    fun bindNoteProcessingScheduler(scheduler: WorkManagerNoteProcessingScheduler): NoteProcessingScheduler
}
