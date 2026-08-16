// ============================================================
// INFOLD News - Cloudflare Worker entry
//
// Handles:
//   - public APIs      /api/articles, /api/articles/:id, /api/categories, /api/search
//   - image serving    /api/images/:key  (R2)
//   - admin APIs       /api/admin/*      (session-protected)
//   - static assets    env.ASSETS with index.html SPA fallback
//   - cron trigger     scheduled()  -> handleScheduled()
// ============================================================

import { json, clampInt, nowISO, readBodyJson, slugify, isSecureRequest } from './util.js';
import {
  verifyPassword,
  createSession,
  deleteSession,
  requireAdmin,
  cookieHeader,
  clearCookieHeader,
  readCookie,
  COOKIE_NAME,
} from './auth.js';
import {
  handleScheduled,
  getAutomationStatus,
  setAutomationEnabled,
  runAutomationNow,
} from './automation.js';
import {
  registerReader,
  loginReader,
  logoutReader,
  meReader,
} from './reader-auth.js';
import {
  awardArticlePoints,
  spendPointsForAdFree,
  getAdFreeStatus,
  getPointHistory,
  getPointsSummary,
  requireReader,
} from './points.js';

const MAX_UPLOAD_BYTES = 5 * 1024 * 1024; // 5 MB
const ALLOWED_UPLOAD_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp', 'image/gif'];

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    try {
      // ---- API ----
      if (path.startsWith('/api')) {
        if (method === 'GET' && path.startsWith('/api/images/')) {
          return serveImage(request, env, path.slice('/api/images/'.length));
        }
        if (method === 'GET' && path === '/api/articles') return listArticles(env, url);
        if (method === 'GET' && /^\/api\/articles\/\d+$/.test(path)) return getArticle(env, path);
        if (method === 'GET' && path === '/api/categories') return listCategories(env);
        if (method === 'GET' && path === '/api/search') return searchArticles(env, url);
        if (method === 'GET' && path === '/api/ogp') return fetchOgpPreview(url);
        if (method === 'POST' && path === '/api/translate') return translateArticle(request, env);
        // ---- reader auth & points (points feature) ----
        if (
          path === '/api/auth/register' || path === '/api/auth/login' || path === '/api/auth/logout' ||
          path === '/api/auth/me' || path === '/api/points' || path === '/api/points/history' ||
          path === '/api/points/ad-free' || /^\/api\/articles\/\d+\/complete$/.test(path)
        ) {
          return handleReader(request, env, url, path, method);
        }
        if (path.startsWith('/api/admin')) return handleAdmin(request, env, url, path, method);
        return json({ error: 'not_found' }, 404);
      }

      // ---- PWA files (manifest / service worker / digital asset links) ----
      // ・sw.js / manifest.json: 更新を確実に反映させるためキャッシュさせない
      // ・.well-known/assetlinks.json: TWA 検証用。存在しない場合は SPA
      //   フォールバック（index.html）を返さず 404 にする
      if (
        method === 'GET' &&
        (path === '/sw.js' || path === '/manifest.json' || path === '/.well-known/assetlinks.json')
      ) {
        const pwaResp = await env.ASSETS.fetch(request);
        const pwaHeaders = new Headers(pwaResp.headers);
        pwaHeaders.set('Cache-Control', 'no-cache, must-revalidate');
        if (path === '/.well-known/assetlinks.json') {
          pwaHeaders.set('Content-Type', 'application/json');
        }
        return new Response(pwaResp.body, { status: pwaResp.status, headers: pwaHeaders });
      }

      // ---- Static assets (SPA) ----
      if (method === 'GET' && env.ASSETS) {
        const assetResp = await env.ASSETS.fetch(request);
        if (assetResp.status === 200) {
          // index.html contains the whole UI/CSS — never let browsers or the
          // edge cache serve a stale copy, or phones show an outdated design.
          if ((assetResp.headers.get('content-type') || '').includes('text/html')) {
            const headers = new Headers(assetResp.headers);
            headers.set('Cache-Control', 'no-cache, must-revalidate');
            return new Response(assetResp.body, { status: 200, headers });
          }
          return assetResp;
        }
        // SPA fallback: unknown front-end route -> index.html
        // Note: fetch '/' (not '/index.html') because Cloudflare redirects
        // /index.html -> / with 307, which would defeat the SPA fallback.
        const indexUrl = new URL('/', url);
        const fallback = await env.ASSETS.fetch(new Request(indexUrl.toString(), request));
        const fbHeaders = new Headers(fallback.headers);
        fbHeaders.set('Cache-Control', 'no-cache, must-revalidate');
        return new Response(fallback.body, { status: fallback.status, headers: fbHeaders });
      }
      return new Response('Not found', { status: 404 });
    } catch (err) {
      console.error('Unhandled error:', err);
      return json({ error: 'server_error', message: String((err && err.message) || err) }, 500);
    }
  },

  async scheduled(event, env, ctx) {
    ctx.waitUntil(
      handleScheduled(env).catch((err) => {
        console.error('Scheduled automation error:', err);
      })
    );
  },
};

