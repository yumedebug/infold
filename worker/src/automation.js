// ============================================================
// INFOLD News - automated posting
//
// Flow: Google News RSS -> duplicate check -> Gemini (gemini-flash-latest)
//       -> category detection -> thumbnail (SVG) -> R2 -> D1 -> publish
//
// Cron runs hourly (0 * * * *) - the tightest Free-plan interval. ITFIS
// articles are imported on every tick; the state machine in automation_state
// decides whether the daily Gemini attempt is due:
//   - 08:00 JST (first attempt) or later -> start
//   - failure -> status 'retrying', next_retry_at = now + 30 min
//   - success -> status 'success' (never runs again that day)
//   - too many failures -> status 'failed' (stops for the day)
// ============================================================

import { jstNow, jstDateStr, jstDateTime, nowISO } from './util.js';

const CRON_START_MINUTES = 8 * 60; // 08:00 Asia/Tokyo
const RETRY_INTERVAL_MINUTES = 30;
const MAX_ATTEMPTS = 6;
const GEMINI_MODEL = 'gemini-flash-latest';

// RSS sources tried in order. Google News (Technology topic) is preferred, but
// it sometimes blocks Cloudflare Workers IPs (HTTP 503), so we fall back to
// gadget/tech-focused feeds. All sources are IT/gadget themed so the daily
// AI auto-post always covers tech topics (candidates are also filtered by
// detectCategory below as a safety net).
const RSS_SOURCES = [
  { name: 'Google News', url: 'https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja&topic=tc' },
  { name: 'GIZMODO', url: 'https://www.gizmodo.jp/index.xml' },
  { name: 'GIGAZINE', url: 'https://gigazine.net/news/rss_2.0/' },
];

/** Effective Gemini model: env override wins, otherwise the default constant */
const getModel = (env) => env.GEMINI_MODEL || GEMINI_MODEL;

const CATEGORY_ALIASES = {
  it: 'it', IT: 'it', 'it・テクノロジー': 'it', technology: 'it', tech: 'it',
  ai: 'ai', AI: 'ai', '人工知能': 'ai',
  windows: 'windows', Windows: 'windows',
  android: 'android', Android: 'android',
  apple: 'apple', Apple: 'apple', iOS: 'apple', macOS: 'apple', iphone: 'apple', mac: 'apple',
  web: 'web', Web: 'web', 'ウェブ': 'web',
  programming: 'programming', Programming: 'programming', 'プログラミング': 'programming', developer: 'programming',
  other: 'other', その他: 'other', Other: 'other',
};

const CATEGORY_COLORS = {
  it: '#2563eb',
  ai: '#7c3aed',
  windows: '#0284c7',
  android: '#16a34a',
  apple: '#64748b',
  web: '#db2777',
  programming: '#ea580c',
  other: '#475569',
};

const SLUG_TO_NAME = {
  it: 'IT', ai: 'AI', windows: 'Windows', android: 'Android',
  apple: 'Apple', web: 'Web', programming: 'Programming', other: 'その他',
};

// ------------------------------------------------------------
// Cron entry point
// ------------------------------------------------------------

export async function handleScheduled(env) {
  // ITFIS full-text import: streams itfis.net articles via RSS (no AI).
  // Runs on every scheduled tick, independent of the daily Gemini pipeline.
  await importItfis(env);

  const now = jstNow();
  const today = jstDateStr(now);

  const enabled = (await getSetting(env, 'automation_enabled')) !== '0';
  if (!enabled) return { ok: false, reason: 'automation disabled' };

  const row = await env.DB.prepare('SELECT * FROM automation_state WHERE date = ?').bind(today).first();
  const nowMin = now.getHours() * 60 + now.getMinutes();

  // No state for today yet -> start the pipeline at/after 08:00 JST
  if (!row) {
    if (nowMin < CRON_START_MINUTES) return { ok: false, reason: 'before 08:00' };
    const res = await env.DB.prepare(
      `INSERT OR IGNORE INTO automation_state
         (date, status, last_attempt_at, next_retry_at, attempt_count, created_at, updated_at)
       VALUES (?, 'running', ?, ?, 1, ?, ?)`
    ).bind(today, nowISO(), new Date(Date.now() + RETRY_INTERVAL_MINUTES * 60000).toISOString(), nowISO(), nowISO()).run();
    if (res.meta.changes === 0) return { ok: false, reason: 'already claimed' };
    return runPipeline(env, today, 1);
  }

  if (row.status === 'success' || row.status === 'failed') {
    return { ok: false, reason: `already ${row.status} today` };
  }

  // Retry path: claim only when the last attempt is older than the retry window
  if (row.status === 'retrying' || row.status === 'running') {
    const cutoff = new Date(Date.now() - (RETRY_INTERVAL_MINUTES - 5) * 60000).toISOString();
    const claim = await env.DB.prepare(
      `UPDATE automation_state
       SET status = 'running', last_attempt_at = ?, attempt_count = attempt_count + 1, updated_at = ?
       WHERE date = ? AND status = 'retrying' AND datetime(last_attempt_at) <= datetime(?)`
    ).bind(nowISO(), nowISO(), today, cutoff).run();
    if (claim.meta.changes === 0) return { ok: false, reason: 'not claimable yet' };
    return runPipeline(env, today, row.attempt_count + 1);
  }

  return { ok: false, reason: `unknown status: ${row.status}` };
}

