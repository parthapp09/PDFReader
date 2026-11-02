package com.itspark.pdfreader.presentation.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.R
import com.itspark.pdfreader.databinding.ItemPdfThumbnailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfThumbnailAdapter(
    private val renderer: PdfRenderer,
    private val scope: CoroutineScope,
    private val onThumbnailClick: (Int) -> Unit
) : RecyclerView.Adapter<PdfThumbnailAdapter.ThumbnailViewHolder>() {

    companion object {
        private const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }

    private var selectedIndex = 0
    private val thumbnailCache = object : LruCache<Int, Bitmap>(32) {
        override fun sizeOf(key: Int, value: Bitmap): Int = 1
    }

    private val renderLock = Any()

    inner class ThumbnailViewHolder(private val binding: ItemPdfThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var renderJob: Job? = null

        fun bind(pageIndex: Int) {
            renderJob?.cancel()

            binding.tvThumbnailPageNum.text = "${pageIndex + 1}"
            updateSelectionState(pageIndex)

            val cached = thumbnailCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                binding.ivThumbnail.tag = pageIndex
                binding.ivThumbnail.setImageBitmap(cached)
            } else {
                if (binding.ivThumbnail.tag != pageIndex) {
                    binding.ivThumbnail.setImageBitmap(null)
                }
                renderJob = scope.launch(Dispatchers.IO) {
                    val bitmap = renderThumbnail(pageIndex)
                    if (bitmap != null) {
                        thumbnailCache.put(pageIndex, bitmap)
                        withContext(Dispatchers.Main) {
                            binding.ivThumbnail.tag = pageIndex
                            binding.ivThumbnail.setImageBitmap(bitmap)
                        }
                    }
                }
            }

            binding.root.setOnClickListener {
                updateSelection(pageIndex)
                onThumbnailClick(pageIndex)
            }
        }

        fun updateSelectionState(pageIndex: Int) {
            val bgRes = if (pageIndex == selectedIndex) {
                R.drawable.bg_thumbnail_selected
            } else {
                R.drawable.bg_thumbnail_unselected
            }
            binding.thumbnailCard.setBackgroundResource(bgRes)
        }

        private fun renderThumbnail(index: Int): Bitmap? {
            synchronized(renderLock) {
                if (index < 0 || index >= renderer.pageCount) return null
                return try {
                    val page = renderer.openPage(index)
                    // Scaled down thumbnail: ~140px width
                    val targetWidth = 140
                    val targetHeight = (page.height * (targetWidth.toFloat() / page.width)).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
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

    fun updateSelection(newIndex: Int) {
        if (newIndex == selectedIndex) return
        val oldIndex = selectedIndex
        selectedIndex = newIndex
        notifyItemChanged(oldIndex, PAYLOAD_SELECTION)
        notifyItemChanged(newIndex, PAYLOAD_SELECTION)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ItemPdfThumbnailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThumbnailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: ThumbnailViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.updateSelectionState(position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = renderer.pageCount

    fun clearCache() {
        thumbnailCache.evictAll()
    }
}
