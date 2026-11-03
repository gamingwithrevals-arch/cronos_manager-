package com.cronoszip.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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

/** What the lists show and how they are ordered. */
data class ListOptions(
    val showHidden: Boolean = false,
    val sort: SortMode = SortMode.Name,
    val descending: Boolean = false,
)

/**
 * One place that lists a folder, for local paths and storage-app providers alike. [list] is safe
 * to call from the UI: the work runs on Dispatchers.IO. Returns null when the folder cannot be read.
 */
object FileListing {
    /** The last provider error, kept so the screen can show what went wrong. */
    @Volatile
    var lastError: String? = null
        private set

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
                listProvider(context, uri)?.let { items ->
                    FileSort.sort(
                        items.filter { options.showHidden || !it.name.startsWith(".") },
                        options.sort,
                        options.descending,
                    )
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
        return FileSort.sort(items, options.sort, options.descending)
    }

    private fun listProvider(context: Context, treeUri: Uri): List<FileItem>? {
        lastError = null
        SafStorage.list(context, treeUri)?.let { return it }
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUri(treeUri.authority, docId)
            val items = queryChildren(context, childrenUri, treeUri)
            if (items == null) {
                lastError = "The provider returned no listing for this folder."
            }
            items
        } catch (error: Exception) {
            lastError = error.message ?: error::class.java.simpleName
            null
        }
    }

    private fun queryChildren(context: Context, childrenUri: Uri, treeUri: Uri): List<FileItem>? {
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        ) ?: return null
        return cursor.use { c ->
            val items = mutableListOf<FileItem>()
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val directory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                items.add(
                    FileItem(
                        name = name,
                        path = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                        directory = directory,
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        modified = if (c.isNull(4)) 0L else c.getLong(4),
                        archive = !directory && isArchive(name),
                        documentId = id,
                    ),
                )
            }
            items
        }
    }
}
