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

    companion object {
        private const val KEY_FIRST_LAUNCH = "key_is_first_launch"
        private const val KEY_LANGUAGE_CODE = "key_language_code"
    }
}