// ============================================================
// Admin API
// ============================================================

async function handleAdmin(request, env, url, path, method) {
  // login / logout do not require an existing session
  if (method === 'POST' && path === '/api/admin/login') return login(request, env);
  if (method === 'POST' && path === '/api/admin/logout') return logout(request, env);

  const { user, response } = await requireAdmin(env, request);
  if (response) return response;

  // CSRF defense: same-origin (or configured Pages origin) + custom header for mutations
  if (method === 'POST' || method === 'PUT' || method === 'DELETE') {
    if (request.headers.get('X-Requested-With') !== 'XMLHttpRequest') {
      return json({ error: 'csrf_rejected' }, 403);
    }
    const origin = request.headers.get('Origin');
    if (origin) {
      try {
        // When the frontend is hosted on Cloudflare Pages and proxies /api/* to
        // this Worker, the Origin is the Pages host, not this Worker's host.
        // Allow it via the ALLOWED_ORIGINS var (comma-separated host list).
        const allowed = (env.ALLOWED_ORIGINS || '')
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean);
        const host = new URL(origin).host;
        if (host !== url.host && !allowed.includes(host)) {
          return json({ error: 'csrf_rejected' }, 403);
        }
      } catch {
        return json({ error: 'csrf_rejected' }, 403);
      }
    }
  }

  // ---- auth state ----
  if (method === 'GET' && path === '/api/admin/me') return json({ user });

  // ---- stats / dashboard ----
  if (method === 'GET' && path === '/api/admin/stats') return adminStats(env);

  // ---- articles ----
  if (method === 'GET' && path === '/api/admin/articles') return adminListArticles(env, url);
  if (method === 'GET' && /^\/api\/admin\/articles\/\d+$/.test(path)) return adminGetArticle(env, path);
  if (method === 'POST' && path === '/api/admin/articles') return adminCreateArticle(env, request);
  if (method === 'PUT' && /^\/api\/admin\/articles\/\d+$/.test(path)) return adminUpdateArticle(env, request, path);
  if (method === 'DELETE' && /^\/api\/admin\/articles\/\d+$/.test(path)) return adminDeleteArticle(env, path);

  // ---- R2 upload ----
  if (method === 'POST' && path === '/api/admin/upload') return adminUpload(env, request);

  // ---- automation ----
  if (method === 'GET' && path === '/api/admin/automation') return json(await getAutomationStatus(env));
  if (method === 'POST' && path === '/api/admin/automation/toggle') {
    const body = await readBodyJson(request);
    const enabled = !!body?.enabled;
    await setAutomationEnabled(env, enabled);
    return json({ ok: true, enabled });
  }
  if (method === 'POST' && path === '/api/admin/automation/run') {
    const result = await runAutomationNow(env);
    if (result.ok) return json({ ok: true, articleId: result.articleId });
    return json({ ok: false, code: result.code, error: result.error }, 409);
  }

  // ---- categories ----
  if (method === 'POST' && path === '/api/admin/categories') return adminCreateCategory(env, request);
  if (method === 'PUT' && /^\/api\/admin\/categories\/\d+$/.test(path)) return adminUpdateCategory(env, request, path);
  if (method === 'DELETE' && /^\/api\/admin\/categories\/\d+$/.test(path)) return adminDeleteCategory(env, path);

  return json({ error: 'not_found' }, 404);
}

