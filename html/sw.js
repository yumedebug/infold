/* ============================================================
   INFOLD Service Worker
   ------------------------------------------------------------
   ニュースサイトは常に最新が命。方針は「オンライン優先」:
   - ページ遷移（ナビゲーション）: ネットワーク優先。
     失敗時はキャッシュ済みのアプリシェル（index.html）を返す。
   - 静的アセット（アイコン・manifest 等）: キャッシュ優先 +
     バックグラウンドで更新（stale-while-revalidate）。
   - /api/* は一切キャッシュせず、ネットワークのみに任せる。
     オフライン時はアプリ側の「接続エラー」表示がそのまま機能する。
   ------------------------------------------------------------
   ※ 公開ファイル（index.html やアイコン等）を差し替えたら、
   この CACHE_NAME のバージョン番号を上げて古いキャッシュを無効化する。
   ============================================================ */

const CACHE_NAME = 'infold-shell-v1';
const SHELL_URLS = ['/', '/manifest.json', '/icons/icon-192.png', '/icons/icon-512.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_URLS))
      // シェルの一部が取得できなくてもインストールを止めない
      .catch(() => {})
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  // API は常にネットワークへ（キャッシュしない）。
  // 失敗した場合はブラウザのネットワークエラーとしてアプリ側の
  // オフラインバナー / エラー表示に任せる。
  if (url.pathname.startsWith('/api/')) return;

  // ページ遷移: ネットワーク優先、失敗時はキャッシュしたシェルへ
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches
              .open(CACHE_NAME)
              .then((cache) => cache.put(req, copy))
              .catch(() => {});
          }
          return res;
        })
        .catch(() =>
          caches.match(req).then((cached) => cached || caches.match('/'))
        )
    );
    return;
  }

  // 静的アセット: キャッシュ優先 + バックグラウンド更新
  event.respondWith(
    caches.match(req).then((cached) => {
      const network = fetch(req)
        .then((res) => {
          if (res && res.ok) {
            const copy = res.clone();
            caches
              .open(CACHE_NAME)
              .then((cache) => cache.put(req, copy))
              .catch(() => {});
          }
          return res;
        })
        .catch(() => cached);
      return cached || network;
    })
  );
});
