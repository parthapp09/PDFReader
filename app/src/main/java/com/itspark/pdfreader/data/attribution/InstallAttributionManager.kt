package com.itspark.pdfreader.data.attribution

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.itspark.pdfreader.domain.model.AttributionData
import com.itspark.pdfreader.domain.model.AttributionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.milliseconds

object InstallAttributionManager {

    private const val TAG = "InstallAttribution"
    private const val DEFAULT_TIMEOUT_MS = 2500L

    /**
     * Resolves attribution from Google Play Install Referrer API.
     * Guaranteed to return within [timeoutMs] to prevent delaying app startup.
     */
    suspend fun resolveAttribution(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): AttributionData = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(timeoutMs.milliseconds) {
            queryInstallReferrer(context.applicationContext)
        }
        result ?: AttributionData(
            type = AttributionType.ORGANIC,
            rawReferrer = "timeout_or_unavailable"
        )
    }

    private suspend fun queryInstallReferrer(appContext: Context): AttributionData =
        suspendCancellableCoroutine { cont ->
            try {
                val referrerClient = InstallReferrerClient.newBuilder(appContext).build()

                referrerClient.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        try {
                            when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK -> {
                                    val response: ReferrerDetails? = referrerClient.installReferrer
                                    val rawReferrer = response?.installReferrer.orEmpty()
                                    val attribution = parseReferrerString(rawReferrer)
                                    Log.d(TAG, "Install Referrer OK: $attribution")
                                    if (cont.isActive) cont.resume(attribution)
                                }
                                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                                    Log.w(TAG, "Install Referrer not supported on this device")
                                    if (cont.isActive) cont.resume(AttributionData(type = AttributionType.ORGANIC, rawReferrer = "not_supported"))
                                }
                                InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                                    Log.w(TAG, "Install Referrer service unavailable")
                                    if (cont.isActive) cont.resume(AttributionData(type = AttributionType.ORGANIC, rawReferrer = "service_unavailable"))
                                }
                                else -> {
                                    Log.w(TAG, "Install Referrer responseCode: $responseCode")
                                    if (cont.isActive) cont.resume(AttributionData(type = AttributionType.ORGANIC, rawReferrer = "code_$responseCode"))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading install referrer", e)
                            if (cont.isActive) cont.resume(AttributionData(type = AttributionType.ORGANIC, rawReferrer = "exception_${e.message}"))
                        } finally {
                            try {
                                referrerClient.endConnection()
                            } catch (_: Exception) {}
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        Log.d(TAG, "Install Referrer service disconnected")
                        if (cont.isActive) {
                            cont.resume(AttributionData(type = AttributionType.ORGANIC, rawReferrer = "disconnected"))
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start InstallReferrerClient", e)
                if (cont.isActive) {
                    cont.resume(AttributionData(type = AttributionType.ORGANIC, rawReferrer = "client_failed"))
                }
            }
        }

    /**
     * Parses the Google Play referrer query parameters and classifies organic vs paid traffic.
     */
    fun parseReferrerString(rawReferrer: String): AttributionData {
        if (rawReferrer.isBlank()) {
            return AttributionData(type = AttributionType.ORGANIC)
        }

        // Install referrers arrive URL-query formatted, e.g.:
        // "utm_source=google-play&utm_medium=cpc&gclid=Cj0KCQjw123...&utm_campaign=summer_promo"
        val syntheticUri = Uri.parse("https://play.google.com/store/apps/details?$rawReferrer")
        val gclid = syntheticUri.getQueryParameter("gclid")
        val utmSource = syntheticUri.getQueryParameter("utm_source")
        val utmMedium = syntheticUri.getQueryParameter("utm_medium")
        val utmCampaign = syntheticUri.getQueryParameter("utm_campaign")

        val type = when {
            // 1. GCLID detection (Google Ads Click ID explicitly present)
            !gclid.isNullOrBlank() -> AttributionType.PAID_GOOGLE

            // 2. CPC / Paid Medium check
            utmMedium?.equals("cpc", ignoreCase = true) == true ||
            utmMedium?.equals("paid", ignoreCase = true) == true ||
            utmMedium?.equals("ppc", ignoreCase = true) == true ||
            utmMedium?.equals("ads", ignoreCase = true) == true -> {
                if (utmSource?.contains("google", ignoreCase = true) == true) {
                    AttributionType.PAID_GOOGLE
                } else {
                    AttributionType.PAID_OTHER
                }
            }

            // 3. Known paid acquisition partner sources
            utmSource?.matches(
                Regex("(?i).*(facebook|fb|instagram|tiktok|bytedance|unity|mintegral|ironsource|applovin|snapchat|twitter|kwai).*")
            ) == true -> AttributionType.PAID_OTHER

            else -> AttributionType.ORGANIC
        }

        return AttributionData(
            type = type,
            gclid = gclid,
            utmSource = utmSource,
            utmMedium = utmMedium,
            utmCampaign = utmCampaign,
            rawReferrer = rawReferrer
        )
    }
}
