package com.cronoszip.app.data

import android.content.Context
import android.net.Uri
import com.cronoszip.app.util.isArchive
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How a folder's entries are ordered. */
enum class SortMode(val label: String) {
    Name("Name"),
    Size("Size"),
    Date("Date"),
    Type("Type"),
}

/** What the lists show and how they are ordered. Folders always come first. */
data class ListOptions(
    val showHidden: Boolean = false,
    val sort: SortMode = SortMode.Name,
    val descending: Boolean = false,
)

/**
 * One place that lists a folder, for local paths and SAF trees alike. [list] is safe to call
 * from the UI: the work runs on Dispatchers.IO. Returns null when the folder cannot be read.
 *
 * A SAF tree on device storage is listed through its real file path whenever that path can be
 * read. Items from a real path have no documentId; the screens use that to tell the two apart.
 */
object FileListing {
    suspend fun list(context: Context, location: String, options: ListOptions = ListOptions()): List<FileItem>? =
        withContext(Dispatchers.IO) {
            if (!SafStorage.isSaf(location)) {
                return@withContext listLocal(location, options)
            }
            val uri = Uri.parse(location)
            val realPath = PinnedStorage.resolveTreePath(uri) ?: PinnedStorage.resolveDocumentPath(uri)
            if (realPath != null) {
                listLocal(realPath, options)
            } else {
                SafStorage.list(context, uri)?.let { items ->
                    sorted(items.filter { options.showHidden || !it.name.startsWith(".") }, options)
                }
            }
        }

    /** Synchronous local listing. UI code should use [list] so the work stays off the main thread. */
    fun listLocal(path: String, options: ListOptions = ListOptions()): List<FileItem>? {
        val children = try {
            File(path).listFiles()
        } catch (error: SecurityException) {
            null
        } ?: return null
        val items = children.asSequence()
            .filter { options.showHidden || !it.name.startsWith(".") }
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
        return sorted(items, options)
    }

    private fun sorted(items: List<FileItem>, options: ListOptions): List<FileItem> {
        val comparator: Comparator<FileItem> = when (options.sort) {
            SortMode.Name -> compareBy<FileItem> { it.name.lowercase() }
            SortMode.Size -> compareBy<FileItem> { it.size }
            SortMode.Date -> compareBy<FileItem> { it.modified }
            SortMode.Type -> compareBy<FileItem> { it.name.substringAfterLast('.', "").lowercase() }
        }
        val ordered = if (options.descending) items.sortedWith(comparator.reversed()) else items.sortedWith(comparator)
        return ordered.sortedBy { !it.directory }
    }
}
