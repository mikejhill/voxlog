package com.mikejhill.voxlog.feature.settings.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.data.repository.LabelRepository
import com.mikejhill.voxlog.core.designsystem.component.CategoryColorPalette
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Category and label management state. */
data class CategoriesUiState(val categories: List<Category> = emptyList(), val labels: List<Label> = emptyList())

/** Creates, edits and deletes categories and labels. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val labelRepository: LabelRepository,
) : ViewModel() {
    /** Screen state. */
    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.observeCategories(),
        labelRepository.observeLabels(),
    ) { categories, labels -> CategoriesUiState(categories, labels) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CategoriesUiState())

    /** Creates a category with the next palette color. */
    fun createCategory(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val color = CategoryColorPalette[uiState.value.categories.size % CategoryColorPalette.size]
        categoryRepository.createCategory(name, color, DEFAULT_ICON)
    }

    /** Saves an edited category. */
    fun saveCategory(category: Category) = viewModelScope.launch { categoryRepository.updateCategory(category) }

    /** Deletes a category; its notes move to Uncategorized. */
    fun deleteCategory(id: CategoryId) = viewModelScope.launch { categoryRepository.deleteCategory(id) }

    /** Renames a label. */
    fun renameLabel(label: Label, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) labelRepository.updateLabel(label.copy(name = name.trim()))
    }

    /** Deletes a label everywhere. */
    fun deleteLabel(id: LabelId) = viewModelScope.launch { labelRepository.deleteLabel(id) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_ICON = "book"
    }
}
