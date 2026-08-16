-- ============================================================
-- INFOLD News - D1 Migration 0003 (FCM push notifications)
--
-- Adds tables for the Android app's push notifications.
-- Only NEW tables are created here; existing tables are left untouched.
-- Apply with:
--   wrangler d1 execute news-site-db --remote --file=migrations/0003_push.sql
-- ============================================================

-- ------------------------------------------------------------
-- push_devices: FCM device tokens registered by the Android app
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_devices (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  token      TEXT NOT NULL UNIQUE,
  platform   TEXT NOT NULL DEFAULT 'android',
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_push_devices_updated ON push_devices (updated_at);

-- ------------------------------------------------------------
-- push_notifications: articles already notified (dedupe)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_notifications (
  article_id INTEGER PRIMARY KEY,
  sent_at    TEXT NOT NULL
);
