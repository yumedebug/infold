# INFOLD News — Cloudflare Workers デプロイ手順

ニュース・メディアサイト。フロントエンド（`html/`）と API・自動投稿を **1つの Cloudflare Worker** にまとめてデプロイする。

## 構成（すべて Workers 1 本に集約）

```
Cloudflare Worker（infold-news）
├── 静的アセット  html/  （index.html 1ファイル = HTML + CSS + JS 全部インライン）
├── API           /api/* （記事・検索・画像配信）
├── 管理API        /api/admin/* （セッション保護）
├── D1            DB     （記事・ユーザー・セッション・自動投稿状態）
├── R2            IMAGES （画像保存）
└── Cron          0 * * * * （ITFIS取り込み 1時間間隔（Freeプラン最大頻度）+ 毎日 08:00 JST 自動投稿）
```

- フロントエンドは **`html/index.html` 1ファイル** に HTML・CSS・JavaScript をすべて含む（外部CSS/JSなし、参照する外部ファイルなし）。
- **`html/` フォルダ内のファイル数は 1**（上限 1000 を大きく下回る。Workers 静的アセット上限は 2万ファイル）。
- 設定ファイル（`wrangler.jsonc`・`package.json`・migrations など）は **html/ に入れない**。
  html/ は公開配信されるため、入れると D1 の database_id などが外部に漏れる。

## 必要なもの

- Node.js 18+
- Cloudflare アカウント
- Google Gemini API Key

## Cloudflare Dashboard で行う作業

1. **D1 データベース**作成 → 名前 `infold-news-db`（作成後に `database_id` を控える）
2. **R2 バケット**作成 → 名前 `news-site-images`
3. （Worker は `wrangler deploy` 時に自動作成される）

## ローカルで行うコマンド

```bash
npm install                       # 依存関係インストール

npm run db:migrate:local          # ローカル D1 に migration 適用
npm run dev                       # ローカル開発サーバー
npm run create-admin              # 管理者ユーザー作成（SQL を出力）

npm run db:migrate:remote         # 本番 D1 に migration 適用
npm run deploy                    # ★ Workers へデプロイ（フロント + API + cron 全部）
```

**`npm run deploy` 1回で完了。** html フォルダを個別にアップロードする必要はない
（`wrangler.jsonc` の `assets.directory: "./html"` により自動で同梱される）。

## 設定（wrangler.jsonc）

```jsonc
{
  "assets": {
    "directory": "./html",          // ★ フロント（index.html）を同梱
    "binding": "ASSETS",
    "not_found_handling": "none"    // 一致しないパスは Worker へ（SPA フォールバック）
  },
  "d1_databases": [{ "binding": "DB", "database_name": "infold-news-db", "database_id": "..." }],
  "r2_buckets":   [{ "binding": "IMAGES", "bucket_name": "news-site-images" }],
  "triggers": { "crons": ["0 * * * *"] },
  "vars": {
    "GEMINI_MODEL": "gemini-flash-latest",
    "ALLOWED_ORIGINS": ""            // 通常は空でOK（同一オリジン運用のため）
  }
}
```

- `database_id` は自分の D1 の ID に置き換える（`wrangler d1 info` で確認）。
- `ALLOWED_ORIGINS`: フロントと API が同じ Workers ドメインなら不要（空のまま）。
  将来カスタムドメインや別オリジンから API を叩く場合のみ設定。

## Secret 設定

```bash
wrangler secret put GEMINI_API_KEY
# プロンプトに Gemini API キーを入力
```

## 動作確認

| 項目 | 確認方法 |
| --- | --- |
| フロント表示 | `https://infold-news.<あなたのサブドメイン>.workers.dev` を開く |
| API 動作 | 同 URL の `/api/articles` が JSON を返す |
| 日本語/English | ヘッダーの切替 |
| ライト/ダーク | ヘッダーの ☀️/🌙 |
| 管理者ログイン | `/admin/login` |
| 自動投稿 | Cron `0 * * * *`（ITFIS取り込みは毎時、日次の自動投稿は 08:00 JST に開始） |

## Cron 確認

