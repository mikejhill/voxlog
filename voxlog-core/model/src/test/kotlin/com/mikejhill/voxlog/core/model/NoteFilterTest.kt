package com.mikejhill.voxlog.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class NoteFilterTest {
    private val journal = CategoryId("journal")
    private val work = LabelId("work")
    private val urgent = LabelId("urgent")

    @Test
    fun `empty filter matches everything`() {
        assertThat(NoteFilter().matches(note())).isTrue()
    }

    @Test
    fun `category filter matches only that category`() {
        val filter = NoteFilter(categoryId = journal)

        assertThat(filter.matches(note(category = journal))).isTrue()
        assertThat(filter.matches(note(category = CategoryId.UNCATEGORIZED))).isFalse()
    }

    @Test
    fun `ANY label mode matches notes with at least one selected label`() {
        val filter = NoteFilter(labelIds = setOf(work, urgent), labelMatchMode = LabelMatchMode.ANY)

        assertThat(filter.matches(note(labels = setOf(work)))).isTrue()
        assertThat(filter.matches(note(labels = emptySet()))).isFalse()
    }

    @Test
    fun `ALL label mode requires every selected label`() {
        val filter = NoteFilter(labelIds = setOf(work, urgent), labelMatchMode = LabelMatchMode.ALL)

        assertThat(filter.matches(note(labels = setOf(work)))).isFalse()
        assertThat(filter.matches(note(labels = setOf(work, urgent, LabelId("other"))))).isTrue()
    }

    @Test
    fun `label filters span categories`() {
        val filter = NoteFilter(labelIds = setOf(work))

        assertThat(filter.matches(note(category = journal, labels = setOf(work)))).isTrue()
        assertThat(filter.matches(note(category = CategoryId.UNCATEGORIZED, labels = setOf(work)))).isTrue()
    }

    @Test
    fun `word count ignores repeated whitespace`() {
        assertThat(note(text = "  one   two\nthree  ").wordCount).isEqualTo(3)
        assertThat(note(text = "").wordCount).isEqualTo(0)
    }

    private fun note(category: CategoryId = CategoryId.UNCATEGORIZED, labels: Set<LabelId> = emptySet(), text: String = "") = Note(
        id = NoteId.random(),
        categoryId = category,
        captureMethod = CaptureMethod.TEXT_ENTRY,
        title = "t",
        titleSource = TitleSource.DEFAULT,
        text = text,
        rawTranscript = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        durationMillis = null,
        audioFileName = null,
        location = null,
        language = null,
        status = NoteStatus.READY,
        transcriptionProgressPercent = null,
        labelIds = labels,
        captureShortcutId = null,
        processingLog = null,
    )
}
