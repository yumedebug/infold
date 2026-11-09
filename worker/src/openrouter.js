// ============================================================
// INFOLD News - OpenRouter AI article generation
//
// Flow: RSS / ITFIS -> ai_news_items -> OpenRouter (free model only)
//       -> strict JSON validation -> ai_generations history
//       -> D1 draft (manual) or published (scheduled)
//
// IMPORTANT:
//   - The API key is set from the admin UI and stored in the D1 settings
//     table. The OPENROUTER_API_KEY Workers Secret is the fallback when no
//     key is saved. The value is never returned by any API response or
//     written to logs.
//   - Only free models are accepted: ids ending in ":free", or the
//     "openrouter/free" router, and their metadata must report 0 price.
//     There is no automatic fallback to a paid model.
// ============================================================

import { clampInt, jstDateStr, nowISO } from './util.js';
import { notifyArticlePublished } from './push.js';

const OPENROUTER_CHAT_URL = 'https://openrouter.ai/api/v1/chat/completions';
const OPENROUTER_MODELS_URL = 'https://openrouter.ai/api/v1/models';
const DEFAULT_MODEL = 'openrouter/free';
const API_KEY_SETTING = 'openrouter_api_key';
const MIN_API_KEY_LENGTH = 20;
const DEFAULT_DAILY_LIMIT = 10;
const MAX_SOURCE_TEXT = 14000;

const RSS_SOURCES = [
  { name: 'Google News', url: 'https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja&topic=tc' },
  { name: 'GIZMODO', url: 'https://www.gizmodo.jp/index.xml' },
  { name: 'GIGAZINE', url: 'https://gigazine.net/news/rss_2.0/' },
];

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

export class OpenRouterError extends Error {
  constructor(code, message, status = 502, extra = {}) {
    super(message);
    this.name = 'OpenRouterError';
    this.code = code;
    this.status = status;
    Object.assign(this, extra);
  }
}

/**
 * The key saved from the admin UI (D1) wins, so the key can be changed
 * without redeploying. The OPENROUTER_API_KEY Workers Secret is the
 * fallback for deployments that only use secrets.
 */
async function resolveApiKey(env) {
  let stored = '';
  try { stored = String((await getSetting(env, API_KEY_SETTING, '')) || '').trim(); } catch (_) { stored = ''; }
  if (stored) return { key: stored, source: 'setting' };
  const fromEnv = String(env.OPENROUTER_API_KEY || '').trim();
  if (fromEnv) return { key: fromEnv, source: 'env' };
  return { key: '', source: 'none' };
}

export async function isOpenRouterConfigured(env) {
  return !!(await resolveApiKey(env)).key;
}

export function getOpenRouterModel(env, settingModel = '') {
  return String(env.OPENROUTER_MODEL || settingModel || DEFAULT_MODEL).trim();
}

export function isFreeModelId(model) {
  const id = String(model || '').trim();
  return /:free$/i.test(id) || id === 'openrouter/free';
}

function makeError(code, message, status = 502, extra = {}) {
  return new OpenRouterError(code, message, status, extra);
}

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

function parseBool(value, fallback = true) {
  if (value === undefined || value === null || value === '') return fallback;
  return value === '1' || value === 1 || value === true || value === 'true';
}

async function getConfig(env) {
  const [enabledRaw, autoGenerateRaw, autoPublishRaw, limitRaw, settingModel, apiKey] = await Promise.all([
    getSetting(env, 'openrouter_enabled', '1'),
    getSetting(env, 'openrouter_auto_generate', '1'),
    getSetting(env, 'openrouter_auto_publish', '1'),
    getSetting(env, 'openrouter_daily_limit', String(DEFAULT_DAILY_LIMIT)),
    getSetting(env, 'openrouter_model', ''),
    resolveApiKey(env),
  ]);
  const model = getOpenRouterModel(env, settingModel);
  return {
    enabled: parseBool(enabledRaw, true),
    autoGenerate: parseBool(autoGenerateRaw, true),
    autoPublish: parseBool(autoPublishRaw, true),
    dailyLimit: clampInt(limitRaw, 1, 100, DEFAULT_DAILY_LIMIT),
    model,
    modelSource: env.OPENROUTER_MODEL ? 'env' : (settingModel ? 'setting' : 'default'),
    freeModel: isFreeModelId(model),
    apiKey: apiKey.key,
    apiKeySource: apiKey.source,
    configured: !!apiKey.key,
  };
}

// ------------------------------------------------------------
// OpenRouter model verification (free-only guard)
// ------------------------------------------------------------

