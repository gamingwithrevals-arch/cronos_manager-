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
 * Walks internal storage and streams the files that match. Results arrive while the walk runs,
 * so the screen shows matches as they are found instead of waiting for the whole walk.
 */
object FileScan {
    private const val MAX_RESULTS = 2000
    private const val TIME_BUDGET_MS = 20_000L
    private const val EMIT_EVERY_MS = 250L

    fun find(root: File, match: (File) -> Boolean): Flow<List<FileItem>> = flow {
        val found = ArrayList<FileItem>()
        val started = System.currentTimeMillis()
        var lastEmit = started
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (found.size >= MAX_RESULTS) break
            if (System.currentTimeMillis() - started > TIME_BUDGET_MS) break
            val dir = pending.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory) {
                    if (dir == root && child.name == "Android") continue
                    pending.add(child)
                }
                if (match(child)) found.add(toItem(child))
                if (found.size >= MAX_RESULTS) break
            }
            val now = System.currentTimeMillis()
            if (now - lastEmit >= EMIT_EVERY_MS) {
                emit(found.toList())
                lastEmit = now
            }
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
