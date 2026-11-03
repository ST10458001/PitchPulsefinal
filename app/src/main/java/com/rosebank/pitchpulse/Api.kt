package com.rosebank.pitchpulse

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for the PitchPulse REST API (api/server.js, hosted on Cloud Run).
 * The base URL lives in res/values/api.xml. When it is empty the app falls back to calling
 * TheSportsDB directly and saving predictions straight to Firestore, so nothing breaks.
 *
 * Signed-in calls send the Firebase ID token as "Authorization: Bearer <token>".
 * All methods that touch the network must run on a background thread.
 *
 * References:
 *  - Android Developers, 2025. Connect to the network (HttpURLConnection).
 *  - Firebase, 2025. Retrieve ID tokens on clients. https://firebase.google.com/docs/auth/admin/verify-id-tokens
 */
object Api {
    private const val TAG = "PP-RestApi"

    fun baseUrl(context: Context): String = context.getString(R.string.api_base_url).trim().trimEnd('/')

    /** Club search through our API, or directly through TheSportsDB if no API is configured. */
    fun searchTeams(context: Context, query: String): List<SearchHit> {
        val base = baseUrl(context)
        if (base.isEmpty()) return SportsApi.searchTeams(query)
        val url = "$base/v1/teams/search?q=" + URLEncoder.encode(query, "UTF-8")
        Log.d(TAG, "GET $url")
        val (code, body) = request("GET", url, null, null)
        if (code !in 200..299) throw IOException("HTTP $code")
        val teams = JSONObject(body).optJSONArray("teams") ?: return emptyList()
        Log.d(TAG, "API returned ${teams.length()} team(s)")
        return (0 until teams.length()).map { i ->
            val t = teams.getJSONObject(i)
            SearchHit("team", "api-${t.optString("id")}", "${t.optString("name")} · ${t.optString("league")}")
        }
    }

    /** Sends a prediction to POST /v1/predictions. On any failure it saves straight to Firestore instead. */
    fun submitPrediction(
        context: Context, matchId: String, result: String, score: String, scorer: String, safeBet: Boolean
    ) {
        val base = baseUrl(context)
        if (base.isEmpty()) {
            Cloud.savePrediction(context, matchId, result, score, scorer, safeBet)
            return
        }
        val app = context.applicationContext
        Thread {
            try {
                val token = idToken(app) ?: throw IllegalStateException("not signed in")
                val json = JSONObject()
                    .put("matchId", matchId)
                    .put("result", result)
                    .put("score", score)
                    .put("scorer", scorer)
                    .put("safeBet", safeBet)
                val (code, body) = request("POST", "$base/v1/predictions", token, json.toString())
                if (code !in 200..299) throw IOException("HTTP $code $body")
                Log.i(TAG, "Prediction $matchId saved through the API")
            } catch (e: Exception) {
                Log.w(TAG, "API save failed (${e.message}); saving directly to Firestore")
                Cloud.savePrediction(app, matchId, result, score, scorer, safeBet)
            }
        }.start()
    }

    private fun idToken(context: Context): String? {
        if (!Cloud.ready(context)) return null
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(false), 8, TimeUnit.SECONDS).token
        } catch (e: Exception) {
            Log.w(TAG, "Could not get ID token: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun request(method: String, url: String, token: String?, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "PitchPulse/1.0")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}
