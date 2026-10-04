package com.mikejhill.voxlog.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.mikejhill.voxlog.core.data.search.SearchResult
import com.mikejhill.voxlog.core.designsystem.component.EmptyState
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId

/** Search screen: a focused query field, category chips, then exact matches followed by related notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (CategoryId?) -> Unit,
    onResultClick: (NoteId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.search_back)) }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    onQueryChange("")
                                }) { Icon(Icons.Rounded.Close, stringResource(R.string.search_clear)) }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("searchField"),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.large),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                FilterChip(state.categoryId == null, { onCategorySelect(null) }, label = { Text(stringResource(R.string.search_all)) })
                state.categories.forEach { category ->
                    FilterChip(state.categoryId == category.id, { onCategorySelect(category.id) }, label = { Text(category.name) })
                }
            }
            if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.hasNoResults) {
                EmptyState(
                    Icons.Outlined.SearchOff,
                    stringResource(R.string.search_no_results_title),
                    stringResource(R.string.search_no_results_message),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.large),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    modifier = Modifier.testTag("searchResults"),
                ) {
                    resultSection(R.string.search_text_matches, state.textMatches, onResultClick)
                    resultSection(R.string.search_related, state.relatedMatches, onResultClick)
                }
            }
        }
    }
}

private fun LazyListScope.resultSection(titleRes: Int, results: List<SearchResult>, onResultClick: (NoteId) -> Unit) {
    if (results.isEmpty()) return
    item(key = "header-$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.extraSmall),
        )
    }
    items(results, key = { "$titleRes-${it.note.id.value}" }) { result ->
        Card(onClick = { onResultClick(result.note.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                Text(result.note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    result.note.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
