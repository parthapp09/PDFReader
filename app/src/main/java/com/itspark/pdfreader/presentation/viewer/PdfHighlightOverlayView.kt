package com.itspark.pdfreader.presentation.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight hardware-accelerated overlay that draws search highlights over a PDF page.
 * Completely decouples highlight rendering from PDF page bitmap rendering, eliminating
 * bitmap cache evictions, re-renders, and visual flicker/glitches during search navigation.
 */
class PdfHighlightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var highlights: List<RectF> = emptyList()
    private var activeRect: RectF? = null

    // Soft semi-transparent blue for standard search matches
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#502196F3")
        style = Paint.Style.FILL
    }

    // Vibrant semi-transparent yellow for the active/focused search match
    private val activeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3FFD600")
        style = Paint.Style.FILL
    }

    init {
        // Overlay should not intercept touch events
        isClickable = false
        isFocusable = false
    }

    fun setHighlights(rects: List<RectF>, active: RectF?) {
        this.highlights = rects
        this.activeRect = active
        visibility = if (rects.isEmpty()) GONE else VISIBLE
        invalidate()
    }

    fun clearHighlights() {
        this.highlights = emptyList()
        this.activeRect = null
        visibility = GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (highlights.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (r in highlights) {
            val pixelRect = RectF(
                r.left * w,
                r.top * h,
                r.right * w,
                r.bottom * h
            )
            pixelRect.inset(-2f, -1f)
            val isActive = activeRect != null && isSameRect(r, activeRect!!)
            val paint = if (isActive) activeHighlightPaint else highlightFillPaint
            canvas.drawRoundRect(pixelRect, 4f, 4f, paint)
        }
    }

    private fun isSameRect(r1: RectF, r2: RectF): Boolean {
        val eps = 0.002f
        return Math.abs(r1.left - r2.left) < eps &&
                Math.abs(r1.top - r2.top) < eps &&
                Math.abs(r1.right - r2.right) < eps &&
                Math.abs(r1.bottom - r2.bottom) < eps
    }
}
