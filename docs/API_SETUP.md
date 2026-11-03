# PitchPulse REST API: build, deploy and connect

The API lives in `api/` (Node.js + Express). It sits between the phone and the data:

```
Phone ──HTTPS + Firebase ID token──►  PitchPulse API (Cloud Run)  ──►  Firestore  (users/{uid}/predictions)
                                                                  └─►  TheSportsDB (club search, cached 60 s)
```

| Method | Route | Auth | Purpose |
|---|---|---|---|
| GET | `/v1/health` | none | Service check |
| GET | `/v1/teams/search?q=arsenal` | none | Club search, flattened and cached for 60 seconds |
| POST | `/v1/predictions` | Bearer token | Saves a prediction in Firestore for the signed-in user |
| GET | `/v1/predictions` | Bearer token | Lists the signed-in user's predictions |

The app already works **without** this API (it calls TheSportsDB directly, which satisfies the brief's "use any API that exists on the internet"). Deploy your own API to get the stronger "REST API you create, connected to a database" marks.

## 1. Deploy to Google Cloud Run
Use the **same Google Cloud project as your Firebase project** (Firebase console → Project settings → Project ID).

> Cloud Run needs a billing account on the project (Firebase "Blaze" plan). Class-sized traffic stays inside the free tier. Create a **budget alert** to be safe. If you cannot enable billing, skip this and keep the direct TheSportsDB call.

```bash
# one-time: install the Google Cloud CLI, then
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# from the project root
gcloud run deploy pitchpulse-api --source api --region europe-west1 --allow-unauthenticated
```
(`africa-south1` also works if it is offered to you.) When it finishes it prints a **Service URL** such as `https://pitchpulse-api-abc123-ew.a.run.app`.

If Logcat or the Cloud Run logs later show `PERMISSION_DENIED` from Firestore, give the Cloud Run service account the **Cloud Datastore User** role (Google Cloud console → IAM).

Test it:
```bash
curl https://YOUR-URL/v1/health
curl "https://YOUR-URL/v1/teams/search?q=arsenal"
```

## 2. Connect the app
Open `app/src/main/res/values/api.xml` and paste the URL (no trailing slash):

```xml
<string name="api_base_url" translatable="false">https://pitchpulse-api-abc123-ew.a.run.app</string>
```
Rebuild and run. Now:
- **Search** (Search tab, 3+ letters) goes through your API. The section title changes to *Results · PitchPulse API*.
- **Predictions** are sent to `POST /v1/predictions` with the Firebase ID token. If the API is unreachable the app saves straight to Firestore, so nothing is lost.
- **Profile → Stored data** shows which REST API is in use.

## 3. What to show in the demo video
1. Cloud Run console: the `pitchpulse-api` service and its **Logs** (you will see `POST /v1/predictions` and `Saved prediction ...`).
2. In the app: lock a prediction, then show the document in **Firestore → `users/{uid}/predictions/{matchId}`**. It has `source: "api"`.
3. Search a club in the app and show the results labelled *PitchPulse API*.
4. Logcat filtered by `PP-RestApi`.
5. Briefly open `api/server.js` and explain token verification and the 60-second cache.

## Run the API on your own PC (testing only)
```bash
cd api
npm install
# Firebase console → Project settings → Service accounts → Generate new private key (never commit it)
set GOOGLE_APPLICATION_CREDENTIALS=C:\keys\serviceAccount.json      # Windows
export GOOGLE_APPLICATION_CREDENTIALS=~/keys/serviceAccount.json    # macOS/Linux
npm start
curl http://localhost:8080/v1/health
```
The phone cannot reach `localhost`, so use this for curl/Postman testing and deploy to Cloud Run for the app.

## Troubleshooting
| Problem | Fix |
|---|---|
| Search still says *TheSportsDB* | `api_base_url` is empty, or the app was not rebuilt. |
| `401 Invalid or expired token` | The user is a guest or signed out. Sign in with Firebase. The API and the app must use the **same Firebase project**. |
| `502 Sports data provider unavailable` | TheSportsDB is down or rate-limited. Try again in a minute. |
| Logcat `API save failed ... saving directly to Firestore` | The API URL is wrong or the service is not public (`--allow-unauthenticated`). The data is still saved through Firestore. |
| Cloud Run deploy asks to enable APIs | Answer `y`. |
