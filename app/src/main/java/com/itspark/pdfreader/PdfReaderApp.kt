package com.itspark.pdfreader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.monetization.AppOpenAdManager
import com.itspark.pdfreader.monetization.InterstitialAdManager
import com.itspark.pdfreader.monetization.MonetizationManager
import com.itspark.pdfreader.monetization.NativeAdManager
import com.itspark.pdfreader.monetization.RewardedAdManager
import com.itspark.pdfreader.notification.NotificationEngine
import com.itspark.pdfreader.notification.ReaderFirebaseMessagingService
import com.itspark.pdfreader.notification.observers.DocumentDownloadsObserver
import com.itspark.pdfreader.notification.observers.ScreenshotObserver
import com.itspark.pdfreader.notification.work.EngagementScheduler
import com.itspark.pdfreader.presentation.splash.SplashActivity
import com.google.android.gms.ads.MobileAds
import com.itspark.pdfreader.service.DocumentSyncForegroundService
import java.lang.ref.WeakReference

/**
 * Custom Application class orchestrating MobileAds initialization,
 * foreground lifecycle observation, and global ad management.
 */
class PdfReaderApp : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "PdfReaderApp"

        lateinit var instance: PdfReaderApp
            private set
    }

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    lateinit var interstitialAdManager: InterstitialAdManager
        private set

    lateinit var nativeAdManager: NativeAdManager
        private set

    lateinit var rewardedAdManager: RewardedAdManager
        private set

    var isAppInForeground: Boolean = false
        private set

    private var screenshotObserver: ScreenshotObserver? = null
    private var documentDownloadsObserver: DocumentDownloadsObserver? = null

    private var currentActivityRef: WeakReference<Activity>? = null

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this

        // 1. Initialize Cohort Monetization Engine
        MonetizationManager.initialize(this)

        // 2. Initialize Google Mobile Ads SDK asynchronously
        MobileAds.initialize(this) { initializationStatus ->
            Log.i(TAG, "Mobile Ads SDK initialized successfully: $initializationStatus")
        }

        // 3. Initialize Ad Managers
        appOpenAdManager = AppOpenAdManager(this)
        interstitialAdManager = InterstitialAdManager(this)
        nativeAdManager = NativeAdManager(this)
        rewardedAdManager = RewardedAdManager(this)

        // 4. Register Activity & Process Lifecycle Observers
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // 5. Initialize Remote Config & Feature Flags
        RemoteConfigManager.initialize(this)

        // 6. Initialize Notification System & Engagement Engine
        NotificationEngine.initializeChannels(this)
        EngagementScheduler.schedulePeriodicEngagement(this)
        ReaderFirebaseMessagingService.syncAttributionTopics(this)
        registerFileObservers()

        // 7. Start Foreground Document Synchronization Service if enabled
        DocumentSyncForegroundService.startIfEnabled(this)
    }

    /**
     * Registers application-level ContentObservers for screenshot capture and new document downloads.
     * Persists across the application process lifecycle so downloads outside the app are detected.
     */
    fun registerFileObservers() {
        val hasStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager() ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasStorage) {
            try {
                if (screenshotObserver == null) {
                    screenshotObserver = ScreenshotObserver(this)
                    contentResolver.registerContentObserver(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        true,
                        screenshotObserver!!
                    )
                }
                if (documentDownloadsObserver == null) {
                    documentDownloadsObserver = DocumentDownloadsObserver(this)
                    contentResolver.registerContentObserver(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        true,
                        documentDownloadsObserver!!
                    )
                }
                Log.d(TAG, "Application-level file ContentObservers registered successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register application file ContentObservers: ${e.message}")
            }
        }
    }

    // ─── ProcessLifecycleOwner (Foreground / Background) ─────────────────────

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        val currentActivity = currentActivityRef?.get()

        // Do not trigger background-resume App Open ad on the Splash screen, or if disabled remotely
        if (currentActivity != null && currentActivity !is SplashActivity) {
            if (RemoteConfigManager.isAdsGlobalEnabled() &&
                RemoteConfigManager.isResumeAppOpenEnabled() &&
                appOpenAdManager.hasSufficientBackgroundTime()
            ) {
                Log.d(TAG, "Application foregrounded with sufficient background duration. Showing App Open ad...")
                appOpenAdManager.showAdIfAvailable(currentActivity) {
                    // Ad completed or skipped
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        appOpenAdManager.onAppBackgrounded()
        // Schedule exit / inactivity engagement check
        EngagementScheduler.scheduleExitFollowup(this)
    }

    // ─── ActivityLifecycleCallbacks ──────────────────────────────────────────

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }
}
