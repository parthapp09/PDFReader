package com.itspark.pdfreader.presentation.home

import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val documents: List<DocumentModel>,
        val currentFormat: DocumentFormat,
        val searchQuery: String = "",
        val isSearching: Boolean = false
    ) : HomeUiState
    data class Empty(val message: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
