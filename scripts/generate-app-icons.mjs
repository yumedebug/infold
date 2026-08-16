#!/usr/bin/env node
// ============================================================
// INFOLD アイコン生成スクリプト
//
// html/favicon.svg と同じ INFOLD ロゴ（角丸グラデーション +
// 白いマーク）をラスタライズして、以下を生成する。
//   - PWA 用アイコン       html/icons/*.png, html/apple-touch-icon.png
//   - Android 用アイコン   android/app/src/main/res/mipmap-*/*
//
// 依存パッケージなし（Node 標準の zlib で PNG をエンコード）。
// 実行: node scripts/generate-app-icons.mjs
// ============================================================

import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

// ------------------------------------------------------------
// 最小 PNG エンコーダ（RGBA・8bit・非圧縮フィルタ）
// ------------------------------------------------------------
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const t = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}

function encodePNG(size, rgba) {
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type: RGBA
  const raw = Buffer.alloc(size * (1 + size * 4));
  for (let y = 0; y < size; y++) {
    raw[y * (1 + size * 4)] = 0; // filter: none
    rgba.copy(raw, y * (1 + size * 4) + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([sig, pngChunk('IHDR', ihdr), pngChunk('IDAT', deflateSync(raw, { level: 9 })), pngChunk('IEND', Buffer.alloc(0))]);
}

// ------------------------------------------------------------
// 描画ヘルパー
// ------------------------------------------------------------
const clamp01 = (v) => Math.min(1, Math.max(0, v));

// 角丸矩形の符号付き距離（負 = 内部）
function sdRoundRect(px, py, rect) {
  const qx = Math.abs(px - rect.cx) - (rect.hw - rect.r);
  const qy = Math.abs(py - rect.cy) - (rect.hh - rect.r);
  const ax = Math.max(qx, 0);
  const ay = Math.max(qy, 0);
  return Math.hypot(ax, ay) + Math.min(Math.max(qx, qy), 0) - rect.r;
}

// source-over 合成
function blend(buf, size, x, y, color, alpha) {
  if (alpha <= 0 || x < 0 || y < 0 || x >= size || y >= size) return;
  const i = (y * size + x) * 4;
  const sa = clamp01(alpha);
  const da = buf[i + 3] / 255;
  const oa = sa + da * (1 - sa);
  if (oa <= 0) return;
  buf[i] = Math.round((color[0] * sa + buf[i] * da * (1 - sa)) / oa);
  buf[i + 1] = Math.round((color[1] * sa + buf[i + 1] * da * (1 - sa)) / oa);
  buf[i + 2] = Math.round((color[2] * sa + buf[i + 2] * da * (1 - sa)) / oa);
  buf[i + 3] = Math.round(oa * 255);
}

// INFOLD ロゴの色
const GRAD_A = [0, 200, 255]; // #00c8ff
const GRAD_B = [139, 92, 255]; // #8b5cff
const WHITE = [255, 255, 255];

// ロゴの白い要素（favicon.svg と同じ 48x48 座標系）
const LOGO_MARKS = [
  { type: 'rrect', x: 13, y: 17, w: 10, h: 15, r: 3, alpha: 1 },
  { type: 'circle', cx: 18, cy: 10.5, r: 3.6, alpha: 1 },
  { type: 'rrect', x: 27, y: 17, w: 10, h: 15, r: 3, alpha: 0.55 },
];

// style:
//   'full'       濃紺背景 + グロー + ロゴ 76%（通常アイコン）
//   'maskable'   濃紺背景 + ロゴ 60%（maskable セーフゾーン内）
//   'foreground' 透過背景 + ロゴ 55%（Android アダプティブアイコン用）
function renderIcon(size, style) {
  const buf = Buffer.alloc(size * size * 4);
  const c = size / 2;
  const margin =
    style === 'maskable' ? size * 0.2 : style === 'foreground' ? size * 0.225 : size * 0.12;

  // ---- 背景（foreground は透過） ----
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4;
      if (style === 'foreground') {
        buf[i + 3] = 0;
        continue;
      }
      const dx = x - c;
      const dy = y - c;
      const glow = Math.exp(-(dx * dx + dy * dy) / (2 * Math.pow(size * 0.38, 2)));
      buf[i] = 11; // #0b1224 ベース + シアンのグロー
      buf[i + 1] = Math.round(18 + glow * 100);
      buf[i + 2] = Math.round(36 + glow * 127);
      buf[i + 3] = 255;
    }
  }

  // ---- ロゴ本体（角丸グラデーション矩形） ----
  const lo = margin;
  const lw = size - 2 * margin; // ロゴ矩形の幅（48 座標系で 44 に相当）
  const rect = {
    cx: c,
    cy: c,
    hw: lw / 2,
    hh: lw / 2,
    r: (lw * 12) / 44, // favicon.svg の rx=12 / 44
  };

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const cov = clamp01(0.5 - sdRoundRect(x + 0.5, y + 0.5, rect));
      if (cov <= 0) continue;
      const t = clamp01(((x - lo) + (y - lo)) / (2 * lw));
      const col = [
        Math.round(GRAD_A[0] + (GRAD_B[0] - GRAD_A[0]) * t),
        Math.round(GRAD_A[1] + (GRAD_B[1] - GRAD_A[1]) * t),
        Math.round(GRAD_A[2] + (GRAD_B[2] - GRAD_A[2]) * t),
      ];
      blend(buf, size, x, y, col, cov);
    }
  }

  // ---- 白いマーク（スケール + オフセット） ----
  const s = size / 48;
  for (const m of LOGO_MARKS) {
    if (m.type === 'rrect') {
      const mr = {
        cx: lo + (m.x + m.w / 2) * s,
        cy: lo + (m.y + m.h / 2) * s,
        hw: (m.w / 2) * s,
        hh: (m.h / 2) * s,
        r: m.r * s,
      };
      for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
          const cov = clamp01(0.5 - sdRoundRect(x + 0.5, y + 0.5, mr)) * m.alpha;
          if (cov > 0) blend(buf, size, x, y, WHITE, cov);
        }
      }
    } else {
      const cr = m.r * s;
      const cxp = lo + m.cx * s;
      const cyp = lo + m.cy * s;
      for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
          const d = Math.hypot(x + 0.5 - cxp, y + 0.5 - cyp);
          const cov = clamp01(0.5 - (d - cr)) * m.alpha;
          if (cov > 0) blend(buf, size, x, y, WHITE, cov);
        }
      }
    }
  }

  return encodePNG(size, buf);
}

