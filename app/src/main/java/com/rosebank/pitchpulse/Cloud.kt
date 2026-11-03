package com.rosebank.pitchpulse

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Online services: Firebase Authentication, Cloud Firestore (NoSQL) and Cloud Messaging (push).
 * Every call is guarded by ready(). When google-services.json is missing the app falls back
 * to the on-device DocumentStore, so the project still builds and runs.
 *
 * References:
 *  - Firebase, 2025. Authenticate with Firebase using password-based accounts on Android.
 *  - Firebase, 2025. Add data to Cloud Firestore.
 *  - Firebase, 2025. Set up a Firebase Cloud Messaging client app on Android.
 */
object Cloud {
    private const val TAG = "PP-Cloud"

    /** True once the Firebase app was created from google-services.json. */
    fun ready(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun uid(context: Context): String? =
        if (ready(context)) FirebaseAuth.getInstance().currentUser?.uid else null

    /** users/{uid}: profile document stored in Firestore. */
    fun saveProfile(context: Context, uid: String, email: String, name: String, provider: String) {
        if (!ready(context)) return
        val data = hashMapOf<String, Any>(
            "email" to email,
            "name" to name,
            "provider" to provider,
            "updatedAt" to Timestamp.now()
        )
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { Log.i(TAG, "Firestore: saved users/$uid") }
            .addOnFailureListener { Log.e(TAG, "Firestore: profile save failed", it) }
    }

    /** users/{uid}/predictions/{matchId}: synced prediction. Firestore also queues writes offline. */
    fun savePrediction(
        context: Context, matchId: String, result: String?, score: String?, scorer: String?, safeBet: Boolean
    ) {
        val uid = uid(context) ?: return
        val data = hashMapOf<String, Any?>(
            "matchId" to matchId,
            "result" to result,
            "score" to score,
            "scorer" to scorer,
            "safeBet" to safeBet,
            "updatedAt" to Timestamp.now()
        )
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("predictions").document(matchId)
            .set(data)
            .addOnSuccessListener { Log.i(TAG, "Firestore: saved prediction $matchId") }
            .addOnFailureListener { Log.e(TAG, "Firestore: prediction save failed", it) }
    }

    /** Fetch the FCM token, store it for the signed-in user, and join the goals topic. */
    fun registerPush(context: Context) {
        if (!ready(context)) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.i(TAG, "FCM token: $token") // copy this into Firebase Console > Messaging > Send test message
                saveToken(context, token)
            }
            .addOnFailureListener { Log.e(TAG, "FCM token fetch failed", it) }
        FirebaseMessaging.getInstance().subscribeToTopic("goals")
            .addOnSuccessListener { Log.i(TAG, "Subscribed to topic: goals") }
    }

    fun saveToken(context: Context, token: String) {
        val uid = uid(context) ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("devices").document("current")
            .set(mapOf("token" to token, "updatedAt" to Timestamp.now()))
            .addOnFailureListener { Log.e(TAG, "Token save failed", it) }
    }

    fun signOut(context: Context) {
        if (!ready(context)) return
        Log.i(TAG, "Signing out of Firebase")
        FirebaseAuth.getInstance().signOut()
    }

    /** Text for the Stored data screen so the demo video can show the online services. */
    fun status(context: Context): String {
        if (!ready(context)) return "Firebase: not configured (add app/google-services.json). Using on-device storage."
        val user = FirebaseAuth.getInstance().currentUser
        return if (user == null) "Firebase: connected, no signed-in user"
        else "Firebase Auth uid: ${user.uid}\nFirestore path: users/${user.uid}\nSee the Firebase console for the live data."
    }
}
