package com.itspark.pdfreader.monetization

import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import com.itspark.pdfreader.consent.ConsentManager
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Manages both Collapsible Banners (Home screen) and Adaptive Standard Banners (PDF Viewer).
 */
class BannerAdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "BannerAdManager"
    }

    private val configRepo = AdConfigRepository(activity)
    private var activeAdView: AdView? = null

    /**
     * Loads a Collapsible Banner ad into the specified container.
     */
    fun loadCollapsibleBanner(
        container: ViewGroup,
        placement: AdPlacement = AdPlacement.HOME_BOTTOM_BANNER,
        onLoaded: (() -> Unit)? = null
    ) {
        if (!ConsentManager.canRequestAds(activity) ||
            !configRepo.isPlacementEnabled(placement) ||
            !MonetizationManager.shouldShowBanner(activity)
        ) {
            container.visibility = ViewGroup.GONE
            return
        }

        val adView = AdView(activity).apply {
            adUnitId = configRepo.getAdUnitId(placement)
            setAdSize(getAdaptiveAdSize())
        }
        activeAdView = adView

        container.removeAllViews()
        container.addView(adView)
        container.visibility = ViewGroup.VISIBLE

        // Google AdMob Collapsible Banner Configuration
        val extras = Bundle().apply {
            putString("collapsible", "bottom")
        }
        val request = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.i(TAG, "Collapsible banner loaded for [${placement.placementKey}]")
                onLoaded?.invoke()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Collapsible banner failed [${placement.placementKey}]: ${loadAdError.message}")
            }
        }

        adView.loadAd(request)
    }

    /**
     * Loads a Standard Adaptive Banner ad (non-collapsible, steady for reader view).
     */
    fun loadStandardBanner(
        container: ViewGroup,
        placement: AdPlacement = AdPlacement.VIEWER_BOTTOM_BANNER,
        onLoaded: (() -> Unit)? = null
    ) {
        if (!ConsentManager.canRequestAds(activity) ||
            !configRepo.isPlacementEnabled(placement) ||
            !MonetizationManager.shouldShowBanner(activity)
        ) {
            container.visibility = ViewGroup.GONE
            return
        }

        val adView = AdView(activity).apply {
            adUnitId = configRepo.getAdUnitId(placement)
            setAdSize(getAdaptiveAdSize())
        }
        activeAdView = adView

        container.removeAllViews()
        container.addView(adView)
        container.visibility = ViewGroup.VISIBLE

        val request = AdRequest.Builder().build()

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.i(TAG, "Standard banner loaded for [${placement.placementKey}]")
                onLoaded?.invoke()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Standard banner failed [${placement.placementKey}]: ${loadAdError.message}")
            }
        }

        adView.loadAd(request)
    }

    private fun getAdaptiveAdSize(): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)

        val density = outMetrics.density
        var adWidthPixels = outMetrics.widthPixels.toFloat()
        if (adWidthPixels == 0f) {
            adWidthPixels = 320f
        }
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    fun pause() {
        activeAdView?.pause()
    }

    fun resume() {
        activeAdView?.resume()
    }

    fun destroy() {
        activeAdView?.destroy()
        activeAdView = null
    }
}
