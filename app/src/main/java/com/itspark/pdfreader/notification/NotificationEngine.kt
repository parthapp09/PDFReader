package com.itspark.pdfreader.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.itspark.pdfreader.R
import com.itspark.pdfreader.analytics.AnalyticsTracker
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.fullscreen.FullScreenPromotionalActivity
import com.itspark.pdfreader.presentation.home.HomeActivity
import kotlin.random.Random

/**
 * Central engine managing notification lifecycle, channels, frequency throttling,
 * duplicate controls, deep-links, and impression/interaction telemetry.
 */
object NotificationEngine {

    private const val TAG = "NotificationEngine"
    private const val PREFS_NAME = "notification_engine_prefs"
    private const val KEY_LAST_NOTIF_TIME = "last_notification_timestamp"
    private const val KEY_MIN_INTERVAL_MS = "min_notification_interval_ms"

    // Default 2-hour minimum cooldown between non-critical notifications
    private const val DEFAULT_MIN_INTERVAL_MS = 2 * 60 * 60 * 1000L

    // Fixed / unified notification IDs to prevent notification shade clutter
    const val ID_SCREENSHOT_EVENT = 1001
    const val ID_NEW_FILE_EVENT = 1002
    const val ID_ENGAGEMENT_REMINDER = 2001
    const val ID_FCM_PUSH = 3001

    @Volatile
    private var channelsInitialized = false

    private val exitHandler = Handler(Looper.getMainLooper())
    private var pendingExitRunnable: Runnable? = null

