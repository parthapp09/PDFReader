package com.itspark.pdfreader.notification.observers

import android.content.Context
import android.os.Environment
import android.util.Log
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages low-level Linux inotify FileObservers across standard system folders
 * and popular messaging download directories (WhatsApp, Telegram, Messenger,
 * Viber, LINE, Discord, Zalo, Teams, Tencent, Gapo).
 */
class DirectoryWatcherManager private constructor(context: Context) {

    // Store only Application Context to prevent memory leaks if an Activity is passed
    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "DirWatcherManager"

        @Volatile
        private var instance: DirectoryWatcherManager? = null

        fun getInstance(context: Context): DirectoryWatcherManager {
            return instance ?: synchronized(this) {
                instance ?: DirectoryWatcherManager(context.applicationContext).also { instance = it }
            }
        }

        // Shared deduplication cache between ContentObserver and FileObserver (filePath -> timestamp)
        val recentNotifiedFiles = ConcurrentHashMap<String, Long>()
        private const val NOTIFY_DEBOUNCE_WINDOW_MS = 15000L

        /**
         * Resolves all existing and readable candidate directories where documents can be saved
         * by downloads or messaging apps (WhatsApp, Telegram, Messenger, Teams, etc.).
         * Shared between real-time FileObservers and MediaStoreScanner.
         */
        fun resolveCandidateDirectories(): List<File> {
            val candidates = mutableListOf<File>()

            // 1. Standard System Downloads & Documents
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { candidates.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { candidates.add(it) }

            val root = Environment.getExternalStorageDirectory()
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            candidates.add(File(root, "Download"))
            candidates.add(File(root, "Documents"))

            // 2. WhatsApp & WhatsApp Business
            candidates.add(File(root, "WhatsApp/Media/WhatsApp Documents"))
            candidates.add(File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"))
            candidates.add(File(root, "WhatsApp Business/Media/WhatsApp Business Documents"))

            // 3. Telegram
            candidates.add(File(root, "Telegram/Telegram Documents"))
            candidates.add(File(downloads, "Telegram"))

            // 4. Messenger
            candidates.add(File(downloads, "Messenger"))

            // 5. Viber
            candidates.add(File(root, "viber/media"))
            candidates.add(File(downloads, "Viber"))

            // 6. LINE
            candidates.add(File(downloads, "LINE"))

            // 7. Discord
            candidates.add(File(downloads, "Discord"))

            // 8. Zalo
            candidates.add(File(downloads, "Zalo"))
            candidates.add(File(root, "Zalo"))

            // 9. Microsoft Teams
            candidates.add(File(downloads, "Microsoft Teams"))
            candidates.add(File(downloads, "Teams"))

            // 10. Tencent / WeChat / QQ
            candidates.add(File(root, "tencent/MicroMsg/Download"))
            candidates.add(File(root, "tencent/QQfile_recv"))

            // 11. Gapo
            candidates.add(File(downloads, "Gapo"))

            // Filter only existing and readable directories, deduplicated by canonical path
            return candidates
                .filter { it.exists() && it.isDirectory && it.canRead() }
                .distinctBy { try { it.canonicalPath } catch (_: Exception) { it.absolutePath } }
        }
    }

    private val activeObservers = mutableListOf<AppDirectoryFileObserver>()
    private var isStarted = false

    /**
     * Starts watching all candidate directories.
     */
    fun startWatching() {
        if (isStarted) return
        if (!RemoteConfigManager.isFileObserverEnabled()) {
            Log.d(TAG, "FileObserver is disabled via Remote Config.")
            return
        }

        val targetDirs = resolveCandidateDirectories()
        Log.i(TAG, "Initializing FileObservers for ${targetDirs.size} candidate directories...")

        activeObservers.clear()
        for (dir in targetDirs) {
            val observer = AppDirectoryFileObserver(dir) { file, source ->
                handleDetectedFile(file, source)
            }
            observer.start()
            activeObservers.add(observer)
        }
        isStarted = true
    }

    /**
     * Stops all active FileObservers.
     */
    fun stopWatching() {
        if (!isStarted) return
        for (observer in activeObservers) {
            observer.stop()
        }
        activeObservers.clear()
        isStarted = false
        Log.i(TAG, "Stopped all directory FileObservers")
    }

    private fun handleDetectedFile(file: File, source: MessagingApp) {
        if (!RemoteConfigManager.isDownloadsEnabled()) return
        if (NotificationEngine.isAppInForeground()) {
            Log.d(TAG, "File detected while app is in foreground. Skipping notification.")
            return
        }

        val filePath = file.absolutePath
        val now = System.currentTimeMillis()

        // Check if this file was already notified within the debounce window
        val lastNotified = recentNotifiedFiles[filePath]
        if (lastNotified != null && (now - lastNotified) < NOTIFY_DEBOUNCE_WINDOW_MS) {
            return
        }
        recentNotifiedFiles[filePath] = now

        // Prune stale cache entries if size exceeds 100 to prevent unbounded memory growth
        if (recentNotifiedFiles.size > 100) {
            val cutoff = now - NOTIFY_DEBOUNCE_WINDOW_MS
            recentNotifiedFiles.entries.removeIf { it.value < cutoff }
        }

        Log.i(TAG, "Dispatching notification for: ${file.name} (Source: ${source.displayName})")
        NotificationEngine.dispatchIncomingDocumentNotification(appContext, file.name, filePath, source)
    }
}
