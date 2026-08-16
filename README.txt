完全新規でニュース・情報メディアWebサイトを開発してください。

既存プロジェクトの改修ではなく、ゼロから作成してください。

ITFISのような「一般ユーザーが記事を読むサイト」と「管理者が記事を投稿・管理するCMS」を分離した構成を参考にしてください。

ただし、ITFISのデザイン・ロゴ・文章・画像・UIをコピーせず、完全オリジナルのデザインにしてください。

---

# 【最重要仕様】

## フロントエンド

HTML + CSS + JavaScriptで実装する。

一般ユーザー側のフロントエンドは、

`index.html`

1ファイルにまとめる。

HTML・CSS・JavaScriptをすべてindex.html内に記述する。

CSS：

```html
<style>
...
</style>
```

JavaScript：

```html
<script>
...
</script>
```

としてindex.htmlへ含める。

外部CSSファイルは作成しない。

外部JavaScriptファイルも作成しない。

ただし、Cloudflare Workersなどのバックエンドコードは別ファイルで構わない。

---

# 【言語】

日本語 / Englishの2言語に完全対応する。

ヘッダーに言語切替を配置する。

```text
日本語 | English
```

JavaScriptの翻訳辞書でUIを管理する。

言語設定はlocalStorageに保存する。

翻訳対象：

* ナビゲーション
* ボタン
* 記事一覧
* 検索
* カテゴリ
* ログイン
* 管理画面
* 設定
* エラー
* ローディング
* ダイアログ
* フッター

記事本文そのものは自動翻訳しない。

---

# 【テーマ】

ライトテーマ / ダークテーマを実装する。

ヘッダーにテーマ切替ボタンを配置する。

```text
☀️ / 🌙
```

ユーザーが選択したテーマをlocalStorageへ保存する。

初回アクセスでは、

`prefers-color-scheme`

も考慮する。

すべてのUIをダークテーマ対応にする。

---

# 【デザイン】

完全オリジナルのニュース・メディアサイトUIを作る。

デザイン：

* モダン
* スタイリッシュ
* シンプル
* 高い可読性
* 適切な余白
* カードUI
* 滑らかなhover
* 控えめなアニメーション
* PC対応
* タブレット対応
* スマートフォン対応

ITFISの構造を参考にしても、デザインそのものはコピーしない。

オリジナルロゴも作成する。

---

# 【Cloudflare構成】

Firebaseは使用しない。

Cloudflareを中心に構築する。

使用するサービス：

### Cloudflare Workers

API・バックエンド処理。

### Cloudflare D1

データベース。

### Cloudflare R2

画像・ファイル保存。

### Cloudflare Workers Cron Triggers

自動投稿。

### Cloudflare Pages / Workers

フロントエンドホスティング。

### Cloudflare DNS

ドメイン管理。

---

# 【データベース】

Cloudflare D1を使用する。

テーブル：

```text
users
articles
categories
sessions
automation_state
```

必要なら追加テーブルを作成してよい。

---

# 【記事データ】

articlesには最低限、

```text
id
title
description
content
thumbnail
category
status
featured
sourceUrl
sourceName
publishedAt
createdAt
updatedAt
isAutomated
```

を保存する。

status：

```text
draft
published
```

isAutomated：

```text
true
false
```

---

# 【一般ユーザー画面】

以下を実装する。

```text
/
```

トップページ

```text
/articles
```

記事一覧

```text
/article/:id
```

記事詳細

```text
/category/:category
```

カテゴリ

```text
/search
```

検索

---

# 【トップページ】

以下を表示：

* ヘッダー
* オリジナルロゴ
* ナビゲーション
* 検索
* 日本語 / English切替
* ライト / ダーク切替
* ヒーロー記事
* おすすめ記事
* 最新記事
* カテゴリ一覧
* フッター

記事カード：

* サムネイル
* カテゴリ
* タイトル
* 概要
* 投稿日

---

# 【記事ページ】

記事詳細には、

* タイトル
* サムネイル
* カテゴリ
* 投稿日
* 本文
* 情報源
* 関連記事

を表示する。

記事本文はサニタイズして表示する。

XSSを防止する。

---

# 【検索】

タイトル・概要・本文・カテゴリを対象に検索する。

検索結果をカード表示する。

0件の場合：

日本語：

```text
記事が見つかりませんでした
```

English：

```text
No articles found
```

---

# 【カテゴリ】

最初から以下のカテゴリを用意する。

