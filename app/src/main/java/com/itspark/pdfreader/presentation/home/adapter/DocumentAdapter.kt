package com.itspark.pdfreader.presentation.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.PdfReaderApp
import com.itspark.pdfreader.R
import com.itspark.pdfreader.databinding.ItemDocumentCardBinding
import com.itspark.pdfreader.databinding.ItemNativeAdCardBinding
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.monetization.AdPlacement
import com.itspark.pdfreader.presentation.utils.FormatUtils

/**
 * Item types supported in the document feed.
 */
sealed class DocumentFeedItem {
    data class Document(val model: DocumentModel) : DocumentFeedItem()
    data class NativeAd(val id: String = "feed_ad") : DocumentFeedItem()
}

class DocumentAdapter(
    private val onItemClick: (DocumentModel) -> Unit,
    private val onBookmarkToggle: (DocumentModel) -> Unit,
    private val onMenuClick: (DocumentModel) -> Unit
) : ListAdapter<DocumentFeedItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_DOCUMENT = 0
        private const val VIEW_TYPE_NATIVE_AD = 1
        private const val AD_FREQUENCY = 6
    }

    /**
     * Injects Native Ad slots into the document list if ads are eligible.
     */
    fun submitDocuments(
        documents: List<DocumentModel>,
        showAds: Boolean,
        commitCallback: (() -> Unit)? = null
    ) {
        val items = mutableListOf<DocumentFeedItem>()
        documents.forEachIndexed { index, doc ->
            items.add(DocumentFeedItem.Document(doc))
            if (showAds && (index + 1) % AD_FREQUENCY == 4 && index < documents.size - 1) {
                items.add(DocumentFeedItem.NativeAd("feed_ad_${index}"))
            }
        }
        submitList(items, commitCallback)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DocumentFeedItem.Document -> VIEW_TYPE_DOCUMENT
            is DocumentFeedItem.NativeAd -> VIEW_TYPE_NATIVE_AD
        }
    }

    inner class DocumentViewHolder(private val binding: ItemDocumentCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DocumentModel) {
            binding.tvDocumentName.text = item.name
            binding.tvDocumentMeta.text = "${FormatUtils.formatDate(item.dateModifiedMs)} • ${item.formattedSize}"
            binding.tvBadgeText.text = item.extension.uppercase()

            // Dynamic folded page badge matching document format
            val badgeDrawable = when (item.format) {
                DocumentFormat.PDF -> R.drawable.ic_doc_page_pdf
                DocumentFormat.WORD -> R.drawable.ic_doc_page_word
                DocumentFormat.EXCEL -> R.drawable.ic_doc_page_excel
                DocumentFormat.PPT -> R.drawable.ic_doc_page_ppt
                DocumentFormat.TXT, DocumentFormat.ALL -> R.drawable.ic_doc_page_pdf
            }
            binding.ivDocPageBadge.setImageResource(badgeDrawable)

            // Star icon toggle: clean vector outline vs gold filled star
            val starRes = if (item.isBookmarked) {
                R.drawable.ic_star_filled
            } else {
                R.drawable.ic_star_outline
            }
            binding.btnBookmark.setImageResource(starRes)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnBookmark.setOnClickListener { onBookmarkToggle(item) }
            binding.btnOptions.setOnClickListener { onMenuClick(item) }
        }
    }

    inner class NativeAdViewHolder(private val binding: ItemNativeAdCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            PdfReaderApp.instance.nativeAdManager.loadIntoContainer(
                container = binding.flFeedNativeAdContainer,
                placement = AdPlacement.NATIVE_HOME_FEED,
                layoutResId = R.layout.layout_native_ad_medium
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_NATIVE_AD) {
            val binding = ItemNativeAdCardBinding.inflate(inflater, parent, false)
            NativeAdViewHolder(binding)
        } else {
            val binding = ItemDocumentCardBinding.inflate(inflater, parent, false)
            DocumentViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DocumentFeedItem.Document -> (holder as DocumentViewHolder).bind(item.model)
            is DocumentFeedItem.NativeAd -> (holder as NativeAdViewHolder).bind()
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<DocumentFeedItem>() {
        override fun areItemsTheSame(old: DocumentFeedItem, new: DocumentFeedItem): Boolean {
            return when {
                old is DocumentFeedItem.Document && new is DocumentFeedItem.Document ->
                    old.model.id == new.model.id || old.model.path.equals(new.model.path, ignoreCase = true)
                old is DocumentFeedItem.NativeAd && new is DocumentFeedItem.NativeAd ->
                    old.id == new.id
                else -> false
            }
        }

        override fun areContentsTheSame(old: DocumentFeedItem, new: DocumentFeedItem): Boolean {
            return old == new
        }
    }
}
