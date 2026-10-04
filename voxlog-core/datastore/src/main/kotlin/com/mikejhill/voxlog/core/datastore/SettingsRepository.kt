package com.mikejhill.voxlog.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads and updates [AppSettings]. */
interface SettingsRepository {
    /** Streams the current settings. */
    val settings: Flow<AppSettings>

    /** Returns the current settings once. */
    suspend fun current(): AppSettings = settings.first()

    /** Atomically applies [transform] to the stored settings. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** [SettingsRepository] backed by a JSON DataStore file. */
@Singleton
class DataStoreSettingsRepository
@Inject
constructor(private val dataStore: DataStore<AppSettings>) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData(transform)
    }
}

/** JSON serializer that tolerates unknown keys so downgrades never lose the whole file. */
internal object AppSettingsSerializer : Serializer<AppSettings> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings = try {
        json.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("Unreadable settings file", exception)
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}

/** Hilt bindings for settings storage. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /** Provides the singleton settings DataStore. */
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<AppSettings> =
        DataStoreFactory.create(serializer = AppSettingsSerializer) {
            File(context.filesDir, "datastore/settings.json")
        }

    /** Binds the DataStore-backed repository. */
    @Provides
    fun provideSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository = repository

    /** Binds the Keystore-backed secret store. */
    @Provides
    fun provideSecretStore(store: KeystoreSecretStore): SecretStore = store
}