export async function checkOpenRouterModel(env, model, apiKey = '') {
  const key = String(apiKey || '') || (await resolveApiKey(env)).key;
  if (!key) {
    return { ok: false, error: 'not_configured', message: 'OpenRouter API key is not configured' };
  }
  const id = String(model || '').trim();
  if (!isFreeModelId(id)) {
    return { ok: false, error: 'free_model_required', message: `Model must be a free model: ${id}` };
  }

  let resp;
  try {
    resp = await fetch(OPENROUTER_MODELS_URL, {
      headers: {
        Authorization: `Bearer ${key}`,
        'HTTP-Referer': 'https://infold.f5.si',
        'X-Title': 'INFOLD',
      },
    });
  } catch (err) {
    return { ok: false, error: 'connection_failed', message: String((err && err.message) || err).slice(0, 300) };
  }
  if (!resp.ok) {
    const text = String(await resp.text().catch(() => '')).replace(/\s+/g, ' ').trim();
    return {
      ok: false,
      error: 'models_request_failed',
      message: `HTTP ${resp.status}${text ? ' ' + text.slice(0, 200) : ''}`,
    };
  }
  let data;
  try { data = await resp.json(); } catch (_) { data = null; }
  if (!data || !Array.isArray(data.data)) {
    return { ok: false, error: 'models_request_failed', message: 'Unexpected response from the models endpoint' };
  }
  const found = data.data.find((m) => m && m.id === id);
  if (!found) {
    return { ok: false, error: 'model_unavailable', message: `Free model is not currently available: ${id}` };
  }
  const pricing = found.pricing || {};
  const promptPrice = Number(pricing.prompt || 0);
  const completionPrice = Number(pricing.completion || 0);
  if (promptPrice !== 0 || completionPrice !== 0) {
    return { ok: false, error: 'not_free_model', message: `Model is not free: ${id}` };
  }
  return {
    ok: true,
    model: id,
    name: found.name || id,
    contextLength: found.context_length || 0,
    pricing: { prompt: pricing.prompt || '0', completion: pricing.completion || '0' },
  };
}

// ------------------------------------------------------------
// Settings / status for the admin workspace
// ------------------------------------------------------------

export async function getOpenRouterStatus(env) {
  const config = await getConfig(env);
  const today = jstDateStr();

  let todayCounts = { today: 0, success: 0, failed: 0, review: 0 };
  let articleCounts = { drafts: 0, published: 0 };
  try {
    const r = await env.DB.prepare(
      `SELECT COUNT(*) AS today,
         COALESCE(SUM(CASE WHEN status IN ('draft','published') THEN 1 ELSE 0 END), 0) AS success,
         COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0) AS failed,
         COALESCE(SUM(CASE WHEN status = 'needs_review' THEN 1 ELSE 0 END), 0) AS review
       FROM ai_generations WHERE created_date = ?`
    ).bind(today).first();
    if (r) todayCounts = {
      today: Number(r.today) || 0,
      success: Number(r.success) || 0,
      failed: Number(r.failed) || 0,
      review: Number(r.review) || 0,
    };
    const a = await env.DB.prepare(
      `SELECT
         COALESCE(SUM(CASE WHEN status = 'draft' THEN 1 ELSE 0 END), 0) AS drafts,
         COALESCE(SUM(CASE WHEN status = 'published' THEN 1 ELSE 0 END), 0) AS published
       FROM articles`
    ).first();
    if (a) articleCounts = { drafts: Number(a.drafts) || 0, published: Number(a.published) || 0 };
  } catch (err) {
    // Migration 0004 has not been applied yet.
    todayCounts = { today: 0, success: 0, failed: 0, review: 0, migrationRequired: true };
    articleCounts = { drafts: 0, published: 0 };
  }

  return {
    provider: 'OpenRouter',
    configured: config.configured,
    enabled: config.enabled,
    autoGenerate: config.autoGenerate,
    autoPublish: config.autoPublish,
    dailyLimit: config.dailyLimit,
    model: config.model,
    modelSource: config.modelSource,
    freeModel: isFreeModelId(config.model),
    apiKeySet: config.configured,
    apiKeySource: config.apiKeySource,
    apiKeyLocation: config.apiKeySource === 'setting'
      ? 'Saved from the admin screen (D1)'
      : config.apiKeySource === 'env'
        ? 'Cloudflare Workers Secrets (OPENROUTER_API_KEY)'
        : 'Not configured',
    today: todayCounts,
    articles: articleCounts,
  };
}

