package com.mikejhill.voxlog.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.search.MatchType
import com.mikejhill.voxlog.core.data.search.SearchRepository
import com.mikejhill.voxlog.core.data.search.SearchResult
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Search results split by match type, full-text first. */
data class SearchUiState(
    val query: String = "",
    val categoryId: CategoryId? = null,
    val isSearching: Boolean = false,
    val textMatches: List<SearchResult> = emptyList(),
    val relatedMatches: List<SearchResult> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    /** True after a non-blank search returned nothing. */
    val hasNoResults: Boolean
        get() = query.isNotBlank() && !isSearching && textMatches.isEmpty() && relatedMatches.isEmpty()
}

/** Runs hybrid search as the user types, debounced to avoid a query per keystroke. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val searchRepository: SearchRepository, categoryRepository: CategoryRepository) :
    ViewModel() {
    private val query = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<CategoryId?>(null)

    /** Current query text, updated immediately for the text field. */
    val queryText: StateFlow<String> = query.asStateFlow()

    /** Screen state. */
    val uiState: StateFlow<SearchUiState> = combine(
        combine(query.debounce(DEBOUNCE_MILLIS), categoryFilter) { text, category -> text to category }
            .flatMapLatest { (text, category) -> resultsFor(text, category) },
        categoryRepository.observeCategories(),
    ) { state, categories -> state.copy(categories = categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    /** Updates the query. */
    fun onQueryChanged(text: String) {
        query.value = text
    }

    /** Restricts results to one category, or all when null. */
    fun onCategorySelected(categoryId: CategoryId?) {
        categoryFilter.value = categoryId
    }

    private fun resultsFor(text: String, categoryId: CategoryId?) = flow {
        if (text.isBlank()) {
            emit(SearchUiState(categoryId = categoryId))
            return@flow
        }
        emit(SearchUiState(query = text, categoryId = categoryId, isSearching = true))
        val results = searchRepository.search(text, NoteFilter(categoryId = categoryId))
        emit(
            SearchUiState(
                query = text,
                categoryId = categoryId,
                textMatches = results.filter { it.matchType == MatchType.FULL_TEXT },
                relatedMatches = results.filter { it.matchType == MatchType.SEMANTIC },
            ),
        )
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
