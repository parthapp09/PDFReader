package com.itspark.pdfreader.domain.model

/**
 * Cohort-based monetization configurations driven by attribution segment.
 *
 * Paid users (Google Ads / Paid Media):
 *   - Longer cooldown between interstitials (e.g., 90s) to protect Day-1 retention & user experience.
 *   - Strict session frequency cap (e.g., max 5 interstitials).
 *   - App Open ads suppressed on cold start to avoid early churn.
 *
 * Organic users (Play Store Search / Browse):
 *   - Standard monetization cooldown (e.g., 45s).
 *   - Standard session cap (e.g., max 10 interstitials).
 *   - App Open ads enabled.
 */
data class MonetizationConfig(
    val interstitialCooldownSeconds: Int,
    val maxInterstitialsPerSession: Int,
    val showBannerAds: Boolean,
    val showAppOpenAdOnStart: Boolean
) {
    companion object {
        fun forSegment(type: AttributionType): MonetizationConfig = when (type) {
            AttributionType.PAID_GOOGLE, AttributionType.PAID_OTHER -> MonetizationConfig(
                interstitialCooldownSeconds = 90,
                maxInterstitialsPerSession = 5,
                showBannerAds = true,
                showAppOpenAdOnStart = false
            )
            AttributionType.ORGANIC, AttributionType.UNKNOWN -> MonetizationConfig(
                interstitialCooldownSeconds = 45,
                maxInterstitialsPerSession = 10,
                showBannerAds = true,
                showAppOpenAdOnStart = true
            )
        }
    }
}
