package com.rosebank.pitchpulse

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Public REST API. Results are read from TheSportsDB, not hard-coded.
 * Reference: TheSportsDB, 2025. Free Sports API documentation - searchteams.php.
 * https://www.thesportsdb.com/free_sports_api
 */
object SportsApi {
    private const val TAG = "PP-Api"

    fun searchTeams(query: String): List<SearchHit> {
        Log.d(TAG, "GET searchteams.php?t=$query")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=$encoded").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "PitchPulse/1.0")
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val teams = JSONObject(text).optJSONArray("teams")
        if (teams == null) {
            Log.d(TAG, "No teams returned for \"$query\"")
            return emptyList()
        }
        Log.d(TAG, "Received ${teams.length()} team(s)")
        return (0 until teams.length()).map { index ->
            val team = teams.getJSONObject(index)
            SearchHit(
                "team",
                "api-${team.optString("idTeam")}",
                "${team.optString("strTeam")} · ${team.optString("strLeague")}"
            )
        }
    }
}
