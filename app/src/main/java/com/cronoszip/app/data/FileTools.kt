package com.cronoszip.app.data

import com.cronoszip.app.util.FileOpener
import java.io.File
import java.nio.file.Files

/** Details shown in the Info dialog for a file or folder on phone storage. */
data class FileDetails(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val items: Int,
    val modified: Long,
    val hidden: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val mime: String,
)

object FileTools {
    /** Stop counting after this many files so a huge folder still finishes. */
    private const val SIZE_FILE_LIMIT = 200_000

    fun details(path: String): FileDetails? {
        val file = File(path)
        if (!file.exists()) return null
        val isDir = file.isDirectory
        return FileDetails(
            path = file.absolutePath,
            isDirectory = isDir,
            size = if (isDir) folderSize(file) else file.length(),
            items = if (isDir) (file.list()?.size ?: 0) else 0,
            modified = file.lastModified(),
            hidden = file.name.startsWith("."),
            readable = file.canRead(),
            writable = file.canWrite(),
            mime = if (isDir) "Folder" else FileOpener.mimeFor(file.name),
        )
    }

    /** Total size of everything inside [dir]. Symbolic links are not followed. */
    fun folderSize(dir: File): Long {
        var total = 0L
        var seen = 0
        dir.walkTopDown()
            .onEnter { folder -> !Files.isSymbolicLink(folder.toPath()) }
            .forEach { entry ->
                if (seen >= SIZE_FILE_LIMIT) return@forEach
                if (entry.isFile && !Files.isSymbolicLink(entry.toPath())) {
                    total += entry.length()
                    seen++
                }
            }
        return total
    }

    /** Creates an empty file named [name] inside [dir]. Fails if the name is taken. */
    fun createFile(dir: File, name: String): Result<File> = runCatching {
        val problem = nameProblem(name)
        if (problem != null) throw IllegalArgumentException(problem)
        if (!dir.isDirectory) throw IllegalStateException("This folder does not exist.")
        val target = File(dir, name)
        if (target.exists()) throw IllegalStateException("A file or folder with that name already exists.")
        if (!target.createNewFile()) throw IllegalStateException("The file could not be created here.")
        target
    }

    private fun nameProblem(name: String): String? = when {
        name.isBlank() -> "The name is empty."
        name == "." || name == ".." -> "That name is not allowed."
        name.contains('/') || name.contains('\\') -> "The name cannot contain a slash."
        name.length > 240 -> "The name is too long."
        else -> null
    }
}
