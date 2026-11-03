package com.cronoszip.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.data.AppPrefs
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Largest file the editor opens. Bigger files are refused, so saving can never cut them short. */
private const val EDITOR_MAX_BYTES = 512 * 1024

/** Reads a whole text file from phone storage or a mounted storage. Refuses files that are too large. */
private fun readEditableText(context: Context, item: FileItem): Result<String> = runCatching {
    val bytes: ByteArray = if (SafStorage.isSaf(item.path)) {
        val uri = Uri.parse(item.path)
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not open this file.")
    } else {
        File(item.path).readBytes()
    }
    if (bytes.size > EDITOR_MAX_BYTES) {
        throw IllegalStateException("This file is larger than 512 KB, so it can't be edited safely here.")
    }
    String(bytes, Charsets.UTF_8)
}

/** Writes the whole text back to phone storage or a mounted storage. */
private fun writeEditableText(context: Context, item: FileItem, content: String): Result<Unit> = runCatching {
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (SafStorage.isSaf(item.path)) {
        val uri = Uri.parse(item.path)
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("This storage does not allow writing to this file.")
        output.use {
            it.write(bytes)
            it.flush()
        }
    } else {
        File(item.path).writeBytes(bytes)
    }
}

/**
 * Full-screen text and config editor. It is drawn in the main window, not in a Dialog, so the
 * status bar, navigation bar and keyboard are handled the same way as the rest of the app.
 * The header stays at the top, the text area shrinks to fit, and the Cancel and Save buttons
 * always stay visible above the bottom bars and the keyboard.
 */
@Composable
fun FileTextEditor(
    item: FileItem,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(item.path) { mutableStateOf("") }
    var loading by remember(item.path) { mutableStateOf(true) }
    var saving by remember(item.path) { mutableStateOf(false) }
    var loadError by remember(item.path) { mutableStateOf<String?>(null) }

    // The Android back button closes the editor first, before it reaches the file manager.
    BackHandler(enabled = true) {
        if (!saving) onDismiss()
    }

    LaunchedEffect(item.path) {
        loading = true
        loadError = null
        val result = withContext(Dispatchers.IO) { readEditableText(context, item) }
        result
            .onSuccess { text = it }
            .onFailure { loadError = it.message ?: "Could not read this file as text." }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (AppPrefs.amoled) Color.Black else Color(0xFF07101E))
            .pointerInput(Unit) {
                // Swallows taps on the empty background so they never reach the file list behind.
                detectTapGestures(onTap = { })
            }
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TEXT & CONFIG EDITOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = CronosCyan,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { if (!saving) onDismiss() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .liquidGlass(20.dp)
                .padding(12.dp),
        ) {
            when {
                loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CronosCyan, strokeWidth = 2.dp)
                }
                loadError != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = loadError.orEmpty(),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    cursorBrush = SolidColor(CronosCyan),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                text = "Cancel",
                modifier = Modifier.weight(1f),
                onClick = { if (!saving) onDismiss() },
            )
            PrimaryButton(
                text = if (saving) "Saving…" else "Save changes",
                modifier = Modifier.weight(1f),
                enabled = !loading && !saving && loadError == null,
                onClick = {
                    saving = true
                    val updated = text
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            writeEditableText(context, item, updated)
                        }
                        saving = false
                        result
                            .onSuccess {
                                onSaved()
                                onDismiss()
                            }
                            .onFailure { onError(it.message ?: "Could not save changes.") }
                    }
                },
            )
        }
    }
}
