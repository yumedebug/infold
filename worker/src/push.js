// ============================================================
// INFOLD News - Firebase Cloud Messaging (FCM) push notifications
//
// The Android app registers its FCM device token via /api/push/register.
// The hourly cron (scheduled()) picks up articles published since the last
// run and sends an FCM notification (title: INFOLD, body: article title)
// to every registered device. Tapping the notification opens the native
// article detail screen (article_id is carried in the FCM data payload).
//
// Configuration (worker secrets, all optional - when unset the module is a
// harmless no-op so the site keeps working):
//   FCM_PROJECT_ID       Firebase project id (e.g. infold-news)
//   FCM_CLIENT_EMAIL     service account email (xxx@infold-news.iam.gserviceaccount.com)
//   FCM_PRIVATE_KEY      service account private key (PEM), base64-encoded
//
// FCM HTTP v1 API requires an OAuth2 access token minted from the service
// account (JWT -> google oauth2 token endpoint). No third-party libraries:
// signing uses the Workers Web Crypto (RS256).
// ============================================================

import { json, nowISO, readBodyJson } from './util.js';

// Articles published within this window (plus a margin over the hourly cron)
// are candidates for a push notification.
const PUSH_WINDOW_MS = 70 * 60 * 1000;

const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
const FCM_TOKEN_URL = 'https://oauth2.googleapis.com/token';

// ------------------------------------------------------------
// Device token registration API
// ------------------------------------------------------------

export async function registerPushDevice(request, env) {
  const body = await readBodyJson(request);
  const token = String(body?.token || '').trim();
  const platform = String(body?.platform || 'android').trim().slice(0, 20);
  if (!token || token.length < 10 || token.length > 4096) {
    return json({ error: 'invalid_token' }, 400);
  }
  const now = nowISO();
  await env.DB.prepare(
    `INSERT INTO push_devices (token, platform, created_at, updated_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(token) DO UPDATE SET platform = excluded.platform, updated_at = excluded.updated_at`
  ).bind(token, platform, now, now).run();
  return json({ ok: true });
}

export async function unregisterPushDevice(request, env) {
  const body = await readBodyJson(request);
  const token = String(body?.token || '').trim();
  if (!token) return json({ ok: true });
  await env.DB.prepare('DELETE FROM push_devices WHERE token = ?').bind(token).run();
  return json({ ok: true });
}

// ------------------------------------------------------------
// Scheduled push sender (called from scheduled())
// ------------------------------------------------------------

export async function handlePushNotifications(env) {
  if (!isFcmConfigured(env)) return { ok: false, reason: 'fcm not configured' };

  const cutoff = new Date(Date.now() - PUSH_WINDOW_MS).toISOString();
  const rows = await env.DB.prepare(
    `SELECT a.id, a.title
     FROM articles a
     WHERE a.status = 'published' AND a.published_at > ?
       AND NOT EXISTS (SELECT 1 FROM push_notifications p WHERE p.article_id = a.id)
     ORDER BY a.published_at ASC LIMIT 20`
  ).bind(cutoff).all();

  const articles = rows.results || [];
  if (!articles.length) return { ok: true, sent: 0 };

  const devices = await env.DB.prepare(
    'SELECT token FROM push_devices ORDER BY id DESC LIMIT 5000'
  ).all();
  const tokens = (devices.results || []).map((d) => d.token);

  let sent = 0;
  for (const article of articles) {
    if (!tokens.length) break;
    const results = await Promise.allSettled(
      tokens.map((token) => sendFcmMessage(env, token, article).catch(async (err) => {
        // Unregistered / invalid tokens are cleaned up lazily.
        if (err && (err.status === 404 || err.status === 400)) {
          await env.DB.prepare('DELETE FROM push_devices WHERE token = ?').bind(token).run();
        }
        return null;
      }))
    );
    const okCount = results.filter((r) => r.status === 'fulfilled' && r.value === true).length;
    if (okCount > 0) {
      await env.DB.prepare(
        'INSERT OR IGNORE INTO push_notifications (article_id, sent_at) VALUES (?, ?)'
      ).bind(article.id, nowISO()).run();
      sent += 1;
    }
  }
  return { ok: true, sent };
}

