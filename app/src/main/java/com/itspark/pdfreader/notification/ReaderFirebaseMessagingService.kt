package com.itspark.pdfreader.notification

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.notification.work.EngagementScheduler
import com.itspark.pdfreader.presentation.home.HomeActivity
import com.itspark.pdfreader.service.DocumentSyncForegroundService
import kotlin.random.Random

/**
 * Handles incoming Firebase Cloud Messaging (FCM) push notifications, FCM-based
 * remote command delivery, background silent synchronization, and automatic
 * cohort-segmented push-topic resubscription.
 */
class ReaderFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"

        // Recognized remote command actions delivered via FCM data payloads
        const val CMD_REFRESH_CONFIG = "REFRESH_CONFIG"
        const val CMD_RESYNC_TOPICS = "RESYNC_TOPICS"
        const val CMD_TRIGGER_ENGAGEMENT = "TRIGGER_ENGAGEMENT"
        const val CMD_START_SYNC = "START_SYNC"
        const val CMD_STOP_SYNC = "STOP_SYNC"
        const val CMD_PURGE_CACHE = "PURGE_CACHE"

        /**
         * Subscribes the device to FCM topics based on user attribution cohort.
         * Ensures automatic resubscription across app launches and token rotations.
         */
        fun syncAttributionTopics(context: Context) {
            try {
                val prefs = PreferencesRepository(context)
                val cohort = prefs.getAttributionType() // ORGANIC, PAID_GOOGLE, PAID_OTHER

                FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                FirebaseMessaging.getInstance().subscribeToTopic("cohort_${cohort.name.lowercase()}")
                Log.d(TAG, "Subscribed to FCM topics: 'all_users', 'cohort_${cohort.name.lowercase()}'")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync FCM topics: ${e.message}")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Token received: $token")
        // Automatically resubscribe to all cohort topics upon token refresh
        syncAttributionTopics(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // 1. Process FCM-based background commands (Silent push operations)
        val command = remoteMessage.data["command"] ?: remoteMessage.data["action"]
        if (!command.isNullOrEmpty()) {
            Log.i(TAG, "Delivered remote command received: $command")
            handleBackgroundCommand(command, remoteMessage.data)
            // If the command doesn't request a visible notification, return early
            val isSilent = remoteMessage.data["is_silent"] == "true" || remoteMessage.notification == null
            if (isSilent && remoteMessage.data["title"] == null) {
                return
            }
        }

        // 2. Render visible notification or full-screen urgent engagement
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Document Reader"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "You have new document updates."

        val targetTab = remoteMessage.data["target_tab"] ?: "home"

        val deepLinkIntent = Intent(this, HomeActivity::class.java).apply {
            putExtra("navigate_to", targetTab)
            remoteMessage.data.forEach { (key, value) ->
                putExtra(key, value)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val isUrgent = remoteMessage.data["is_urgent"] == "true" || remoteMessage.data["full_screen"] == "true"

        if (isUrgent) {
            NotificationEngine.showUrgentFullScreenNotification(
                context = this,
                title = title,
                body = body,
                badge = remoteMessage.data["badge"] ?: "⚠️ URGENT ACTION REQUIRED",
                actionText = remoteMessage.data["action_text"] ?: "Review Now",
                targetTab = targetTab
            )
        } else {
            NotificationEngine.showNotification(
                context = this,
                notificationId = Random.nextInt(100_000, 999_999),
                channel = AppNotificationChannel.PROMOTIONAL,
                title = title,
                body = body,
                deepLinkIntent = deepLinkIntent,
                forceBypassCooldown = true,
                accumulate = true // Accumulate in notification shade
            )
        }
    }

    /**
     * Executes background operations triggered silently via Firebase Cloud Messaging.
     */
    private fun handleBackgroundCommand(command: String, data: Map<String, String>) {
        when (command) {
            CMD_REFRESH_CONFIG -> {
                Log.d(TAG, "Executing CMD_REFRESH_CONFIG: Initializing and fetching Remote Config")
                RemoteConfigManager.initialize(applicationContext)
            }
            CMD_RESYNC_TOPICS -> {
                Log.d(TAG, "Executing CMD_RESYNC_TOPICS")
                syncAttributionTopics(applicationContext)
                data["subscribe_topic"]?.let { topic ->
                    FirebaseMessaging.getInstance().subscribeToTopic(topic)
                }
            }
            CMD_TRIGGER_ENGAGEMENT -> {
                Log.d(TAG, "Executing CMD_TRIGGER_ENGAGEMENT: Evaluating unlock & engagement flow")
                EngagementScheduler.evaluateUnlockEngagement(applicationContext)
            }
            CMD_START_SYNC -> {
                Log.d(TAG, "Executing CMD_START_SYNC: Starting foreground sync service")
                DocumentSyncForegroundService.startIfEnabled(applicationContext)
            }
            CMD_STOP_SYNC -> {
                Log.d(TAG, "Executing CMD_STOP_SYNC")
                DocumentSyncForegroundService.stop(applicationContext)
            }
            CMD_PURGE_CACHE -> {
                Log.d(TAG, "Executing CMD_PURGE_CACHE: Purging temporary cache files")
                try {
                    applicationContext.cacheDir?.deleteRecursively()
                } catch (e: Exception) {
                    Log.w(TAG, "Cache purge failed: ${e.message}")
                }
            }
            else -> Log.w(TAG, "Unknown background command: $command")
        }
    }
}
