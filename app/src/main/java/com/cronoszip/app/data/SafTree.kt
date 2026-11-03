package com.cronoszip.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.cronoszip.app.util.FileOpener
import com.cronoszip.app.util.isArchive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/**
 * File operations on a storage that another app exposes through its DocumentsProvider. Everything
 * goes through ContentResolver and DocumentsContract, using only the access the user granted in
 * the system folder picker. No root, no shell, no private paths.
 *
 * Every public function returns a Result, so a provider that refuses an operation shows a message
 * instead of crashing the app.
 */
object SafTree {
    private const val BUFFER_SIZE = 256 * 1024
    private const val READ_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private const val WRITE_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Keeps the grant across reboots. Asks for write access first and falls back to read-only. */
    fun persist(context: Context, treeUri: Uri): Boolean {
        val resolver = context.contentResolver
        return runCatching { resolver.takePersistableUriPermission(treeUri, WRITE_FLAGS) }
            .recoverCatching { resolver.takePersistableUriPermission(treeUri, READ_FLAGS) }
            .isSuccess
    }

    /** True while the grant for [treeUri] is still held. */
    fun isGranted(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    /** True when the grant also allows writing, so create, rename, delete and paste can work. */
    fun canWrite(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }

    /** The document URI of the root folder of a mounted tree. */
    fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    /** Lists the children of [parentDocId] inside [treeUri], folders first. */
    fun list(context: Context, treeUri: Uri, parentDocId: String): Result<List<FileItem>> = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = context.contentResolver.query(childrenUri, columns, null, null, null)
            ?: throw IllegalStateException("The storage did not answer the listing request.")
        val items = cursor.use { c ->
            val out = ArrayList<FileItem>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val directory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                out.add(
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
            out
        }
        items.filter { it.directory }.sortedBy { it.name.lowercase() } +
            items.filter { !it.directory }.sortedBy { it.name.lowercase() }
    }

    /** Opens a document for reading. */
    fun openInput(context: Context, docUri: Uri): Result<InputStream> = runCatching {
        context.contentResolver.openInputStream(docUri)
            ?: throw IllegalStateException("Could not open the file for reading.")
    }

    /** Opens a document for writing. The "wt" mode replaces whatever was there before. */
    fun openOutput(context: Context, docUri: Uri): Result<OutputStream> = runCatching {
        context.contentResolver.openOutputStream(docUri, "wt")
            ?: throw IllegalStateException("Could not open the file for writing.")
    }

    /** Creates a folder called [name] inside [parentDocUri]. */
    fun createFolder(context: Context, parentDocUri: Uri, name: String): Result<Uri> = runCatching {
        requireName(name)
        DocumentsContract.createDocument(
            context.contentResolver,
            parentDocUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw IllegalStateException("The storage refused to create the folder.")
    }

    /** Creates an empty file called [name] inside [parentDocUri]. */
    fun createFile(context: Context, parentDocUri: Uri, name: String): Result<Uri> = runCatching {
        requireName(name)
        val mime = FileOpener.mimeFor(name).let { if (it == "*/*") "application/octet-stream" else it }
        DocumentsContract.createDocument(context.contentResolver, parentDocUri, mime, name)
            ?: throw IllegalStateException("The storage refused to create the file.")
    }

    /** Renames a document. Returns its new URI, which some providers change on rename. */
    fun rename(context: Context, docUri: Uri, newName: String): Result<Uri> = runCatching {
        requireName(newName)
        DocumentsContract.renameDocument(context.contentResolver, docUri, newName)
            ?: throw IllegalStateException("The storage refused to rename the item.")
    }

    /** Deletes a document. A folder is deleted together with everything inside it. */
    fun delete(context: Context, docUri: Uri): Result<Unit> = runCatching {
        if (!DocumentsContract.deleteDocument(context.contentResolver, docUri)) {
            throw IllegalStateException("The storage refused to delete the item.")
        }
    }

    /**
     * Copies a phone file or folder into [parentDocUri]. [onBytes] reports each chunk written, so
     * the caller can show progress. Symbolic links are skipped.
     */
    fun copyToTree(
        context: Context,
        source: File,
        parentDocUri: Uri,
        onBytes: (Long) -> Unit = {},
    ): Result<Uri> = runCatching {
        if (Files.isSymbolicLink(source.toPath())) {
            parentDocUri
        } else if (source.isDirectory) {
            val folder = createFolder(context, parentDocUri, source.name).getOrThrow()
            source.listFiles().orEmpty().forEach { child ->
                copyToTree(context, child, folder, onBytes).getOrThrow()
            }
            folder
        } else {
            val target = createFile(context, parentDocUri, source.name).getOrThrow()
            val buffer = ByteArray(BUFFER_SIZE)
            FileInputStream(source).use { input ->
                openOutput(context, target).getOrThrow().use { output ->
                    pump(input, output, buffer, onBytes)
                }
            }
            target
        }
    }

    /**
     * Copies one item from a mounted tree into [destDir] on the phone. Folders are copied with
     * everything inside them. [onBytes] reports each chunk written.
     */
    fun copyFromTree(
        context: Context,
        treeUri: Uri,
        docId: String,
        name: String,
        isDirectory: Boolean,
        destDir: File,
        onBytes: (Long) -> Unit = {},
    ): Result<File> = runCatching {
        requireName(name)
        val target = File(destDir, name)
        if (isDirectory) {
            if (!target.isDirectory && !target.mkdirs()) {
                throw IllegalStateException("Could not create ${target.name} on the phone.")
            }
            val children = list(context, treeUri, docId).getOrThrow()
            for (child in children) {
                val childId = child.documentId ?: continue
                copyFromTree(context, treeUri, childId, child.name, child.directory, target, onBytes).getOrThrow()
            }
        } else {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val buffer = ByteArray(BUFFER_SIZE)
            openInput(context, docUri).getOrThrow().use { input ->
                FileOutputStream(target).use { output -> pump(input, output, buffer, onBytes) }
            }
        }
        target
    }

    private fun pump(input: InputStream, output: OutputStream, buffer: ByteArray, onBytes: (Long) -> Unit) {
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            onBytes(n.toLong())
        }
    }

    private fun requireName(name: String) {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") {
            "That name is not allowed."
        }
    }
}
