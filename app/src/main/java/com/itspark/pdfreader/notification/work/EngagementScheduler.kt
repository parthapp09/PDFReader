package com.itspark.pdfreader.notification.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Manages WorkManager triggers for engagement, unlock checks, and inactivity followups.
 */
object EngagementScheduler {

    private const val TAG = "EngagementScheduler"
    private const val PERIODIC_WORK_NAME = "periodic_engagement_worker"
    private const val INACTIVITY_WORK_NAME = "inactivity_followup_worker"

    /**
     * Schedules a recurring 24-hour background engagement task.
     */
    fun schedulePeriodicEngagement(context: Context) {
        val periodicRequest = PeriodicWorkRequestBuilder<EngagementNotificationWorker>(
            24, TimeUnit.HOURS,
            6, TimeUnit.HOURS // flex interval
        ).setInitialDelay(8, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        Log.d(TAG, "Periodic 24h engagement worker registered")
    }

    /**
     * Evaluates whether to show a re-engagement notification upon device unlock.
     */
    fun evaluateUnlockEngagement(context: Context) {
        val unlockRequest = OneTimeWorkRequestBuilder<EngagementNotificationWorker>()
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "unlock_engagement_worker",
            ExistingWorkPolicy.KEEP,
            unlockRequest
        )
    }

    /**
     * Schedules a gentle followup reminder after the app has been in the background for 18 hours.
     */
    fun scheduleExitFollowup(context: Context) {
        val followupRequest = OneTimeWorkRequestBuilder<EngagementNotificationWorker>()
            .setInitialDelay(18, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            INACTIVITY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            followupRequest
        )
    }
}
