package com.mikejhill.voxlog.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** The icons a category can use, keyed by the stable name stored in the database. */
object CategoryIcons {
    /** Every selectable icon in picker order. */
    val all: Map<String, ImageVector> = linkedMapOf(
        "inbox" to Icons.Outlined.Inbox,
        "book" to Icons.Outlined.Book,
        "psychology" to Icons.Outlined.Psychology,
        "lightbulb" to Icons.Outlined.Lightbulb,
        "checklist" to Icons.Outlined.Checklist,
        "notifications" to Icons.Outlined.Notifications,
        "straighten" to Icons.Outlined.Straighten,
        "monitor_heart" to Icons.Outlined.MonitorHeart,
        "fitness" to Icons.Outlined.FitnessCenter,
        "dining" to Icons.Outlined.LocalDining,
        "sleep" to Icons.Outlined.Bedtime,
        "work" to Icons.Outlined.Work,
        "favorite" to Icons.Outlined.FavoriteBorder,
    )

    /** Returns the icon for [name], falling back to the inbox icon. */
    fun forName(name: String): ImageVector = all[name] ?: Icons.Outlined.Inbox
}

/** Category colors offered in the picker. */
val CategoryColorPalette: List<Long> = listOf(
    0xFF78909C,
    0xFF5C6BC0,
    0xFF26A69A,
    0xFFEF6C00,
    0xFFAB47BC,
    0xFF66BB6A,
    0xFFEC407A,
    0xFF29B6F6,
    0xFF8D6E63,
)
