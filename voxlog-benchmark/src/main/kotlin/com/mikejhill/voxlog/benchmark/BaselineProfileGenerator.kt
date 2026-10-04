package com.mikejhill.voxlog.benchmark

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
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
        startActivityAndWait(Intent("com.mikejhill.voxlog.action.WRITE_TEXT").setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.pressBack()
        startActivityAndWait(Intent("com.mikejhill.voxlog.action.RECORD_VOICE").setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.wait(Until.hasObject(By.text("Recording")), TIMEOUT_MILLIS)
        device.wait(Until.findObject(By.res("stopButton")), TIMEOUT_MILLIS)?.click()
        device.wait(Until.hasObject(By.text("Saved")), TIMEOUT_MILLIS)
    }

    private companion object {
        const val PACKAGE = "com.mikejhill.voxlog"
        const val TIMEOUT_MILLIS = 5_000L
    }
}
