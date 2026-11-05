package com.itspark.pdfreader.presentation.home

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itspark.pdfreader.data.repository.DocumentRepository
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.data.source.DocumentCache
import com.itspark.pdfreader.data.source.SampleFilesManager
import com.itspark.pdfreader.domain.model.DocumentFormat
import com.itspark.pdfreader.domain.model.DocumentModel
import com.itspark.pdfreader.domain.model.NavigationTab
import com.itspark.pdfreader.presentation.home.model.DummyFilesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(
    application: Application,
    private val repository: DocumentRepository
) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var allFilesCache = listOf<DocumentModel>()
    private var filesByFormat = mapOf<DocumentFormat, List<DocumentModel>>()

    // In-memory set synced with SharedPreferences
    private val bookmarkedPaths = mutableSetOf<String>().apply {
        addAll(prefs.getBookmarkedPaths())
    }

    private var selectedFormat = DocumentFormat.ALL
    private var currentNavTab = NavigationTab.HOME
    private var currentSearch = ""
    var isUsingDummyData: Boolean = false
        private set

    /**
     * Loads dummy files for first-time / unpermitted users.
     * Sample files are copied from assets/default_files/ to cacheDir/default_files/
     * so they can be opened safely without storage permissions.
     */
    fun loadDummyDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            isUsingDummyData = true
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
     * @param highlightPath optional path of a newly generated file to ensure it appears at position 0
     * @param forceRefresh whether to bypass in-memory caches and re-query MediaStore
     */
    fun fetchDocuments(highlightPath: String? = null, forceRefresh: Boolean = false) {
        // If an initial scan is already actively running and no specific file was requested,
        // let the active scan finish rather than canceling it and restarting from scratch.
        if (fetchJob?.isActive == true && highlightPath == null) {
            return
        }

        // If files are already loaded in memory and no highlight/refresh requested, keep current data
        if (!forceRefresh && allFilesCache.isNotEmpty() && highlightPath == null) {
            return
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            // 1. Instant Cache Check: If pre-scanned during splash, display immediately (0ms)
            val preloaded = DocumentCache.preloadedDocuments
            if (!preloaded.isNullOrEmpty() && allFilesCache.isEmpty()) {
                isUsingDummyData = false
                allFilesCache = preloaded
                filesByFormat = preloaded.groupBy { it.format }
                emitCurrentState()

                if (highlightPath == null && !forceRefresh) {
                    return@launch
                }
            } else if (allFilesCache.isEmpty()) {
                _uiState.value = HomeUiState.Loading
            }

            try {
                withContext(Dispatchers.Default) {
                    val realFiles = repository.getDocuments().toMutableList()
                    DocumentCache.preloadedDocuments = realFiles
                    if (!highlightPath.isNullOrEmpty()) {
                        val idx = realFiles.indexOfFirst { it.path.equals(highlightPath, ignoreCase = true) }
                        if (idx > 0) {
                            val item = realFiles.removeAt(idx)
                            realFiles.add(0, item)
                        }
                    }
                    if (realFiles.isNotEmpty()) {
                        isUsingDummyData = false
                        allFilesCache = realFiles
                        filesByFormat = realFiles.groupBy { it.format }
                    } else {
                        // Fallback to sample files if device storage has no documents
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

    fun onTabChanged(format: DocumentFormat, force: Boolean = false) {
        if (!force && selectedFormat == format && currentSearch.isEmpty()) return
        selectedFormat = format
        emitCurrentState()
    }

    fun setNavigationTab(tab: NavigationTab) {
        if (currentNavTab == tab) return
        currentNavTab = tab
        emitCurrentState()
    }

    fun onSearchQuery(query: String) {
        currentSearch = query.trim()
        emitCurrentState()
    }

    fun toggleBookmark(document: DocumentModel) {
        val path = document.path
        if (bookmarkedPaths.contains(path)) {
            bookmarkedPaths.remove(path)
            prefs.removeBookmark(path)
        } else {
            bookmarkedPaths.add(path)
            prefs.addBookmark(path)
        }
        emitCurrentState()
    }

    /**
     * Renames a document on disk and updates cache + persisted bookmark paths.
     */
    fun renameDocument(
        document: DocumentModel,
        newNameWithoutExt: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val trimmed = newNameWithoutExt.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "File name cannot be empty")
            return
        }

        val newFullName = "$trimmed.${document.extension}"
        val oldFile = File(document.path)
        val parent = oldFile.parentFile ?: getApplication<Application>().cacheDir
        val newFile = File(parent, newFullName)

        if (newFile.exists() && newFile.absolutePath != oldFile.absolutePath) {
            onResult(false, "A file with this name already exists")
            return
        }

        val renamed = oldFile.renameTo(newFile)
        if (!renamed) {
            onResult(false, "Could not rename file")
            return
        }

        // Notify MediaScanner for real files
        if (document.id >= 0) {
            MediaScannerConnection.scanFile(
                getApplication(),
                arrayOf(oldFile.absolutePath, newFile.absolutePath),
                null,
                null
            )
        }

        // If it was bookmarked, migrate bookmark path
        if (bookmarkedPaths.contains(document.path)) {
            bookmarkedPaths.remove(document.path)
            prefs.removeBookmark(document.path)
            bookmarkedPaths.add(newFile.absolutePath)
            prefs.addBookmark(newFile.absolutePath)
        }

        allFilesCache = allFilesCache.map {
            if (it.id == document.id) {
                it.copy(
                    name = newFullName,
                    path = newFile.absolutePath,
                    uri = if (document.id < 0) Uri.fromFile(newFile) else document.uri
                )
            } else it
        }
        filesByFormat = allFilesCache.groupBy { it.format }
        emitCurrentState()
        onResult(true, null)
    }

    /**
     * Deletes a document from disk and removes it from cache and bookmarks.
     */
    fun deleteDocument(
        document: DocumentModel,
        onResult: (Boolean, String?) -> Unit
    ) {
        val file = File(document.path)
        if (file.exists()) {
            file.delete()
        }

        // Delete from MediaStore ContentResolver if real file
        if (document.id >= 0) {
            try {
                getApplication<Application>().contentResolver.delete(document.uri, null, null)
            } catch (_: Exception) {}
            MediaScannerConnection.scanFile(
                getApplication(),
                arrayOf(document.path),
                null,
                null
            )
        }

        if (bookmarkedPaths.contains(document.path)) {
            bookmarkedPaths.remove(document.path)
            prefs.removeBookmark(document.path)
        }

        allFilesCache = allFilesCache.filter { it.id != document.id }
        filesByFormat = allFilesCache.groupBy { it.format }
        emitCurrentState()
        onResult(true, null)
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

            // Map bookmarks based on persistent path set
            val mapped = filtered.map {
                if (bookmarkedPaths.contains(it.path)) it.copy(isBookmarked = true) else it.copy(isBookmarked = false)
            }

            // If user is on Bookmarks tab, show only bookmarked items
            val finalList = if (currentNavTab == NavigationTab.BOOKMARKS) {
                mapped.filter { it.isBookmarked }
            } else {
                mapped
            }

            withContext(Dispatchers.Main) {
                _uiState.value = HomeUiState.Success(
                    documents = ArrayList(finalList),
                    currentFormat = selectedFormat,
                    navTab = currentNavTab,
                    searchQuery = currentSearch,
                    isSearching = currentSearch.isNotEmpty()
                )
            }
        }
    }
}
