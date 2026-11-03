# PitchPulse — Your Game. Your Stats. Your Way.

Android sports app (Kotlin, XML layouts, Room) for **OPSC6312 – Mobile Application Development, Part 3 (Final PoE)**.

| | |
|---|---|
| **Group** | Group 10, Rosebank International |
| **Authors** | Tshegofatso Mokwele (ST10458001), Lindokuhle Nelson (ST10446928) |
| **Package** | `com.rosebank.pitchpulse` · Min SDK 26 · Target SDK 34 |
| **Demo video** | 🎥 **PASTE UNLISTED YOUTUBE LINK HERE** |
| **Release notes** | [RELEASE_NOTES.md](RELEASE_NOTES.md) |
| **AI usage write-up** | [AI_USAGE.md](AI_USAGE.md) |

## Screenshots

<!-- Replace these with real phone screenshots saved in docs/screenshots/ -->
| Home | Match detail | Prediction League | Settings |
|---|---|---|---|
| ![Home]<img width="738" height="1600" alt="WhatsApp Image 2026-11-03 at 19 20 53" src="https://github.com/user-attachments/assets/ed4bb11f-ab0c-4297-b368-7df4361ed7fe" />
 | ![Match]<img width="738" height="1600" alt="WhatsApp Image 2026-11-03 at 19 20 53" src="https://github.com/user-attachments/assets/b009fbbf-b72d-4947-923b-2ed754b06a46" />
 | ![Predictions]<img width="738" height="1600" alt="WhatsApp Image 2026-11-03 at 19 20 53 (1)" src="https://github.com/user-attachments/assets/998e165b-3494-4245-a498-62537a36e3df" />
 | ![Settings]<img width="738" height="1600" alt="WhatsApp Image 2026-11-03 at 19 36 21" src="https://github.com/user-attachments/assets/ef343e6c-4e97-4ec2-a824-fe0708d364d0" />
 |

---

## 1. Purpose of the app

PitchPulse puts live scores, news, favourites and a points-based **Prediction League** into one app. Our Part 1 research on LiveScore, Sofascore and SportyTV showed three recurring problems: intrusive ads, shallow statistics, and users having to jump between apps for news and scores. PitchPulse answers each one:

- **Ad-light promise:** one small banner on non-critical screens, never full-screen. *Pulse Pro* removes it.
- **Stat Cards:** simple swipeable match stats (possession, shots, xG, momentum) instead of heavy heatmaps.
- **Pulse Mode:** short commentary lines that react to live score changes.
- **Prediction League:** earn Pulse Points for correct predictions, with badges, a leaderboard and a daily *Safe Bet*. No gambling.
- **News and scores together** in a single Discover feed.

## 2. Part 3 requirements and where to find them

| Brief requirement | How PitchPulse meets it |
|---|---|
| Register and log in, password encrypted | Welcome screen. Password rules: 8+ characters, one capital, one number. Accounts live in **Firebase Authentication**, which hashes and salts passwords on Google's servers (the app never stores them). Without Firebase config the app falls back to a salted SHA-256 hash on the device (`PasswordHasher`). |
| Single sign-on (SSO) | **Continue with Google** (Credential Manager + Firebase Authentication). SSO accounts have no password. |
| Change settings | Settings screen: notifications per event type, theme, language, Pulse Pro. |
| REST API | Our own **PitchPulse REST API** (`api/`, Node.js + Express on Cloud Run) connected to Firestore: club search (`GET /v1/teams/search`, cached, backed by TheSportsDB) and prediction saving (`POST /v1/predictions`, Firebase-token protected). The app falls back to calling TheSportsDB directly if no API URL is set. See [docs/API_SETUP.md](docs/API_SETUP.md). |
| Offline mode with sync | Scores are cached in **Room**. Predictions made offline are queued in Room and written to **Firestore** when the network returns (`Repository.flushQueue`). |
| Real-time notifications | **Firebase Cloud Messaging** push (`PitchPulseMessagingService`) plus local goal alerts and a *Send a test alert* button (`Notifier`). |
| Multi-language (2+ South African languages) | **English, Afrikaans and isiZulu** (`values`, `values-af`, `values-zu`), switchable in Settings. |
| NoSQL store and read | **Cloud Firestore**: `users/{uid}`, `users/{uid}/predictions`, `users/{uid}/devices`. A local document store (`DocumentStore`) mirrors it for offline use. |
| Blob storage | Profile photo bytes saved as a blob on the device (`BlobStore`). |
| Invalid input handled | Bad emails, weak passwords, wrong passwords and non-image files are rejected without crashing. |
| Part 1 features | Live scores, favourites, match detail, Stat Cards, news feed, predictions, search, profile, badges. |
| Icon and image assets | `res/drawable/ic_pulse_logo.xml` plus store assets in `docs/`. |
| Automated tests + GitHub Actions | JVM unit tests and a CI workflow (section 7). |
| Logging | Tagged `Log` output (section 6). |

