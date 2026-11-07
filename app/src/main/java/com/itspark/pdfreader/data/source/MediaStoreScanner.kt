package com.itspark.pdfreader.data.source

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.notification.observers.DirectoryWatcherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    private val supportedExtensions = setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv", "txt")

    suspend fun scanDocuments(): List<DocumentModel> = withContext(Dispatchers.IO) {
        val documents = ArrayList<DocumentModel>(256)
        val existingPaths = HashSet<String>(256)

        // 1. Query Android MediaStore
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
            "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${it}' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.${it}')"
        }
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            val cursor = context.contentResolver.query(
                contentUri,
                projection,
                "($selection) AND ${MediaStore.Files.FileColumns.SIZE} > 0",
                null,
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
                    val rawPath = it.getString(dataCol)
                    val size = it.getLong(sizeCol)
                    if (size <= 0) continue

                    val fileName = if (!rawName.isNullOrBlank()) {
                        rawName
                    } else if (!rawPath.isNullOrBlank()) {
                        rawPath.substringAfterLast('/')
                    } else {
                        "document"
                    }

                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (ext.isEmpty() || !supportedExtensions.contains(ext)) continue

                    val path = if (!rawPath.isNullOrBlank()) {
                        rawPath
                    } else {
                        File(Environment.getExternalStorageDirectory(), fileName).absolutePath
                    }

                    val format = DocumentFormat.fromExtension(ext)
                    val dateModified = it.getLong(dateCol) * 1000L
                    val uri = ContentUris.withAppendedId(contentUri, id)

                    if (existingPaths.add(path.lowercase())) {
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Direct File System Scan for Common Document & Messaging App Directories
        // Catches all downloaded/transferred files even before MediaStore indexing occurs
        try {
            val candidateDirs = DirectoryWatcherManager.resolveCandidateDirectories()
            for (dir in candidateDirs) {
                scanDirectoryRecursive(dir, documents, existingPaths, maxDepth = 3)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort descending by date modified
        documents.sortByDescending { it.dateModifiedMs }
        documents
    }

    private fun scanDirectoryRecursive(
        dir: File,
        results: ArrayList<DocumentModel>,
        existingPaths: HashSet<String>,
        maxDepth: Int
    ) {
        if (maxDepth <= 0) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirectoryRecursive(file, results, existingPaths, maxDepth - 1)
                }
            } else if (file.isFile && file.length() > 0) {
                val ext = file.extension.lowercase()
                if (supportedExtensions.contains(ext)) {
                    val path = file.absolutePath
                    if (existingPaths.add(path.lowercase())) {
                        results.add(
                            DocumentModel(
                                id = file.hashCode().toLong(),
                                name = file.name,
                                path = path,
                                uri = Uri.fromFile(file),
                                sizeBytes = file.length(),
                                dateModifiedMs = file.lastModified(),
                                extension = ext,
                                format = DocumentFormat.fromExtension(ext)
                            )
                        )
                    }
                }
            }
        }
    }
}
