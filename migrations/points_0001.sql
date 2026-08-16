-- ============================================================
-- INFOLD POINTS - D1 Migration 0001 (initial schema)
--
-- Dedicated points database. Only points-related data lives here.
-- user_id refers to readers.id in the news D1 (no cross-DB FK).
-- All timestamps are stored as ISO-8601 UTC strings, written
-- explicitly by the Worker (nowISO()).
-- Apply with:
--   wrangler d1 execute INFOLD_POINTS --remote --file=migrations/points_0001.sql
-- ============================================================

-- ------------------------------------------------------------
-- point_balances: current points per user
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS point_balances (
  user_id    INTEGER PRIMARY KEY,
  points     INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT ''
);

-- ------------------------------------------------------------
-- point_history: point movements (earn / spend)
--   type: 'read' (earn) | 'ad_free' (spend)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS point_history (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL,
  amount     INTEGER NOT NULL,
  type       TEXT NOT NULL,
  article_id INTEGER,
  reason     TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_point_history_user ON point_history (user_id, id);

-- ------------------------------------------------------------
-- article_point_claims: last time a user earned points per article
--   (1 user x 1 article = 1 point every 3 hours)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS article_point_claims (
  user_id         INTEGER NOT NULL,
  article_id      INTEGER NOT NULL,
  last_claimed_at TEXT NOT NULL,
  PRIMARY KEY (user_id, article_id)
);

-- ------------------------------------------------------------
-- ad_free_periods: purchased ad-free periods
--   duration: length in seconds
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ad_free_periods (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id      INTEGER NOT NULL,
  started_at   TEXT NOT NULL,
  expires_at   TEXT NOT NULL,
  points_spent INTEGER NOT NULL,
  duration     INTEGER NOT NULL,
  created_at   TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_ad_free_user ON ad_free_periods (user_id, expires_at);