## 3. Design considerations

- **Phone-first UI:** Material components, bottom navigation with five destinations (Home, Favourites, Predictions, Search, Profile), pull-to-refresh and a two-theme colour system (Charcoal and Ivory).
- **Never crash on bad input:** every form validates before saving and shows an inline error instead of throwing.
- **Offline first:** the UI always reads from Room, so the app still shows the last known scores with no connection.
- **Privacy:** only an email, a password hash (or SSO provider id), predictions, settings and an optional photo are stored. Passwords are never logged.
- **Accessibility and localisation:** all text is in string resources in three languages, and the system font size is respected.

### Architecture

```
Activities (XML + ViewBinding)  ──►  Repository  ──►  Room (scores, news, favourites, predictions)
        │                                 │
        │                                 ├──►  DocumentStore (on-device NoSQL mirror)
        │                                 └──►  Cloud ──► Firebase Auth · Firestore · Cloud Messaging
        ├──►  Api  ──►  PitchPulse REST API (Cloud Run) ──► Firestore + TheSportsDB
        │         └─(fallback)──►  SportsApi ──► TheSportsDB (direct)
        ├──►  BlobStore  (profile photo)
        └──►  Notifier   (notifications)
```

## 4. 🧪 How to run the app in the lab

> The brief requires the final app to run on a **physical Android phone, not an emulator**. Try this before the day you record or present.

### What you need
- A lab PC (or laptop) with **Android Studio Ladybug or newer** (it includes JDK 17).
- **Android SDK Platform 34** (Android Studio → *Tools → SDK Manager*).
- An **Android phone, Android 8.0+ (API 26+)**, with a **Google account signed in**, and a **data USB cable** (not a charge-only one).
- **`app/google-services.json`** from your Firebase project (see [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md)). It is committed in the repo, so a fresh clone already has it. The project uses a shared `app/debug.keystore` so Google sign-in works on any lab PC.
- **Internet on the PC** for the first Gradle sync. The app also needs internet for the club search.

### Step 1 — Get the project
1. Clone the repo (or unzip the download): `git clone <your-repo-url>`
2. In Android Studio choose **File → Open** and select the folder that **directly contains `settings.gradle`**. Do not open the parent folder.

### Step 2 — Let Gradle sync
1. Wait for *Gradle sync* to finish (first run downloads Gradle 8.7 and libraries, about 5 to 10 minutes on lab internet).
2. If it asks for the Gradle JDK, pick **JDK 17** (*Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*).
3. If Android Studio asks to create `local.properties` or install missing SDK parts, accept.

### Step 3 — Prepare the phone
1. *Settings → About phone* → tap **Build number** 7 times to unlock Developer options.
2. *Settings → Developer options* → turn on **USB debugging**.
3. Plug the phone in and tap **Allow** on the "Allow USB debugging?" prompt (tick *Always allow*).
4. In Android Studio the phone should appear in the device drop-down. To check from a terminal: `adb devices`. It must say `device`, not `unauthorized`.

*No cable?* Use **Developer options → Wireless debugging** and pair with *Device Manager → Pair using Wi-Fi*. The PC and phone must be on the same network. Many lab networks block this, so prefer USB.

### Step 4 — Run
1. Select your phone in the device drop-down and the **`app`** run configuration.
2. Press the green **Run ▶** button.
3. The app installs and opens on the Splash screen, then Onboarding, then the Welcome/login screen.

### Step 5 — Quick smoke test (2 minutes)
1. Register with an email, a name and a password such as `Pulse2026`.
2. Open **Profile → Stored data** and check the *ONLINE SERVICES* section shows a Firebase uid. In the Firebase console, check the user under *Authentication* and the documents under *Firestore*.
3. Tap a match, then the **Stats** tab, then go back.
4. Search a club (for example `Arsenal`, 3+ letters) and check results appear. The heading says *PitchPulse API* once `api_base_url` is set, otherwise *TheSportsDB*.

