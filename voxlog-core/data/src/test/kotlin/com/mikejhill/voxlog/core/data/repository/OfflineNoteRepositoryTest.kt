package com.mikejhill.voxlog.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.GeoLocation
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import com.mikejhill.voxlog.core.testing.TestDataGraph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineNoteRepositoryTest {
    private val scope = TestScope()
    private val harness = TestDataGraph(scope)

    @After
    fun tearDown() = harness.close()

    @Test
    fun `text note gets a timestamp title with the capture offset and is scheduled`() = scope.runTest {
        val id = harness.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, title = null, text = "Hello"))

        val note = harness.notes.getNote(id)!!
        assertThat(note.title).isEqualTo("2026-10-04T08:15:00-05:00")
        assertThat(note.titleSource).isEqualTo(TitleSource.DEFAULT)
        assertThat(note.captureMethod).isEqualTo(CaptureMethod.TEXT_ENTRY)
        assertThat(note.createdAt.toString()).isEqualTo("2026-10-04T13:15:00Z")
        assertThat(harness.scheduler.calls).containsExactly("text:${id.value}")
    }

    @Test
    fun `a typed title is user-owned`() = scope.runTest {
        val id = harness.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, title = " Groceries ", text = "milk"))

        val note = harness.notes.getNote(id)!!
        assertThat(note.title).isEqualTo("Groceries")
        assertThat(note.titleSource).isEqualTo(TitleSource.USER)
    }

    @Test
    fun `capture into an unknown category falls back to Uncategorized`() = scope.runTest {
        val id = harness.notes.createTextNote(TextNoteDraft(CategoryId("deleted"), title = null, text = "x"))

        assertThat(harness.notes.getNote(id)!!.categoryId).isEqualTo(CategoryId.UNCATEGORIZED)
    }

    @Test
    fun `category default labels are applied at capture time`() = scope.runTest {
        val label = harness.labels.getOrCreate("health")
        val category = harness.categories.createCategory("Health", 0xFF000000, "book")
        harness.categories.updateCategory(category.copy(settings = category.settings.copy(defaultLabelIds = setOf(label.id))))

        val id = harness.notes.createTextNote(TextNoteDraft(category.id, title = null, text = "bp 120/80"))

        assertThat(harness.notes.getNote(id)!!.labelIds).containsExactly(label.id)
    }

    @Test
    fun `voice notes are hidden while recording and visible as soon as recording completes`() = scope.runTest {
        val id = NoteId.random()

        harness.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))
        assertThat(harness.notes.observeNotes(NoteFilter()).first()).isEmpty()

        harness.notes.completeVoiceNote(id, durationMillis = 5_000, categoryId = CategoryId.UNCATEGORIZED)
        val visible = harness.notes.observeNotes(NoteFilter()).first().single()

        assertThat(visible.status).isEqualTo(NoteStatus.TRANSCRIBING)
        assertThat(visible.durationMillis).isEqualTo(5_000)
        assertThat(harness.scheduler.calls).containsExactly("voice:${id.value}")
    }

    @Test
    fun `switching category mid-recording applies the final category's audio setting`() = scope.runTest {
        val textOnly = harness.categories.createCategory("Measurements", 0xFF000000, "book")
        harness.categories.updateCategory(textOnly.copy(settings = textOnly.settings.copy(shouldSaveAudio = false)))
        val id = NoteId.random()

        harness.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))
        harness.notes.completeVoiceNote(id, 1_000, textOnly.id)

        val row = harness.database.noteDao().getNote(id.value)!!.note
        assertThat(row.categoryId).isEqualTo(textOnly.id.value)
        assertThat(row.shouldRetainAudio).isFalse()
    }

    @Test
    fun `moving a note keeps its capture-time audio setting`() = scope.runTest {
        val textOnly = harness.categories.createCategory("Measurements", 0xFF000000, "book")
        harness.categories.updateCategory(textOnly.copy(settings = textOnly.settings.copy(shouldSaveAudio = false)))
        val id = NoteId.random()
        harness.notes.beginVoiceNote(VoiceCaptureRequest(id, CategoryId.UNCATEGORIZED))
        harness.notes.completeVoiceNote(id, 1_000, CategoryId.UNCATEGORIZED)

        harness.notes.moveToCategory(id, textOnly.id)

        assertThat(harness.database.noteDao().getNote(id.value)!!.note.shouldRetainAudio).isTrue()
    }

    @Test
    fun `editing the title marks it user-owned and schedules re-indexing`() = scope.runTest {
        val id = harness.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, null, "x"))

        harness.notes.updateTitle(id, "Renamed")

        assertThat(harness.notes.getNote(id)!!.titleSource).isEqualTo(TitleSource.USER)
        assertThat(harness.scheduler.calls).contains("edit:${id.value}")
    }

    @Test
    fun `deleting a category moves its notes to Uncategorized`() = scope.runTest {
        val category = harness.categories.createCategory("Temp", 0xFF000000, "book")
        val id = harness.notes.createTextNote(TextNoteDraft(category.id, null, "x"))

        harness.categories.deleteCategory(category.id)

        assertThat(harness.notes.getNote(id)!!.categoryId).isEqualTo(CategoryId.UNCATEGORIZED)
        assertThat(harness.categories.getCategory(category.id)).isNull()
    }

    @Test
    fun `the system category cannot be deleted or renamed`() = scope.runTest {
        val system = harness.categories.getCategory(CategoryId.UNCATEGORIZED)!!

        harness.categories.deleteCategory(CategoryId.UNCATEGORIZED)
        harness.categories.updateCategory(system.copy(name = "Renamed", settings = system.settings.copy(shouldSaveAudio = false)))

        val after = harness.categories.getCategory(CategoryId.UNCATEGORIZED)!!
        assertThat(after.name).isEqualTo("Uncategorized")
        assertThat(after.settings.shouldSaveAudio).isFalse()
    }

    @Test
    fun `location is attached asynchronously when enabled`() = scope.runTest {
        harness.settings.update { it.copy(isLocationCaptureEnabled = true) }
        harness.location.location = GeoLocation(1.0, 2.0, 5f, "Somewhere")

        val id = harness.notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, null, "x"))
        advanceUntilIdle()

        assertThat(harness.notes.observeNote(id).first()!!.location?.placeName).isEqualTo("Somewhere")
    }

    @Test
    fun `deleting a label removes it from notes and category defaults`() = scope.runTest {
        val label = harness.labels.getOrCreate("temp")
        val category = harness.categories.createCategory("C", 0xFF000000, "book")
        harness.categories.updateCategory(category.copy(settings = category.settings.copy(defaultLabelIds = setOf(label.id))))
        val id = harness.notes.createTextNote(TextNoteDraft(category.id, null, "x"))

        harness.labels.deleteLabel(label.id)

        assertThat(harness.notes.getNote(id)!!.labelIds).isEmpty()
        assertThat(harness.categories.getCategory(category.id)!!.settings.defaultLabelIds).isEmpty()
    }

    @Test
    fun `label names are unique ignoring case`() = scope.runTest {
        val first = harness.labels.getOrCreate("Work")
        val second = harness.labels.getOrCreate("work ")

        assertThat(second.id).isEqualTo(first.id)
    }
}
