package com.mikejhill.voxlog.core.data.repository

import com.mikejhill.voxlog.core.data.database.dao.CategoryDao
import com.mikejhill.voxlog.core.data.database.dao.NoteDao
import com.mikejhill.voxlog.core.data.database.entity.NoteEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteLabelCrossRef
import com.mikejhill.voxlog.core.data.database.toDomain
import com.mikejhill.voxlog.core.data.di.ApplicationScope
import com.mikejhill.voxlog.core.data.location.LocationCapture
import com.mikejhill.voxlog.core.data.pipeline.NoteProcessingScheduler
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.DefaultNoteTitle
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** [NoteRepository] backed by the local Room database. */
@Singleton
@Suppress("LongParameterList", "TooManyFunctions") // Repository surface mirrors the note lifecycle.
class OfflineNoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
    private val audioFileStore: AudioFileStore,
    private val locationCapture: LocationCapture,
    private val scheduler: NoteProcessingScheduler,
    private val clock: Clock,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : NoteRepository {
    override fun observeNotes(filter: NoteFilter): Flow<List<Note>> = noteDao.observeNotes(filter.categoryId?.value).map { rows ->
        rows.map { it.toDomain() }.filter(filter::matches)
    }

    override fun observeNote(id: NoteId): Flow<Note?> = noteDao.observeNote(id.value).map { it?.toDomain() }

    override suspend fun getNote(id: NoteId): Note? = noteDao.getNote(id.value)?.toDomain()

    override suspend fun createTextNote(draft: TextNoteDraft): NoteId {
        val categoryId = resolveCategory(draft.categoryId)
        val category = categoryDao.getCategory(categoryId.value)
        val noteId = NoteId.random()
        val now = clock.instant()
        val userTitle = draft.title?.trim()?.takeIf { it.isNotEmpty() }
        noteDao.insert(
            newEntity(noteId, categoryId, CaptureMethod.TEXT_ENTRY, draft.captureShortcutId).copy(
                title = userTitle ?: DefaultNoteTitle.forCapture(now, clock.zone),
                titleSource = (if (userTitle != null) TitleSource.USER else TitleSource.DEFAULT).name,
                text = draft.text,
                status = NoteStatus.READY.name,
            ),
        )
        val defaultLabels = category?.defaultLabelIds.orEmpty().toSet()
        noteDao.insertLabelLinks((defaultLabels + draft.labelIds.map { it.value }).map { labelLink(noteId, it) })
        captureLocationAsync(noteId)
        scheduler.scheduleAfterTextCapture(noteId)
        return noteId
    }

    override fun recordingFileFor(noteId: NoteId): File = audioFileStore.recordingFile(recordingFileName(noteId))

    override suspend fun beginVoiceNote(request: VoiceCaptureRequest) {
        val categoryId = resolveCategory(request.categoryId)
        val category = categoryDao.getCategory(categoryId.value)
        noteDao.insert(
            newEntity(request.noteId, categoryId, CaptureMethod.VOICE_RECORDING, request.captureShortcutId).copy(
                recordingFileName = recordingFileName(request.noteId),
                shouldRetainAudio = category?.shouldSaveAudio ?: true,
                status = NoteStatus.RECORDING.name,
            ),
        )
        noteDao.insertLabelLinks(category?.defaultLabelIds.orEmpty().map { labelLink(request.noteId, it) })
        captureLocationAsync(request.noteId)
    }

    override suspend fun completeVoiceNote(id: NoteId, durationMillis: Long, categoryId: CategoryId) {
        val finalCategoryId = resolveCategory(categoryId)
        val current = noteDao.getNote(id.value)?.note ?: return
        val hasCategoryChanged = current.categoryId != finalCategoryId.value
        val finalCategory = if (hasCategoryChanged) categoryDao.getCategory(finalCategoryId.value) else null
        noteDao.update(
            current.copy(
                categoryId = finalCategoryId.value,
                shouldRetainAudio = finalCategory?.shouldSaveAudio ?: current.shouldRetainAudio,
                durationMillis = durationMillis,
                status = NoteStatus.TRANSCRIBING.name,
                transcriptionProgressPercent = 0,
                updatedAtEpochMillis = clock.millis(),
            ),
        )
        finalCategory?.let { category -> noteDao.insertLabelLinks(category.defaultLabelIds.map { labelLink(id, it) }) }
        scheduler.scheduleAfterVoiceCapture(id)
    }

    override suspend fun updateTitle(id: NoteId, title: String) {
        updateEntity(id) { it.copy(title = title.trim(), titleSource = TitleSource.USER.name) }
        scheduler.scheduleAfterEdit(id)
    }

    override suspend fun updateText(id: NoteId, text: String) {
        updateEntity(id) { it.copy(text = text) }
        scheduler.scheduleAfterEdit(id)
    }

    override suspend fun moveToCategory(id: NoteId, categoryId: CategoryId) {
        val target = resolveCategory(categoryId)
        updateEntity(id) { it.copy(categoryId = target.value) }
        scheduler.scheduleSync()
    }

    override suspend fun setLabels(id: NoteId, labelIds: Set<LabelId>) {
        noteDao.replaceLabels(id.value, labelIds.map { it.value })
        updateEntity(id) { it }
        scheduler.scheduleSync()
    }

    override suspend fun deleteAudio(id: NoteId) {
        val note = noteDao.getNote(id.value)?.note ?: return
        note.audioFileName?.let { audioFileStore.audioFile(it).delete() }
        noteDao.update(note.copy(audioFileName = null, updatedAtEpochMillis = clock.millis()))
        scheduler.scheduleSync()
    }

    override suspend fun deleteNote(id: NoteId) {
        val note = noteDao.getNote(id.value)?.note ?: return
        note.audioFileName?.let { audioFileStore.audioFile(it).delete() }
        note.recordingFileName?.let { audioFileStore.recordingFile(it).delete() }
        noteDao.delete(id.value)
        scheduler.scheduleSync()
    }

    override suspend fun reprocess(id: NoteId) = scheduler.scheduleReprocess(id)

    override fun audioFileFor(note: Note): File? = note.audioFileName?.let(audioFileStore::audioFile)?.takeIf { it.exists() }

    private suspend fun resolveCategory(requested: CategoryId): CategoryId =
        if (categoryDao.getCategory(requested.value) != null) requested else CategoryId.UNCATEGORIZED

    private suspend fun updateEntity(id: NoteId, transform: (NoteEntity) -> NoteEntity) {
        val current = noteDao.getNote(id.value)?.note ?: return
        noteDao.update(transform(current).copy(updatedAtEpochMillis = clock.millis()))
    }

    private fun captureLocationAsync(noteId: NoteId) {
        applicationScope.launch {
            val settings = settingsRepository.current()
            if (!settings.isLocationCaptureEnabled) return@launch
            val location = locationCapture.currentLocation(settings.isPreciseLocationEnabled) ?: return@launch
            val current = noteDao.getNote(noteId.value)?.note ?: return@launch
            noteDao.update(
                current.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    placeName = location.placeName,
                ),
            )
        }
    }

    private fun newEntity(id: NoteId, categoryId: CategoryId, method: CaptureMethod, shortcutId: String?): NoteEntity {
        val now = clock.instant()
        return NoteEntity(
            id = id.value,
            categoryId = categoryId.value,
            captureMethod = method.name,
            title = DefaultNoteTitle.forCapture(now, clock.zone),
            titleSource = TitleSource.DEFAULT.name,
            text = "",
            rawTranscript = null,
            createdAtEpochMillis = now.toEpochMilli(),
            updatedAtEpochMillis = now.toEpochMilli(),
            durationMillis = null,
            audioFileName = null,
            recordingFileName = null,
            shouldRetainAudio = false,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            placeName = null,
            language = null,
            status = NoteStatus.READY.name,
            transcriptionProgressPercent = null,
            captureShortcutId = shortcutId,
            processingLog = null,
        )
    }

    private fun recordingFileName(noteId: NoteId) = "${noteId.value}.wav"

    private fun labelLink(noteId: NoteId, labelId: String) = NoteLabelCrossRef(noteId.value, labelId)
}