/** Manual run from the admin settings page (still limited to 1/day) */
export async function runAutomationNow(env) {
  const today = jstDateStr();
  const row = await env.DB.prepare('SELECT * FROM automation_state WHERE date = ?').bind(today).first();

  if (row && row.status === 'success') {
    return { ok: false, code: 'already_done', error: null };
  }
  if (row && (row.status === 'running' || row.status === 'retrying')) {
    if (row.status === 'retrying') {
      const claim = await env.DB.prepare(
        `UPDATE automation_state
         SET status = 'running', last_attempt_at = ?, attempt_count = attempt_count + 1, updated_at = ?
         WHERE date = ? AND status = 'retrying'`
      ).bind(nowISO(), nowISO(), today).run();
      if (claim.meta.changes === 1) {
        return runPipeline(env, today, row.attempt_count + 1);
      }
    }
    return { ok: false, code: 'in_progress', error: null };
  }

  const res = await env.DB.prepare(
    `INSERT OR IGNORE INTO automation_state
       (date, status, last_attempt_at, next_retry_at, attempt_count, created_at, updated_at)
     VALUES (?, 'running', ?, ?, 1, ?, ?)`
  ).bind(today, nowISO(), new Date(Date.now() + RETRY_INTERVAL_MINUTES * 60000).toISOString(), nowISO(), nowISO()).run();
  if (res.meta.changes === 0) {
    return { ok: false, code: 'already_done', error: null };
  }
  return runPipeline(env, today, 1);
}

// ------------------------------------------------------------
// Pipeline
// ------------------------------------------------------------

async function runPipeline(env, today, attemptCount) {
  try {
    const articleId = await generateArticle(env);
    await env.DB.prepare(
      `UPDATE automation_state SET status = 'success', article_id = ?, error = NULL, updated_at = ? WHERE date = ?`
    ).bind(articleId, nowISO(), today).run();
    return { ok: true, articleId };
  } catch (err) {
    const message = String((err && err.message) || err).slice(0, 500);
    const nextRetry = new Date(Date.now() + RETRY_INTERVAL_MINUTES * 60000).toISOString();
    if (attemptCount >= MAX_ATTEMPTS) {
      await env.DB.prepare(
        `UPDATE automation_state SET status = 'failed', error = ?, next_retry_at = ?, updated_at = ? WHERE date = ?`
      ).bind(message, nextRetry, nowISO(), today).run();
    } else {
      await env.DB.prepare(
        `UPDATE automation_state SET status = 'retrying', error = ?, next_retry_at = ?, updated_at = ? WHERE date = ?`
      ).bind(message, nextRetry, nowISO(), today).run();
    }
    return { ok: false, code: 'failed', error: message };
  }
}