async function login(request, env) {
  const body = await readBodyJson(request);
  const email = String(body?.email || '').trim().toLowerCase();
  const password = String(body?.password || '');
  if (!email || !password) return json({ error: 'invalid_request' }, 400);

  const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(email).first();
  if (!user) return json({ error: 'invalid_credentials' }, 401);
  const valid = await verifyPassword(password, user.password_hash);
  if (!valid) return json({ error: 'invalid_credentials' }, 401);

  const token = await createSession(env, user.id);
  return json(
    { ok: true, user: { id: user.id, email: user.email, name: user.name, role: user.role } },
    200,
    { 'Set-Cookie': cookieHeader(token, isSecureRequest(request)) }
  );
}

async function logout(request, env) {
  const token = readCookie(request, COOKIE_NAME);
  await deleteSession(env, token);
  return json({ ok: true }, 200, { 'Set-Cookie': clearCookieHeader() });
}

// ============================================================
// Reader auth & points API (points feature)
// ============================================================

/** Same CSRF rules as the admin APIs: custom header + same-origin check. */
function csrfOk(request, env, url) {
  if (request.headers.get('X-Requested-With') !== 'XMLHttpRequest') return false;
  const origin = request.headers.get('Origin');
  if (origin) {
    try {
      const allowed = (env.ALLOWED_ORIGINS || '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);
      const host = new URL(origin).host;
      if (host !== url.host && !allowed.includes(host)) return false;
    } catch {
      return false;
    }
  }
  return true;
}

async function handleReader(request, env, url, path, method) {
  if (method === 'POST' && !csrfOk(request, env, url)) {
    return json({ error: 'csrf_rejected' }, 403);
  }

  // ---- auth ----
  if (method === 'POST' && path === '/api/auth/register') return registerReader(env, request);
  if (method === 'POST' && path === '/api/auth/login') return loginReader(env, request);
  if (method === 'POST' && path === '/api/auth/logout') return logoutReader(env, request);
  if (method === 'GET' && path === '/api/auth/me') return meReader(env, request);

  // ---- points (reader login required) ----
  if (method === 'POST' && /^\/api\/articles\/\d+\/complete$/.test(path)) {
    const { reader, response } = await requireReader(env, request);
    if (response) return response;
    const articleId = Number(path.split('/')[3]);
    const res = await awardArticlePoints(env, reader, articleId);
    if (res.error === 'not_found') return json({ error: 'not_found' }, 404);
    return json(res);
  }
  if (method === 'GET' && path === '/api/points') {
    const { reader, response } = await requireReader(env, request);
    if (response) return response;
    return json(await getPointsSummary(env, reader));
  }
  if (method === 'GET' && path === '/api/points/history') {
    const { reader, response } = await requireReader(env, request);
    if (response) return response;
    const limit = clampInt(url.searchParams.get('limit'), 1, 100, 50);
    return json({ history: await getPointHistory(env, reader.id, limit) });
  }
  if (method === 'POST' && path === '/api/points/ad-free') {
    const { reader, response } = await requireReader(env, request);
    if (response) return response;
    const body = await readBodyJson(request);
    const plan = clampInt(body?.plan, 0, 100000, 0);
    const res = await spendPointsForAdFree(env, reader, plan);
    if (res.error === 'invalid_plan') return json({ error: 'invalid_plan' }, 400);
    if (res.error === 'insufficient_points') return json({ error: 'insufficient_points', points: res.points }, 409);
    return json(res);
  }
  if (method === 'GET' && path === '/api/points/ad-free') {
    const { reader, response } = await requireReader(env, request);
    if (response) return response;
    return json(await getAdFreeStatus(env, reader.id));
  }
  return json({ error: 'not_found' }, 404);
}

// ============================================================
// Public API
// ============================================================

async function listArticles(env, url) {
  const page = clampInt(url.searchParams.get('page'), 1, 1000, 1);
  const limit = clampInt(url.searchParams.get('limit'), 1, 50, 12);
  const category = url.searchParams.get('category');
  const featured = url.searchParams.get('featured') === '1' ? 1 : null;
  const exclude = url.searchParams.get('exclude');

  const where = ["status = 'published'"];
  const params = [];
  if (category) {
    where.push('category = ?');
    params.push(category);
  }
  if (featured !== null) {
    where.push('featured = ?');
    params.push(featured);
  }
  if (exclude) {
    where.push('id != ?');
    params.push(clampInt(exclude, 1, 1e12, 0));
  }
  const whereSql = where.join(' AND ');

  const countRow = await env.DB.prepare(`SELECT COUNT(*) AS total FROM articles WHERE ${whereSql}`).bind(...params).first();
  // Prioritise INFOLD originals (AI-generated + hand-written) over external
  // imports: non-ITFIS rows sort first, then featured, then newest.
  const rows = await env.DB.prepare(
    `SELECT id, title, description, thumbnail, category, featured, source_url, source_name, published_at, is_automated
     FROM articles WHERE ${whereSql}
     ORDER BY (source_name = 'ITFIS') ASC, featured DESC, published_at DESC, id DESC
     LIMIT ? OFFSET ?`
  ).bind(...params, limit, (page - 1) * limit).all();

  return json({ articles: rows.results || [], total: countRow?.total || 0, page, limit });
}

async function getArticle(env, path) {
  const id = Number(path.split('/')[3]);
  const row = await env.DB.prepare(
    `SELECT id, title, description, content, thumbnail, category, featured,
            source_url, source_name, published_at, created_at, updated_at, is_automated
     FROM articles WHERE id = ? AND status = 'published'`
  ).bind(id).first();
  if (!row) return json({ error: 'not_found' }, 404);

  const related = await env.DB.prepare(
    `SELECT id, title, description, thumbnail, category, published_at
     FROM articles WHERE status = 'published' AND category = ? AND id != ?
     ORDER BY published_at DESC, id DESC LIMIT 3`
  ).bind(row.category, id).all();

  return json({ article: row, related: related.results || [] });
}

async function listCategories(env) {
  const rows = await env.DB.prepare(
    `SELECT c.id, c.name, c.name_en, c.slug, c.sort_order,
       (SELECT COUNT(*) FROM articles a WHERE a.category = c.slug AND a.status = 'published') AS article_count
     FROM categories c ORDER BY c.sort_order ASC, c.id ASC`
  ).all();
  return json({ categories: rows.results || [] });
}

async function searchArticles(env, url) {
  const q = String(url.searchParams.get('q') || '').trim();
  if (!q) return json({ articles: [], total: 0, q: '' });
  const like = `%${q}%`;
  const rows = await env.DB.prepare(
    `SELECT id, title, description, thumbnail, category, featured, source_name, published_at
     FROM articles WHERE status = 'published'
       AND (title LIKE ? OR description LIKE ? OR content LIKE ? OR category LIKE ?)
     ORDER BY (source_name = 'ITFIS') ASC, featured DESC, published_at DESC, id DESC LIMIT 50`
  ).bind(like, like, like, like).all();
  return json({ articles: rows.results || [], total: (rows.results || []).length, q });
}

// ============================================================
// Admin API: stats & articles
// ============================================================

async function adminStats(env) {
  const total = await env.DB.prepare('SELECT COUNT(*) AS c FROM articles').first();
  const published = await env.DB.prepare("SELECT COUNT(*) AS c FROM articles WHERE status = 'published'").first();
  const drafts = await env.DB.prepare("SELECT COUNT(*) AS c FROM articles WHERE status = 'draft'").first();
  const featured = await env.DB.prepare('SELECT COUNT(*) AS c FROM articles WHERE featured = 1').first();
  const latest = await env.DB.prepare(
    `SELECT id, title, category, status, featured, published_at, updated_at
     FROM articles ORDER BY updated_at DESC, id DESC LIMIT 5`
  ).all();
  const automation = await getAutomationStatus(env);
  return json({
    stats: {
      total: total?.c || 0,
      published: published?.c || 0,
      drafts: drafts?.c || 0,
      featured: featured?.c || 0,
    },
    latest: latest.results || [],
    automation,
  });
}

async function adminListArticles(env, url) {
  const status = url.searchParams.get('status');
  const q = String(url.searchParams.get('q') || '').trim();
  const conds = [];
  const params = [];
  if (status === 'draft' || status === 'published') {
    conds.push('status = ?');
    params.push(status);
  }
  if (q) {
    conds.push('(title LIKE ? OR description LIKE ?)');
    params.push(`%${q}%`, `%${q}%`);
  }
  const whereSql = conds.length ? ` WHERE ${conds.join(' AND ')}` : '';
  const rows = await env.DB.prepare(
    `SELECT id, title, description, thumbnail, category, status, featured,
            source_name, published_at, created_at, updated_at
     FROM articles${whereSql} ORDER BY updated_at DESC, id DESC LIMIT 200`
  ).bind(...params).all();
  return json({ articles: rows.results || [] });
}

async function adminGetArticle(env, path) {
  const id = Number(path.split('/')[4]);
  const row = await env.DB.prepare('SELECT * FROM articles WHERE id = ?').bind(id).first();
  if (!row) return json({ error: 'not_found' }, 404);
  return json({ article: row });
}

async function adminCreateArticle(env, request) {
  const body = await readBodyJson(request);
  const data = validateArticleInput(body);
  if (data.error) return json({ error: data.error }, 400);

  const publishedAt = data.publishedAt || (data.status === 'published' ? nowISO() : null);
  const res = await env.DB.prepare(
    `INSERT INTO articles
       (title, description, content, thumbnail, category, status, featured,
        source_url, source_name, published_at, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    data.title, data.description, data.content, data.thumbnail, data.category,
    data.status, data.featured, data.sourceUrl, data.sourceName, publishedAt,
    nowISO(), nowISO()
  ).run();
  return json({ ok: true, id: Number(res.meta.last_row_id) }, 201);
}

async function adminUpdateArticle(env, request, path) {
  const id = Number(path.split('/')[4]);
  const existing = await env.DB.prepare('SELECT * FROM articles WHERE id = ?').bind(id).first();
  if (!existing) return json({ error: 'not_found' }, 404);

  const body = await readBodyJson(request);
  const data = validateArticleInput(body);
  if (data.error) return json({ error: data.error }, 400);

  const publishedAt = data.publishedAt || (data.status === 'published' && !existing.published_at ? nowISO() : existing.published_at);
  await env.DB.prepare(
    `UPDATE articles SET title=?, description=?, content=?, thumbnail=?, category=?, status=?,
       featured=?, source_url=?, source_name=?, published_at=?, updated_at=? WHERE id=?`
  ).bind(
    data.title, data.description, data.content, data.thumbnail, data.category,
    data.status, data.featured, data.sourceUrl, data.sourceName, publishedAt,
    nowISO(), id
  ).run();
  return json({ ok: true, id });
}

async function adminDeleteArticle(env, path) {
  const id = Number(path.split('/')[4]);
  const row = await env.DB.prepare('SELECT thumbnail FROM articles WHERE id = ?').bind(id).first();
  if (!row) return json({ error: 'not_found' }, 404);

  await env.DB.prepare('DELETE FROM articles WHERE id = ?').bind(id).run();

  // Best-effort cleanup of the R2 object for thumbnails we manage
  if (env.IMAGES && row.thumbnail && row.thumbnail.startsWith('/api/images/')) {
    const key = row.thumbnail.slice('/api/images/'.length);
    await env.IMAGES.delete(key).catch(() => {});
  }
  return json({ ok: true });
}

function validateArticleInput(body) {
  const status = body?.status === 'published' ? 'published' : 'draft';
  const title = String(body?.title || '').trim();
  if (!title) return { error: 'title_required' };
  return {
    title: title.slice(0, 200),
    description: String(body?.description || '').slice(0, 2000),
    content: String(body?.content || '').slice(0, 100000),
    thumbnail: String(body?.thumbnail || '').slice(0, 1000),
    category: String(body?.category || 'other').trim().slice(0, 50),
    status,
    featured: body?.featured ? 1 : 0,
    sourceUrl: String(body?.sourceUrl || '').slice(0, 1000) || null,
    sourceName: String(body?.sourceName || '').slice(0, 200) || null,
    publishedAt: body?.publishedAt ? String(body.publishedAt).slice(0, 32) : null,
  };
}

// ============================================================
// Admin API: R2 image upload / serving
// ============================================================

async function adminUpload(env, request) {
  if (!env.IMAGES) {
    return json({ error: 'r2_disabled', message: 'R2 storage is not configured yet. Enable R2 in the Cloudflare Dashboard first.' }, 503);
  }
  const body = await readBodyJson(request);
  const contentType = String(body?.contentType || '').toLowerCase();
  const data = String(body?.data || '');
  if (!ALLOWED_UPLOAD_TYPES.includes(contentType)) {
    return json({ error: 'invalid_type', message: 'Only PNG / JPEG / WebP / GIF images are allowed' }, 400);
  }
  if (!data) return json({ error: 'no_data' }, 400);

  let bytes;
  try {
    bytes = Uint8Array.from(atob(data), (c) => c.charCodeAt(0));
  } catch {
    return json({ error: 'invalid_data' }, 400);
  }
  if (bytes.byteLength > MAX_UPLOAD_BYTES) {
    return json({ error: 'too_large', message: 'Image exceeds 5 MB limit' }, 413);
  }

  const ext = (contentType.split('/')[1] || 'png').replace('jpeg', 'jpg');
  const key = `uploads/${Date.now()}-${Math.random().toString(36).slice(2, 10)}.${ext}`;
  await env.IMAGES.put(key, bytes, {
    httpMetadata: { contentType, cacheControl: 'public, max-age=31536000, immutable' },
  });
  return json({ ok: true, url: `/api/images/${key}` }, 201);
}

async function serveImage(request, env, key) {
  if (!env.IMAGES) {
    return json({ error: 'r2_disabled' }, 503);
  }
  if (!key || !/^[a-zA-Z0-9/._-]+$/.test(key) || key.includes('..')) {
    return json({ error: 'not_found' }, 404);
  }
  const obj = await env.IMAGES.get(key);
  if (!obj) return json({ error: 'not_found' }, 404);

  const etag = obj.httpEtag;
  if (request.headers.get('If-None-Match') === etag) {
    return new Response(null, { status: 304, headers: { ETag: etag } });
  }
  return new Response(obj.body, {
    headers: {
      'Content-Type': obj.httpMetadata?.contentType || 'application/octet-stream',
      'Cache-Control': obj.httpMetadata?.cacheControl || 'public, max-age=86400',
      ETag: etag,
    },
  });
}

// ============================================================
// Article translation (Gemini — separate key/model from auto-posting)
// ============================================================

const TRANSLATION_MODEL_DEFAULT = 'gemini-3.1-flash-lite';

async function translateArticle(request, env) {
  const apiKey = env.TRANSLATION_API_KEY;
  if (!apiKey) return json({ error: 'translation_not_configured' }, 503);
  const body = await readBodyJson(request);
  const text = String((body && body.text) || '').trim();
  const target = String((body && body.target) || 'en').trim();
  if (!text) return json({ error: 'empty_text' }, 400);
  if (text.length > 20000) return json({ error: 'text_too_long' }, 400);
  const model = env.TRANSLATION_MODEL || TRANSLATION_MODEL_DEFAULT;
  const langName = target === 'ja' ? 'Japanese' : 'English';

  const prompt =
    `You are a professional news translator. Translate the following news article into ${langName}.\n` +
    `Keep the markdown-ish structure: keep lines starting with ## as headings, keep '- ' list items, keep blank lines between paragraphs.\n` +
    `Keep URLs and **bold** markers unchanged. Do not add commentary.\n` +
    `Return ONLY the translated text.\n\n` +
    `ARTICLE:\n${text}`;

  try {
    const resp = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.3, maxOutputTokens: 16384 },
        }),
      }
    );
    if (!resp.ok) {
      const errText = await resp.text().catch(() => '');
      return json({ error: 'translate_failed', message: `HTTP ${resp.status} ${errText.slice(0, 200)}` }, 502);
    }
    const data = await resp.json();
    const translated =
      (((data.candidates && data.candidates[0] && data.candidates[0].content) || {}).parts || [])
        .map((p) => p.text || '')
        .join('')
        .trim();
    if (!translated) return json({ error: 'empty_translation' }, 502);
    return json({ translated });
  } catch (err) {
    return json({ error: 'translate_failed', message: String((err && err.message) || err).slice(0, 200) }, 502);
  }
}

