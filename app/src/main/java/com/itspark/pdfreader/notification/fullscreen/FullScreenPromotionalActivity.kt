package com.itspark.pdfreader.notification.fullscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.itspark.pdfreader.config.RemoteConfigManager
import com.itspark.pdfreader.databinding.ActivityFullscreenPromotionalBinding
import com.itspark.pdfreader.presentation.home.HomeActivity
import java.util.Locale

/**
 * Full-screen promotional / warning interface triggered by high-priority full-screen intents.
 * Bypasses the lock screen (where permitted) to present urgent alerts, storage warnings,
 * or promotional offers to capture user attention.
 */
class FullScreenPromotionalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenPromotionalBinding
    private var countDownTimer: CountDownTimer? = null

    companion object {
        const val EXTRA_TITLE = "extra_promo_title"
        const val EXTRA_BODY = "extra_promo_body"
        const val EXTRA_BADGE = "extra_promo_badge"
        const val EXTRA_ACTION_TEXT = "extra_promo_action"
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_COUNTDOWN_SECONDS = "extra_countdown_seconds"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RemoteConfigManager.isSelfLaunchOnLockEnabled()) {
            wakeAndUnlockScreen()
        }

        binding = ActivityFullscreenPromotionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindDataFromIntent()
        startCountdown()
        setupListeners()
    }

    private fun wakeAndUnlockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun bindDataFromIntent() {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Storage & Document Alert"
        val body = intent.getStringExtra(EXTRA_BODY)
            ?: "Unindexed document fragments detected. Tap to inspect and protect your workspace."
        val badge = intent.getStringExtra(EXTRA_BADGE) ?: "⚠️ URGENT ACTION REQUIRED"
        val actionText = intent.getStringExtra(EXTRA_ACTION_TEXT) ?: "Scan & Secure Workspace"

        binding.tvPromoTitle.text = title
        binding.tvPromoBody.text = body
        binding.tvUrgencyBadge.text = badge
        binding.btnPrimaryAction.text = actionText
    }

    private fun startCountdown() {
        // Countdown from RemoteConfig (customizable via EXTRA_COUNTDOWN_SECONDS)
        val defaultSeconds = RemoteConfigManager.getCountdownSeconds()
        val countdownSeconds = intent.getIntExtra(EXTRA_COUNTDOWN_SECONDS, defaultSeconds)
        val totalMillis = countdownSeconds * 1000L

        countDownTimer = object : CountDownTimer(totalMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvCountdownTimer.text = String.format(Locale.getDefault(), "Automatic cleanup in %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.tvCountdownTimer.text = "⚠️ Urgency window expired"
                binding.btnPrimaryAction.text = "Session Expired - Tap to Dismiss"
                binding.btnPrimaryAction.setOnClickListener { finish() }
            }
        }.start()
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnSecondaryDismiss.setOnClickListener {
            finish()
        }

        binding.btnPrimaryAction.setOnClickListener {
            val targetTab = intent.getStringExtra(EXTRA_TARGET_TAB) ?: "home"
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                putExtra("navigate_to", targetTab)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
