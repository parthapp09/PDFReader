package com.itspark.pdfreader.presentation.tools

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.itspark.pdfreader.databinding.ItemPdfToolBinding
import com.itspark.pdfreader.presentation.tools.PdfToolItem

class PdfToolsAdapter(
    private val tools: List<PdfToolItem>,
    private val onToolClick: (PdfToolItem) -> Unit
) : RecyclerView.Adapter<PdfToolsAdapter.ToolViewHolder>() {

    inner class ToolViewHolder(private val binding: ItemPdfToolBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.cardTool.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position in tools.indices) {
                    onToolClick(tools[position])
                }
            }
        }

        fun bind(item: PdfToolItem) {
            binding.tvToolTitle.text = item.title
            binding.ivToolIcon.setImageResource(item.iconRes)
            ViewCompat.setBackgroundTintList(binding.layoutIconCircle, ColorStateList.valueOf(item.circleColor))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemPdfToolBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position])
    }

    override fun getItemCount(): Int = tools.size
}