### Optional: build the APK without running from Studio
- Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The file is `app/build/outputs/apk/debug/app-debug.apk`.
- Install on the phone: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Or in a terminal: `gradle :app:assembleDebug` (or `./gradlew assembleDebug` if you generated the wrapper).

### Run the unit tests
Right-click `app/src/test` → **Run 'Tests in ...'**, or run `gradle :app:testDebugUnitTest`.

### Lab troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails / can't download | The lab firewall may block `services.gradle.org` or Maven. Use a phone hotspot, or sync at home once and bring the project with its `.gradle` cache. Then use *File → Settings → Gradle → Offline work*. |
| "Unsupported class file major version" or JDK error | Set **Gradle JDK = 17**. |
| "SDK location not found" | Create `local.properties` with `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` (or fix it in *SDK Manager*). |
| Phone not listed | Use a data cable, enable USB debugging, accept the RSA prompt, try *File → Invalidate Caches*. Set the USB mode to **File transfer**. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall the old PitchPulse from the phone, then run again. |
| No notification appears | On Android 13+ tap **Allow** on the permission prompt, or enable notifications in the phone's app settings. |
| Search returns nothing | Check the phone has internet. Type at least 2 letters. |
| Build error after pulling | *Build → Clean Project*, then *Rebuild Project*. |

## 5. Demo video checklist (record on the phone, with voice-over)

The brief asks for a professional video with a **voice-over** that shows every function. Suggested order:

1. **Launch:** splash, onboarding, welcome screen.
2. **Register:** bad email → inline error; weak password → inline error; then a valid account.
3. **Online data:** show the **Firebase console**: *Authentication → Users* (the new account, password hidden because it is hashed) and *Firestore → Data* (`users/{uid}`). Then show *Profile → Stored data* and explain where each kind of data lives (Auth, Firestore, Room, blob storage).
4. **Log out and log in** with the same account; also show a **wrong password** being rejected.
5. **Google SSO:** tap *Continue with Google*, pick an account, and show the new user in Firebase *Authentication* with the Google provider.
6. **Home feed:** scores, status dots, pull-to-refresh, Discover news.
7. **Match detail → Stats**, share a Stat Card.
8. **Favourites:** star, reorder.
9. **Prediction League:** lock a prediction, points and leaderboard, badges.
10. **REST API:** show the Cloud Run service and its logs, lock a prediction and show it in Firestore with `source: api`, then search a club and show the results labelled *PitchPulse API* (see [docs/API_SETUP.md](docs/API_SETUP.md)).
11. **Offline sync:** turn on airplane mode → lock a prediction ("Saved offline") → turn airplane mode off → show the prediction appear in **Firestore** (`users/{uid}/predictions`).
12. **Notifications:** *Settings → Send a test alert*, then send a **push from the Firebase console** (*Messaging → Send test message*) and show it arriving on the phone.
13. **Settings:** toggles, theme, **language switch to Afrikaans and isiZulu**.
14. **Profile photo** saved as a blob (visible in *Stored data*).
15. **Logcat:** show the `PP-` log lines while you do step 2, 10 and 11.
16. Show the **GitHub repo, the README, the passing GitHub Actions run, and the Play Store prep evidence.**

Upload it to YouTube as **Unlisted** and paste the link at the top of this README.

## 6. Logging

Tagged logs (filter Logcat by `PP-`) show how the code behaves. Passwords are never logged.

| Tag | What it logs |
|---|---|
| `PP-Login` | Registration, login success/failure, SSO, guest |
| `PP-Cloud` | Firestore writes, FCM token, topic subscription, sign-out |
| `PP-FCM` | Push messages received |
| `PP-Repo` | Room ready, predictions saved online/offline, queue sync |
| `PP-RestApi` | Calls to our REST API and fallbacks |
| `PP-Api` | Direct TheSportsDB requests (fallback) |
| `PP-NoSQL` | Document reads and writes |
| `PP-Blob` | Blob saves |
| `PP-Notify` | Notifications posted or skipped |
| `PP-Settings` | Language changes |

## 7. Automated testing and GitHub Actions