```text
IT
AI
Windows
Android
Apple
Web
Programming
その他
```

管理者が追加・編集・削除できるようにする。

---

# 【管理者CMS】

管理者専用画面：

```text
/admin
```

以下を実装する。

* ログイン
* ダッシュボード
* 記事一覧
* 新規記事作成
* 記事編集
* 記事削除
* 下書き
* 公開
* カテゴリ管理
* 自動投稿設定
* ログアウト

一般ユーザー画面とは完全に分離する。

---

# 【管理者ログイン】

```text
/admin/login
```

入力：

* メールアドレス
* パスワード

Cloudflare Workers + D1で認証する。

Firebase Authenticationは使用しない。

パスワードを平文保存しない。

安全なパスワードハッシュを使用する。

ログイン成功後はセッションを発行する。

Cookie：

* HttpOnly
* Secure
* SameSite

を適切に設定する。

未認証ユーザーが管理画面へアクセスした場合はログイン画面へ移動する。

ログアウトも実装する。

---

# 【管理者ダッシュボード】

以下を表示：

* 総記事数
* 公開記事数
* 下書き数
* おすすめ記事数
* 今日の自動投稿状態
* 最新記事
* 新規記事作成

---

# 【記事作成】

```text
/admin/articles/new
```

入力：

* タイトル
* 概要
* 本文
* サムネイル
* カテゴリ
* 公開日時
* 公開 / 下書き
* おすすめ記事

下書き保存と公開に対応する。

---

# 【記事編集】

```text
/admin/articles/:id/edit
```

既存記事を編集できる。

---

# 【記事削除】

削除前に確認ダイアログを表示する。

管理者だけが削除可能。

---

# 【R2】

画像はCloudflare R2へ保存する。

PCのローカルディスクには保存しない。

記事サムネイルなどをR2から読み込む。

---

# 【AI】

AI記事生成には、

**Gemini API**

を使用する。

使用モデルは、

**gemini-3.1-flash**

に固定する。

APIキーは、

```text
GEMINI_API_KEY
```

などのCloudflare Workers Secrets / Environment Variablesに保存する。

APIキーをフロントエンドに絶対に含めない。

ソースコードに直接APIキーを書かない。

---

# 【ニュース取得】

ニュース取得には、**IT・ガジェット系**のRSSを使用する。

* **Google News RSS（テクノロジートピック `topic=tc`）**
* **GIZMODO**（ガジェット）
* **GIGAZINE**（テクノロジー）

取得候補はカテゴリ判定（detectCategory）で**IT・ガジェット関連のみ**に絞り込む。

Google News RSSから取得する情報：

Google News RSSから取得する情報：

* タイトル
* URL
* 公開日時
* ソース名
* 概要

など。

取得したニュースから記事候補を作成する。

---

# 【自動記事生成】

毎日1記事を自動生成する。

処理：

```text
Google News RSS
↓
最新ニュース取得
↓
重複チェック
↓
ニュース候補選択
（IT・ガジェット関連のみに絞り込み）
↓
Gemini API
gemini-3.1-flash
↓
記事生成
↓
おすすめ（★）設定
↓
カテゴリ判定
↓
サムネイル処理
↓
R2
↓
D1
↓
公開
```

---

# 【AI記事生成ルール】

Geminiには取得したニュース情報を渡す。

ニュースに存在しない情報を勝手に作らない。

事実関係を変更しない。

元ニュース本文を大量コピーしない。

情報を整理・要約し、独自の文章として記事化する。

記事データに必ず、

```text
sourceUrl
sourceName
```

を保存する。

---

# 【重複防止】

以下を比較する。

* sourceUrl
* 元ニュースタイトル
* 既存記事タイトル

sourceUrlが同じ場合は重複。

タイトルが非常に似ている場合も重複候補として扱う。

重複していた場合は別のニュースを選択する。

---

# 【自動投稿時間】

毎日、

**08:00（Asia/Tokyo）**

に自動投稿する。

Cloudflare Workers Cron Triggersを使用する。

PCは必要ない。

PCが、

* 電源OFF
* スリープ
* ブラウザを閉じている
* ネットワークから切断

されていても自動投稿が実行される構成にする。

---

# 【自動投稿リトライ】

08:00に失敗した場合、

```text
08:00 → 失敗
08:30 → 再試行
09:00 → 再試行
09:30 → 再試行
...
```

と30分ごとに再試行する。

