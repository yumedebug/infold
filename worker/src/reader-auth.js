// ============================================================
// INFOLD News - reader authentication (points feature)
//
// Readers are separate from admin users. They live in the news D1
// (readers / reader_sessions tables — new tables only, existing
// tables untouched) and get their own HttpOnly cookie, distinct
// from the admin session cookie.
// ============================================================

import { json, nowISO, readBodyJson, isSecureRequest } from './util.js';
import { hashPassword, verifyPassword, randomToken, sha256hex, readCookie } from './auth.js';

export const READER_COOKIE = 'infold_reader_session';
const READER_SESSION_DAYS = 30;

export function readerCookieHeader(token, secure) {
  const parts = [
    `${READER_COOKIE}=${token}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
    `Max-Age=${READER_SESSION_DAYS * 86400}`,
  ];
  if (secure) parts.push('Secure');
  return parts.join('; ');
}

export function clearReaderCookieHeader() {
  return `${READER_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`;
}

export async function createReaderSession(env, readerId) {
  const token = randomToken();
  const hash = await sha256hex(token);
  const expiresAt = new Date(Date.now() + READER_SESSION_DAYS * 864e5).toISOString();
  await env.DB.prepare(
    'INSERT INTO reader_sessions (id, reader_id, token_hash, expires_at) VALUES (?, ?, ?, ?)'
  ).bind(crypto.randomUUID(), readerId, hash, expiresAt).run();
  return token;
}

export async function deleteReaderSession(env, token) {
  if (!token) return;
  const hash = await sha256hex(token);
  await env.DB.prepare('DELETE FROM reader_sessions WHERE token_hash = ?').bind(hash).run();
}

/** Resolve the current reader from the session cookie (null when unauthenticated) */
export async function getSessionReader(env, request) {
  const token = readCookie(request, READER_COOKIE);
  if (!token) return null;
  const hash = await sha256hex(token);
  const row = await env.DB.prepare(
    `SELECT r.id, r.email, r.name, s.expires_at AS expires_at
     FROM reader_sessions s JOIN readers r ON r.id = s.reader_id
     WHERE s.token_hash = ?`
  ).bind(hash).first();
  if (!row) return null;
  if (new Date(row.expires_at).getTime() < Date.now()) {
    await env.DB.prepare('DELETE FROM reader_sessions WHERE token_hash = ?').bind(hash).run();
    return null;
  }
  return { id: row.id, email: row.email, name: row.name };
}

// ------------------------------------------------------------
// API handlers
// ------------------------------------------------------------

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export async function registerReader(env, request) {
  const body = await readBodyJson(request);
  const email = String(body?.email || '').trim().toLowerCase();
  const password = String(body?.password || '');
  const name = String(body?.name || '').trim().slice(0, 50);
  if (!EMAIL_RE.test(email)) return json({ error: 'invalid_email' }, 400);
  if (password.length < 8) return json({ error: 'password_too_short' }, 400);
  const passwordHash = await hashPassword(password);
  try {
    const res = await env.DB.prepare(
      'INSERT INTO readers (email, password_hash, name) VALUES (?, ?, ?)'
    ).bind(email, passwordHash, name).run();
    const id = Number(res.meta.last_row_id);
    const token = await createReaderSession(env, id);
    return json({ ok: true, user: { id, email, name } }, 201, {
      'Set-Cookie': readerCookieHeader(token, isSecureRequest(request)),
    });
  } catch (_) {
    return json({ error: 'email_taken' }, 409);
  }
}

export async function loginReader(env, request) {
  const body = await readBodyJson(request);
  const email = String(body?.email || '').trim().toLowerCase();
  const password = String(body?.password || '');
  if (!email || !password) return json({ error: 'invalid_request' }, 400);
  const row = await env.DB.prepare(
    'SELECT id, email, name, password_hash FROM readers WHERE email = ?'
  ).bind(email).first();
  if (!row || !(await verifyPassword(password, row.password_hash))) {
    return json({ error: 'invalid_credentials' }, 401);
  }
  const token = await createReaderSession(env, row.id);
  return json({ ok: true, user: { id: row.id, email: row.email, name: row.name } }, 200, {
    'Set-Cookie': readerCookieHeader(token, isSecureRequest(request)),
  });
}

export async function logoutReader(env, request) {
  const token = readCookie(request, READER_COOKIE);
  await deleteReaderSession(env, token);
  return json({ ok: true }, 200, { 'Set-Cookie': clearReaderCookieHeader() });
}

export async function meReader(env, request) {
  const reader = await getSessionReader(env, request);
  return json({ user: reader });
}
