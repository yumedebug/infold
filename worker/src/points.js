// ============================================================
// INFOLD News - points engine
//
// Uses the dedicated INFOLD_POINTS D1 (binding POINTS). user_id
// refers to readers.id in the news D1. The server is the source
// of truth for balances, claim times and ad-free expiry; client
// clocks are never trusted.
//
// Rules:
//   - Reading an article awards +1 point, at most once per 3 hours
//     per user per article ("1ユーザー × 1記事につき、3時間に1回、1ポイント").
//   - The only benefit is a temporary ad-free period:
//       10 POINT -> 6 hours
//       30 POINT -> 24 hours
//       70 POINT -> 7 days
// ============================================================

import { json } from './util.js';
import { getSessionReader } from './reader-auth.js';

const CLAIM_INTERVAL_MS = 3 * 60 * 60 * 1000; // 3 hours

export const AD_FREE_PLANS = [
  { points: 10, durationHours: 6, durationLabel: '6h' },
  { points: 30, durationHours: 24, durationLabel: '24h' },
  { points: 70, durationHours: 168, durationLabel: '7d' },
];

async function getBalance(env, userId) {
  const row = await env.POINTS.prepare('SELECT points FROM point_balances WHERE user_id = ?')
    .bind(userId).first();
  return row ? Number(row.points) : 0;
}

/**
 * Award +1 point for finishing an article (30s + 80% scroll are measured
 * client-side; here we enforce login + the 3-hour per-article window).
 * Returns { awarded, points?, nextAvailableAt? }.
 */
export async function awardArticlePoints(env, reader, articleId) {
  const now = new Date().toISOString();

  const article = await env.DB.prepare('SELECT id FROM articles WHERE id = ? AND status = ?')
    .bind(articleId, 'published').first();
  if (!article) return { awarded: false, error: 'not_found' };

  // Atomic claim (compare-and-swap): the UPDATE branch only succeeds when the
  // previous claim is older than the 3-hour window, so concurrent requests
  // (multi-tab, reload, API spam) can never double-award.
  const cutoff = new Date(Date.now() - CLAIM_INTERVAL_MS).toISOString();
  const claim = await env.POINTS.prepare(
    `INSERT INTO article_point_claims (user_id, article_id, last_claimed_at)
     VALUES (?, ?, ?)
     ON CONFLICT(user_id, article_id) DO UPDATE SET last_claimed_at = excluded.last_claimed_at
     WHERE article_point_claims.last_claimed_at <= ?`
  ).bind(reader.id, articleId, now, cutoff).run();

  if (claim.meta.changes === 0) {
    const prev = await env.POINTS.prepare(
      'SELECT last_claimed_at FROM article_point_claims WHERE user_id = ? AND article_id = ?'
    ).bind(reader.id, articleId).first();
    const next = prev && prev.last_claimed_at
      ? new Date(new Date(prev.last_claimed_at).getTime() + CLAIM_INTERVAL_MS).toISOString()
      : null;
    return { awarded: false, nextAvailableAt: next };
  }

  await env.POINTS.batch([
    env.POINTS.prepare(
      `INSERT INTO point_history (user_id, amount, type, article_id, reason, created_at)
       VALUES (?, 1, 'read', ?, 'article_read', ?)`
    ).bind(reader.id, articleId, now),
    env.POINTS.prepare(
      `INSERT INTO point_balances (user_id, points, updated_at) VALUES (?, 1, ?)
       ON CONFLICT(user_id) DO UPDATE SET points = points + 1, updated_at = excluded.updated_at`
    ).bind(reader.id, now),
  ]);

  return { awarded: true, points: await getBalance(env, reader.id) };
}

/**
 * Spend points on a temporary ad-free period.
 * planPoints must be one of AD_FREE_PLANS' points values.
 */
export async function spendPointsForAdFree(env, reader, planPoints) {
  const plan = AD_FREE_PLANS.find((p) => p.points === planPoints);
  if (!plan) return { ok: false, error: 'invalid_plan' };

  const now = new Date();
  const nowIso = now.toISOString();
  const expiresIso = new Date(now.getTime() + plan.durationHours * 3600 * 1000).toISOString();
  const durationSeconds = plan.durationHours * 3600;

  // Atomic spend: only succeeds when the balance covers the cost, so two
  // concurrent requests can never spend the same points twice.
  const spend = await env.POINTS.prepare(
    `UPDATE point_balances SET points = points - ?, updated_at = ?
     WHERE user_id = ? AND points >= ?`
  ).bind(plan.points, nowIso, reader.id, plan.points).run();
  if (spend.meta.changes === 0) {
    return { ok: false, error: 'insufficient_points', points: await getBalance(env, reader.id) };
  }

  await env.POINTS.batch([
    env.POINTS.prepare(
      `INSERT INTO point_history (user_id, amount, type, article_id, reason, created_at)
       VALUES (?, ?, 'ad_free', NULL, 'ad_free_plan', ?)`
    ).bind(reader.id, -plan.points, nowIso),
    env.POINTS.prepare(
      `INSERT INTO ad_free_periods (user_id, started_at, expires_at, points_spent, duration, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(reader.id, nowIso, expiresIso, plan.points, durationSeconds, nowIso),
  ]);

  return { ok: true, plan: plan.points, expiresAt: expiresIso, points: await getBalance(env, reader.id) };
}

/** Current ad-free status (server-side expires_at is the source of truth). */
export async function getAdFreeStatus(env, userId) {
  const now = new Date().toISOString();
  const row = await env.POINTS.prepare(
    'SELECT MAX(expires_at) AS expires_at FROM ad_free_periods WHERE user_id = ? AND expires_at > ?'
  ).bind(userId, now).first();
  const expiresAt = row && row.expires_at ? row.expires_at : null;
  if (!expiresAt) return { active: false };
  const remainingMs = new Date(expiresAt).getTime() - Date.now();
  return { active: true, expiresAt, remainingSeconds: Math.max(0, Math.floor(remainingMs / 1000)) };
}

export async function getPointHistory(env, userId, limit = 50) {
  const rows = await env.POINTS.prepare(
    `SELECT id, amount, type, article_id, reason, created_at
     FROM point_history WHERE user_id = ? ORDER BY id DESC LIMIT ?`
  ).bind(userId, limit).all();
  return rows.results || [];
}

/** Everything the /api/points page needs in one call. */
export async function getPointsSummary(env, reader) {
  const [balance, adFree] = await Promise.all([
    getBalance(env, reader.id),
    getAdFreeStatus(env, reader.id),
  ]);
  return { points: balance, adFree, plans: AD_FREE_PLANS };
}

/** Gate: requires a logged-in reader (401 otherwise). */
export async function requireReader(env, request) {
  const reader = await getSessionReader(env, request);
  if (!reader) return { reader: null, response: json({ error: 'unauthorized', message: 'Login required' }, 401) };
  return { reader, response: null };
}
