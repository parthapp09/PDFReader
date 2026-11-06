package com.itspark.pdfreader.presentation.splash

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itspark.pdfreader.PdfReaderApp
import com.itspark.pdfreader.analytics.AnalyticsTracker
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.consent.ConsentManager
import com.itspark.pdfreader.data.attribution.InstallAttributionManager
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.data.source.DocumentCache
import com.itspark.pdfreader.data.source.MediaStoreScanner
import com.itspark.pdfreader.databinding.ActivitySplashBinding
import com.itspark.pdfreader.monetization.AdPlacement
import com.itspark.pdfreader.monetization.MonetizationManager
import com.itspark.pdfreader.presentation.home.HomeActivity
import com.itspark.pdfreader.presentation.language.LanguageActivity
import com.itspark.pdfreader.presentation.tools.DynamicShortcutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Splash screen shown every time the app is launched.
 *
 * Attribution & Consent Gatekeeper Flow:
 *   1. Animate progress bar (2.5s)
 *   2. On first launch: Query Play Install Referrer & detect GCLID / UTM parameters
 *   3. Persist attribution classification (ORGANIC, PAID_GOOGLE, PAID_OTHER)
 *   4. Dispatch attribution event and set user properties in Firebase Analytics
 *   5. Resolve Segment-Aware Consent via Google UMP SDK (GDPR / Privacy)
 *   6. Initialize cohort-based MonetizationManager before launching dependent activities
 *   7. Route to LanguageActivity (first launch) or HomeActivity
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    companion object {
        private const val SPLASH_DURATION_MS = 2500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        DynamicShortcutManager.publishDefaultShortcuts(this)
        prewarmDocumentCacheIfPermitted()
        animateProgressBar()
        resolveAttributionAndNavigate()
    }

    private fun prewarmDocumentCacheIfPermitted() {
        if (hasStoragePermission()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val scanner = MediaStoreScanner(applicationContext)
                    DocumentCache.preloadedDocuments = scanner.scanDocuments()
                } catch (_: Exception) {}
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun animateProgressBar() {
        binding.progressBar.progress = 0
        val duration = RemoteConfigManager.getSplashDelayMs().coerceAtLeast(1000L)
        val animator = ObjectAnimator.ofInt(binding.progressBar, "progress", 0, 100)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    /**
     * Resolves attribution signals and configures monetization cohorts before leaving Splash.
     */
    private fun resolveAttributionAndNavigate() {
        val startTime = System.currentTimeMillis()

        lifecycleScope.launch {
            val prefs = PreferencesRepository(this@SplashActivity)

            // 1. Resolve attribution from Google Play on first launch
            if (!prefs.isAttributionResolved) {
                // Maximum 2.0s timeout to leave headroom for splash animation
                val attribution = InstallAttributionManager.resolveAttribution(this@SplashActivity, timeoutMs = 2000L)
                prefs.saveAttribution(attribution)

                // 2. Dispatch attribution event & user segment property to Firebase Analytics
                AnalyticsTracker.logAttributionEvent(this@SplashActivity, attribution)
            } else {
                // Ensure active user property is in sync with persistent classification
                val cached = prefs.getAttributionData()
                AnalyticsTracker.setUserSegment(this@SplashActivity, cached.type, cached.utmSource)
            }

            // 3. Resolve Segment-Aware Consent via Google UMP SDK (GDPR / Privacy)
            val segment = prefs.getAttributionType()
            ConsentManager.resolveConsentAsync(this@SplashActivity, segment, timeoutMs = 2500L)

            // 4. Initialize Cohort Monetization Decision Engine
            MonetizationManager.initialize(this@SplashActivity)

            // Start preloading the cold start ad in parallel while the progress bar animates
            val startupFormat = RemoteConfigManager.getAdStartupFormat().uppercase()
            if (RemoteConfigManager.isAdsGlobalEnabled() && ConsentManager.canRequestAds(this@SplashActivity)) {
                when (startupFormat) {
                    "INTERSTITIAL" -> {
                        PdfReaderApp.instance.interstitialAdManager.preload(AdPlacement.INTERSTITIAL_OPEN_DOC)
                    }
                    "APP_OPEN" -> {
                        if (MonetizationManager.shouldShowAppOpenAd(this@SplashActivity)) {
                            PdfReaderApp.instance.appOpenAdManager.preloadColdStartAd(this@SplashActivity)
                        }
                    }
                }
            }

            // 5. Ensure splash progress bar animation completes smoothly
            val targetDuration = RemoteConfigManager.getSplashDelayMs().coerceAtLeast(1000L)
            val elapsed = System.currentTimeMillis() - startTime
            val remainingDelay = (targetDuration - elapsed).coerceAtLeast(0L)
            if (remainingDelay > 0) {
                delay(remainingDelay.milliseconds)
            }

            if (isFinishing || isDestroyed) return@launch

            fun proceedToNextScreen() {
                if (isFinishing || isDestroyed) return
                val next = if (prefs.isFirstLaunch) {
                    Intent(this@SplashActivity, LanguageActivity::class.java)
                } else {
                    Intent(this@SplashActivity, HomeActivity::class.java).apply {
                        if (intent.component?.className?.contains("Scanner", ignoreCase = true) == true) {
                            putExtra("navigate_to", "tools")
                        }
                    }
                }
                startActivity(next)
                if (Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
                finish()
            }

            // 6. Cold Start Ad Presentation (App Open vs Interstitial vs None)
            if (RemoteConfigManager.isAdsGlobalEnabled() && ConsentManager.canRequestAds(this@SplashActivity)) {
                when (startupFormat) {
                    "INTERSTITIAL" -> {
                        PdfReaderApp.instance.interstitialAdManager.showIfAllowed(
                            activity = this@SplashActivity,
                            placement = AdPlacement.INTERSTITIAL_OPEN_DOC,
                            onAdClosed = { proceedToNextScreen() }
                        )
                    }
                    "APP_OPEN" -> {
                        if (MonetizationManager.shouldShowAppOpenAd(this@SplashActivity)) {
                            PdfReaderApp.instance.appOpenAdManager.loadAndShowColdStart(
                                activity = this@SplashActivity,
                                onComplete = { proceedToNextScreen() }
                            )
                        } else {
                            proceedToNextScreen()
                        }
                    }
                    else -> {
                        proceedToNextScreen()
                    }
                }
            } else {
                proceedToNextScreen()
            }
        }
    }
}
