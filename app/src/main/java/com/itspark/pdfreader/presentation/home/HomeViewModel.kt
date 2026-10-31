package com.itspark.pdfreader.presentation.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itspark.pdfreader.data.repository.DocumentRepository
import com.itspark.pdfreader.data.source.SampleFilesManager
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.presentation.home.model.DummyFilesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    application: Application,
    private val repository: DocumentRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allFilesCache = listOf<DocumentModel>()
    private var filesByFormat = mapOf<DocumentFormat, List<DocumentModel>>()
    private val bookmarkedIds = mutableSetOf<Long>()
    private var selectedFormat = DocumentFormat.ALL
    private var currentSearch = ""
    var isUsingDummyData: Boolean = false
        private set

    /**
     * Loads dummy files for first-time / unpermitted users.
     * SampleFilesManager creates real files in internal storage (no permission needed),
     * so tapping them will actually open a valid document.
     */
    fun loadDummyDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            isUsingDummyData = true
            // Generate real sample files in filesDir/samples/ (runs on IO thread)
            val sampleFiles = SampleFilesManager.ensureSamplesExist(getApplication())
            val dummies = DummyFilesProvider.getDummyDocuments(sampleFiles)
            allFilesCache = dummies
            filesByFormat = dummies.groupBy { it.format }
            withContext(Dispatchers.Main) {
                emitCurrentState()
            }
        }
    }

    /**
     * Scans real storage when permission is granted.
     */
    fun fetchDocuments() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                withContext(Dispatchers.Default) {
                    val realFiles = repository.getDocuments()
                    if (realFiles.isNotEmpty()) {
                        isUsingDummyData = false
                        allFilesCache = realFiles
                        filesByFormat = realFiles.groupBy { it.format }
                    } else {
                        // No real files found — fall back to sample files
                        isUsingDummyData = true
                        val sampleFiles = SampleFilesManager.ensureSamplesExist(getApplication())
                        allFilesCache = DummyFilesProvider.getDummyDocuments(sampleFiles)
                        filesByFormat = allFilesCache.groupBy { it.format }
                    }
                }
                emitCurrentState()
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
