package com.mikejhill.voxlog.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mikejhill.voxlog.R
import com.mikejhill.voxlog.core.data.di.ApplicationScope
import com.mikejhill.voxlog.core.data.repository.CategoryRepository
import com.mikejhill.voxlog.core.model.CaptureIntents
import com.mikejhill.voxlog.core.model.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps dynamic launcher shortcuts ("Record into Journal", …) in sync with the user's first few
 * categories. Static "Voice note" / "Text note" shortcuts are declared in `res/xml/shortcuts.xml`.
 */
@Singleton
class ShortcutPublisher
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val categoryRepository: CategoryRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    /** Starts observing categories. Call once from [android.app.Application.onCreate]. */
    fun start() {
        scope.launch {
            categoryRepository
                .observeCategories()
                .map { categories -> categories.filterNot { it.isSystem }.take(MAX_DYNAMIC_SHORTCUTS) }
                .distinctUntilChanged()
                .collect { publish(it) }
        }
    }

    private fun publish(categories: List<Category>) {
        val shortcuts =
            categories.mapIndexed { index, category ->
                val intent =
                    Intent(CaptureIntents.ACTION_RECORD_VOICE)
                        .setPackage(context.packageName)
                        .putExtra(CaptureIntents.EXTRA_CATEGORY_ID, category.id.value)
                        .putExtra(CaptureIntents.EXTRA_SHORTCUT_ID, "dynamic-voice-${category.id.value}")
                ShortcutInfoCompat
                    .Builder(context, "dynamic-voice-${category.id.value}")
                    .setShortLabel(category.name.take(MAX_LABEL_LENGTH))
                    .setLongLabel(context.getString(R.string.shortcut_record_into, category.name))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_record))
                    .setIntent(intent)
                    .setRank(index + STATIC_SHORTCUT_COUNT)
                    .build()
            }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private companion object {
        /** Launchers typically show four shortcuts; two are the static voice/text ones. */
        const val MAX_DYNAMIC_SHORTCUTS = 2
        const val STATIC_SHORTCUT_COUNT = 2
        const val MAX_LABEL_LENGTH = 25
    }
}
