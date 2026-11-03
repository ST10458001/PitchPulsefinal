package com.rosebank.pitchpulse

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class TimelineEvent(val minute: String, val text: String)
data class SearchHit(val kind: String, val id: String, val label: String)
data class BoardRow(val name: String, val points: Int, val you: Boolean)

/**
 * Orchestrates Room (offline cache) and the in-app sports feed.
 * A production build would fetch API-Football through the Cloud Run gateway
 * and write the same tables. Direct client calls to API-Football are intentionally absent.
 *
 * Offline mode with sync: predictions made while offline are saved in Room with queued = true
 * and written to the document database by flushQueue() once the network returns.
 * References:
 *  - Android Developers, 2025. Room persistence library.
 *  - Android Developers, 2025. ConnectivityManager (network state checks).
 */
object Repository {
    private const val TAG = "PP-Repo"
    private lateinit var db: AppDatabase
    var ready: Boolean = false
        private set

    fun isReady(): Boolean = ready

    fun init(context: Context) {
        if (ready) return
        db = AppDatabase.get(context)
        if (db.matches().all().isEmpty()) {
            db.matches().upsertAll(seedMatches())
            db.news().upsertAll(seedNews())
        }
        ready = true
        Log.i(TAG, "Repository ready: ${db.matches().all().size} matches cached in Room")
    }

    fun matches(sport: String = "All"): List<MatchEntity> {
        val order = mapOf("LIVE" to 0, "HT" to 1, "NS" to 2, "FT" to 3)
        val all = db.matches().all()
        val filtered = if (sport == "All") all else all.filter { it.sport.equals(sport, true) }
        return filtered.sortedWith(compareBy({ order[it.status] ?: 9 }, { it.kickoffAt }))
    }

    fun match(id: String): MatchEntity? = db.matches().all().find { it.id == id }

    fun news(): List<NewsEntity> = db.news().all()

    fun prediction(id: String): PredictionEntity? = db.predictions().one(id)

    fun predictions(): List<PredictionEntity> = db.predictions().all()

    fun favourites(kind: String? = null): List<FavouriteEntity> {
        val all = db.favourites().all()
        return if (kind == null) all else all.filter { it.kind == kind }
    }

    fun isStarred(kind: String, id: String): Boolean =
        db.favourites().all().any { it.kind == kind && it.entityId == id }

    /** @return "on", "off", or "limit" (FR-003 caps favourites at 50). */
    fun toggleFavourite(kind: String, id: String, label: String): String {
        if (isStarred(kind, id)) {
            db.favourites().remove(kind, id)
            return "off"
        }
        if (db.favourites().count() >= 50) return "limit"
        val pos = (db.favourites().all().maxOfOrNull { it.position } ?: 0) + 1
        db.favourites().upsert(FavouriteEntity(kind, id, label, pos))
        return "on"
    }