export async function updateOpenRouterSettings(env, body) {
  const model = String(body?.model || '').trim();
  if (model && !isFreeModelId(model)) {
    throw makeError('free_model_required', 'Only free OpenRouter models can be used', 400);
  }
  const apiKey = String(body?.apiKey || '').trim();
  if (apiKey) {
    if (apiKey.length < MIN_API_KEY_LENGTH) {
      throw makeError('invalid_api_key', 'That API key looks too short', 400);
    }
    await setSetting(env, API_KEY_SETTING, apiKey);
  } else if (body?.clearApiKey === true) {
    await env.DB.prepare('DELETE FROM settings WHERE key = ?').bind(API_KEY_SETTING).run();
  }
  if (body?.enabled !== undefined) {
    await setSetting(env, 'openrouter_enabled', body.enabled ? '1' : '0');
  }
  if (body?.autoGenerate !== undefined) {
    await setSetting(env, 'openrouter_auto_generate', body.autoGenerate ? '1' : '0');
  }
  if (body?.autoPublish !== undefined) {
    await setSetting(env, 'openrouter_auto_publish', body.autoPublish ? '1' : '0');
  }
  if (body?.dailyLimit !== undefined) {
    await setSetting(env, 'openrouter_daily_limit', String(clampInt(body.dailyLimit, 1, 100, DEFAULT_DAILY_LIMIT)));
  }
  if (model) {
    await setSetting(env, 'openrouter_model', model);
  }
  return getOpenRouterStatus(env);
}

// ------------------------------------------------------------
// News collection
// ------------------------------------------------------------

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

function extractTag(body, tag) {
  const m = String(body || '').match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return m ? m[1] : '';
}

function extractFirstImage(html) {
  const m = String(html || '').match(/<img[^>]+src=["']([^"']+)["']/i);
  return m && /^https?:\/\//i.test(m[1]) ? m[1] : '';
}