- **Tests:** `PointsCalculatorTest` (result-only, score, full-marks and miss cases) and `PasswordHasherTest` (same password and salt give the same hash, a different salt changes the hash, the plain password is never the hash, and only the right password matches).
- **Workflow:** `.github/workflows/android.yml` runs on every push and pull request. It sets up JDK 17, the Android SDK and Gradle 8.7, **runs the unit tests, builds the debug APK**, and uploads the test report and the APK as artifacts.
- This proves the project builds on a clean Linux machine, not just on our laptops. Check the **Actions** tab for the green tick.

## 8. Version control

- Repository initialised with this README. We committed and pushed regularly as features were added.
- The REST API source is in `api/`, with a CI check on every push.
- Final hand-in commit is tagged:

```
git tag -a "final-PoE" -m "Final PoE submission"
git push origin final-PoE
```

- Release keystores and `key.properties` are in `.gitignore`. `google-services.json` and the shared **debug** keystore are committed on purpose so the project runs on any machine. No zip files are committed.

## 9. Publishing preparation

Signed APK/AAB steps, the Data safety answers, and the listing checklist are in [docs/PLAY_STORE.md](docs/PLAY_STORE.md). Evidence to include: signed APK, store screenshots, and a screenshot of the build uploaded to Google Play Console.

## 10. Release notes (summary)

Full details are in [RELEASE_NOTES.md](RELEASE_NOTES.md). **v1.0.0 (final PoE)** adds Google SSO, offline sync, notifications, three-language support, a REST API search, NoSQL and blob storage, logging, CI and release signing support, on top of the prototype's score feed, favourites and Prediction League. The innovative features in this version are Pulse Mode, Stat Cards, the Prediction League with Safe Bet, and the ad-light model.

## 11. Limitations and future work

- Match scores are a seeded, simulated feed (a poller advances live minutes and goals). The Part 1 design routes real data through our own Cloud Run API from API-Football.
- The profile photo blob is stored on the device. Firebase Cloud Storage would host it online but needs a paid plan.
- Your own REST API runs on Google Cloud Run. Until you set `api_base_url` in `res/values/api.xml`, the app uses TheSportsDB directly.
- Without `google-services.json` the app falls back to on-device accounts and storage, and Google SSO asks for the email instead of showing the Google account picker.

## 12. References

- Android Developers, 2025. *Room persistence library*. https://developer.android.com/training/data-storage/room [Accessed 3 November 2026].
- Android Developers, 2025. *Create a notification*. https://developer.android.com/develop/ui/views/notifications/build-notification [Accessed 3 November 2026].
- Android Developers, 2025. *Data and file storage overview*. https://developer.android.com/training/data-storage [Accessed 3 November 2026].
- Android Developers, 2025. *Localize your app / per-app languages*. https://developer.android.com/guide/topics/resources/localization [Accessed 3 November 2026].
- Android Developers, 2025. *Run apps on a hardware device*. https://developer.android.com/studio/run/device [Accessed 3 November 2026].
- Express, 2025. *Express 4.x API reference*. https://expressjs.com/en/4x/api.html [Accessed 3 November 2026].
- Google Cloud, 2025. *Deploy to Cloud Run from source*. https://cloud.google.com/run/docs/deploying-source-code [Accessed 3 November 2026].
- Firebase, 2025. *Verify ID tokens (Admin SDK)*. https://firebase.google.com/docs/auth/admin/verify-id-tokens [Accessed 3 November 2026].
- Firebase, 2025. *Authentication, Cloud Firestore and Cloud Messaging for Android*. https://firebase.google.com/docs/android/setup [Accessed 3 November 2026].
- Android Developers, 2025. *Sign in your user with Credential Manager*. https://developer.android.com/identity/sign-in/credential-manager-siwg [Accessed 3 November 2026].
- Firebase, 2025. *Firebase Cloud Messaging*. https://firebase.google.com/docs/cloud-messaging [Accessed 3 November 2026].
- GitHub Marketplace, 2025. *Automated build Android app with GitHub Action*. https://github.com/marketplace/actions/automated-build-android-app-with-github-action [Accessed 3 November 2026].
- IMAD5112, 2025. *GitHub Actions build.yml example*. https://github.com/IMAD5112/Github-actions/blob/main/.github/workflows/build.yml [Accessed 3 November 2026].
- OWASP, 2024. *Password Storage Cheat Sheet*. https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html [Accessed 3 November 2026].
- TheSportsDB, 2025. *Free Sports API*. https://www.thesportsdb.com/free_sports_api [Accessed 3 November 2026].
