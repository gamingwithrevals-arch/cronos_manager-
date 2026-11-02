package com.cronoszip.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.util.isArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ThumbKind { Apk, Image, Video, Other }

private val thumbCache = LruCache<String, ImageBitmap>(120)

private val audioExtensions = setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus")

private fun thumbKindOf(item: FileItem): ThumbKind {
    if (item.directory) return ThumbKind.Other
    return when (item.name.substringAfterLast('.', "").lowercase()) {
        "apk" -> ThumbKind.Apk
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> ThumbKind.Image
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v" -> ThumbKind.Video
        else -> ThumbKind.Other
    }
}

private fun iconFor(item: FileItem, kind: ThumbKind): ImageVector = when {
    item.directory -> Icons.Rounded.Folder
    kind == ThumbKind.Image -> Icons.Rounded.Image
    kind == ThumbKind.Video -> Icons.Rounded.Movie
    isArchive(item.name) -> Icons.Rounded.Archive
    item.name.substringAfterLast('.', "").lowercase() in audioExtensions -> Icons.Rounded.Audiotrack
    else -> Icons.Rounded.Description
}

/** Shows a real preview for APKs (their app icon), images and videos, and a type icon for everything else. */
@Composable
fun FileThumb(item: FileItem, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val kind = thumbKindOf(item)
    val thumb by produceState<ImageBitmap?>(initialValue = null, item.path, item.modified, px) {
        value = if (kind == ThumbKind.Other) {
            null
        } else {
            withContext(Dispatchers.IO) { loadThumb(context, item, kind, px) }
        }
    }
    val tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(if (item.directory) CronosCyan.copy(alpha = 0.14f) else CronosBlue.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = iconFor(item, kind),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

private fun loadThumb(context: Context, item: FileItem, kind: ThumbKind, px: Int): ImageBitmap? {
    val key = "${item.path}|${item.modified}|$px"
    thumbCache.get(key)?.let { return it }
    val bitmap: Bitmap? = when (kind) {
        ThumbKind.Image -> decodeImage(item.path, px)
        ThumbKind.Video -> videoFrame(item.path)
        ThumbKind.Apk -> apkIcon(context, item.path, px)
        ThumbKind.Other -> null
    }
    val image = bitmap?.asImageBitmap() ?: return null
    thumbCache.put(key, image)
    return image
}

private fun decodeImage(path: String, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > px * 2 && bounds.outHeight / sample > px * 2) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun videoFrame(path: String): Bitmap? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.getFrameAtTime(0)
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun apkIcon(context: Context, path: String, px: Int): Bitmap? {
    return runCatching {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = path
        app.publicSourceDir = path
        val drawable = app.loadIcon(pm)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}
