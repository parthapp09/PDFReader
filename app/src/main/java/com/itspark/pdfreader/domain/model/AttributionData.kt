package com.itspark.pdfreader.domain.model

enum class AttributionType {
    ORGANIC,
    PAID_GOOGLE,
    PAID_OTHER,
    UNKNOWN;

    val analyticsValue: String
        get() = when (this) {
            ORGANIC -> "organic"
            PAID_GOOGLE -> "paid_google"
            PAID_OTHER -> "paid_other"
            UNKNOWN -> "unknown"
        }
}

data class AttributionData(
    val type: AttributionType,
    val gclid: String? = null,
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val rawReferrer: String? = null,
    val installTimestampMs: Long = System.currentTimeMillis()
)
