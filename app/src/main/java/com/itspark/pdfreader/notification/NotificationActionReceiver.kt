package com.itspark.pdfreader.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.itspark.pdfreader.analytics.AnalyticsTracker

/**
 * Handles interaction callbacks for notification clicks and dismissals (swiping away).
 * Dispatches analytics telemetry and routes deep-link intents cleanly.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReceiver"
        const val ACTION_NOTIFICATION_CLICKED = "com.itspark.pdfreader.NOTIFICATION_CLICKED"
        const val ACTION_NOTIFICATION_DISMISSED = "com.itspark.pdfreader.NOTIFICATION_DISMISSED"
        const val EXTRA_TARGET_INTENT = "extra_target_intent"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: "default"

        when (intent.action) {
            ACTION_NOTIFICATION_CLICKED -> {
                Log.d(TAG, "Notification clicked: id=$notifId, channel=$channelId")
                AnalyticsTracker.logNotificationClick(context, notifId.toString(), channelId)
                // Dismiss notification from shade upon action/body click
                try {
                    androidx.core.app.NotificationManagerCompat.from(context).cancel(notifId)
                } catch (_: Exception) {}

                val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_TARGET_INTENT)
                }

                targetIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(it)
                }
            }

            ACTION_NOTIFICATION_DISMISSED -> {
                Log.d(TAG, "Notification dismissed: id=$notifId, channel=$channelId")
                AnalyticsTracker.logNotificationDismissed(context, notifId.toString(), channelId)
            }
        }
    }
}
