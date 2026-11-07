package com.itspark.pdfreader.notification.observers

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.itspark.pdfreader.R
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.AppNotificationChannel
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.presentation.tools.ImageToPdfPreviewActivity

/**
 * Monitors MediaStore.Images.Media.EXTERNAL_CONTENT_URI for screenshot captures.
 * Prompts the user with an actionable notification to convert the screenshot to a PDF.
 */
class ScreenshotObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "ScreenshotObserver"
    }

    private var lastHandledTimestamp = 0L

    init {
        // Establish initial baseline on registration so existing screenshots on device never trigger an alert
        try {
            val projection = arrayOf(MediaStore.Images.Media.DATE_ADDED)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lastHandledTimestamp = cursor.getLong(0)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (uri == null || !RemoteConfigManager.isScreenshotEnabled())  return

        try {
            inspectImageUri(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting image URI: ${e.message}")
        }
    }

    private fun inspectImageUri(uri: Uri) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: ""
                val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))

                val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                        path.contains("screenshot", ignoreCase = true) ||
                        path.contains("Screenshots", ignoreCase = true)

                // Ensure it's a recent screenshot (within the last 15 seconds) and not a duplicate
                val nowSec = System.currentTimeMillis() / 1000
                if (isScreenshot && (nowSec - dateAdded) < 15 && dateAdded != lastHandledTimestamp) {
                    lastHandledTimestamp = dateAdded
                    dispatchScreenshotNotification(uri, name)
                }
            }
        }
    }

    private fun dispatchScreenshotNotification(imageUri: Uri, imageName: String) {
        Log.i(TAG, "Screenshot detected: $imageName | URI: $imageUri")

        // Deep-link intent targeting Image to PDF preview screen
        val deepLink = ImageToPdfPreviewActivity.createIntent(context, listOf(imageUri))

        val convertAction = NotificationCompat.Action.Builder(
            R.drawable.ic_tool_image_to_pdf,
            "Convert to PDF",
            NotificationEngine.createActionPendingIntent(
                context = context,
                notificationId = NotificationEngine.ID_SCREENSHOT_EVENT,
                channelId = AppNotificationChannel.TOOLS.id,
                targetIntent = deepLink,
                requestCode = 201
            )
        ).build()

        NotificationEngine.showNotification(
            context = context,
            notificationId = NotificationEngine.ID_SCREENSHOT_EVENT,
            channel = AppNotificationChannel.TOOLS,
            title = "Screenshot Captured",
            body = "Convert your screenshot into a clean PDF document with one tap.",
            deepLinkIntent = deepLink,
            actions = listOf(convertAction),
            forceBypassCooldown = true // User-initiated event, bypass standard cooldown
        )
    }
}
