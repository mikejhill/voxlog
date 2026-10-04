package com.mikejhill.voxlog.core.testing

import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.repository.LabelRepository
import com.mikejhill.voxlog.core.data.repository.NoteRepository
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.data.repository.VoiceCaptureRequest
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.CategorySettings
import com.mikejhill.voxlog.core.model.DefaultNoteTitle
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import java.io.File
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [NoteRepository] for fast, deterministic ViewModel tests. */
class FakeNoteRepository(private val clock: Clock = TestTime.CLOCK) : NoteRepository {
    private val notes = MutableStateFlow<Map<NoteId, Note>>(emptyMap())

    override fun observeNotes(filter: NoteFilter): Flow<List<Note>> = notes.map { all ->
        all.values.filter { it.status != NoteStatus.RECORDING && filter.matches(it) }.sortedByDescending { it.createdAt }
    }

    override fun observeNote(id: NoteId): Flow<Note?> = notes.map { it[id] }

    override suspend fun getNote(id: NoteId): Note? = notes.value[id]

    override suspend fun createTextNote(draft: TextNoteDraft): NoteId {
        val note = newNote(NoteId.random(), draft.categoryId, CaptureMethod.TEXT_ENTRY).copy(
            title = draft.title ?: DefaultNoteTitle.forCapture(clock.instant(), clock.zone),
            titleSource = if (draft.title != null) TitleSource.USER else TitleSource.DEFAULT,
            text = draft.text,
            labelIds = draft.labelIds,
            captureShortcutId = draft.captureShortcutId,
        )
        put(note)
        return note.id
    }

    override fun recordingFileFor(noteId: NoteId): File = File.createTempFile(noteId.value, ".wav")

    override suspend fun beginVoiceNote(request: VoiceCaptureRequest) =
        put(newNote(request.noteId, request.categoryId, CaptureMethod.VOICE_RECORDING).copy(status = NoteStatus.RECORDING))

    override suspend fun completeVoiceNote(id: NoteId, durationMillis: Long, categoryId: CategoryId) =
        edit(id) { it.copy(durationMillis = durationMillis, categoryId = categoryId, status = NoteStatus.TRANSCRIBING) }

    override suspend fun updateTitle(id: NoteId, title: String) = edit(id) { it.copy(title = title, titleSource = TitleSource.USER) }

    override suspend fun updateText(id: NoteId, text: String) = edit(id) { it.copy(text = text) }

    override suspend fun moveToCategory(id: NoteId, categoryId: CategoryId) = edit(id) { it.copy(categoryId = categoryId) }

    override suspend fun setLabels(id: NoteId, labelIds: Set<LabelId>) = edit(id) { it.copy(labelIds = labelIds) }

    override suspend fun deleteAudio(id: NoteId) = edit(id) { it.copy(audioFileName = null) }

    override suspend fun deleteNote(id: NoteId) = notes.update { it - id }

    override suspend fun reprocess(id: NoteId) = Unit

    override fun audioFileFor(note: Note): File? = null

    /** Adds or replaces a note directly, for arranging test state. */
    fun put(note: Note) = notes.update { it + (note.id to note) }

    private fun edit(id: NoteId, transform: (Note) -> Note) = notes.update { all ->
        all[id]?.let { all + (id to transform(it).copy(updatedAt = clock.instant())) } ?: all
    }

    private fun newNote(id: NoteId, categoryId: CategoryId, method: CaptureMethod) = Note(
        id = id,
        categoryId = categoryId,
        captureMethod = method,
        title = DefaultNoteTitle.forCapture(clock.instant(), clock.zone),
        titleSource = TitleSource.DEFAULT,
        text = "",
        rawTranscript = null,
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
        durationMillis = null,
        audioFileName = null,
        location = null,
        language = null,
        status = NoteStatus.READY,
        transcriptionProgressPercent = null,
        labelIds = emptySet(),
        captureShortcutId = null,
        processingLog = null,
    )
}

/** In-memory [CategoryRepository] seeded with the system category. */
class FakeCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow(
        listOf(Category(CategoryId.UNCATEGORIZED, "Uncategorized", 0xFF78909C, "inbox", true, 0, CategorySettings())),
    )

    override fun observeCategories(): Flow<List<Category>> = categories

    override suspend fun getCategory(id: CategoryId): Category? = categories.value.firstOrNull { it.id == id }

    override suspend fun createCategory(name: String, colorArgb: Long, iconName: String): Category {
        val category = Category(CategoryId.random(), name, colorArgb, iconName, false, categories.value.size, CategorySettings())
        categories.update { it + category }
        return category
    }

    override suspend fun updateCategory(category: Category) = categories.update { all ->
        all.map {
            if (it.id ==
                category.id
            ) {
                category
            } else {
                it
            }
        }
    }

    override suspend fun deleteCategory(id: CategoryId) = categories.update { all -> all.filterNot { it.id == id && !it.isSystem } }
}

/** In-memory [LabelRepository]. */
class FakeLabelRepository : LabelRepository {
    private val labels = MutableStateFlow<List<Label>>(emptyList())

    override fun observeLabels(): Flow<List<Label>> = labels.map { all -> all.sortedBy { it.name.lowercase() } }

    override suspend fun getLabels(): List<Label> = labels.value

    override suspend fun getOrCreate(name: String): Label = labels.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        ?: Label(LabelId.random(), name.trim(), LABEL_COLOR).also { label -> labels.update { it + label } }

    override suspend fun updateLabel(label: Label) = labels.update { all -> all.map { if (it.id == label.id) label else it } }

    override suspend fun deleteLabel(id: LabelId) = labels.update { all -> all.filterNot { it.id == id } }

    private companion object {
        const val LABEL_COLOR = 0xFF5C6BC0
    }
}
