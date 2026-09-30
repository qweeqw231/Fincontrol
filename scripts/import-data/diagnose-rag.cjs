/**
 * 诊断脚本：复现 KnowledgeBaseService.retrieve() 的关键词检索与 formatContext() 截断，
 * 用于定位「实盘数字未被 AI 命中」的原因（排序挤出 vs 400 字符截断）。
 *
 * 用法：node diagnose-rag.cjs "查询文本"
 */
const fs = require('fs');
const path = require('path');

const KB = path.join(
  __dirname,
  '../../fincontrol-backend/src/main/resources/knowledge/knowledge-base.json'
);
const SNIPPET_MAX_CHARS = 800;
const TOKEN_SPLIT = /[\s,，。、；;：:！!？?"'“”（）()\[\]【】<>《》—…\-]+/;

function tokenize(query) {
  const tokens = new Set();
  for (const part of query.trim().split(TOKEN_SPLIT)) {
    if (!part || !part.trim()) continue;
    const p = part.trim();
    tokens.add(p);
    if (p.length > 3 && /[\u4e00-\u9fa5]/.test(p)) {
      for (let i = 0; i < p.length - 1; i++) tokens.add(p.slice(i, i + 2));
    }
  }
  return tokens;
}

function retrieve(docs, query, topN) {
  const keywords = tokenize(query);
  const scored = [];
  for (const doc of docs) {
    const haystack = (doc.title + ' ' + doc.content).toLowerCase();
    let score = 0;
    const hits = [];
    for (const kw of keywords) {
      if (kw.length < 2) continue;
      if (haystack.includes(kw.toLowerCase())) {
        if (doc.title.toLowerCase().includes(kw.toLowerCase())) {
          score += 3;
          hits.push(`${kw}(T)`);
        } else {
          score += 1;
          hits.push(kw);
        }
      }
    }
    if (score > 0) scored.push({ doc, score, hits });
  }
  scored.sort((a, b) => b.score - a.score);
  return { top: scored.slice(0, topN), keywords: [...keywords] };
}

const query = process.argv[2] || '2026年8月末那次ZOH校正，货币类和固收类分别补仓多少？IC-DRR是多少？';
const docs = JSON.parse(fs.readFileSync(KB, 'utf8'));

console.log('查询:', query);
const { top, keywords } = retrieve(docs, query, 3);
console.log('\n关键词数:', keywords.length);
console.log('关键词:', keywords.join(' | '));

console.log('\n=== Top-3（实际注入 prompt 的条目）===');
top.forEach((t, i) => {
  console.log(`\n[${i + 1}] score=${t.score}  §${t.doc.section} ${t.doc.title}`);
  console.log('  命中:', t.hits.join(', '));
  const snippet = t.doc.content.slice(0, SNIPPET_MAX_CHARS);
  console.log('  截断后片段末尾 80 字:', JSON.stringify(snippet.slice(-80)));
});

// 检查目标条目（含 8/31 ZOH 数字）的排名与截断位置
console.log('\n=== 含「8,713.89」的目标条目 ===');
const target = docs.find((d) => d.content.includes('8,713.89'));
if (!target) {
  console.log('未找到');
} else {
  const all = retrieve(docs, query, 999).top;
  const rank = all.findIndex((x) => x.doc.section === target.section) + 1;
  console.log(`§${target.section} ${target.title} — 排名 ${rank} / 命中条目总数 ${all.length}`);
  const idx = target.content.indexOf('8,713.89');
  console.log(`「8,713.89」在 content 中的字符位置: ${idx}（截断阈值 ${SNIPPET_MAX_CHARS}）`);
  console.log(`是否落入截断范围: ${idx !== -1 && idx < SNIPPET_MAX_CHARS ? '是' : '否 ← 被截断丢弃'}`);
  const idxIC = target.content.indexOf('IC-DRR');
  console.log(`「IC-DRR」字符位置: ${idxIC}，落入截断范围: ${idxIC !== -1 && idxIC < SNIPPET_MAX_CHARS ? '是' : '否 ← 被截断丢弃'}`);
}

// 第 6 章各条内容的长度与关键数字位置，用于确定合理的截断阈值
console.log('\n=== 第 6 章各条 content 长度与关键数字位置 ===');
const KEYS = ['8,713.89', 'IC-DRR', '9,525.87', '1.1694', '330.15', '-10.32', '67.4'];
for (const d of docs.filter((x) => x.chapter === 6)) {
  const pos = KEYS.map((k) => {
    const i = d.content.indexOf(k);
    return i < 0 ? null : `${k}@${i}`;
  })
    .filter(Boolean)
    .join('  ');
  console.log(`§${d.section}  len=${d.content.length}  ${pos}`);
}

const lens = docs.map((d) => d.content.length).sort((a, b) => a - b);
console.log(
  `\n全库 content 长度分布: min=${lens[0]} p50=${lens[Math.floor(lens.length * 0.5)]} p90=${lens[Math.floor(lens.length * 0.9)]} max=${lens[lens.length - 1]}`
);

// ============================================================
// 变体对比：评估分词与 topN 改动对目标条目排名的影响
// ============================================================
const isCJK = (ch) => /[\u4e00-\u9fa5]/.test(ch);

/** 变体分词：仅在「两个字符都是中文」时才生成 bigram，避免 ASCII 子串噪声（ZO/OH/20/02） */
function tokenizeCJKOnly(query) {
  const tokens = new Set();
  for (const part of query.trim().split(TOKEN_SPLIT)) {
    if (!part || !part.trim()) continue;
    const p = part.trim();
    tokens.add(p);
    if (p.length > 3 && isCJK(p)) {
      for (let i = 0; i < p.length - 1; i++) {
        const bg = p.slice(i, i + 2);
        if (isCJK(bg[0]) && isCJK(bg[1])) tokens.add(bg);
      }
    }
  }
  return tokens;
}

function rankWith(tokenizer, query, topN) {
  const keywords = tokenizer(query);
  const scored = [];
  for (const doc of docs) {
    const haystack = (doc.title + ' ' + doc.content).toLowerCase();
    let score = 0;
    for (const kw of keywords) {
      if (kw.length < 2) continue;
      if (haystack.includes(kw.toLowerCase())) {
        score += doc.title.toLowerCase().includes(kw.toLowerCase()) ? 3 : 1;
      }
    }
    if (score > 0) scored.push({ doc, score });
  }
  scored.sort((a, b) => b.score - a.score);
  return {
    top: scored.slice(0, topN),
    rankOf64: scored.findIndex((x) => x.doc.section === '6.4') + 1,
  };
}

console.log('\n=== 变体对比（目标：§6.4 进入 top-N 且带 8/31 数据）===');
const VARIANTS = [
  ['现状（全 bigram + topN=3）', tokenize, 3],
  ['仅中文 bigram + topN=3', tokenizeCJKOnly, 3],
  ['仅中文 bigram + topN=5', tokenizeCJKOnly, 5],
  ['现状分词 + topN=5', tokenize, 5],
];
for (const [name, tk, n] of VARIANTS) {
  const r = rankWith(tk, query, n);
  const inTop = r.top.some((x) => x.doc.section === '6.4');
  console.log(`\n【${name}】§6.4 排名=${r.rankOf64}  是否在 top-${n} 内=${inTop ? '✅ 是' : '❌ 否'}`);
  r.top.forEach((t, i) => console.log(`   ${i + 1}. §${t.doc.section} (score=${t.score}) ${t.doc.title}`));
}