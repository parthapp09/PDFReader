package com.itspark.pdfreader.monetization

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.itspark.pdfreader.consent.ConsentManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * Manages loading, caching, and showing App Open Ads for:
 * 1. Cold start launch (SplashActivity)
 * 2. Returning to foreground from background (>30s threshold)
 */
class AppOpenAdManager(private val context: Context) {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val MIN_BACKGROUND_TIME_MS = 30000L // 30 seconds threshold
        private const val AD_EXPIRY_MS = 4 * 3600 * 1000L // AdMob App Open ads expire after 4 hours
    }

    private val configRepo = AdConfigRepository(context)

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd: Boolean = false
    private var loadTimeMs: Long = 0L

    private var appBackgroundTimestamp: Long = 0L

    /**
     * Call when application moves to the background.
     */
    fun onAppBackgrounded() {
        appBackgroundTimestamp = SystemClock.elapsedRealtime()
        Log.d(TAG, "App backgrounded at $appBackgroundTimestamp")
    }

    /**
     * Checks if the app was backgrounded long enough to qualify for a foreground App Open ad.
     */
    fun hasSufficientBackgroundTime(): Boolean {
        if (appBackgroundTimestamp <= 0) return false
        val duration = SystemClock.elapsedRealtime() - appBackgroundTimestamp
        return duration >= MIN_BACKGROUND_TIME_MS
    }

    private fun isAdAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return appOpenAd != null && (now - loadTimeMs) < AD_EXPIRY_MS
    }

    /**
     * Preloads an App Open Ad into memory.
     */
    fun preloadAd(onLoaded: (() -> Unit)? = null) {
        if (isLoadingAd || isAdAvailable()) {
            onLoaded?.invoke()
            return
        }

        if (!ConsentManager.canRequestAds(context)) {
            Log.d(TAG, "Preload skipped: Consent not granted")
            return
        }

        val placement = AdPlacement.RESUME_APP_OPEN
        if (!configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Preload skipped: Placement disabled")
            return
        }

        isLoadingAd = true
        val adUnitId = configRepo.getAdUnitId(placement)
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAd = false
                    appOpenAd = ad
                    loadTimeMs = SystemClock.elapsedRealtime()
                    Log.i(TAG, "App Open Ad loaded successfully")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    Log.w(TAG, "App Open Ad failed to load: ${loadAdError.message} (code ${loadAdError.code})")
                }
            }
        )
    }

    /**
     * Displays the App Open Ad if available and all policy checks pass.
     */
    fun showAdIfAvailable(
        activity: Activity,
        placement: AdPlacement = AdPlacement.RESUME_APP_OPEN,
        onComplete: () -> Unit
    ) {
        if (!ConsentManager.canRequestAds(activity)) {
            Log.d(TAG, "Cannot show App Open Ad: Consent not granted")
            onComplete()
            return
        }

        if (!configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Cannot show App Open Ad: Placement disabled")
            onComplete()
            return
        }

        if (!MonetizationManager.shouldShowAppOpenAd(activity)) {
            Log.d(TAG, "Cannot show App Open Ad: Cohort policy disallowed")
            onComplete()
            return
        }

        if (!AdDisplayCoordinator.canShowFullScreenAd()) {
            Log.d(TAG, "Cannot show App Open Ad: AdDisplayCoordinator mutex active")
            onComplete()
            return
        }

        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            Log.d(TAG, "App Open Ad not cached. Preloading for next time...")
            preloadAd()
            onComplete()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdDisplayCoordinator.onAdStartedShowing(AdFormatType.APP_OPEN)
                Log.i(TAG, "App Open Ad displayed on screen")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "App Open Ad dismissed")
                appOpenAd = null
                AdDisplayCoordinator.onAdDismissed()
                preloadAd() // Cache next ad
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "App Open Ad failed to show: ${adError.message}")
                appOpenAd = null
                AdDisplayCoordinator.onAdDismissed()
                preloadAd()
                onComplete()
            }
        }

        ad.show(activity)
    }

    private var coldStartAd: AppOpenAd? = null
    private var isLoadingColdStart: Boolean = false

    /**
     * Preloads the cold start App Open Ad in parallel with the splash animation.
     */
    fun preloadColdStartAd(activity: Activity, onLoaded: (() -> Unit)? = null) {
        val placement = AdPlacement.SPLASH_APP_OPEN
        if (isLoadingColdStart || coldStartAd != null) {
            onLoaded?.invoke()
            return
        }

        if (!ConsentManager.canRequestAds(activity) ||
            !configRepo.isPlacementEnabled(placement) ||
            !MonetizationManager.shouldShowAppOpenAd(activity)
        ) {
            return
        }

        isLoadingColdStart = true
        val adUnitId = configRepo.getAdUnitId(placement)
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            activity,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingColdStart = false
                    coldStartAd = ad
                    Log.i(TAG, "Cold Start App Open Ad preloaded successfully")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingColdStart = false
                    coldStartAd = null
                    Log.w(TAG, "Cold Start App Open Ad failed to preload: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Cold start specific load & show flow (with timeout support).
     */
    fun loadAndShowColdStart(activity: Activity, onComplete: () -> Unit) {
        val placement = AdPlacement.SPLASH_APP_OPEN

        if (!ConsentManager.canRequestAds(activity) ||
            !configRepo.isPlacementEnabled(placement) ||
            !MonetizationManager.shouldShowAppOpenAd(activity)
        ) {
            onComplete()
            return
        }

        var hasFinished = false
        fun finishOnce() {
            if (!hasFinished) {
                hasFinished = true
                onComplete()
            }
        }

        fun showAd(ad: AppOpenAd) {
            if (activity.isFinishing || activity.isDestroyed) {
                finishOnce()
                return
            }

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    AdDisplayCoordinator.onAdStartedShowing(AdFormatType.APP_OPEN)
                }

                override fun onAdDismissedFullScreenContent() {
                    AdDisplayCoordinator.onAdDismissed()
                    coldStartAd = null
                    finishOnce()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    AdDisplayCoordinator.onAdDismissed()
                    coldStartAd = null
                    finishOnce()
                }
            }

            ad.show(activity)
        }

        // If ad was already preloaded during the 2.5s splash animation, show immediately!
        val cachedAd = coldStartAd
        if (cachedAd != null) {
            showAd(cachedAd)
            return
        }

        val adUnitId = configRepo.getAdUnitId(placement)
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            activity,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    showAd(ad)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Splash App Open Ad failed: ${loadAdError.message}")
                    finishOnce()
                }
            }
        )
    }
}
