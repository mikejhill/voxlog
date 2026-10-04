package com.mikejhill.voxlog.feature.settings.categories

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mikejhill.voxlog.core.model.CaptureIntents
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.feature.settings.R

/** The kind of capture a pinned shortcut starts. */
enum class ShortcutKind {
    /** Starts recording immediately. */
    VOICE,

    /** Opens the text composer. */
    TEXT,
}

/** Pins per-category home-screen shortcuts that capture straight into that category. */
object ShortcutPinner {
    /** Whether the current launcher supports pinning. */
    fun isSupported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the launcher to pin a [kind] shortcut for [category]. The launcher shows its own confirmation. */
    fun requestPin(context: Context, category: Category, kind: ShortcutKind): Boolean {
        val shortcutId = "${kind.name.lowercase()}-${category.id.value}"
        val action = if (kind == ShortcutKind.VOICE) CaptureIntents.ACTION_RECORD_VOICE else CaptureIntents.ACTION_WRITE_TEXT
        val intent = Intent(action)
            .setPackage(context.packageName)
            .putExtra(CaptureIntents.EXTRA_CATEGORY_ID, category.id.value)
            .putExtra(CaptureIntents.EXTRA_SHORTCUT_ID, shortcutId)
        val label = if (kind == ShortcutKind.VOICE) category.name else "${category.name} (text)"
        val icon = if (kind == ShortcutKind.VOICE) R.drawable.ic_shortcut_voice else R.drawable.ic_shortcut_text
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(label.take(MAX_SHORT_LABEL))
            .setLongLabel(if (kind == ShortcutKind.VOICE) "Record into ${category.name}" else "Write into ${category.name}")
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private const val MAX_SHORT_LABEL = 25
}
