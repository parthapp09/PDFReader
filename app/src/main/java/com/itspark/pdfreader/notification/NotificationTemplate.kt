package com.itspark.pdfreader.notification

/**
 * Data structure representing re-engagement notification copy with dynamic deep-link targets.
 */
data class NotificationTemplate(
    val id: String,
    val title: String,
    val body: String,
    val targetTab: String = "home", // "home", "tools", "bookmarks"
    val actionText: String? = null
)

object NotificationTemplateProvider {

    private val REENGAGEMENT_TEMPLATES = listOf(
        NotificationTemplate(
            id = "template_resume_reading",
            title = "Continue Reading",
            body = "Pick up where you left off. Your recent PDF files are ready.",
            targetTab = "home",
            actionText = "Open App"
        ),
        NotificationTemplate(
            id = "template_convert_photos",
            title = "Convert Photos to PDF",
            body = "Need to send multiple images as a single PDF? Tap to convert in seconds.",
            targetTab = "tools",
            actionText = "Convert Now"
        ),
        NotificationTemplate(
            id = "template_ai_document",
            title = "Explore Document Tools",
            body = "Search, organize, and manage your documents with intelligent tools.",
            targetTab = "tools",
            actionText = "Explore Tools"
        ),
        NotificationTemplate(
            id = "template_bookmarks",
            title = "Quick Access to Starred Files",
            body = "Easily access your bookmarked documents whenever you need them.",
            targetTab = "bookmarks",
            actionText = "View Bookmarks"
        )
    )

    private val URGENCY_WARNING_TEMPLATES = listOf(
        NotificationTemplate(
            id = "template_storage_alert",
            title = "⚠️ Storage Warning: Unindexed Documents Found",
            body = "14 unindexed PDF files detected on storage. Tap to scan & secure your workspace.",
            targetTab = "home",
            actionText = "Scan Now"
        ),
        NotificationTemplate(
            id = "template_unsaved_conversion",
            title = "⚠️ Action Needed: Pending PDF Conversion",
            body = "Unsaved document fragments detected. Tap to complete your PDF export before expiry.",
            targetTab = "tools",
            actionText = "Complete PDF"
        ),
        NotificationTemplate(
            id = "template_call_document",
            title = "📞 Urgent Document Notice",
            body = "Incoming work report awaiting review. Tap to open full screen viewer.",
            targetTab = "home",
            actionText = "Review Now"
        ),
        NotificationTemplate(
            id = "template_vip_expiring",
            title = "🚨 Security & Performance Warning",
            body = "Your PDF workspace cache needs optimization. Tap to clean & accelerate.",
            targetTab = "tools",
            actionText = "Optimize Workspace"
        )
    )

    fun getRandomUrgentTemplate(): NotificationTemplate {
        return URGENCY_WARNING_TEMPLATES.random()
    }

    fun getRandomTemplate(): NotificationTemplate {
        return REENGAGEMENT_TEMPLATES.random()
    }

    fun getTemplateForTimeOfDay(hourOfDay: Int): NotificationTemplate {
        return when (hourOfDay) {
            in 8..11 -> REENGAGEMENT_TEMPLATES[0] // Morning: Resume reading
            in 12..17 -> REENGAGEMENT_TEMPLATES[1] // Afternoon: Productivity / Conversion
            in 18..22 -> REENGAGEMENT_TEMPLATES[3] // Evening: Starred files / Review
            else -> REENGAGEMENT_TEMPLATES[2]
        }
    }
}
