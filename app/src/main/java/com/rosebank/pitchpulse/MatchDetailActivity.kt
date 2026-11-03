package com.rosebank.pitchpulse

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rosebank.pitchpulse.databinding.ActivityMatchDetailBinding

/** FR-004. Overview, stat cards and lineups for one fixture. */
class MatchDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityMatchDetailBinding
    private lateinit var matchId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.isReady()) Repository.init(this)
        matchId = intent.getStringExtra("id").orEmpty()
        binding = ActivityMatchDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
        binding.overview.simulate.setOnClickListener {
            Repository.simulateFullTime(this, matchId)
            Toast.makeText(this, "Full time — points updated if you had a prediction", Toast.LENGTH_SHORT).show()
            bind()
        }
        listOf("possession" to binding.stats.cardPossession, "shots" to binding.stats.cardShots, "xg" to binding.stats.cardXg, "momentum" to binding.stats.cardMomentum)
            .forEach { (card, view) ->
                view.setOnClickListener {
                    startActivity(Intent(this, StatsActivity::class.java).putExtra("id", matchId).putExtra("card", card))
                }
                view.contentDescription = "Open $card stat card"
            }
        bind()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) bind()
    }

    private fun show(index: Int) {
        binding.overview.root.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.stats.root.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.lineups.root.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun bind() {
        val match = Repository.match(matchId) ?: return finish()
        binding.toolbar.title = match.league
        binding.league.text = "${match.sport} · ${match.league}"
        binding.scoreLine.text = "${match.home}  ${match.homeScore} – ${match.awayScore}  ${match.away}"
        binding.clock.text = match.clock
        binding.overview.pulse.text = match.pulse
        binding.overview.simulate.visibility = if (match.status == "FT") View.GONE else View.VISIBLE
        binding.stats.possessionValue.text = "${match.possessionHome}% – ${100 - match.possessionHome}%"
        binding.stats.possessionDetail.text = "${match.home} share of the ball"
        binding.stats.shotsValue.text = "${match.shotsHome} – ${match.shotsAway}"
        binding.stats.shotsDetail.text = "On target ${match.sotHome} – ${match.sotAway}"
        binding.stats.xgValue.text = "${"%.2f".format(match.xgHome)} – ${"%.2f".format(match.xgAway)}"
        binding.stats.xgDetail.text = "Expected goals, home first"
        binding.stats.momentumValue.text = match.momentum.split(",").joinToString("  ")
        binding.overview.timeline.removeAllViews()
        Repository.events(match.id).forEach { event ->
            val line = TextView(this).apply {
                text = "${event.minute}   ${event.text}"
                setTextColor(ink())
                textSize = 15f
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            binding.overview.timeline.addView(line, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        binding.lineups.homeXi.removeAllViews()
        binding.lineups.awayXi.removeAllViews()
        column(binding.lineups.homeXi, match.home, Repository.lineup(match.home))
        column(binding.lineups.awayXi, match.away, Repository.lineup(match.away))
    }

    private fun ink(): Int {
        val typed = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typed, true)
        return if (typed.resourceId != 0) getColor(typed.resourceId) else typed.data
    }

    private fun column(host: LinearLayout, title: String, names: List<String>) {
        host.addView(TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.cyan))
            textSize = 14f
        })
        names.forEachIndexed { index, name ->
            host.addView(TextView(this).apply {
                text = "${index + 1}  $name"
                setTextColor(ink())
                textSize = 14f
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 8, 0)
            })
        }
    }
}
