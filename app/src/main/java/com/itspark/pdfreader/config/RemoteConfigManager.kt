package com.itspark.pdfreader.config

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.itspark.pdfreader.monetization.AdConfigRepository
import com.itspark.pdfreader.monetization.MonetizationManager
import com.itspark.pdfreader.notification.NotificationEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Centralized manager for Firebase Remote Config and feature flag management.
 * Controls 40+ flags including individual ad placements, server-side JSON Ad Unit IDs,
 * advertising & notification frequency, heads-up and full-screen controls,
 * and screen variant behaviors without requiring app updates.
 */
object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"

    // ─── 40+ Parameter Keys ──────────────────────────────────────────────────

    // Group 1: General Advertising Controls (1-5)
    const val KEY_ADS_GLOBAL_ENABLED = "ads_global_enabled"
    const val KEY_AD_SERVER_CONFIG_JSON = "ad_server_config_json"
    const val KEY_AD_LOADING_TIMEOUT_MS = "ad_loading_timeout_ms"
    const val KEY_AD_INTERSTITIAL_MIN_INTERVAL_SEC = "ad_interstitial_min_interval_sec"
    const val KEY_AD_INTERSTITIAL_CLICK_FREQUENCY = "ad_interstitial_click_frequency"

    // Group 2: Screen-Specific Ad Placements (6-12)
    const val KEY_AD_STARTUP_FORMAT = "ad_startup_format"
    const val KEY_AD_RESUME_APP_OPEN_ENABLED = "ad_resume_app_open_enabled"
    const val KEY_AD_HOME_BANNER_ENABLED = "ad_home_banner_enabled"
    const val KEY_AD_VIEWER_BANNER_ENABLED = "ad_viewer_banner_enabled"
    const val KEY_AD_EXIT_DIALOG_NATIVE_ENABLED = "ad_exit_dialog_native_enabled"
    const val KEY_AD_AI_DIALOG_NATIVE_ENABLED = "ad_ai_dialog_native_enabled"
    const val KEY_AD_VIP_REWARDED_ENABLED = "ad_vip_rewarded_enabled"

    // Group 3: Out-of-App Notification Master & Timing (13-18)
    const val KEY_NOTIF_OUT_OF_APP_ENABLED = "notif_out_of_app_enabled"
    const val KEY_NOTIF_MIN_INTERVAL_HOURS = "notif_min_interval_hours"
    const val KEY_NOTIF_EXIT_DELAY_MS = "notif_exit_delay_ms"
    const val KEY_NOTIF_COUNTDOWN_SECONDS = "notif_countdown_seconds"
    const val KEY_NOTIF_INACTIVITY_HOURS = "notif_inactivity_hours"
    const val KEY_NOTIF_PERIODIC_HOURS = "notif_periodic_hours"

    // Group 4: Notification Style, Channel & Accumulation (19-26)
    const val KEY_NOTIF_HEADS_UP_ENABLED = "notif_heads_up_enabled"
    const val KEY_NOTIF_FULL_SCREEN_ENABLED = "notif_full_screen_enabled"
    const val KEY_NOTIF_DUPLICATE_ACCUMULATE = "notif_duplicate_accumulate"
    const val KEY_NOTIF_STYLE_VARIANT = "notif_style_variant"
    const val KEY_NOTIF_EXIT_ALERT_ENABLED = "notif_exit_alert_enabled"
    const val KEY_NOTIF_SCREENSHOT_ENABLED = "notif_screenshot_enabled"
    const val KEY_NOTIF_DOWNLOADS_ENABLED = "notif_downloads_enabled"
    const val KEY_NOTIF_UNLOCK_ENABLED = "notif_unlock_enabled"

    // Group 5: Background WorkManager & Scheduling (27-28)
    const val KEY_NOTIF_PERIODIC_WORKER_ENABLED = "notif_periodic_worker_enabled"
    const val KEY_NOTIF_UNLOCK_WORKER_ENABLED = "notif_unlock_worker_enabled"

    // Group 6: Self-Launch, Lock Screen & Screen Variants (29-34)
    const val KEY_SELF_LAUNCH_ON_LOCK_ENABLED = "self_launch_on_lock_enabled"
    const val KEY_SCREEN_SPLASH_DELAY_MS = "screen_splash_delay_ms"
    const val KEY_SCREEN_EXIT_DIALOG_VARIANT = "screen_exit_dialog_variant"
    const val KEY_SCREEN_HOME_VARIANT = "screen_home_variant"
    const val KEY_SCREEN_TOOLS_VIP_ACTION = "screen_tools_vip_action"
    const val KEY_READER_RENDER_QUALITY = "reader_render_quality"

    // Group 7: Feature Flags (35-38)
    const val KEY_FEATURE_AI_TOOLS_ENABLED = "feature_ai_tools_enabled"
    const val KEY_FEATURE_IMAGE_TO_PDF_LIMIT = "feature_image_to_pdf_limit"
    const val KEY_FEATURE_NIGHT_MODE_ENABLED = "feature_night_mode_enabled"
    const val KEY_FEATURE_RECENT_FILES_TAB = "feature_recent_files_tab"

    // Group 8: Cohorts, Analytics & Metadata (39-42)
    const val KEY_COHORT_AD_AGGRESSIVENESS = "cohort_ad_aggressiveness"
    const val KEY_COHORT_OVERRIDE_TOPIC = "cohort_override_topic"
    const val KEY_ANALYTICS_SAMPLING_RATE = "analytics_sampling_rate"
    const val KEY_CONFIG_VERSION = "config_version"

    private var remoteConfig: FirebaseRemoteConfig? = null

    /**
     * Initializes Firebase Remote Config with fallback defaults and real-time listeners.
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext

        try {
            val app = if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(appContext)
            }

            if (app == null) {
                Log.w(TAG, "FirebaseApp could not be initialized. Skipping RemoteConfig.")
                return
            }

            val config = FirebaseRemoteConfig.getInstance()
            remoteConfig = config

            // Development: 0s cache for immediate live changes. Production: 3600s (1h)
            val isDebuggable = (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val minFetchInterval = if (isDebuggable) 0L else 3600L

            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(minFetchInterval)
                .build()
            config.setConfigSettingsAsync(settings)

            // Register in-memory default values
            config.setDefaultsAsync(buildDefaultMap())

            // Apply cached / default values immediately on startup (zero network waiting)
            applyConfigurationToApp(appContext)

            // Fetch and activate latest configuration from server
            config.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "Remote Config fetched and activated successfully (debuggable=$isDebuggable)")
                    applyConfigurationToApp(appContext)
                } else {
                    Log.w(TAG, "Remote Config fetch failed. Retaining cached/default values.")
                }
            }

            // Real-time remote configuration update listener (Firebase Remote Config Realtime API)
            config.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    Log.d(TAG, "Realtime configuration update received: ${configUpdate.updatedKeys}")
                    config.activate().addOnCompleteListener {
                        applyConfigurationToApp(appContext)
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.w(TAG, "Remote Config realtime update error: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseRemoteConfig: ${e.message}", e)
        }
    }

    /**
     * Synchronizes updated values into downstream subsystems.
     */
    fun applyConfigurationToApp(context: Context) {
        try {
            // A. Update notification engine cooldown
            val cooldownHours = getNotifMinIntervalHours()
            val cooldownMs = (cooldownHours * 3600 * 1000).toLong()
            NotificationEngine.setRemoteMinIntervalMs(context, cooldownMs)

            // B. Apply server-side JSON ad configuration if present
            val serverJson = getAdServerConfigJson()
            if (serverJson.isNotBlank()) {
                AdConfigRepository(context).applyServerConfig(serverJson)
            }

            Log.i(TAG, "Remote config successfully synced with notification and ad repositories")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying remote config: ${e.message}", e)
        }
    }

    // ─── Getters (Safe Fallback to Defaults if RemoteConfig Not Available) ───

    fun isAdsGlobalEnabled(): Boolean = remoteConfig?.getBoolean(KEY_ADS_GLOBAL_ENABLED) ?: true
    fun getAdServerConfigJson(): String = remoteConfig?.getString(KEY_AD_SERVER_CONFIG_JSON) ?: ""
    fun getAdLoadingTimeoutMs(): Long = remoteConfig?.getLong(KEY_AD_LOADING_TIMEOUT_MS) ?: 6000L
    fun getAdInterstitialMinIntervalSec(): Long = remoteConfig?.getLong(KEY_AD_INTERSTITIAL_MIN_INTERVAL_SEC) ?: 45L
    fun getAdInterstitialClickFrequency(): Int = remoteConfig?.getLong(KEY_AD_INTERSTITIAL_CLICK_FREQUENCY)?.toInt() ?: 3

    fun getAdStartupFormat(): String = remoteConfig?.getString(KEY_AD_STARTUP_FORMAT) ?: "APP_OPEN"
    fun isResumeAppOpenEnabled(): Boolean = remoteConfig?.getBoolean(KEY_AD_RESUME_APP_OPEN_ENABLED) ?: true
    fun isHomeBannerEnabled(): Boolean = remoteConfig?.getBoolean(KEY_AD_HOME_BANNER_ENABLED) ?: true
    fun isViewerBannerEnabled(): Boolean = remoteConfig?.getBoolean(KEY_AD_VIEWER_BANNER_ENABLED) ?: true
    fun isExitDialogNativeEnabled(): Boolean = remoteConfig?.getBoolean(KEY_AD_EXIT_DIALOG_NATIVE_ENABLED) ?: true
    fun isAiDialogNativeEnabled(): Boolean = remoteConfig?.getBoolean(KEY_AD_AI_DIALOG_NATIVE_ENABLED) ?: true
    fun isVipRewardedEnabled(): Boolean = remoteConfig?.getBoolean(KEY_AD_VIP_REWARDED_ENABLED) ?: true

    fun isOutOfAppNotifEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_OUT_OF_APP_ENABLED) ?: true
    fun getNotifMinIntervalHours(): Double = remoteConfig?.getDouble(KEY_NOTIF_MIN_INTERVAL_HOURS) ?: 2.0
    fun getExitDelayMs(): Long = remoteConfig?.getLong(KEY_NOTIF_EXIT_DELAY_MS) ?: 1200L
    fun getCountdownSeconds(): Int = remoteConfig?.getLong(KEY_NOTIF_COUNTDOWN_SECONDS)?.toInt() ?: 15
    fun getInactivityHours(): Long = remoteConfig?.getLong(KEY_NOTIF_INACTIVITY_HOURS) ?: 18L
    fun getPeriodicHours(): Long = remoteConfig?.getLong(KEY_NOTIF_PERIODIC_HOURS) ?: 24L

    fun isHeadsUpEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_HEADS_UP_ENABLED) ?: true
    fun isFullScreenEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_FULL_SCREEN_ENABLED) ?: true
    fun isDuplicateAccumulateEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_DUPLICATE_ACCUMULATE) ?: true
    fun getNotifStyleVariant(): String = remoteConfig?.getString(KEY_NOTIF_STYLE_VARIANT) ?: "RICH_BIG_TEXT"
    fun isExitAlertEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_EXIT_ALERT_ENABLED) ?: true
    fun isScreenshotEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_SCREENSHOT_ENABLED) ?: true
    fun isDownloadsEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_DOWNLOADS_ENABLED) ?: true
    fun isUnlockEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_UNLOCK_ENABLED) ?: true

    fun isPeriodicWorkerEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_PERIODIC_WORKER_ENABLED) ?: true
    fun isUnlockWorkerEnabled(): Boolean = remoteConfig?.getBoolean(KEY_NOTIF_UNLOCK_WORKER_ENABLED) ?: true

    fun isSelfLaunchOnLockEnabled(): Boolean = remoteConfig?.getBoolean(KEY_SELF_LAUNCH_ON_LOCK_ENABLED) ?: true
    fun getSplashDelayMs(): Long = remoteConfig?.getLong(KEY_SCREEN_SPLASH_DELAY_MS) ?: 1800L
    fun getExitDialogVariant(): String = remoteConfig?.getString(KEY_SCREEN_EXIT_DIALOG_VARIANT) ?: "NATIVE_AD"
    fun getHomeVariant(): String = remoteConfig?.getString(KEY_SCREEN_HOME_VARIANT) ?: "DEFAULT"
    fun getToolsVipAction(): String = remoteConfig?.getString(KEY_SCREEN_TOOLS_VIP_ACTION) ?: "REWARDED"
    fun getReaderRenderQuality(): String = remoteConfig?.getString(KEY_READER_RENDER_QUALITY) ?: "STANDARD"

    fun isAiToolsEnabled(): Boolean = remoteConfig?.getBoolean(KEY_FEATURE_AI_TOOLS_ENABLED) ?: true
    fun getImageToPdfBatchLimit(): Int = remoteConfig?.getLong(KEY_FEATURE_IMAGE_TO_PDF_LIMIT)?.toInt() ?: 50
    fun isNightModeEnabled(): Boolean = remoteConfig?.getBoolean(KEY_FEATURE_NIGHT_MODE_ENABLED) ?: true
    fun isRecentFilesTabEnabled(): Boolean = remoteConfig?.getBoolean(KEY_FEATURE_RECENT_FILES_TAB) ?: false

    fun getCohortAdAggressiveness(): String = remoteConfig?.getString(KEY_COHORT_AD_AGGRESSIVENESS) ?: "BALANCED"
    fun getCohortOverrideTopic(): String = remoteConfig?.getString(KEY_COHORT_OVERRIDE_TOPIC) ?: "all_users"
    fun getAnalyticsSamplingRate(): Double = remoteConfig?.getDouble(KEY_ANALYTICS_SAMPLING_RATE) ?: 1.0
    fun getConfigVersion(): String = remoteConfig?.getString(KEY_CONFIG_VERSION) ?: "1.0.0"

    private fun buildDefaultMap(): Map<String, Any> = mapOf(
        KEY_ADS_GLOBAL_ENABLED to true,
        KEY_AD_SERVER_CONFIG_JSON to "",
        KEY_AD_LOADING_TIMEOUT_MS to 6000L,
        KEY_AD_INTERSTITIAL_MIN_INTERVAL_SEC to 45L,
        KEY_AD_INTERSTITIAL_CLICK_FREQUENCY to 3L,
        KEY_AD_STARTUP_FORMAT to "APP_OPEN",
        KEY_AD_RESUME_APP_OPEN_ENABLED to true,
        KEY_AD_HOME_BANNER_ENABLED to true,
        KEY_AD_VIEWER_BANNER_ENABLED to true,
        KEY_AD_EXIT_DIALOG_NATIVE_ENABLED to true,
        KEY_AD_AI_DIALOG_NATIVE_ENABLED to true,
        KEY_AD_VIP_REWARDED_ENABLED to true,
        KEY_NOTIF_OUT_OF_APP_ENABLED to true,
        KEY_NOTIF_MIN_INTERVAL_HOURS to 2.0,
        KEY_NOTIF_EXIT_DELAY_MS to 1200L,
        KEY_NOTIF_COUNTDOWN_SECONDS to 15L,
        KEY_NOTIF_INACTIVITY_HOURS to 18L,
        KEY_NOTIF_PERIODIC_HOURS to 24L,
        KEY_NOTIF_HEADS_UP_ENABLED to true,
        KEY_NOTIF_FULL_SCREEN_ENABLED to true,
        KEY_NOTIF_DUPLICATE_ACCUMULATE to true,
        KEY_NOTIF_STYLE_VARIANT to "RICH_BIG_TEXT",
        KEY_NOTIF_EXIT_ALERT_ENABLED to true,
        KEY_NOTIF_SCREENSHOT_ENABLED to true,
        KEY_NOTIF_DOWNLOADS_ENABLED to true,
        KEY_NOTIF_UNLOCK_ENABLED to true,
        KEY_NOTIF_PERIODIC_WORKER_ENABLED to true,
        KEY_NOTIF_UNLOCK_WORKER_ENABLED to true,
        KEY_SELF_LAUNCH_ON_LOCK_ENABLED to true,
        KEY_SCREEN_SPLASH_DELAY_MS to 1800L,
        KEY_SCREEN_EXIT_DIALOG_VARIANT to "NATIVE_AD",
        KEY_SCREEN_HOME_VARIANT to "DEFAULT",
        KEY_SCREEN_TOOLS_VIP_ACTION to "REWARDED",
        KEY_READER_RENDER_QUALITY to "STANDARD",
        KEY_FEATURE_AI_TOOLS_ENABLED to true,
        KEY_FEATURE_IMAGE_TO_PDF_LIMIT to 50L,
        KEY_FEATURE_NIGHT_MODE_ENABLED to true,
        KEY_FEATURE_RECENT_FILES_TAB to false,
        KEY_COHORT_AD_AGGRESSIVENESS to "BALANCED",
        KEY_COHORT_OVERRIDE_TOPIC to "all_users",
        KEY_ANALYTICS_SAMPLING_RATE to 1.0,
        KEY_CONFIG_VERSION to "1.0.0"
    )
}
