// ============================================================
// INFOLD News - authentication & sessions
// Passwords: PBKDF2-SHA256 (100,000 iterations, 16-byte salt)
// Sessions:  random 256-bit token, stored in D1 as SHA-256 hash,
//            delivered as HttpOnly / SameSite=Lax cookie.
// ============================================================

import { json } from './util.js';

export const COOKIE_NAME = 'infold_session';
const PBKDF2_ITERATIONS = 100000; // must match scripts/create-admin.js
const SESSION_DAYS = 7;

// ---------- password hashing ----------

function b64(bytes) {
  return btoa(String.fromCharCode(...bytes));
}
function unb64(s) {
  return Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
}

/** Hash a password -> "pbkdf2$<iterations>$<saltB64>$<hashB64>" */
export async function hashPassword(password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    key,
    256
  );
  return `pbkdf2$${PBKDF2_ITERATIONS}$${b64(salt)}$${b64(new Uint8Array(bits))}`;
}

/** Constant-time comparison of a candidate password against a stored hash */
export async function verifyPassword(password, stored) {
  try {
    const parts = String(stored || '').split('$');
    if (parts.length !== 4 || parts[0] !== 'pbkdf2') return false;
    const iterations = parseInt(parts[1], 10);
    if (!Number.isInteger(iterations) || iterations <= 0) return false;
    const salt = unb64(parts[2]);
    const expected = unb64(parts[3]);
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
    const bits = new Uint8Array(
      await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations, hash: 'SHA-256' }, key, 256)
    );
    if (bits.length !== expected.length) return false;
    let diff = 0;
    for (let i = 0; i < bits.length; i++) diff |= bits[i] ^ expected[i];
    return diff === 0;
  } catch {
    return false;
  }
}

// ---------- session tokens ----------

export function randomToken() {
  const b = crypto.getRandomValues(new Uint8Array(32));
  return [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
}

export async function sha256hex(s) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function createSession(env, userId) {
  const token = randomToken();
  const hash = await sha256hex(token);
  const expiresAt = new Date(Date.now() + SESSION_DAYS * 864e5).toISOString();
  await env.DB.prepare(
    'INSERT INTO sessions (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)'
  ).bind(crypto.randomUUID(), userId, hash, expiresAt).run();
  return token;
}

export async function deleteSession(env, token) {
  if (!token) return;
  const hash = await sha256hex(token);
  await env.DB.prepare('DELETE FROM sessions WHERE token_hash = ?').bind(hash).run();
}

/** Resolve the current user from the session cookie (null when unauthenticated) */
export async function getSessionUser(env, request) {
  const token = readCookie(request, COOKIE_NAME);
  if (!token) return null;
  const hash = await sha256hex(token);
  const row = await env.DB.prepare(
    `SELECT u.id, u.email, u.name, u.role, s.expires_at AS expires_at
     FROM sessions s JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ?`
  ).bind(hash).first();
  if (!row) return null;
  if (new Date(row.expires_at).getTime() < Date.now()) {
    await env.DB.prepare('DELETE FROM sessions WHERE token_hash = ?').bind(hash).run();
    return null;
  }
  return { id: row.id, email: row.email, name: row.name, role: row.role };
}

// ---------- cookies ----------

export function cookieHeader(token, secure) {
  const parts = [
    `${COOKIE_NAME}=${token}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
    `Max-Age=${SESSION_DAYS * 86400}`,
  ];
  if (secure) parts.push('Secure');
  return parts.join('; ');
}

export function clearCookieHeader() {
  return `${COOKIE_NAME}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`;
}

export function readCookie(request, name) {
  const header = request.headers.get('Cookie') || '';
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i > -1 && part.slice(0, i).trim() === name) return part.slice(i + 1).trim();
  }
  return null;
}

/** Admin gate: returns { user, response } — response is set when access is denied */
export async function requireAdmin(env, request) {
  const user = await getSessionUser(env, request);
  if (!user) return { user: null, response: json({ error: 'unauthorized', message: 'Not authenticated' }, 401) };
  if (user.role !== 'admin') return { user: null, response: json({ error: 'forbidden', message: 'Forbidden' }, 403) };
  return { user, response: null };
}
