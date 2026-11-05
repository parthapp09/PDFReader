package com.itspark.pdfreader.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.itspark.pdfreader.domain.model.AttributionData
import com.itspark.pdfreader.domain.model.AttributionType
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsTracker {

    private const val TAG = "AnalyticsTracker"

    // Custom Event Names
    private const val EVENT_ATTRIBUTION_RESOLVED = "attribution_resolved"
    private const val EVENT_MONETIZATION_COHORT_DECISION = "monetization_decision"
    private const val EVENT_NOTIFICATION_IMPRESSION = "notification_impression"
    private const val EVENT_NOTIFICATION_CLICK = "notification_click"
    private const val EVENT_NOTIFICATION_DISMISSED = "notification_dismissed"

    // User Properties
    private const val PROP_USER_SEGMENT = "user_segment"
    private const val PROP_ACQUISITION_SOURCE = "acquisition_source"

    private var firebaseAnalytics: FirebaseAnalytics? = null

    private fun getFirebase(context: Context): FirebaseAnalytics? {
        if (firebaseAnalytics == null) {
            try {
                firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseAnalytics not initialized: ${e.message}")
            }
        }
        return firebaseAnalytics
    }

    /**
     * Sets Firebase User Properties for user segmentation and cohort analysis.
     */
    fun setUserSegment(context: Context, segment: AttributionType, source: String? = null) {
        val fa = getFirebase(context)
        val segmentValue = segment.analyticsValue
        val sourceValue = source ?: if (segment == AttributionType.PAID_GOOGLE) "google_ads" else "organic"

        Log.i(TAG, "Setting User Property: $PROP_USER_SEGMENT = $segmentValue | $PROP_ACQUISITION_SOURCE = $sourceValue")
        try {
            fa?.setUserProperty(PROP_USER_SEGMENT, segmentValue)
            fa?.setUserProperty(PROP_ACQUISITION_SOURCE, sourceValue)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user property in Firebase", e)
        }
    }

    /**
     * Logs the primary attribution event with full campaign parameters.
     */
    fun logAttributionEvent(context: Context, attribution: AttributionData) {
        val fa = getFirebase(context)
        val bundle = Bundle().apply {
            putString("attribution_type", attribution.type.analyticsValue)
            putBoolean("has_gclid", !attribution.gclid.isNullOrBlank())
            putString("gclid", attribution.gclid.orEmpty())
            putString(FirebaseAnalytics.Param.SOURCE, attribution.utmSource.orEmpty())
            putString(FirebaseAnalytics.Param.MEDIUM, attribution.utmMedium.orEmpty())
            putString(FirebaseAnalytics.Param.CAMPAIGN, attribution.utmCampaign.orEmpty())
        }

        Log.i(TAG, "Logging Firebase Event: $EVENT_ATTRIBUTION_RESOLVED with params: $bundle")
        try {
            fa?.logEvent(EVENT_ATTRIBUTION_RESOLVED, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event $EVENT_ATTRIBUTION_RESOLVED", e)
        }

        // Also update persistent user property
        setUserSegment(context, attribution.type, attribution.utmSource)
    }

    /**
     * Logs ad decision logic for attribution-based monetization tracking.
     */
    fun logMonetizationDecision(
        context: Context,
        adType: String,
        allowed: Boolean,
        reason: String
    ) {
        val fa = getFirebase(context)
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putBoolean("decision_allowed", allowed)
            putString("decision_reason", reason)
        }

        Log.d(TAG, "Logging Firebase Event: $EVENT_MONETIZATION_COHORT_DECISION | $bundle")
        try {
            fa?.logEvent(EVENT_MONETIZATION_COHORT_DECISION, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log monetization decision", e)
        }
    }

    /**
     * Tracks notification impression.
     */
    fun logNotificationImpression(context: Context, notificationId: String, channelId: String) {
        val fa = getFirebase(context)
        val bundle = Bundle().apply {
            putString("notification_id", notificationId)
            putString("channel_id", channelId)
            putLong("timestamp_ms", System.currentTimeMillis())
        }
        try {
            fa?.logEvent(EVENT_NOTIFICATION_IMPRESSION, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification impression", e)
        }
    }

    /**
     * Tracks user click / interaction with a notification.
     */
    fun logNotificationClick(context: Context, notificationId: String, channelId: String) {
        val fa = getFirebase(context)
        val bundle = Bundle().apply {
            putString("notification_id", notificationId)
            putString("channel_id", channelId)
            putLong("timestamp_ms", System.currentTimeMillis())
        }
        try {
            fa?.logEvent(EVENT_NOTIFICATION_CLICK, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification click", e)
        }
    }

    /**
     * Tracks notification dismissal (swipe away).
     */
    fun logNotificationDismissed(context: Context, notificationId: String, channelId: String) {
        val fa = getFirebase(context)
        val bundle = Bundle().apply {
            putString("notification_id", notificationId)
            putString("channel_id", channelId)
            putLong("timestamp_ms", System.currentTimeMillis())
        }
        try {
            fa?.logEvent(EVENT_NOTIFICATION_DISMISSED, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification dismissal", e)
        }
    }
}
