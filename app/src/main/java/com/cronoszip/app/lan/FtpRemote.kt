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
 * A connection to a CronosZip share on another phone. Only the IP address and port are needed.
 * Every call blocks, so run it on a background thread. Failed operations reconnect and retry.
 */
class FtpRemote(
    private val host: String,
    private val port: Int,
) : Closeable {
    private var client: FTPClient = newClient()

    private fun newClient(): FTPClient = FTPClient().apply {
        connectTimeout = 10_000
        defaultTimeout = 60_000
        controlEncoding = "UTF-8"
        setBufferSize(TRANSFER_BUFFER)
    }

    /** Connects and logs in anonymously. Retries a few times on a flaky Wi-Fi link. */
    fun connect() {
        var lastError = IOException("Could not connect.")
        for (attempt in 1..3) {
            client = newClient()
            try {
                client.connect(host, port)
                // Socket options need a live socket, so they are set only after connecting.
                client.setTcpNoDelay(true)
                client.setKeepAlive(true)
                if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                    throw IOException("The other phone did not answer. Check the address.")
                }
                if (!client.login("anonymous", "")) {
                    throw IOException("The other phone refused the connection.")
                }
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                return
            } catch (e: IOException) {
                lastError = e
                runCatching { client.disconnect() }
                Thread.sleep(800L * attempt)
            }
        }
        throw lastError
    }

    private fun reconnect() {
        runCatching { client.disconnect() }
        connect()
    }

    /** Runs [action]. If it fails with a network error, reconnects and tries again. */
    private fun <T> withRetry(action: () -> T): T {
        var lastError: IOException? = null
        for (attempt in 1..3) {
            try {
                return action()
            } catch (e: IOException) {
                lastError = e
                runCatching { reconnect() }
                Thread.sleep(800L * attempt)
            }
        }
        throw lastError ?: IOException("The transfer failed.")
    }

    fun currentDirectory(): String = client.printWorkingDirectory() ?: "/"

    fun list(dir: String): List<RemoteEntry> = withRetry {
        val files = client.listFiles(dir) ?: throw IOException("Could not read $dir")
        files
            .filter { it.name.isNotEmpty() && it.name != "." && it.name != ".." }
            .map { RemoteEntry(it.name, remoteJoin(dir, it.name), it.isDirectory, it.size) }
            .sortedWith(compareBy<RemoteEntry>({ !it.directory }, { it.name.lowercase() }))
    }

    fun download(remotePath: String, target: File, onBytes: (Long) -> Unit) = withRetry {
        val input = client.retrieveFileStream(remotePath)
            ?: throw IOException("Could not read $remotePath")
        input.use { source ->
            target.outputStream().use { copy(source, it, onBytes) }
        }
        if (!client.completePendingCommand()) {
            throw IOException("Download of $remotePath did not finish.")
        }
    }

    fun upload(source: File, remotePath: String, onBytes: (Long) -> Unit) = withRetry {
        val output = client.storeFileStream(remotePath)
            ?: throw IOException("Could not write $remotePath")
        output.use { target ->
            source.inputStream().use { copy(it, target, onBytes) }
        }
        if (!client.completePendingCommand()) {
            throw IOException("Upload of ${source.name} did not finish.")
        }
    }

    fun mkdir(path: String) = withRetry {
        if (!client.makeDirectory(path)) throw IOException("Could not create folder $path")
    }

    fun delete(path: String) = withRetry {
        if (!client.deleteFile(path)) throw IOException("Could not delete $path")
    }

    fun removeDirectory(path: String) = withRetry {
        if (!client.removeDirectory(path)) throw IOException("Could not remove folder $path")
    }

    private fun copy(input: InputStream, output: OutputStream, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(TRANSFER_BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read
            onBytes(total)
        }
        output.flush()
    }

    override fun close() {
        runCatching { client.logout() }
        runCatching { client.disconnect() }
    }
}
