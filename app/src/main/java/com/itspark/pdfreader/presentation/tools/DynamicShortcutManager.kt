package com.itspark.pdfreader.presentation.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.itspark.pdfreader.R
import com.itspark.pdfreader.presentation.home.HomeActivity
import com.itspark.pdfreader.presentation.splash.SplashActivity
import com.itspark.pdfreader.presentation.tools.ImageToPdfPreviewActivity
import com.itspark.pdfreader.presentation.viewer.PdfViewerActivity

/**
 * Manages Android Dynamic App Shortcuts, enabling 1-tap re-engagement
 * for the user's most recently viewed documents directly from the home screen launcher.
 */
object DynamicShortcutManager {

    private const val TAG = "DynamicShortcutManager"
    private const val DYNAMIC_SHORTCUT_ID_PREFIX = "dynamic_doc_"

    @Volatile
    private var defaultShortcutsPublished = false

    /**
     * Publishes a dynamic launcher shortcut for the given document.
     */
    fun pushDocumentShortcut(context: Context, docName: String, docPath: String, docUri: Uri) {
        try {
            val mainComponent = ComponentName(context, SplashActivity::class.java)

            val launchIntent = Intent(context, PdfViewerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setPackage(context.packageName)
                putExtra(PdfViewerActivity.EXTRA_DOC_PATH, docPath)
                putExtra(PdfViewerActivity.EXTRA_DOC_NAME, docName)
                putExtra(PdfViewerActivity.EXTRA_DOC_URI, docUri.toString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val shortcutId = "$DYNAMIC_SHORTCUT_ID_PREFIX${docName.hashCode()}"
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setActivity(mainComponent)
                .setShortLabel(docName.take(15))
                .setLongLabel("Open $docName")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_doc_page_pdf))
                .setIntent(launchIntent)
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            Log.d(TAG, "Dynamic shortcut pushed: $docName ($shortcutId)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push dynamic shortcut: ${e.message}")
        }
    }

    /**
     * Publishes default tool shortcuts programmatically using AndroidX ShortcutManagerCompat.
     * Backwards-compatible across all API levels: active on API 25+, safely no-ops on API 23-24.
     */
    fun publishDefaultShortcuts(context: Context) {
        if (defaultShortcutsPublished) return
        try {
            val mainComponent = ComponentName(context, SplashActivity::class.java)

            val scanIntent = Intent(context, ImageToPdfPreviewActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val recentIntent = Intent(context, HomeActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("navigate_to", "recent")
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val allDocsIntent = Intent(context, HomeActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("navigate_to", "home")
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val scanShortcut = ShortcutInfoCompat.Builder(context, "shortcut_scan")
                .setActivity(mainComponent)
                .setShortLabel(context.getString(R.string.shortcut_scan_short))
                .setLongLabel(context.getString(R.string.shortcut_scan_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_nav_tools))
                .setIntent(scanIntent)
                .build()

            val recentShortcut = ShortcutInfoCompat.Builder(context, "shortcut_recent")
                .setActivity(mainComponent)
                .setShortLabel(context.getString(R.string.shortcut_recent_short))
                .setLongLabel(context.getString(R.string.shortcut_recent_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_nav_recent))
                .setIntent(recentIntent)
                .build()

            val allDocsShortcut = ShortcutInfoCompat.Builder(context, "shortcut_all_docs")
                .setActivity(mainComponent)
                .setShortLabel(context.getString(R.string.shortcut_all_short))
                .setLongLabel(context.getString(R.string.shortcut_all_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_doc_page_pdf))
                .setIntent(allDocsIntent)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(scanShortcut, recentShortcut, allDocsShortcut))
            defaultShortcutsPublished = true
            Log.d(TAG, "Default dynamic shortcuts set via AndroidX (target: ${mainComponent.className})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish default shortcuts: ${e.message}")
        }
    }
}
