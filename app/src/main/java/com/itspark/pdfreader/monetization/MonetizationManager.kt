package com.itspark.pdfreader.monetization

import android.content.Context
import android.util.Log
import com.itspark.pdfreader.analytics.AnalyticsTracker
import com.itspark.pdfreader.consent.ConsentManager
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.domain.model.AttributionType
import com.itspark.pdfreader.domain.model.MonetizationConfig

object MonetizationManager {

    private const val TAG = "MonetizationManager"

    private var activeConfig: MonetizationConfig = MonetizationConfig.forSegment(AttributionType.ORGANIC)
    private var userSegment: AttributionType = AttributionType.ORGANIC

    private var lastInterstitialTimeMs: Long = 0L
    private var sessionInterstitialCount: Int = 0

    fun initialize(context: Context) {
        val prefs = PreferencesRepository(context)
        userSegment = prefs.getAttributionType()
        activeConfig = MonetizationConfig.forSegment(userSegment)

        Log.i(
            TAG,
            "Initialized for segment [$userSegment] -> Cooldown: ${activeConfig.interstitialCooldownSeconds}s | " +
            "Max Ads: ${activeConfig.maxInterstitialsPerSession} | Banners: ${activeConfig.showBannerAds} | AppOpen: ${activeConfig.showAppOpenAdOnStart}"
        )
    }

    fun getSegment(): AttributionType = userSegment

    fun getConfig(): MonetizationConfig = activeConfig

    /**
     * Determines whether an interstitial ad is permitted under the active user cohort and consent rules.
     */
    fun canShowInterstitial(context: Context, placementTag: String): Boolean {
        // 1. Consent Policy Verification
        if (!ConsentManager.canRequestAds(context)) {
            AnalyticsTracker.logMonetizationDecision(
                context,
                adType = "interstitial_$placementTag",
                allowed = false,
                reason = "consent_not_granted"
            )
            return false
        }

        val now = System.currentTimeMillis()
        val cooldownMs = activeConfig.interstitialCooldownSeconds * 1000L

        if (sessionInterstitialCount >= activeConfig.maxInterstitialsPerSession) {
            AnalyticsTracker.logMonetizationDecision(
                context,
                adType = "interstitial_$placementTag",
                allowed = false,
                reason = "session_cap_reached_${activeConfig.maxInterstitialsPerSession}"
            )
            return false
        }

        val elapsedMs = now - lastInterstitialTimeMs
        if (lastInterstitialTimeMs > 0 && elapsedMs < cooldownMs) {
            val remainingSec = (cooldownMs - elapsedMs) / 1000
            AnalyticsTracker.logMonetizationDecision(
                context,
                adType = "interstitial_$placementTag",
                allowed = false,
                reason = "cooldown_active_${remainingSec}s_remaining"
            )
            return false
        }

        AnalyticsTracker.logMonetizationDecision(
            context,
            adType = "interstitial_$placementTag",
            allowed = true,
            reason = "cohort_rules_passed"
        )
        return true
    }

    /**
     * Call when an interstitial ad has been successfully presented to the user.
     */
    fun recordInterstitialShown(context: Context, placementTag: String) {
        lastInterstitialTimeMs = System.currentTimeMillis()
        sessionInterstitialCount++
        Log.d(TAG, "Recorded interstitial impression for [$placementTag]. Total in session: $sessionInterstitialCount")
    }

    /**
     * Decides banner ad visibility based on cohort configuration and consent status.
     */
    fun shouldShowBanner(context: Context? = null): Boolean {
        if (context != null && !ConsentManager.canRequestAds(context)) {
            return false
        }
        return activeConfig.showBannerAds
    }

    /**
     * Decides whether cold-start App Open Ads should be displayed.
     */
    fun shouldShowAppOpenAd(context: Context? = null): Boolean {
        if (context != null && !ConsentManager.canRequestAds(context)) {
            return false
        }
        return activeConfig.showAppOpenAdOnStart
    }
}
