// ============================================================
// INFOLD News - shared utilities
// ============================================================

/** JSON response helper */
export function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', ...extraHeaders },
  });
}

export const pad = (n) => String(n).padStart(2, '0');

/** A Date object holding the current wall-clock time in Asia/Tokyo */
export function jstNow() {
  return new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Tokyo' }));
}

/** 'YYYY-MM-DD' for the given (JST) date */
export function jstDateStr(d = jstNow()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 'HH:mm' JST */
export function jstTime(d = jstNow()) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 'YYYY-MM-DD HH:mm:ss' JST */
export function jstDateTime(d = jstNow()) {
  return `${jstDateStr(d)} ${jstTime(d)}:${pad(d.getSeconds())}`;
}

/** ISO-8601 UTC timestamp (what we store in D1) */
export function nowISO() {
  return new Date().toISOString();
}

/** Loose slugifier (keeps ascii, kana and kanji) */
export function slugify(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFKC')
    .replace(/[^a-z0-9\u3040-\u30ff\u4e00-\u9fff-]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** Parse an integer safely inside [min, max], falling back to `def` */
export function clampInt(v, min, max, def) {
  const n = parseInt(v, 10);
  if (Number.isNaN(n)) return def;
  return Math.min(max, Math.max(min, n));
}

/** Read + parse a JSON request body (never throws) */
export function readBodyJson(request) {
  return request.json().catch(() => null);
}

/** True when the current request is served over HTTPS (for Secure cookie) */
export function isSecureRequest(request) {
  const url = new URL(request.url);
  return url.protocol === 'https:' || request.headers.get('x-forwarded-proto') === 'https';
}