成功した時点で、その日の自動投稿を終了する。

同じ日に2記事以上自動投稿しない。

---

# 【二重実行防止】

Cronが複数回実行された場合でも、同じ日に複数の記事を投稿しない。

D1の状態管理やトランザクション等を利用する。

automation_state：

```text
date
status
lastAttemptAt
nextRetryAt
attemptCount
```

などを保存する。

---

# 【ログ】

非常に重要。

PCのローカルストレージにログを保存してはいけない。

以下を作らない。

```text
.log
debug.log
error.log
cron.log
execution.log
cache files
temporary JSON
RSS保存ファイル
一時記事ファイル
```

自動投稿処理は完全にCloudflare上で実行する。

大量の永続ログをD1へ保存しない。

必要最低限の自動投稿状態のみD1へ保存する。

---

# 【自動投稿設定】

```text
/admin/settings
```

に自動投稿設定を作る。

表示：

```text
自動投稿：ON / OFF

投稿時刻：08:00

タイムゾーン：Asia/Tokyo

1日の投稿数：1

今日の状態：
未実行 / 実行中 / 成功 / 再試行中

最終試行：

次回試行：

試行回数：
```

「今すぐ実行」ボタンも実装する。

---

# 【SEO】

記事ページに、

* title
* description
* OGP
* canonical
* h1
* h2

を設定する。

記事タイトルをページタイトルに反映する。

---

# 【レスポンシブ】

PC：

複数カラム。

タブレット：

適切な中間レイアウト。

スマートフォン：

1列中心。

以下を守る。

* 横スクロールを極力発生させない
* タップしやすいボタン
* スマホ用ナビゲーション
* 読みやすい記事本文
* 画像を画面幅に合わせる
* 管理画面もスマホ対応

---

# 【ローディング】

データ取得中はSkeleton UIなどを表示。

データなし：

JA：
「記事がありません」

EN：
「No articles available」

エラー：

JA：
「読み込みに失敗しました」

EN：
「Failed to load」

---

# 【セキュリティ】

必ず以下を実装する。

* XSS対策
* SQL Injection対策
* CSRF対策
* セッション管理
* 管理者権限チェック
* パスワードハッシュ
* APIキー保護
* R2アップロード制限
* 記事本文サニタイズ

---

# 【SPA】

可能であればindex.htmlをSPAとして実装する。

JavaScriptで以下のルートを切り替える。

```text
/
 /articles
 /article/:id
 /category/:category
 /search
 /admin/login
 /admin
 /admin/articles
 /admin/articles/new
 /admin/articles/:id/edit
 /admin/categories
 /admin/settings
```

フロントエンドのHTML・CSS・JSはindex.htmlにまとめる。

---

# 【最終構成】

```text
                    Cloudflare
                         │
              ┌──────────┴──────────┐
              │                     │
           Workers                  R2
              │                  画像保存
              │
              D1
              │
        ┌─────┴─────┐
        │           │
     ユーザー      管理者
        │           │
      記事閲覧    CMS
                    │
                  Cron
                    │
                  08:00
                    │
             Google News RSS
                    │
                    ↓
              Gemini API
          gemini-3.1-flash
                    │
                    ↓
                 D1 / R2
                    │
                    ↓
                  公開
```

---

# 【完成条件】

以下をすべて実際に動作させる。

1. 完全新規サイト
2. オリジナルUI
3. HTML + CSS + JavaScript
4. フロントエンドをindex.html 1ファイルに統合
5. 日本語対応
6. English対応
7. ライトテーマ
8. ダークテーマ
9. レスポンシブ
10. トップページ
11. 記事一覧
12. 記事詳細
13. カテゴリ
14. 検索
15. 管理者ログイン
16. 管理者ログアウト
17. 管理者認証
18. 記事作成
19. 下書き
20. 公開
21. 編集
22. 削除
23. カテゴリ管理
24. R2画像保存
25. D1データ保存
26. Google News RSS取得
27. Gemini API使用
28. gemini-3.1-flash使用
29. 毎日08:00自動投稿
30. PC OFFでも自動投稿
31. 失敗時30分ごとに再試行
32. 成功時その日の処理終了
33. 二重投稿防止
34. PCへログファイルを保存しない
35. Firebaseを一切使用しない
36. Cloudflare中心で動作する

---

# 【実装上の重要事項】

完全新規プロジェクトとして作成する。

既存コードを前提にしない。

