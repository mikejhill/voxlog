package com.mikejhill.voxlog.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** User-selectable appearance. Mirrors the persisted setting without depending on the datastore module. */
enum class VoxLogThemeMode {
    /** Follow the system. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark. */
    DARK,
}

/** Branded fallback palette (deep indigo with a warm coral accent) used when dynamic color is off or unavailable. */
private val LightBrandColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF9C4146),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDADA),
    onTertiaryContainer = Color(0xFF40000A),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
    error = Color(0xFFBA1A1A),
)

private val DarkBrandColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB3B4),
    onTertiary = Color(0xFF5F131B),
    tertiaryContainer = Color(0xFF7E2A30),
    onTertiaryContainer = Color(0xFFFFDADA),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
    error = Color(0xFFFFB4AB),
)

private val VoxLogTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Medium),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
    )
}

private val VoxLogShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Monospaced style for the recording timer, so digits don't shift as they change. */
val TimerTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 64.sp,
    letterSpacing = (-1).sp,
)

/**
 * VoxLog's Material 3 theme. Uses wallpaper-based dynamic color (Material You) on Android 12+ when
 * [isDynamicColorEnabled], otherwise the branded palette.
 */
@Composable
fun VoxLogTheme(
    themeMode: VoxLogThemeMode = VoxLogThemeMode.SYSTEM,
    isDynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        VoxLogThemeMode.SYSTEM -> isSystemInDarkTheme()
        VoxLogThemeMode.LIGHT -> false
        VoxLogThemeMode.DARK -> true
    }
    val canUseDynamicColor = isDynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        canUseDynamicColor && isDark -> dynamicDarkColorScheme(LocalContext.current)
        canUseDynamicColor -> dynamicLightColorScheme(LocalContext.current)
        isDark -> DarkBrandColors
        else -> LightBrandColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = VoxLogTypography, shapes = VoxLogShapes, content = content)
}
