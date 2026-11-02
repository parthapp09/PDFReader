package com.itspark.pdfreader.presentation.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.R
import com.itspark.pdfreader.databinding.ItemDocumentCardBinding
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.presentation.utils.FormatUtils

class DocumentAdapter(
    private val onItemClick: (DocumentModel) -> Unit,
    private val onBookmarkToggle: (DocumentModel) -> Unit,
    private val onMenuClick: (DocumentModel) -> Unit
) : ListAdapter<DocumentModel, DocumentAdapter.DocumentViewHolder>(DiffCallback) {

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DocumentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DocumentModel>() {
        override fun areItemsTheSame(old: DocumentModel, new: DocumentModel): Boolean {
            return old.id == new.id || old.path.equals(new.path, ignoreCase = true)
        }
        override fun areContentsTheSame(old: DocumentModel, new: DocumentModel): Boolean {
            return old == new
        }
    }
}
