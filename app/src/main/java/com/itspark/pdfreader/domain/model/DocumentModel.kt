package com.itspark.pdfreader.domain.model

import android.net.Uri

data class DocumentModel(
    val id: Long,
    val name: String,
    val path: String,
    val uri: Uri,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
    val extension: String,
    val format: DocumentFormat,
    val isBookmarked: Boolean = false
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = sizeBytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.2f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
        }
}
