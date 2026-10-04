package com.mikejhill.voxlog

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mikejhill.voxlog.core.datastore.AppSettings
import com.mikejhill.voxlog.core.datastore.SettingsRepository
import com.mikejhill.voxlog.core.designsystem.component.ScreenRoot
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogTheme
import com.mikejhill.voxlog.core.designsystem.theme.VoxLogThemeMode
import com.mikejhill.voxlog.core.model.CaptureIntents
import com.mikejhill.voxlog.core.model.CategoryId
import com.mikejhill.voxlog.core.model.Note
import com.mikejhill.voxlog.feature.notes.NotesNavigationCallbacks
import com.mikejhill.voxlog.feature.notes.NotesRoute
import com.mikejhill.voxlog.feature.notes.notesScreen
import com.mikejhill.voxlog.feature.search.SearchRoute
import com.mikejhill.voxlog.feature.search.searchScreen
import com.mikejhill.voxlog.feature.settings.SettingsCallbacks
import com.mikejhill.voxlog.feature.settings.SettingsRoute
import com.mikejhill.voxlog.feature.settings.settingsScreens
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Hosts the main navigation graph: notes (list/detail), search and settings. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkNoteId = noteIdFrom(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            VoxLogTheme(VoxLogThemeMode.valueOf(settings.themeMode.name), settings.isDynamicColorEnabled) {
                ScreenRoot {
                    RequestNotificationPermissionOnce()
                    VoxLogNavHost(rememberNavController(), deepLinkNoteId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    @Composable
    private fun VoxLogNavHost(navController: NavHostController, initialNoteId: String?) {
        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        NavHost(navController = navController, startDestination = NotesRoute(initialNoteId)) {
            notesScreen(
                NotesNavigationCallbacks(
                    onRecordVoice = { startCapture(CaptureIntents.ACTION_RECORD_VOICE, it) },
                    onWriteText = { startCapture(CaptureIntents.ACTION_WRITE_TEXT, it) },
                    onSearch = { navController.navigate(SearchRoute) },
                    onSettings = { navController.navigate(SettingsRoute) },
                    onShare = ::shareNote,
                ),
            )
            searchScreen(
                onOpenNote = { navController.navigate(NotesRoute(it.value)) },
                onBack = { navController.popBackStack() },
            )
            settingsScreens(
                SettingsCallbacks(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) },
                    onRequestLocationPermission = { isPrecise ->
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (isPrecise) add(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        locationPermission.launch(permissions.toTypedArray())
                    },
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            )
        }
    }

    @Composable
    private fun RequestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    private fun startCapture(action: String, categoryId: CategoryId?) {
        startActivity(Intent(action).setPackage(packageName).putExtra(CaptureIntents.EXTRA_CATEGORY_ID, categoryId?.value))
    }

    private fun shareNote(note: Note) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, note.title)
            .putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.text}")
        startActivity(Intent.createChooser(send, null))
    }

    private fun noteIdFrom(intent: Intent?): String? =
        intent?.dataString?.takeIf { it.startsWith(CaptureIntents.NOTE_URI_PREFIX) }?.removePrefix(CaptureIntents.NOTE_URI_PREFIX)
}
