package com.mikejhill.voxlog.feature.capture

import com.google.common.truth.Truth.assertThat
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteFilter
import com.mikejhill.voxlog.core.model.TitleSource
import com.mikejhill.voxlog.core.testing.FakeCategoryRepository
import com.mikejhill.voxlog.core.testing.FakeNoteRepository
import com.mikejhill.voxlog.core.testing.FakeSettingsRepository
import com.mikejhill.voxlog.core.testing.MainDispatcherRule
import com.mikejhill.voxlog.core.testing.TestTime
import com.mikejhill.voxlog.feature.capture.recording.RecordingState
import com.mikejhill.voxlog.feature.capture.recording.RecordingStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val scope = TestScope(mainDispatcherRule.dispatcher)
    private val notes = FakeNoteRepository()
    private val settings = FakeSettingsRepository()
    private val recordingState = RecordingStateHolder()
    private val viewModel by lazy { CaptureViewModel(recordingState, FakeCategoryRepository(), notes, settings, TestTime.CLOCK) }

    @Test
    fun `composer starts with an editable timestamp title and cannot save an empty body`() {
        viewModel.startComposer(CategoryId.UNCATEGORIZED)

        val state = viewModel.composerState.value
        assertThat(state.title).isEqualTo("2026-10-04T08:15:00-05:00")
        assertThat(state.canSave).isFalse()
    }

    @Test
    fun `saving with the untouched timestamp keeps a DEFAULT title so auto-naming may improve it`() = scope.runTest {
        viewModel.startComposer(CategoryId.UNCATEGORIZED)
        viewModel.onTextChanged("Remember the milk")

        viewModel.saveTextNote(shortcutId = "text")
        advanceUntilIdle()

        val note = notes.observeNotes(NoteFilter()).first().single()
        assertThat(note.titleSource).isEqualTo(TitleSource.DEFAULT)
        assertThat(note.captureShortcutId).isEqualTo("text")
        assertThat(viewModel.composerState.value.isSaved).isTrue()
    }

    @Test
    fun `a typed title is saved as the user's title`() = scope.runTest {
        viewModel.startComposer(CategoryId.UNCATEGORIZED)
        viewModel.onTitleChanged("Shopping")
        viewModel.onTextChanged("milk")

        viewModel.saveTextNote(shortcutId = null)
        advanceUntilIdle()

        val note = notes.observeNotes(NoteFilter()).first().single()
        assertThat(note.title).isEqualTo("Shopping")
        assertThat(note.titleSource).isEqualTo(TitleSource.USER)
    }

    @Test
    fun `explicit category wins over the configured default`() = scope.runTest {
        settings.update { it.copy(defaultCategoryId = "journal") }

        assertThat(viewModel.resolveCategory("ideas")).isEqualTo(CategoryId("ideas"))
        assertThat(viewModel.resolveCategory(null)).isEqualTo(CategoryId("journal"))
    }

    @Test
    fun `starting a new capture clears a previous result but not an active recording`() {
        recordingState.update { RecordingState.Saved(com.mikejhill.voxlog.core.model.NoteId("old")) }

        viewModel.resetFinishedRecording()

        assertThat(viewModel.recordingState.value).isEqualTo(RecordingState.Idle)
    }
}
