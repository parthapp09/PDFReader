package com.itspark.pdfreader.notification.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.notification.ReaderFirebaseMessagingService
import com.itspark.pdfreader.notification.work.EngagementScheduler
import com.itspark.pdfreader.service.DocumentSyncForegroundService

/**
 * BroadcastReceiver listening for device reboot and application update events.
 * Guarantees automated restoration of:
 * 1. Background WorkManager periodic engagement jobs
 * 2. Notification channels and alerts
 * 3. FCM push-topic subscriptions
 * 4. Persistent foreground synchronization services
 * 5. Remote configuration warm-up
 */
class AppRestorationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppRestorationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "System event received: $action - Restoring app background operations")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                try {
                    // 1. Re-initialize Notification Channels
                    NotificationEngine.initializeChannels(context)

                    // 2. Re-register and enforce 24-hour periodic WorkManager jobs
                    EngagementScheduler.schedulePeriodicEngagement(context)

                    // 3. Resync FCM attribution topics
                    ReaderFirebaseMessagingService.syncAttributionTopics(context)

                    // 4. Initialize Remote Config defaults and fetch fresh parameters
                    RemoteConfigManager.initialize(context)

                    // 5. Restore persistent sync foreground service if enabled
                    DocumentSyncForegroundService.startIfEnabled(context)

                    Log.i(TAG, "All background operations successfully restored for action: $action")
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring background operations: ${e.message}", e)
                }
            }
        }
    }
}
