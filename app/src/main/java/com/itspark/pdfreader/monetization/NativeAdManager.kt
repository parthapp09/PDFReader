package com.itspark.pdfreader.monetization

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.itspark.pdfreader.R
import com.itspark.pdfreader.consent.ConsentManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Manages loading and binding of AdMob Native Ads across the application.
 * Follows the official Google AdMob Native Advanced specifications and GitHub sample code
 * (googleads-mobile-android-examples / NativeAdvancedExample).
 */
class NativeAdManager(private val context: Context) {

    companion object {
        private const val TAG = "NativeAdManager"
    }

    private val configRepo = AdConfigRepository(context)

    // Cached native ads
    private val cachedNativeAds = mutableMapOf<AdPlacement, NativeAd?>()
    private val loadingPlacements = mutableSetOf<AdPlacement>()

    /**
     * Preloads a Native Ad into memory.
     */
    fun preload(placement: AdPlacement, onLoaded: ((NativeAd) -> Unit)? = null) {
        if (loadingPlacements.contains(placement) || cachedNativeAds[placement] != null) {
            cachedNativeAds[placement]?.let { onLoaded?.invoke(it) }
            return
        }

        if (!ConsentManager.canRequestAds(context) || !configRepo.isPlacementEnabled(placement)) {
            Log.d(TAG, "Preload skipped for [$placement]: Consent or placement disabled")
            return
        }

        loadingPlacements.add(placement)
        val adUnitId = configRepo.getAdUnitId(placement)

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                loadingPlacements.remove(placement)
                cachedNativeAds[placement]?.destroy()
                cachedNativeAds[placement] = nativeAd
                Log.i(TAG, "Native ad preloaded for [${placement.placementKey}]")
                onLoaded?.invoke(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    loadingPlacements.remove(placement)
                    Log.w(TAG, "Native ad failed [${placement.placementKey}]: ${loadAdError.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Loads and immediately populates a Native Ad into a ViewGroup container.
     */
    fun loadIntoContainer(
        container: ViewGroup,
        placement: AdPlacement,
        layoutResId: Int = R.layout.layout_native_ad_medium,
        onLoaded: (() -> Unit)? = null
    ) {
        if (!ConsentManager.canRequestAds(context) || !configRepo.isPlacementEnabled(placement)) {
            container.visibility = View.GONE
            return
        }

        // Use cached ad if available
        val cachedAd = cachedNativeAds[placement]
        if (cachedAd != null) {
            populateView(container, cachedAd, layoutResId)
            onLoaded?.invoke()
            return
        }

        val adUnitId = configRepo.getAdUnitId(placement)
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                cachedNativeAds[placement]?.destroy()
                cachedNativeAds[placement] = nativeAd
                populateView(container, nativeAd, layoutResId)
                onLoaded?.invoke()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Native ad failed [${placement.placementKey}]: ${loadAdError.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Binds a NativeAd to a layout inflating inside the target container.
     * Follows the exact field-mapping and null-safety logic from Google's official GitHub sample:
     * googleads-mobile-android-examples/kotlin/admob/NativeAdvancedExample
     */
    fun populateView(container: ViewGroup, nativeAd: NativeAd, layoutResId: Int) {
        val inflater = LayoutInflater.from(container.context)
        val adView = inflater.inflate(layoutResId, container, false) as NativeAdView

        // 1. Headline (Mandatory asset in AdMob)
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        (adView.headlineView as? TextView)?.text = nativeAd.headline

        // 2. Body
        adView.bodyView = adView.findViewById(R.id.ad_body)
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.INVISIBLE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as? TextView)?.text = nativeAd.body
        }

        // 3. Call To Action Button
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as? Button)?.text = nativeAd.callToAction
        }

        // 4. App Icon
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        // 5. Star Rating
        adView.starRatingView = adView.findViewById(R.id.ad_stars)
        if (nativeAd.starRating == null || nativeAd.starRating!! <= 0) {
            adView.starRatingView?.visibility = View.GONE
        } else {
            (adView.starRatingView as? RatingBar)?.rating = nativeAd.starRating!!.toFloat()
            adView.starRatingView?.visibility = View.VISIBLE
        }

        // 6. Price
        adView.priceView = adView.findViewById(R.id.ad_price)
        if (nativeAd.price == null) {
            adView.priceView?.visibility = View.GONE
        } else {
            adView.priceView?.visibility = View.VISIBLE
            (adView.priceView as? TextView)?.text = nativeAd.price
        }

        // 7. Store
        adView.storeView = adView.findViewById(R.id.ad_store)
        if (nativeAd.store == null) {
            adView.storeView?.visibility = View.GONE
        } else {
            adView.storeView?.visibility = View.VISIBLE
            (adView.storeView as? TextView)?.text = nativeAd.store
        }

        // 8. Advertiser
        adView.advertiserView = adView.findViewById(R.id.ad_advertiser)
        if (nativeAd.advertiser == null) {
            adView.advertiserView?.visibility = View.GONE
        } else {
            adView.advertiserView?.visibility = View.VISIBLE
            (adView.advertiserView as? TextView)?.text = nativeAd.advertiser
        }

        // 9. Media View (Image / Video)
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        if (mediaView != null && nativeAd.mediaContent != null) {
            mediaView.mediaContent = nativeAd.mediaContent
            adView.mediaView = mediaView
        }

        // Mandatory: Register the native ad object with the NativeAdView
        adView.setNativeAd(nativeAd)

        container.removeAllViews()
        container.addView(adView)
        container.visibility = View.VISIBLE
    }

    /**
     * Destroys all cached native ads to prevent memory leaks.
     */
    fun destroy() {
        cachedNativeAds.values.forEach { it?.destroy() }
        cachedNativeAds.clear()
    }
}
