package com.mikejhill.voxlog.feature.search

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mikejhill.voxlog.core.model.NoteId
import kotlinx.serialization.Serializable

/** Type-safe route of the search screen. */
@Serializable
data object SearchRoute

/** Adds search to the host's navigation graph. */
fun NavGraphBuilder.searchScreen(onOpenNote: (NoteId) -> Unit, onBack: () -> Unit) {
    composable<SearchRoute> {
        val viewModel: SearchViewModel = hiltViewModel()
        val query by viewModel.queryText.collectAsStateWithLifecycle()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SearchScreen(
            query = query,
            state = state,
            onQueryChange = viewModel::onQueryChanged,
            onCategorySelect = viewModel::onCategorySelected,
            onResultClick = onOpenNote,
            onBack = onBack,
        )
    }
}
