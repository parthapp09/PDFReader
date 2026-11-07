package com.itspark.pdfreader.notification.observers

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationEngine

/**
 * Monitors MediaStore.Files for newly downloaded or received documents.
 * Dispatches a high-priority heads-up notification allowing the user to view the file immediately.
 */
class DocumentDownloadsObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "DownloadsObserver"
    }

    private var lastObservedFileId: Long = -1L
    private var lastObservedTimestamp: Long = 0L

    init {
        // Establish initial baseline on registration so existing files on device never trigger an alert
        try {
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATE_ADDED)
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lastObservedFileId = cursor.getLong(0)
                    lastObservedTimestamp = cursor.getLong(1)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (!RemoteConfigManager.isDownloadsEnabled()) {
            return
        }
        // If the user is currently in the app, the Home list already updates live
        if (NotificationEngine.isAppInForeground()) {
            return
        }
        try {
            detectRecentDocument()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking media store for new documents: ${e.message}")
        }
    }

    private fun detectRecentDocument() {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_ADDED
        )

        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.docx' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xlsx' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pptx' OR " +
                "${MediaStore.Files.FileColumns.DATA} LIKE '%.pdf' OR " +
                "${MediaStore.Files.FileColumns.DATA} LIKE '%.docx'"

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: "Document"
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)) ?: ""
                val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))

                val nowSec = System.currentTimeMillis() / 1000
                // Check if file was added within the last 20 seconds
                if (id != lastObservedFileId && (nowSec - dateAdded) < 20 && dateAdded != lastObservedTimestamp) {
                    lastObservedFileId = id
                    lastObservedTimestamp = dateAdded

                    val nowMs = System.currentTimeMillis()
                    val lastNotified = DirectoryWatcherManager.recentNotifiedFiles[path]
                    if (lastNotified == null || (nowMs - lastNotified) >= 15000L) {
                        DirectoryWatcherManager.recentNotifiedFiles[path] = nowMs
                        val source = MessagingApp.resolveSource(path)
                        Log.i(TAG, "New document detected: $name ($path) via ${source.displayName}")
                        NotificationEngine.dispatchIncomingDocumentNotification(context, name, path, source)
                    }
                }
            }
        }
    }
}