// ------------------------------------------------------------
// 出力定義
// ------------------------------------------------------------
const outputs = [
  // PWA / Web
  { file: 'html/icons/icon-192.png', size: 192, style: 'full' },
  { file: 'html/icons/icon-512.png', size: 512, style: 'full' },
  { file: 'html/icons/icon-maskable-192.png', size: 192, style: 'maskable' },
  { file: 'html/icons/icon-maskable-512.png', size: 512, style: 'maskable' },
  { file: 'html/apple-touch-icon.png', size: 180, style: 'full' },
  // Android レガシーランチャーアイコン
  { file: 'android/app/src/main/res/mipmap-mdpi/ic_launcher.png', size: 48, style: 'full' },
  { file: 'android/app/src/main/res/mipmap-hdpi/ic_launcher.png', size: 72, style: 'full' },
  { file: 'android/app/src/main/res/mipmap-xhdpi/ic_launcher.png', size: 96, style: 'full' },
  { file: 'android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png', size: 144, style: 'full' },
  { file: 'android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png', size: 192, style: 'full' },
  // Android アダプティブアイコンの前景
  { file: 'android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png', size: 432, style: 'foreground' },
];

for (const out of outputs) {
  const abs = join(ROOT, out.file);
  mkdirSync(dirname(abs), { recursive: true });
  writeFileSync(abs, renderIcon(out.size, out.style));
  console.log('generated', out.file, `(${out.size}x${out.size}, ${out.style})`);
}
console.log('done.');
