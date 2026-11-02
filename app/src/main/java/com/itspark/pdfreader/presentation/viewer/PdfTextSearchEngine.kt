package com.itspark.pdfreader.presentation.viewer

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

data class PdfSearchResult(
    val pageIndex: Int,
    val matchRect: RectF,
    val previewSnippet: String = ""
)

/**
 * High-performance PDF text search engine powered by PDFBox-Android (Apache 2.0).
 * Handles all PDF versions, object streams (/ObjStm), font encodings (Type0/CID, TrueType),
 * typographic ligatures (fi, fl, ff), and character-exact bounding box coordinates.
 */
object PdfTextSearchEngine {

    private const val TAG = "PDF_SEARCH"
    @Volatile private var isPdfBoxInitialized = false

    suspend fun search(
        context: Context,
        pdfPath: String,
        pdfUriString: String,
        query: String,
        totalPages: Int
    ): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        Log.d(TAG, "Search query: '$trimmedQuery' | totalPages: $totalPages")

        if (trimmedQuery.length < 2 || totalPages <= 0) {
            return@withContext emptyList()
        }

        try {
            if (!isPdfBoxInitialized) {
                synchronized(this) {
                    if (!isPdfBoxInitialized) {
                        PDFBoxResourceLoader.init(context.applicationContext)
                        isPdfBoxInitialized = true
                    }
                }
            }
            val document = openPdDocument(context, pdfPath, pdfUriString)
            return@withContext try {
                val stripper = KeywordSearchStripper(trimmedQuery)
                stripper.startPage = 1
                stripper.endPage = document.numberOfPages.coerceAtMost(totalPages)
                stripper.getText(document)
                Log.d(TAG, "Search completed. Matches found: ${stripper.results.size}")
                stripper.results
            } finally {
                try {
                    document.close()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during PDF search: ${e.message}", e)
            emptyList()
        }
    }

    private fun openPdDocument(context: Context, path: String, uriString: String): PDDocument {
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return PDDocument.load(file)
            }
        }
        if (uriString.isNotBlank()) {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return PDDocument.load(stream)
            }
        }
        throw IllegalArgumentException("Cannot open document: path and uri are invalid")
    }

    private class KeywordSearchStripper(
        private val query: String
    ) : PDFTextStripper() {

        val results = mutableListOf<PdfSearchResult>()
        private val normalizedQuery = Normalizer.normalize(query.trim().lowercase(), Normalizer.Form.NFKD)

        init {
            sortByPosition = true
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>?) {
            if (!textPositions.isNullOrEmpty()) {
                val lineNormalized = Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase()
                val page = currentPage
                val mediaBox = page?.mediaBox
                val pw = mediaBox?.width ?: 595.28f
                val ph = mediaBox?.height ?: 841.89f
                val pageIdx = (currentPageNo - 1).coerceAtLeast(0)

                // 1. Exact match with decomposed ligatures (e.g. fi -> f + i)
                var startIdx = lineNormalized.indexOf(normalizedQuery)
                while (startIdx != -1) {
                    val endIdx = (startIdx + normalizedQuery.length).coerceAtMost(textPositions.size)
                    if (startIdx < textPositions.size) {
                        val firstPos = textPositions[startIdx]
                        val lastPos = textPositions[(endIdx - 1).coerceAtLeast(startIdx)]

                        val left = (firstPos.xDirAdj / pw).coerceIn(0.005f, 0.985f)
                        val right = ((lastPos.xDirAdj + lastPos.widthDirAdj) / pw).coerceIn(left + 0.005f, 0.995f)
                        val top = ((firstPos.yDirAdj - firstPos.heightDir) / ph).coerceIn(0.005f, 0.985f)
                        val bottom = (firstPos.yDirAdj / ph).coerceIn(top + 0.005f, 0.995f)

                        results.add(
                            PdfSearchResult(
                                pageIndex = pageIdx,
                                matchRect = RectF(left, top, right, bottom),
                                previewSnippet = text.trim()
                            )
                        )
                    }
                    startIdx = lineNormalized.indexOf(normalizedQuery, startIdx + normalizedQuery.length.coerceAtLeast(1))
                }

                // 2. Typo-tolerant matching for words >= 5 chars (e.g. certifcate <-> certificate)
                if (normalizedQuery.length >= 5 && lineNormalized.indexOf(normalizedQuery) == -1) {
                    val words = lineNormalized.split(Regex("[^\\p{L}\\p{Nd}]+"))
                    var charOffset = 0
                    for (w in words) {
                        val wIdx = lineNormalized.indexOf(w, charOffset)
                        if (wIdx != -1) {
                            if (isFuzzyMatch(w, normalizedQuery)) {
                                val endIdx = (wIdx + w.length).coerceAtMost(textPositions.size)
                                if (wIdx < textPositions.size) {
                                    val firstPos = textPositions[wIdx]
                                    val lastPos = textPositions[(endIdx - 1).coerceAtLeast(wIdx)]

                                    val left = (firstPos.xDirAdj / pw).coerceIn(0.005f, 0.985f)
                                    val right = ((lastPos.xDirAdj + lastPos.widthDirAdj) / pw).coerceIn(left + 0.005f, 0.995f)
                                    val top = ((firstPos.yDirAdj - firstPos.heightDir) / ph).coerceIn(0.005f, 0.985f)
                                    val bottom = (firstPos.yDirAdj / ph).coerceIn(top + 0.005f, 0.995f)

                                    results.add(
                                        PdfSearchResult(
                                            pageIndex = pageIdx,
                                            matchRect = RectF(left, top, right, bottom),
                                            previewSnippet = text.trim()
                                        )
                                    )
                                }
                            }
                            charOffset = wIdx + w.length
                        }
                    }
                }
            }
            super.writeString(text, textPositions)
        }
    }

    private fun isFuzzyMatch(w1: String, w2: String): Boolean {
        if (w1 == w2) return true
        if (Math.abs(w1.length - w2.length) > 1) return false
        if (w1.length < 5 || w2.length < 5) return false

        var i = 0
        var j = 0
        var diff = 0
        while (i < w1.length && j < w2.length) {
            if (w1[i] != w2[j]) {
                diff++
                if (diff > 1) return false
                if (w1.length > w2.length) {
                    i++
                    continue
                } else if (w2.length > w1.length) {
                    j++
                    continue
                }
            }
            i++
            j++
        }
        return true
    }
}