まずプロジェクト構成を設計し、その後に実装する。

Cloudflare Workers、D1、R2、Cron Triggersが実際にデプロイ可能な構成にする。

フロントエンドはindex.html 1ファイルにまとめる。

バックエンドに必要なWorkerコード、D1 migration、wrangler設定などは別ファイルで構わない。

最終的に、

「管理者が記事を投稿 → D1へ保存 → 一般ユーザーが閲覧」

および、

「毎日08:00 → Google News RSS → Gemini API（gemini-3.1-flash）→ 記事生成 → D1/R2 → 自動公開」

が実際に動作する完成したWebアプリケーションにしてください。

見た目だけのモックアップではなく、Cloudflareへデプロイして実運用できる状態まで実装してください。



ここもお願いします。

D1用のschema.sqlを作成
↓
users
articles
categories
sessions
automation_state
を作成
↓
WranglerでD1へ適用
↓
WorkerにDB binding
↓
APIからD1を操作



# 【Cloudflare実装・セットアップ追加要件】

このプロジェクトは完全にCloudflare中心で動作させる。

実装だけでなく、Cloudflareへ実際にデプロイできる状態まで構成する。

## D1

Cloudflare D1を使用する。

以下のテーブルをmigrationで作成する。

* users
* articles
* categories
* sessions
* automation_state

`migrations/0001_initial.sql`などのmigrationファイルを作成する。

D1 binding名は、

`DB`

に統一する。

Workerからは、

`env.DB`

でD1へアクセスできるようにする。

D1を単なるローカルSQLiteとして扱わない。

Cloudflare上のRemote D1へmigrationを適用できる構成にする。

---

## R2

Cloudflare R2を使用する。

バケット名は例として、

`news-site-images`

を使用する。

Worker binding名は、

`IMAGES`

に統一する。

Workerから、

`env.IMAGES`

でR2へアクセスできるようにする。

記事サムネイルなどをR2へ保存する。

PCのローカルディスクへ画像を保存しない。

---

## Wrangler

Cloudflare Workersの設定ファイルを作成する。

`wrangler.jsonc`

または適切なWrangler設定ファイルを使用する。

以下を適切に設定する。

* Worker name
* main
* compatibility_date
* D1 binding
* R2 binding
* Cron Triggers

database_idなど環境固有の値を適当な値でハードコードしない。

---

## Gemini API

AI記事生成にはGoogle Gemini APIを使用する。

使用モデル：

`gemini-3.1-flash`

APIキーは、

`GEMINI_API_KEY`

というCloudflare Secret / Environment Variableから取得する。

APIキーを、

* index.html
* JavaScript
* GitHubへ公開されるコード
* README
* SQL
* クライアント側コード

へ絶対に記述しない。

ブラウザからGemini APIを直接呼び出さない。

必ずCloudflare Workerを経由する。

---

## Google News

ニュース取得にはGoogle News RSSを使用する。

WorkerからGoogle News RSSを取得する。

PC上のPythonやNode.jsでニュースを取得する構成にはしない。

ニュース取得結果をPCへ保存しない。

Google News RSSから、

* title
* link
* publication date
* source
* description

などを取得する。

既存記事と比較して重複を防止する。

---

## Cron

Cloudflare Workers Cron Triggersを使用する。

PCの、

* Windowsタスクスケジューラ
* Python
* Node.js
* ブラウザ
* PowerShell
* setInterval
* setTimeout

などを自動投稿の実行基盤として使用しない。

PCが完全に電源OFFでも自動投稿が実行される必要がある。

毎日、日本時間08:00に自動投稿処理を開始する。

タイムゾーンの扱いを正しく実装する。

---

## 30分リトライ

08:00の処理が失敗した場合、30分後に再試行する。

例：

08:00
↓失敗
08:30
↓失敗
09:00
↓失敗
09:30
↓成功

成功したらその日の処理を終了する。

同じ日に複数の記事を自動公開しない。

---

## 二重実行防止

Cronが重複して実行された場合でも二重投稿しない。

D1の`automation_state`を利用する。

必要に応じてD1のトランザクションや一意制約を使用する。

例えば日付を一意に扱い、

`2026-08-12`

について自動投稿成功済みなら、その日は再度記事を作成しない。

---

## 管理者認証

Firebase Authenticationは使用しない。

Cloudflare Workers + D1で管理者認証を実装する。

パスワードは安全なハッシュ方式で保存する。