function parseRss(xml, fallbackSource) {
  const items = [];
  const itemRe = /<item>([\s\S]*?)<\/item>/g;
  let m;
  while ((m = itemRe.exec(String(xml || '')))) {
    const body = m[1];
    const title = stripTags(extractTag(body, 'title'));
    const link = stripTags(extractTag(body, 'link'));
    const pubDate = stripTags(extractTag(body, 'pubDate'));
    const descRaw = extractTag(body, 'description');
    const desc = stripTags(descRaw);
    const sourceMatch = descRaw.match(/<font[^>]*color=["']?#6f6f6f["']?[^>]*>([\s\S]*?)<\/font>/i);
    const source = stripTags(sourceMatch ? sourceMatch[1] : '') ||
      (String(title).includes(' - ') ? String(title).split(' - ').pop() : '') ||
      fallbackSource;
    const enclosure = body.match(/<enclosure[^>]+url=["']([^"']+)["']/i);
    const media = body.match(/<media:content[^>]+url=["']([^"']+)["']/i);
    const image = (enclosure && /^https?:\/\//i.test(enclosure[1]) ? enclosure[1] : '') ||
      (media && /^https?:\/\//i.test(media[1]) ? media[1] : '') ||
      extractFirstImage(descRaw);
    if (!title || !link || !/^https?:/i.test(link)) continue;
    items.push({
      title: title.slice(0, 300),
      link,
      pubDate,
      description: desc.slice(0, 1200),
      source: String(source || fallbackSource).slice(0, 120),
      image,
    });
  }
  return items;
}

async function fetchRssSources() {
  const jobs = RSS_SOURCES.map(async (src) => {
    try {
      const resp = await fetch(src.url, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
          Accept: 'application/rss+xml, application/xml, text/xml, */*',
        },
        signal: AbortSignal.timeout(15000),
      });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const xml = await resp.text();
      const items = parseRss(xml, src.name);
      return items.slice(0, 12);
    } catch (err) {
      console.log(`[openrouter] RSS source ${src.name} failed: ${String((err && err.message) || err).slice(0, 200)}`);
      return [];
    }
  });
  const results = await Promise.allSettled(jobs);
  return results.flatMap((r) => (r.status === 'fulfilled' ? r.value : []));
}

async function upsertNewsItem(env, item) {
  await env.DB.prepare(
    `INSERT INTO ai_news_items
       (source_url, title, source_name, published_at, description, raw_content, image_url, status, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, 'new', ?, ?)
     ON CONFLICT(source_url) DO UPDATE SET
       title = excluded.title,
       source_name = excluded.source_name,
       published_at = COALESCE(excluded.published_at, ai_news_items.published_at),
       description = excluded.description,
       raw_content = CASE WHEN length(excluded.raw_content) > length(ai_news_items.raw_content)
                          THEN excluded.raw_content ELSE ai_news_items.raw_content END,
       image_url = COALESCE(NULLIF(excluded.image_url, ''), ai_news_items.image_url),
       updated_at = excluded.updated_at`
  ).bind(
    item.link,
    String(item.title || '').slice(0, 300),
    String(item.source || '').slice(0, 120),
    item.pubDate ? new Date(item.pubDate).toISOString() : null,
    String(item.description || '').slice(0, 1500),
    String(item.description || '').slice(0, MAX_SOURCE_TEXT),
    String(item.image || '').slice(0, 1000),
    nowISO(),
    nowISO()
  ).run();
}

/** Pull RSS + recent ITFIS rows into ai_news_items (deduped by URL). */
export async function syncNewsItems(env) {
  let count = 0;

  const rss = await fetchRssSources();
  for (const item of rss.slice(0, 30)) {
    try { await upsertNewsItem(env, item); count += 1; } catch (err) {
      console.log(`[openrouter] news upsert failed: ${String((err && err.message) || err).slice(0, 200)}`);
    }
  }

  // Recent ITFIS articles already imported into D1 are also valid sources.
  try {
    const rows = await env.DB.prepare(
      `SELECT title, source_url, source_name, published_at, description, content, thumbnail
       FROM articles WHERE source_name = 'ITFIS' AND source_url IS NOT NULL AND source_url != ''
       ORDER BY published_at DESC, id DESC LIMIT 12`
    ).all();
    for (const row of rows.results || []) {
      try {
        await upsertNewsItem(env, {
          title: row.title,
          link: row.source_url,
          source: row.source_name || 'ITFIS',
          pubDate: row.published_at,
          description: row.description || '',
          raw_content: row.content || '',
          image: row.thumbnail || '',
        });
        count += 1;
      } catch (_) { /* one bad row must not stop the sync */ }
    }
  } catch (err) {
    console.log(`[openrouter] ITFIS source sync failed: ${String((err && err.message) || err).slice(0, 200)}`);
  }

  return count;
}

export async function listNewsItems(env, limit = 50) {
  const rows = await env.DB.prepare(
    `SELECT n.*,
       (SELECT g.id FROM ai_generations g WHERE g.news_item_id = n.id ORDER BY g.id DESC LIMIT 1) AS generation_id,
       (SELECT g.status FROM ai_generations g WHERE g.news_item_id = n.id ORDER BY g.id DESC LIMIT 1) AS generation_status,
       (SELECT g.article_id FROM ai_generations g WHERE g.news_item_id = n.id ORDER BY g.id DESC LIMIT 1) AS article_id
     FROM ai_news_items n
     ORDER BY COALESCE(n.published_at, n.created_at) DESC, n.id DESC
     LIMIT ?`
  ).bind(clampInt(limit, 1, 100, 50)).all();
  return rows.results || [];
}

/** Unused Google News items, newest first (scheduled auto-publish only). */
async function listGoogleNewsCandidates(env, limit = 6) {
  const rows = await env.DB.prepare(
    `SELECT n.* FROM ai_news_items n
     WHERE n.source_url LIKE 'https://news.google.com/%'
       AND NOT EXISTS (
         SELECT 1 FROM ai_generations g
         WHERE g.news_item_id = n.id AND g.status IN ('draft', 'published')
       )
     ORDER BY COALESCE(n.published_at, n.created_at) DESC, n.id DESC
     LIMIT ?`
  ).bind(clampInt(limit, 1, 100, 6)).all();
  return rows.results || [];
}

async function getNewsItem(env, id) {
  return env.DB.prepare('SELECT * FROM ai_news_items WHERE id = ?').bind(Number(id)).first();
}

// ------------------------------------------------------------
// Source text extraction (public pages only, no paywall bypass)
// ------------------------------------------------------------

function isPublicHttpUrl(raw) {
  let u;
  try { u = new URL(raw); } catch (_) { return false; }
  if (!/^https?:$/i.test(u.protocol)) return false;
  const host = u.hostname.toLowerCase();
  if (
    host === 'localhost' || host.endsWith('.local') ||
    /^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.|0\.)/.test(host) ||
    host === '[::1]' || host.startsWith('fe80:')
  ) return false;
  return true;
}

function htmlToReadableText(html) {
  let s = String(html || '');
  s = s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<noscript[\s\S]*?<\/noscript>/gi, ' ')
    .replace(/<(nav|header|footer|aside|form|iframe|svg|video|audio)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|section|article|h[1-6]|li|blockquote|tr)>/gi, '\n\n')
    .replace(/<[^>]*>/g, ' ');
  s = stripTags(s)
    .replace(/[ \t]+/g, ' ')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  return s.slice(0, MAX_SOURCE_TEXT);
}

async function fetchSourceText(url) {
  if (!isPublicHttpUrl(url)) return '';
  try {
    const resp = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
        Accept: 'text/html,application/xhtml+xml',
      },
      redirect: 'follow',
      signal: AbortSignal.timeout(12000),
    });
    if (!resp.ok) return '';
    const ct = resp.headers.get('content-type') || '';
    if (!/text\/html|application\/xhtml\+xml/i.test(ct)) return '';
    const html = (await resp.text()).slice(0, 400000);
    const text = htmlToReadableText(html);
    return text.length >= 180 ? text : '';
  } catch (_) {
    return '';
  }
}

// ------------------------------------------------------------
// Prompt + validation
// ------------------------------------------------------------

