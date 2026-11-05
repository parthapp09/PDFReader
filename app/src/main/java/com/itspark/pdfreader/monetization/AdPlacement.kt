package com.itspark.pdfreader.monetization

/**
 * Supported Ad Formats across the application.
 */
enum class AdFormatType {
    APP_OPEN,
    INTERSTITIAL,
    COLLAPSIBLE_BANNER,
    STANDARD_BANNER,
    NATIVE,
    REWARDED
}

/**
 * Central registry of all ad placements in the application.
 * Default Ad Unit IDs use Google's official AdMob test IDs for guaranteed test delivery.
 * These can be dynamically overridden server-side or via AdConfigRepository without app updates.
 */
enum class AdPlacement(
    val placementKey: String,
    val defaultAdUnitId: String,
    val format: AdFormatType
) {
    // 1. App Open
    SPLASH_APP_OPEN(
        placementKey = "splash_app_open",
        defaultAdUnitId = "ca-app-pub-3940256099942544/9257395921",
        format = AdFormatType.APP_OPEN
    ),
    RESUME_APP_OPEN(
        placementKey = "resume_app_open",
        defaultAdUnitId = "ca-app-pub-3940256099942544/9257395921",
        format = AdFormatType.APP_OPEN
    ),

    // 2. Banners
    HOME_BOTTOM_BANNER(
        placementKey = "home_bottom_banner",
        defaultAdUnitId = "ca-app-pub-3940256099942544/9214589741", // Collapsible banner
        format = AdFormatType.COLLAPSIBLE_BANNER
    ),
    VIEWER_BOTTOM_BANNER(
        placementKey = "viewer_bottom_banner",
        defaultAdUnitId = "ca-app-pub-3940256099942544/6300978111", // Standard Adaptive banner
        format = AdFormatType.STANDARD_BANNER
    ),

    // 3. Interstitials
    INTERSTITIAL_OPEN_DOC(
        placementKey = "interstitial_open_doc",
        defaultAdUnitId = "ca-app-pub-3940256099942544/1033173712",
        format = AdFormatType.INTERSTITIAL
    ),
    INTERSTITIAL_CLOSE_DOC(
        placementKey = "interstitial_close_doc",
        defaultAdUnitId = "ca-app-pub-3940256099942544/1033173712",
        format = AdFormatType.INTERSTITIAL
    ),
    INTERSTITIAL_LANGUAGE_DONE(
        placementKey = "interstitial_language_done",
        defaultAdUnitId = "ca-app-pub-3940256099942544/1033173712",
        format = AdFormatType.INTERSTITIAL
    ),
    INTERSTITIAL_POST_CONVERT(
        placementKey = "interstitial_post_convert",
        defaultAdUnitId = "ca-app-pub-3940256099942544/1033173712",
        format = AdFormatType.INTERSTITIAL
    ),

    // 4. Native Ads
    NATIVE_LANGUAGE_ONBOARDING(
        placementKey = "native_language_onboarding",
        defaultAdUnitId = "ca-app-pub-3940256099942544/2247696110",
        format = AdFormatType.NATIVE
    ),
    NATIVE_HOME_FEED(
        placementKey = "native_home_feed",
        defaultAdUnitId = "ca-app-pub-3940256099942544/2247696110",
        format = AdFormatType.NATIVE
    ),
    NATIVE_EXIT_DIALOG(
        placementKey = "native_exit_dialog",
        defaultAdUnitId = "ca-app-pub-3940256099942544/2247696110",
        format = AdFormatType.NATIVE
    ),
    NATIVE_CONVERSION_RESULT(
        placementKey = "native_conversion_result",
        defaultAdUnitId = "ca-app-pub-3940256099942544/2247696110",
        format = AdFormatType.NATIVE
    ),
    NATIVE_AI_FEATURE(
        placementKey = "native_ai_feature",
        defaultAdUnitId = "ca-app-pub-3940256099942544/2247696110",
        format = AdFormatType.NATIVE
    ),
    // 5. Rewarded Ads
    REWARDED_VIP_FEATURE(
        placementKey = "rewarded_vip_feature",
        defaultAdUnitId = "ca-app-pub-3940256099942544/5224354917",
        format = AdFormatType.REWARDED
    );

    companion object {
        fun fromKey(key: String): AdPlacement? {
            return entries.find { it.placementKey.equals(key, ignoreCase = true) }
        }
    }
}
