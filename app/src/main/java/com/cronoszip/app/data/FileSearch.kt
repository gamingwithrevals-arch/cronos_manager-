package com.cronoszip.app.data

import com.cronoszip.app.util.isArchive
import java.io.File
import java.nio.file.Files

/** Name search through a folder and all of its subfolders. */
object FileSearch {
    /** Upper limit on how many files are looked at, so a search on a huge storage still finishes. */
    private const val VISIT_LIMIT = 200_000

    /** Finds files and folders under [root] whose name contains [query]. Stops at [limit] results. */
    fun find(root: String, query: String, limit: Int = 500): List<FileItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return File(root).walkTopDown()
            .onEnter { dir -> !Files.isSymbolicLink(dir.toPath()) && !dir.name.startsWith(".") }
            .take(VISIT_LIMIT)
            .filter { it.absolutePath != File(root).absolutePath && it.name.contains(q, ignoreCase = true) }
            .take(limit)
            .map { f ->
                FileItem(
                    name = f.name,
                    path = f.absolutePath,
                    directory = f.isDirectory,
                    size = if (f.isDirectory) 0L else f.length(),
                    modified = f.lastModified(),
                    archive = f.isFile && isArchive(f.name),
                )
            }
            .toList()
    }
}
