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

# INFOLD Android アプリ（ネイティブ / Jetpack Compose）

INFOLD を **WebView や TWA を使わない独立したネイティブ Android ニュースアプリ**として実装したものです。

```
Android アプリ（Jetpack Compose のネイティブ UI）
        ↓  HTTP / JSON
INFOLD API（/api/*  Cloudflare Worker）
        ↓
Cloudflare Worker / D1（既存のバックエンドをそのまま使用）
```

- アプリ名: **INFOLD**（ランチャー表示名）
- パッケージ名（application ID）: `jp.infold.news`（バージョン 2.0.0 からネイティブ化）
- **WebView / TWA / Chrome は一切使用しません**。すべての画面（ホーム・記事一覧・記事詳細・カテゴリ・検索・ログイン・アカウント・INFOLD POINT・一時的な広告削除・設定）を Android ネイティブ UI で描画します。
- データは既存の INFOLD API（Cloudflare Worker / D1）から取得します。既存 API を変更せずにそのまま利用できます（認証は Web 版と同じクッキーベース）。
- Web 版の **Liquid Glass デザイン・ダーク/ライトテーマ・日本語/English** をネイティブ UI で再現しています。
- 記事内の**外部リンクのみ**外部ブラウザで開きます。
- 新着記事の **FCM プッシュ通知**に対応しています（通知タップで該当記事のネイティブ詳細画面を開きます）。
- ネットワーク接続できない場合は、ネイティブのエラー画面で「ネットワーク環境が整った場所でこちらのアプリを再度開き直してください。」と表示します。

## Android Studio がなくても APK / AAB を作れる

ローカルの PC に **Android Studio / Android SDK を一切インストールせずに** ビルドできます。

- ビルドは **GitHub Actions のクラウド上**（ubuntu-latest）で実行します。
- `android/` フォルダには Gradle ラッパーが含まれており、GitHub Actions 側で Java 17・Android SDK・Gradle を自動セットアップします。
- ビルド成果物（APK / AAB / ログ）は GitHub 上にのみ保存され、ローカル PC には保存されません。

## GitHub Actions でビルドする方法

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
4. `./gradlew :app:assembleDebug :app:assembleRelease :app:bundleRelease` でビルド
5. 以下を生成（署名鍵が無ければ debug 鍵で署名）
   - **Debug APK**（`app-debug.apk`）
   - **Release APK**（`app-release.apk`）
   - **Release AAB**（`app-release.aab`、Google Play 提出用）
6. 成果物を **Artifact** として保存（30日間保持）し、**GitHub Releases** にも公開

ビルドに失敗した場合は、Actions のログ（`--stacktrace` 出力）と `gradle-build-log` Artifact で原因を確認できます。

## ビルド成果物を取得する方法

### 方法1: GitHub Releases（簡単・推奨）

ビルドが成功すると **GitHub Releases** に自動公開されます（タグ `v2.0.0-<ビルド番号>`）。

1. リポジトリの **Releases** ページ（https://github.com/yumedebug/infold/releases）を開く
2. 最新の Release の **Assets** から `app-release.apk`（または `app-release.aab`）をダウンロード

> 直接ダウンロード: `https://github.com/yumedebug/infold/releases/latest/download/app-release.apk`

### 方法2: Actions の Artifact から取得

1. Actions タブ →「Build INFOLD Android APK」→ 実行したワークフローをクリック
2. ページ下部の **Artifacts** セクションから取得
   - `infold-debug-apk` … Debug APK
   - `infold-release` … Release APK + Release AAB
3. ダウンロードした ZIP を解凍して使用

> 注意: Actions の Artifact は GitHub ログインが必要です。ブラウザで GitHub にログインした状態でダウンロードしてください。

## APK を Android 端末へインストールする方法

1. Android 端末で「提供元不明のアプリ」のインストールを許可する（設定 → セキュリティ / アプリ）
2. ダウンロードした `app-release.apk` を端末に転送（USB / クラウド / ブラウザから直接ダウンロード等）
3. ファイルをタップしてインストール
4. ランチャーに **INFOLD** アイコンが表示されます

> 旧バージョン（v1.x の TWA 版）をインストール済みの場合は、署名が異なるため一度アンインストールしてからインストールしてください。

## Release 署名の設定（Google Play 提出時に必須）

### 現状の動作

- **署名鍵（GitHub Secrets）が未設定**の場合: debug 鍵で署名した APK/AAB を生成します。実機テスト・インストールは可能ですが、**Google Play には提出できません**（また、バージョン更新時の署名継続もできません）。
- **署名鍵を設定**した場合: 正規の Release 署名付き APK / AAB を生成します。

