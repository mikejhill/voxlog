package com.mikejhill.voxlog

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows through the real app on a device: launcher-shortcut intents, the foreground
 * recording service, persistence, the notes list and search. Uses UI Automator so the flows cross
 * activities exactly as a user's shortcut tap would.
 */
@RunWith(AndroidJUnit4::class)
class CaptureEndToEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName

    @Before
    fun grantPermissions() {
        device.executeShellCommand("pm grant $packageName android.permission.RECORD_AUDIO")
        device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }

    private fun launch(action: String) {
        val intent = Intent(action).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.targetContext.startActivity(intent)
    }

    private fun openMainScreen() {
        val intent = instrumentation.targetContext.packageManager.getLaunchIntentForPackage(packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.targetContext.startActivity(intent)
        device.wait(Until.hasObject(By.text("VoxLog")), TIMEOUT_MILLIS)
    }

    @Test
    fun textShortcutSavesANoteThatAppearsInTheListAndSearch() {
        val marker = "e2e-${UUID.randomUUID().toString().take(8)}"
        launch("com.mikejhill.voxlog.action.WRITE_TEXT")
        device.wait(Until.findObject(By.res("bodyField")), TIMEOUT_MILLIS)!!.text = "Blood pressure reading $marker"
        device.findObject(By.res("saveTextNote")).click()

        openMainScreen()
        assertThat(device.wait(Until.hasObject(By.textContains(marker)), TIMEOUT_MILLIS)).isTrue()

        device.findObject(By.desc("Search")).click()
        device.wait(Until.findObject(By.res("searchField")), TIMEOUT_MILLIS)!!.text = marker
        assertThat(device.wait(Until.hasObject(By.res("searchResults").hasDescendant(By.textContains(marker))), TIMEOUT_MILLIS)).isTrue()
    }

    @Test
    fun voiceShortcutRecordsUntilStoppedAndSavesImmediately() {
        launch("com.mikejhill.voxlog.action.RECORD_VOICE")
        assertThat(device.wait(Until.hasObject(By.text("Recording")), TIMEOUT_MILLIS)).isTrue()

        // Recording keeps going on its own: nothing stops it automatically.
        device.waitForIdle()
        Thread.sleep(RECORDING_MILLIS)
        assertThat(device.hasObject(By.text("Recording"))).isTrue()

        device.findObject(By.desc("Stop and save")).click()
        assertThat(device.wait(Until.hasObject(By.text("Saved")), TIMEOUT_MILLIS)).isTrue()

        openMainScreen()
        assertThat(device.wait(Until.hasObject(By.res("noteCard")), TIMEOUT_MILLIS)).isTrue()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val RECORDING_MILLIS = 3_000L
    }
}
