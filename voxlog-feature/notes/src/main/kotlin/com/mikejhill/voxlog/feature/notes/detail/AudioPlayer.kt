package com.mikejhill.voxlog.feature.notes.detail

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikejhill.voxlog.core.designsystem.theme.Spacing
import com.mikejhill.voxlog.feature.notes.R
import com.mikejhill.voxlog.feature.notes.format.NoteFormatting
import java.io.File
import kotlinx.coroutines.delay

private const val POSITION_POLL_MILLIS = 250L

/** Playback state for one audio file; owns the [MediaPlayer] and exposes Compose-observable state. */
@Stable
class AudioPlaybackState internal constructor(file: File) {
    private val player = MediaPlayer().apply {
        setDataSource(file.absolutePath)
        prepare()
    }

    /** Whether audio is currently playing. */
    var isPlaying: Boolean by mutableStateOf(false)
        private set

    /** Current position in milliseconds. */
    var positionMillis: Float by mutableFloatStateOf(0f)
        private set

    /** Total length in milliseconds. */
    val durationMillis: Int = player.duration

    init {
        player.setOnCompletionListener {
            isPlaying = false
            positionMillis = 0f
        }
    }

    /** Starts or pauses playback. */
    fun togglePlayback() {
        if (isPlaying) player.pause() else player.start()
        isPlaying = !isPlaying
    }

    /** Jumps to [millis]. */
    fun seekTo(millis: Float) {
        positionMillis = millis
        player.seekTo(millis.toInt())
    }

    /** Refreshes [positionMillis] from the player. */
    internal fun refreshPosition() {
        positionMillis = player.currentPosition.toFloat()
    }

    /** Releases the native player. */
    internal fun release() = player.release()
}

/** Creates an [AudioPlaybackState] for [file] that is released when it leaves composition. */
@Composable
fun rememberAudioPlaybackState(file: File): AudioPlaybackState {
    val state = remember(file) { AudioPlaybackState(file) }
    DisposableEffect(state) { onDispose { state.release() } }
    LaunchedEffect(state, state.isPlaying) {
        while (state.isPlaying) {
            state.refreshPosition()
            delay(POSITION_POLL_MILLIS)
        }
    }
    return state
}

/** Minimal play/pause + scrub control for a note's retained audio. */
@Composable
fun AudioPlayer(file: File, modifier: Modifier = Modifier) {
    val playback = rememberAudioPlaybackState(file)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            FilledTonalIconButton(onClick = playback::togglePlayback) {
                Icon(
                    if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playback.isPlaying) R.string.notes_pause else R.string.notes_play),
                )
            }
            Slider(
                value = playback.positionMillis,
                onValueChange = playback::seekTo,
                valueRange = 0f..playback.durationMillis.toFloat().coerceAtLeast(1f),
                modifier = Modifier.weight(1f),
            )
            Text(
                NoteFormatting.duration(playback.durationMillis.toLong()),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = Spacing.small),
            )
        }
    }
}
