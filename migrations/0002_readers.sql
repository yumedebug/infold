-- ============================================================
-- INFOLD News - D1 Migration 0002 (reader accounts)
--
-- Adds reader signup/login for the points feature. Only NEW tables
-- are created here; existing tables are left untouched.
-- Apply with:
--   wrangler d1 execute news-site-db --remote --file=migrations/0002_readers.sql
-- ============================================================

-- ------------------------------------------------------------
-- readers: reader accounts (password stored as PBKDF2 hash,
-- same scheme as admin users)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS readers (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  name          TEXT NOT NULL DEFAULT '',
  created_at    TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at    TEXT
);

-- ------------------------------------------------------------
-- reader_sessions: HttpOnly cookie sessions for readers
-- (separate from the admin sessions table)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reader_sessions (
  id         TEXT PRIMARY KEY,
  reader_id  INTEGER NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (reader_id) REFERENCES readers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reader_sessions_reader  ON reader_sessions (reader_id);
CREATE INDEX IF NOT EXISTS idx_reader_sessions_expires ON reader_sessions (expires_at);