const SYSTEM_PROMPT = `あなたはテクノロジーニュースメディア「INFOLD」の日本語ニュース記者です。
読者が単なる要約ではなく、ニュースの意味と背景を理解できる独立した記事を書きます。
事実と推測を明確に区別し、元情報にない事実・数字・発言・製品仕様を絶対に創作しません。
元記事の文章を大量にコピーせず、独自の文章として再構成します。`;

function buildPrompt(item, sourceText) {
  return `以下のニュース情報だけを事実の出典として、INFOLD用の日本語ニュース記事を1本執筆してください。

【厳守ルール】
- 提供された情報に含まれる事実だけを使う。存在しない発表、数字、日付、企業発言、製品仕様を追加しない。
- 情報が不足している場合は無理に記事を作らず、{"status":"needs_review","reason":"..."} だけを返す。
- 元記事の文章を大量にコピーしない。独立した文章として再構成する。
- 自然な日本語で、直訳調・誇張・水増しを避ける。専門用語には必要に応じて短い説明を付ける。
- タイトルは32〜70字程度。リード文は120〜220字程度。
- 本文は「見出し」と「段落」の配列で構成する。見出しは2〜4個、本文全体は800〜1800字程度を目安にする。
- 記事の構成は次の流れにする: ニュースの詳細 → 技術的・社会的背景 → 今後の影響や注目点 → まとめ。
- category は次のいずれか1つ: IT / AI / Windows / Android / Apple / Web / Programming / Other
- sources には元記事の情報源だけを入れる。
- editorial_notes には、確認が必要な情報や不確かな点を書く。無ければ空文字。
- 出力はJSONオブジェクトのみ。Markdownのコードブロックや説明文を付けない。

【ニュース情報】
タイトル: ${item.title}
情報源: ${item.source_name || item.source_name || '不明'}
公開日時: ${item.published_at || '不明'}
RSS概要: ${item.description || 'なし'}
元記事URL: ${item.source_url}

【取得できた元記事本文（参考。無い場合はRSS概要のみを事実源にする）】
${sourceText || '（本文は取得できませんでした。RSS概要とタイトルだけを事実源にしてください）'}

【出力JSON形式】
{
  "title": "記事タイトル",
  "lead": "リード文",
  "content": [
    {"type": "heading", "level": 2, "text": "見出し"},
    {"type": "paragraph", "text": "本文"}
  ],
  "category": "AI",
  "sources": [{"title": "情報源名", "url": "元記事URL"}],
  "editorial_notes": "確認が必要な情報"
}`;
}

function normalizeCategory(value) {
  const raw = String(value || '').trim();
  return CATEGORY_ALIASES[raw] || CATEGORY_ALIASES[raw.toLowerCase()] || 'other';
}

function cleanText(value, max) {
  return String(value == null ? '' : value).replace(/\s+/g, ' ').trim().slice(0, max);
}

function validateGeneratedArticle(parsed, item) {
  if (!parsed || typeof parsed !== 'object') {
    throw makeError('invalid_json', 'OpenRouter returned invalid JSON', 502);
  }
  if (parsed.status === 'needs_review') {
    throw makeError('needs_review', cleanText(parsed.reason, 1000) || 'Source information is insufficient', 422, {
      editorialNotes: cleanText(parsed.reason, 1000),
    });
  }

  const title = cleanText(parsed.title, 200);
  const lead = cleanText(parsed.lead, 600);
  if (title.length < 5) throw makeError('invalid_output', 'Generated title is too short', 502);
  if (lead.length < 10) throw makeError('invalid_output', 'Generated lead is too short', 502);

  const rawBlocks = Array.isArray(parsed.content) ? parsed.content : [];
  const blocks = [];
  for (const block of rawBlocks.slice(0, 60)) {
    if (!block || typeof block !== 'object') continue;
    if (block.type === 'heading') {
      const text = cleanText(block.text, 200);
      if (text) blocks.push({ type: 'heading', level: 2, text });
    } else if (block.type === 'paragraph') {
      const text = String(block.text == null ? '' : block.text).trim().slice(0, 6000);
      if (text) blocks.push({ type: 'paragraph', text });
    }
  }
  if (blocks.length < 3) throw makeError('invalid_output', 'Generated content has too few blocks', 502);
  if (!blocks.some((b) => b.type === 'paragraph')) {
    throw makeError('invalid_output', 'Generated content has no paragraphs', 502);
  }

  const category = normalizeCategory(parsed.category);
  const editorialNotes = cleanText(parsed.editorial_notes, 1000);

  // The original article is the only guaranteed source. Do not trust
  // additional URLs produced by the model (they may be hallucinated).
  const sources = [{
    title: cleanText(item.source_name || item.source || 'Original article', 120),
    url: item.source_url,
  }];

  return {
    title,
    lead,
    content: blocks,
    category,
    sources,
    editorial_notes: editorialNotes,
  };
}