    fun moveFavourite(kind: String, id: String, up: Boolean) {
        val list = db.favourites().all().filter { it.kind == kind }.toMutableList()
        val index = list.indexOfFirst { it.entityId == id }
        if (index < 0) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in list.indices) return
        val a = list[index]
        val b = list[swapWith]
        db.favourites().upsert(a.copy(position = b.position))
        db.favourites().upsert(b.copy(position = a.position))
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun submitPrediction(context: Context, draft: PredictionEntity): String {
        if (Prefs.mode(context) == "guest") {
            return "Create a permanent account before the Prediction League"
        }
        val match = match(draft.matchId) ?: return "Match not found"
        val lockAt = match.kickoffAt - 5 * 60 * 1000
        if (match.status != "NS" || System.currentTimeMillis() >= lockAt) {
            return "Predictions lock five minutes before kick-off"
        }
        if (draft.safeBet) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (Prefs.safeBetDay(context) == today) return "Safe Bet already used today"
            Prefs.setSafeBetDay(context, today)
        }
        val online = isOnline(context)
        Log.i(TAG, "Prediction for match ${draft.matchId}: online=$online, queued=${!online}")
        val saved = draft.copy(queued = !online, points = 0, settled = false)
        db.predictions().upsert(saved)
        DocumentStore.put(
            context,
            "predictions",
            saved.matchId,
            org.json.JSONObject()
                .put("matchId", saved.matchId)
                .put("result", saved.resultPick)
                .put("score", saved.scorePick)
                .put("scorer", saved.scorerPick)
                .put("safeBet", saved.safeBet)
                .put("queued", saved.queued)
        )
        if (online) Api.submitPrediction(context, saved.matchId, saved.resultPick, saved.scorePick, saved.scorerPick, saved.safeBet)
        return if (online) "Prediction locked in" else "Saved offline — will send when you reconnect"
    }

    fun flushQueue(context: Context) {
        if (!isOnline(context)) return
        val pending = db.predictions().all().filter { it.queued }
        if (pending.isNotEmpty()) Log.i(TAG, "Back online: syncing ${pending.size} queued prediction(s)")
        pending.forEach {
            db.predictions().upsert(it.copy(queued = false))
            DocumentStore.put(
                context,
                "predictions",
                it.matchId,
                org.json.JSONObject()
                    .put("matchId", it.matchId)
                    .put("result", it.resultPick)
                    .put("score", it.scorePick)
                    .put("scorer", it.scorerPick)
                    .put("safeBet", it.safeBet)
                    .put("queued", false)
            )
            Api.submitPrediction(context, it.matchId, it.resultPick, it.scorePick, it.scorerPick, it.safeBet)
        }
    }

    /** FR-002 background poll. Football minutes advance; a goal rewrites the Pulse line. */
    fun tick(context: Context): String? {
        var latest: String? = null
        db.matches().all().filter { it.status == "LIVE" && it.sport == "FOOTBALL" }.forEach { match ->
            var minute = match.minute + 1
            var status = "LIVE"
            var clock = "$minute'"
            var home = match.homeScore
            var away = match.awayScore
            var xgH = match.xgHome
            var xgA = match.xgAway
            var pulse = match.pulse
            if (minute >= 90) {
                status = "FT"
                clock = "FT"
                minute = 90
            } else if (Random.nextDouble() < 0.2) {
                if (xgH >= xgA) {
                    home += 1
                    xgH += 0.27
                    pulse = "${match.home} are building serious pressure — xG now ${"%.2f".format(xgH)}."
                } else {
                    away += 1
                    xgA += 0.27
                    pulse = "${match.away} are building serious pressure — xG now ${"%.2f".format(xgA)}."
                }
                latest = pulse
            }
            val next = match.copy(
                minute = minute,
                clock = clock,
                status = status,
                homeScore = home,
                awayScore = away,
                xgHome = xgH,
                xgAway = xgA,
                pulse = pulse
            )
            db.matches().upsert(next)
            if (status == "FT") settle(context, next)
        }
        return latest
    }

    fun simulateFullTime(context: Context, id: String): MatchEntity? {
        val match = match(id) ?: return null
        val done = match.copy(status = "FT", clock = "FT", minute = if (match.minute > 0) match.minute else 90)
        db.matches().upsert(done)
        settle(context, done)
        return done
    }

    fun settle(context: Context, match: MatchEntity) {
        val pred = db.predictions().one(match.id) ?: return
        if (pred.settled) return
        val actual = when {
            match.homeScore > match.awayScore -> "HOME"
            match.homeScore < match.awayScore -> "AWAY"
            else -> "DRAW"
        }
        val resultOk = pred.resultPick == actual
        val scoreOk = pred.scorePick.trim() == "${match.homeScore}-${match.awayScore}"
        val scorerOk = pred.scorerPick.isNotBlank() &&
            match.firstScorer.contains(pred.scorerPick.trim(), ignoreCase = true)
        val pts = PointsCalculator.award(resultOk, scoreOk, scorerOk)
        if (pts > 0) {
            val streak = Prefs.streak(context) + 1
            Prefs.setStreak(context, streak)
            if (streak > Prefs.bestStreak(context)) Prefs.setBest(context, streak)
            Prefs.addPoints(context, pts)
        } else if (!pred.safeBet) {
            Prefs.setStreak(context, 0)
        }
        db.predictions().upsert(pred.copy(points = pts, settled = true, queued = false))
    }

    fun accuracy(): Int {
        val done = db.predictions().all().filter { it.settled }
        if (done.isEmpty()) return 0
        return done.count { it.points > 0 } * 100 / done.size
    }

    fun leaderboard(context: Context): List<BoardRow> {
        val rows = mutableListOf(
            BoardRow("Lindokuhle N.", 42, false),
            BoardRow("Thandi M.", 36, false),
            BoardRow("Alex Nkosi", 28, false),
            BoardRow("Kyle D.", 19, false),
            BoardRow(Prefs.name(context), Prefs.points(context), true)
        )
        return rows.sortedByDescending { it.points }
    }

    fun search(query: String): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        db.matches().all().forEach {
            hits += SearchHit("team", it.home, it.home)
            hits += SearchHit("team", it.away, it.away)
            hits += SearchHit("league", it.league, it.league)
            hits += SearchHit("match", it.id, "${it.home} vs ${it.away}")
        }
        val unique = hits.distinctBy { it.kind + ":" + it.id }
        if (query.isBlank()) {
            return unique.filter { it.kind == "team" }.distinctBy { it.label }.take(8)
        }
        return unique.filter { it.label.contains(query, ignoreCase = true) }.take(20)
    }

    fun events(id: String): List<TimelineEvent> = when (id) {
        "psl-1" -> listOf(
            TimelineEvent("12'", "Goal · Kaizer Chiefs"),
            TimelineEvent("51'", "Goal · Orlando Pirates"),
            TimelineEvent("60'", "Yellow · Orlando Pirates")
        )
        "psl-2" -> listOf(
            TimelineEvent("8'", "Goal · Mamelodi Sundowns"),
            TimelineEvent("29'", "Goal · Mamelodi Sundowns")
        )
        "epl-1" -> listOf(TimelineEvent("22'", "Goal · Arsenal"))
        "epl-2" -> listOf(
            TimelineEvent("15'", "Goal · Liverpool"),
            TimelineEvent("44'", "Goal · Chelsea"),
            TimelineEvent("70'", "Goal · Liverpool"),
            TimelineEvent("81'", "Goal · Chelsea")
        )
        else -> listOf(TimelineEvent("—", "Kick-off still to come"))
    }

    fun lineup(team: String): List<String> {
        val named = mapOf(
            "Kaizer Chiefs" to listOf("Petersen", "Hlanti", "Frosler", "Dove", "Ntshangase", "Mmodi", "Du Preez", "Castillo", "Gonzalez", "Saile", "Shabalala"),
            "Orlando Pirates" to listOf("Chaine", "Hotto", "Sibisi", "Ndlovu", "Xoki", "Makhaula", "Saleng", "Mbatha", "Dzvukamanja", "Lepasa", "Dlamini"),
            "Mamelodi Sundowns" to listOf("Williams", "Mudau", "Kekana", "Boutouil", "Modiba", "Mokoena", "Zwane", "Allende", "Shalulile", "Ribeiro", "Rayners"),
            "Manchester United" to listOf("Onana", "Dalot", "Varane", "Martinez", "Shaw", "Casemiro", "Mainoo", "Bruno", "Rashford", "Højlund", "Garnacho"),
            "Arsenal" to listOf("Raya", "White", "Saliba", "Gabriel", "Timber", "Rice", "Ødegaard", "Saka", "Havertz", "Martinelli", "Trossard"),
            "Liverpool" to listOf("Alisson", "Alexander-Arnold", "Konaté", "Van Dijk", "Robertson", "Mac Allister", "Szoboszlai", "Gravenberch", "Salah", "Díaz", "Núñez"),
            "Chelsea" to listOf("Sánchez", "James", "Colwill", "Fofana", "Cucurella", "Caicedo", "Enzo", "Palmer", "Madueke", "Jackson", "Neto")
        )
        return named[team] ?: (1..11).map { "$team $it" }
    }

    fun clearCache() {
        db.matches().clear()
        db.news().clear()
        db.matches().upsertAll(seedMatches())
        db.news().upsertAll(seedNews())
    }

    private fun hours(h: Int) = System.currentTimeMillis() + h * 3_600_000L

    private fun seedMatches(): List<MatchEntity> {
        fun row(
            id: String, sport: String, league: String,
            home: String, away: String, hc: String, ac: String, hcol: String, acol: String,
            hs: Int, aws: Int, minute: Int, clock: String, status: String, kick: Long,
            poss: Int, sh: Int, sa: Int, soth: Int, sota: Int,
            xgh: Double, xga: Double, momentum: String, pulse: String, scorer: String
        ) = MatchEntity(
            id, sport, league, home, away, hc, ac, hcol, acol, hs, aws, minute, clock, status, kick,
            poss, sh, sa, soth, sota, xgh, xga, momentum, pulse, scorer
        )
        return listOf(
            row("psl-1", "Football", "Betway Premiership", "Kaizer Chiefs", "Orlando Pirates", "KZC", "ORL", "#1A1A1A", "#1E3A8A", 1, 1, 67, "67'", "LIVE", hours(-1), 54, 9, 7, 4, 3, 1.42, 0.98, "40,48,52,61,58,66,70", "Kaizer Chiefs are building serious pressure — three big chances in five minutes.", "Saile"),
            row("psl-2", "Football", "Betway Premiership", "Mamelodi Sundowns", "Stellenbosch", "SUN", "STB", "#0B3D91", "#6B1D2A", 2, 0, 45, "HT", "HT", hours(-2), 68, 12, 3, 6, 1, 1.85, 0.31, "55,60,62,70,66", "Sundowns have the game on a string. Stellenbosch are camped in.", "Shalulile"),
            row("epl-1", "Football", "Premier League", "Manchester United", "Arsenal", "MUN", "ARS", "#7A1020", "#9C1C2C", 0, 1, 34, "34'", "LIVE", hours(-1), 42, 4, 8, 1, 4, 0.46, 1.21, "30,35,33,40,38,44", "Arsenal are building serious pressure — xG gap is widening.", "Saka"),
            row("laliga-1", "Football", "La Liga", "Barcelona", "Real Madrid", "BAR", "RMA", "#7A1038", "#0E2240", 0, 0, 0, "20:45", "NS", hours(3), 50, 0, 0, 0, 0, 0.0, 0.0, "50,50,50", "El Clásico is still to come. Predictions close five minutes before kick-off.", ""),
            row("epl-2", "Football", "Premier League", "Liverpool", "Chelsea", "LIV", "CHE", "#7A1020", "#0A3470", 2, 2, 90, "FT", "FT", hours(-5), 58, 15, 11, 6, 5, 2.10, 1.55, "48,60,55,62,58,50", "A four-goal draw. Salah and Palmer both on the scoresheet.", "Salah"),
            row("nba-1", "Basketball", "NBA", "Los Angeles Lakers", "Boston Celtics", "LAL", "BOS", "#3A1D6E", "#0E4D2C", 98, 101, 0, "Q3", "LIVE", hours(-1), 48, 39, 41, 18, 19, 0.0, 0.0, "45,52,49,55,51", "Celtics by a possession. The glass is deciding this quarter.", ""),
            row("atp-1", "Tennis", "ATP Finals", "Djokovic", "Alcaraz", "DJO", "ALC", "#1B3A4B", "#8A3E12", 1, 1, 0, "Set 2", "LIVE", hours(-2), 61, 18, 14, 7, 9, 0.0, 0.0, "58,62,55,60", "Djokovic is landing more first serves. Alcaraz is winning the longer rallies.", ""),
            row("cric-1", "Cricket", "Test", "Proteas", "India", "SA", "IND", "#0E4D32", "#0C3B66", 248, 6, 0, "42 ov", "LIVE", hours(-4), 0, 22, 4, 0, 0, 0.0, 0.0, "40,46,52,48,55", "India are 248 for 6. The new ball is the next swing.", ""),
            row("caf-1", "Football", "AFCON qualifier", "Bafana Bafana", "Nigeria", "BAF", "NGA", "#0E4D32", "#14532D", 0, 0, 0, "Tomorrow", "NS", hours(26), 50, 0, 0, 0, 0, 0.0, 0.0, "50,50", "A free prediction is still open.", ""),
            row("epl-3", "Football", "Premier League", "Tottenham", "Newcastle", "TOT", "NEW", "#132257", "#1A1A1A", 0, 0, 0, "15:00", "NS", hours(6), 50, 0, 0, 0, 0, 0.0, 0.0, "50,50", "Both sides named attacking XIs.", "")
        )
    }

    private fun seedNews(): List<NewsEntity> = listOf(
        NewsEntity("n1", "Soweto Derby level at the break of the hour", "News24", "Chiefs and Pirates trade chances. Pulse has the xG swing, not a popup ad.", "https://www.news24.com/sport", "Chiefs"),
        NewsEntity("n2", "Sundowns stroll, Stellenbosch search for a foothold", "SuperSport", "Two goals, a mountain of possession, and a stat card instead of a heatmap.", "https://www.bbc.com/sport/football", "Sundowns"),
        NewsEntity("n3", "Saka puts Arsenal ahead at Old Trafford", "BBC Sport", "United are chasing. The Pulse line updated off the xG jump.", "https://www.bbc.com/sport/football", "Arsenal"),
        NewsEntity("n4", "El Clásico prediction window is open", "Sky Sports", "Lock a result, a score or a first scorer. It closes five minutes before kick-off.", "https://www.skysports.com/football", "Barcelona"),
        NewsEntity("n5", "Proteas take the second new ball", "ESPNcricinfo", "India 248 for 6. Follow the Proteas and the score stays on your Favourites tab.", "https://www.espncricinfo.com/", "Proteas"),
        NewsEntity("n6", "Celtics by three in the third", "NBA", "A quiet banner. The game is the point.", "https://www.nba.com/news", "Celtics")
    )
}
