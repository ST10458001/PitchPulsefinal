package com.rosebank.pitchpulse

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.rosebank.pitchpulse.databinding.ActivityMainBinding
import com.rosebank.pitchpulse.databinding.DialogPredictBinding
import com.rosebank.pitchpulse.databinding.ViewFavouritesBinding
import com.rosebank.pitchpulse.databinding.ViewHomeBinding
import com.rosebank.pitchpulse.databinding.ViewPredictionsBinding
import com.rosebank.pitchpulse.databinding.ViewProfileBinding
import com.rosebank.pitchpulse.databinding.ViewSearchBinding

/**
 * Home shell. XML lives in res/layout; this activity owns the behaviour:
 * live scores, Discover, favourites, the Prediction League, search and profile.
 *
 * Bottom navigation swaps those layouts inside [ActivityMainBinding.content].
 * Match detail, stats, settings and badges are their own activities.
 *
 * FR-002 poll is 30 seconds. FR-003 favourites cap at 50. FR-006 locks predictions
 * five minutes before kick-off. Guests cannot enter the league (FR-001).
 */
class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@registerForActivityResult
        val clipped = if (bytes.size > 400_000) bytes.copyOf(400_000) else bytes
        BlobStore.save(this, "avatar.jpg", clipped)
        Toast.makeText(this, "Profile photo saved to blob storage", Toast.LENGTH_SHORT).show()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var sport = "All"
    private var discover = false
    private var favKind = "team"
    private var showBoard = false
    private var home: ViewHomeBinding? = null
    private var favs: ViewFavouritesBinding? = null
    private var preds: ViewPredictionsBinding? = null
    private var search: ViewSearchBinding? = null
    private var matchAdapter: MatchAdapter? = null
    private val ticker = object : Runnable {
        override fun run() {
            val pulse = Repository.tick(this@MainActivity)
            if (pulse != null && Prefs.notif(this@MainActivity, "goals")) {
                home?.pulseStrip?.text = pulse
                Toast.makeText(this@MainActivity, pulse, Toast.LENGTH_SHORT).show()
                Notifier.push(this@MainActivity, "PitchPulse", pulse)
            }
            refreshHome()
            handler.postDelayed(this, 30_000)
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                binding.offlineBanner.visibility = View.GONE
                Repository.flushQueue(this@MainActivity)
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread { binding.offlineBanner.visibility = View.VISIBLE }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.isReady()) Repository.init(this)
        if (!Prefs.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_favourites -> showFavourites()
                R.id.nav_predictions -> showPredictions()
                R.id.nav_search -> showSearch()
                R.id.nav_profile -> showProfile()
                else -> showHome()
            }
            true
        }
        val tab = savedInstanceState?.getInt("tab") ?: R.id.nav_home
        binding.bottomNav.selectedItemId = tab
        if (binding.content.childCount == 0) showHome()
        if (binding.offlineBanner.visibility != View.VISIBLE && !Repository.isOnline(this)) {
            binding.offlineBanner.visibility = View.VISIBLE
        }
        handler.postDelayed(ticker, 30_000)
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        refreshHome()
    }

    override fun onStop() {
        if (::binding.isInitialized) {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        }
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) outState.putInt("tab", binding.bottomNav.selectedItemId)
    }

    private fun updateBanner(critical: Boolean) {
        val show = !critical && !Prefs.isPro(this)
        binding.adBanner.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showHome() {
        updateBanner(false)
        val page = ViewHomeBinding.inflate(layoutInflater, binding.content, false)
        binding.content.removeAllViews()
        binding.content.addView(page.root)
        home = page
        favs = null
        preds = null
        search = null
        matchAdapter = MatchAdapter(
            onOpen = { openMatch(it.id) },
            onStar = { starMatch(it) },
            starred = { Repository.isStarred("match", it) }
        )
        page.list.layoutManager = LinearLayoutManager(this)
        page.list.adapter = matchAdapter
        page.sportChips.setOnCheckedStateChangeListener { _, checked ->
            sport = when (checked.firstOrNull()) {
                R.id.chipFootball -> "Football"
                R.id.chipBasketball -> "Basketball"
                R.id.chipTennis -> "Tennis"
                R.id.chipCricket -> "Cricket"
                else -> "All"
            }
            refreshHome()
        }
        page.feedTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                discover = tab.position == 1
                refreshHome()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        page.refresh.setOnRefreshListener {
            val pulse = Repository.tick(this)
            if (pulse != null) page.pulseStrip.text = pulse
            page.refresh.isRefreshing = false
            refreshHome()
        }
        val live = Repository.matches("All").firstOrNull { it.status == "LIVE" }
        page.pulseStrip.text = live?.pulse ?: "Pull to refresh. Scores poll every 30 seconds."
        refreshHome()
    }

    private fun refreshHome() {
        val page = home ?: return
        val adapter = matchAdapter ?: return
        if (discover) {
            if (page.list.adapter !is NewsAdapter) {
                page.list.adapter = NewsAdapter { article ->
                    CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(article.url))
                }
            }
            (page.list.adapter as NewsAdapter).submit(Repository.news())
            page.empty.visibility = View.GONE
        } else {
            if (page.list.adapter !== adapter) page.list.adapter = adapter
            val rows = Repository.matches(sport)
            adapter.submit(rows)
            page.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun starMatch(match: MatchEntity) {
        val result = Repository.toggleFavourite("match", match.id, "${match.home} vs ${match.away}")
        toastFavourite(result)
        refreshHome()
    }

    private fun toastFavourite(result: String) {
        val msg = when (result) {
            "limit" -> "50 favourites is the cap, so alerts stay useful"
            "on" -> "Added to favourites"
            else -> "Removed from favourites"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showFavourites() {
        updateBanner(false)
        val page = ViewFavouritesBinding.inflate(layoutInflater, binding.content, false)
        binding.content.removeAllViews()
        binding.content.addView(page.root)
        home = null
        favs = page
        val adapter = FavouriteAdapter(
            onMove = {
                Repository.moveFavourite(it.kind, it.entityId, true)
                bindFavourites()
            },
            onRemove = {
                Repository.toggleFavourite(it.kind, it.entityId, it.label)
                bindFavourites()
            }
        )
        page.list.layoutManager = LinearLayoutManager(this)
        page.list.adapter = adapter
        page.segments.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            favKind = when (checkedId) {
                R.id.segLeagues -> "league"
                R.id.segMatches -> "match"
                else -> "team"
            }
            bindFavourites()
        }
        bindFavourites()
    }

    private fun bindFavourites() {
        val page = favs ?: return
        val rows = Repository.favourites(favKind)
        (page.list.adapter as? FavouriteAdapter)?.submit(rows)
        page.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showPredictions() {
        updateBanner(true)
        val page = ViewPredictionsBinding.inflate(layoutInflater, binding.content, false)
        binding.content.removeAllViews()
        binding.content.addView(page.root)
        home = null
        preds = page
        page.pointsHeader.text = "${Prefs.points(this)} Pulse Points · streak ${Prefs.streak(this)}"
        page.list.layoutManager = LinearLayoutManager(this)
        page.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showBoard = tab.position == 1
                bindPredictions()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        bindPredictions()
    }

    private fun bindPredictions() {
        val page = preds ?: return
        page.pointsHeader.text = "${Prefs.points(this)} Pulse Points · streak ${Prefs.streak(this)}"
        if (showBoard) {
            page.list.adapter = LeaderboardAdapter().also { it.submit(Repository.leaderboard(this)) }
        } else {
            page.list.adapter = PredictionAdapter { askPrediction(it) }
                .also { it.submit(Repository.matches("All")) }
        }
    }

    private fun askPrediction(match: MatchEntity) {
        if (Prefs.mode(this) == "guest") {
            Toast.makeText(this, "Create a permanent account before the Prediction League", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java).putExtra("upgrade", true))
            return
        }
        val dialogBinding = DialogPredictBinding.inflate(layoutInflater)
        dialogBinding.title.text = "${match.home} vs ${match.away}"
        dialogBinding.pickHome.text = match.homeCode
        dialogBinding.pickAway.text = match.awayCode
        if (match.sport != "Football") dialogBinding.pickDraw.visibility = View.GONE
        dialogBinding.pickHome.isChecked = true
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.submit_prediction) { _, _ ->
                val pick = when {
                    dialogBinding.pickAway.isChecked -> "AWAY"
                    dialogBinding.pickDraw.isChecked -> "DRAW"
                    else -> "HOME"
                }
                val message = Repository.submitPrediction(
                    this,
                    PredictionEntity(
                        matchId = match.id,
                        resultPick = pick,
                        scorePick = dialogBinding.scoreInput.text?.toString().orEmpty(),
                        scorerPick = dialogBinding.scorerInput.text?.toString().orEmpty(),
                        safeBet = dialogBinding.safeBet.isChecked,
                        queued = false,
                        points = 0,
                        settled = false
                    )
                )
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                bindPredictions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearch() {
        updateBanner(false)
        val page = ViewSearchBinding.inflate(layoutInflater, binding.content, false)
        binding.content.removeAllViews()
        binding.content.addView(page.root)
        home = null
        search = page
        val adapter = SearchAdapter(
            onStar = { hit ->
                val result = Repository.toggleFavourite(hit.kind, hit.id, hit.label)
                toastFavourite(result)
               // adapter.submit(adapterItems(page))
            },
            starred = { Repository.isStarred(it.kind, it.id) }
        )
        page.list.layoutManager = LinearLayoutManager(this)
        page.list.adapter = adapter
        val debounced = Runnable {
            val query = page.query.text?.toString()?.trim().orEmpty()
            if (query.length >= 2) Prefs.pushRecent(this, query)
            page.section.text = if (query.isBlank()) "Trending teams" else "Results"
            adapter.submit(Repository.search(query))
        }
        page.query.addTextChangedListener(SimpleWatcher {
            handler.removeCallbacks(debounced)
            handler.postDelayed(debounced, 300)
        })
        adapter.submit(Repository.search(""))
        val remote = Runnable {
            val query = page.query.text?.toString()?.trim().orEmpty()
            if (query.length < 3 || !Repository.isOnline(this)) return@Runnable
            Thread {
                try {
                    val hits = Api.searchTeams(this, query)
                    val merged = (Repository.search(query) + hits).distinctBy { it.kind + it.id }
                    runOnUiThread {
                        if (page.query.text?.toString()?.trim() == query) {
                            adapter.submit(merged)
                            if (hits.isNotEmpty()) page.section.text = if (Api.baseUrl(this).isEmpty()) "Results · TheSportsDB" else "Results · PitchPulse API"
                        }
                    }
                } catch (_: Exception) {
                    // Local results stay on screen if the API is unreachable.
                }
            }.start()
        }
        page.query.addTextChangedListener(SimpleWatcher {
            handler.removeCallbacks(remote)
            handler.postDelayed(remote, 400)
        })
    }

    private fun adapterItems(page: ViewSearchBinding) =
        Repository.search(page.query.text?.toString()?.trim().orEmpty())

    private fun showProfile() {
        updateBanner(false)
        val page = ViewProfileBinding.inflate(layoutInflater, binding.content, false)
        binding.content.removeAllViews()
        binding.content.addView(page.root)
        home = null
        val name = Prefs.name(this)
        page.avatar.text = name.take(1).uppercase()
        page.name.text = name
        page.email.text = when (Prefs.mode(this)) {
            "guest" -> "Guest · progress will not stick to a league profile"
            "google" -> Prefs.email(this) + " · Google"
            else -> Prefs.email(this)
        }
        page.stats.text = "${Prefs.points(this)} Pulse Points\n${Repository.accuracy()}% prediction accuracy\nBest streak ${Prefs.bestStreak(this)}"
        page.badges.setOnClickListener { startActivity(Intent(this, AchievementsActivity::class.java)) }
        page.settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        page.photo.setOnClickListener { pickAvatar.launch("image/*") }
        page.vault.setOnClickListener { startActivity(Intent(this, VaultActivity::class.java)) }
        page.upgrade.visibility = if (Prefs.mode(this) == "guest") View.VISIBLE else View.GONE
        page.upgrade.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java).putExtra("upgrade", true))
        }
        page.logout.setOnClickListener {
            Cloud.signOut(this)
            Prefs.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun openMatch(id: String) {
        startActivity(Intent(this, MatchDetailActivity::class.java).putExtra("id", id))
    }
}

/** Tiny TextWatcher so the search field stays debounced inside MainActivity. */
private class SimpleWatcher(val onChange: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChange()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
