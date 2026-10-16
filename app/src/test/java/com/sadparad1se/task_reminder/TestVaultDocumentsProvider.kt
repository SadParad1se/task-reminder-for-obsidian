package com.sadparad1se.task_reminder

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/** Real document-tree queries backed by temporary files, including configurable read failures. */
internal class TestVaultDocumentsProvider(private val root: File) : ContentProvider() {
    var unreadable: String? = null
    override fun onCreate() = true
    // Robolectric routes resolver calls through the legacy query overload, which Android 16's
    // DocumentsProvider rejects. Dispatch the document URIs explicitly in this test provider.
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val id = DocumentsContract.getDocumentId(uri)
        return if (uri.lastPathSegment == "children") queryChildDocuments(id, projection) else queryDocument(id, projection)
    }

    override fun getType(uri: Uri): String = if (file(DocumentsContract.getDocumentId(uri)).isDirectory) {
        DocumentsContract.Document.MIME_TYPE_DIR
    } else {
        "text/markdown"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = error("Read-only test provider")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only test provider")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only test provider")

    private fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        return cursor(projection).apply { addDocument(documentId) }
    }

    private fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?): Cursor {
        return cursor(projection).apply {
            file(parentDocumentId).listFiles().orEmpty().forEach { addDocument("$parentDocumentId/${it.name}") }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val documentId = DocumentsContract.getDocumentId(uri)
        if (documentId == unreadable) throw FileNotFoundException("Test read failure")
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun file(id: String) = if (id == "root") root else File(root, id.removePrefix("root/"))

    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    ))

    private fun MatrixCursor.addDocument(id: String) {
        val file = file(id)
        addRow(columnNames.map<String, Any?> {
            when (it) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> file.name
                DocumentsContract.Document.COLUMN_MIME_TYPE -> if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "text/markdown"
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                else -> null
            }
        }.toTypedArray())
    }
}
