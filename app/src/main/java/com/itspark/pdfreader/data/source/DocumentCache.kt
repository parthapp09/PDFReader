package com.itspark.pdfreader.data.source

import com.itspark.pdfreader.domain.model.DocumentModel

/**
 * Thread-safe in-memory cache populated during splash animation
 * so HomeActivity loads files instantly (0ms) without blocking the UI.
 */
object DocumentCache {
    @Volatile
    var preloadedDocuments: List<DocumentModel>? = null

    fun clear() {
        preloadedDocuments = null
    }
}