// ============================================================
// OGP link preview (for link cards in article content)
// ============================================================

function ogpDecodeEntities(s) {
  return String(s || '')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_m, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_m, h) => String.fromCharCode(parseInt(h, 16)));
}

async function fetchOgpPreview(url) {
  const raw = (url.searchParams.get('url') || '').trim();
  let target;
  try { target = new URL(raw); } catch (_) { return json({ error: 'invalid_url' }, 400); }
  if (!/^https?:$/.test(target.protocol)) return json({ error: 'invalid_url' }, 400);
  // SSRF guard: block localhost / private / link-local hosts.
  const host = target.hostname.toLowerCase();
  if (
    host === 'localhost' || host.endsWith('.local') ||
    /^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.|0\.)/.test(host) ||
    host === '[::1]' || host.startsWith('fe80:')
  ) {
    return json({ error: 'blocked' }, 400);
  }
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 6000);
    const resp = await fetch(target.toString(), {
      headers: { 'User-Agent': 'Mozilla/5.0 (compatible; INFOLD-OGP/1.0)' },
      redirect: 'follow',
      signal: controller.signal,
    });
    clearTimeout(timer);
    const ct = resp.headers.get('content-type') || '';
    if (!ct.includes('text/html')) return json({ url: target.toString(), title: '', description: '', image: '' });
    const html = (await resp.text()).slice(0, 300000);
    const grab = (prop) => {
      const re = new RegExp('<meta[^>]+(?:property|name)=["\']' + prop + '["\'][^>]*content=["\']([^"\']*)["\']', 'i');
      const m = html.match(re);
      return m ? ogpDecodeEntities(m[1]).trim() : '';
    };
    const title = grab('og:title') || grab('twitter:title') || (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1]?.trim().slice(0, 200) || '';
    const description = grab('og:description') || grab('twitter:description') || grab('description');
    let image = grab('og:image') || grab('twitter:image');
    if (image && /^\/\//.test(image)) image = 'https:' + image;
    else if (image && !/^https?:\/\//i.test(image)) image = new URL(image, target).toString();
    return json({ url: target.toString(), title: title.slice(0, 200), description: description.slice(0, 300), image });
  } catch (err) {
    return json({ error: 'fetch_failed', message: String((err && err.message) || err).slice(0, 200) }, 502);
  }
}

