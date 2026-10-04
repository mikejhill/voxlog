package com.mikejhill.voxlog.core.data.search

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.data.embedding.EmbeddingService
import com.mikejhill.voxlog.core.data.model.ModelRepository
import com.mikejhill.voxlog.core.data.repository.TextNoteDraft
import com.mikejhill.voxlog.core.data.storage.AudioFileStore
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.testing.TestDataGraph
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HybridSearchRepositoryTest {
    private val scope = TestScope()
    private val harness = TestDataGraph(scope)

    // No model is downloaded in tests, so semantic search is skipped and only full-text results appear.
    private val search = HybridSearchRepository(
        searchDao = harness.database.searchDao(),
        noteDao = harness.database.noteDao(),
        embeddingService = EmbeddingService(
            ModelRepository(AudioFileStore(ApplicationProvider.getApplicationContext())),
            harness.database.noteDao(),
            harness.database.searchDao(),
        ),
        settingsRepository = harness.settings,
    )

    @After
    fun tearDown() = harness.close()

    private suspend fun note(title: String, text: String, category: CategoryId = CategoryId.UNCATEGORIZED) =
        harness.notes.createTextNote(TextNoteDraft(category, title, text))

    @Test
    fun `matches words by prefix across title and body`() = scope.runTest {
        val id = note("Groceries", "Buy tomatoes and basil")
        note("Other", "Nothing relevant")

        val results = search.search("tomat", NoteFilter())

        assertThat(results.map { it.note.id }).containsExactly(id)
        assertThat(results.single().matchType).isEqualTo(MatchType.FULL_TEXT)
    }

    @Test
    fun `title matches rank above body matches`() = scope.runTest {
        val bodyMatch = note("Weekend", "Remember to call about the garden")
        val titleMatch = note("Garden plans", "Raised beds")

        val results = search.search("garden", NoteFilter())

        assertThat(results.map { it.note.id }).containsExactly(titleMatch, bodyMatch).inOrder()
    }

    @Test
    fun `all words must match`() = scope.runTest {
        val both = note("a", "red apple")
        note("b", "red car")

        assertThat(search.search("red apple", NoteFilter()).map { it.note.id }).containsExactly(both)
    }

    @Test
    fun `filters apply to search results`() = scope.runTest {
        val journal = harness.categories.createCategory("Journal", 0, "book")
        val inJournal = note("x", "sunrise", journal.id)
        note("y", "sunrise")

        assertThat(search.search("sunrise", NoteFilter(categoryId = journal.id)).map { it.note.id }).containsExactly(inJournal)
    }

    @Test
    fun `edits are reflected in the full-text index`() = scope.runTest {
        val id = note("t", "old words")

        harness.notes.updateText(id, "brand new content")

        assertThat(search.search("old", NoteFilter())).isEmpty()
        assertThat(search.search("brand", NoteFilter()).map { it.note.id }).containsExactly(id)
    }

    @Test
    fun `punctuation and FTS operators in queries are neutralized`() = scope.runTest {
        note("t", "meeting notes")

        assertThat(search.search("\"meeting\" -notes*", NoteFilter())).hasSize(1)
        assertThat(search.search("!!!", NoteFilter())).isEmpty()
    }
}
