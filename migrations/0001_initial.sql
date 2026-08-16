-- ============================================================
-- INFOLD News - D1 Migration 0001 (initial schema)
-- Apply with:
--   wrangler d1 execute infold-news-db --local  --file=migrations/0001_initial.sql
--   wrangler d1 execute infold-news-db --remote --file=migrations/0001_initial.sql
-- ============================================================

-- ------------------------------------------------------------
-- users: admin accounts (password stored as PBKDF2 hash, never plaintext)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  name          TEXT NOT NULL DEFAULT '',
  role          TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('admin')),
  created_at    TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at    TEXT
);

-- ------------------------------------------------------------
-- categories: editable by admin (JA + EN display names)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT NOT NULL,
  name_en    TEXT NOT NULL DEFAULT '',
  slug       TEXT NOT NULL UNIQUE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ------------------------------------------------------------
-- articles
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS articles (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  title        TEXT NOT NULL,
  description  TEXT NOT NULL DEFAULT '',
  content      TEXT NOT NULL DEFAULT '',
  thumbnail    TEXT NOT NULL DEFAULT '',
  category     TEXT NOT NULL DEFAULT 'other',
  status       TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
  featured     INTEGER NOT NULL DEFAULT 0 CHECK (featured IN (0, 1)),
  source_url   TEXT,
  source_name  TEXT,
  published_at TEXT,
  created_at   TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT,
  is_automated INTEGER NOT NULL DEFAULT 0 CHECK (is_automated IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_articles_status_published ON articles (status, published_at);
CREATE INDEX IF NOT EXISTS idx_articles_category_status  ON articles (category, status);
CREATE INDEX IF NOT EXISTS idx_articles_featured         ON articles (featured, status);
CREATE INDEX IF NOT EXISTS idx_articles_source_url       ON articles (source_url);

-- ------------------------------------------------------------
-- sessions: HttpOnly cookie sessions (token stored hashed)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
  id         TEXT PRIMARY KEY,
  user_id    INTEGER NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sessions_user    ON sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions (expires_at);

-- ------------------------------------------------------------
-- automation_state: one row per day for auto-posting (dedup guard)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS automation_state (
  date            TEXT PRIMARY KEY, -- 'YYYY-MM-DD' (Asia/Tokyo)
  status          TEXT NOT NULL DEFAULT 'pending', -- pending|running|retrying|success|failed
  last_attempt_at TEXT,
  next_retry_at   TEXT,
  attempt_count   INTEGER NOT NULL DEFAULT 0,
  article_id      INTEGER,
  error           TEXT,
  created_at      TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at      TEXT
);

-- ------------------------------------------------------------
-- settings: key/value app settings (automation_enabled etc.)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS settings (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL DEFAULT '',
  updated_at TEXT
);

-- ------------------------------------------------------------
-- Seed default categories (admin can add/edit/delete later)
-- ------------------------------------------------------------
INSERT OR IGNORE INTO categories (name, name_en, slug, sort_order) VALUES
  ('IT',          'IT',          'it',          1),
  ('AI',          'AI',          'ai',          2),
  ('Windows',     'Windows',     'windows',     3),
  ('Android',     'Android',     'android',     4),
  ('Apple',       'Apple',       'apple',       5),
  ('Web',         'Web',         'web',         6),
  ('Programming', 'Programming', 'programming', 7),
  ('その他',       'Other',       'other',       8);

-- ------------------------------------------------------------
-- Seed default settings
-- ------------------------------------------------------------
INSERT OR IGNORE INTO settings (key, value) VALUES ('automation_enabled', '1');
