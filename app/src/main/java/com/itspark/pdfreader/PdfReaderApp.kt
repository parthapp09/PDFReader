package com.itspark.pdfreader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.itspark.pdfreader.monetization.AppOpenAdManager
import com.itspark.pdfreader.monetization.InterstitialAdManager
import com.itspark.pdfreader.monetization.MonetizationManager
import com.itspark.pdfreader.monetization.NativeAdManager
import com.itspark.pdfreader.monetization.RewardedAdManager
import com.itspark.pdfreader.presentation.splash.SplashActivity
import com.google.android.gms.ads.MobileAds
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
    }

    // ─── ProcessLifecycleOwner (Foreground / Background) ─────────────────────

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        val currentActivity = currentActivityRef?.get()

        // Do not trigger background-resume App Open ad on the Splash screen
        if (currentActivity != null && currentActivity !is SplashActivity) {
            if (appOpenAdManager.hasSufficientBackgroundTime()) {
                Log.d(TAG, "Application foregrounded with sufficient background duration. Showing App Open ad...")
                appOpenAdManager.showAdIfAvailable(currentActivity) {
                    // Ad completed or skipped
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        appOpenAdManager.onAppBackgrounded()
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
