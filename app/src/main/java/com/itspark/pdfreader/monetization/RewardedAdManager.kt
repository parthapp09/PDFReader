package com.itspark.pdfreader.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.itspark.pdfreader.consent.ConsentManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages loading and presentation of Rewarded Video Ads for unlocking VIP features or AI tools.
 */
class RewardedAdManager(private val context: Context) {

    companion object {
        private const val TAG = "RewardedAdManager"
    }

    private val configRepo = AdConfigRepository(context)
    private var cachedRewardedAd: RewardedAd? = null
    private var isLoading: Boolean = false

    fun preload(placement: AdPlacement = AdPlacement.REWARDED_VIP_FEATURE, onLoaded: (() -> Unit)? = null) {
        if (isLoading || cachedRewardedAd != null) {
            onLoaded?.invoke()
            return
        }

        if (!ConsentManager.canRequestAds(context) || !configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Preload skipped: Consent or placement disabled")
            return
        }

        isLoading = true
        val adUnitId = configRepo.getAdUnitId(placement)
        val request = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adUnitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    cachedRewardedAd = ad
                    Log.i(TAG, "Rewarded ad loaded successfully")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoading = false
                    cachedRewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    fun showIfAvailable(
        activity: Activity,
        placement: AdPlacement = AdPlacement.REWARDED_VIP_FEATURE,
        onUserEarnedReward: (RewardItem) -> Unit,
        onAdClosed: () -> Unit
    ) {
        if (!ConsentManager.canRequestAds(activity)) {
            Log.d(TAG, "Cannot show rewarded ad: Consent not granted")
            onAdClosed()
            return
        }

        if (!configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Rewarded placement [${placement.placementKey}] disabled remotely")
            onAdClosed()
            return
        }

        if (!AdDisplayCoordinator.canShowFullScreenAd()) {
            Log.d(TAG, "Cannot show rewarded ad: Full-screen mutex active")
            onAdClosed()
            return
        }

        val ad = cachedRewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded ad not cached. Preloading for next time...")
            preload(placement)
            onAdClosed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdDisplayCoordinator.onAdStartedShowing(AdFormatType.REWARDED)
                Log.i(TAG, "Rewarded ad showed full screen")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "Rewarded ad dismissed")
                cachedRewardedAd = null
                AdDisplayCoordinator.onAdDismissed()
                preload(placement)
                onAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                cachedRewardedAd = null
                AdDisplayCoordinator.onAdDismissed()
                preload(placement)
                onAdClosed()
            }
        }

        ad.show(activity) { rewardItem ->
            Log.i(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onUserEarnedReward(rewardItem)
        }
    }
}
