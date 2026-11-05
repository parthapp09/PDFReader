package com.itspark.pdfreader.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.itspark.pdfreader.consent.ConsentManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages loading, caching, frequency-capping, and presentation of Interstitial Ads.
 */
class InterstitialAdManager(private val context: Context) {

    companion object {
        private const val TAG = "InterstitialAdManager"
    }

    private val configRepo = AdConfigRepository(context)

    // Cached interstitial per placement key
    private val cachedAds = mutableMapOf<AdPlacement, InterstitialAd?>()
    private val loadingPlacements = mutableSetOf<AdPlacement>()

    /**
     * Preloads an interstitial ad for a specific placement.
     */
    fun preload(placement: AdPlacement, onLoaded: (() -> Unit)? = null) {
        if (loadingPlacements.contains(placement) || cachedAds[placement] != null) {
            onLoaded?.invoke()
            return
        }

        if (!ConsentManager.canRequestAds(context) || !configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Preload skipped for [$placement]: Consent or placement disabled")
            return
        }

        loadingPlacements.add(placement)
        val adUnitId = configRepo.getAdUnitId(placement)
        val request = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    loadingPlacements.remove(placement)
                    cachedAds[placement] = interstitialAd
                    Log.i(TAG, "Interstitial loaded for placement [${placement.placementKey}]")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    loadingPlacements.remove(placement)
                    cachedAds[placement] = null
                    Log.w(TAG, "Interstitial failed for [${placement.placementKey}]: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows the interstitial ad if permitted by cohort cooldown, session cap, and display mutex.
     * Always invokes onAdClosed whether the ad is shown, skipped, or fails.
     */
    fun showIfAllowed(
        activity: Activity,
        placement: AdPlacement,
        onAdClosed: () -> Unit
    ) {
        // 1. Consent Policy Verification
        if (!ConsentManager.canRequestAds(activity)) {
            Log.d(TAG, "Interstitial skipped: Consent not granted")
            onAdClosed()
            return
        }

        // 2. Remote Placement Enablement
        if (!configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Interstitial skipped: Placement [${placement.placementKey}] disabled remotely")
            onAdClosed()
            return
        }

        // 3. Cohort Rules (Cooldown & Session Cap)
        if (!MonetizationManager.canShowInterstitial(activity, placement.placementKey)) {
            Log.d(TAG, "Interstitial skipped: Cohort cooldown or cap active for [${placement.placementKey}]")
            onAdClosed()
            return
        }

        // 4. Overlap & Display Mutex Lock
        if (!AdDisplayCoordinator.canShowFullScreenAd()) {
            Log.d(TAG, "Interstitial skipped: Full-screen ad mutex active")
            onAdClosed()
            return
        }

        val cachedAd = cachedAds[placement]
        if (cachedAd == null) {
            Log.d(TAG, "No cached interstitial for [${placement.placementKey}]. Preloading for next trigger...")
            preload(placement)
            onAdClosed()
            return
        }

        cachedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdDisplayCoordinator.onAdStartedShowing(AdFormatType.INTERSTITIAL)
                MonetizationManager.recordInterstitialShown(activity, placement.placementKey)
                Log.i(TAG, "Interstitial displayed for placement [${placement.placementKey}]")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "Interstitial dismissed for placement [${placement.placementKey}]")
                cachedAds[placement] = null
                AdDisplayCoordinator.onAdDismissed()
                preload(placement) // Cache next one
                onAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                cachedAds[placement] = null
                AdDisplayCoordinator.onAdDismissed()
                preload(placement)
                onAdClosed()
            }
        }

        cachedAd.show(activity)
    }
}
