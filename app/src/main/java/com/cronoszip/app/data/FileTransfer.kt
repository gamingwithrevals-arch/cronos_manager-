package com.cronoszip.app.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files

/** What a transfer is doing right now, shown in the progress box. */
data class TransferProgress(
    val title: String,
    val current: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val doneItems: Int,
    val totalItems: Int,
)

enum class TransferKind { Copy, Move, Delete }

/** What to do when the destination already has an item with the same name. */
enum class ConflictPolicy { Merge, Replace, Skip, KeepBoth }

/**
 * Copy, move and delete for real file paths, with byte progress, cancel and conflict handling.
 * Moves try a fast rename first, so moving inside one storage volume is instant.
 */
object FileTransfer {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    private class Tally {
        var bytes = 0L
        var items = 0
    }

    /** Names of the items in [paths] that already exist inside [destDir]. */
    fun conflicts(paths: List<String>, destDir: File): List<String> =
        paths.map { File(it).name }.filter { File(destDir, it).exists() }

    /** Same as the full version, keeping both copies when names clash. */
    fun run(
        kind: TransferKind,
        paths: List<String>,
        destDir: File?,
        onProgress: (TransferProgress) -> Unit,
    ): Result<Int> = run(kind, paths, destDir, ConflictPolicy.KeepBoth, onProgress)

    fun run(
        kind: TransferKind,
        paths: List<String>,
        destDir: File?,
        policy: ConflictPolicy,
        onProgress: (TransferProgress) -> Unit,
    ): Result<Int> = runCatching {
        cancelled = false
        val sources = paths.map { File(it) }.filter { it.exists() || Files.isSymbolicLink(it.toPath()) }
        val dest: File? = when (kind) {
            TransferKind.Delete -> null
            else -> destDir ?: throw IllegalArgumentException("Choose a destination folder.")
        }
        if (dest != null) {
            if (!dest.isDirectory) throw IllegalStateException("The destination folder does not exist.")
            val d = dest.absoluteFile.toPath().normalize()
            sources.forEach { src ->
                val s = src.absoluteFile.toPath().normalize()
                if (d == s || d.startsWith(s)) {
                    throw IllegalArgumentException("A folder cannot be copied or moved into itself.")
                }
            }
        }

        val total = sources.sumOf { sizeOf(it) }
        val tally = Tally()
        val title = when (kind) {
            TransferKind.Copy -> "Copying"
            TransferKind.Move -> "Moving"
            TransferKind.Delete -> "Deleting"
        }
        var lastReport = 0L
        fun report(current: String, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastReport > 120) {
                lastReport = now
                onProgress(TransferProgress(title, current, tally.bytes, total, tally.items, sources.size))
            }
        }

        sources.forEach { src ->
            checkCancel()
            when (kind) {
                TransferKind.Copy -> place(src, dest!!, policy, move = false, tally = tally) { report(src.name) }
                TransferKind.Move -> place(src, dest!!, policy, move = true, tally = tally) { report(src.name) }
                TransferKind.Delete -> deleteTree(src, countBytes = true, tally = tally) { report(src.name) }
            }
            tally.items++
            report(src.name, force = true)
        }
        tally.items
    }

    /** Puts [src] inside [dest] according to the conflict [policy]. */
    private fun place(
        src: File,
        dest: File,
        policy: ConflictPolicy,
        move: Boolean,
        tally: Tally,
        step: () -> Unit,
    ) {
        val existing = File(dest, src.name)
        val target: File
        if (!existing.exists()) {
            target = existing
        } else {
            when (policy) {
                ConflictPolicy.Skip -> return
                ConflictPolicy.Replace -> {
                    deleteTree(existing, countBytes = false, tally = tally, step = step)
                    target = existing
                }
                ConflictPolicy.Merge -> target = existing
                ConflictPolicy.KeepBoth -> target = uniqueTarget(dest, src.name)
            }
        }
        if (move && !target.exists() && src.renameTo(target)) {
            tally.bytes += sizeOf(target)
            return
        }
        copyTree(src, target, tally, step)
        if (move) deleteTree(src, countBytes = false, tally = tally, step = step)
    }

    private fun checkCancel() {
        if (cancelled) throw InterruptedException("Cancelled")
    }

    private fun copyTree(src: File, dst: File, tally: Tally, step: () -> Unit) {
        checkCancel()
        if (Files.isSymbolicLink(src.toPath())) return
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyTree(it, File(dst, it.name), tally, step) }
        } else if (src.isFile) {
            val buffer = ByteArray(256 * 1024)
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    while (true) {
                        checkCancel()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        tally.bytes += n
                        step()
                    }
                }
            }
        }
    }

    private fun deleteTree(f: File, countBytes: Boolean, tally: Tally, step: () -> Unit) {
        checkCancel()
        if (!Files.isSymbolicLink(f.toPath()) && f.isDirectory) {
            f.listFiles()?.forEach { deleteTree(it, countBytes, tally, step) }
        } else if (f.isFile && countBytes) {
            tally.bytes += f.length()
        }
        if (!f.delete() && f.exists()) throw IllegalStateException("Could not delete ${f.name}.")
        step()
    }

    /** Total bytes under [f], without following symbolic links. */
    fun sizeOf(f: File): Long {
        if (Files.isSymbolicLink(f.toPath())) return 0L
        if (f.isFile) return f.length()
        return f.walkTopDown()
            .onEnter { dir -> !Files.isSymbolicLink(dir.toPath()) }
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
            .sumOf { it.length() }
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (target.exists()) {
            target = File(dir, "$base ($index)$ext")
            index++
        }
        return target
    }
}
