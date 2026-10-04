package com.mikejhill.voxlog.benchmark

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile for the paths where startup latency matters most: the voice and
 * text capture shortcuts and opening the notes list. Run with
 * `./gradlew :voxlog-app:generateBaselineProfile`; the result is committed to
 * `voxlog-app/src/main/generated/baselineProfiles/` so release builds stay reproducible.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        device.executeShellCommand("pm grant $PACKAGE android.permission.RECORD_AUDIO")
        startActivityAndWait()
        startActivityAndWait(captureIntent("com.mikejhill.voxlog.action.WRITE_TEXT"))
        device.pressBack()
        device.executeShellCommand("am force-stop $PACKAGE")
        startActivityAndWait(captureIntent("com.mikejhill.voxlog.action.RECORD_VOICE"))
        check(device.wait(Until.hasObject(By.text("Recording").pkg(PACKAGE)), TIMEOUT_MILLIS))
        val configurator = Configurator.getInstance()
        val previousTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0
        try {
            checkNotNull(device.wait(Until.findObject(By.res("stopButton").pkg(PACKAGE)), TIMEOUT_MILLIS)).click()
            check(device.wait(Until.hasObject(By.text("Saved").pkg(PACKAGE)), TIMEOUT_MILLIS))
        } finally {
            configurator.waitForIdleTimeout = previousTimeout
        }
    }

    private fun captureIntent(action: String) = Intent(action)
        .setClassName(PACKAGE, "$PACKAGE.feature.capture.CaptureActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val PACKAGE = "com.mikejhill.voxlog"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
