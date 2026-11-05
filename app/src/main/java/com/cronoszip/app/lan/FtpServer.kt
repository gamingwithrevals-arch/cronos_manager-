package com.cronoszip.app.lan

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Buffer size for every file read and write, both sending and receiving. */
const val TRANSFER_BUFFER = 256 * 1024

/**
 * A small FTP server that shares one folder tree on the local network.
 * Login is open: any username and password are accepted, so another phone only needs the
 * IP address and port. Passive mode only.
 */
class FtpServer(
    private val root: File,
    private val port: Int,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var listener: ServerSocket? = null
    private var workers: ExecutorService? = null

    fun start() {
        check(running.compareAndSet(false, true)) { "Server is already running" }
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(port))
        val pool = Executors.newCachedThreadPool()
        listener = server
        workers = pool
        Thread({ acceptLoop(server, pool) }, "ftp-accept").apply {
            isDaemon = true
            start()
        }
    }

    private fun acceptLoop(server: ServerSocket, pool: ExecutorService) {
        while (running.get()) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            pool.execute { FtpSession(client, root).run() }
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { listener?.close() }
        workers?.shutdownNow()
        listener = null
        workers = null
    }
}

private class FtpSession(
    private val control: Socket,
    private val root: File,
) {
    private val reader = BufferedReader(InputStreamReader(control.getInputStream(), Charsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(control.getOutputStream(), Charsets.UTF_8))
    private var loggedIn = false
    private var cwd = "/"
    private var passive: ServerSocket? = null
    private var renameFrom: File? = null

    fun run() {
        try {
            control.tcpNoDelay = true
            control.keepAlive = true
            control.soTimeout = 600_000
            reply(220, "CronosZip LAN share ready")
            while (true) {
                val line = reader.readLine() ?: break
                val space = line.indexOf(' ')
                val command = (if (space < 0) line else line.substring(0, space)).uppercase(Locale.ROOT)
                val argument = if (space < 0) "" else line.substring(space + 1).trim()
                if (!handle(command, argument)) break
            }
        } catch (e: Exception) {
            // The client dropped or went quiet. Nothing else to do for this session.
        } finally {
            closePassive()
            runCatching { control.close() }
        }
    }

    /** Returns false when the session should end. */
    private fun handle(command: String, argument: String): Boolean {
        when (command) {
            "USER" -> reply(331, "Any password works")
            "PASS" -> {
                loggedIn = true
                reply(230, "Logged in")
            }
            "QUIT" -> {
                reply(221, "Goodbye")
                return false
            }
            "SYST" -> reply(215, "UNIX Type: L8")
            "FEAT" -> send("211-Features\r\n UTF8\r\n211 End\r\n")
            "NOOP", "TYPE", "OPTS" -> reply(200, "OK")
            else -> if (!loggedIn) reply(530, "Please log in first") else authorised(command, argument)
        }
        return true
    }

    private fun authorised(command: String, argument: String) {
        when (command) {
            "PWD", "XPWD" -> reply(257, "\"$cwd\" is the current folder")
            "CWD", "CDUP" -> {
                val virtual = normalize(if (command == "CDUP") ".." else argument)
                val dir = virtual?.let { target(it) }
                if (virtual != null && dir != null && dir.isDirectory) {
                    cwd = virtual
                    reply(250, "Folder changed")
                } else {
                    reply(550, "No such folder")
                }
            }
            "PASV" -> openPassive()
            "LIST", "NLST" -> listFolder(namesOnly = command == "NLST", argument = argument)
            "RETR" -> retrieve(argument)
            "STOR" -> store(argument)
            "DELE" -> {
                val file = target(argument)
                if (file != null && file.isFile && file.delete()) reply(250, "Deleted")
                else reply(550, "Could not delete that file")
            }
            "MKD", "XMKD" -> {
                val dir = target(argument)
                if (dir != null && !dir.exists() && dir.mkdir()) reply(257, "\"$argument\" created")
                else reply(550, "Could not create that folder")
            }
            "RMD", "XRMD" -> {
                val dir = target(argument)
                val empty = dir != null && dir.isDirectory && dir.list()?.isEmpty() == true
                if (dir != null && empty && dir.delete()) reply(250, "Folder removed")
                else reply(550, "Could not remove the folder (it must be empty)")
            }
            "RNFR" -> {
                val file = target(argument)
                if (file != null && file.exists()) {
                    renameFrom = file
                    reply(350, "Ready for the new name")
                } else {
                    reply(550, "No such file or folder")
                }
            }
            "RNTO" -> {
                val from = renameFrom
                val to = target(argument)
                renameFrom = null
                if (from != null && to != null && !to.exists() && from.renameTo(to)) reply(250, "Renamed")
                else reply(553, "Could not rename")
            }
            "SIZE" -> {
                val file = target(argument)
                if (file != null && file.isFile) reply(213, file.length().toString())
                else reply(550, "No such file")
            }
            else -> reply(502, "Command not supported")
        }
    }

    private fun listFolder(namesOnly: Boolean, argument: String) {
        val path = if (argument.isBlank() || argument.startsWith("-")) cwd else argument
        val folder = target(path)
        if (folder == null || !folder.isDirectory) {
            reply(550, "No such folder")
            return
        }
        // Every file and every hidden file is listed. A null result means Android denied access.
        val children = folder.listFiles()
        if (children == null) {
            reply(550, "Cannot read this folder. Turn on All files access on the phone that is sharing.")
            return
        }
        val sorted = children.sortedBy { it.name.lowercase(Locale.ROOT) }
        val text = buildString {
            for (child in sorted) {
                append(if (namesOnly) child.name else unixLine(child)).append("\r\n")
            }
        }
        data("Opening data connection for the listing", "Listing sent") { socket ->
            val output = socket.getOutputStream()
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    private fun retrieve(argument: String) {
        val file = target(argument)
        if (file == null || !file.isFile) {
            reply(550, "No such file")
            return
        }
        data("Opening data connection for ${file.name}", "Download complete") { socket ->
            file.inputStream().use { input -> pump(input, socket.getOutputStream()) }
        }
    }

    private fun store(argument: String) {
        val file = target(argument)
        if (file == null || file.isDirectory || file.parentFile?.isDirectory != true) {
            reply(553, "Cannot write there")
            return
        }
        data("Ready to receive ${file.name}", "Upload complete") { socket ->
            try {
                FileOutputStream(file).use { output -> pump(socket.getInputStream(), output) }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
    }

    private fun openPassive() {
        closePassive()
        val server = ServerSocket(0)
        server.soTimeout = 15_000
        passive = server
        val address = (control.localAddress as? Inet4Address)?.address ?: ByteArray(4)
        val ip = address.joinToString(",") { (it.toInt() and 0xff).toString() }
        val port = server.localPort
        reply(227, "Entering Passive Mode ($ip,${port shr 8},${port and 0xff})")
    }

    private fun closePassive() {
        runCatching { passive?.close() }
        passive = null
    }

    /** Accepts the pending passive connection, runs [transfer] on it, then replies to the client. */
    private fun data(opening: String, done: String, transfer: (Socket) -> Unit) {
        val server = passive
        passive = null
        if (server == null) {
            reply(425, "Use PASV first")
            return
        }
        reply(150, opening)
        try {
            server.use { listener ->
                listener.accept().use { socket ->
                    socket.soTimeout = 120_000
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.sendBufferSize = TRANSFER_BUFFER
                    socket.receiveBufferSize = TRANSFER_BUFFER
                    transfer(socket)
                }
            }
            reply(226, done)
        } catch (e: Exception) {
            reply(426, "Transfer stopped: ${e.message ?: "connection closed"}")
        }
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(TRANSFER_BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    /** Turns a client path into a clean path such as "/Music/rock", or null if it leaves the share. */
    private fun normalize(path: String): String? {
        val full = when {
            path.startsWith("/") -> path
            cwd == "/" -> "/$path"
            else -> "$cwd/$path"
        }
        val parts = ArrayList<String>()
        for (segment in full.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return "/" + parts.joinToString("/")
    }

    /** The real file for a client path, or null if it resolves outside the share. */
    private fun target(path: String): File? {
        val virtual = normalize(path) ?: return null
        val file = File(root, virtual.trimStart('/'))
        return try {
            val base = root.canonicalPath
            val real = file.canonicalPath
            if (real == base || real.startsWith("$base/")) file else null
        } catch (e: Exception) {
            null
        }
    }

    private fun unixLine(file: File): String {
        val type = if (file.isDirectory) "d" else "-"
        val perms = if (file.isDirectory) "rwxr-xr-x" else "rw-r--r--"
        val size = if (file.isDirectory) 4096L else file.length()
        val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(file.lastModified()))
        return "$type$perms 1 owner group ${size.toString().padStart(12)} $date ${file.name}"
    }

    private fun reply(code: Int, message: String) = send("$code $message\r\n")

    private fun send(text: String) {
        writer.write(text)
        writer.flush()
    }
}
