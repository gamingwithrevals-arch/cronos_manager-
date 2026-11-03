package com.cronoszip.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.cronoszip.app.util.isArchive
import java.io.File
import java.io.FileOutputStream

/**
 * Browsing and operations for locations that only exist behind Android's document picker (SAF).
 * Mirrors BFE's document-tree backend: list children with DocumentsContract, navigate by document
 * id, and create/rename/delete/copy through the provider instead of pretending it is a File path.
 */
object SafStorage {
    fun isSaf(location: String): Boolean = location.startsWith("content://")

    fun list(context: Context, treeUri: Uri): List<FileItem>? {
        val resolver = context.contentResolver
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        return try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                val result = ArrayList<FileItem>(cursor.count.coerceAtLeast(0))
                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else null
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (id.isNullOrBlank() || name.isNullOrBlank()) continue

                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L

                    result.add(
                        FileItem(
                            name = name,
                            path = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                            directory = directory,
                            size = size,
                            modified = modified,
                            archive = !directory && isArchive(name),
                            documentId = id,
                        ),
                    )
                }
                result.sortedWith(compareBy<FileItem>({ !it.directory }, { it.name.lowercase() }))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun childTreeUri(parentTree: Uri, childDocumentId: String): Uri? {
        val authority = parentTree.authority ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, childDocumentId)
    }

    fun parentTreeUri(treeUri: Uri): Uri? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val parentId = when {
            documentId.contains('/') -> documentId.substringBeforeLast('/')
            documentId.contains(':') -> documentId.substringBefore(':') + ":"
            else -> return null
        }
        if (parentId.isBlank() || parentId == documentId) return null
        val authority = treeUri.authority ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, parentId)
    }

    fun displayName(context: Context, treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return queryName(context, documentUri)
    }

    fun documentName(context: Context, documentUri: Uri): String? = queryName(context, documentUri)

    fun locationLabel(context: Context, location: String): String =
        if (!isSaf(location)) location
        else displayName(context, Uri.parse(location)) ?: "Documents"

    private fun queryName(context: Context, documentUri: Uri): String? = runCatching {
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    fun createFolder(context: Context, treeUri: Uri, name: String): Result<Uri> = runCatching {
        val parent = parentDocumentUri(treeUri)
        DocumentsContract.createDocument(
            context.contentResolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw IllegalStateException("The folder could not be created.")
    }

    fun rename(context: Context, documentUri: Uri, name: String): Result<Uri> = runCatching {
        DocumentsContract.renameDocument(context.contentResolver, documentUri, name)
            ?: throw IllegalStateException("The item could not be renamed.")
    }

    fun delete(context: Context, documentUri: Uri): Result<Unit> = runCatching {
        if (!DocumentsContract.deleteDocument(context.contentResolver, documentUri)) {
            throw IllegalStateException("The item could not be deleted.")
        }
    }

    fun copy(context: Context, sourceDocument: Uri, destTree: Uri, name: String): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(sourceDocument) ?: "application/octet-stream"
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            throw IllegalStateException("Copying folders between document providers is not supported yet.")
        }
        val target = DocumentsContract.createDocument(
            resolver,
            parentDocumentUri(destTree),
            mime,
            name,
        ) ?: throw IllegalStateException("The item could not be copied.")
        resolver.openInputStream(sourceDocument)?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                ?: throw IllegalStateException("The destination could not be opened.")
        } ?: throw IllegalStateException("The source could not be opened.")
        target
    }

    fun move(context: Context, sourceDocument: Uri, destTree: Uri, name: String): Result<Uri> {
        val copy = copy(context, sourceDocument, destTree, name)
        return copy.onSuccess { delete(context, sourceDocument) }
    }

    fun copyFromFile(context: Context, source: File, destTree: Uri, name: String): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        val mime = if (source.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
        if (source.isDirectory) {
            throw IllegalStateException("Copying folders into a document provider is not supported yet.")
        }
        val target = DocumentsContract.createDocument(
            resolver,
            parentDocumentUri(destTree),
            mime,
            name,
        ) ?: throw IllegalStateException("The item could not be copied.")
        source.inputStream().use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                ?: throw IllegalStateException("The destination could not be opened.")
        }
        target
    }

    fun copyToFile(context: Context, sourceDocument: Uri, destDir: File, name: String): Result<File> = runCatching {
        val target = uniqueFile(destDir, name)
        context.contentResolver.openInputStream(sourceDocument)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("The source could not be opened.")
        target
    }

    /** Copies a SAF document into the app cache so the archive engine can read a real path. */
    fun copyToCache(context: Context, sourceDocument: Uri, name: String): File? = runCatching {
        val dir = File(context.cacheDir, "saf")
        if (!dir.exists()) dir.mkdirs()
        val target = uniqueFile(dir, name)
        context.contentResolver.openInputStream(sourceDocument)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    fun viewIntent(context: Context, documentUri: Uri, mime: String?): Intent? {
        val type = mime ?: context.contentResolver.getType(documentUri) ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentUri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun parentDocumentUri(treeUri: Uri): Uri {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    }

    private fun uniqueFile(dir: File, name: String): File {
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

/** Paste that works across plain files, SAF trees and mixtures of both. */
object MixedFileOps {
    fun paste(
        context: Context,
        destLocation: String,
        sourceLocation: String,
        cut: Boolean,
    ): Result<String> = runCatching {
        val destSaf = SafStorage.isSaf(destLocation)
        val srcSaf = SafStorage.isSaf(sourceLocation)
        val name = if (srcSaf) {
            SafStorage.documentName(context, Uri.parse(sourceLocation)) ?: "item"
        } else {
            File(sourceLocation).name
        }
        if (destSaf) {
            val destTree = Uri.parse(destLocation)
            val result = if (srcSaf) {
                if (cut) SafStorage.move(context, Uri.parse(sourceLocation), destTree, name)
                else SafStorage.copy(context, Uri.parse(sourceLocation), destTree, name)
            } else {
                SafStorage.copyFromFile(context, File(sourceLocation), destTree, name)
            }
            result.getOrThrow()
            if (cut && !srcSaf) {
                FileOperations.delete(File(sourceLocation)).getOrThrow()
            }
            name
        } else {
            val destDir = File(destLocation)
            if (srcSaf) {
                SafStorage.copyToFile(context, Uri.parse(sourceLocation), destDir, name).getOrThrow()
                if (cut) SafStorage.delete(context, Uri.parse(sourceLocation)).getOrThrow()
                name
            } else {
                FileOperations.paste(destDir).getOrThrow()?.name ?: name
            }
        }
    }
}
