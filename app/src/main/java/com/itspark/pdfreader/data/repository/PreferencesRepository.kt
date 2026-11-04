package com.itspark.pdfreader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.itspark.pdfreader.domain.model.AttributionData
import com.itspark.pdfreader.domain.model.AttributionType

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var selectedLanguageCode: String
        get() = prefs.getString(KEY_LANGUAGE_CODE, "en_US") ?: "en_US"
        set(value) = prefs.edit().putString(KEY_LANGUAGE_CODE, value).apply()

    // ─── Attribution & Cohort Persistence ──────────────────────────────────────

    var isAttributionResolved: Boolean
        get() = prefs.getBoolean(KEY_ATTRIBUTION_RESOLVED, false)
        private set(value) = prefs.edit().putBoolean(KEY_ATTRIBUTION_RESOLVED, value).apply()

    fun saveAttribution(attribution: AttributionData) {
        prefs.edit()
            .putString(KEY_ATTRIBUTION_TYPE, attribution.type.name)
            .putString(KEY_ATTRIBUTION_GCLID, attribution.gclid.orEmpty())
            .putString(KEY_ATTRIBUTION_SOURCE, attribution.utmSource.orEmpty())
            .putString(KEY_ATTRIBUTION_MEDIUM, attribution.utmMedium.orEmpty())
            .putString(KEY_ATTRIBUTION_CAMPAIGN, attribution.utmCampaign.orEmpty())
            .putString(KEY_ATTRIBUTION_RAW_REFERRER, attribution.rawReferrer.orEmpty())
            .putLong(KEY_ATTRIBUTION_TIMESTAMP, attribution.installTimestampMs)
            .putBoolean(KEY_ATTRIBUTION_RESOLVED, true)
            .apply()
    }

    fun getAttributionType(): AttributionType {
        val typeStr = prefs.getString(KEY_ATTRIBUTION_TYPE, null) ?: return AttributionType.ORGANIC
        return runCatching { AttributionType.valueOf(typeStr) }.getOrDefault(AttributionType.ORGANIC)
    }

    fun getAttributionData(): AttributionData {
        return AttributionData(
            type = getAttributionType(),
            gclid = prefs.getString(KEY_ATTRIBUTION_GCLID, null)?.takeIf { it.isNotEmpty() },
            utmSource = prefs.getString(KEY_ATTRIBUTION_SOURCE, null)?.takeIf { it.isNotEmpty() },
            utmMedium = prefs.getString(KEY_ATTRIBUTION_MEDIUM, null)?.takeIf { it.isNotEmpty() },
            utmCampaign = prefs.getString(KEY_ATTRIBUTION_CAMPAIGN, null)?.takeIf { it.isNotEmpty() },
            rawReferrer = prefs.getString(KEY_ATTRIBUTION_RAW_REFERRER, null)?.takeIf { it.isNotEmpty() },
            installTimestampMs = prefs.getLong(KEY_ATTRIBUTION_TIMESTAMP, 0L)
        )
    }

    /**
     * Set of bookmarked file paths.
     * Persisting file paths ensures bookmarks survive app restarts,
     * re-indexes, and device reboots.
     */
    fun getBookmarkedPaths(): Set<String> {
        return prefs.getStringSet(KEY_BOOKMARKED_PATHS, emptySet())?.toSet() ?: emptySet()
    }

    fun addBookmark(path: String) {
        val updated = getBookmarkedPaths().toMutableSet()
        if (updated.add(path)) {
            prefs.edit().putStringSet(KEY_BOOKMARKED_PATHS, updated).apply()
        }
    }

    fun removeBookmark(path: String) {
        val updated = getBookmarkedPaths().toMutableSet()
        if (updated.remove(path)) {
            prefs.edit().putStringSet(KEY_BOOKMARKED_PATHS, updated).apply()
        }
    }

    fun isBookmarked(path: String): Boolean {
        return getBookmarkedPaths().contains(path)
    }

    companion object {
        private const val KEY_FIRST_LAUNCH = "key_is_first_launch"
        private const val KEY_LANGUAGE_CODE = "key_language_code"
        private const val KEY_BOOKMARKED_PATHS = "key_bookmarked_paths"

        private const val KEY_ATTRIBUTION_RESOLVED = "key_attribution_resolved"
        private const val KEY_ATTRIBUTION_TYPE = "key_attribution_type"
        private const val KEY_ATTRIBUTION_GCLID = "key_attribution_gclid"
        private const val KEY_ATTRIBUTION_SOURCE = "key_attribution_source"
        private const val KEY_ATTRIBUTION_MEDIUM = "key_attribution_medium"
        private const val KEY_ATTRIBUTION_CAMPAIGN = "key_attribution_campaign"
        private const val KEY_ATTRIBUTION_RAW_REFERRER = "key_attribution_raw_referrer"
        private const val KEY_ATTRIBUTION_TIMESTAMP = "key_attribution_timestamp"
    }
}
