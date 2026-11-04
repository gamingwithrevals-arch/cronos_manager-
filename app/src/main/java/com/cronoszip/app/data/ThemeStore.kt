package com.cronoszip.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The theme choice and custom colour, saved to disk and loaded on every launch. */
object ThemePrefs {
    private const val PREFS = "cronoszip.theme"
    private const val KEY_MODE = "mode"
    private const val KEY_CUSTOM = "customArgb"
    private val defaultCustomArgb: Int = 0xFF2F7CFF.toInt()
    private var loaded = false

    var mode: ThemeMode by mutableStateOf(ThemeMode.Cronos)
        private set

    var customArgb: Int by mutableStateOf(defaultCustomArgb)
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the saved theme once. Safe to call from every composition. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context)
        mode = runCatching { ThemeMode.valueOf(saved.getString(KEY_MODE, null).orEmpty()) }
            .getOrDefault(ThemeMode.Cronos)
        customArgb = saved.getInt(KEY_CUSTOM, defaultCustomArgb)
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        save(context)
    }

    fun setCustomColor(context: Context, argb: Int) {
        customArgb = argb
        mode = ThemeMode.Custom
        save(context)
    }

    private fun save(context: Context) {
        prefs(context).edit()
            .putString(KEY_MODE, mode.name)
            .putInt(KEY_CUSTOM, customArgb)
            .commit()
    }
}
