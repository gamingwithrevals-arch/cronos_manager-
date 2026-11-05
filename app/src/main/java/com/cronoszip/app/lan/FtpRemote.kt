package com.cronoszip.app.lan

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

/** One entry in a folder on the other phone. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
)

fun remoteJoin(dir: String, name: String): String =
    if (dir.endsWith("/")) dir + name else "$dir/$name"

fun remoteParent(path: String): String {
    if (path.isEmpty() || path == "/") return "/"
    val parent = path.trimEnd('/').substringBeforeLast('/', "")
    return if (parent.isEmpty()) "/" else parent
}

/**
 * A connection to a CronosZip share on another phone. Every call blocks, so run it on a
 * background thread.
 */
class FtpRemote(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
) : Closeable {
    private val client = FTPClient()

    fun connect() {
        client.connectTimeout = 10_000
        client.defaultTimeout = 30_000
        client.controlEncoding = "UTF-8"
        client.connect(host, port)
        if (!FTPReply.isPositiveCompletion(client.replyCode)) {
            client.disconnect()
            throw IOException("The other phone did not answer. Check the address and port.")
        }
        if (!client.login(username, password)) {
            client.disconnect()
            throw IOException("Wrong username or password.")
        }
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
    }

    fun currentDirectory(): String = client.printWorkingDirectory() ?: "/"

    fun list(dir: String): List<RemoteEntry> {
        val files = client.listFiles(dir) ?: throw IOException("Could not read $dir")
        return files
            .filter { it.name.isNotEmpty() && it.name != "." && it.name != ".." }
            .map { RemoteEntry(it.name, remoteJoin(dir, it.name), it.isDirectory, it.size) }
            .sortedWith(compareBy<RemoteEntry>({ !it.directory }, { it.name.lowercase() }))
    }

    fun download(remotePath: String, target: File, onBytes: (Long) -> Unit) {
        val input = client.retrieveFileStream(remotePath)
            ?: throw IOException("Could not read $remotePath")
        input.use { source ->
            target.outputStream().use { copy(source, it, onBytes) }
        }
        if (!client.completePendingCommand()) {
            throw IOException("Download of $remotePath did not finish.")
        }
    }

    fun upload(source: File, remotePath: String, onBytes: (Long) -> Unit) {
        val output = client.storeFileStream(remotePath)
            ?: throw IOException("Could not write $remotePath")
        output.use { target ->
            source.inputStream().use { copy(it, target, onBytes) }
        }
        if (!client.completePendingCommand()) {
            throw IOException("Upload of ${source.name} did not finish.")
        }
    }

    fun mkdir(path: String) {
        if (!client.makeDirectory(path)) throw IOException("Could not create folder $path")
    }

    fun delete(path: String) {
        if (!client.deleteFile(path)) throw IOException("Could not delete $path")
    }

    fun removeDirectory(path: String) {
        if (!client.removeDirectory(path)) throw IOException("Could not remove folder $path")
    }

    private fun copy(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            onBytes(total)
        }
    }

    override fun close() {
        runCatching { client.logout() }
        runCatching { client.disconnect() }
    }
}
