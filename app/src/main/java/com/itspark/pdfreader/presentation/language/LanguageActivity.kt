package com.itspark.pdfreader.presentation.language

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.itspark.pdfreader.PdfReaderApp
import com.itspark.pdfreader.R
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.databinding.ActivityLanguageBinding
import com.itspark.pdfreader.domain.model.LanguageModel
import com.itspark.pdfreader.monetization.AdPlacement
import com.itspark.pdfreader.presentation.home.HomeActivity
import com.itspark.pdfreader.presentation.language.adapter.LanguageAdapter

class LanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var adapter: LanguageAdapter
    private lateinit var prefs: PreferencesRepository

    private val allLanguages = listOf(
        LanguageModel("en_US", "English (US)", "English (US)", "🇺🇸"),
        LanguageModel("en_GB", "English (UK)", "English (UK)", "🇬🇧"),
        LanguageModel("en_AU", "English (Australia)", "English (Australia)", "🇦🇺"),
        LanguageModel("en_IN", "English (India)", "English (India)", "🇮🇳"),
        LanguageModel("pt_PT", "Português", "Portuguese (Portugal)", "🇵🇹"),
        LanguageModel("pt_BR", "Português (Brasil)", "Portuguese (Brazil)", "🇧🇷"),
        LanguageModel("es_ES", "Español", "Spanish", "🇪🇸"),
        LanguageModel("fr_FR", "Français", "French", "🇫🇷"),
        LanguageModel("de_DE", "Deutsch", "German", "🇩🇪"),
        LanguageModel("it_IT", "Italiano", "Italian", "🇮🇹"),
        LanguageModel("hi_IN", "हिन्दी", "Hindi", "🇮🇳")
    )

    private var selectedCode = "en_US"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesRepository(this)
        selectedCode = prefs.selectedLanguageCode

        setupRecyclerView()
        setupSearch()
        setupDoneButton()
        setupBottomNativeAd()

        // Preload Interstitial for transition
        PdfReaderApp.instance.interstitialAdManager.preload(AdPlacement.INTERSTITIAL_LANGUAGE_DONE)
    }

    private fun setupBottomNativeAd() {
        // Dynamic ad format: Medium native on first launch onboarding; large native on settings
        val layoutRes = if (prefs.isFirstLaunch) {
            R.layout.layout_native_ad_medium
        } else {
            R.layout.layout_native_ad_large
        }

        PdfReaderApp.instance.nativeAdManager.loadIntoContainer(
            container = binding.layoutBottomAdContainer,
            placement = AdPlacement.NATIVE_LANGUAGE_ONBOARDING,
            layoutResId = layoutRes
        )
    }

    private fun setupRecyclerView() {
        adapter = LanguageAdapter { selected ->
            selectedCode = selected.code
        }
        binding.rvLanguages.adapter = adapter
        adapter.setSelectedCode(selectedCode)
        adapter.submitList(allLanguages)
    }

    private fun setupSearch() {
        binding.etSearchLanguage.addTextChangedListener { text ->
            val query = text?.toString().orEmpty().trim()
            val filtered = if (query.isEmpty()) {
                allLanguages
            } else {
                allLanguages.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true)
                }
            }
            adapter.submitList(filtered)
        }
    }

    private fun setupDoneButton() {
        binding.btnDone.setOnClickListener {
            // Save selected language and mark onboarding as complete
            prefs.selectedLanguageCode = selectedCode
            prefs.isFirstLaunch = false

            fun navigateToHome() {
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
                finish()
            }

            // Show Interstitial on transition if allowed by cohort cooldown & mutex
            PdfReaderApp.instance.interstitialAdManager.showIfAllowed(
                activity = this,
                placement = AdPlacement.INTERSTITIAL_LANGUAGE_DONE,
                onAdClosed = { navigateToHome() }
            )
        }
    }
}
