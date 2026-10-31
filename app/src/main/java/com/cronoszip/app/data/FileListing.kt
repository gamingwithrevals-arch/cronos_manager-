package com.cronoszip.app.data

import android.content.Context
import android.net.Uri
import com.cronoszip.app.util.isArchive
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One place that lists a folder, for local paths and SAF trees alike. [list] is safe to call
 * from the UI: the work runs on Dispatchers.IO. Returns null when the folder cannot be read,
 * the same contract the screens already expect.
 *
 * Note: SAF listings skip dot-files inside [SafStorage.list] itself, so [showHidden] only
 * affects local folders until that filter is lifted in a later step.
 */
object FileListing {
    suspend fun list(context: Context, location: String, showHidden: Boolean = false): List<FileItem>? =
        withContext(Dispatchers.IO) {
            if (SafStorage.isSaf(location)) {
                SafStorage.list(context, Uri.parse(location))
                    ?.filter { showHidden || !it.name.startsWith(".") }
            } else {
                listLocal(location, showHidden)
            }
        }

    /** Synchronous local listing. UI code should use [list] so the work stays off the main thread. */
    fun listLocal(path: String, showHidden: Boolean = false): List<FileItem>? {
        val children = try {
            File(path).listFiles()
        } catch (error: SecurityException) {
            null
        } ?: return null
        return children.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .map { child ->
                FileItem(
                    name = child.name,
                    path = child.absolutePath,
                    directory = child.isDirectory,
                    size = if (child.isDirectory) 0L else child.length(),
                    modified = child.lastModified(),
                    archive = child.isFile && isArchive(child.name),
                )
            }
            .toList()
    }
}
