package com.mikejhill.voxlog.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikejhill.voxlog.core.designsystem.theme.Spacing

/** A small colored dot paired with a category name; color is never the only signal. */
@Composable
fun CategoryBadge(name: String, colorArgb: Long, iconName: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Category $name" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(colorArgb).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CategoryIcons.forName(iconName), contentDescription = null, tint = Color(colorArgb), modifier = Modifier.size(14.dp))
        }
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A compact, non-interactive label chip. */
@Composable
fun LabelTag(name: String, colorArgb: Long, modifier: Modifier = Modifier) {
    Text(
        text = "#$name",
        style = MaterialTheme.typography.labelSmall,
        color = Color(colorArgb),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color(colorArgb).copy(alpha = 0.12f))
            .padding(horizontal = Spacing.small, vertical = 2.dp),
    )
}

/** An interactive chip used for category selection on capture screens. */
@Composable
fun CategoryChip(name: String, colorArgb: Long, iconName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(name) },
        leadingIcon = {
            Icon(
                CategoryIcons.forName(iconName),
                contentDescription = null,
                tint = Color(colorArgb),
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        modifier = modifier,
    )
}

/** A friendly, centered empty state with an icon, title and supporting text. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.huge),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
