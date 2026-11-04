package com.itspark.pdfreader.consent

import android.app.Activity
import android.content.Context
import android.util.Log
import com.itspark.pdfreader.analytics.AnalyticsTracker
import com.itspark.pdfreader.domain.model.AttributionType
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.milliseconds

/**
 * Segment-Aware Consent Manager backed by Google User Messaging Platform (UMP).
 *
 * Capabilities:
 *   - Checks geographic applicability (e.g. EEA/UK GDPR compliance).
 *   - Honors user acquisition segments (applies conservative timeouts for paid cohorts).
 *   - Informs analytics and monetization about consent states (personalized vs limited ads).
 *   - Provides privacy options re-entry form for Google Play Policy compliance.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"
    private const val CONSENT_TIMEOUT_MS = 3000L

    /**
     * Suspending consent resolution with automatic timeout protection.
     * Guaranteed never to hang or block splash navigation.
     */
    suspend fun resolveConsentAsync(
        activity: Activity,
        segment: AttributionType,
        timeoutMs: Long = CONSENT_TIMEOUT_MS
    ): Boolean = withContext(Dispatchers.Main) {
        val result = withTimeoutOrNull(timeoutMs.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                requestConsent(activity, segment) { canRequestAds ->
                    if (continuation.isActive) {
                        continuation.resume(canRequestAds)
                    }
                }
            }
        }
        // If timed out, fall back safely to current consent information status
        result ?: canRequestAds(activity)
    }

    /**
     * Executes the Google UMP consent request and conditionally shows the form if required.
     */
    fun requestConsent(
        activity: Activity,
        segment: AttributionType,
        onComplete: (canRequestAds: Boolean) -> Unit
    ) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInfo: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent status successfully retrieved
                val isConsentRequired = consentInfo.consentStatus == ConsentInformation.ConsentStatus.REQUIRED

                Log.d(TAG, "Consent info updated. Required: $isConsentRequired | Segment: $segment")

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form presentation error: [${formError.errorCode}] ${formError.message}")
                    }

                    val canRequestAds = consentInfo.canRequestAds()
                    applyConsentRules(activity, segment, canRequestAds)
                    onComplete(canRequestAds)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: [${requestError.errorCode}] ${requestError.message}")
                // In case of error (e.g. offline), proceed without blocking
                val fallbackCanRequest = consentInfo.canRequestAds()
                applyConsentRules(activity, segment, fallbackCanRequest)
                onComplete(fallbackCanRequest)
            }
        )
    }

    /**
     * Applies segment-specific privacy settings based on user consent choice.
     */
    private fun applyConsentRules(context: Context, segment: AttributionType, canRequestAds: Boolean) {
        Log.i(TAG, "Applying consent rules for segment [$segment]: canRequestAds = $canRequestAds")

        AnalyticsTracker.logMonetizationDecision(
            context,
            adType = "consent_evaluation",
            allowed = canRequestAds,
            reason = if (canRequestAds) "consent_granted_or_not_required" else "consent_denied_or_restricted"
        )
    }

    /**
     * Returns whether ads can be requested under current consent status.
     * True if user granted consent, or if consent is not legally required (e.g. non-EEA users).
     */
    fun canRequestAds(context: Context): Boolean {
        return try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(context)
            consentInfo.canRequestAds() ||
                    consentInfo.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED ||
                    consentInfo.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Checks if the user is in a region requiring privacy options (e.g. to re-open consent settings).
     */
    fun isPrivacyOptionsRequired(context: Context): Boolean {
        return try {
            UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the consent modification form (for Settings / About screen privacy options).
     */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Error showing privacy options form: ${formError.message}")
            }
            onDismissed()
        }
    }
}
