package com.mikejhill.voxlog.feature.search

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.data.search.MatchType
import com.mikejhill.voxlog.core.data.search.SearchRepository
import com.mikejhill.voxlog.core.data.search.SearchResult
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.testing.FakeCategoryRepository
import com.mikejhill.voxlog.core.testing.FakeNoteRepository
import com.mikejhill.voxlog.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val scope = TestScope(dispatcher)
    private val notes = FakeNoteRepository()

    /** Records every query and returns one full-text and one semantic hit. */
    private class RecordingSearch(private val notes: FakeNoteRepository) : SearchRepository {
        val queries = mutableListOf<Pair<String, NoteFilter>>()

        override suspend fun search(query: String, filter: NoteFilter): List<SearchResult> {
            queries += query to filter
            val note = notes.observeNotes(NoteFilter()).first().first()
            return listOf(SearchResult(note, MatchType.FULL_TEXT, 2.0), SearchResult(note, MatchType.SEMANTIC, 0.5))
        }
    }

    @Test
    fun `debounces typing and splits results by match type`() = scope.runTest {
        notes.createTextNote(TextNoteDraft(CategoryId.UNCATEGORIZED, "t", "x"))
        val search = RecordingSearch(notes)
        val viewModel = SearchViewModel(search, FakeCategoryRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("g")
        viewModel.onQueryChanged("ga")
        viewModel.onQueryChanged("gar")
        advanceTimeBy(1_000)
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(search.queries.map { it.first }).containsExactly("gar")
        val state = viewModel.uiState.value
        assertThat(state.textMatches).hasSize(1)
        assertThat(state.relatedMatches).hasSize(1)
        assertThat(state.hasNoResults).isFalse()
    }

    @Test
    fun `blank queries do not search`() = scope.runTest {
        val search = RecordingSearch(notes)
        val viewModel = SearchViewModel(search, FakeCategoryRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("   ")
        advanceTimeBy(1_000)

        assertThat(search.queries).isEmpty()
        assertThat(viewModel.uiState.value.hasNoResults).isFalse()
    }
}
