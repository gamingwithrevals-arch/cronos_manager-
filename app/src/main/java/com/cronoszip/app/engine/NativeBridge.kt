package com.cronoszip.app.engine

import android.util.Log
import androidx.annotation.Keep

internal object NativeBridge {
    private const val TAG = "CronosZipNative"

    var loaded: Boolean = false
        private set

    init {
        loaded = try {
            System.loadLibrary("cronoszip_jni")
            true
        } catch (error: Throwable) {
            Log.e(TAG, "The native extraction engine did not load", error)
            false
        }
    }

    external fun nativeCheck(config: String): String

    external fun nativeList(config: String): String

    external fun nativeTest(config: String, callback: NativeEventCallback): String

    external fun nativeExtract(config: String, callback: NativeEventCallback): String

    external fun nativeRestore(config: String): String

    external fun nativeCompress(config: String): String

    external fun nativeCancel()
}

/** The Rust side calls onEvent with one small JSON object per event. */
class NativeEventCallback(
    private val handler: (String) -> Unit,
) {
    @Keep
    fun onEvent(event: String) {
        handler(event)
    }
}
