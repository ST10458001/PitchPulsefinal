package com.rosebank.pitchpulse

import android.content.Context

/** Local stand-in for Firebase Auth session + user_prefs. Swap the session calls for FirebaseAuth later. */
object Prefs {
    private const val FILE = "pitchpulse"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOnboarded(ctx: Context) = sp(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context) = sp(ctx).edit().putBoolean("onboarded", true).apply()

    fun isLoggedIn(ctx: Context) = sp(ctx).getBoolean("logged", false)

    fun setSession(ctx: Context, name: String, email: String, mode: String) {
        sp(ctx).edit()
            .putBoolean("logged", true)
            .putString("name", name)
            .putString("email", email)
            .putString("mode", mode)
            .apply()
    }

    fun name(ctx: Context) = sp(ctx).getString("name", "Guest") ?: "Guest"
    fun email(ctx: Context) = sp(ctx).getString("email", "") ?: ""
    fun mode(ctx: Context) = sp(ctx).getString("mode", "guest") ?: "guest"
    fun logout(ctx: Context) = sp(ctx).edit().putBoolean("logged", false).apply()

    fun theme(ctx: Context) = sp(ctx).getString("theme", "charcoal") ?: "charcoal"
    fun setTheme(ctx: Context, theme: String) = sp(ctx).edit().putString("theme", theme).apply()

    fun isPro(ctx: Context) = sp(ctx).getBoolean("pro", false)
    fun setPro(ctx: Context, value: Boolean) = sp(ctx).edit().putBoolean("pro", value).apply()

    fun notif(ctx: Context, key: String) = sp(ctx).getBoolean(key, true)
    fun setNotif(ctx: Context, key: String, value: Boolean) =
        sp(ctx).edit().putBoolean(key, value).apply()

    fun language(ctx: Context) = sp(ctx).getString("lang", "en") ?: "en"
    fun setLanguage(ctx: Context, lang: String) = sp(ctx).edit().putString("lang", lang).apply()

    fun points(ctx: Context) = sp(ctx).getInt("points", 0)
    fun addPoints(ctx: Context, amount: Int) =
        sp(ctx).edit().putInt("points", points(ctx) + amount).apply()

    fun streak(ctx: Context) = sp(ctx).getInt("streak", 0)
    fun setStreak(ctx: Context, value: Int) = sp(ctx).edit().putInt("streak", value).apply()
    fun bestStreak(ctx: Context) = sp(ctx).getInt("best", 0)
    fun setBest(ctx: Context, value: Int) = sp(ctx).edit().putInt("best", value).apply()

    fun safeBetDay(ctx: Context) = sp(ctx).getString("safe", "") ?: ""
    fun setSafeBetDay(ctx: Context, day: String) = sp(ctx).edit().putString("safe", day).apply()

    fun recentSearches(ctx: Context): MutableList<String> {
        return (sp(ctx).getString("recent", "") ?: "")
            .split("|")
            .filter { it.isNotBlank() }
            .toMutableList()
    }

    fun pushRecent(ctx: Context, query: String) {
        val list = recentSearches(ctx)
        list.remove(query)
        list.add(0, query)
        sp(ctx).edit().putString("recent", list.take(6).joinToString("|")).apply()
    }
}
