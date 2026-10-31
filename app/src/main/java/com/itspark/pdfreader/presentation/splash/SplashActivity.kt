package com.itspark.pdfreader.presentation.splash

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.itspark.pdfreader.data.repository.PreferencesRepository
import com.itspark.pdfreader.databinding.ActivitySplashBinding
import com.itspark.pdfreader.presentation.home.HomeActivity
import com.itspark.pdfreader.presentation.language.LanguageActivity

/**
 * Splash screen shown every time the app is launched.
 *
 * Flow:
 *   SplashActivity (2.5s)
 *       ↓ isFirstLaunch == true   → LanguageActivity
 *       ↓ isFirstLaunch == false  → HomeActivity
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val SPLASH_DURATION_MS = 2500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide the action bar if present
        supportActionBar?.hide()

        animateProgressBar()
        scheduleNavigation()
    }

    /**
     * Smoothly animates the progress bar from 0 → 100 over the splash duration.
     */
    private fun animateProgressBar() {
        binding.progressBar.progress = 0
        val animator = ObjectAnimator.ofInt(binding.progressBar, "progress", 0, 100)
        animator.duration = SPLASH_DURATION_MS
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    /**
     * After the splash duration, navigate to the appropriate next screen.
     */
    private fun scheduleNavigation() {
        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            val prefs = PreferencesRepository(this)
            val next = if (prefs.isFirstLaunch) {
                Intent(this, LanguageActivity::class.java)
            } else {
                Intent(this, HomeActivity::class.java)
            }

            startActivity(next)
            finish() // remove splash from back stack
        }, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // prevent leaks
    }
}
