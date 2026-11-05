package com.itspark.pdfreader.presentation.tools

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.itspark.pdfreader.R

enum class PdfToolType {
    IMAGE_TO_PDF,
    AI
}

data class PdfToolItem(
    val type: PdfToolType,
    val title: String,
    @DrawableRes val iconRes: Int,
    @ColorInt val circleColor: Int
)

object PdfToolsDataProvider {
    fun getTools(): List<PdfToolItem> = listOf(
        PdfToolItem(
            type = PdfToolType.IMAGE_TO_PDF,
            title = "Image to PDF",
            iconRes = R.drawable.ic_tool_image_to_pdf,
            circleColor = Color.parseColor("#0D9488") // Vibrant Teal / Emerald
        ),
        PdfToolItem(
            type = PdfToolType.AI,
            title = "AI",
            iconRes = R.drawable.ic_tool_ai,
            circleColor = Color.parseColor("#7C3AED") // Electric Purple for AI
        )
    )
}
