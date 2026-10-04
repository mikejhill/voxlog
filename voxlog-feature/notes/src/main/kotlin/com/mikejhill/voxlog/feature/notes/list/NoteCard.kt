package com.mikejhill.voxlog.feature.notes.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikejhill.voxlog.core.designsystem.component.CategoryBadge
import com.mikejhill.voxlog.core.designsystem.component.LabelTag
import com.mikejhill.voxlog.core.designsystem.theme.LocalClock
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.feature.notes.R
import com.mikejhill.voxlog.feature.notes.format.NoteFormatting

/** One note in the list: title, a short excerpt, category, labels, capture info and processing state. */
@Composable
fun NoteCard(
    note: Note,
    category: Category?,
    labels: List<Label>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("noteCard"),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        },
    ) {
        Column(Modifier.padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            NoteExcerpt(note)
            MetadataRow(note, category)
            if (labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
                    labels.forEach { LabelTag(it.name, it.colorArgb) }
                }
            }
        }
    }
}

@Composable
private fun NoteExcerpt(note: Note) {
    when (note.status) {
        NoteStatus.TRANSCRIBING -> {
            val progress = note.transcriptionProgressPercent ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    note.text.ifBlank { stringResource(R.string.notes_transcribing, progress) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }

        NoteStatus.FAILED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                note.processingLog ?: stringResource(R.string.notes_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> if (note.text.isNotBlank()) {
            Text(
                note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetadataRow(note: Note, category: Category?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        category?.let { CategoryBadge(it.name, it.colorArgb, it.iconName) }
        val isVoice = note.captureMethod == CaptureMethod.VOICE_RECORDING
        Icon(
            if (isVoice) Icons.Outlined.Mic else Icons.AutoMirrored.Outlined.Notes,
            contentDescription = stringResource(if (isVoice) R.string.notes_voice_note else R.string.notes_text_note),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        note.durationMillis?.let {
            Text(
                NoteFormatting.duration(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (note.hasAudio) {
            Icon(
                Icons.Outlined.GraphicEq,
                contentDescription = stringResource(R.string.notes_has_audio),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            NoteFormatting.relativeTime(note.createdAt, LocalClock.current.millis()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
