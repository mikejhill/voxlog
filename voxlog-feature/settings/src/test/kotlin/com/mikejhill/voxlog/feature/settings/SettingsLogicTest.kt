package com.mikejhill.voxlog.feature.settings

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.testing.FakeCategoryRepository
import com.mikejhill.voxlog.core.testing.FakeLabelRepository
import com.mikejhill.voxlog.core.testing.MainDispatcherRule
import com.mikejhill.voxlog.feature.settings.categories.CategoriesViewModel
import com.mikejhill.voxlog.feature.settings.ui.parseHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsLogicTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `hook headers parse one Name colon value per line and skip malformed lines`() {
        val headers = parseHeaders("Authorization: Bearer abc:def\n\nnot a header\n: missing name\nX-Empty:")

        assertThat(headers).containsExactly("Authorization", "Bearer abc:def", "X-Empty", "")
    }

    @Test
    fun `new categories cycle through the palette and blank names are ignored`() = runTest {
        val categories = FakeCategoryRepository()
        val viewModel = CategoriesViewModel(categories, FakeLabelRepository())

        viewModel.createCategory("Journal")
        viewModel.createCategory("   ")

        val names = categories.observeCategories().first().map { it.name }
        assertThat(names).containsExactly("Uncategorized", "Journal")
    }

    @Test
    fun `deleting a category through the view model leaves the system category`() = runTest {
        val categories = FakeCategoryRepository()
        val viewModel = CategoriesViewModel(categories, FakeLabelRepository())

        viewModel.deleteCategory(CategoryId.UNCATEGORIZED)

        assertThat(categories.observeCategories().first().map { it.id }).containsExactly(CategoryId.UNCATEGORIZED)
    }

    @Test
    fun `labels can be renamed but not to a blank name`() = runTest {
        val labels = FakeLabelRepository()
        val viewModel = CategoriesViewModel(FakeCategoryRepository(), labels)
        val label = labels.getOrCreate("wrok")

        viewModel.renameLabel(label, "work")
        viewModel.renameLabel(label.copy(name = "work"), "  ")

        assertThat(labels.getLabels().single().name).isEqualTo("work")
    }
}