```bash
wrangler deploy --dry-run   # 設定の検証
# または Dashboard → Workers → 該当 Worker → Triggers で確認
```

## D1 確認

```bash
npm run db:tables   # テーブル一覧（remote・ニュース用D1）
```

## ポイント機能（INFOLD POINT）

- 読者アカウント（`/account` で登録・ログイン）が記事を読了（30秒経過 + 本文80%スクロール）すると +1 POINT。
  同じ記事は3時間ごとに再獲得可能（`/api/articles/:id/complete`）。
- ポイントページ（`/points`）で「一時的な広告削除」を利用（10pt→6h / 30pt→24h / 70pt→7日間）。
  広告なし中は Ninja AdMax 広告枠と AIChatプロモーションバナーが非表示になります。
- データは**専用D1 `INFOLD_POINTS`**（binding `POINTS`）に保存。既存のニュース用D1のテーブルは変更していません
  （読者アカウントは既存D1に新規テーブル `readers` / `reader_sessions` を追加）。

```bash
# ポイント専用D1のマイグレーション
wrangler d1 execute INFOLD_POINTS --remote --file=migrations/points_0001.sql
# 読者テーブルのマイグレーション（ニュース用D1）
wrangler d1 execute news-site-db --remote --file=migrations/0002_readers.sql
# 既存ITFIS記事のプレースホルダ画像を実画像に差し替え（任意・一括）
node scripts/backfill-itfis-images.mjs
```

## 注意

- `GEMINI_API_KEY` をフロントエンドやソースコードに書かない。
- `html/` にはフロントエンドのファイル（現状 index.html のみ）だけを置く。
  設定・SQL・鍵ファイルは絶対に入れない（公開されてしまう）。

---

# INFOLD Android アプリ（PWA + Trusted Web Activity）

INFOLD の Web サイト（https://infold.f5.si/）を **PWA + Trusted Web Activity（TWA）** 方式で Android アプリ化したものです。

```
INFOLD Web (https://infold.f5.si/)
        ↓  PWA（manifest.json / Service Worker / アイコン）
        ↓  Android TWA（android/ プロジェクト）
        ↓  GitHub Actions でクラウドビルド
        ↓  INFOLD APK
```

- アプリ名: **INFOLD**（ランチャー表示名）
- パッケージ名（application ID）: `jp.infold.news`
- アプリ起動で `https://infold.f5.si/` を表示
- Android の戻るボタンで Web ページの履歴を自然に戻れる（履歴が無ければアプリ終了）
- 外部サイトへのリンクは外部ブラウザで開く
- Web サイトの UI・機能（記事・ログイン・ポイント・広告・Liquid Glass・ダーク/ライト・日本語/English 等）は一切変更していません

## Android Studio がなくても APK を作れる

このプロジェクトの Android アプリは、**ローカルの PC に Android Studio / Android SDK を一切インストールせずに** APK を作成できます。

- ビルドは **GitHub Actions のクラウド上**（ubuntu-latest）で実行します。
- `android/` フォルダには Gradle ラッパーが含まれており、GitHub Actions 側で Java 17・Android SDK・Gradle を自動セットアップします。
- ローカル PC には APK や Android SDK のファイルは保存されません（ビルド成果物は GitHub 上の Artifact にのみ保存）。

## GitHub Actions で APK をビルドする方法

`.github/workflows/build-apk.yml` が自動ビルドを行います。

### 自動実行（push）

`main` ブランチへ `android/` またはワークフローを push すると自動的にビルドが開始されます。

### 手動実行

1. GitHub のリポジトリページで **Actions** タブを開く
2. 左側の **「Build INFOLD Android APK」** をクリック
3. **Run workflow** ボタンを押す

### ビルドの中身

1. Java 17（Temurin）をセットアップ
2. Android SDK をセットアップ
3. Gradle をセットアップ（キャッシュ付き）
4. `./gradlew :app:assembleRelease` でビルド
5. Release APK を生成（署名鍵が無ければ debug 鍵で署名）
6. APK を **Artifact** として保存（30日間保持）

