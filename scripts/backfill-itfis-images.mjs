// ============================================================
// Backfill: replace SVG placeholder thumbnails of existing ITFIS
// articles with the real featured image from itfis.net.
//
// Usage: node scripts/backfill-itfis-images.mjs
// Requires: wrangler auth (wrangler login or CLOUDFLARE_API_TOKEN), Node 18+.
//
// Image resolution order per article:
//   1. WP REST API by post id (source_url like /archives/<id>) with _embed
//   2. og:image meta scraped from the article page
// Only rows where a real https image is found are updated.
// ============================================================

import { execFileSync } from 'node:child_process';
import { writeFileSync, unlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const DB = 'news-site-db';
// Run the local wrangler JS directly so it works on Windows (no .cmd spawning).
const WRANGLER = join('node_modules', 'wrangler', 'bin', 'wrangler.js');
const ITFIS_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

function d1(command) {
  const out = execFileSync(
    process.execPath,
    [WRANGLER, 'd1', 'execute', DB, '--remote', '--json', '--command', command],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'], maxBuffer: 64 * 1024 * 1024 }
  );
  const parsed = JSON.parse(out);
  return (parsed[0] && parsed[0].results) || [];
}

function sqlEscape(s) {
  return String(s).replace(/'/g, "''");
}

async function resolveImage(sourceUrl) {
  // 1) WP REST API by post id (/archives/<id>)
  const m = String(sourceUrl || '').match(/\/archives\/(\d+)(?:[/?#]|$)/);
  if (m) {
    try {
      const resp = await fetch(`https://itfis.net/wp-json/wp/v2/posts/${m[1]}?_embed=1`, {
        headers: { 'User-Agent': ITFIS_UA, Accept: 'application/json' },
        signal: AbortSignal.timeout(15000),
      });
      if (resp.ok) {
        const post = await resp.json();
        const fm = post && post._embedded && post._embedded['wp:featuredmedia'];
        const u = fm && fm[0] && fm[0].source_url;
        if (u && /^https?:\/\//i.test(u)) return u;
      }
    } catch (_) {
      /* fall through */
    }
  }
  // 2) og:image meta scraped from the article page
  try {
    const resp = await fetch(sourceUrl, {
      headers: { 'User-Agent': ITFIS_UA },
      signal: AbortSignal.timeout(15000),
      redirect: 'follow',
    });
    if (resp.ok) {
      const html = await resp.text();
      const og =
        html.match(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i) ||
        html.match(/<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']/i);
      const u = og && og[1];
      if (u && /^https?:\/\//i.test(u)) return u;
    }
  } catch (_) {
    /* fall through */
  }
  return '';
}

const rows = d1(
  `SELECT id, source_url, thumbnail FROM articles WHERE source_name = 'ITFIS'`
);
const targets = rows.filter((r) => String(r.thumbnail || '').startsWith('data:image'));
console.log(`ITFIS articles: ${rows.length}, SVG placeholders to fix: ${targets.length}`);

const updates = [];
let ok = 0;
let fail = 0;
for (const r of targets) {
  const url = await resolveImage(r.source_url);
  if (url) {
    updates.push(
      `UPDATE articles SET thumbnail = '${sqlEscape(url)}' WHERE id = ${Number(r.id)};`
    );
    ok++;
    console.log(`  [ok] id=${r.id} -> ${url}`);
  } else {
    fail++;
    console.log(`  [no image found] id=${r.id} ${r.source_url}`);
  }
}

if (!updates.length) {
  console.log('No updates to apply.');
  process.exit(0);
}

const sql = updates.join('\n');
const tmp = join(tmpdir(), `backfill-itfis-${Date.now()}.sql`);
writeFileSync(tmp, sql);
try {
  execFileSync(process.execPath, [WRANGLER, 'd1', 'execute', DB, '--remote', '--file', tmp], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'inherit'],
    maxBuffer: 64 * 1024 * 1024,
  });
} finally {
  unlinkSync(tmp);
}
console.log(`Applied ${ok} thumbnail updates (${fail} articles skipped).`);
