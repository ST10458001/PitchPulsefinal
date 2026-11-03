# Play Store checklist

The listing is not submitted from this workspace. Do these in Android Studio before you upload an AAB.

1. **App icon.** `res/drawable/ic_pulse_logo.xml` is the launcher icon. Export a 512×512 PNG for the store listing from that mark.
2. **Version.** `versionCode` 1, `versionName` 1.0, `targetSdk` 34, `minSdk` 26. Package `com.rosebank.pitchpulse`.
3. **Signing.** Build → Generate Signed Bundle. Keep the upload keystore outside the repo. Do not commit passwords.
4. **Privacy.** The app stores an email, a password hash (or an SSO provider id), predictions, settings and an optional profile photo. Say that in the Data safety form. No real-money gambling.
5. **Permissions.** Internet, network state, notifications, and the photo picker only when the user saves a profile photo.
6. **Release build.** `minifyEnabled` is off so the marker can still read the project. Turn minify on only after a release test on a physical phone.
7. **Phone, not emulator.** Record the demo video on a handset. Show register, the stored hash, Google SSO, settings, a TheSportsDB search, an offline prediction syncing back, a notification, Afrikaans or isiZulu, and the profile photo blob.

## Signed release build (added for the final PoE)
1. Build -> Generate Signed Bundle / APK -> create a keystore (`.jks`) and keep it **outside** the repo.
2. Optional command-line signing: create `key.properties` in the project root with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. It is git-ignored, and `app/build.gradle` picks it up automatically.
3. Build -> Generate Signed Bundle / APK -> APK -> release. Keep the signed APK and screenshots as evidence of publication prep.
4. Play Console: create the app, upload the AAB to Internal testing, fill in the Data safety form, and take a screenshot of the upload page for the PoE.
