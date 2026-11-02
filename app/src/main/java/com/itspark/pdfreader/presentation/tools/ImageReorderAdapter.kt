package com.itspark.pdfreader.presentation.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.databinding.ItemImageReorderCardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ImageReorderAdapter(
    initialUris: List<Uri>,
    private val scope: CoroutineScope,
    private val onListChanged: (Int) -> Unit
) : RecyclerView.Adapter<ImageReorderAdapter.ImageReorderViewHolder>() {

    companion object {
        const val PAYLOAD_INDEX = "PAYLOAD_INDEX"
        private const val THUMB_DIMEN = 350
    }

    private val items = initialUris.toMutableList()

    // Cache downsampled thumbnails in memory (32 items max)
    private val thumbnailCache = object : LruCache<String, Bitmap>(32) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    inner class ImageReorderViewHolder(private val binding: ItemImageReorderCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var loadJob: Job? = null

        init {
            binding.btnRemoveImage.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    removeItem(currentPos)
                }
            }
        }

        fun bind(position: Int) {
            updateIndexBadge(position)
            cancelLoad()

            val uri = items[position]
            val cacheKey = uri.toString()

            val cached = thumbnailCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                binding.ivThumbnail.tag = cacheKey
                binding.ivThumbnail.setImageBitmap(cached)
            } else {
                if (binding.ivThumbnail.tag != cacheKey) {
                    binding.ivThumbnail.setImageBitmap(null)
                }
                loadJob = scope.launch(Dispatchers.IO) {
                    val bitmap = loadSampledBitmap(binding.root.context, uri, THUMB_DIMEN, THUMB_DIMEN)
                    if (bitmap != null) {
                        thumbnailCache.put(cacheKey, bitmap)
                        withContext(Dispatchers.Main) {
                            binding.ivThumbnail.tag = cacheKey
                            binding.ivThumbnail.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }

        fun updateIndexBadge(position: Int) {
            binding.tvIndexBadge.text = (position + 1).toString()
        }

        fun cancelLoad() {
            loadJob?.cancel()
            loadJob = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageReorderViewHolder {
        val binding = ItemImageReorderCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ImageReorderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageReorderViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: ImageReorderViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_INDEX)) {
            holder.updateIndexBadge(position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: ImageReorderViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelLoad()
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<Uri> = items.toList()

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)

        val start = min(fromPosition, toPosition)
        val count = abs(fromPosition - toPosition) + 1
        notifyItemRangeChanged(start, count, PAYLOAD_INDEX)
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size - position, PAYLOAD_INDEX)
            onListChanged(items.size)
        }
    }

    fun addItems(newUris: List<Uri>) {
        if (newUris.isEmpty()) return
        val start = items.size
        items.addAll(newUris)
        notifyItemRangeInserted(start, newUris.size)
        onListChanged(items.size)
    }

    private fun loadSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(reqWidth, reqHeight), null)
                } catch (_: Exception) {}
            }

            // Fallback for API 23-28
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var inSampleSize = 1
            val maxDim = max(options.outWidth, options.outHeight)
            val reqMaxDim = max(reqWidth, reqHeight)
            while ((maxDim / inSampleSize) > (reqMaxDim * 1.5)) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }
}
