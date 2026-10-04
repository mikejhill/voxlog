package com.mikejhill.voxlog.core.data.database

import com.mikejhill.voxlog.core.data.database.dao.NoteWithLabels
import com.mikejhill.voxlog.core.data.database.entity.CategoryEntity
import com.mikejhill.voxlog.core.data.database.entity.LabelEntity
import com.mikejhill.voxlog.core.data.database.entity.NoteEntity
import com.mikejhill.voxlog.core.model.CaptureMethod
import com.mikejhill.voxlog.core.model.Category
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.CategorySettings
import com.mikejhill.voxlog.core.model.GeoLocation
import com.mikejhill.voxlog.core.model.Label
import com.mikejhill.voxlog.core.model.LabelId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.core.model.NoteStatus
import com.mikejhill.voxlog.core.model.TitleSource
import java.time.Instant

/** Converts a note row and its labels to the domain model. */
internal fun NoteWithLabels.toDomain(): Note = with(note) {
    Note(
        id = NoteId(id),
        categoryId = CategoryId(categoryId),
        captureMethod = CaptureMethod.valueOf(captureMethod),
        title = title,
        titleSource = TitleSource.valueOf(titleSource),
        text = text,
        rawTranscript = rawTranscript,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
        durationMillis = durationMillis,
        audioFileName = audioFileName,
        location = toLocation(),
        language = language,
        status = NoteStatus.valueOf(status),
        transcriptionProgressPercent = transcriptionProgressPercent,
        labelIds = labels.map { LabelId(it.id) }.toSet(),
        captureShortcutId = captureShortcutId,
        processingLog = processingLog,
    )
}

private fun NoteEntity.toLocation(): GeoLocation? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return GeoLocation(lat, lon, accuracyMeters, placeName)
}

/** Converts a category row to the domain model. */
internal fun CategoryEntity.toDomain(): Category = Category(
    id = CategoryId(id),
    name = name,
    colorArgb = colorArgb,
    iconName = iconName,
    isSystem = isSystem,
    sortOrder = sortOrder,
    settings = CategorySettings(
        shouldSaveAudio = shouldSaveAudio,
        defaultLabelIds = defaultLabelIds.map(::LabelId).toSet(),
        shouldAutoCleanup = shouldAutoCleanup,
        shouldAutoLabel = shouldAutoLabel,
        shouldAutoName = shouldAutoName,
    ),
)

/** Converts a domain category to its row form. */
internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id.value,
    name = name,
    colorArgb = colorArgb,
    iconName = iconName,
    isSystem = isSystem,
    sortOrder = sortOrder,
    shouldSaveAudio = settings.shouldSaveAudio,
    defaultLabelIds = settings.defaultLabelIds.map { it.value }.sorted(),
    shouldAutoCleanup = settings.shouldAutoCleanup,
    shouldAutoLabel = settings.shouldAutoLabel,
    shouldAutoName = settings.shouldAutoName,
)

/** Converts a label row to the domain model. */
internal fun LabelEntity.toDomain(): Label = Label(LabelId(id), name, colorArgb)

/** Converts a domain label to its row form. */
internal fun Label.toEntity(): LabelEntity = LabelEntity(id.value, name.trim(), colorArgb)
