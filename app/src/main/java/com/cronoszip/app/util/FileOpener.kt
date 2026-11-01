package com.cronoszip.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cronoszip.app.ui.viewer.ViewerActivity
import com.cronoszip.app.ui.viewer.ViewerTypes
import java.io.File
import java.util.Locale

/**
 * Opens a local file. Text and photos open in Chronos Manager's own viewer. An .apk opens
 * Android's installer. Everything else opens in whichever app handles its type.
 */
object FileOpener {
    fun open(context: Context, path: String): Result<Unit> = runCatching {
        val file = File(path)
        if (!file.isFile) throw IllegalArgumentException("This item is not a file.")

        if (ViewerTypes.isText(file.name) || ViewerTypes.isImage(file.name)) {
            context.startActivity(
                Intent(context, ViewerActivity::class.java).apply {
                    putExtra(ViewerActivity.EXTRA_PATH, file.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return@runCatching
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("This file is on storage Chronos Manager cannot share yet.")
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw IllegalStateException("No app on this phone can open ${extension(file.name)} files.")
        }
    }

    fun mimeFor(name: String): String = mimeByExtension[extension(name)] ?: "*/*"

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private val mimeByExtension = mapOf(
        "apk" to "application/vnd.android.package-archive",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "log" to "text/plain",
        "md" to "text/plain",
        "kt" to "text/plain",
        "java" to "text/plain",
        "gradle" to "text/plain",
        "properties" to "text/plain",
        "sh" to "text/plain",
        "yml" to "text/plain",
        "yaml" to "text/plain",
        "json" to "application/json",
        "xml" to "text/xml",
        "csv" to "text/csv",
        "html" to "text/html",
        "htm" to "text/html",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "ogg" to "audio/ogg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
    )
}
