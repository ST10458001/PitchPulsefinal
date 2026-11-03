package com.rosebank.pitchpulse

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun wrap(context: Context): Context {
        val code = Prefs.language(context)
        val locale = when (code) {
            "af" -> Locale.forLanguageTag("af")
            "zu" -> Locale.forLanguageTag("zu")
            else -> Locale.ENGLISH
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
