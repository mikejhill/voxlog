package com.mikejhill.voxlog.feature.notes.list

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.LabelMatchMode
import com.mikejhill.voxlog.core.testing.FakeCategoryRepository
import com.mikejhill.voxlog.core.testing.FakeLabelRepository
import com.mikejhill.voxlog.core.testing.FakeNoteRepository
import com.mikejhill.voxlog.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val scope = TestScope(mainDispatcherRule.dispatcher)
    private val notes = FakeNoteRepository()
    private val categories = FakeCategoryRepository()
    private val labels = FakeLabelRepository()
    private val viewModel by lazy { NotesListViewModel(notes, categories, labels) }

    /** Advances the test scheduler until the state matches. */
    private fun TestScope.content(predicate: (NotesListUiState.Content) -> Boolean = { true }): NotesListUiState.Content {
        backgroundScope.launch { viewModel.uiState.collect {} }
        repeat(MAX_PUMPS) {
            testScheduler.advanceUntilIdle()
            (viewModel.uiState.value as? NotesListUiState.Content)?.takeIf(predicate)?.let { return it }
        }
        error("State never matched: ${viewModel.uiState.value}")
    }

    private companion object {
        const val MAX_PUMPS = 5
    }

    @Test
    fun `shows notes newest first with categories available for filtering`() = scope.runTest {
        notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "first", "a"))

        val state = content { it.notes.isNotEmpty() }

        assertThat(state.notes.map { it.title }).containsExactly("first")
        assertThat(state.categories.map { it.name }).contains("Uncategorized")
        assertThat(state.isFiltered).isFalse()
    }

    @Test
    fun `category and label filters combine`() = scope.runTest {
        val journal = categories.createCategory("Journal", 0, "book")
        val work = labels.getOrCreate("work")
        notes.createTextNote(TextNoteDraft(journal.id, "journal work", "x", labelIds = setOf(work.id)))
        notes.createTextNote(TextNoteDraft(journal.id, "journal only", "x"))
        notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "other work", "x", labelIds = setOf(work.id)))

        viewModel.selectCategory(journal.id)
        viewModel.toggleLabel(work.id)

        val state = content { it.isFiltered && it.notes.size == 1 }
        assertThat(state.notes.single().title).isEqualTo("journal work")

        viewModel.clearFilters()
        assertThat(content { !it.isFiltered }.notes).hasSize(3)
    }

    @Test
    fun `label match mode can require all labels`() = scope.runTest {
        val a = labels.getOrCreate("a")
        val b = labels.getOrCreate("b")
        notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "both", "x", labelIds = setOf(a.id, b.id)))
        notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "one", "x", labelIds = setOf(a.id)))
        viewModel.toggleLabel(a.id)
        viewModel.toggleLabel(b.id)

        viewModel.setLabelMatchMode(LabelMatchMode.ALL)

        assertThat(content { it.filter.labelMatchMode == LabelMatchMode.ALL && it.notes.size == 1 }.notes.single().title).isEqualTo("both")
    }

    @Test
    fun `deletion hides immediately, can be undone, and only commits when confirmed`() = scope.runTest {
        val id = notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "doomed", "x"))
        content { it.notes.isNotEmpty() }

        viewModel.requestDeletion(id)
        assertThat(content { it.notes.isEmpty() }.notes).isEmpty()
        assertThat(notes.getNote(id)).isNotNull()

        viewModel.undoDeletion(id)
        assertThat(content { it.notes.isNotEmpty() }.notes).hasSize(1)

        viewModel.requestDeletion(id)
        viewModel.commitDeletion(id)
        content { it.notes.isEmpty() }
        assertThat(notes.getNote(id)).isNull()
    }
}