平文パスワードをD1へ保存しない。

セッションをD1で管理する。

Cookieには適切に、

* HttpOnly
* Secure
* SameSite

を設定する。

管理者APIは必ず認証状態を確認する。

---

## API

Workerに以下のAPIを実装する。

### 一般ユーザー

`GET /api/articles`

公開記事一覧。

`GET /api/articles/:id`

記事詳細。

`GET /api/categories`

カテゴリ一覧。

`GET /api/search?q=`

記事検索。

---

### 管理者

`POST /api/admin/login`

管理者ログイン。

`POST /api/admin/logout`

ログアウト。

`GET /api/admin/me`

ログイン状態確認。

`GET /api/admin/articles`

管理者用記事一覧。

`POST /api/admin/articles`

記事作成。

`PUT /api/admin/articles/:id`

記事編集。

`DELETE /api/admin/articles/:id`

記事削除。

`POST /api/admin/upload`

R2への画像アップロード。

---

## CORS / APIセキュリティ

不要なCORSを許可しない。

管理者APIは認証済みセッションのみアクセス可能にする。

APIキーやD1/R2の認証情報をクライアントへ返さない。

---

## ログ・容量

PCのストレージ容量が非常に限られているため、ローカルへログを残さない。

以下を生成しない。

* `.log`
* `debug.log`
* `error.log`
* `cron.log`
* `execution.log`
* ニュースキャッシュ
* RSS保存ファイル
* 一時記事JSON
* 一時画像
* 自動投稿履歴ファイル

自動投稿はCloudflare上だけで完結させる。

D1にも大量のログを永久保存しない。

---

## index.html

一般ユーザー側のUIは、

`index.html`

にまとめる。

このファイルの中に、

* HTML
* CSS
* JavaScript

を含める。

外部CSSファイルを作らない。

外部JSファイルを作らない。

日本語 / English切替を実装する。

ライト / ダークテーマを実装する。

レスポンシブデザインにする。

---

## 管理画面

管理画面も日本語 / Englishに対応する。

管理者が、

* 記事作成
* 記事編集
* 記事削除
* 下書き
* 公開
* カテゴリ管理
* 自動投稿ON/OFF
* 自動投稿の手動実行

を行えるようにする。

---

## README

プロジェクトにREADME.mdを作成する。

READMEには以下を必ず記載する。

### 必要なもの

* Node.js
* Cloudflareアカウント
* Gemini API Key

### Cloudflare Dashboardで行う作業

* D1作成
* R2 Bucket作成
* Worker作成
* D1 Binding
* R2 Binding
* Gemini Secret設定

### ローカルで行うコマンド

依存関係インストール。

開発サーバー起動。

D1 migration。

Cloudflareへのデプロイ。

### Secret設定

`GEMINI_API_KEY`

をCloudflare Secretへ登録する方法。

### デプロイ

Wranglerを使ってCloudflareへデプロイする方法。

### Cron確認

毎日08:00のCronが設定されていることを確認する方法。

### D1確認

D1のテーブルを確認する方法。

---

## デプロイ可能性

最終成果物は、

「コードを書いただけ」

で終わらせない。

Cloudflareへデプロイ可能な構成にする。

Wrangler設定に矛盾がないことを確認する。

D1 binding名：

`DB`

R2 binding名：

`IMAGES`

Gemini Secret：

`GEMINI_API_KEY`

で統一する。

存在しない環境変数やbindingをコードから参照しない。

---

## 最終テスト

実装後、最低限以下を確認する。

1. index.htmlが表示される
2. 日本語に切り替えられる
3. Englishに切り替えられる
4. ダークテーマにできる
5. ライトテーマに戻せる
6. D1から記事を取得できる
7. 管理者ログインできる
8. 管理者以外が管理APIを利用できない
9. 記事を作成できる
10. 記事を編集できる
11. 記事を削除できる
12. R2へ画像を保存できる
13. Google News RSSを取得できる
14. Gemini APIをWorkerから呼び出せる
15. `gemini-3.1-flash`を使用している
16. APIキーがクライアントへ漏れない
17. Cronが設定されている
18. 08:00の自動投稿が実行される
19. 失敗時30分後に再試行される
20. 成功時に二重投稿されない
21. PCがOFFでも自動投稿できる
22. PCにログファイルが生成されない

エラーが発生した場合は、原因を特定して修正し、最終的にデプロイ可能な状態にしてください。


ここまでお願いします。