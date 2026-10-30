package com.itspark.pdfreader.data.repository

import com.itspark.pdfreader.data.source.MediaStoreScanner
import com.itspark.pdfreader.domain.model.DocumentModel

interface DocumentRepository {
    suspend fun getDocuments(): List<DocumentModel>
}

class DocumentRepositoryImpl(
    private val scanner: MediaStoreScanner
) : DocumentRepository {
    override suspend fun getDocuments(): List<DocumentModel> = scanner.scanDocuments()
}