    /**
     * Initializes all Notification Channels for Android 8.0+ (API 26+).
     * Guarded to avoid repeated redundant IPC system calls.
     */
    fun initializeChannels(context: Context) {
        if (channelsInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null) {
                AppNotificationChannel.values().forEach { channelDef ->
                    val channel = NotificationChannel(
                        channelDef.id,
                        channelDef.channelName,
                        channelDef.importance
                    ).apply {
                        description = channelDef.description
                        enableLights(true)
                        enableVibration(channelDef.sound)
                    }
                    manager.createNotificationChannel(channel)
                }
                channelsInitialized = true
                Log.d(TAG, "Notification channels initialized successfully")
            }
        }
    }

    /**
     * Creates an Action PendingIntent routing through NotificationActionReceiver
     * so button taps log click telemetry and automatically dismiss the notification.
     */
    fun createActionPendingIntent(
        context: Context,
        notificationId: Int,
        channelId: String,
        targetIntent: Intent,
        requestCode: Int = Random.nextInt(10_000, 99_999)
    ): PendingIntent {
        val clickIntent = Intent(context.applicationContext, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_CLICKED
            putExtra(NotificationActionReceiver.EXTRA_TARGET_INTENT, targetIntent)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_CHANNEL_ID, channelId)
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Sets dynamic remotely configurable minimum interval between notifications.
     */
    fun setRemoteMinIntervalMs(context: Context, intervalMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_MIN_INTERVAL_MS, intervalMs).apply()
    }

    /**
     * Checks if the app is currently open and visible in the foreground.
     */
    fun isAppInForeground(): Boolean {
        return try {
            com.itspark.pdfreader.PdfReaderApp.instance.isAppInForeground
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if notification permission is granted on the device.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Dispatches a notification if permission and frequency throttling allow.
     * Supports CATEGORY_CALL, fullScreenIntent, and random accumulating notification IDs.
     */
    @SuppressLint("MissingPermission")
    fun showNotification(
        context: Context,
        notificationId: Int,
        channel: AppNotificationChannel,
        title: String,
        body: String,
        deepLinkIntent: Intent,
        largeIcon: Bitmap? = null,
        bigPicture: Bitmap? = null,
        actions: List<NotificationCompat.Action> = emptyList(),
        forceBypassCooldown: Boolean = false,
        category: String? = null,
        fullScreenIntent: Intent? = null,
        accumulate: Boolean = false
    ) {
        val appContext = context.applicationContext

        // Remote Config Master Kill-Switch for Out-of-App Notifications
        if (!RemoteConfigManager.isOutOfAppNotifEnabled()) {
            Log.d(TAG, "Notification skipped: out-of-app notifications disabled by Remote Config")
            return
        }

        // 0. Ensure Notification Channels are created on Android 8.0+
        initializeChannels(appContext)

        // 1. Android 13+ Runtime Permission Check
        if (!hasNotificationPermission(appContext)) {
            Log.w(TAG, "Notification skipped: Notification permission not granted")
            return
        }

        // Suppress re-engagement and exit notifications if the user is ALREADY inside the app
        if (isAppInForeground() && (channel == AppNotificationChannel.RE_ENGAGEMENT || channel == AppNotificationChannel.PROMOTIONAL)) {
            Log.d(TAG, "Notification suppressed: user is already actively using the app")
            return
        }

        // 2. Frequency & Minimum Interval Cooldown Enforcement
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotif = prefs.getLong(KEY_LAST_NOTIF_TIME, 0L)
        val minInterval = prefs.getLong(KEY_MIN_INTERVAL_MS, DEFAULT_MIN_INTERVAL_MS)
        val now = System.currentTimeMillis()

        if (!forceBypassCooldown && (now - lastNotif) < minInterval) {
            val remainingSec = (minInterval - (now - lastNotif)) / 1000
            Log.d(TAG, "Notification throttled: cooldown active ($remainingSec seconds remaining)")
            return
        }

        // Dynamic ID assignment: Use random ID if accumulation is requested and permitted by Remote Config
        val shouldAccumulate = accumulate && RemoteConfigManager.isDuplicateAccumulateEnabled()
        val effectiveId = if (shouldAccumulate) Random.nextInt(100_000, 999_999) else notificationId

        // 3. Content Intent (with Click Tracking)
        val clickIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_CLICKED
            putExtra(NotificationActionReceiver.EXTRA_TARGET_INTENT, deepLinkIntent)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, effectiveId)
            putExtra(NotificationActionReceiver.EXTRA_CHANNEL_ID, channel.id)
        }
        val contentPendingIntent = PendingIntent.getBroadcast(
            appContext,
            effectiveId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Delete Intent (with Dismissal Tracking)
        val dismissIntent = Intent(appContext, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_DISMISSED
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, effectiveId)
            putExtra(NotificationActionReceiver.EXTRA_CHANNEL_ID, channel.id)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            appContext,
            effectiveId + 5000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Build Notification
        val allowHeadsUp = RemoteConfigManager.isHeadsUpEnabled()
        val builder = NotificationCompat.Builder(appContext, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(
                if (allowHeadsUp && (channel.importance == NotificationManager.IMPORTANCE_HIGH || category == NotificationCompat.CATEGORY_CALL))
                    NotificationCompat.PRIORITY_MAX
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )

        // Assign category (if fullScreenIntent is present, CATEGORY_CALL is accepted; otherwise use CATEGORY_REMINDER)
        val effectiveCategory = if (category == NotificationCompat.CATEGORY_CALL && fullScreenIntent == null) {
            NotificationCompat.CATEGORY_REMINDER
        } else {
            category
        }
        effectiveCategory?.let { builder.setCategory(it) }

        // Attach Full Screen Intent if enabled in Remote Config
        val allowFullScreen = RemoteConfigManager.isFullScreenEnabled()
        if (allowFullScreen && fullScreenIntent != null) {
            val fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                effectiveId + 10000,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        largeIcon?.let { builder.setLargeIcon(it) }

        // Expandable Rich Style
        if (bigPicture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        // Actionable Buttons
        actions.forEach { action -> builder.addAction(action) }

        try {
            val notificationManager = NotificationManagerCompat.from(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification skipped: POST_NOTIFICATIONS not granted")
                return
            }
            notificationManager.notify(effectiveId, builder.build())

            // Update timestamp & log impression
            prefs.edit().putLong(KEY_LAST_NOTIF_TIME, now).apply()
            AnalyticsTracker.logNotificationImpression(appContext, effectiveId.toString(), channel.id)
            Log.i(TAG, "Notification successfully posted: id=$effectiveId, channel=${channel.id}, category=$effectiveCategory")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display notification", e)
        }
    }

    /**
     * Shows a high-urgency notification with:
     * 1. CATEGORY_CALL priority
     * 2. Full-Screen Intent launching FullScreenPromotionalActivity
     * 3. Random notification IDs allowing multiple notifications to accumulate
     * 4. Urgency/warning messaging to drive app re-opening
     */
    fun showUrgentFullScreenNotification(
        context: Context,
        title: String,
        body: String,
        badge: String = "⚠️ URGENT ACTION REQUIRED",
        actionText: String = "Scan & Secure Workspace",
        targetTab: String = "home",
        countdownSeconds: Int = RemoteConfigManager.getCountdownSeconds()
    ) {
        val fsIntent = Intent(context, FullScreenPromotionalActivity::class.java).apply {
            putExtra(FullScreenPromotionalActivity.EXTRA_TITLE, title)
            putExtra(FullScreenPromotionalActivity.EXTRA_BODY, body)
            putExtra(FullScreenPromotionalActivity.EXTRA_BADGE, badge)
            putExtra(FullScreenPromotionalActivity.EXTRA_ACTION_TEXT, actionText)
            putExtra(FullScreenPromotionalActivity.EXTRA_TARGET_TAB, targetTab)
            putExtra(FullScreenPromotionalActivity.EXTRA_COUNTDOWN_SECONDS, countdownSeconds)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notifId = Random.nextInt(100_000, 999_999)

        val primaryAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            actionText,
            createActionPendingIntent(
                context = context,
                notificationId = notifId,
                channelId = AppNotificationChannel.FILES.id,
                targetIntent = fsIntent,
                requestCode = notifId + 1
            )
        ).build()

        showNotification(
            context = context,
            notificationId = notifId,
            channel = AppNotificationChannel.FILES,
            title = title,
            body = body,
            deepLinkIntent = fsIntent,
            actions = listOf(primaryAction),
            forceBypassCooldown = true,
            category = NotificationCompat.CATEGORY_CALL,
            fullScreenIntent = fsIntent,
            accumulate = true // Accumulates multiple un-collapsed notifications
        )
    }

    /**
     * Triggered when the user presses Home or switches to Recent Apps (onUserLeaveHint)
     * or confirms Exit on the in-app Exit dialog.
     * Fires an urgent notification with FullScreenIntent targeting FullScreenPromotionalActivity.
     */
    fun showRecentAppsExitNotification(context: Context) {
        val appContext = context.applicationContext
        if (!RemoteConfigManager.isExitAlertEnabled()) {
            Log.d(TAG, "Exit notification skipped: disabled by Remote Config")
            return
        }
        val template = NotificationTemplateProvider.getRandomUrgentTemplate()
        showUrgentFullScreenNotification(
            context = appContext,
            title = template.title,
            body = template.body,
            badge = "⚠️ CRITICAL ACTION REQUIRED",
            actionText = "Inspect & Secure Workspace",
            targetTab = template.targetTab,
            countdownSeconds = RemoteConfigManager.getCountdownSeconds()
        )
    }

    /**
     * Schedules an exit notification to fire shortly after the activity finishes
     * so it arrives cleanly while the user is looking at their device launcher screen.
     * Debounced to prevent duplicate simultaneous executions.
     */
    fun showDelayedExitNotification(context: Context, delayMs: Long = RemoteConfigManager.getExitDelayMs()) {
        val appContext = context.applicationContext
        pendingExitRunnable?.let { exitHandler.removeCallbacks(it) }
        val runnable = Runnable {
            showRecentAppsExitNotification(appContext)
            pendingExitRunnable = null
        }
        pendingExitRunnable = runnable
        exitHandler.postDelayed(runnable, delayMs)
    }

    /**
     * Cancels a specific notification by ID.
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(notificationId)
        } catch (_: Exception) {}
    }

    /**
     * Creates an ongoing persistent notification for the background document sync foreground service.
     */
    fun createOngoingSyncNotification(context: Context): Notification {
        initializeChannels(context)
        val targetIntent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, AppNotificationChannel.FOREGROUND_SERVICE.id)
            .setSmallIcon(R.drawable.ic_nav_all_docs)
            .setContentTitle("Document Reader Sync Active")
            .setContentText("Monitoring and indexing local documents for quick access")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
