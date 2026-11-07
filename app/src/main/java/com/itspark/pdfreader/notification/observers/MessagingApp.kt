package com.itspark.pdfreader.notification.observers

/**
 * Enumerates supported file sources including standard system folders
 * and popular messaging platforms. Provides path keyword matching,
 * package identification, and localized notification copy.
 */
enum class MessagingApp(
    val displayName: String,
    val pathKeywords: List<String>,
    val packageNames: List<String>
) {
    WHATSAPP(
        displayName = "WhatsApp",
        pathKeywords = listOf(
            "WhatsApp/Media/WhatsApp Documents",
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",
            "WhatsApp Business/Media/WhatsApp Business Documents",
            "com.whatsapp"
        ),
        packageNames = listOf("com.whatsapp", "com.whatsapp.w4b")
    ),

    TELEGRAM(
        displayName = "Telegram",
        pathKeywords = listOf(
            "Telegram/Telegram Documents",
            "Download/Telegram",
            "Telegram",
            "org.telegram.messenger"
        ),
        packageNames = listOf("org.telegram.messenger", "org.telegram.messenger.web")
    ),

    MESSENGER(
        displayName = "Messenger",
        pathKeywords = listOf(
            "Download/Messenger",
            "Movies/Messenger",
            "Facebook/Messenger",
            "com.facebook.orca"
        ),
        packageNames = listOf("com.facebook.orca")
    ),

    VIBER(
        displayName = "Viber",
        pathKeywords = listOf(
            "viber/media",
            "Download/Viber",
            "com.viber.voip"
        ),
        packageNames = listOf("com.viber.voip")
    ),

    LINE(
        displayName = "LINE",
        pathKeywords = listOf(
            "Download/LINE",
            "LINE",
            "jp.naver.line.android"
        ),
        packageNames = listOf("jp.naver.line.android")
    ),

    DISCORD(
        displayName = "Discord",
        pathKeywords = listOf(
            "Download/Discord",
            "Discord",
            "com.discord"
        ),
        packageNames = listOf("com.discord")
    ),

    ZALO(
        displayName = "Zalo",
        pathKeywords = listOf(
            "Download/Zalo",
            "Zalo",
            "com.zing.zalo"
        ),
        packageNames = listOf("com.zing.zalo")
    ),

    TEAMS(
        displayName = "Microsoft Teams",
        pathKeywords = listOf(
            "Download/Microsoft Teams",
            "Teams",
            "com.microsoft.teams"
        ),
        packageNames = listOf("com.microsoft.teams")
    ),

    TENCENT(
        displayName = "WeChat / Tencent",
        pathKeywords = listOf(
            "tencent/MicroMsg/Download",
            "tencent/QQfile_recv",
            "Android/data/com.tencent.mm",
            "com.tencent.mm",
            "com.tencent.mobileqq"
        ),
        packageNames = listOf("com.tencent.mm", "com.tencent.mobileqq")
    ),

    GAPO(
        displayName = "Gapo",
        pathKeywords = listOf(
            "Download/Gapo",
            "Gapo",
            "vn.gapo.work"
        ),
        packageNames = listOf("vn.gapo.work", "vn.gapo")
    ),

    DOCUMENTS_DIR(
        displayName = "Documents",
        pathKeywords = listOf(
            "/Documents/",
            "Documents"
        ),
        packageNames = emptyList()
    ),

    GENERIC_DOWNLOAD(
        displayName = "Downloads",
        pathKeywords = listOf(
            "/Download/",
            "Download"
        ),
        packageNames = emptyList()
    );

    companion object {
        /**
         * Resolves the originating application or directory by matching file paths
         * or Android MediaStore owner package tags.
         */
        fun resolveSource(filePath: String, ownerPackage: String? = null): MessagingApp {
            // 1. Try resolving by explicit owner package name
            if (!ownerPackage.isNullOrEmpty()) {
                entries.firstOrNull { it.packageNames.contains(ownerPackage) }?.let { return it }
            }

            // 2. Try resolving by specific messaging app path signatures
            for (app in entries) {
                if (app == DOCUMENTS_DIR || app == GENERIC_DOWNLOAD) continue
                for (keyword in app.pathKeywords) {
                    if (filePath.contains(keyword, ignoreCase = true)) {
                        return app
                    }
                }
            }

            // 3. Fallback to Documents directory or generic Downloads
            if (filePath.contains("Documents", ignoreCase = true)) {
                return DOCUMENTS_DIR
            }

            return GENERIC_DOWNLOAD
        }
    }
}
