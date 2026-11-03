package com.rosebank.pitchpulse

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives real-time push messages from Firebase Cloud Messaging (goal alerts, results).
 * Reference: Firebase, 2025. Receive messages in an Android app.
 * https://firebase.google.com/docs/cloud-messaging/android/receive
 */
class PitchPulseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "New FCM token received")
        Cloud.saveToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "PitchPulse"
        val body = message.notification?.body ?: message.data["body"] ?: return
        Log.i(TAG, "Push received: $title")
        Notifier.push(this, title, body)
    }

    private companion object {
        const val TAG = "PP-FCM"
    }
}
