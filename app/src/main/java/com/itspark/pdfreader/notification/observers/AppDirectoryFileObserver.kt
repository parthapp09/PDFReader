package com.itspark.pdfreader.notification.observers

import android.os.Build
import android.os.FileObserver
import android.util.Log
import java.io.File

/**
 * Low-level Linux inotify FileObserver monitoring a specific directory for new document writes.
 * Supports both modern API 29+ (File constructor) and legacy Android versions.
 */
class AppDirectoryFileObserver(
    val directory: File,
    private val onDocumentDiscovered: (file: File, source: MessagingApp) -> Unit
) {

    companion object {
        private const val TAG = "DirectoryFileObserver"
        private val SUPPORTED_EXTENSIONS = setOf("pdf", "docx", "xlsx", "pptx", "txt")
        private const val MASK = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
    }

    private var fileObserver: FileObserver? = null
    private var isWatching = false
    private var lastObservedFile: String? = null
    private var lastObservedTime: Long = 0L

    fun start() {
        if (isWatching || !directory.exists() || !directory.isDirectory) return

        try {
            fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(directory, MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        handleInotifyEvent(event, path)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(directory.absolutePath, MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        handleInotifyEvent(event, path)
                    }
                }
            }

            fileObserver?.startWatching()
            isWatching = true
            Log.d(TAG, "Started watching directory: ${directory.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to watch directory ${directory.absolutePath}: ${e.message}")
        }
    }

    fun stop() {
        try {
            if (isWatching) {
                fileObserver?.stopWatching()
                fileObserver = null
                isWatching = false
                Log.d(TAG, "Stopped watching directory: ${directory.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping observer for ${directory.absolutePath}: ${e.message}")
        }
    }

    private fun handleInotifyEvent(event: Int, fileName: String?) {
        if (fileName == null) return

        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in SUPPORTED_EXTENSIONS) return

        val now = System.currentTimeMillis()
        // Debounce repeated inotify events for the same file within 5 seconds
        if (fileName == lastObservedFile && (now - lastObservedTime) < 5000L) {
            return
        }

        val targetFile = File(directory, fileName)
        if (targetFile.exists() && targetFile.length() > 0) {
            lastObservedFile = fileName
            lastObservedTime = now

            val source = MessagingApp.resolveSource(targetFile.absolutePath)
            Log.i(TAG, "FileObserver captured document: ${targetFile.name} from ${source.displayName}")
            onDocumentDiscovered(targetFile, source)
        }
    }
}
