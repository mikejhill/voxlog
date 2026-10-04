package com.mikejhill.voxlog.feature.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.designsystem.component.ScreenRoot
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogThemeMode
import com.mikejhill.voxlog.core.model.CaptureIntents
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.feature.capture.recording.RecordingService
import com.mikejhill.voxlog.feature.capture.recording.RecordingState
import com.mikejhill.voxlog.feature.capture.ui.RecordScreen
import com.mikejhill.voxlog.feature.capture.ui.TextComposerScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Entry point for every capture (launcher shortcuts, the Quick Settings tile, in-app buttons).
 * For voice, recording starts in [onCreate] before any UI is composed, so speech begins being
 * captured as early as possible.
 */
@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val viewModel: CaptureViewModel by viewModels()

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startRecording() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isVoice = intent.action != CaptureIntents.ACTION_WRITE_TEXT
        if (isVoice && savedInstanceState == null) beginVoiceCapture()
        if (!isVoice && savedInstanceState == null) beginTextCapture()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            VoxLogTheme(VoxLogThemeMode.valueOf(settings.themeMode.name), settings.isDynamicColorEnabled) {
                ScreenRoot { if (isVoice) VoiceContent() else TextContent() }
            }
        }
    }

    @Composable
    private fun VoiceContent() {
        val state by viewModel.recordingState.collectAsStateWithLifecycle()
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        RecordScreen(
            state = state,
            categories = categories,
            elapsedRealtime = SystemClock::elapsedRealtime,
            onStop = { RecordingService.stop(this) },
            onDiscard = { RecordingService.discard(this) },
            onCategoryChange = { RecordingService.changeCategory(this, it) },
            onOpenNote = ::openNote,
            onDone = ::finish,
        )
        LaunchedEffect(state) {
            if (state == RecordingState.Discarded) finish()
        }
    }

    @Composable
    private fun TextContent() {
        val state by viewModel.composerState.collectAsStateWithLifecycle()
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        LaunchedEffect(state.isSaved) { if (state.isSaved) finish() }
        TextComposerScreen(
            state = state,
            categories = categories,
            onTitleChange = viewModel::onTitleChanged,
            onTextChange = viewModel::onTextChanged,
            onCategoryChange = viewModel::onComposerCategoryChanged,
            onSave = { viewModel.saveTextNote(intent.getStringExtra(CaptureIntents.EXTRA_SHORTCUT_ID)) },
            onClose = ::finish,
        )
    }

    private fun beginVoiceCapture() {
        val isAlreadyRecording = viewModel.recordingState.value is RecordingState.Recording
        if (isAlreadyRecording || intent.getBooleanExtra(RecordingService.EXTRA_RESUME_ONLY, false)) return
        viewModel.resetFinishedRecording()
        val isGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isGranted) startRecording() else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        RecordingService.start(
            context = this,
            categoryId = intent.getStringExtra(CaptureIntents.EXTRA_CATEGORY_ID)?.let(::CategoryId),
            shortcutId = intent.getStringExtra(CaptureIntents.EXTRA_SHORTCUT_ID),
        )
    }

    private fun beginTextCapture() {
        lifecycleScope.launch {
            viewModel.startComposer(viewModel.resolveCategory(intent.getStringExtra(CaptureIntents.EXTRA_CATEGORY_ID)))
        }
    }

    private fun openNote(noteId: NoteId) {
        startActivity(Intent(Intent.ACTION_VIEW, (CaptureIntents.NOTE_URI_PREFIX + noteId.value).toUri()).setPackage(packageName))
        finish()
    }
}
