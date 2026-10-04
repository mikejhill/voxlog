package com.mikejhill.voxlog.screenshots

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.mikejhill.voxlog.core.data.model.ModelFileState
import com.mikejhill.voxlog.core.data.search.MatchType
import com.mikejhill.voxlog.core.data.search.SearchResult
import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.HookSettings
import com.mikejhill.voxlog.core.datastore.LlmProviderType
import com.mikejhill.voxlog.core.datastore.PostProcessingSettings
import com.mikejhill.voxlog.core.datastore.ThemeMode
import com.mikejhill.voxlog.core.designsystem.theme.LocalClock
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogThemeMode
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.NoteId
import com.mikejhill.voxlog.feature.capture.ComposerUiState
import com.mikejhill.voxlog.feature.capture.recording.RecordingState
import com.mikejhill.voxlog.feature.capture.ui.RecordScreen
import com.mikejhill.voxlog.feature.capture.ui.TextComposerScreen
import com.mikejhill.voxlog.feature.notes.detail.NoteDetailActions
import com.mikejhill.voxlog.feature.notes.detail.NoteDetailScreen
import com.mikejhill.voxlog.feature.notes.detail.NoteDetailUiState
import com.mikejhill.voxlog.feature.notes.list.NotesListScreen
import com.mikejhill.voxlog.feature.notes.sample.SampleNotes
import com.mikejhill.voxlog.feature.search.SearchScreen
import com.mikejhill.voxlog.feature.search.SearchUiState
import com.mikejhill.voxlog.feature.settings.SettingsActions
import com.mikejhill.voxlog.feature.settings.SettingsUiState
import com.mikejhill.voxlog.feature.settings.categories.CategoriesScreen
import com.mikejhill.voxlog.feature.settings.categories.CategoriesUiState
import com.mikejhill.voxlog.feature.settings.categories.CategoryEditorScreen
import com.mikejhill.voxlog.feature.settings.categories.LabelsScreen
import com.mikejhill.voxlog.feature.settings.ui.SettingsNavigation
import com.mikejhill.voxlog.feature.settings.ui.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every key screen with deterministic sample data in light and dark themes.
 *
 * Files prefixed with a digit form the store listing; `x_` files are regression-only.
 * `verifyRoborazziDebug` fails CI when a screen changes unexpectedly; `recordRoborazziDebug`
 * updates the baselines, and `generateStoreScreenshots` copies the light-theme set into the
 * F-Droid listing (fastlane/metadata/android/en-US/images/phoneScreenshots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        var themeMode by mutableStateOf(VoxLogThemeMode.LIGHT)
        composeRule.setContent {
            CompositionLocalProvider(LocalClock provides SampleNotes.clock) {
                VoxLogTheme(themeMode = themeMode, isDynamicColorEnabled = false, content = content)
            }
        }
        for (mode in listOf(VoxLogThemeMode.LIGHT, VoxLogThemeMode.DARK)) {
            themeMode = mode
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("$SCREENSHOT_DIR/${name}_${mode.name.lowercase()}.png")
        }
    }

    @Test
    fun notesList() = capture("1_notes") {
        NotesListScreen(state = SampleNotes.contentState, selectedNoteId = null, actions = SampleNotes.noOpActions)
    }

    @Test
    fun recording() = capture("2_recording") {
        RecordScreen(
            state = RecordingState.Recording(NoteId("rec"), CategoryId("journal"), 0L, 0.55f, 900L),
            categories = SampleNotes.categories,
            elapsedRealtime = { 154_000L },
            onStop = {},
            onDiscard = {},
            onCategoryChange = {},
            onOpenNote = {},
            onDone = {},
        )
    }

    @Test
    fun noteDetail() = capture("3_detail") {
        NoteDetailScreen(
            state = NoteDetailUiState.Content(SampleNotes.notes.first(), SampleNotes.categories, SampleNotes.labels, audioFile = null),
            actions = NoteDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }

    @Test
    fun textComposer() = capture("4_composer") {
        TextComposerScreen(
            state = ComposerUiState(
                title = "Ideas for the garden planner",
                text = "Keep everything offline. Mirror notes into a folder so Syncthing can sync them.",
                categoryId = CategoryId("ideas"),
            ),
            categories = SampleNotes.categories,
            onTitleChange = {},
            onTextChange = {},
            onCategoryChange = {},
            onSave = {},
            onClose = {},
        )
    }

    @Test
    fun search() = capture("5_search") {
        val notes = SampleNotes.notes
        SearchScreen(
            query = "river",
            state = SearchUiState(
                query = "river",
                textMatches = listOf(SearchResult(notes[0], MatchType.FULL_TEXT, 3.0)),
                relatedMatches = listOf(SearchResult(notes[2], MatchType.SEMANTIC, 0.5)),
                categories = SampleNotes.categories,
            ),
            onQueryChange = {},
            onCategorySelect = {},
            onResultClick = {},
            onBack = {},
        )
    }

    @Test
    fun settings() = capture("6_settings") {
        SettingsScreen(
            state = SettingsUiState(
                settings = AppSettings(
                    isLocationCaptureEnabled = true,
                    themeMode = ThemeMode.SYSTEM,
                    postProcessing = PostProcessingSettings(
                        provider = LlmProviderType.ANTHROPIC,
                        model = "claude-opus-5-5",
                        shouldCleanupGlobally = true,
                    ),
                    hooks = listOf(HookSettings("h", "Home Assistant", "https://ha.example/api/webhook/voxlog")),
                ),
                categories = SampleNotes.categories,
                speechModelStates = mapOf("base.en" to ModelFileState.Ready, "small.en" to ModelFileState.Downloading(42)),
                embeddingModelState = ModelFileState.Ready,
                hasApiKey = true,
            ),
            actions = NoOpSettingsActions,
            navigation = SettingsNavigation({}, {}, {}, {}, appVersion = "1.0.0"),
        )
    }

    @Test
    fun categories() = capture("x_categories") {
        CategoriesScreen(state = CategoriesUiState(SampleNotes.categories, SampleNotes.labels), onCreate = {}, onOpen = {}, onBack = {})
    }

    @Test
    fun categoryEditor() = capture("x_category_editor") {
        CategoryEditorScreen(category = SampleNotes.categories[2], labels = SampleNotes.labels, onSave = {}, onDelete = {}, onBack = {})
    }

    @Test
    fun labels() = capture("x_labels") {
        LabelsScreen(labels = SampleNotes.labels, onRename = { _, _ -> }, onDelete = {}, onBack = {})
    }

    private object NoOpSettingsActions : SettingsActions {
        override fun setDefaultCategory(id: CategoryId) = Unit
        override fun setLocationCapture(isEnabled: Boolean) = Unit
        override fun setPreciseLocation(isPrecise: Boolean) = Unit
        override fun selectSpeechModel(id: String) = Unit
        override fun deleteSpeechModel(id: String) = Unit
        override fun setSemanticSearch(isEnabled: Boolean) = Unit
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setDynamicColor(isEnabled: Boolean) = Unit
        override fun updatePostProcessing(transform: (PostProcessingSettings) -> PostProcessingSettings) = Unit
        override fun setProvider(provider: LlmProviderType) = Unit
        override fun setApiKey(key: String) = Unit
        override fun loadAvailableModels() = Unit
        override fun saveHook(hook: HookSettings) = Unit
        override fun newHook(): HookSettings = HookSettings("", "", "")
        override fun deleteHook(id: String) = Unit
        override fun setSyncFolder(uri: Uri) = Unit
        override fun clearSyncFolder() = Unit
        override fun syncNow() = Unit
        override fun exportTo(uri: Uri) = Unit
        override fun onMessageShown() = Unit
    }

    private companion object {
        const val SCREENSHOT_DIR = "src/test/screenshots"
    }
}
