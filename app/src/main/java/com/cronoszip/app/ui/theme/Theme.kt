package com.cronoszip.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cronoszip.app.data.ThemeMode
import com.cronoszip.app.data.ThemePrefs

/** The four accent colours that the theme controls. */
private data class AccentSet(
    val blue: Color,
    val blueDeep: Color,
    val cyan: Color,
    val blueSoft: Color,
)

private val cronosAccents = AccentSet(
    blue = Color(0xFF2F7CFF),
    blueDeep = Color(0xFF0B3D91),
    cyan = Color(0xFF35D6FF),
    blueSoft = Color(0xFF8FC2FF),
)

/** Custom colour: the chosen colour is the main accent, and the others are tints and shades of it. */
private fun customAccents(seed: Color): AccentSet = AccentSet(
    blue = seed,
    blueDeep = lerp(seed, Color.Black, 0.45f),
    cyan = lerp(seed, Color.White, 0.25f),
    blueSoft = lerp(seed, Color.White, 0.55f),
)

/** Material You: the accents come from the phone's wallpaper colours. */
private fun materialAccents(scheme: ColorScheme): AccentSet = AccentSet(
    blue = scheme.primary,
    blueDeep = scheme.primaryContainer,
    cyan = scheme.tertiary,
    blueSoft = lerp(scheme.primary, Color.White, 0.35f),
)

private fun applyAccents(set: AccentSet) {
    CronosBlue = set.blue
    CronosBlueDeep = set.blueDeep
    CronosCyan = set.cyan
    CronosBlueSoft = set.blueSoft
}

/** The Cronos colour scheme, built from the current accent colours. */
private fun cronosScheme(): ColorScheme = darkColorScheme(
    primary = CronosBlue,
    onPrimary = TextPrimary,
    primaryContainer = CronosBlueDeep,
    onPrimaryContainer = ColorTokens.onPrimaryContainer,
    secondary = CronosCyan,
    onSecondary = Ink,
    secondaryContainer = NightBlue,
    onSecondaryContainer = CronosBlueSoft,
    tertiary = CronosCyan,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = NightBlue,
    onSurface = TextPrimary,
    surfaceVariant = ColorTokens.surfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceTint = CronosBlue,
    outline = ColorTokens.outline,
    outlineVariant = ColorTokens.outlineVariant,
    error = Danger,
    onError = Ink,
    errorContainer = ColorTokens.errorContainer,
    onErrorContainer = ColorTokens.onErrorContainer,
    scrim = Ink,
)

/**
 * Builds the colour scheme for the chosen mode and sets the accent colours to match.
 * Material You needs Android 12 or newer; older phones fall back to Cronos blue.
 */
private fun buildTheme(context: Context, mode: ThemeMode, customArgb: Int): ColorScheme = when (mode) {
    ThemeMode.Cronos -> {
        applyAccents(cronosAccents)
        cronosScheme()
    }
    ThemeMode.Custom -> {
        applyAccents(customAccents(Color(customArgb)))
        cronosScheme()
    }
    ThemeMode.Material -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wallpaper = dynamicDarkColorScheme(context)
            applyAccents(materialAccents(wallpaper))
            wallpaper
        } else {
            applyAccents(cronosAccents)
            cronosScheme()
        }
    }
}

private object ColorTokens {
    val onPrimaryContainer = Color(0xFFD9E8FF)
    val surfaceVariant = Color(0xFF102238)
    val outline = Color(0xFF3A5478)
    val outlineVariant = Color(0xFF1C3350)
    val errorContainer = Color(0xFF4A1420)
    val onErrorContainer = Color(0xFFFFD9DD)
}

private val CronosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun CronosZipTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember(context) { ThemePrefs.load(context) }
    val mode = ThemePrefs.mode
    val customArgb = ThemePrefs.customArgb
    val scheme = remember(mode, customArgb) { buildTheme(context, mode, customArgb) }
    MaterialTheme(
        colorScheme = scheme,
        typography = CronosTypography,
        shapes = CronosShapes,
        content = content,
    )
}