/** Fire-and-forget notify for admin-published articles (best effort). */
export async function notifyArticlePublished(env, articleId) {
  if (!isFcmConfigured(env)) return;
  try {
    const row = await env.DB.prepare(
      `SELECT a.id, a.title FROM articles a
       WHERE a.id = ? AND a.status = 'published'
         AND NOT EXISTS (SELECT 1 FROM push_notifications p WHERE p.article_id = a.id)`
    ).bind(articleId).first();
    if (!row) return;
    const devices = await env.DB.prepare(
      'SELECT token FROM push_devices ORDER BY id DESC LIMIT 5000'
    ).all();
    const tokens = (devices.results || []).map((d) => d.token);
    if (!tokens.length) return;
    await Promise.allSettled(tokens.map((token) => sendFcmMessage(env, token, row)));
    await env.DB.prepare(
      'INSERT OR IGNORE INTO push_notifications (article_id, sent_at) VALUES (?, ?)'
    ).bind(articleId, nowISO()).run();
  } catch (err) {
    console.log(`[push] notifyArticlePublished failed: ${String((err && err.message) || err).slice(0, 300)}`);
  }
}

function isFcmConfigured(env) {
  return Boolean(env.FCM_PROJECT_ID && env.FCM_CLIENT_EMAIL && env.FCM_PRIVATE_KEY);
}

// ------------------------------------------------------------
// FCM HTTP v1 send
// ------------------------------------------------------------

async function sendFcmMessage(env, token, article) {
  const accessToken = await getFcmAccessToken(env);
  if (!accessToken) throw new Error('no access token');

  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(env.FCM_PROJECT_ID)}/messages:send`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        message: {
          token,
          notification: {
            title: 'INFOLD',
            body: String(article.title || '新しい記事が公開されました').slice(0, 200),
          },
          data: {
            article_id: String(article.id),
            click_action: 'OPEN_ARTICLE',
          },
          android: {
            notification: {
              channel_id: 'new_articles',
              priority: 'HIGH',
            },
          },
        },
      }),
    }
  );

  if (!resp.ok) {
    const err = new Error(`FCM HTTP ${resp.status}`);
    err.status = resp.status;
    throw err;
  }
  return true;
}

// ------------------------------------------------------------
// Service-account JWT -> OAuth2 access token
// ------------------------------------------------------------

async function getFcmAccessToken(env) {
  const clientEmail = env.FCM_CLIENT_EMAIL;
  const keyBase64 = env.FCM_PRIVATE_KEY;

  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = {
    iss: clientEmail,
    scope: FCM_SCOPE,
    aud: FCM_TOKEN_URL,
    iat: now,
    exp: now + 3600,
  };

  const enc = (obj) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const signingInput = `${enc(header)}.${enc(claims)}`;

  const key = await importServiceAccountKey(keyBase64);
  const sigBytes = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(signingInput)
  );
  const signature = btoa(String.fromCharCode(...new Uint8Array(sigBytes)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  const jwt = `${signingInput}.${signature}`;

  const resp = await fetch(FCM_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}&assertion=${encodeURIComponent(jwt)}`,
  });
  if (!resp.ok) throw new Error(`OAuth token HTTP ${resp.status}`);
  const data = await resp.json();
  return data && data.access_token ? data.access_token : null;
}

/** Decode the base64-encoded PEM and import it as an RSA private key. */
async function importServiceAccountKey(keyBase64) {
  let pem = keyBase64;
  // Accept a raw PEM too (defensive): if it already looks like a PEM, use it.
  if (!/^-----BEGIN/.test(pem.trim())) {
    try {
      pem = atob(String(keyBase64).trim());
    } catch {
      throw new Error('FCM_PRIVATE_KEY must be base64-encoded PEM');
    }
  }
  const body = pem
    .replace(/-----BEGIN [^-]+-----/, '')
    .replace(/-----END [^-]+-----/, '')
    .replace(/\s+/g, '');
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return crypto.subtle.importKey(
    'pkcs8',
    bytes,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
}