ビルドに失敗した場合は、Actions のログ（`--stacktrace` 出力）で原因を確認できます。

## GitHub Actions から APK を取得する方法

1. Actions タブ →「Build INFOLD Android APK」→ 実行したワークフローをクリック
2. ページ下部の **Artifacts** セクションに `infold-apk` があるのでクリックしてダウンロード
3. ダウンロードした ZIP を解凍すると `app-release.apk` が入っています

## APK を Android 端末へインストールする方法

1. Android 端末で「提供元不明のアプリ」のインストールを許可する（設定 → セキュリティ / アプリ）
2. ダウンロードした `app-release.apk` を端末に転送（USB / クラウド / ブラウザから直接ダウンロード等）
3. ファイルをタップしてインストール

> 注意: GitHub Actions の Artifact はログインが必要です。ブラウザで GitHub にログインした状態でダウンロードしてください。

## Release APK の署名設定（必要に応じて）

### 現状の動作

- **署名鍵（GitHub Secrets）が未設定**の場合: debug 鍵で署名した APK を生成します。実機テスト・インストールは可能ですが、**TWA 検証が通らないためアプリ内 WebView モード**で表示されます（見た目・操作感はほぼ同じ）。
- **署名鍵を設定**した場合: 正規の Release 署名付き APK を生成し、サイトとの **TWA 検証が成立**してフルスクリーンの Trusted Web Activity として動作します。

### 署名鍵を準備する（Android Studio 不要）

1. GitHub の Actions タブ → **「Generate release keystore」** → Run workflow を実行
2. 完了後、Artifact `infold-release-keystore` をダウンロード
   - `infold-release.keystore` … 署名鍵
   - `keystore-password.txt` … パスワード（鍵と同一）
3. ワークフローのログに表示される **SHA256 フィンガープリント** を控える

### GitHub Secrets に設定する

リポジトリの **Settings → Secrets and variables → Actions → New repository secret** で以下を登録します。

| Secret 名 | 値 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `infold-release.keystore` を base64 エンコードした文字列（例: `base64 -w0 infold-release.keystore` の出力） |
| `ANDROID_KEYSTORE_PASSWORD` | `keystore-password.txt` の内容 |
| `ANDROID_KEY_ALIAS` | `infold` |

> 署名鍵は**絶対にリポジトリへコミットしない**でください（`.gitignore` で除外済み）。

### TWA 検証（assetlinks.json）を有効化する

TWA としてフルスクリーン動作させるには、サイト側に Digital Asset Links を設置する必要があります。

1. `html/.well-known/assetlinks.json` の `sha256_cert_fingerprints` に、先ほど控えた **SHA256 フィンガープリント**（`AA:BB:CC:...` 形式）を記載する（現在はプレースホルダー）
2. `npm run deploy` などでサイトへデプロイする
3. アプリを再インストール（署名鍵を変えた場合は一度アンインストールが必要）
4. 検証が成功すると、アプリがフルスクリーンの TWA として起動します

> フィンガープリントが未設定の間も、アプリ内 WebView フォールバックにより問題なく利用できます。

## PWA の構成

`html/` に以下を追加しています（Web サイトのデザイン・機能は変更していません）。

- `manifest.json` … PWA マニフェスト（アプリ名 INFOLD・standalone・テーマカラー・アイコン）
- `sw.js` … Service Worker（オンライン優先。オフライン時はアプリシェルとエラー表示）
- `icons/` … アプリアイコン（192 / 512 / maskable）
- `apple-touch-icon.png` … iOS 用アイコン
- `.well-known/assetlinks.json` … TWA 検証用（Android アプリの署名フィンガープリントを記載）

アイコンは `node scripts/generate-app-icons.mjs` で再生成できます（依存パッケージなし・Node.js のみ）。

## Android アプリのビルドをローカルで試す場合（任意）

Android Studio は不要ですが、JDK 17 と Android SDK が手元にある環境なら以下でもビルドできます（通常は GitHub Actions で十分です）。

```bash
cd android
./gradlew :app:assembleRelease   # 出力: android/app/build/outputs/apk/release/app-release.apk
```
