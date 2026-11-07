package com.itspark.pdfreader.notification.work

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.itspark.pdfreader.notification.AppNotificationChannel
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationTemplateProvider
import com.itspark.pdfreader.presentation.home.HomeActivity
import java.util.Calendar
import kotlin.random.Random

/**
 * Background WorkManager worker responsible for periodic re-engagement reminders.
 * Selects rotating, time-of-day appropriate notification templates.
 */
class EngagementNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!RemoteConfigManager.isPeriodicWorkerEnabled() || !RemoteConfigManager.isOutOfAppNotifEnabled()) {
            return Result.success()
        }
        if (NotificationEngine.isAppInForeground()) {
            return Result.success()
        }
        // Alternate between high-impact full-screen urgent warnings and rotating engagement reminders
        if (Random.nextBoolean()) {
            val urgent = NotificationTemplateProvider.getRandomUrgentTemplate()
            NotificationEngine.showUrgentFullScreenNotification(
                context = applicationContext,
                title = urgent.title,
                body = urgent.body,
                actionText = urgent.actionText ?: "Review Now",
                targetTab = urgent.targetTab
            )
        } else {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val template = NotificationTemplateProvider.getTemplateForTimeOfDay(currentHour)

            val targetIntent = Intent(applicationContext, HomeActivity::class.java).apply {
                putExtra("navigate_to", template.targetTab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            NotificationEngine.showNotification(
                context = applicationContext,
                notificationId = Random.nextInt(100_000, 999_999),
                channel = AppNotificationChannel.RE_ENGAGEMENT,
                title = template.title,
                body = template.body,
                deepLinkIntent = targetIntent,
                forceBypassCooldown = true,
                accumulate = true // Accumulates in notification shade
            )
        }

        return Result.success()
    }
}
