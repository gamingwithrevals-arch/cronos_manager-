package com.cronoszip.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cronoszip.app.data.AppPrefs
import com.cronoszip.app.data.ThemeMode
import com.cronoszip.app.ui.theme.SolidSurface
import com.cronoszip.app.ui.theme.SolidSurfaceRim

/**
 * The app's panel surface. Normally a translucent liquid-glass panel with a bright rim.
 * Pure Material You swaps it for a solid Material 3 container, and AMOLED makes it solid black.
 */
fun Modifier.liquidGlass(radius: Dp = 22.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    if (AppPrefs.themeMode == ThemeMode.PureMaterial) {
        return this
            .clip(shape)
            .background(SolidSurface)
            .border(BorderStroke(1.dp, SolidSurfaceRim), shape)
    }
    if (AppPrefs.amoled) {
        return this
            .clip(shape)
            .background(Color.Black)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape)
    }
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.09f),
                ),
            ),
        )
        .border(
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.22f),
                    ),
                ),
            ),
            shape,
        )
}
