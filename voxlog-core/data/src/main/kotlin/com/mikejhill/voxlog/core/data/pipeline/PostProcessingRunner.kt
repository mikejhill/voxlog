package com.mikejhill.voxlog.core.data.pipeline

import com.mikejhill.voxlog.core.data.database.dao.CategoryDao
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.database.entity.CategoryEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteLabelCrossRef
import com.mikejhill.voxlog.core.data.repository.LabelRepository
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import com.mikejhill.voxlog.engine.llm.HookEndpoint
import com.mikejhill.voxlog.engine.llm.HookNote
import com.mikejhill.voxlog.engine.llm.HookPatch
import com.mikejhill.voxlog.engine.llm.HookPayload
import com.mikejhill.voxlog.engine.llm.HttpHookClient
import com.mikejhill.voxlog.engine.llm.LlmException
import com.mikejhill.voxlog.engine.llm.NotePostProcessor
import com.mikejhill.voxlog.engine.llm.PostProcessingRequest
import com.mikejhill.voxlog.engine.llm.PostProcessingResult
import com.mikejhill.voxlog.engine.llm.PostProcessingTasks
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Applies optional post-processing to a saved note: built-in LLM tasks, then custom HTTP hooks.
 * With nothing configured it only marks the note READY. Failures are logged on the note and never
 * undo the save.
 */
@Suppress("LongParameterList")
class PostProcessingRunner @Inject constructor(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val labelRepository: LabelRepository,
    private val settingsRepository: SettingsRepository,
    private val llmClientFactory: LlmClientFactory,
    private val hookClient: HttpHookClient,
    private val audioFileStore: AudioFileStore,
    private val clock: Clock,
) {
    /** Processes [noteId]. Throws [LlmException] only for retryable failures. */
    suspend fun run(noteId: NoteId) {
        val note = noteDao.getNote(noteId.value)?.note ?: return
        val settings = settingsRepository.current()
        val log = mutableListOf<String>()
        runBuiltInTasks(note, settings, log)
        runHooks(noteId, settings, log)
        val latest = noteDao.getNote(noteId.value)?.note ?: return
        noteDao.update(
            latest.copy(
                status = NoteStatus.READY.name,
                processingLog = log.joinToString("\n").ifBlank { null },
                updatedAtEpochMillis = clock.millis(),
            ),
        )
    }

    private suspend fun runBuiltInTasks(note: NoteEntity, settings: AppSettings, log: MutableList<String>) {
        val client = llmClientFactory.create(settings) ?: return
        val category = categoryDao.getCategory(note.categoryId)
        val tasks = tasksFor(note, category, settings)
        if (!tasks.hasAnyTask) return
        val request = PostProcessingRequest(
            text = note.text,
            tasks = tasks,
            existingLabels = labelRepository.getLabels().map { it.name },
            categoryNames = categoryDao.getCategories().filterNot { it.isSystem }.map { it.name },
            extraInstructions = settings.postProcessing.cleanupInstructions,
        )
        try {
            applyResult(NoteId(note.id), NotePostProcessor(client).process(request))
        } catch (exception: LlmException) {
            if (exception.isRetryable) throw exception
            log += "AI processing failed: ${exception.message}"
        }
    }

    private fun tasksFor(note: NoteEntity, category: CategoryEntity?, settings: AppSettings): PostProcessingTasks {
        val global = settings.postProcessing
        val isVoice = note.captureMethod == CaptureMethod.VOICE_RECORDING.name
        return PostProcessingTasks(
            // Cleanup targets transcription artifacts, so typed notes are left exactly as written.
            shouldCleanup = isVoice && (global.shouldCleanupGlobally || category?.shouldAutoCleanup == true),
            shouldAutoLabel = global.shouldAutoLabelGlobally || category?.shouldAutoLabel == true,
            shouldAutoCategorize = global.shouldAutoCategorizeGlobally && note.categoryId == CategoryId.UNCATEGORIZED.value,
            shouldAutoName = note.titleSource == TitleSource.DEFAULT.name &&
                (global.shouldAutoNameGlobally || category?.shouldAutoName == true),
        )
    }

    private suspend fun applyResult(noteId: NoteId, result: PostProcessingResult) {
        val latest = noteDao.getNote(noteId.value)?.note ?: return
        val matchedCategory = result.category?.let { name ->
            categoryDao.getCategories().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) && !it.isSystem }
        }
        noteDao.update(
            latest.copy(
                // Never overwrite text the user edited while processing ran.
                text = result.cleanedText?.takeIf { latest.text == latest.rawTranscript } ?: latest.text,
                title = result.title?.takeIf { latest.titleSource == TitleSource.DEFAULT.name }?.trim() ?: latest.title,
                titleSource = if (result.title != null && latest.titleSource == TitleSource.DEFAULT.name) {
                    TitleSource.AUTO.name
                } else {
                    latest.titleSource
                },
                categoryId = matchedCategory?.takeIf { latest.categoryId == CategoryId.UNCATEGORIZED.value }?.id ?: latest.categoryId,
            ),
        )
        addLabels(noteId, result.labels.orEmpty())
    }

    private suspend fun runHooks(noteId: NoteId, settings: AppSettings, log: MutableList<String>) {
        val note = noteDao.getNote(noteId.value) ?: return
        val hooks = settings.hooks.filter { it.appliesTo(note.note.categoryId) }
        if (hooks.isEmpty()) return
        val categoryName = categoryDao.getCategory(note.note.categoryId)?.name.orEmpty()
        val payload =
            HookPayload(event = HookPayload.EVENT_NOTE_CREATED, note = note.note.toHookNote(categoryName, note.labels.map { it.name }))
        val audio = note.note.audioFileName?.let(audioFileStore::audioFile)?.takeIf { it.exists() }
        for (hook in hooks) {
            try {
                hookClient.invoke(HookEndpoint(hook.url, hook.headers, hook.shouldIncludeAudio), payload, audio)
                    ?.let { applyPatch(noteId, it) }
            } catch (exception: LlmException) {
                log += "Hook \"${hook.name}\" failed: ${exception.message}"
            }
        }
    }

    private suspend fun applyPatch(noteId: NoteId, patch: HookPatch) {
        val latest = noteDao.getNote(noteId.value)?.note ?: return
        val category = patch.category?.let { name -> categoryDao.getCategories().firstOrNull { it.name.equals(name, ignoreCase = true) } }
        noteDao.update(
            latest.copy(
                text = patch.text ?: latest.text,
                title = patch.title ?: latest.title,
                titleSource = if (patch.title != null) TitleSource.AUTO.name else latest.titleSource,
                categoryId = category?.id ?: latest.categoryId,
            ),
        )
        addLabels(noteId, patch.addLabels)
    }

    private suspend fun addLabels(noteId: NoteId, names: List<String>) {
        val labelIds = names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .map { labelRepository.getOrCreate(it).id.value }
        noteDao.insertLabelLinks(labelIds.map { NoteLabelCrossRef(noteId.value, it) })
    }

    private fun NoteEntity.toHookNote(categoryName: String, labelNames: List<String>) = HookNote(
        id = id,
        title = title,
        text = text,
        rawTranscript = rawTranscript,
        category = categoryName,
        labels = labelNames,
        captureMethod = captureMethod,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis).toString(),
        durationMillis = durationMillis,
        latitude = latitude,
        longitude = longitude,
    )
}
