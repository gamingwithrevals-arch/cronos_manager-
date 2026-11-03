package com.cronoszip.app.data

import com.cronoszip.app.util.isArchive
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Searches storage breadth-first: the shallow folders people use most (DCIM, Pictures, Download)
 * are checked before deep folder trees. Matches stream out while the walk runs.
 */
object FileScan {
    private const val MAX_RESULTS = 5000
    private const val EMIT_EVERY_MS = 300L

    /** Android's private app folders. Android blocks reading them anyway, so the walk skips them. */
    private val skippedInsideAndroid = setOf("data", "obb")

    fun find(root: File, match: (File) -> Boolean): Flow<List<FileItem>> = flow {
        val found = ArrayList<FileItem>()
        var lastEmit = System.currentTimeMillis()
        val queue = ArrayDeque<File>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory && !(dir.name == "Android" && child.name in skippedInsideAndroid)) {
                    queue.addLast(child)
                }
                if (match(child)) {
                    found.add(toItem(child))
                    if (found.size >= MAX_RESULTS) break
                }
            }
            val now = System.currentTimeMillis()
            if (now - lastEmit >= EMIT_EVERY_MS) {
                emit(found.toList())
                lastEmit = now
            }
            if (found.size >= MAX_RESULTS) break
        }
        emit(found.toList())
    }.flowOn(Dispatchers.IO)

    private fun toItem(f: File): FileItem = FileItem(
        name = f.name,
        path = f.absolutePath,
        directory = f.isDirectory,
        size = if (f.isDirectory) 0L else f.length(),
        modified = f.lastModified(),
        archive = f.isFile && isArchive(f.name),
    )
}
