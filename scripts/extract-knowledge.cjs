// 从 micro-control-finance 静态站点提取正文，生成 RAG 知识库 JSON
const fs = require('fs')
const path = require('path')

const SITE_ROOT = 'c:\\Work\\Minimax_Work\\micro-control-finance'
const MANIFEST = path.join(SITE_ROOT, 'assets', 'content-manifest.json')
const OUTPUT = 'c:\\Work\\Fincontrol-main\\fincontrol-backend\\src\\main\\resources\\knowledge\\knowledge-base.json'

const CHAPTER_NAMES = {
  1: '理论渊源', 2: '架构与算法', 3: '实证、回测与子系统', 4: '工程实现', 5: '修正、危机与方法论'
}

function decodeHtml(str) {
  return str
    .replace(/&gt;/g, '>')
    .replace(/&lt;/g, '<')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&ldquo;/g, '\u201C')
    .replace(/&rdquo;/g, '\u201D')
}

function extractMainText(html) {
  // 定位 <main class="page ...">
  const mainMatch = html.match(/<main[^>]*class="[^"]*page[^"]*"[^>]*>([\s\S]*?)<\/main>/)
  if (!mainMatch) return ''
  let content = mainMatch[1]

  // 移除 script/style
  content = content.replace(/<script[\s\S]*?<\/script>/gi, '')
  content = content.replace(/<style[\s\S]*?<\/style>/gi, '')

  // 处理表格：先把表格提取为文本
  content = content.replace(/<table[\s\S]*?<\/table>/gi, (table) => {
    const rows = []
    const trMatches = [...table.matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/gi)]
    for (const tr of trMatches) {
      const cells = [...tr[1].matchAll(/<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/gi)]
        .map(c => c[1].replace(/<[^>]+>/g, '').trim())
      if (cells.length) rows.push(cells.join(' | '))
    }
    return '\n' + rows.join('\n') + '\n'
  })

  // 标题
  content = content.replace(/<h1[^>]*>([\s\S]*?)<\/h1>/gi, '\n# $1\n')
  content = content.replace(/<h2[^>]*>([\s\S]*?)<\/h2>/gi, '\n## $1\n')
  content = content.replace(/<h3[^>]*>([\s\S]*?)<\/h3>/gi, '\n### $1\n')
  content = content.replace(/<h4[^>]*>([\s\S]*?)<\/h4>/gi, '\n#### $1\n')

  // 段落
  content = content.replace(/<p[^>]*>/gi, '\n')
  content = content.replace(/<li[^>]*>/gi, '\n- ')

  // 公式块保留 LaTeX
  content = content.replace(/<div class="eq-block"[^>]*>([\s\S]*?)<\/div>/gi, '\n$1\n')

  // 移除剩余 HTML 标签
  content = content.replace(/<[^>]+>/g, '')

  // HTML 实体解码
  content = decodeHtml(content)

  // 清理空白
  content = content.replace(/[ \t]+/g, ' ')
  content = content.replace(/\n{3,}/g, '\n\n')
  return content.trim()
}

function main() {
  const manifest = JSON.parse(fs.readFileSync(MANIFEST, 'utf-8'))
  const docs = []
  let total = 0

  for (const ch of manifest.chapters) {
    const chNum = ch.num
    const chDir = path.join(SITE_ROOT, ch.dir)

    // 章导论
    const introPath = path.join(chDir, 'index.html')
    if (fs.existsSync(introPath)) {
      const content = extractMainText(fs.readFileSync(introPath, 'utf-8'))
      if (content) {
        docs.push({
          section: `第${chNum}章导论`,
          title: ch.title_long.includes('·') ? ch.title_long.split('·').pop().trim() : ch.title_long,
          chapter: chNum,
          chapterName: CHAPTER_NAMES[chNum] || '',
          content,
        })
        total++
      }
    }

    // 各节
    for (const sec of ch.sections) {
      const secPath = path.join(chDir, `${sec.slug}.html`)
      if (fs.existsSync(secPath)) {
        const content = extractMainText(fs.readFileSync(secPath, 'utf-8'))
        if (content) {
          docs.push({
            section: sec.num,
            title: sec.title,
            chapter: chNum,
            chapterName: CHAPTER_NAMES[chNum] || '',
            content,
          })
          total++
        }
      }
    }
  }

  fs.mkdirSync(path.dirname(OUTPUT), { recursive: true })
  fs.writeFileSync(OUTPUT, JSON.stringify(docs, null, 2), 'utf-8')
  console.log(`提取完成：${total} 条知识条目 -> ${OUTPUT}`)
  const lengths = docs.map(d => d.content.length)
  console.log(`正文长度：最短 ${Math.min(...lengths)} / 最长 ${Math.max(...lengths)} / 平均 ${Math.round(lengths.reduce((a,b)=>a+b,0)/lengths)} 字`)
}

main()
