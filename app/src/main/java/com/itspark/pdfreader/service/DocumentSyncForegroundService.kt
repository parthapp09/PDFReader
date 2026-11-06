package com.itspark.pdfreader.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.itspark.pdfreader.PdfReaderApp
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.notification.NotificationEngine

/**
 * Persistent foreground service for continuous document index synchronization
 * and storage monitoring. Returns START_STICKY to guarantee automatic resurrection
 * by the Android OS if terminated under memory pressure.
 */
class DocumentSyncForegroundService : Service() {

    companion object {
        private const val TAG = "DocSyncService"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.itspark.pdfreader.action.START_SYNC"
        const val ACTION_STOP = "com.itspark.pdfreader.action.STOP_SYNC"

        /**
         * Starts the persistent foreground service if enabled by Remote Config.
         */
        fun startIfEnabled(context: Context) {
            if (!RemoteConfigManager.isPeriodicWorkerEnabled() && !RemoteConfigManager.isOutOfAppNotifEnabled()) {
                Log.d(TAG, "Foreground sync skipped: disabled by remote configuration")
                return
            }
            try {
                val intent = Intent(context, DocumentSyncForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
                Log.i(TAG, "DocumentSyncForegroundService start requested")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start foreground service: ${e.message}")
            }
        }

        /**
         * Stops the foreground service.
         */
        fun stop(context: Context) {
            try {
                val intent = Intent(context, DocumentSyncForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DocumentSyncForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationEngine.createOngoingSyncNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Ensure file observers are registered for system download/screenshot detection
        try {
            PdfReaderApp.instance.registerFileObservers()
        } catch (e: Exception) {
            Log.w(TAG, "Observer registration from service: ${e.message}")
        }

        Log.i(TAG, "DocumentSyncForegroundService running in foreground (START_STICKY)")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DocumentSyncForegroundService destroyed")
    }
}
