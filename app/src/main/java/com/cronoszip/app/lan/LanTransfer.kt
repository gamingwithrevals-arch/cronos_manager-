package com.cronoszip.app.lan

import android.os.Environment
import com.cronoszip.app.util.formatBytes
import java.io.File
import java.io.IOException

/**
 * Copies and moves between this phone and the other phone. A move only removes the source after
 * the copy has finished, so a failed transfer never loses a file.
 */
object LanTransfer {
    /** Where files received from the other phone are saved. */
    fun inbox(): File =
        File(Environment.getExternalStorageDirectory(), "Download/CronosZip").apply { mkdirs() }

    /** Copies (or moves) chosen items from the other phone into the inbox. Returns how many finished. */
    fun receive(
        remote: FtpRemote,
        entries: List<RemoteEntry>,
        move: Boolean,
        onStatus: (String) -> Unit,
    ): Int {
        val destination = inbox()
        var done = 0
        for (entry in entries) {
            onStatus("Receiving ${entry.name}…")
            downloadTree(remote, entry, destination) { bytes ->
                onStatus("${entry.name}: ${formatBytes(bytes)}")
            }
            if (move) deleteRemote(remote, entry)
            done++
        }
        return done
    }

    /** Sends a local file or folder into [remoteDir] on the other phone. */
    fun send(
        remote: FtpRemote,
        local: File,
        remoteDir: String,
        move: Boolean,
        onStatus: (String) -> Unit,
    ) {
        onStatus("Sending ${local.name}…")
        uploadTree(remote, local, remoteDir) { bytes ->
            onStatus("${local.name}: ${formatBytes(bytes)}")
        }
        if (move && !local.deleteRecursively()) {
            throw IOException("Sent, but the original could not be removed.")
        }
    }

    private fun downloadTree(
        remote: FtpRemote,
        entry: RemoteEntry,
        destinationDir: File,
        onBytes: (Long) -> Unit,
    ): File {
        val target = uniqueLocal(destinationDir, entry.name)
        if (entry.directory) {
            target.mkdirs()
            for (child in remote.list(entry.path)) {
                downloadTree(remote, child, target, onBytes)
            }
        } else {
            remote.download(entry.path, target, onBytes)
        }
        return target
    }

    private fun uploadTree(
        remote: FtpRemote,
        local: File,
        remoteDir: String,
        onBytes: (Long) -> Unit,
    ): String {
        val target = remoteJoin(remoteDir, uniqueRemoteName(remote, remoteDir, local.name))
        if (local.isDirectory) {
            remote.mkdir(target)
            local.listFiles()?.sortedBy { it.name }?.forEach { child ->
                uploadTree(remote, child, target, onBytes)
            }
        } else {
            remote.upload(local, target, onBytes)
        }
        return target
    }

    private fun deleteRemote(remote: FtpRemote, entry: RemoteEntry) {
        if (entry.directory) {
            for (child in remote.list(entry.path)) deleteRemote(remote, child)
            remote.removeDirectory(entry.path)
        } else {
            remote.delete(entry.path)
        }
    }

    /** A name that does not clash with an existing file, such as "photo (2).jpg". */
    private fun uniqueLocal(dir: File, name: String): File {
        var candidate = File(dir, name)
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$name ($n)")
            n++
        }
        return candidate
    }

    private fun uniqueRemoteName(remote: FtpRemote, dir: String, name: String): String {
        val taken = remote.list(dir).map { it.name }.toSet()
        if (name !in taken) return name
        var n = 2
        while ("$name ($n)" in taken) n++
        return "$name ($n)"
    }
}
