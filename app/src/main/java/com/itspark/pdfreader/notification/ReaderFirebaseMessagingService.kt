package com.itspark.pdfreader.notification

import android.content.Context
import android.content.Intent
import android.util.Log
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.presentation.home.HomeActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

/**
 * Handles incoming Firebase Cloud Messaging (FCM) push notifications and manages
 * cohort-segmented topic subscriptions for targeted re-engagement campaigns.
 */
class ReaderFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"

        /**
         * Subscribes the device to FCM topics based on user attribution cohort.
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
        syncAttributionTopics(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

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
}
