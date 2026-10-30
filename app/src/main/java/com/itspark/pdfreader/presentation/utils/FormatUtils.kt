package com.itspark.pdfreader.presentation.utils

import android.graphics.Color
import com.itspark.pdfreader.domain.model.DocumentFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)

    fun formatDate(timestampMs: Long): String {
        return dateFormatter.format(Date(timestampMs))
    }

    fun getFormatBadgeColor(format: DocumentFormat): Int {
        return when (format) {
            DocumentFormat.PDF -> Color.parseColor("#EA3434")
            DocumentFormat.WORD -> Color.parseColor("#2979FF")
            DocumentFormat.EXCEL -> Color.parseColor("#00C853")
            DocumentFormat.PPT -> Color.parseColor("#FF6D00")
            DocumentFormat.TXT, DocumentFormat.ALL -> Color.parseColor("#757575")
        }
    }
}
