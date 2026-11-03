# Part 3 — what the app covers

PitchPulse is the final PoE build: live scores, predictions, and the services the brief asks you to show on camera.

| Requirement | Where to show it |
| --- | --- |
| Register and sign in | Welcome screen. Password needs 8 characters, one capital and one number. |
| Password is not stored in plain text | Web: the authentication service keeps a one-way hash. Profile → Stored data shows the hash. Android: SHA-256 plus a salt in the `accounts` document (`PasswordHasher`). |
| Single sign-on | Continue with Google. SSO accounts have no password. On a phone without `google-services.json`, enter the Google email and the app records provider `google`. |
| Settings | Settings: alerts, theme, Pulse Pro, and language. |
| REST API | Search a club name (3+ letters). With `api_base_url` set, the request goes to our Cloud Run API (`docs/API_SETUP.md`), which reads TheSportsDB; predictions are saved through `POST /v1/predictions` into Firestore. Without it, the app calls TheSportsDB directly. |
| Offline sync | Turn on airplane mode, lock a prediction (it says Saved offline), turn airplane mode off. The queued prediction is written to the document database. Android uses Room for the score cache and flushes the queue when the network returns. |
| Real-time alerts (push) | Firebase console → Messaging → Send test message (see `docs/FIREBASE_SETUP.md`). Also Settings → Send a test alert. Live goal lines also raise a notification when goal alerts are on. |
| South African languages | Settings → English, Afrikaans, isiZulu. |
| NoSQL read and write | Profile → Stored data. Collections: `predictions`, `settings`. Android: `files/nosql`. |
| Blob storage | Profile → Save profile photo. The file is stored as a blob, not only as a screen avatar. |
| Invalid input | Bad emails, weak passwords and non-image files are rejected without crashing. |
| Tests | `PointsCalculatorTest`, `PasswordHasherTest`. GitHub Actions workflow: `.github/workflows/android.yml`. |
| Logging | Logcat filter `PP-` shows tagged logs for login, sync, API, storage, blobs, alerts and settings (see README). Passwords are never logged. |
| Play Store prep | `docs/PLAY_STORE.md`. |

Tag the final commit `final PoE` before you hand in.

Firebase setup and the demo checklist for the online services: `docs/FIREBASE_SETUP.md`.
