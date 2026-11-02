package com.cronoszip.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** App-wide display settings. The AMOLED switch lives here so every screen can read it. */
object AppPrefs {
    private const val PREFS = "cronoszip.prefs"
    private const val KEY_AMOLED = "amoled"
    private var loaded = false

    var amoled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        amoled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AMOLED, false)
    }

    fun setAmoled(context: Context, value: Boolean) {
        amoled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AMOLED, value)
            .apply()
    }
}
