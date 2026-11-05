package com.itspark.pdfreader.monetization

import android.os.SystemClock
import android.util.Log

/**
 * Coordinates ad display across the application to prevent overlapping ads,
 * manage mutual exclusion (mutex lock), and enforce safe intervals between impressions.
 */
object AdDisplayCoordinator {

    private const val TAG = "AdDisplayCoordinator"

    /**
     * Minimum buffer time (in milliseconds) required after any full-screen ad dismissal
     * before another full-screen ad is allowed to present.
     */
    private const val MIN_AD_DISMISS_BUFFER_MS = 8000L

    @Volatile
    private var isAdShowing: Boolean = false

    @Volatile
    private var activeAdFormat: AdFormatType? = null

    @Volatile
    private var lastAdDismissTimestamp: Long = 0L

    /**
     * Checks if a full-screen ad (App Open, Interstitial, or Rewarded) can be shown right now.
     */
    @Synchronized
    fun canShowFullScreenAd(): Boolean {
        if (isAdShowing) {
            Log.w(TAG, "Ad display blocked: An ad is currently showing ($activeAdFormat)")
            return false
        }

        val elapsedSinceDismiss = SystemClock.elapsedRealtime() - lastAdDismissTimestamp
        if (lastAdDismissTimestamp > 0 && elapsedSinceDismiss < MIN_AD_DISMISS_BUFFER_MS) {
            val waitSec = (MIN_AD_DISMISS_BUFFER_MS - elapsedSinceDismiss) / 1000
            Log.w(TAG, "Ad display blocked: Safe buffer active (${waitSec}s remaining)")
            return false
        }

        return true
    }

    /**
     * Acquires the display lock when an ad starts showing.
     */
    @Synchronized
    fun onAdStartedShowing(format: AdFormatType) {
        isAdShowing = true
        activeAdFormat = format
        Log.d(TAG, "Ad display lock ACQUIRED for format: $format")
    }

    /**
     * Releases the display lock when an ad is dismissed or fails to show.
     */
    @Synchronized
    fun onAdDismissed() {
        Log.d(TAG, "Ad display lock RELEASED for format: $activeAdFormat")
        isAdShowing = false
        activeAdFormat = null
        lastAdDismissTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * Inspects whether any full-screen ad is actively displayed.
     */
    fun isAnyAdShowing(): Boolean = isAdShowing
}
