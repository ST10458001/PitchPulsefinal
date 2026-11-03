package com.rosebank.pitchpulse

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.rosebank.pitchpulse.databinding.ActivitySettingsBinding

/** FR-007 toggles, language, alerts, and the single Pulse Pro upsell. */
class SettingsActivity : BaseActivity() {
    private val tag = "PP-Settings"
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Notifier.push(this, "PitchPulse", "Goal alerts are on")
            Toast.makeText(this, "Alert sent", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications stay in the app until you allow them", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.isReady()) Repository.init(this)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.goals.isChecked = Prefs.notif(this, "goals")
        binding.cards.isChecked = Prefs.notif(this, "cards")
        binding.fullTime.isChecked = Prefs.notif(this, "ft")
        binding.predictionAlerts.isChecked = Prefs.notif(this, "preds")
        binding.goals.setOnCheckedChangeListener { _, v -> Prefs.setNotif(this, "goals", v) }
        binding.cards.setOnCheckedChangeListener { _, v -> Prefs.setNotif(this, "cards", v) }
        binding.fullTime.setOnCheckedChangeListener { _, v -> Prefs.setNotif(this, "ft", v) }
        binding.predictionAlerts.setOnCheckedChangeListener { _, v -> Prefs.setNotif(this, "preds", v) }
        if (Prefs.theme(this) == "ivory") binding.themeIvory.isChecked = true else binding.themeCharcoal.isChecked = true
        var ignoreTheme = false
        binding.themes.setOnCheckedChangeListener { _, id ->
            if (ignoreTheme) return@setOnCheckedChangeListener
            if (id == R.id.themeIvory && Prefs.points(this) < 15) {
                ignoreTheme = true
                binding.themeCharcoal.isChecked = true
                ignoreTheme = false
                Toast.makeText(this, "Ivory unlocks at 15 Pulse Points", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            Prefs.setTheme(this, if (id == R.id.themeIvory) "ivory" else "charcoal")
            recreate()
        }
        when (Prefs.language(this)) {
            "af" -> binding.langAf.isChecked = true
            "zu" -> binding.langZu.isChecked = true
            else -> binding.langEn.isChecked = true
        }
        var ignoreLang = false
        binding.languages.setOnCheckedChangeListener { _, id ->
            if (ignoreLang) return@setOnCheckedChangeListener
            val code = when (id) {
                R.id.langAf -> "af"
                R.id.langZu -> "zu"
                else -> "en"
            }
            if (code == Prefs.language(this)) return@setOnCheckedChangeListener
            Log.i(tag, "Language changed to $code")
            Prefs.setLanguage(this, code)
            recreate()
        }
        binding.testAlert.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && !Notifier.allowed(this)) {
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Notifier.push(this, "PitchPulse", "Goal alerts are on")
                Toast.makeText(this, "Alert sent", Toast.LENGTH_SHORT).show()
            }
        }
        binding.clearCache.setOnClickListener {
            Repository.clearCache()
            Toast.makeText(this, "Offline cache rebuilt", Toast.LENGTH_SHORT).show()
        }
        fun paintPro() {
            binding.pulsePro.text = if (Prefs.isPro(this)) "Pulse Pro is on — banner hidden" else "Pulse Pro — remove the banner"
        }
        paintPro()
        binding.pulsePro.setOnClickListener {
            Prefs.setPro(this, !Prefs.isPro(this))
            paintPro()
        }
    }
}