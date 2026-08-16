#!/usr/bin/env node
// Debug script: reproduce worker's generateWithGemini flow and inspect raw response
import fs from 'node:fs';

const key = fs.readFileSync('.dev.vars', 'utf8').match(/GEMINI_API_KEY=(.+)/)?.[1]?.trim();

function stripTags(s) {
  return String(s || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}
function extractTag(body, tag) {
  const m = body.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i'));
  return m ? m[1] : '';
}

async function fetchGoogleNews() {
  const resp = await fetch('https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja', {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
      Accept: 'application/rss+xml, application/xml, text/xml, */*',
    },
  });
  const xml = await resp.text();
  const items = [];
  const itemRe = /<item>([\s\S]*?)<\/item>/g;
  let m;
  while ((m = itemRe.exec(xml)) !== null) {
    const body = m[1];
    const title = stripTags(extractTag(body, 'title'));
    const link = extractTag(body, 'link').trim();
    const pubDate = extractTag(body, 'pubDate').trim();
    const desc = extractTag(body, 'description');
    const sourceMatch = desc.match(/<font[^>]*color="#6f6f6f"[^>]*>([\s\S]*?)<\/font>/i);
    const source = stripTags(sourceMatch ? sourceMatch[1] : '') || String(title).split(' - ').pop() || '';
    if (!title || !link || !/^https?:/.test(link)) continue;
    items.push({ title, link, pubDate, description: stripTags(desc).slice(0, 500), source });
  }
  return items.slice(0, 15);
}

function buildPrompt(item) {
  return `あなたはニュース記事ライターです。以下に示すGoogle Newsのニュース情報のみを事実の出典として、日本語のニュース記事を1本作成してください。

【厳守ルール】
- ニュース情報に含まれる事実だけを使う。新しい情報・数字・固有名詞を勝手に追加・推測しない
- 元ニュースの文章を大量にコピーしない。情報を整理・要約し、独自の文章として書き直す
- タイトルは30字以内の簡潔な見出し
- 概要（description）は80〜150字の要約
- 本文（content）は400〜700字程度、3〜5段落。段落は空行で区切り、必要なら段落冒頭に「## 」を付けて小見出しにする
- カテゴリは次のいずれかから1つだけ選ぶ: IT / AI / Windows / Android / Apple / Web / Programming / その他
- 出力はJSONオブジェクトのみ（マークダウンのコードブロックや注釈を付けない）

【ニュース情報】
タイトル: ${item.title}
ソース: ${item.source}
公開日時: ${item.pubDate}
概要: ${item.description}
URL: ${item.link}

【出力JSON形式】
{"title":"...","description":"...","content":"...","category":"..."}`;
}

const items = await fetchGoogleNews();
console.log('=== RSS items fetched:', items.length, '===');
const item = items[0];
console.log('=== candidate:', item.title, '|', item.source, '===');

const resp = await fetch(
  'https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent',
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
    body: JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: buildPrompt(item) }] }],
      generationConfig: {
        temperature: 0.7,
        maxOutputTokens: 8192,
        responseMimeType: 'application/json',
      },
    }),
  }
);
const raw = await resp.text();
console.log('=== HTTP', resp.status, '===');
const data = JSON.parse(raw);
if (!resp.ok) {
  console.log('ERROR RESPONSE:', raw.slice(0, 800));
  process.exit(1);
}
const cand = data?.candidates?.[0] || {};
console.log('=== finishReason:', cand.finishReason, '===');
console.log('=== usageMetadata:', JSON.stringify(data?.usageMetadata || {}), '===');
const parts = cand?.content?.parts || [];
console.log('=== parts count:', parts.length, '===');
parts.forEach((p, i) => {
  console.log(`part[${i}] keys:`, Object.keys(p), 'text length:', (p.text || '').length);
});
const text = parts.map((p) => p.text || '').join('').trim();
console.log('=== joined text (first 300 chars): ===');
console.log(JSON.stringify(text.slice(0, 300)));
console.log('=== joined text (last 200 chars): ===');
console.log(JSON.stringify(text.slice(-200)));
try {
  const cleaned = text.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '');
  const parsed = JSON.parse(cleaned);
  console.log('=== PARSE OK === title:', parsed.title, '| category:', parsed.category);
} catch (e) {
  console.log('=== PARSE FAILED:', e.message, '===');
}
