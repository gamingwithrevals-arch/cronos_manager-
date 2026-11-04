package com.cronoszip.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Available app theme palettes. */
enum class ThemeMode(val label: String, val description: String) {
    Cronos("Cronos blue", "The original Cronos Manager look."),
    Material("Phone colours (Material You)", "Colours taken from your wallpaper. Needs Android 12 or newer."),
    Custom("Custom colour", "One accent colour you pick, used across the whole app."),
}

/** App-wide display settings, persisted immediately and restored on launch. */
object AppPrefs {
    private const val PREFS = "cronoszip.prefs"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_CUSTOM_THEME_COLOR = "custom_theme_color"
    private var loaded = false

    var amoled by mutableStateOf(false)
        private set

    var themeMode by mutableStateOf(ThemeMode.Cronos)
        private set

    var customThemeColor by mutableStateOf(0xFF2F7CFF.toInt())
        private set

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        amoled = prefs.getBoolean(KEY_AMOLED, false)
        themeMode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.Cronos
        customThemeColor = prefs.getInt(KEY_CUSTOM_THEME_COLOR, 0xFF2F7CFF.toInt())
    }

    fun setAmoled(context: Context, value: Boolean) {
        amoled = value
        preferences(context).edit().putBoolean(KEY_AMOLED, value).commit()
    }

    fun setTheme(context: Context, mode: ThemeMode, customColor: Int = customThemeColor) {
        load(context)
        themeMode = mode
        customThemeColor = customColor
        preferences(context).edit()
            .putString(KEY_THEME_MODE, mode.name)
            .putInt(KEY_CUSTOM_THEME_COLOR, customColor)
            .commit()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
