package io.github.zixt233.pirt.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import io.github.zixt233.pirt.R
import java.io.File
import java.io.FileNotFoundException

/** Exposes the single PIRT workspace through Android's Storage Access Framework. */
class WorkspaceDocumentsProvider : DocumentsProvider() {
    private val workspace: File
        get() = File(requireNotNull(context).filesDir, "pirt/workspace").apply { mkdirs() }.canonicalFile

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        cursor.newRow()
            .add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            .add(DocumentsContract.Root.COLUMN_TITLE, "PIRT")
            .add(DocumentsContract.Root.COLUMN_SUMMARY, "Workspace")
            .add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.pirt_launcher)
            .add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY or
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
            )
            .add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).also { cursor ->
            includeDocument(cursor, fileForId(documentId))
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory: $parentDocumentId")
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).also { cursor ->
            parent.listFiles().orEmpty()
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                .forEach { includeDocument(cursor, it) }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val file = fileForId(documentId)
        if (!file.isFile) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForId(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        validateName(displayName)
        val target = File(parent, displayName)
        check(!target.exists()) { "$displayName already exists" }
        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile()
        check(created) { "Could not create $displayName" }
        notifyChanged(parentDocumentId)
        return idForFile(target)
    }

    override fun deleteDocument(documentId: String) {
        check(documentId != ROOT_DOCUMENT_ID) { "The workspace root cannot be deleted" }
        val file = fileForId(documentId)
        val parentId = file.parentFile?.let(::idForFile)
        check(file.deleteRecursively()) { "Could not delete ${file.name}" }
        parentId?.let(::notifyChanged)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        check(documentId != ROOT_DOCUMENT_ID) { "The workspace root cannot be renamed" }
        validateName(displayName)
        val source = fileForId(documentId)
        val target = File(requireNotNull(source.parentFile), displayName)
        check(!target.exists()) { "$displayName already exists" }
        check(source.renameTo(target)) { "Could not rename ${source.name}" }
        notifyChanged(idForFile(requireNotNull(target.parentFile)))
        return idForFile(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileForId(parentDocumentId).canonicalFile
        val child = fileForId(documentId).canonicalFile
        return child.path == parent.path || child.path.startsWith(parent.path + File.separator)
    }

    private fun includeDocument(cursor: MatrixCursor, file: File) {
        if (!file.exists()) throw FileNotFoundException(file.path)
        val directory = file.isDirectory
        val flags = if (directory) {
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        } else {
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }
        cursor.newRow()
            .add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, idForFile(file))
            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, if (file == workspace) "Workspace" else file.name)
            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType(file))
            .add(DocumentsContract.Document.COLUMN_SIZE, if (directory) null else file.length())
            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(DocumentsContract.Document.COLUMN_FLAGS, flags)
    }

    private fun fileForId(documentId: String): File {
        val root = workspace
        val relative = when {
            documentId == ROOT_DOCUMENT_ID -> ""
            documentId.startsWith("$ROOT_DOCUMENT_ID/") -> documentId.removePrefix("$ROOT_DOCUMENT_ID/")
            else -> throw FileNotFoundException(documentId)
        }
        val candidate = File(root, relative).canonicalFile
        if (candidate.path != root.path && !candidate.path.startsWith(root.path + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        return candidate
    }

    private fun idForFile(file: File): String {
        val root = workspace
        val candidate = file.canonicalFile
        if (candidate.path == root.path) return ROOT_DOCUMENT_ID
        check(candidate.path.startsWith(root.path + File.separator)) { "File is outside workspace" }
        return "$ROOT_DOCUMENT_ID/${candidate.relativeTo(root).invariantSeparatorsPath}"
    }

    private fun mimeType(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name) {
            "Invalid file name"
        }
    }

    private fun notifyChanged(documentId: String) {
        requireNotNull(context).contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(authority(requireNotNull(context)), documentId),
            null,
        )
    }

    companion object {
        const val ROOT_ID = "pirt-workspace"
        const val ROOT_DOCUMENT_ID = "workspace"

        fun authority(context: android.content.Context): String = "${context.packageName}.workspace.documents"

        fun openInSystemFileManager(context: Context): Result<Unit> = runCatching {
            val authority = authority(context)
            val rootUri = DocumentsContract.buildRootUri(authority, ROOT_ID)
            val documentUri = DocumentsContract.buildDocumentUri(authority, ROOT_DOCUMENT_ID)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
            val intents = listOf(
                Intent(Intent.ACTION_VIEW).apply {
                    data = rootUri
                    addFlags(flags)
                },
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentUri)
                    addFlags(flags)
                },
            )
            val intent = intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
                ?: intents.first()
            context.startActivity(
                if (intent.resolveActivity(context.packageManager) != null) {
                    intent
                } else {
                    Intent.createChooser(intent, "打开 Workspace")
                },
            )
        }

        private val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
