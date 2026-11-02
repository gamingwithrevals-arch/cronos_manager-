package com.cronoszip.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide display settings. The AMOLED switch is saved to disk immediately and read on every launch. */
object AppPrefs {
    private const val PREFS = "cronoszip.prefs"
    private const val KEY_AMOLED = "amoled"
    private var loaded = false

    var amoled by mutableStateOf(false)
        private set

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        amoled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AMOLED, false)
    }

    fun setAmoled(context: Context, value: Boolean) {
        amoled = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AMOLED, value)
            .commit()
    }
}
