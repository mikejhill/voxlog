package com.mikejhill.voxlog.feature.capture.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.mikejhill.voxlog.core.designsystem.component.CategoryChip
import com.mikejhill.voxlog.core.designsystem.component.CategoryPickerSheet
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.designsystem.theme.TimerTextStyle
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.CategorySettings
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.feature.capture.R
import com.mikejhill.voxlog.feature.capture.recording.RecordingState
import kotlinx.coroutines.delay

/** Below this many minutes of free storage, the screen warns that the recording will end. */
private const val LOW_STORAGE_WARNING_MINUTES = 30L
private const val LEVEL_HISTORY_SIZE = 48
private const val TIMER_TICK_MILLIS = 200L
private val RecordingRed = Color(0xFFE53935)

/**
 * The live recording screen: a large timer, a scrolling level meter, the target category and one
 * big Stop button. There is no time limit; the only automatic stop is running out of storage.
 */
@Composable
fun RecordScreen(
    state: RecordingState,
    categories: List<Category>,
    elapsedRealtime: () -> Long,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onCategoryChange: (CategoryId) -> Unit,
    onOpenNote: (NoteId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(targetState = state, contentKey = { it::class }, label = "recordState") { target ->
            when (target) {
                is RecordingState.Recording -> RecordingContent(target, categories, elapsedRealtime, onStop, onDiscard, onCategoryChange)
                is RecordingState.Saved -> SavedContent(onOpenNote = { onOpenNote(target.noteId) }, onDone = onDone)
                is RecordingState.Failed -> FailedContent(target.reason, onDone)
                RecordingState.Idle, RecordingState.Discarded -> StartingContent()
            }
        }
    }
}

@Composable
private fun RecordingContent(
    state: RecordingState.Recording,
    categories: List<Category>,
    elapsedRealtime: () -> Long,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onCategoryChange: (CategoryId) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var isPickerOpen by remember { mutableStateOf(false) }
    val category = categories.firstOrNull { it.id == state.categoryId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecordingIndicator()
        Spacer(Modifier.weight(1f))
        ElapsedTimer(startedAt = state.startedAtElapsedMillis, elapsedRealtime = elapsedRealtime)
        Spacer(Modifier.height(Spacing.extraLarge))
        LevelMeter(level = state.inputLevel, modifier = Modifier.fillMaxWidth().height(72.dp))
        Spacer(Modifier.height(Spacing.extraLarge))
        if (category != null) {
            CategoryChip(category.name, category.colorArgb, category.iconName, onClick = { isPickerOpen = true })
        }
        if (state.remainingStorageMinutes < LOW_STORAGE_WARNING_MINUTES) {
            Text(
                pluralStringResource(R.plurals.capture_low_storage, state.remainingStorageMinutes.toInt(), state.remainingStorageMinutes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.medium),
            )
        }
        Spacer(Modifier.weight(1f))
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onStop()
            },
            modifier = Modifier
                .size(112.dp)
                .testTag("stopButton")
                .semantics { contentDescription = "Stop and save" },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(Spacing.medium))
        Text(stringResource(R.string.capture_stop_hint), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = onDiscard, modifier = Modifier.padding(top = Spacing.small)) {
            Text(stringResource(R.string.capture_discard))
        }
    }
    if (isPickerOpen) {
        CategoryPickerSheet(
            categories = categories,
            selectedId = state.categoryId,
            onSelect = {
                onCategoryChange(it)
                isPickerOpen = false
            },
            onDismiss = { isPickerOpen = false },
        )
    }
}

@Composable
private fun RecordingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Canvas(Modifier.size(12.dp)) { drawCircle(color = RecordingRed) }
        Text(stringResource(R.string.capture_recording), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ElapsedTimer(startedAt: Long, elapsedRealtime: () -> Long) {
    val currentClock by rememberUpdatedState(elapsedRealtime)
    var now by remember { mutableLongStateOf(elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = currentClock()
            delay(TIMER_TICK_MILLIS)
        }
    }
    Text(
        text = formatElapsed(now - startedAt),
        style = TimerTextStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("elapsedTimer"),
    )
}

/** Formats milliseconds as `m:ss` or `h:mm:ss`. */
internal fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
private fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(LEVEL_HISTORY_SIZE) { add(0f) } } }
    val animated by animateFloatAsState(level, label = "level")
    LaunchedEffect(animated) {
        history.removeAt(0)
        history.add(animated)
    }
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = "Input level" }) {
        val barWidth = size.width / (LEVEL_HISTORY_SIZE * 1.6f)
        history.forEachIndexed { index, value ->
            val barHeight = (value.coerceAtLeast(0.04f)) * size.height
            drawRoundRect(
                color = barColor.copy(alpha = 0.35f + 0.65f * index / LEVEL_HISTORY_SIZE),
                topLeft = Offset(index * barWidth * 1.6f, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun SavedContent(onOpenNote: () -> Unit, onDone: () -> Unit) {
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        delay(AUTO_CLOSE_MILLIS)
        currentOnDone()
    }
    StatusContent(
        title = stringResource(R.string.capture_saved_title),
        message = stringResource(R.string.capture_saved_message),
        isError = false,
    ) {
        Button(onClick = onOpenNote) { Text(stringResource(R.string.capture_open_note)) }
        TextButton(onClick = onDone) { Text(stringResource(R.string.capture_done)) }
    }
}

private const val AUTO_CLOSE_MILLIS = 1_500L

@Composable
private fun FailedContent(reason: String, onDone: () -> Unit) {
    StatusContent(title = stringResource(R.string.capture_failed_title), message = reason, isError = true) {
        Button(onClick = onDone) { Text(stringResource(R.string.capture_done)) }
    }
}

@Composable
private fun StartingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.capture_starting), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatusContent(title: String, message: String, isError: Boolean, actions: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions()
    }
}

/** Sample data shared by previews and screenshot tests. */
internal object RecordScreenSamples {
    val categories = listOf(
        Category(CategoryId.UNCATEGORIZED, "Uncategorized", 0xFF78909C, "inbox", true, 0, CategorySettings()),
        Category(CategoryId("journal"), "Journal", 0xFF5C6BC0, "book", false, 1, CategorySettings()),
    )
    val recording = RecordingState.Recording(NoteId("sample"), CategoryId("journal"), 0L, 0.4f, 900L)
}

@PreviewLightDark
@Composable
private fun RecordScreenPreview() {
    VoxLogTheme(isDynamicColorEnabled = false) {
        RecordScreen(
            state = RecordScreenSamples.recording,
            categories = RecordScreenSamples.categories,
            elapsedRealtime = { 83_000L },
            onStop = {},
            onDiscard = {},
            onCategoryChange = {},
            onOpenNote = {},
            onDone = {},
        )
    }
}
