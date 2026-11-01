package com.cronoszip.app.ui.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cronoszip.app.ui.background.LiquidBackground
import com.cronoszip.app.ui.theme.CronosZipTheme

/** Opens one file in Chronos Manager's own viewer: a photo viewer or a text editor. */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        setContent {
            CronosZipTheme {
                ViewerRoot(path = path, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "com.cronoszip.app.viewer.PATH"
    }
}

@Composable
private fun ViewerRoot(path: String, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        LiquidBackground(animate = false)
        if (ViewerTypes.isImage(path)) {
            PhotoViewerScreen(path = path, onClose = onClose)
        } else {
            TextEditorScreen(path = path, onClose = onClose)
        }
    }
}
