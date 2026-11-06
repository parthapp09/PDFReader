package com.itspark.pdfreader.monetization

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.itspark.pdfreader.config.RemoteConfigManager
import org.json.JSONObject

/**
 * Repository for managing Ad Unit IDs and placement configurations dynamically.
 * Supports runtime replacement of Ad Unit IDs, per-placement enable/disable toggles,
 * and timeouts from server-side JSON configs without requiring app releases.
 */
class AdConfigRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AdConfigRepository"
        private const val PREFS_NAME = "ad_remote_config_prefs"

        private const val KEY_PREFIX_ENABLED = "placement_enabled_"
        private const val KEY_PREFIX_AD_UNIT = "placement_ad_unit_"
        private const val KEY_PREFIX_FORMAT = "placement_format_"
        private const val KEY_PREFIX_FIRST_LAUNCH_FORMAT = "first_launch_format_"
        private const val KEY_GLOBAL_TIMEOUT = "global_ad_timeout_ms"
        private const val KEY_GLOBAL_INTERVAL = "global_interstitial_interval_sec"

        const val DEFAULT_TIMEOUT_MS = 6000L
    }

    /**
     * Checks if a placement is enabled remotely. Honors master kill-switch and individual flags.
     */
    fun isPlacementEnabled(placement: AdPlacement): Boolean {
        if (!RemoteConfigManager.isAdsGlobalEnabled()) {
            return false
        }

        // Check placement-specific remote flags
        val flagEnabled = when (placement) {
            AdPlacement.SPLASH_APP_OPEN -> RemoteConfigManager.getAdStartupFormat() != "NONE"
            AdPlacement.RESUME_APP_OPEN -> RemoteConfigManager.isResumeAppOpenEnabled()
            AdPlacement.HOME_BOTTOM_BANNER -> RemoteConfigManager.isHomeBannerEnabled()
            AdPlacement.VIEWER_BOTTOM_BANNER -> RemoteConfigManager.isViewerBannerEnabled()
            AdPlacement.NATIVE_EXIT_DIALOG -> RemoteConfigManager.isExitDialogNativeEnabled()
            AdPlacement.NATIVE_AI_FEATURE -> RemoteConfigManager.isAiDialogNativeEnabled()
            AdPlacement.REWARDED_VIP_FEATURE -> RemoteConfigManager.isVipRewardedEnabled()
            else -> true
        }

        if (!flagEnabled) return false

        return prefs.getBoolean(KEY_PREFIX_ENABLED + placement.placementKey, true)
    }

    /**
     * Dynamically determines the active ad format for a placement.
     * Supports different ad formats for first launch versus subsequent launches,
     * as well as runtime server-side format overrides.
     */
    fun getPlacementFormat(placement: AdPlacement, isFirstLaunch: Boolean = false): AdFormatType {
        if (placement == AdPlacement.SPLASH_APP_OPEN) {
            val startupFormat = RemoteConfigManager.getAdStartupFormat()
            if (startupFormat.equals("INTERSTITIAL", ignoreCase = true)) {
                return AdFormatType.INTERSTITIAL
            }
        }
        if (isFirstLaunch) {
            val firstLaunchFormatStr = prefs.getString(KEY_PREFIX_FIRST_LAUNCH_FORMAT + placement.placementKey, null)
            if (firstLaunchFormatStr != null) {
                try {
                    return AdFormatType.valueOf(firstLaunchFormatStr.uppercase())
                } catch (_: Exception) {}
            }
        }

        val formatStr = prefs.getString(KEY_PREFIX_FORMAT + placement.placementKey, null)
        if (formatStr != null) {
            try {
                return AdFormatType.valueOf(formatStr.uppercase())
            } catch (_: Exception) {}
        }

        return placement.format
    }

    /**
     * Retrieves the active Ad Unit ID for a placement (remote override if present, else default).
     */
    fun getAdUnitId(placement: AdPlacement): String {
        return prefs.getString(KEY_PREFIX_AD_UNIT + placement.placementKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: placement.defaultAdUnitId
    }

    /**
     * Sets an ad loading timeout in milliseconds. Checks RemoteConfig first.
     */
    fun getAdLoadingTimeoutMs(): Long {
        val remoteTimeout = RemoteConfigManager.getAdLoadingTimeoutMs()
        return if (remoteTimeout > 0) remoteTimeout else prefs.getLong(KEY_GLOBAL_TIMEOUT, DEFAULT_TIMEOUT_MS)
    }

    /**
     * Updates placement configurations dynamically from a server-side JSON payload.
     * Example payload:
     * {
     *   "global_timeout_ms": 7000,
     *   "placements": {
     *     "splash_app_open": { "enabled": true, "ad_unit_id": "ca-app-pub-..." },
     *     "home_bottom_banner": { "enabled": true }
     *   }
     * }
     */
    fun applyServerConfig(jsonConfig: String): Boolean {
        return try {
            val json = JSONObject(jsonConfig)
            val editor = prefs.edit()

            if (json.has("global_timeout_ms")) {
                editor.putLong(KEY_GLOBAL_TIMEOUT, json.getLong("global_timeout_ms"))
            }

            if (json.has("placements")) {
                val placementsObj = json.getJSONObject("placements")
                val keys = placementsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val placementJson = placementsObj.getJSONObject(key)

                    if (placementJson.has("enabled")) {
                        editor.putBoolean(KEY_PREFIX_ENABLED + key, placementJson.getBoolean("enabled"))
                    }
                    if (placementJson.has("ad_unit_id")) {
                        editor.putString(KEY_PREFIX_AD_UNIT + key, placementJson.getString("ad_unit_id"))
                    }
                    if (placementJson.has("format")) {
                        editor.putString(KEY_PREFIX_FORMAT + key, placementJson.getString("format"))
                    }
                    if (placementJson.has("first_launch_format")) {
                        editor.putString(KEY_PREFIX_FIRST_LAUNCH_FORMAT + key, placementJson.getString("first_launch_format"))
                    }
                }
            }

            editor.apply()
            Log.i(TAG, "Dynamic ad configuration applied successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply server ad config: ${e.message}", e)
            false
        }
    }

    /**
     * Programmatically override a specific placement's unit ID (e.g. for testing or hotfix).
     */
    fun setAdUnitOverride(placement: AdPlacement, adUnitId: String?) {
        prefs.edit().putString(KEY_PREFIX_AD_UNIT + placement.placementKey, adUnitId).apply()
    }

    /**
     * Programmatically toggle an individual placement on or off.
     */
    fun setPlacementEnabled(placement: AdPlacement, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFIX_ENABLED + placement.placementKey, enabled).apply()
    }
}
