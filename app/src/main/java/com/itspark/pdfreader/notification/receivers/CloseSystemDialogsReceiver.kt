package com.itspark.pdfreader.notification.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.presentation.tools.DynamicShortcutManager

/**
 * Listens for system UI dismissals (ACTION_CLOSE_SYSTEM_DIALOGS) which occur
 * when the user presses the hardware/navigation Home key, opens Recent Apps,
 * or performs a full-screen home gesture.
 *
 * Triggers local background preparation, dynamic shortcut updates, and
 * evaluates exit/background engagement opportunities.
 */
class CloseSystemDialogsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CloseSysDialogsRecv"
        const val SYSTEM_DIALOG_REASON_KEY = "reason"
        const val REASON_HOME_KEY = "homekey"
        const val REASON_RECENT_APPS = "recentapps"
        const val REASON_FS_GESTURE = "fs_gesture"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return

        if (!RemoteConfigManager.isSystemDialogsTriggerEnabled()) {
            return
        }

        val reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY)
        Log.d(TAG, "Captured CLOSE_SYSTEM_DIALOGS event. Reason: $reason")

        when (reason) {
            REASON_HOME_KEY, REASON_FS_GESTURE -> {
                Log.d(TAG, "User exited to Home Screen via navigation/gesture. Executing local actions...")
                onUserNavigatedHome(context)
            }
            REASON_RECENT_APPS -> {
                Log.d(TAG, "User switched to Recent Apps. Updating shortcuts...")
                DynamicShortcutManager.publishDefaultShortcuts(context)
            }
        }
    }

    private fun onUserNavigatedHome(context: Context) {
        // 1. Maintain fresh dynamic shortcuts on Home Screen
        DynamicShortcutManager.publishDefaultShortcuts(context)

        // 2. If out-of-app engagement is enabled, evaluate delayed exit/inactivity reminder
        if (RemoteConfigManager.isExitAlertEnabled()) {
            NotificationEngine.scheduleDelayedExitAlert(context)
        }
    }
}
