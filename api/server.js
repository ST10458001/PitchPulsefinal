'use strict';

/**
 * PitchPulse REST API (Node.js + Express).
 *
 *  GET  /v1/health              public   - service check
 *  GET  /v1/teams/search?q=     public   - club search (proxies TheSportsDB, cached 60 s)
 *  POST /v1/predictions         signed in - save a prediction in Firestore
 *  GET  /v1/predictions         signed in - list the caller's predictions
 *
 * Signed-in routes need the header  Authorization: Bearer <Firebase ID token>.
 * The token is verified with the Firebase Admin SDK, and every document is written under
 * users/{uid}, so a user can only ever touch their own data.
 *
 * References:
 *  - Express, 2025. Express 4.x API reference. https://expressjs.com/en/4x/api.html
 *  - Firebase, 2025. Verify ID tokens (Admin SDK). https://firebase.google.com/docs/auth/admin/verify-id-tokens
 *  - Firebase, 2025. Add the Admin SDK to your server. https://firebase.google.com/docs/admin/setup
 *  - TheSportsDB, 2025. Free Sports API. https://www.thesportsdb.com/free_sports_api
 */

const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');

// On Cloud Run this uses the service's default credentials.
// Locally set GOOGLE_APPLICATION_CREDENTIALS to a service-account key file.
admin.initializeApp();
const db = admin.firestore();

const app = express();
app.use(cors());
app.use(express.json({ limit: '10kb' }));

// Simple request log (never logs tokens)
app.use((req, _res, next) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.originalUrl}`);
  next();
});

/** Verifies the Firebase ID token and puts the decoded user on req.user. */
async function requireAuth(req, res, next) {
  const match = (req.headers.authorization || '').match(/^Bearer (.+)$/);
  if (!match) return res.status(401).json({ error: 'Missing bearer token' });
  try {
    req.user = await admin.auth().verifyIdToken(match[1]);
    return next();
  } catch (err) {
    console.warn('Token rejected:', err.code || err.message);
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

// ---- 60-second cache so we stay inside the free API limits ----
const cache = new Map();
const TTL_MS = 60 * 1000;

app.get('/v1/health', (_req, res) => {
  res.json({ status: 'ok', service: 'pitchpulse-api', time: new Date().toISOString() });
});

app.get('/v1/teams/search', async (req, res) => {
  const q = String(req.query.q || '').trim();
  if (q.length < 3 || q.length > 40) {
    return res.status(400).json({ error: 'q must be 3 to 40 characters' });
  }
  const key = q.toLowerCase();
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < TTL_MS) return res.json(hit.body);

  try {
    const url = 'https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=' + encodeURIComponent(q);
    const upstream = await fetch(url, { signal: AbortSignal.timeout(8000) });
    if (!upstream.ok) throw new Error('TheSportsDB HTTP ' + upstream.status);
    const data = await upstream.json();
    // Flatten the large upstream payload into the small shape the phone needs.
    const teams = (data.teams || []).slice(0, 20).map((t) => ({
      id: String(t.idTeam),
      name: t.strTeam,
      league: t.strLeague || ''
    }));
    const body = { teams };
    cache.set(key, { at: Date.now(), body });
    return res.json(body);
  } catch (err) {
    console.error('Search failed:', err.message);
    return res.status(502).json({ error: 'Sports data provider unavailable' });
  }
});

/** Returns a trimmed string (max 40 chars) or null. */
const text = (v) => (typeof v === 'string' && v.trim() ? v.trim().slice(0, 40) : null);

app.post('/v1/predictions', requireAuth, async (req, res) => {
  const { matchId, result, score, scorer, safeBet } = req.body || {};
  if (typeof matchId !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(matchId)) {
    return res.status(400).json({ error: 'matchId is required (letters, numbers, - and _ only)' });
  }
  const doc = {
    matchId,
    result: text(result),
    score: text(score),
    scorer: text(scorer),
    safeBet: safeBet === true,
    source: 'api',
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  };
  try {
    await db.collection('users').doc(req.user.uid).collection('predictions').doc(matchId).set(doc);
    console.log(`Saved prediction ${matchId} for ${req.user.uid}`);
    return res.status(201).json({ saved: true, matchId });
  } catch (err) {
    console.error('Firestore write failed:', err.message);
    return res.status(500).json({ error: 'Could not save the prediction' });
  }
});

app.get('/v1/predictions', requireAuth, async (req, res) => {
  try {
    const snap = await db.collection('users').doc(req.user.uid).collection('predictions').get();
    return res.json({ predictions: snap.docs.map((d) => ({ id: d.id, ...d.data() })) });
  } catch (err) {
    console.error('Firestore read failed:', err.message);
    return res.status(500).json({ error: 'Could not read predictions' });
  }
});

app.use((_req, res) => res.status(404).json({ error: 'Not found' }));
// eslint-disable-next-line no-unused-vars
app.use((err, _req, res, _next) => {
  console.error('Unhandled error:', err.message);
  res.status(400).json({ error: 'Bad request' });
});

const port = process.env.PORT || 8080;
app.listen(port, () => console.log(`PitchPulse API listening on ${port}`));
