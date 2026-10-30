package com.itspark.pdfreader.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itspark.pdfreader.data.repository.DocumentRepository
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allFilesCache = listOf<DocumentModel>()
    // Pre-grouped cache for instant O(1) tab switching without re-filtering
    private var filesByFormat = mapOf<DocumentFormat, List<DocumentModel>>()
    private val bookmarkedIds = mutableSetOf<Long>()
    private var selectedFormat = DocumentFormat.ALL
    private var currentSearch = ""

    fun fetchDocuments() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                withContext(Dispatchers.Default) {
                    allFilesCache = repository.getDocuments()
                    filesByFormat = allFilesCache.groupBy { it.format }
                }

                if (allFilesCache.isEmpty()) {
                    _uiState.value = HomeUiState.Empty("No files found on device")
                } else {
                    emitCurrentState()
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.localizedMessage ?: "Failed to scan files")
            }
        }
    }

    fun onTabChanged(format: DocumentFormat) {
        if (selectedFormat == format && currentSearch.isEmpty()) return
        selectedFormat = format
        emitCurrentState()
    }

    fun onSearchQuery(query: String) {
        currentSearch = query.trim()
        emitCurrentState()
    }

    fun toggleBookmark(document: DocumentModel) {
        if (bookmarkedIds.contains(document.id)) {
            bookmarkedIds.remove(document.id)
        } else {
            bookmarkedIds.add(document.id)
        }
        emitCurrentState()
    }

    private fun emitCurrentState() {
        viewModelScope.launch(Dispatchers.Default) {
            val baseList = if (selectedFormat == DocumentFormat.ALL) {
                allFilesCache
            } else {
                filesByFormat[selectedFormat] ?: emptyList()
            }

            val filtered = if (currentSearch.isEmpty()) {
                baseList
            } else {
                baseList.filter { it.name.contains(currentSearch, ignoreCase = true) }
            }

            val mapped = if (bookmarkedIds.isEmpty()) {
                filtered
            } else {
                filtered.map {
                    if (bookmarkedIds.contains(it.id)) it.copy(isBookmarked = true) else it
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.value = HomeUiState.Success(
                    documents = mapped,
                    currentFormat = selectedFormat,
                    searchQuery = currentSearch,
                    isSearching = currentSearch.isNotEmpty()
                )
            }
        }
    }
}