async function generateArticle(env) {
  // 1) fetch news RSS (with fallback sources)
  const newsItems = await fetchNews();
  if (!newsItems.length) throw new Error('All RSS sources returned no items');

  // 2) duplicate check against existing automated articles
  const existing = await env.DB.prepare(
    `SELECT source_url, title FROM articles
     WHERE is_automated = 1 AND source_url IS NOT NULL AND source_url != ''`
  ).all();
  const existingUrls = new Set((existing.results || []).map((r) => r.source_url));
  const existingTitles = (existing.results || []).map((r) => normalizeTitle(r.title));

  let candidate = null;
  for (const item of newsItems) {
    const nt = normalizeTitle(item.title);
    if (existingUrls.has(item.link)) continue;
    if (existingTitles.some((t) => t === nt || t.includes(nt) || nt.includes(t))) continue;
    // IT・ガジェット関連のネタだけを採用する（カテゴリ判定でIT系以外はスキップ）
    if (!detectCategory(`${item.title} ${item.description}`)) continue;
    candidate = item;
    break;
  }
  if (!candidate) throw new Error('No unique IT/gadget news candidate found (duplicate or topic filter)');

  // 3) Gemini generation
  const generated = await generateWithGemini(env, candidate);

  // 4) category normalization
  const category = CATEGORY_ALIASES[String(generated.category || '').trim()] || 'other';

  // 5) thumbnail -> R2
  const thumbnail = await createThumbnail(env, generated.title, category, candidate.source);

  // 6) publish to D1
  const publishedAt = nowISO();
  const res = await env.DB.prepare(
    `INSERT INTO articles
       (title, description, content, thumbnail, category, status, featured,
        source_url, source_name, published_at, is_automated, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, 'published', 1, ?, ?, ?, 1, ?, ?)` // AI自動投稿はおすすめ(★)にする
  ).bind(
    String(generated.title || '').slice(0, 200),
    String(generated.description || '').slice(0, 600),
    String(generated.content || ''),
    thumbnail,
    category,
    candidate.link,
    String(candidate.source || '').slice(0, 120),
    publishedAt,
    publishedAt,
    publishedAt
  ).run();

  return Number(res.meta.last_row_id);
}

// ------------------------------------------------------------
// News RSS (multi-source with fallback)
// ------------------------------------------------------------

