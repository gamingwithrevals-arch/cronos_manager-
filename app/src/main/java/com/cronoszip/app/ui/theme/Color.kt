package com.cronoszip.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/*
 * The four accent colours follow the theme the user picks (Cronos, Material You or custom).
 * They are state, so every screen that uses them updates straight away.
 */
var CronosBlue by mutableStateOf(Color(0xFF2F7CFF))
var CronosBlueDeep by mutableStateOf(Color(0xFF0B3D91))
var CronosCyan by mutableStateOf(Color(0xFF35D6FF))
var CronosBlueSoft by mutableStateOf(Color(0xFF8FC2FF))

val Ink = Color(0xFF02060D)
val InkBlue = Color(0xFF061529)
val NightBlue = Color(0xFF081A30)
val GlassWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF2F7FF)
val TextSecondary = Color(0xFFA9BEDB)
val Success = Color(0xFF4ADE80)
val Warning = Color(0xFFFFC857)
val Danger = Color(0xFFFF6B6B)
