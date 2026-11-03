# PitchPulse Release Notes

## v1.0.0 — Final PoE (Part 3)

<!-- Verify the "since the prototype" list against your Part 2 prototype before submitting. -->

### Innovative features in this version
| Feature | What it does |
|---|---|
| **Ad-light promise** | A single small banner, no full-screen ads. *Pulse Pro* removes the banner. |
| **Pulse Mode** | Live Pulse line that rewrites itself when a goal is scored. |
| **Stat Cards** | Swipeable, shareable match stat cards instead of heavy heatmaps. |
| **Prediction League + Safe Bet** | Predict results, scores and first scorers for Pulse Points, badges and a leaderboard. One risk-free *Safe Bet* per day. |
| **News + scores in one feed** | Discover tab combines articles with live match updates. |

### Added since the prototype
- **Firebase Authentication:** email/password accounts (passwords hashed and salted by Firebase) and **Google single sign-on** through Credential Manager. Strength rules: 8+ characters, a capital, a number.
- **Own REST API** (Node.js + Express on Cloud Run): Firebase-token-protected prediction saving into Firestore, and a cached club-search endpoint. Falls back to direct calls if unavailable.
- **Cloud Firestore (NoSQL):** profiles, predictions and device tokens stored online per user, protected by security rules (`firestore.rules`).
- **Firebase Cloud Messaging:** real push notifications and a `goals` topic.
- **Offline mode with sync:** Room cache for scores, plus a prediction queue that syncs to Firestore on reconnect.
- **Alerts:** goal and result notifications with per-type toggles and a test alert.
- **Multi-language support:** English, Afrikaans and isiZulu with an in-app language picker.
- **REST API integration:** club search powered by TheSportsDB.
- **On-device NoSQL mirror** and **blob storage** for the profile photo.
- **Stored data screen** to show hashes, documents and blobs during the demo.
- **Logging** across login, sync, API, storage and notifications (Logcat tags `PP-*`).
- **Automated testing and CI:** unit tests plus a GitHub Actions workflow that builds the APK.
- **Release preparation:** optional release signing via `key.properties`, Play Store checklist, final app icon.

### Improvements and fixes
- Invalid email, password, name and image inputs are now rejected with inline messages instead of failing.
- Prediction lock window (5 minutes before kick-off) and Safe Bet daily limit enforced.
- Guests are prompted to create an account before the Prediction League.
- Lecturer feedback applied: *(list the specific feedback points you changed here)*

### Known limitations
- Scores come from a simulated feed, and the profile photo blob stays on the device. See README section 11.
