package com.itspark.pdfreader.notification.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.notification.work.EngagementScheduler

/**
 * Listens for system device unlock (ACTION_USER_PRESENT).
 * Evaluates contextual re-engagement if minimum interval cooldown allows.
 */
class DeviceUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DeviceUnlockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            // Do not trigger unlock engagement if the user is already actively inside the app
            if (NotificationEngine.isAppInForeground()) {
                Log.d(TAG, "Device unlocked but app is already active in foreground. Skipping.")
                return
            }
            Log.d(TAG, "Device unlocked by user. Evaluating engagement opportunity...")
            EngagementScheduler.evaluateUnlockEngagement(context)
        }
    }
}
