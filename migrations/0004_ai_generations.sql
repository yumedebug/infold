-- ============================================================
-- INFOLD News - D1 Migration 0004 (OpenRouter AI article generation)
--
-- Apply with:
--   wrangler d1 execute news-site-db --local  --file=migrations/0004_ai_generations.sql
--   wrangler d1 execute news-site-db --remote --file=migrations/0004_ai_generations.sql
--
-- This migration is additive. It never drops or rewrites existing data.
-- ============================================================

-- ------------------------------------------------------------
-- ai_news_items: fetched RSS / ITFIS candidates waiting for AI writing
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_news_items (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  source_url   TEXT NOT NULL UNIQUE,
  title        TEXT NOT NULL,
  source_name  TEXT NOT NULL DEFAULT '',
  published_at TEXT,
  description  TEXT NOT NULL DEFAULT '',
  raw_content  TEXT NOT NULL DEFAULT '',
  image_url    TEXT NOT NULL DEFAULT '',
  status       TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new', 'generated', 'failed', 'skipped')),
  created_at   TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT
);
CREATE INDEX IF NOT EXISTS idx_ai_news_status_published ON ai_news_items (status, published_at);

-- ------------------------------------------------------------
-- ai_generations: immutable generation history (regeneration adds a row)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_generations (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  news_item_id      INTEGER,
  source_url        TEXT NOT NULL,
  source_name       TEXT NOT NULL DEFAULT '',
  model             TEXT NOT NULL,
  status            TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'failed', 'needs_review')),
  title             TEXT NOT NULL DEFAULT '',
  lead              TEXT NOT NULL DEFAULT '',
  content_json      TEXT NOT NULL DEFAULT '[]',
  content_markdown  TEXT NOT NULL DEFAULT '',
  sources_json      TEXT NOT NULL DEFAULT '[]',
  editorial_notes   TEXT NOT NULL DEFAULT '',
  article_id        INTEGER,
  error             TEXT,
  tokens_prompt     INTEGER NOT NULL DEFAULT 0,
  tokens_completion INTEGER NOT NULL DEFAULT 0,
  created_date      TEXT NOT NULL,
  created_at        TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT
);
CREATE INDEX IF NOT EXISTS idx_ai_generations_news   ON ai_generations (news_item_id, id);
CREATE INDEX IF NOT EXISTS idx_ai_generations_date   ON ai_generations (created_date);
CREATE INDEX IF NOT EXISTS idx_ai_generations_status ON ai_generations (status, id);

-- ------------------------------------------------------------
-- OpenRouter settings. The API key is not seeded here: it is saved from the
-- admin screen into settings.openrouter_api_key when an admin enters one.
-- ------------------------------------------------------------
INSERT OR IGNORE INTO settings (key, value) VALUES ('openrouter_enabled', '1');
INSERT OR IGNORE INTO settings (key, value) VALUES ('openrouter_auto_generate', '1');
INSERT OR IGNORE INTO settings (key, value) VALUES ('openrouter_auto_publish', '1');
INSERT OR IGNORE INTO settings (key, value) VALUES ('openrouter_daily_limit', '10');
INSERT OR IGNORE INTO settings (key, value) VALUES ('openrouter_model', 'openrouter/free');
