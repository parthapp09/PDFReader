package com.itspark.pdfreader.presentation.viewer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * A FrameLayout container that adds smooth pinch-to-zoom, double-tap zoom,
 * and two-dimensional panning to its child (e.g. RecyclerView for PDF pages).
 */
class ZoomableFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var posX = 0f
    private var posY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val minScale = 1.0f
    private val maxScale = 4.0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(minScale, min(scaleFactor, maxScale))

            clampPosition()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            scaleFactor = if (scaleFactor > 1.2f) {
                1.0f
            } else {
                2.2f
            }
            if (scaleFactor == 1.0f) {
                posX = 0f
                posY = 0f
            }
            clampPosition()
            invalidate()
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                activePointerId = ev.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleFactor > 1.05f) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = ev.getX(pointerIndex)
                        val y = ev.getY(pointerIndex)

                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        posX += dx
                        posY += dy

                        lastTouchX = x
                        lastTouchY = y

                        clampPosition()
                        invalidate()
                        return true // Consume drag when zoomed in
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = ev.getX(newPointerIndex)
                    lastTouchY = ev.getY(newPointerIndex)
                    activePointerId = ev.getPointerId(newPointerIndex)
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun clampPosition() {
        val maxPosX = (width * (scaleFactor - 1f)) / 2f
        val maxPosY = (height * (scaleFactor - 1f)) / 2f

        posX = max(-maxPosX, min(posX, maxPosX))
        posY = max(-maxPosY, min(posY, maxPosY))
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(posX, posY)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        posX = 0f
        posY = 0f
        invalidate()
    }
}
