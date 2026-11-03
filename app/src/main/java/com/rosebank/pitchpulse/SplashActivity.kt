package com.rosebank.pitchpulse

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.rosebank.pitchpulse.databinding.ActivitySplashBinding

/** Brand cold start. Seeds Room, then hands off to onboarding, login, or home. */
class SplashActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val go = Runnable {
        Repository.init(this)
        val next = when {
            !Prefs.isOnboarded(this) -> OnboardingActivity::class.java
            !Prefs.isLoggedIn(this) -> LoginActivity::class.java
            else -> MainActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.version.text = getString(R.string.version_label)
        handler.postDelayed(go, 1600)
    }

    override fun onDestroy() {
        handler.removeCallbacks(go)
        super.onDestroy()
    }
}
