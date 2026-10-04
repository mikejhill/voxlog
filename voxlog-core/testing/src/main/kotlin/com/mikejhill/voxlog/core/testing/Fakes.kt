package com.mikejhill.voxlog.core.testing

import com.mikejhill.voxlog.core.data.location.LocationCapture
import com.mikejhill.voxlog.core.data.pipeline.LlmClientFactory
import com.mikejhill.voxlog.core.data.pipeline.NoteProcessingScheduler
import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.SecretStore
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.GeoLocation
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.engine.llm.LlmClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/** Records scheduling calls instead of enqueuing WorkManager work. */
class FakeNoteProcessingScheduler : NoteProcessingScheduler {
    /** Every call in order, e.g. `voice:<id>`, `text:<id>`, `edit:<id>`, `reprocess:<id>`, `sync`. */
    val calls: MutableList<String> = mutableListOf()

    override fun scheduleAfterVoiceCapture(noteId: NoteId) {
        calls += "voice:${noteId.value}"
    }

    override fun scheduleAfterTextCapture(noteId: NoteId) {
        calls += "text:${noteId.value}"
    }

    override fun scheduleAfterEdit(noteId: NoteId) {
        calls += "edit:${noteId.value}"
    }

    override fun scheduleReprocess(noteId: NoteId) {
        calls += "reprocess:${noteId.value}"
    }

    override fun scheduleSync() {
        calls += "sync"
    }
}

/** Returns a fixed location (or none). */
class FakeLocationCapture(var location: GeoLocation? = null) : LocationCapture {
    override suspend fun currentLocation(isPrecise: Boolean): GeoLocation? = location
}

/** In-memory settings. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

/** In-memory secrets. */
class FakeSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun read(name: String): String? = values[name]

    override suspend fun write(name: String, value: String?) {
        if (value.isNullOrBlank()) values.remove(name) else values[name] = value
    }
}

/** An [LlmClient] that returns canned JSON and records the prompts it receives. */
class FakeLlmClient(var reply: String = "{}", var models: List<String> = emptyList()) : LlmClient {
    /** System prompts received, in order. */
    val systemPrompts: MutableList<String> = mutableListOf()

    /** Schemas received, in order. */
    val schemas: MutableList<JsonObject> = mutableListOf()

    override suspend fun completeJson(systemPrompt: String, userPrompt: String, schema: JsonObject): String {
        systemPrompts += systemPrompt
        schemas += schema
        return reply
    }

    override suspend fun listModels(): List<String> = models
}

/** Returns [client] whenever a provider is configured, mirroring the real factory's "off" behavior. */
class FakeLlmClientFactory(var client: LlmClient? = FakeLlmClient()) : LlmClientFactory {
    override suspend fun create(settings: AppSettings): LlmClient? =
        client.takeIf { settings.postProcessing.provider != LlmProviderType.NONE }

    override suspend fun createForModelListing(settings: AppSettings): LlmClient? = create(settings)
}

/** Shared fixed time values so tests never depend on the wall clock. */
object TestTime {
    /** 2026-10-04T13:15:00Z. */
    val NOW: Instant = Instant.parse("2026-10-04T13:15:00Z")

    /** A UTC-5 zone so offset handling is exercised. */
    val ZONE: ZoneId = ZoneOffset.ofHours(-5)

    /** A clock frozen at [NOW] in [ZONE]. */
    val CLOCK: Clock = Clock.fixed(NOW, ZONE)
}
