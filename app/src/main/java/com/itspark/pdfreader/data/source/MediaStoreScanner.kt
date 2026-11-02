package com.itspark.pdfreader.data.source

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    private val supportedExtensions = listOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv", "txt")

    suspend fun scanDocuments(): List<DocumentModel> = withContext(Dispatchers.IO) {
        val documents = ArrayList<DocumentModel>(256)

        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = supportedExtensions.joinToString(separator = " OR ") {
            "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        }
        val selectionArgs = supportedExtensions.map { "%.${it}" }.toTypedArray()
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            val cursor = context.contentResolver.query(
                contentUri,
                projection,
                "($selection) AND ${MediaStore.Files.FileColumns.SIZE} > 0",
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawName = it.getString(nameCol)
                    val path = it.getString(dataCol) ?: continue
                    val size = it.getLong(sizeCol)
                    if (size <= 0) continue

                    // Fast non-blocking filename & extension extraction (no disk I/O)
                    val fileName = if (!rawName.isNullOrBlank()) {
                        rawName
                    } else {
                        path.substringAfterLast('/')
                    }

                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (ext.isEmpty()) continue

                    val format = DocumentFormat.fromExtension(ext)
                    val dateModified = it.getLong(dateCol) * 1000L
                    val uri = ContentUris.withAppendedId(contentUri, id)

                    documents.add(
                        DocumentModel(
                            id = id,
                            name = fileName,
                            path = path,
                            uri = uri,
                            sizeBytes = size,
                            dateModifiedMs = dateModified,
                            extension = ext,
                            format = format
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Direct scan for "Documents/PDF Reader" so newly created PDFs appear immediately without waiting for MediaStore indexing
        try {
            val createdDocsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "PDF Reader"
            )
            if (createdDocsDir.exists() && createdDocsDir.isDirectory) {
                // Instant memory-only lookup (zero disk I/O)
                val existingPaths = HashSet<String>(documents.size)
                for (i in 0 until documents.size) {
                    existingPaths.add(documents[i].path)
                }

                createdDocsDir.listFiles { file -> file.isFile && file.extension.equals("pdf", ignoreCase = true) }?.forEach { f ->
                    val path = f.absolutePath
                    if (!existingPaths.contains(path) && f.length() > 0) {
                        documents.add(
                            0, // Insert at top because it is the most recent file
                            DocumentModel(
                                id = f.hashCode().toLong(),
                                name = f.name,
                                path = path,
                                uri = Uri.fromFile(f),
                                sizeBytes = f.length(),
                                dateModifiedMs = f.lastModified(),
                                extension = "pdf",
                                format = DocumentFormat.PDF
                            )
                        )
                        existingPaths.add(path)
                    }
                }
            }
        } catch (_: Exception) {}

        documents
    }
}
