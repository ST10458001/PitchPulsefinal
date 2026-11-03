# Firebase setup (about 10 minutes)

PitchPulse uses **Firebase Authentication** (email/password + Google SSO), **Cloud Firestore** (NoSQL database) and **Cloud Messaging** (push). Without `app/google-services.json` the app still builds and falls back to on-device storage, so do this before you record the video.

## 1. Create the project
1. Go to https://console.firebase.google.com and click **Add project** (name it `PitchPulse`). Google Analytics can be off.
2. **Project settings -> Your apps -> Android icon**:
   - Package name: `com.rosebank.pitchpulse`
   - **Debug signing certificate SHA-1:** `BD:99:4E:BF:3E:78:72:80:94:CE:59:10:80:12:E2:B6:35:DA:83:A3`

   This is the SHA-1 of the shared `app/debug.keystore` that is committed to the repo. It means Google sign-in works on **every lab PC**, with no per-machine SHA-1.
3. Download **`google-services.json`** and put it in the **`app/`** folder (next to `app/build.gradle`). Commit it. It contains no secrets, only project identifiers.

## 2. Turn on Authentication
**Build -> Authentication -> Get started -> Sign-in method**
- Enable **Email/Password**.
- Enable **Google** (choose a support email, then Save).

After enabling Google, **download `google-services.json` again** and replace the one in `app/`. It now includes the web client ID that the app needs for Google sign-in.

## 3. Create Firestore
1. **Build -> Firestore Database -> Create database** (production mode, any region such as `eur3` or `africa-south1` if offered).
2. **Rules** tab: paste the contents of `firestore.rules` from the repo, then **Publish**.

## 4. Cloud Messaging (push)
Nothing to enable. The app logs its token under `PP-Cloud`. To send a test push:
1. Run the app, sign in, and copy the line `FCM token: ...` from Logcat (filter `PP-Cloud`).
2. Firebase console -> **Messaging -> Create your first campaign -> Firebase Notification messages -> Send test message**.
3. Paste the token, press **Test**. The notification arrives on the phone (allow notifications on Android 13+).
4. You can also send to the topic **`goals`** with a normal campaign (target: Topic = goals).

## 5. What to show in the demo video
- **Authentication -> Users:** the account you just registered (the password is hashed by Google and not visible, which is the point).
- **Firestore -> Data:** `users/{uid}` (profile), `users/{uid}/predictions/{matchId}`, `users/{uid}/devices/current`.
- The app's **Profile -> Stored data** screen, which has an *ONLINE SERVICES* section with the same Firebase uid.
- The **REST API**: TheSportsDB search results in the app (the public API).
- A push notification from the Firebase console arriving on the phone.

## Troubleshooting
| Problem | Fix |
|---|---|
| Google sign-in says unavailable / error 10 | The SHA-1 above is missing in Firebase, or `google-services.json` was not re-downloaded after enabling Google. |
| "Google sign-in is not configured" | `default_web_client_id` is missing: re-download `google-services.json` after enabling the Google provider. |
| Gradle: "File google-services.json is missing" | It should not happen (the plugin only applies when the file exists). Check the file is directly in `app/`. |
| Firestore `PERMISSION_DENIED` in Logcat | Publish the rules from `firestore.rules`, and make sure you are signed in (not guest). |
| No push on the phone | Use the exact token from Logcat, allow notifications, and make sure the phone has internet. |
| Emulator / phone has no Google account | Add a Google account on the phone (Settings -> Accounts). |
