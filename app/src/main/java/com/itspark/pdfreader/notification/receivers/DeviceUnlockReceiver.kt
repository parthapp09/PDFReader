package com.itspark.pdfreader.notification.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.notification.work.EngagementScheduler

/**
 * Listens for system device unlock (ACTION_USER_PRESENT).
 * Evaluates contextual re-engagement if minimum interval cooldown allows.
 */
class DeviceUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DeviceUnlockReceiver"
        private const val DEBOUNCE_WINDOW_MS = 3000L

        @Volatile
        private var lastHandledTimestampMs: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "DeviceUnlockReceiver onReceive: action = $action")

        if (action == Intent.ACTION_USER_PRESENT || action == Intent.ACTION_SCREEN_ON) {
            val now = System.currentTimeMillis()
            // If screen was just turned on and user promptly unlocks within 3s, avoid duplicate execution
            if ((now - lastHandledTimestampMs) < DEBOUNCE_WINDOW_MS && action == Intent.ACTION_SCREEN_ON) {
                return
            }
            lastHandledTimestampMs = now

            if (!RemoteConfigManager.isUnlockEnabled()) {
                Log.d(TAG, "Unlock trigger skipped: disabled via Remote Config (notif_unlock_enabled = false)")
                return
            }
            // Do not trigger unlock engagement if the user is already actively inside the app
            if (NotificationEngine.isAppInForeground()) {
                Log.d(TAG, "Device unlocked/screen on but app is already active in foreground. Skipping.")
                return
            }
            Log.i(TAG, "Device unlocked by user ($action). Evaluating engagement opportunity...")
            EngagementScheduler.evaluateUnlockEngagement(context)
        }
    }
}
