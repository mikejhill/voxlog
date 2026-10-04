package com.mikejhill.voxlog.feature.settings

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.feature.settings.categories.CategoriesScreen
import com.mikejhill.voxlog.feature.settings.categories.CategoriesViewModel
import com.mikejhill.voxlog.feature.settings.categories.CategoryEditorScreen
import com.mikejhill.voxlog.feature.settings.categories.LabelsScreen
import com.mikejhill.voxlog.feature.settings.ui.SettingsNavigation
import com.mikejhill.voxlog.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

/** Global settings. */
@Serializable
data object SettingsRoute

/** Category list. */
@Serializable
data object CategoriesRoute

/** One category's settings. */
@Serializable
data class CategoryEditorRoute(val categoryId: String)

/** Label list. */
@Serializable
data object LabelsRoute

/** Host callbacks for the settings feature. */
data class SettingsCallbacks(
    val onBack: () -> Unit,
    val onNavigate: (Any) -> Unit,
    val onRequestLocationPermission: (isPrecise: Boolean) -> Unit,
    val appVersion: String,
)

/** Adds settings, category and label management to the host's navigation graph. */
fun NavGraphBuilder.settingsScreens(callbacks: SettingsCallbacks) {
    composable<SettingsRoute> {
        val viewModel: SettingsViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            actions = viewModel,
            navigation = SettingsNavigation(
                onBack = callbacks.onBack,
                onManageCategories = { callbacks.onNavigate(CategoriesRoute) },
                onManageLabels = { callbacks.onNavigate(LabelsRoute) },
                onRequestLocationPermission = callbacks.onRequestLocationPermission,
                appVersion = callbacks.appVersion,
            ),
        )
    }
    composable<CategoriesRoute> {
        val viewModel: CategoriesViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        CategoriesScreen(
            state = state,
            onCreate = { viewModel.createCategory(it) },
            onOpen = { callbacks.onNavigate(CategoryEditorRoute(it.value)) },
            onBack = callbacks.onBack,
        )
    }
    composable<CategoryEditorRoute> { entry ->
        val route = entry.toRoute<CategoryEditorRoute>()
        val viewModel: CategoriesViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val category = state.categories.firstOrNull { it.id == CategoryId(route.categoryId) } ?: return@composable
        CategoryEditorScreen(
            category = category,
            labels = state.labels,
            onSave = { viewModel.saveCategory(it) },
            onDelete = {
                viewModel.deleteCategory(category.id)
                callbacks.onBack()
            },
            onBack = callbacks.onBack,
        )
    }
    composable<LabelsRoute> {
        val viewModel: CategoriesViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LabelsScreen(
            labels = state.labels,
            onRename = { label, name -> viewModel.renameLabel(label, name) },
            onDelete = { viewModel.deleteLabel(it.id) },
            onBack = callbacks.onBack,
        )
    }
}