// ============================================================
// Admin API: categories
// ============================================================

async function adminCreateCategory(env, request) {
  const body = await readBodyJson(request);
  const name = String(body?.name || '').trim();
  const nameEn = String(body?.nameEn || '').trim() || name;
  const slug = String(body?.slug || '').trim() || slugify(name);
  if (!name || !slug) return json({ error: 'invalid_request' }, 400);
  try {
    const res = await env.DB.prepare(
      `INSERT INTO categories (name, name_en, slug, sort_order)
       VALUES (?, ?, ?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories))`
    ).bind(name, nameEn, slug).run();
    return json({ ok: true, id: Number(res.meta.last_row_id) }, 201);
  } catch (err) {
    return json({ error: 'duplicate_slug', message: String(err?.message || '') }, 409);
  }
}

async function adminUpdateCategory(env, request, path) {
  const id = Number(path.split('/')[4]);
  const body = await readBodyJson(request);
  const name = String(body?.name || '').trim();
  const nameEn = String(body?.nameEn || '').trim() || name;
  const slug = String(body?.slug || '').trim();
  if (!name || !slug) return json({ error: 'invalid_request' }, 400);
  const res = await env.DB.prepare(
    'UPDATE categories SET name=?, name_en=?, slug=?, sort_order=? WHERE id=?'
  ).bind(name, nameEn, slug, clampInt(body?.sortOrder, 0, 1000, 0), id).run();
  if (res.meta.changes === 0) return json({ error: 'not_found' }, 404);
  return json({ ok: true });
}

async function adminDeleteCategory(env, path) {
  const id = Number(path.split('/')[4]);
  const res = await env.DB.prepare('DELETE FROM categories WHERE id=?').bind(id).run();
  if (res.meta.changes === 0) return json({ error: 'not_found' }, 404);
  return json({ ok: true });
}
