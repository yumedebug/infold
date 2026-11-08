#!/usr/bin/env node
// ============================================================
// cloud-deploy を組み立てる（Cloudflare Workers へ送るファイルだけ）
//
//   node scripts/build-cloud-deploy.mjs
//   npm run build:deploy
//
// 含めるもの:
//   worker/          （main の Worker スクリプト一式）
//   html/            （assets.directory。index.html / sw.js / manifest.json / icons / .well-known）
//   wrangler.jsonc   （Worker 名・ルート・D1 バインディング。書き換えない）
//   package.json / package-lock.json （npx wrangler 実行用）
//
// 含めないもの:
//   android/ node_modules/ .wrangler/ .dev.vars .git/ .freebuff/
//   migrations/ scripts/ README（Workers へアップロードされないため）
//
// 出力先は毎回作り直す（cloud-deploy/ の中身は削除される）。
// ============================================================

import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OUT_NAME = 'cloud-deploy';
const OUT = join(ROOT, OUT_NAME);

/** コピーする対象（この順で出力する） */
const ENTRIES = [
  { from: 'worker', required: true },
  { from: 'html', required: true },
  { from: 'wrangler.jsonc', required: true },
  { from: 'package.json', required: true },
  { from: 'package-lock.json', required: false },
];

/** 万一 worker/ や html/ の中に紛れていても絶対にコピーしないもの */
const NEVER_COPY = [
  /(^|\/)node_modules(\/|$)/,
  /(^|\/)\.wrangler(\/|$)/,
  /(^|\/)\.dev\.vars$/,
  /(^|\/)\.dev\.vars\./,
  /(^|\/)\.git(\/|$)/,
  /(^|\/)\.freebuff(\/|$)/,
  /(^|\/)\.DS_Store$/,
  /\.log$/,
];

/** wrangler が実際に必要とするファイル（存在しなければ失敗させる） */
const MUST_EXIST_IN_OUTPUT = ['worker/src/index.js', 'html/index.html', 'wrangler.jsonc'];

function fail(message) {
  console.error(`✖ ${message}`);
  process.exit(1);
}

function toPosix(p) {
  return p.split('\\').join('/');
}

function shouldCopy(source) {
  const rel = toPosix(relative(ROOT, source));
  return !NEVER_COPY.some((re) => re.test(rel));
}

/** 出力ディレクトリ配下のファイルを再帰的に集める */
function walk(dir, acc = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.isFile()) acc.push(full);
  }
  return acc;
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

// ---- 出力先の安全確認 ----------------------------------------
// cloud-deploy 以外を消してしまわないよう、パスを必ず検証する。
if (dirname(OUT) !== ROOT || OUT_NAME !== 'cloud-deploy') {
  fail(`unexpected output path: ${OUT}`);
}

console.log(`INFOLD — building ${OUT_NAME}/ from ${ROOT}`);

// ---- 作り直し ------------------------------------------------
rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

const copied = [];
const skipped = [];

for (const entry of ENTRIES) {
  const src = join(ROOT, entry.from);
  if (!existsSync(src)) {
    if (entry.required) fail(`missing required entry: ${entry.from}`);
    skipped.push(`${entry.from} (not found, skipped)`);
    continue;
  }
  cpSync(src, join(OUT, entry.from), { recursive: true, filter: shouldCopy });
  copied.push(entry.from);
}

// ---- 検証 ----------------------------------------------------
const missing = MUST_EXIST_IN_OUTPUT.filter((p) => !existsSync(join(OUT, p)));
if (missing.length) fail(`output is incomplete, missing: ${missing.join(', ')}`);

// 秘密鍵が混入していないことを確認する（.dev.vars は絶対に入れない）
const leaked = walk(OUT).filter((f) => /(^|\/)\.dev\.vars($|\.)/.test(toPosix(relative(OUT, f))));
if (leaked.length) fail(`secret file must not be copied: ${leaked.join(', ')}`);

// ---- レポート ------------------------------------------------
const files = walk(OUT).sort((a, b) => toPosix(a).localeCompare(toPosix(b)));
let total = 0;
for (const file of files) total += statSync(file).size;

console.log('');
for (const file of files) {
  const rel = toPosix(relative(OUT, file));
  console.log(`  ${rel.padEnd(38)} ${formatBytes(statSync(file).size).padStart(9)}`);
}
for (const note of skipped) console.log(`  - ${note}`);
console.log('');
console.log(`✔ ${files.length} files, ${formatBytes(total)} -> ${OUT_NAME}/`);
console.log('');
console.log('next:');
console.log(`  cd ${OUT_NAME}`);
console.log('  npm install');
console.log('  npx wrangler deploy --dry-run   # 設定と送信内容の確認（アップロードしない）');
console.log('  npx wrangler deploy             # 既存の infold-news を更新');
console.log('');
console.log('note: deploy は wrangler.jsonc の name / routes / d1_databases をそのまま使うため、');
console.log('      新しい Worker は作られず、既存サイト（infold.f5.si）の更新になります。');
