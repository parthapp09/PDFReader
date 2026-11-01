package com.itspark.pdfreader.data.repository

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var selectedLanguageCode: String
        get() = prefs.getString(KEY_LANGUAGE_CODE, "en_US") ?: "en_US"
        set(value) = prefs.edit().putString(KEY_LANGUAGE_CODE, value).apply()

    /**
     * Set of bookmarked file paths.
     * Persisting file paths ensures bookmarks survive app restarts,
     * re-indexes, and device reboots.
     */
    fun getBookmarkedPaths(): Set<String> {
        return prefs.getStringSet(KEY_BOOKMARKED_PATHS, emptySet())?.toSet() ?: emptySet()
    }

    fun addBookmark(path: String) {
        val updated = getBookmarkedPaths().toMutableSet()
        if (updated.add(path)) {
            prefs.edit().putStringSet(KEY_BOOKMARKED_PATHS, updated).apply()
        }
    }

    fun removeBookmark(path: String) {
        val updated = getBookmarkedPaths().toMutableSet()
        if (updated.remove(path)) {
            prefs.edit().putStringSet(KEY_BOOKMARKED_PATHS, updated).apply()
        }
    }

    fun isBookmarked(path: String): Boolean {
        return getBookmarkedPaths().contains(path)
    }

    companion object {
        private const val KEY_FIRST_LAUNCH = "key_is_first_launch"
        private const val KEY_LANGUAGE_CODE = "key_language_code"
        private const val KEY_BOOKMARKED_PATHS = "key_bookmarked_paths"
    }
}
