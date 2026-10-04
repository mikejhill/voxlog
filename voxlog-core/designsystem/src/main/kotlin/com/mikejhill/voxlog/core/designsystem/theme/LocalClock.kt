package com.mikejhill.voxlog.core.designsystem.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock

/**
 * The clock UI uses for relative times ("5 min. ago") and local date display. Production uses the
 * system clock; previews and screenshot tests provide a fixed one so output is deterministic.
 */
val LocalClock: ProvidableCompositionLocal<Clock> = staticCompositionLocalOf { Clock.systemDefaultZone() }
