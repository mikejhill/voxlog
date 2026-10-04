package com.mikejhill.voxlog.feature.notes.sample

import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.CategorySettings
import com.mikejhill.voxlog.core.model.GeoLocation
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import com.mikejhill.voxlog.feature.notes.list.NotesListActions
import com.mikejhill.voxlog.feature.notes.list.NotesListUiState
import java.time.Instant

/**
 * Realistic sample content for Compose previews, screenshot tests and the auto-generated store
 * screenshots. Kept deterministic so screenshots are stable.
 */
object SampleNotes {
    private val now: Instant = Instant.parse("2026-10-04T14:30:00Z")

    /** Sample categories, including the system category. */
    val categories: List<Category> = listOf(
        Category(CategoryId.UNCATEGORIZED, "Uncategorized", 0xFF78909C, "inbox", true, 0, CategorySettings()),
        Category(CategoryId("journal"), "Journal", 0xFF5C6BC0, "book", false, 1, CategorySettings()),
        Category(CategoryId("health"), "Health log", 0xFF26A69A, "monitor_heart", false, 2, CategorySettings(shouldSaveAudio = false)),
        Category(CategoryId("ideas"), "Ideas", 0xFFEF6C00, "lightbulb", false, 3, CategorySettings()),
    )

    /** Sample labels. */
    val labels: List<Label> = listOf(
        Label(LabelId("gratitude"), "gratitude", 0xFFAB47BC),
        Label(LabelId("work"), "work", 0xFF29B6F6),
        Label(LabelId("bp"), "blood-pressure", 0xFFEC407A),
    )

    /** Sample notes covering voice, text, in-progress transcription and labels. */
    val notes: List<Note> = listOf(
        note(
            id = "a1",
            category = "journal",
            title = "Morning walk by the river",
            text = "Cold but clear this morning. Saw the herons again near the bridge. I want to keep this routine " +
                "going through winter — it sets the tone for the whole day.",
            minutesAgo = 25,
            method = CaptureMethod.VOICE_RECORDING,
            durationMillis = 94_000,
            labels = setOf("gratitude"),
            hasAudio = true,
        ),
        note(
            id = "a2",
            category = "health",
            title = "2026-10-04T07:02:11-05:00",
            text = "Blood pressure 118 over 76, pulse 64. Slept about seven hours.",
            minutesAgo = 450,
            method = CaptureMethod.VOICE_RECORDING,
            durationMillis = 12_000,
            labels = setOf("bp"),
        ),
        note(
            id = "a3",
            category = "ideas",
            title = "Offline-first sync for the garden planner",
            text = "Store everything locally, mirror to a folder, let Syncthing do the rest. No server to run.",
            minutesAgo = 1_600,
            method = CaptureMethod.TEXT_ENTRY,
            labels = setOf("work"),
        ),
        note(
            id = "a4",
            category = "uncategorized",
            title = "2026-10-02T18:45:03-05:00",
            text = "",
            minutesAgo = 2,
            method = CaptureMethod.VOICE_RECORDING,
            durationMillis = 312_000,
            status = NoteStatus.TRANSCRIBING,
            progress = 40,
        ),
    )

    /** A loaded list state. */
    val contentState: NotesListUiState.Content = NotesListUiState.Content(notes, categories, labels, NoteFilter())

    /** Actions that do nothing, for previews. */
    val noOpActions: NotesListActions = NotesListActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Suppress("LongParameterList")
    private fun note(
        id: String,
        category: String,
        title: String,
        text: String,
        minutesAgo: Long,
        method: CaptureMethod,
        durationMillis: Long? = null,
        labels: Set<String> = emptySet(),
        hasAudio: Boolean = false,
        status: NoteStatus = NoteStatus.READY,
        progress: Int? = null,
    ): Note {
        val created = now.minusSeconds(minutesAgo * SECONDS_PER_MINUTE)
        return Note(
            id = NoteId(id),
            categoryId = CategoryId(category),
            captureMethod = method,
            title = title,
            titleSource = if (title.first().isDigit()) TitleSource.DEFAULT else TitleSource.USER,
            text = text,
            rawTranscript = text.takeIf { method == CaptureMethod.VOICE_RECORDING },
            createdAt = created,
            updatedAt = created,
            durationMillis = durationMillis,
            audioFileName = if (hasAudio) "$id.m4a" else null,
            location = SAMPLE_LOCATION,
            language = "en",
            status = status,
            transcriptionProgressPercent = progress,
            labelIds = labels.map(::LabelId).toSet(),
            captureShortcutId = null,
            processingLog = null,
        )
    }

    private const val SECONDS_PER_MINUTE = 60L
    private val SAMPLE_LOCATION =
        GeoLocation(latitude = 44.9778, longitude = -93.265, accuracyMeters = 12f, placeName = "Minneapolis, Minnesota")
}
