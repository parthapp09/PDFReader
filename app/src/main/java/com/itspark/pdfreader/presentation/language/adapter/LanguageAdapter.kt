package com.itspark.pdfreader.presentation.language.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.R
import com.itspark.pdfreader.databinding.ItemLanguageBinding
import com.itspark.pdfreader.domain.model.LanguageModel

class LanguageAdapter(
    private val onLanguageSelected: (LanguageModel) -> Unit
) : ListAdapter<LanguageModel, LanguageAdapter.LanguageViewHolder>(DiffCallback) {

    private var selectedCode: String = "en_US"

    fun setSelectedCode(code: String) {
        selectedCode = code
        notifyDataSetChanged()
    }

    inner class LanguageViewHolder(private val binding: ItemLanguageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LanguageModel) {
            val isSelected = item.code == selectedCode

            binding.tvFlag.text = item.flagEmoji
            binding.tvLanguageTitle.text = item.title
            binding.tvLanguageSubtitle.text = item.subtitle

            // Card background & Radio button matching screenshot
            if (isSelected) {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_language_card_selected)
                binding.ivRadioSelect.setImageResource(R.drawable.ic_radio_selected)
            } else {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_language_card_unselected)
                binding.ivRadioSelect.setImageResource(R.drawable.ic_radio_unselected)
            }

            binding.root.setOnClickListener {
                selectedCode = item.code
                notifyDataSetChanged()
                onLanguageSelected(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LanguageModel>() {
        override fun areItemsTheSame(old: LanguageModel, new: LanguageModel) = old.code == new.code
        override fun areContentsTheSame(old: LanguageModel, new: LanguageModel) = old == new
    }
}
