package com.rosebank.pitchpulse

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Local alerts for goals and results.
 * References:
 *  - Android Developers, 2025. Create a notification.
 *  - Android Developers, 2025. Notification runtime permission (Android 13+).
 */
object Notifier {
    private const val CHANNEL = "pulse"
    private const val TAG = "PP-Notify"

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun push(context: Context, title: String, body: String) {
        if (!allowed(context)) {
            Log.w(TAG, "Notification skipped: POST_NOTIFICATIONS not granted")
            return
        }
        Log.i(TAG, "Posting notification: $title")
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL, "Match alerts", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pulse_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1001, notification)
    }
}
