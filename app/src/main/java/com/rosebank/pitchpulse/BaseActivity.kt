package com.rosebank.pitchpulse

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Applies the charcoal or ivory theme, and the saved language, before the window is created. */
open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (Prefs.theme(this) == "ivory") R.style.Theme_PitchPulse_Ivory
            else R.style.Theme_PitchPulse
        )
        super.onCreate(savedInstanceState)
    }
}