package com.itspark.pdfreader.domain.model

data class LanguageModel(
    val code: String,
    val title: String,
    val subtitle: String,
    val flagEmoji: String,
    val isSelected: Boolean = false
)
