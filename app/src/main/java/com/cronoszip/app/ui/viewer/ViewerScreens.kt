package com.cronoszip.app.ui.viewer

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cronoszip.app.ui.components.BackCircle
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.components.SolidDialogCard
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.util.formatBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which file types the built-in viewers open. Everything else goes to other apps. */
object ViewerTypes {
    private val textExtensions = setOf(
        "txt", "md", "log", "json", "xml", "html", "htm", "csv", "kt", "java", "gradle",
        "properties", "sh", "yml", "yaml", "ini", "cfg", "conf", "toml", "smali", "js", "css",
        "py", "c", "h", "cpp",
    )
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun isText(path: String): Boolean = extensionOf(path) in textExtensions

    fun isImage(path: String): Boolean = extensionOf(path) in imageExtensions

    private fun extensionOf(path: String): String =
        File(path).name.substringAfterLast('.', "").lowercase()
}

/** Largest file the editor will open. Bigger files are too slow to edit on a phone. */
private const val MAX_EDIT_BYTES = 2L * 1024 * 1024

@Composable
fun TextEditorScreen(path: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }
    var loading by remember(path) { mutableStateOf(true) }
    var loadError by remember(path) { mutableStateOf<String?>(null) }
    var text by remember(path) { mutableStateOf("") }
    var savedText by remember(path) { mutableStateOf("") }
    var saving by remember(path) { mutableStateOf(false) }
    var status by remember(path) { mutableStateOf<String?>(null) }
    var confirmClose by remember(path) { mutableStateOf(false) }
    val dirty = text != savedText

    LaunchedEffect(path) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    !file.canRead() -> throw IllegalStateException("This file can't be read.")
                    file.length() > MAX_EDIT_BYTES ->
                        throw IllegalStateException("This file is larger than 2 MB, so it can't be edited here.")
                    else -> file.readText()
                }
            }
        }
        result
            .onSuccess {
                text = it
                savedText = it
            }
            .onFailure { loadError = it.message ?: "The file could not be opened." }
        loading = false
    }

    // The phone back button asks before throwing away unsaved edits.
    BackHandler(enabled = dirty) { confirmClose = true }

    fun save() {
        saving = true
        status = null
        val toWrite = text
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { file.writeText(toWrite) } }
            saving = false
            result
                .onSuccess {
                    savedText = toWrite
                    status = "Saved."
                }
                .onFailure { status = it.message ?: "Could not save this file." }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackCircle(onClick = { if (dirty) confirmClose = true else onClose() })
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        loading -> "Opening…"
                        dirty -> "Unsaved changes"
                        else -> "Saved"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dirty) Warning else TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CronosCyan,
                )
                loadError != null -> Text(
                    text = loadError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = TextPrimary,
                    ),
                )
            }
        }

        if (!loading && loadError == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${text.lines().size} lines · ${formatBytes(text.toByteArray().size.toLong())}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CronosCyan,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "Revert",
                modifier = Modifier.weight(1f),
                enabled = dirty && !saving,
                onClick = {
                    text = savedText
                    status = null
                },
            )
            PrimaryButton(
                text = if (saving) "Saving…" else "Save",
                modifier = Modifier.weight(1f),
                enabled = dirty && !saving && !loading && loadError == null,
                onClick = { save() },
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmClose) {
        Dialog(onDismissRequest = { confirmClose = false }) {
            SolidDialogCard(glow = Warning) {
                Text("Discard changes?", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your edits have not been saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Keep editing",
                        modifier = Modifier.weight(1f),
                        onClick = { confirmClose = false },
                    )
                    PrimaryButton(
                        text = "Discard",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmClose = false
                            onClose()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoViewerScreen(path: String, onClose: () -> Unit) {
    val siblings = remember(path) { imageSiblings(path) }
    var index by remember(path) { mutableStateOf(siblings.indexOf(path).coerceAtLeast(0)) }
    val current = siblings.getOrElse(index) { path }
    // Zoom and pan start fresh for each picture.
    var scale by remember(current) { mutableStateOf(1f) }
    var offset by remember(current) { mutableStateOf(Offset.Zero) }

    val decoded by produceState<kotlin.Result<ImageBitmap>?>(initialValue = null, current) {
        value = withContext(Dispatchers.IO) { runCatching { decodeForScreen(current, 2048) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackCircle(onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = File(current).name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Pinch to zoom · double-tap to zoom in",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val result = decoded
            when {
                result == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CronosCyan,
                )
                result.isFailure -> Text(
                    text = result.exceptionOrNull()?.message ?: "This image can't be shown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                else -> Image(
                    bitmap = result.getOrThrow(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(current) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(1f, 5f)
                                scale = next
                                offset = if (next <= 1f) Offset.Zero else offset + pan
                            }
                        }
                        .pointerInput(current) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            })
                        },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NavButton(
                icon = Icons.Rounded.ChevronLeft,
                enabled = index > 0,
                onClick = { index-- },
            )
            Text(
                text = "${index + 1} / ${siblings.size}",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            NavButton(
                icon = Icons.Rounded.ChevronRight,
                enabled = index < siblings.lastIndex,
                onClick = { index++ },
            )
        }
    }
}

@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(GlassWhite.copy(alpha = 0.06f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The images in the same folder as [path], sorted by name, so the arrows can move between them. */
private fun imageSiblings(path: String): List<String> {
    val parent = File(path).parentFile ?: return listOf(path)
    val files = parent.listFiles()
        ?.filter { it.isFile && ViewerTypes.isImage(it.name) }
        ?.sortedBy { it.name.lowercase() }
        ?.map { it.absolutePath }
    return if (files.isNullOrEmpty()) listOf(path) else files
}

/** Decodes an image no larger than [maxSide] pixels on its longest edge, so big photos don't run out of memory. */
private fun decodeForScreen(path: String, maxSide: Int): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image can't be shown." }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(path, options)
        ?: throw IllegalStateException("This image can't be shown.")
    return bitmap.asImageBitmap()
}
