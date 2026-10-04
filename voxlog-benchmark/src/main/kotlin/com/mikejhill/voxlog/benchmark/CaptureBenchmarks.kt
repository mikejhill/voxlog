package com.mikejhill.voxlog.benchmark

import android.content.Intent
import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeFalse
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Performance budgets for the capture path (see docs/testing.md#performance-budgets):
 * - cold start from the voice shortcut to the microphone recording: median ≤ 500 ms
 * - stop to note saved: median ≤ 150 ms
 * - text shortcut to an editable composer: median ≤ 400 ms
 * - notes list scrolling: < 1% janky frames
 *
 * CI compares results against these budgets with `scripts/check_benchmarks.py`.
 *
 * Tests run in name order. Only the last one saves recordings, because saving queues on-device
 * transcription whose CPU load would distort the startup measurements that follow.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CaptureBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun a_voiceShortcutColdStartToRecording() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), TraceSectionMetric("VoxLog.recordingStart")),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = {
            grantRecordAudio()
            forceStop()
        },
    ) {
        startActivityAndWait(captureIntent(ACTION_RECORD_VOICE))
        check(device.wait(Until.hasObject(By.text("Recording").pkg(TARGET_PACKAGE)), TIMEOUT_MILLIS))
        withFreshObject(By.text("Discard").pkg(TARGET_PACKAGE)) { it.click() }
        check(device.wait(Until.gone(By.text("Recording").pkg(TARGET_PACKAGE)), TIMEOUT_MILLIS))
    }

    @Test
    fun d_stopToSaved() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(TraceSectionMetric("VoxLog.recordingStop")),
        iterations = ITERATIONS,
        setupBlock = {
            grantRecordAudio()
            forceStop()
            startActivityAndWait(captureIntent(ACTION_RECORD_VOICE))
            check(device.wait(Until.hasObject(By.text("Recording").pkg(TARGET_PACKAGE)), TIMEOUT_MILLIS))
        },
    ) {
        // Saved auto-closes after 1.5 seconds; waiting for accessibility idle can miss it entirely.
        val configurator = Configurator.getInstance()
        val previousTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0
        try {
            withFreshObject(By.res("stopButton").pkg(TARGET_PACKAGE)) { it.click() }
            check(device.wait(Until.hasObject(By.text("Saved").pkg(TARGET_PACKAGE)), TIMEOUT_MILLIS))
        } finally {
            configurator.waitForIdleTimeout = previousTimeout
        }
    }

    @Test
    fun b_textShortcutColdStart() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { forceStop() },
    ) {
        startActivityAndWait(captureIntent(ACTION_WRITE_TEXT))
    }

    @Test
    fun c_notesListScroll() {
        assumeFalse(
            "FrameTimingMetric requires frame timeline data unavailable on ranchu/goldfish software emulators",
            Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"),
        )
        measureNotesListScroll()
    }

    private fun measureNotesListScroll() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            seedNotes()
            startActivityAndWait()
        },
    ) {
        check(device.wait(Until.hasObject(By.res("notesList").pkg(TARGET_PACKAGE)), TIMEOUT_MILLIS))
        // Coordinate swipes avoid stale node references while the list recomposes.
        val x = device.displayWidth / 2
        repeat(SCROLLS) {
            device.swipe(x, device.displayHeight * SWIPE_FROM / PERCENT, x, device.displayHeight * SWIPE_TO / PERCENT, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    /** Creates enough text notes to make the list scrollable; runs only when the list is short. */
    private fun MacrobenchmarkScope.seedNotes() {
        startActivityAndWait()
        if (device.findObjects(By.res("noteCard").pkg(TARGET_PACKAGE)).size >= MIN_VISIBLE_CARDS) return
        repeat(SEEDED_NOTES) { index ->
            startActivityAndWait(captureIntent(ACTION_WRITE_TEXT))
            withFreshObject(By.res("bodyField").pkg(TARGET_PACKAGE)) {
                it.text = "Benchmark note $index with enough text to fill a card."
            }
            withFreshObject(By.res("saveTextNote").pkg(TARGET_PACKAGE).enabled(true)) { it.click() }
            check(device.wait(Until.gone(By.res("saveTextNote").pkg(TARGET_PACKAGE)), TIMEOUT_MILLIS))
        }
    }

    /** Reacquires Compose nodes when recomposition invalidates an accessibility reference. */
    private fun MacrobenchmarkScope.withFreshObject(selector: BySelector, action: (UiObject2) -> Unit) {
        repeat(NODE_ATTEMPTS) { attempt ->
            try {
                action(checkNotNull(device.wait(Until.findObject(selector), TIMEOUT_MILLIS)) { "Missing UI node: $selector" })
                return
            } catch (exception: StaleObjectException) {
                if (attempt == NODE_ATTEMPTS - 1) throw exception
            }
        }
    }

    /**
     * Force-stops the app so background work (transcriptions queued by earlier iterations) cannot
     * relaunch the process before a cold start is measured.
     */
    private fun MacrobenchmarkScope.forceStop() {
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    private fun MacrobenchmarkScope.grantRecordAudio() {
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.RECORD_AUDIO")
        device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
    }

    private fun captureIntent(action: String) = Intent(action)
        .setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.feature.capture.CaptureActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val TARGET_PACKAGE = "com.mikejhill.voxlog"
        const val ACTION_RECORD_VOICE = "com.mikejhill.voxlog.action.RECORD_VOICE"
        const val ACTION_WRITE_TEXT = "com.mikejhill.voxlog.action.WRITE_TEXT"
        const val ITERATIONS = 10
        const val TIMEOUT_MILLIS = 5_000L
        const val SCROLLS = 5
        const val SWIPE_FROM = 80
        const val SWIPE_TO = 20
        const val PERCENT = 100
        const val SWIPE_STEPS = 10
        const val MIN_VISIBLE_CARDS = 3
        const val SEEDED_NOTES = 25
        const val NODE_ATTEMPTS = 3
    }
}
