package com.itspark.pdfreader.notification

import android.app.NotificationManager

/**
 * Definition of dedicated Notification Channels for Android 8.0+ (API 26+).
 * Separates file events, tool utilities, re-engagement reminders, and announcements.
 */
enum class AppNotificationChannel(
    val id: String,
    val channelName: String,
    val description: String,
    val importance: Int,
    val sound: Boolean = true
) {
    FILES(
        id = "channel_file_events",
        channelName = "File Alerts & Downloads",
        description = "Alerts when new documents or files are downloaded or received",
        importance = NotificationManager.IMPORTANCE_HIGH
    ),
    TOOLS(
        id = "channel_tools_conversion",
        channelName = "Tools & PDF Utilities",
        description = "Image to PDF conversions and document utility shortcuts",
        importance = NotificationManager.IMPORTANCE_HIGH
    ),
    RE_ENGAGEMENT(
        id = "channel_reengagement",
        channelName = "Document Reminders & Tips",
        description = "Reading reminders, recent document shortcuts, and helpful tips",
        importance = NotificationManager.IMPORTANCE_DEFAULT
    ),
    PROMOTIONAL(
        id = "channel_announcements",
        channelName = "Updates & Features",
        description = "Feature updates and service announcements",
        importance = NotificationManager.IMPORTANCE_HIGH
    )
}