function blocksToMarkdown(blocks) {
  return blocks.map((b) => (b.type === 'heading' ? `## ${b.text}` : b.text)).join('\n\n').trim();
}

function makeThumbnailSvg(title, category, sourceName) {
  const palettes = {
    ai: ['#83C441', '#101114'], it: ['#6EAA35', '#17191D'],
    windows: ['#83C441', '#101114'], android: ['#A5D86A', '#17191D'],
    apple: ['#B8C4AE', '#101114'], web: ['#7FB03D', '#17191D'],
    programming: ['#83C441', '#202329'], other: ['#6EAA35', '#17191D'],
  };
  const [accent, bg] = palettes[category] || palettes.other;
  const safeTitle = String(title || '').replace(/[<>&"]/g, '').slice(0, 48);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
  <rect width="1200" height="630" fill="${bg}"/>
  <rect x="0" y="0" width="12" height="630" fill="${accent}"/>
  <rect x="74" y="74" width="86" height="6" fill="${accent}"/>
  <text x="74" y="150" font-family="Segoe UI, Arial, sans-serif" font-size="34" font-weight="700" letter-spacing="8" fill="${accent}">INFOLD</text>
  <text x="74" y="300" font-family="Segoe UI, Arial, sans-serif" font-size="64" font-weight="700" fill="#F5F5F5">${safeTitle}</text>
  <text x="74" y="540" font-family="Segoe UI, Arial, sans-serif" font-size="26" fill="#A5A8AE">${String(sourceName || '').replace(/[<>&"]/g, '').slice(0, 40)}</text>
</svg>`;
  const bytes = new TextEncoder().encode(svg);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return `data:image/svg+xml;base64,${btoa(binary)}`;
}

// ------------------------------------------------------------
// OpenRouter request
// ------------------------------------------------------------

function parseJsonResponse(text) {
  const cleaned = String(text || '').trim().replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '');
  try { return JSON.parse(cleaned); } catch (_) { /* fall through */ }
  const first = cleaned.indexOf('{');
  const last = cleaned.lastIndexOf('}');
  if (first === -1 || last === -1 || last <= first) {
    throw makeError('invalid_json', 'OpenRouter returned invalid JSON', 502);
  }
  try { return JSON.parse(cleaned.slice(first, last + 1)); } catch (_) {
    throw makeError('invalid_json', 'OpenRouter returned invalid JSON', 502);
  }
}

async function callOpenRouter(env, model, item, sourceText, apiKey = '') {
  const body = {
    model,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: buildPrompt(item, sourceText) },
    ],
    temperature: 0.35,
    max_tokens: 6000,
    response_format: { type: 'json_object' },
  };

  const doRequest = async (withResponseFormat) => {
    const payload = { ...body };
    if (!withResponseFormat) delete payload.response_format;
    return fetch(OPENROUTER_CHAT_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://infold.f5.si',
        'X-Title': 'INFOLD',
      },
      body: JSON.stringify(payload),
    });
  };

  let resp = await doRequest(true);
  // Some free models reject response_format. Retry once without it, but
  // never switch to another model/provider.
  if (resp.status === 400) {
    resp = await doRequest(false);
  }

  const raw = await resp.text().catch(() => '');
  if (resp.status === 429) {
    const retryAfter = resp.headers.get('retry-after') || '';
    throw makeError('rate_limited', 'OpenRouter rate limit reached', 429, { retryAfter });
  }
  if (!resp.ok) {
    throw makeError(
      'upstream_error',
      `OpenRouter API failed: HTTP ${resp.status} ${raw.slice(0, 300)}`,
      resp.status === 401 ? 502 : 502
    );
  }

  let data;
  try { data = JSON.parse(raw); } catch (_) {
    throw makeError('invalid_json', 'OpenRouter returned a non-JSON response', 502);
  }
  const choice = data && Array.isArray(data.choices) ? data.choices[0] : null;
  const text = choice && choice.message ? choice.message.content : '';
  if (!text) throw makeError('empty_response', 'OpenRouter returned an empty response', 502);
  const usage = data.usage || {};
  return {
    parsed: parseJsonResponse(text),
    usage: {
      prompt: Number(usage.prompt_tokens) || 0,
      completion: Number(usage.completion_tokens) || 0,
    },
    rawModel: data.model || model,
  };
}

// ------------------------------------------------------------
// Generation
// ------------------------------------------------------------

async function countToday(env) {
  const row = await env.DB.prepare(
    'SELECT COUNT(*) AS c FROM ai_generations WHERE created_date = ?'
  ).bind(jstDateStr()).first();
  return Number(row?.c) || 0;
}

async function insertGeneration(env, data) {
  const res = await env.DB.prepare(
    `INSERT INTO ai_generations
       (news_item_id, source_url, source_name, model, status, title, lead,
        content_json, content_markdown, sources_json, editorial_notes, article_id,
        error, tokens_prompt, tokens_completion, created_date, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    data.newsItemId || null,
    String(data.sourceUrl || '').slice(0, 1000),
    String(data.sourceName || '').slice(0, 120),
    String(data.model || '').slice(0, 200),
    data.status,
    String(data.title || '').slice(0, 200),
    String(data.lead || '').slice(0, 600),
    JSON.stringify(data.content || []),
    String(data.contentMarkdown || '').slice(0, 100000),
    JSON.stringify(data.sources || []),
    String(data.editorialNotes || '').slice(0, 1000),
    data.articleId || null,
    String(data.error || '').slice(0, 1000) || null,
    Number(data.tokensPrompt) || 0,
    Number(data.tokensCompletion) || 0,
    jstDateStr(),
    nowISO(),
    nowISO()
  ).run();
  return Number(res.meta.last_row_id);
}

async function markNewsStatus(env, newsItemId, status) {
  if (!newsItemId) return;
  await env.DB.prepare(
    'UPDATE ai_news_items SET status = ?, updated_at = ? WHERE id = ?'
  ).bind(status, nowISO(), newsItemId).run().catch(() => {});
}

/**
 * Generate one article from one ai_news_items row.
 * Returns { generationId, articleId, status, duplicate? }.
 */
export async function generateFromNewsItem(env, newsItemId, options = {}) {
  const config = await getConfig(env);
  if (!config.configured) throw makeError('not_configured', 'OpenRouter API key is not configured', 503);
  if (!config.enabled && !options.ignoreEnabled) {
    throw makeError('disabled', 'OpenRouter article generation is disabled', 409);
  }
  if (!config.freeModel) throw makeError('free_model_required', 'Only :free models are allowed', 409);

  const item = await getNewsItem(env, newsItemId);
  if (!item) throw makeError('not_found', 'News item not found', 404);

  if (!options.force) {
    const existing = await env.DB.prepare(
      `SELECT id, article_id, status FROM ai_generations
       WHERE news_item_id = ? AND status IN ('draft', 'published')
       ORDER BY id DESC LIMIT 1`
    ).bind(item.id).first();
    if (existing) {
      return {
        ok: true,
        duplicate: true,
        generationId: existing.id,
        articleId: existing.article_id,
        status: existing.status,
      };
    }
  }

  if (!options.skipLimit) {
    const used = await countToday(env);
    if (used >= config.dailyLimit) {
      throw makeError('daily_limit_reached', `Daily AI generation limit (${config.dailyLimit}) reached`, 429);
    }
  }

  const modelCheck = await checkOpenRouterModel(env, config.model, config.apiKey);
  if (!modelCheck.ok) {
    throw makeError(modelCheck.error, modelCheck.message || 'Free model is not available', 503);
  }

  let sourceText = String(item.raw_content || '').trim();
  if (sourceText.length < 180) {
    sourceText = await fetchSourceText(item.source_url);
  }

  try {
    const result = await callOpenRouter(env, config.model, item, sourceText, config.apiKey);
    const article = validateGeneratedArticle(result.parsed, item);
    const markdown = blocksToMarkdown(article.content);
    const autoPublish = options.autoPublish !== undefined ? !!options.autoPublish : config.autoPublish;
    const thumbnail = item.image_url || makeThumbnailSvg(article.title, article.category, item.source_name);
    const publishedAt = autoPublish ? nowISO() : null;

    const insert = await env.DB.prepare(
      `INSERT INTO articles
         (title, description, content, thumbnail, category, status, featured,
          source_url, source_name, published_at, is_automated, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)`
    ).bind(
      article.title,
      article.lead,
      markdown,
      thumbnail,
      article.category,
      autoPublish ? 'published' : 'draft',
      autoPublish ? 1 : 0,
      item.source_url,
      item.source_name || '',
      publishedAt,
      nowISO(),
      nowISO()
    ).run();
    const articleId = Number(insert.meta.last_row_id);

    const generationId = await insertGeneration(env, {
      newsItemId: item.id,
      sourceUrl: item.source_url,
      sourceName: item.source_name,
      model: config.model,
      status: autoPublish ? 'published' : 'draft',
      title: article.title,
      lead: article.lead,
      content: article.content,
      contentMarkdown: markdown,
      sources: article.sources,
      editorialNotes: article.editorial_notes,
      articleId,
      tokensPrompt: result.usage.prompt,
      tokensCompletion: result.usage.completion,
    });

    await markNewsStatus(env, item.id, 'generated');
    if (autoPublish) {
      await notifyArticlePublished(env, articleId).catch(() => {});
    }

    return {
      ok: true,
      generationId,
      articleId,
      status: autoPublish ? 'published' : 'draft',
      title: article.title,
      editorialNotes: article.editorial_notes,
    };
  } catch (err) {
    const code = err && err.code ? err.code : 'generation_failed';
    const message = String((err && err.message) || err).slice(0, 1000);
    await insertGeneration(env, {
      newsItemId: item.id,
      sourceUrl: item.source_url,
      sourceName: item.source_name,
      model: config.model,
      status: code === 'needs_review' ? 'needs_review' : 'failed',
      title: '',
      lead: '',
      content: [],
      contentMarkdown: '',
      sources: [],
      editorialNotes: err && err.editorialNotes ? err.editorialNotes : '',
      error: message,
    }).catch(() => {});
    await markNewsStatus(env, item.id, code === 'needs_review' ? 'skipped' : 'failed');
    throw err instanceof OpenRouterError
      ? err
      : makeError(code, message, 502);
  }
}

/** Scheduled pipeline: choose one fresh candidate and publish/draft it. */
export async function generateDailyArticle(env) {
  const config = await getConfig(env);
  if (!config.configured) throw makeError('not_configured', 'OpenRouter API key is not configured', 503);
  if (!config.enabled) throw makeError('disabled', 'OpenRouter article generation is disabled', 409);
  if (!config.autoGenerate) throw makeError('auto_generate_disabled', 'OpenRouter auto generation is disabled', 409);

  await syncNewsItems(env);
  // Scheduled posts only use Google News feeds; manual generation can still
  // pick any collected source from the admin screen.
  const candidates = await listGoogleNewsCandidates(env, 6);

  if (!candidates.length) throw makeError('no_candidate', 'No unused news candidate found', 409);

  let lastErr = null;
  for (const item of candidates) {
    try {
      return await generateFromNewsItem(env, item.id, { autoPublish: config.autoPublish });
    } catch (err) {
      lastErr = err;
      // Rate limits, configuration errors, and daily limits must stop the
      // run rather than burning through more candidates.
      if (['rate_limited', 'daily_limit_reached', 'not_configured', 'free_model_required',
        'model_unavailable', 'not_free_model', 'connection_failed', 'disabled',
        'auto_generate_disabled'].includes(err && err.code)) {
        throw err;
      }
      // needs_review / invalid output: try the next candidate (bounded).
    }
  }
  throw lastErr || makeError('generation_failed', 'All AI generation candidates failed', 502);
}

export async function listGenerations(env, limit = 50) {
  const rows = await env.DB.prepare(
    `SELECT id, news_item_id, source_url, source_name, model, status, title, lead,
            editorial_notes, article_id, error, tokens_prompt, tokens_completion,
            created_date, created_at, updated_at
     FROM ai_generations ORDER BY id DESC LIMIT ?`
  ).bind(clampInt(limit, 1, 100, 50)).all();
  return rows.results || [];
}

/** Small connection test. It does not reveal the key. */
export async function testOpenRouter(env, modelOverride, apiKeyOverride) {
  const config = await getConfig(env);
  const apiKey = String(apiKeyOverride || '').trim() || config.apiKey;
  if (!apiKey) {
    return { ok: false, error: 'not_configured', message: 'OpenRouter API key is not configured' };
  }
  const model = String(modelOverride || config.model).trim();
  const check = await checkOpenRouterModel(env, model, apiKey);
  if (!check.ok) return check;

  try {
    const resp = await fetch(OPENROUTER_CHAT_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://infold.f5.si',
        'X-Title': 'INFOLD',
      },
      body: JSON.stringify({
        model,
        messages: [{ role: 'user', content: 'Return exactly this JSON and nothing else: {"ok":true}' }],
        temperature: 0,
        // Reasoning models spend completion tokens on thinking first, so a
        // tiny budget returns an empty message. Keep this generous.
        max_tokens: 512,
      }),
    });
    if (resp.status === 429) {
      return { ok: true, model: check.model, name: check.name, warning: 'rate_limited' };
    }
    const raw = await resp.text().catch(() => '');
    if (!resp.ok) {
      return { ok: false, error: 'connection_failed', message: `HTTP ${resp.status} ${raw.replace(/\s+/g, ' ').slice(0, 200)}` };
    }
    let data = null;
    try { data = JSON.parse(raw); } catch (_) { data = null; }
    const choice = data && Array.isArray(data.choices) ? data.choices[0] : null;
    const content = choice && choice.message ? choice.message.content : '';
    if (!content) {
      const finish = (choice && choice.finish_reason) || 'unknown';
      return {
        ok: false,
        error: 'empty_response',
        message: `HTTP 200 but the model returned no text (finish_reason: ${finish})`,
      };
    }
    return { ok: true, model: check.model, name: check.name, free: true };
  } catch (err) {
    return { ok: false, error: 'connection_failed', message: String((err && err.message) || err).slice(0, 300) };
  }
}
