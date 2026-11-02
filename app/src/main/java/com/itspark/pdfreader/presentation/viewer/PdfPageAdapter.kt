package com.itspark.pdfreader.presentation.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.databinding.ItemPdfPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

    companion object {
        const val PAYLOAD_HIGHLIGHTS = "PAYLOAD_HIGHLIGHTS"
    }

    // Cache up to 16 rendered base PDF bitmaps (never evicted during search navigation)
    private val bitmapCache = object : LruCache<Int, Bitmap>(16) {
        override fun sizeOf(key: Int, value: Bitmap): Int = 1
    }

    private val renderLock = Any()

    // Search highlights state
    private var matchingPages = setOf<Int>()
    private var matchRectsByPage = mapOf<Int, List<RectF>>()
    private var activePageIndex: Int = -1
    private var activeRect: RectF? = null

    inner class PdfPageViewHolder(private val binding: ItemPdfPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var renderJob: Job? = null

        fun bind(pageIndex: Int) {
            renderJob?.cancel()

            // Update badge and highlight overlay
            updateHighlights(pageIndex)

            val cached = bitmapCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                binding.ivPdfPage.tag = pageIndex
                binding.ivPdfPage.setImageBitmap(cached)
                return
            }

            // Only clear bitmap if this recycled view is holding a different page
            if (binding.ivPdfPage.tag != pageIndex) {
                binding.ivPdfPage.setImageBitmap(null)
            }

            renderJob = scope.launch(Dispatchers.IO) {
                val bitmap = renderPageBitmap(pageIndex)
                if (bitmap != null) {
                    bitmapCache.put(pageIndex, bitmap)
                    withContext(Dispatchers.Main) {
                        binding.ivPdfPage.tag = pageIndex
                        binding.ivPdfPage.setImageBitmap(bitmap)
                    }
                }
            }
        }

        fun updateHighlights(pageIndex: Int) {
            val isMatch = matchingPages.contains(pageIndex)
            if (isMatch) {
                val rects = matchRectsByPage[pageIndex] ?: emptyList()
                val active = if (pageIndex == activePageIndex) activeRect else null
                binding.highlightOverlay.setHighlights(rects, active)
            } else {
                binding.highlightOverlay.clearHighlights()
            }
        }

        private fun renderPageBitmap(index: Int): Bitmap? {
            synchronized(renderLock) {
                if (index < 0 || index >= renderer.pageCount) return null
                return try {
                    val page = renderer.openPage(index)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val binding = ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PdfPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: PdfPageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_HIGHLIGHTS)) {
            holder.updateHighlights(position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = renderer.pageCount

    fun setSearchHighlights(
        pages: Set<Int>,
        rects: Map<Int, List<RectF>>,
        activePage: Int = -1,
        activeMatchRect: RectF? = null
    ) {
        matchingPages = pages
        matchRectsByPage = rects
        activePageIndex = activePage
        activeRect = activeMatchRect
        // Notify highlights change using payload without re-rendering any page bitmaps
        notifyItemRangeChanged(0, itemCount, PAYLOAD_HIGHLIGHTS)
    }

    fun setActiveMatch(pageIndex: Int, matchRect: RectF) {
        val prevActivePage = activePageIndex
        activePageIndex = pageIndex
        activeRect = matchRect

        // Instantaneous hardware overlay repaint; base bitmaps remain intact
        if (prevActivePage != -1) {
            notifyItemChanged(prevActivePage, PAYLOAD_HIGHLIGHTS)
        }
        if (pageIndex != -1 && pageIndex != prevActivePage) {
            notifyItemChanged(pageIndex, PAYLOAD_HIGHLIGHTS)
        }
    }

    fun clearSearchHighlights() {
        matchingPages = emptySet()
        matchRectsByPage = emptyMap()
        activePageIndex = -1
        activeRect = null
        notifyItemRangeChanged(0, itemCount, PAYLOAD_HIGHLIGHTS)
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }
}