### 署名鍵を準備する（Android Studio 不要）

1. GitHub の Actions タブ → **「Generate release keystore」** → Run workflow を実行
2. 完了後、Artifact `infold-release-keystore` をダウンロード
   - `infold-release.keystore` … 署名鍵（**大切に保管**。失うと更新版を公開できなくなります）
   - `keystore-password.txt` … パスワード（鍵と同一）

### GitHub Secrets に設定する

リポジトリの **Settings → Secrets and variables → Actions → New repository secret** で以下を登録します。

| Secret 名 | 値 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `infold-release.keystore` を base64 エンコードした文字列（例: `base64 -w0 infold-release.keystore` の出力） |
| `ANDROID_KEYSTORE_PASSWORD` | `keystore-password.txt` の内容 |
| `ANDROID_KEY_ALIAS` | `infold` |

> 署名鍵は**絶対にリポジトリへコミットしない**でください（`.gitignore` で除外済み）。

## プッシュ通知（FCM）の設定

新着記事の通知には **Firebase Cloud Messaging（FCM）** を使用します。未設定のままでもアプリ・ビルドは正常に動作します（通知だけが届きません）。

### 1. Firebase プロジェクトを作成

1. [Firebase コンソール](https://console.firebase.google.com/) でプロジェクトを作成（例: `infold-news`）
2. **プロジェクト設定 → 一般 → あなたのアプリ** で Android アプリを追加
   - パッケージ名: `jp.infold.news`
3. **google-services.json** をダウンロードして `android/app/google-services.json` に配置
   - 配置するとビルド時に FCM が自動で有効になります（無い場合は無効のままビルドされます）

### 2. Worker 側の Secret を設定（通知の送信）

1. Firebase コンソール → **プロジェクト設定 → サービスアカウント → 新しい秘密鍵を生成** でサービスアカウントの JSON を取得
2. JSON 内の `project_id`・`client_email`・`private_key` を控える
3. Worker に Secret を設定（ローカルで `wrangler login` 済みの場合）:

```bash
wrangler secret put FCM_PROJECT_ID       # 例: infold-news
wrangler secret put FCM_CLIENT_EMAIL     # 例: xxx@infold-news.iam.gserviceaccount.com
# 秘密鍵（PEM）を base64 エンコードして設定
printf '%s' "$(jq -r .private_key firebase-service-account.json)" | base64 -w0
wrangler secret put FCM_PRIVATE_KEY
```

3. マイグレーションを適用（プッシュ通知用テーブル）:

```bash
wrangler d1 execute news-site-db --remote --file=migrations/0003_push.sql
```

### 通知の仕組み

- Android アプリ起動時・トークン更新時に FCM トークンを `POST /api/push/register` で登録
- Worker の Cron（1時間ごと）が新着記事を検出し、全端末へ FCM 通知を送信（タイトル: INFOLD / 本文: 記事タイトル）
- 管理画面から記事を公開した場合も即時通知（ベストエフォート）
- 通知タップでアプリが起動し、該当記事の**ネイティブ詳細画面**を開きます

## 技術スタック

- Kotlin / **Jetpack Compose**（Material 3）
- Navigation Compose・Lifecycle ViewModel
- OkHttp（クッキー永続化 CookieJar）+ kotlinx.serialization
- Coil（画像読み込み・SVG 対応）
- Firebase Cloud Messaging（google-services.json 未設定でもビルド可）

## 既存 Web サイトへの影響

- 既存の Web サイト（`html/`）・API・D1・ポイント機能・自動投稿などは変更していません。
- 追加したのは `worker/src/push.js`（プッシュ通知）、`migrations/0003_push.sql`（通知用テーブル）、`/api/push/*` エンドポイントのみです。
- `html/.well-known/assetlinks.json` 等の PWA ファイルは Web サイト側の機能としてそのまま残ります（ネイティブアプリは使用しません）。

## Android アプリのビルドをローカルで試す場合（任意）

Android Studio は不要ですが、JDK 17 と Android SDK が手元にある環境なら以下でもビルドできます（通常は GitHub Actions で十分です）。

```bash
cd android
./gradlew :app:assembleDebug     # Debug APK
./gradlew :app:assembleRelease   # Release APK: android/app/build/outputs/apk/release/app-release.apk
./gradlew :app:bundleRelease     # Release AAB: android/app/build/outputs/bundle/release/app-release.aab
```
