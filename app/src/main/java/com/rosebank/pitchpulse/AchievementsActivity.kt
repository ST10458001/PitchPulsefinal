package com.rosebank.pitchpulse

import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import com.rosebank.pitchpulse.databinding.ActivityAchievementsBinding

/** Badge grid. Progress rings are bars here so the state is obvious without a canvas. */
class AchievementsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.isReady()) Repository.init(this)
        val binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }
        val predictions = Repository.predictions().size
        val correct = Repository.predictions().count { it.settled && it.points > 0 }
        val favs = Repository.favourites().size
        val streak = Prefs.bestStreak(this)
        val safe = Prefs.safeBetDay(this).isNotEmpty()
        val badges = listOf(
            BadgeUi("First Whistle", "$predictions / 1 prediction", (predictions.coerceAtMost(1)) * 100),
            BadgeUi("Full Time", "$correct correct calls", (correct.coerceAtMost(1)) * 100),
            BadgeUi("Scout", "$favs / 5 favourites", (favs * 100 / 5).coerceAtMost(100)),
            BadgeUi("Nostradamus", "Best streak $streak / 10", (streak * 10).coerceAtMost(100)),
            BadgeUi("Safe Bet", if (safe) "Used today" else "One risk-free call a day", if (safe) 100 else 0),
            BadgeUi("Pulse Pro", if (Prefs.isPro(this)) "Banner removed" else "Optional, never required", if (Prefs.isPro(this)) 100 else 0)
        )
        val adapter = BadgeAdapter().also { it.submit(badges) }
        binding.grid.layoutManager = GridLayoutManager(this, 2)
        binding.grid.adapter = adapter
    }
}
