package com.itspark.pdfreader.domain.model

enum class DocumentFormat(val title: String, val extensions: List<String>) {
    ALL("ALL", emptyList()),
    PDF("PDF", listOf("pdf")),
    WORD("WORD", listOf("doc", "docx")),
    PPT("PPT", listOf("ppt", "pptx")),
    EXCEL("EXCEL", listOf("xls", "xlsx", "csv")),
    TXT("TXT", listOf("txt"));

    companion object {
        fun fromExtension(ext: String): DocumentFormat {
            val lower = ext.lowercase()
            return entries.firstOrNull { it.extensions.contains(lower) } ?: TXT
        }
    }
}