async function fetchNews() {
  let lastErr = null;
  for (const src of RSS_SOURCES) {
    try {
      const resp = await fetch(src.url, {
        headers: {
          'User-Agent':
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
          Accept: 'application/rss+xml, application/xml, text/xml, */*',
        },
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const xml = await resp.text();
      if (!xml.includes('<item>')) throw new Error('no <item> entries');
      const items = parseRSS(xml);
      if (!items.length) throw new Error('0 items after parse');
      // Fill in the feed name for sources without an explicit source field
      for (const item of items) {
        if (!item.source) item.source = src.name;
      }
      return items.slice(0, 15);
    } catch (err) {
      lastErr = err;
      console.log(`[automation] RSS source ${src.name} failed: ${err.message}`);
    }
  }
  throw new Error(`All RSS sources failed: ${lastErr ? lastErr.message : 'unknown error'}`);
}

function parseRSS(xml) {
  const items = [];
  const itemRe = /<item>([\s\S]*?)<\/item>/g;
  let m;
  while ((m = itemRe.exec(xml)) !== null) {
    const body = m[1];
    const title = stripTags(extractTag(body, 'title'));
    const link = extractTag(body, 'link').trim();
    const pubDate = extractTag(body, 'pubDate').trim();
    const desc = extractTag(body, 'description');
    const sourceMatch = desc.match(/<font[^>]*color="#6f6f6f"[^>]*>([\s\S]*?)<\/font>/i);
    const source =
      stripTags(sourceMatch ? sourceMatch[1] : '') ||
      (String(title).includes(' - ') ? String(title).split(' - ').pop() : '') ||
      '';
    if (!title || !link || !/^https?:/.test(link)) continue;
    items.push({
      title,
      link,
      pubDate,
      description: stripTags(desc).slice(0, 500),
      source,
    });
  }
  return items;
}

function extractTag(body, tag) {
  const m = body.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return m ? m[1] : '';
}

function stripTags(s) {
  return String(s || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_m, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_m, h) => String.fromCharCode(parseInt(h, 16)))
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeTitle(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/[\s\u3000「」『』()（）[\].,:;!?、。・-]+/g, '');
}

// ------------------------------------------------------------
// ITFIS full-text import (RSS -> direct publish, no AI)
// ------------------------------------------------------------

const ITFIS_API = 'https://itfis.net/wp-json/wp/v2/posts';
const ITFIS_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

/** Pull ALL itfis.net posts via the WP REST API (paged), dedupe by URL/title. */
async function importItfis(env) {
  try {
    const existing = await env.DB.prepare(
      `SELECT source_url, title FROM articles
       WHERE is_automated = 1 AND source_url IS NOT NULL AND source_url != ''`
    ).all();
    const existingUrls = new Set((existing.results || []).map((r) => r.source_url));
    const existingTitles = (existing.results || []).map((r) => normalizeTitle(r.title));

    let imported = 0;
    let page = 1;
    let totalPages = 1;
    let seen = 0;
    // Guard: hard cap so one scheduled run stays within CPU limits.
    const MAX_IMPORT_PER_RUN = 25;

    do {
      const resp = await fetch(`${ITFIS_API}?per_page=100&page=${page}&_embed=1`, {
        headers: { 'User-Agent': ITFIS_UA, Accept: 'application/json' },
        signal: AbortSignal.timeout(20000),
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const totalHdr = resp.headers.get('x-wp-totalpages');
      if (totalHdr) totalPages = Math.max(1, Number(totalHdr) || 1);
      const posts = await resp.json();
      if (!Array.isArray(posts) || !posts.length) break;
      seen += posts.length;

      for (const post of posts) {
        if (imported >= MAX_IMPORT_PER_RUN) break;
        const link = String((post && post.link) || '').trim();
        const rawTitle = stripTags(((post && post.title && post.title.rendered) || ''));
        const title = decodeEntities(rawTitle).trim();
        if (!link || !title) continue;
        const nt = normalizeTitle(title);
        if (existingUrls.has(link)) continue;
        if (existingTitles.some((t) => t === nt || t.includes(nt) || nt.includes(t))) continue;

        const content = htmlToContent((post && post.content && post.content.rendered) || '');
        if (!content) continue;
        const description = String(content.split('\n\n')[0] || content)
          .replace(/^#{1,6}\s+|^-\s+/gm, '')
          .trim()
          .slice(0, 200);
        const category = detectCategory(title) || detectCategory(title + ' ' + content) || 'it';
        // 画像は必ず取得する: _embed のフィーチャー画像 → JetpackメディアURL →
        // 本文の最初の画像 → 生成SVG、の順でフォールバック
        let thumb = '';
        const featuredMedia = post && post._embedded && post._embedded['wp:featuredmedia'];
        const featuredUrl = featuredMedia && featuredMedia[0] && featuredMedia[0].source_url;
        if (featuredUrl && /^https?:\/\//i.test(featuredUrl)) {
          thumb = featuredUrl;
        } else if (post.jetpack_featured_media_url && /^https?:\/\//i.test(post.jetpack_featured_media_url)) {
          thumb = post.jetpack_featured_media_url;
        } else {
          const img = extractFirstImage((post && post.content && post.content.rendered) || '');
          thumb = img && /^https?:\/\//i.test(img) ? img : await createThumbnail(env, title, category, 'ITFIS');
        }
        const publishedAt = (post.date && new Date(post.date).toISOString()) || nowISO();

        await env.DB.prepare(
          `INSERT INTO articles
             (title, description, content, thumbnail, category, status, featured,
              source_url, source_name, published_at, is_automated, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, 'published', 0, ?, ?, ?, 1, ?, ?)`
        ).bind(
          title.slice(0, 200),
          String(description || ''),
          content,
          thumb,
          category,
          link,
          'ITFIS',
          publishedAt,
          publishedAt,
          publishedAt
        ).run();

        existingUrls.add(link);
        existingTitles.push(nt);
        imported += 1;
      }
      page += 1;
      // Stop paging once the run cap is reached (remaining posts import on later crons).
      if (imported >= MAX_IMPORT_PER_RUN) break;
    } while (page <= totalPages);

    return { ok: true, imported, seen };
  } catch (err) {
    console.log(`[automation] ITFIS import failed: ${String((err && err.message) || err).slice(0, 300)}`);
    return { ok: false, error: String((err && err.message) || err).slice(0, 500) };
  }
}

function decodeEntities(s) {
  return String(s || '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_m, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_m, h) => String.fromCharCode(parseInt(h, 16)));
}

/** First https image URL inside RSS HTML content, if any. */
function extractFirstImage(html) {
  const m = String(html || '').match(/<img[^>]+src=["']([^"']+)["']/i);
  if (!m) return '';
  const u = m[1];
  return /^https?:\/\//i.test(u) ? u : '';
}

/** Convert RSS HTML into the site's markdown-ish article format (## / - / paragraphs). */
function htmlToContent(html) {
  let s = String(html || '');
  s = s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1') // strip CDATA first, or tag-removal eats the body
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, ' ')
    .replace(/<(figure|figcaption|aside|nav|form|button|video|audio)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<img[^>]*>/gi, ' ');

  s = s.replace(/<h([1-3])[^>]*>([\s\S]*?)<\/h\1>/gi, (_m, _lv, inner) => '## ' + stripTags(inner) + '\n\n');
  s = s.replace(/<h([4-6])[^>]*>([\s\S]*?)<\/h\1>/gi, (_m, _lv, inner) => '### ' + stripTags(inner) + '\n\n');
  s = s.replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, (_m, inner) => '- ' + stripTags(inner) + '\n');
  s = s.replace(/<\/(p|div|section|article|blockquote|ul|ol|table|tr)>/gi, '\n\n');
  s = s.replace(/<(p|div|section|article|blockquote|ul|ol|tr)[^>]*>/gi, '\n\n');
  s = s.replace(/<br\s*\/?>/gi, '\n');

  s = s
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_m, n) => String.fromCharCode(Number(n)))
    .replace(/[ \t]+/g, ' ')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .split('\n')
    .filter((l) => !/^image:/i.test(l.trim())) // drop 'Image:xxx' image-caption lines
    .join('\n')
    .trim();
  return s;
}

/** Keyword-based category detection (used instead of Gemini for imports).
 *  Returns a slug, or '' when nothing matched (caller decides the fallback). */
function detectCategory(text) {
  const s = String(text || '').toLowerCase();
  const has = (...words) => words.some((w) => s.includes(w));
  // \bai\b: word-boundary so plain English words like 'domain'/'chain' don't match.
  if (has('windows', 'マイクロソフト', 'microsoft', 'copilot')) return 'windows';
  if (has('iphone', 'ios', 'apple', 'アップル', 'mac', 'macbook', 'ipad', 'vision pro', 'watchos', 'airpods')) return 'apple';
  if (has('android', 'サムスン', 'samsung', 'galaxy', 'スマホ', 'pixel', 'huawei', 'oppo', 'xiaomi', '折りたたみ', 'タブレット')) return 'android';
  if (/\bai\b/.test(s) || has('人工知能', 'chatgpt', 'gpt', 'gemini', 'openai', 'claude', 'anthropic', 'grok', 'fable', 'sakana', '機械学習', '生成ai', 'llm', 'deepseek', 'llama', 'mistral', 'midjourney', 'stable diffusion')) return 'ai';
  if (has('プログラミング', 'programming', 'コード', 'developer', '開発者', 'github', 'python', 'javascript', 'typescript', 'rust', 'java', 'docker', 'kubernetes')) return 'programming';
  if (has('web', 'ウェブ', 'ブラウザ', 'chrome', 'firefox', 'edge', 'safari', 'サイト', 'ドメイン')) return 'web';
  if (has('テクノロジー', '半導体', 'チップ', 'nvidia', 'intel', 'amd', 'gpu', 'クラウド', 'cloud', 'データセンター', 'サーバー', '5g', '6g', 'sns', 'twitter', 'meta', 'facebook', 'instagram', 'youtube', 'google', 'amazon', 'nasa', 'ロケット', '衛星', 'kioxia', 'micron', 'tsmc')) return 'it';
  return '';
}

// ------------------------------------------------------------
// Gemini API
// ------------------------------------------------------------

async function generateWithGemini(env, item) {
  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) throw new Error('GEMINI_API_KEY is not configured on this Worker');
  const model = getModel(env);

  const resp = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
      body: JSON.stringify({
        contents: [{ role: 'user', parts: [{ text: buildPrompt(item) }] }],
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 8192,
          responseMimeType: 'application/json',
        },
      }),
    }
  );
  if (!resp.ok) {
    const text = await resp.text().catch(() => '');
    throw new Error(`Gemini API failed: HTTP ${resp.status} ${text.slice(0, 300)}`);
  }
  const data = await resp.json();
  const text = (data?.candidates?.[0]?.content?.parts || []).map((p) => p.text || '').join('').trim();
  if (!text) throw new Error('Gemini API returned an empty response');

  const cleaned = text.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '');
  let parsed;
  try {
    parsed = JSON.parse(cleaned);
  } catch {
    const first = cleaned.indexOf('{');
    const last = cleaned.lastIndexOf('}');
    if (first === -1 || last === -1 || last <= first) throw new Error('Gemini returned invalid JSON');
    parsed = JSON.parse(cleaned.slice(first, last + 1));
  }

  const title = String(parsed.title || item.title).slice(0, 200);
  const description = String(parsed.description || item.description || '').slice(0, 600);
  const content = String(parsed.content || '').trim();
  if (!content) throw new Error('Gemini returned empty article content');

  return { title, description, content, category: String(parsed.category || 'other') };
}

