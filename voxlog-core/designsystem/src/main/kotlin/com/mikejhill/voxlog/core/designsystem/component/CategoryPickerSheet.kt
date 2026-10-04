package com.mikejhill.voxlog.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId

/** Bottom sheet listing every category; tapping one selects it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: CategoryId?,
    onSelect: (CategoryId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Text(
            "Category",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.extraLarge, vertical = Spacing.small),
        )
        LazyColumn(modifier = Modifier.navigationBarsPadding()) {
            items(categories, key = { it.id.value }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    leadingContent = {
                        Icon(CategoryIcons.forName(category.iconName), contentDescription = null, tint = Color(category.colorArgb))
                    },
                    trailingContent = {
                        if (category.id == selectedId) Icon(Icons.Rounded.Check, contentDescription = "Selected")
                    },
                    modifier = Modifier.clickable { onSelect(category.id) },
                )
            }
        }
    }
}
