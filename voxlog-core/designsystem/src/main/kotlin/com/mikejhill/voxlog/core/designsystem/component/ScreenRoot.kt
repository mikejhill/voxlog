package com.mikejhill.voxlog.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Root container for each activity's content. Exposes Compose test tags as Android resource ids
 * so UI Automator end-to-end tests and macrobenchmarks can find elements across activities.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenRoot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.semantics { testTagsAsResourceId = true }) { content() }
}