function buildPrompt(item) {
  return `あなたはニュース記事ライターです。以下に示すGoogle Newsのニュース情報のみを事実の出典として、日本語のニュース記事を1本作成してください。

【厳守ルール】
- テーマはIT・テクノロジー・ガジェット関連のニュースのみを扱う
- ニュース情報に含まれる事実だけを使う。新しい情報・数字・固有名詞を勝手に追加・推測しない
- 元ニュースの文章を大量にコピーしない。情報を整理・要約し、独自の文章として書き直す
- タイトルは30字以内の簡潔な見出し
- 概要（description）は80〜150字の要約
- 本文（content）は400〜700字程度、3〜5段落。段落は空行で区切り、必要なら段落冒頭に「## 」を付けて小見出しにする
- カテゴリは次のいずれかから1つだけ選ぶ: IT / AI / Windows / Android / Apple / Web / Programming / その他
- 出力はJSONオブジェクトのみ（マークダウンのコードブロックや注釈を付けない）

【ニュース情報】
タイトル: ${item.title}
ソース: ${item.source}
公開日時: ${item.pubDate}
概要: ${item.description}
URL: ${item.link}

【出力JSON形式】
{"title":"...","description":"...","content":"...","category":"..."}`;
}

// ------------------------------------------------------------
// Thumbnail (generated SVG -> R2)
// ------------------------------------------------------------

async function createThumbnail(env, title, category, sourceName) {
  const svg = buildThumbSvg(title, category, sourceName);
  if (env.IMAGES) {
    const key = `thumbs/${jstDateStr()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}.svg`;
    await env.IMAGES.put(key, svg, {
      httpMetadata: { contentType: 'image/svg+xml', cacheControl: 'public, max-age=31536000, immutable' },
    });
    return `/api/images/${key}`;
  }
  // Fallback when R2 is not bound: embed the SVG as a data URI in D1.
  // Note: Buffer is not available in the Workers runtime, so encode manually
  // with the standard TextEncoder + btoa globals.
  const bytes = new TextEncoder().encode(svg);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  const b64 = btoa(binary);
  return `data:image/svg+xml;base64,${b64}`;
}

function buildThumbSvg(title, category, sourceName) {
  const color = CATEGORY_COLORS[category] || '#475569';

  return `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${color}"/>
      <stop offset="1" stop-color="#0f1420"/>
    </linearGradient>
  </defs>
  <rect width="1200" height="630" fill="url(#bg)"/>
  <circle cx="1050" cy="80" r="220" fill="#ffffff" opacity="0.06"/>
  <circle cx="140" cy="560" r="170" fill="#ffffff" opacity="0.05"/>
  <text x="600" y="345" text-anchor="middle" font-family="'Segoe UI',sans-serif" font-size="130" font-weight="800" letter-spacing="26" fill="#ffffff" fill-opacity="0.92">INFOLD</text>
</svg>`;
}

// ------------------------------------------------------------
// Automation status for the admin UI
// ------------------------------------------------------------

export async function getAutomationStatus(env) {
  const today = jstDateStr();
  const state = await env.DB.prepare('SELECT * FROM automation_state WHERE date = ?').bind(today).first();
  const enabled = (await getSetting(env, 'automation_enabled')) !== '0';
  return {
    enabled,
    settings: {
      time: '08:00',
      timezone: 'Asia/Tokyo',
      dailyCount: 1,
      cron: '0 * * * *',
      model: getModel(env),
    },
    today: state || null,
    todayDate: today,
  };
}

export async function setAutomationEnabled(env, enabled) {
  await setSetting(env, 'automation_enabled', enabled ? '1' : '0');
}

// ------------------------------------------------------------
// settings table helpers
// ------------------------------------------------------------

async function getSetting(env, key, fallback = '') {
  const row = await env.DB.prepare('SELECT value FROM settings WHERE key = ?').bind(key).first();
  return row ? row.value : fallback;
}

async function setSetting(env, key, value) {
  await env.DB.prepare(
    `INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`
  ).bind(key, value, nowISO()).run();
}
