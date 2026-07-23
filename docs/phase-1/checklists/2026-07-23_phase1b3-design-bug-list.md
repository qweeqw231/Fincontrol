# Phase 1b.3 设计师视角 Bug 清单（静态代码 review）

| 字段 | 值 |
| --- | --- |
| 状态 | 仅记录，不修 |
| Reviewer | Cline（前端静态代码 review） |
| Review 日期 | 2026-07-23（2026-07-24 用户反馈修订 HOME-014） |
| 范围 | `fincontrol-frontend/src/**`（pages / components / stores / api / utils / styles） |
| 不在范围 | 后端 Java 代码、CI、Docker、浏览器兼容实测、性能压测 |
| 关联里程碑 | phase 1b.3 已验收（2026-07-22） |
| Review 方法 | 完整阅读 HomePage / DataPage / CumulativeReturnCard / Sidebar / ErrorBoundary / PlaceholderPage / 4 个 CSS / store / api / utils / index.html |

> 本清单从专业前端设计师视角 review 而得，**每条 ID 永久唯一**，供后续工单 / 排期直接引用。所有"修复方向"仅为伪代码或思路，**不要求立即实施**。

---

## Scope 说明

phase 1b.3 已验收（见 `docs/test-records/manual-tests/1b/2026-07-22_phase1b3-comprehensive-acceptance-report.md`）。用户已知 3 个问题：

1. **HOME-001** — 单独刷新首页显示"暂无快照数据"，必须先点 `/data` 再回首页
2. **DATA-001** — 数据管理页最多只能上传 4 张图，超过 4 张会被截断（业务允许）
3. **DATA-002** — 占位符"请选择 4 张图（建议 0716 数据）"中的 `0716` 是测试日期残留

本清单**确认上述 3 个问题**并补充 25 条新发现（其中 HOME-014 经 2026-07-24 用户反馈后撤下），按严重程度分为 🔴 P0 / 🟡 P1 / 🟢 P2 / ⚪ 备注 四档。

---

## 严重程度与修复难度标签

| 严重度 | 含义 |
| --- | --- |
| 🔴 P0 | 影响数据可见性、用户引导、内存泄漏。**必须在 1b.4 之前修** |
| 🟡 P1 | 设计 token / 一致性问题。每加一个新页面就会踩一次坑 |
| 🟢 P2 | 文案 / 体验细节。可在后续打磨阶段与 AI 顾问一起做 |
| ⚪ 备注 | 经用户确认是**设计意图**，非 bug，仅供后人参考避免误改 |

| 修复难度 | 含义 |
| --- | --- |
| easy | < 30 行代码改动，单文件 |
| medium | 30~150 行，或需跨文件协调 |
| hard | > 150 行，需重构或引入新依赖 |

---

## 用户已确认的 3 个问题（重述）

### 🔴 HOME-001 — 单独刷新首页看不到数据

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🔴 P0（用户首要痛点） |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/HomePage.jsx` |
| 关联文件 | `fincontrol-frontend/src/pages/DataPage.jsx` |
| 关联文件 | `fincontrol-frontend/src/stores/assetSnapshotStore.js` |

**现象**：浏览器直接打开 `http://localhost:5173/` 或按 F5 刷新 → 首页整页只显示一行"暂无快照数据，请先到 /data 上传资产截图"。但只要先去侧栏点一下"数据管理"，再回到首页，数据就出来了。

**根因**：`HomePage.jsx` 全文没有 `useEffect` 调用 `fetchLatest()`。store 默认所有字段为 `null`/`[]`，所以 `latestSnapshot` 是 `null`，进入"空态"分支渲染。

**对照代码 `HomePage.jsx:451-492`**：

```jsx
export default function HomePage() {
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  // ...
  if (!snap) {
    return (
      <div className="page-shell">
        <header className="app-header">
          {/* 仍然渲染完整 header —— 见 HOME-003 */}
          <div className="brand">
            <span className="logo">💰</span>
            <div className="brand-text">
              <h1>FinControl</h1>
              <div className="sub">个人资产配置全景 · 支付宝快照</div>
            </div>
          </div>
          <div className="meta">
            <div className="date">—</div>
            <div>来源：支付宝</div>
          </div>
        </header>
        <div className="empty">暂无快照数据，请先到 /data 上传资产截图。</div>
      </div>
    )
  }
  // ... 完整数据渲染 ...
}
```

**对照代码 `DataPage.jsx:52-56`**（这是为什么先去数据管理就能看到数据的"魔法"）：

```jsx
useEffect(() => {
  fetchLatest(1)
  fetchMetaList()
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [])
```

**修复方向（伪代码）**：

```jsx
// HomePage.jsx
import { useEffect } from 'react'
import { useAssetSnapshotStore } from '../stores/assetSnapshotStore.js'

export default function HomePage() {
  const fetchLatest = useAssetSnapshotStore((s) => s.fetchLatest)
  const snap = useAssetSnapshotStore((s) => s.latestSnapshot)
  const loading = useAssetSnapshotStore((s) => s.loading)
  // ...

  // ✅ 新增：进入首页就拉一次
  useEffect(() => {
    // 只有 store 还没数据时才拉，避免重复请求
    if (!snap) fetchLatest(1)
  }, [snap, fetchLatest])

  // ...
}
```

> 另一种方案：把 `fetchLatest(1)` 提到 `App.jsx` 的顶层 `useEffect`，让 store 在应用启动时就有数据，所有页面共用。

---

### 🟢 DATA-001 — 4 张图上传硬限制

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟢 P2（业务允许，1b.3 验证期临时限制） |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/DataPage.jsx:73-84` |

**现象**：选 5+ 张图 → 弹原生 alert "最多 4 张图，已自动截取前 4 张"，自动截断到 4 张。

**代码 `DataPage.jsx:73-84`**：

```jsx
function handleFiles(e) {
  const list = Array.from(e.target.files || [])
  if (list.length > 4) {
    alert('最多 4 张图，已自动截取前 4 张')
  }
  const next = list.slice(0, 4)
  setFiles(next)
  setFilePreviews(next.map((f) => ({ name: f.name, url: URL.createObjectURL(f) })))
  setStep('idle')
  setError(null)
  setParsedAsset(null)
}
```

**修复方向（业务放开后）**：

```jsx
// 1. 移除 alert（见 GLOBAL-016）
// 2. 改为只接收恰好 4 张，不足 4 张禁用按钮 + 红色 hint
function handleFiles(e) {
  const list = Array.from(e.target.files || [])
  if (list.length === 0) return
  if (list.length < 4) {
    setError(`需要 4 张图，当前选了 ${list.length} 张`)
    return
  }
  if (list.length > 4) {
    setError(`最多 4 张图，请重新选择（当前选了 ${list.length} 张）`)
    return
  }
  // 严格 4 张才入 state
  const next = list.slice(0, 4)
  // ...
}
```

> 当前服务端 `/screenshot/parse-batch?mode={single|multi}` 仅接受 4 张。放开需先确认后端能接受 ≥5 张后再改前端。

---

### 🟢 DATA-002 — 占位符"建议 0716 数据"

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟢 P2（用户体验文案遗留） |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/DataPage.jsx:234` |

**现象**：未选图时显示 `请选择 4 张图（建议 0716 数据）`。`0716` 是开发测试时用的某次截图数据日期。

**代码 `DataPage.jsx:234`**：

```jsx
{filePreviews.length === 0 && (
  <div className="preview-empty">请选择 4 张图（建议 0716 数据）</div>
)}
```

**修复方向**：删掉"（建议 0716 数据）"或改成中性的"请选择 4 张支付宝基金截图"。

```jsx
<div className="preview-empty">请选择 4 张支付宝基金截图</div>
```

---

## 新发现的 Bug（25 条 + 1 条 ⚪ 备注）

### 🔴 P0 — 必修（3 条）

#### 🔴 P0-1 / HOME-002 — 空态缺跳转按钮

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🔴 P0 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/HomePage.jsx:489` |

**现象**：首页无数据时只显示一行"请先到 /data 上传资产截图"。用户必须手动改 URL 或去侧栏找"数据管理"按钮。**整个流程断裂**。

**代码**：

```jsx
{/* HomePage.jsx:489 */}
<div className="empty">暂无快照数据，请先到 /data 上传资产截图。</div>
```

**修复方向**：

```jsx
import { useNavigate } from 'react-router-dom'

function EmptyState({ navigate }) {
  return (
    <div className="empty-state">
      <div className="empty-icon">📊</div>
      <h2>暂无资产快照</h2>
      <p>上传 4 张支付宝基金截图，自动解析你的六大类配置。</p>
      <button
        className="primary-btn"
        onClick={() => navigate('/data')}
      >
        立即上传 →
      </button>
    </div>
  )
}

// 在 HomePage 里：
if (!snap) {
  return (
    <div className="page-shell">
      {/* header 可保留或根据设计取舍 */}
      <EmptyState navigate={useNavigate()} />
    </div>
  )
}
```

---

#### 🔴 P0-2 / GLOBAL-015 — blob URL 内存泄漏

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🔴 P0 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/DataPage.jsx:73-90, 160-179` |

**现象**：每次换图 / 重选 / 删除单图 / 提交入库成功 / 离开页面，都没调 `URL.revokeObjectURL` 释放 blob。浏览器会一直持有这 4 个 blob 对象，对应 4 张原图。长时间使用会持续涨内存。

**代码 `DataPage.jsx:73-90`**：

```jsx
function handleFiles(e) {
  const list = Array.from(e.target.files || [])
  // ...
  const next = list.slice(0, 4)
  setFiles(next)
  setFilePreviews(next.map((f) => ({ name: f.name, url: URL.createObjectURL(f) })))
  //                          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  //                          这里创建的 URL 永远没被 revoke
}

function removeFile(idx) {
  const next = files.filter((_, i) => i !== idx)
  setFiles(next)
  setFilePreviews(filePreviews.filter((_, i) => i !== idx))
  // ⚠️ 被删的那张图的 blob URL 也没 revoke
}
```

**代码 `DataPage.jsx:160-179`**（`confirm` 成功后只清 state，没释放 blob）：

```jsx
async function confirm() {
  // ...
  await fetchLatest(1)
  await fetchMetaList()
  setFiles([])
  setFilePreviews([])  // ⚠️ 这里丢掉了 url 字符串，但 blob 没释放
  setParsedAsset(null)
}
```

**修复方向**：

```jsx
// 1. 封装 cleanup 工具
function revokeAll(previews) {
  previews.forEach((p) => {
    if (p.url) URL.revokeObjectURL(p.url)
  })
}

// 2. handleFiles：先释放旧 blob，再创建新 blob
function handleFiles(e) {
  const list = Array.from(e.target.files || [])
  if (list.length > 4) {
    setError(`最多 4 张图，当前选了 ${list.length} 张`)
  }
  const next = list.slice(0, 4)

  // 释放旧的
  revokeAll(filePreviews)

  // 创建新的
  setFiles(next)
  setFilePreviews(next.map((f) => ({ name: f.name, url: URL.createObjectURL(f) })))
  // ...
}

// 3. removeFile：释放被删的那张
function removeFile(idx) {
  const removed = filePreviews[idx]
  if (removed?.url) URL.revokeObjectURL(removed.url)
  setFiles(files.filter((_, i) => i !== idx))
  setFilePreviews(filePreviews.filter((_, i) => i !== idx))
}

// 4. confirm：释放所有
async function confirm() {
  // ...
  await fetchLatest(1)
  await fetchMetaList()
  revokeAll(filePreviews)  // ✅
  setFiles([])
  setFilePreviews([])
  setParsedAsset(null)
}

// 5. 离开页面：useEffect cleanup
useEffect(() => {
  return () => revokeAll(filePreviews)
}, [])  // eslint-disable-line react-hooks/exhaustive-deps
```

---

#### 🔴 P0-3 / HOME-003 — 加载 / 错误 / 空态视觉断裂

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🔴 P0 |
| 修复难度 | medium |
| 关联文件 | `fincontrol-frontend/src/pages/HomePage.jsx:463-492` |

**现象**：首页有 3 种"非数据态"，但视觉差异巨大：

- **加载中**：`<div className="page-shell"><div className="empty">加载中...</div></div>` —— 整页只有一行
- **错误**：`加载失败：{String(error)}` —— 暴露内部错误
- **空态**：仍然渲染完整蓝色 header + brand + "—" 日期，然后底部塞一行"暂无快照数据" —— 像"半成品"

**代码 `HomePage.jsx:463-492`**：

```jsx
if (loading) {
  return <div className="page-shell"><div className="empty">加载中...</div></div>
}
if (error) {
  return (
    <div className="page-shell">
      <div className="error">加载失败：{String(error)}</div>
    </div>
  )
}
if (!snap) {
  return (
    <div className="page-shell">
      <header className="app-header">
        {/* 完整 header */}
      </header>
      <div className="empty">暂无快照数据，请先到 /data 上传资产截图。</div>
    </div>
  )
}
```

**修复方向**：抽出统一骨架，三态共用 header 风格：

```jsx
// 新增组件
function StateShell({ icon, title, sub, action }) {
  return (
    <div className="page-shell">
      <header className="app-header">
        {/* 共用 header */}
        <div className="brand">
          <span className="logo">💰</span>
          <div className="brand-text">
            <h1>FinControl</h1>
            <div className="sub">个人资产配置全景 · 支付宝快照</div>
          </div>
        </div>
        <div className="meta">
          <div className="date">—</div>
          <div>来源：支付宝</div>
        </div>
      </header>
      <div className="state-shell">
        <div className="state-icon">{icon}</div>
        <h2 className="state-title">{title}</h2>
        {sub && <p className="state-sub">{sub}</p>}
        {action}
      </div>
    </div>
  )
}

// HomePage 里：
if (loading) {
  return <StateShell icon="⏳" title="加载中..." sub="正在拉取最新快照" />
}
if (error) {
  return (
    <StateShell
      icon="⚠️"
      title="加载失败"
      sub="可能是后端服务未启动，请稍后重试"
      action={<button onClick={() => fetchLatest(1)}>重试</button>}
    />
  )
}
if (!snap) {
  return (
    <StateShell
      icon="📊"
      title="暂无资产快照"
      sub="上传 4 张支付宝基金截图，自动解析你的六大类配置"
      action={
        <button className="primary-btn" onClick={() => navigate('/data')}>
          立即上传 →
        </button>
      }
    />
  )
}
```

> 顺带把"加载失败"信息去掉原文（见 GLOBAL-007）。

---

### 🟡 P1 — 应修（9 条）

#### 🟡 P1-1 / GLOBAL-001 — `.card` 类命名冲突

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | medium |
| 关联文件 | `fincontrol-frontend/src/styles/cumulative-return.css:7-15` |
| 关联文件 | `fincontrol-frontend/src/styles/data-page.css:67-76` |

**现象**：同一个 `.card` 类在两个 CSS 里语义不同。

**代码 `cumulative-return.css:7-15`**（被首页 stat-row 用作弹性项）：

```css
.card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-4) var(--space-6);
  box-shadow: var(--shadow-sm);
  flex: 1 1 0;       /* 弹性项 */
  min-width: 0;
}
```

**代码 `data-page.css:67-76`**（被数据管理页用作块级堆叠）：

```css
/* 关键：DataPage 内的 .card 必须是块级堆叠，绝不沿用 cumulative-return.css 的 flex 1 1 0 */
.data-page .card {
  display: block;
  flex: initial;     /* 强行覆盖上面的 flex */
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 16px;
}
```

**修复方向**：拆分为两个语义类：

```css
/* cumulative-return.css：通用 stat-card */
.stat-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-4) var(--space-6);
  box-shadow: var(--shadow-sm);
  flex: 1 1 0;
  min-width: 0;
}

/* global.css：通用 section-card（块级堆叠版） */
.section-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 16px;
}
```

```jsx
// HomePage 用 stat-card
<div className="stat-card">...</div>

// DataPage 用 section-card
<section className="section-card">...</section>
```

---

#### 🟡 P1-2 / GLOBAL-002 — 模态框三套样式

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | medium |
| 关联文件 | `fincontrol-frontend/src/styles/cumulative-return.css:147-247` |
| 关联文件 | `fincontrol-frontend/src/styles/global.css:198-211` |
| 关联文件 | `fincontrol-frontend/src/styles/data-page.css:366-444` |

**现象**：模态框相关样式定义了三遍：

1. **cumulative-return.css** 定义 `.modal-backdrop` / `.modal` / `.modal-header` / `.modal-body` / `.modal-row` 等
2. **global.css** 又定义了一份相同名字的类（部分覆盖）
3. **data-page.css** 又定义了第三套 `.modal-backdrop` / `.modal-content` / `.modal-header` / `.modal-body` / `.modal-footer`（注意 `.modal-content` 而非 `.modal`）

改一处会漏两处。三套不一致导致外观漂移（CumulativeReturnCard 的弹窗和 DataPage 入库预览的弹窗视觉不同）。

**修复方向**：抽取 base + variants 模式。

```css
/* global.css：base modal */
.modal-backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  padding: 16px;
}
.modal {
  background: var(--color-bg-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
  max-width: 480px;
  width: 92vw;
  max-height: 86vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.modal--wide { max-width: 720px; }   /* DataPage 入库预览用 */
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--color-border);
  position: sticky; top: 0; background: #fff; z-index: 1;
}
/* body / footer 同理提取公共 */
```

```jsx
// CumulativeReturnCard.jsx
<div className="modal">  {/* 默认 480px */}
  <div className="modal-header">...</div>
  <div className="modal-body">...</div>
</div>

// DataPage.jsx
<div className="modal modal--wide">  {/* 720px */}
  <div className="modal-header">...</div>
  <div className="modal-body">...</div>
  <div className="modal-footer">...</div>
</div>
```

---

#### 🟡 P1-3 / GLOBAL-005 — 颜色 hex 失控

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | medium |
| 关联文件 | 几乎所有 CSS 文件 |

**现象**：蓝色分散在 5+ 处，正负色两套色板并存。

**蓝色清单**：

| 色值 | 用途 | 文件 |
| --- | --- | --- |
| `#0A59F7` | header / hero 渐变起色 | global.css |
| `#0847c4` | 主按钮 hover / header 渐变终色 | global.css / data-page.css |
| `#2a7aff` | hero 渐变终色 | global.css |
| `#1e40af` | `--color-primary` | variables.css |
| `#1d4ed8` | `--color-primary-hover` | variables.css |
| `#dbeafe` | `--color-primary-light` | variables.css |
| `#91caff` | 边框 hover / badge active | data-page.css |
| `#0A59F7` 复用 | section-title 竖条 | global.css / data-page.css |

**正负色双套色板**：

| 用途 | A 套（业务色，antd 风） | B 套（设计变量） |
| --- | --- | --- |
| 正/成功 | `#52C41A`（绿） | `--color-success: #10b981`（青绿） |
| 负/错误 | `#FF4D4F`（红） | `--color-error: #ef4444`（亮红） |

**修复方向**：收敛到 `variables.css`，**业务色 / 设计色分开**：

```css
/* variables.css：业务色板（antd 风，与中文惯例"涨红跌绿"一致） */
--biz-positive: #FF4D4F;
--biz-negative: #52C41A;
--biz-warning:  #FAAD14;
--biz-info:     #0A59F7;
--biz-bg-soft:  #f0f7ff;
--biz-bg-soft-border: #91caff;

/* 设计系统色板 */
--color-primary:        #0A59F7;   /* 统一改到 #0A59F7 */
--color-primary-hover:  #0847c4;
--color-primary-light:  #f0f7ff;
--color-success:        #52C41A;
--color-error:          #FF4D4F;
--color-warning:        #FAAD14;
```

```css
/* global.css：所有 #0A59F7 替换为 var(--color-primary) */
/* 所有 #FF4D4F 替换为 var(--color-error) */
/* 所有 #52C41A 替换为 var(--color-success) */
```

---

#### 🟡 P1-4 / GLOBAL-003 — 响应式断点不统一

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | easy |
| 关联文件 | 多个 CSS |

**现象**：

| 断点 | 文件 |
| --- | --- |
| `960px` | `global.css:216` |
| `768px` | `data-page.css:131, 456` / `cumulative-return.css:53` |

**修复方向**：在 `variables.css` 中统一：

```css
/* variables.css */
--bp-mobile:  480px;
--bp-tablet:  768px;
--bp-desktop: 1024px;
--bp-wide:    1280px;
```

```css
/* 各处使用 */
@media (max-width: 768px) { ... }   /* → */
@media (max-width: var(--bp-tablet)) { ... }
```

---

#### 🟡 P1-5 / GLOBAL-004 — 字体大小字面量

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/styles/global.css` |

**现象**：`variables.css` 定义了 6 档字号 (`--text-sm/base/lg/xl/2xl/3xl`)，但 `global.css` 大量直接用字面量：

```css
/* global.css */
.section-title { font-size: 15px; }      /* 应该用 var(--text-base) 或新增 var(--text-md) */
.panel-title { font-size: 15px; }
.card-value { font-size: 26px; }         /* 自定义值，没有对应变量 */
.hero-value { font-size: 44px; }         /* 自定义值 */
.timeline .time { font-size: 13px; }     /* 自定义值 */
```

**修复方向**：扩展变量 + 全部字面量改 var：

```css
/* variables.css 扩展 */
--text-xs:   11px;
--text-sm:   12px;
--text-md:   13px;   /* 新增 */
--text-base: 14px;
--text-lg:   16px;
--text-xl:   20px;
--text-2xl:  24px;
--text-3xl:  32px;
--text-display: 44px; /* 新增，hero 用 */
```

```css
/* global.css 全文替换 */
.section-title { font-size: var(--text-md); }
.hero-value { font-size: var(--text-display); font-weight: 700; }
```

---

#### 🟡 P1-6 / HOME-007 — 三卡片不等高

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/styles/global.css:83` |

**现象**：首页顶部 `.stat-row` 三列 grid 强制 1fr。六大类 / 余额类 / CumulativeReturnCard 三卡片高度不一（特别是 CumulativeReturnCard 内嵌双列 + 4 个 ℹ️ 按钮，高度会低于另两张）。

**代码 `global.css:83-89`**：

```css
.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-row .card { background: #fff; border-radius: 8px; padding: 20px 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
```

**修复方向**：默认就是 `align-items: stretch`，但需配合 CumulativeReturnCard 内部布局：

```jsx
// CumulativeReturnCard.jsx：把 crc-grid 改为 row，让累计 / 持有上下排
// 或：把高度不等的部分作为 row，而不是嵌入 label/rate/amt
<div className="cumulative-return-card stat-card">
  <div className="crc-row">
    <div className="crc-block">
      <div className="crc-label">累计</div>
      <div className="crc-value">{formatRate(cumRate)}</div>
    </div>
    <div className="crc-block">
      <div className="crc-label">持有</div>
      <div className="crc-value">{formatRate(holdRate)}</div>
    </div>
  </div>
</div>
```

---

#### 🟡 P1-7 / HOME-013 — "v1.0-DRAFT" 残留 + 技术术语泄漏

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/HomePage.jsx:570-588` |

**现象**：首页"数据口径说明"卡写了 `v1.0-DRAFT` 标签，且正文用 `<code style={{ background: '#fff', padding: '0 4px' }}>` 嵌了 `snapshot_meta.is_current`、`0.00`、`—` 等代码片段。普通用户看不懂术语，开发期标签不该出现在生产 UI。

**代码 `HomePage.jsx:570-588`**：

```jsx
<div className="panel">
  <div className="panel-title">
    数据口径说明
    <span className="tag">v1.0-DRAFT</span>   {/* 开发残留 */}
  </div>
  <div className="note-box" style={{ marginBottom: 0 }}>
    <div className="note-title">📌 数据口径</div>
    "六大类"仅含货币类、固收类、商品类、A股权益类、海外权益类、港股大中华类。
    余额类不参与占六大类比例与目标偏差；首页所有金额、收益、基金数均绑定
    <code style={{ background: '#fff', padding: '0 4px' }}>
      snapshot_meta.is_current           {/* 技术术语 */}
    </code>
    对应日期，避免跨日累加。基金明细"类内占比"分母为大类金额；配置表
    "占六大类"分母为六大类总值；零值（如 +3.84 + -3.84）须显示
    <code style={{ background: '#fff', padding: '0 4px' }}>0.00</code>
    ，未知的 null 才显示
    <code style={{ background: '#fff', padding: '0 4px' }}>—</code>。
  </div>
</div>
```

**修复方向**：重写为面向用户的口径说明：

```jsx
<div className="panel">
  <div className="panel-title">
    数据口径说明
    <span className="tag">v1.0</span>
  </div>
  <div className="note-box" style={{ marginBottom: 0 }}>
    <div className="note-title">📌 资产分组</div>
    <p>本系统将你的持仓分为 <strong>六大类</strong>（货币 / 固收 / 商品 / A股权益 / 海外权益 / 港股大中华）和 <strong>余额类</strong>（如余额宝）。</p>
    <p>占比与目标偏差基于六大类计算，余额类独立显示。</p>
    <div className="note-title" style={{ marginTop: 12 }}>📐 数字约定</div>
    <ul>
      <li>金额 / 占比：保留 2 位小数</li>
      <li>零值（如 +3.84 + -3.84）：显示 0.00</li>
      <li>未知数据：显示 —</li>
    </ul>
  </div>
</div>
```

---

#### 🟡 P1-8 / HOME-016 — 章节序号硬编码

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/DataPage.jsx:205, 238, 277, 293` |

**现象**：数据管理页 4 个 section 标题硬编码 `1.` `2.` `3.` `4.`，未来插入新章节要改 4 处。

**代码**：

```jsx
<h2>1. 上传 4 张图</h2>            {/* :205 */}
<h2>2. 解析与日期</h2>             {/* :238 */}
<h2>3. 确认入库</h2>              {/* :277 */}
<h2>4. 快照管理（决策 27）</h2>    {/* :293 */}
```

**修复方向**：

```jsx
const SECTIONS = [
  { id: 'upload',    title: '上传 4 张图',       hint: '...' },
  { id: 'parse',     title: '解析与日期',        hint: '...' },
  { id: 'confirm',   title: '确认入库',         hint: '...' },
  { id: 'metaList',  title: '快照管理',         hint: '...' },
]

{SECTIONS.map((s, idx) => (
  <section key={s.id} className="card">
    <h2>{idx + 1}. {s.title}</h2>
    <p className="hint">{s.hint}</p>
    {/* ... 内容用条件渲染或拆分组件 */}
  </section>
))}
```

---

#### 🟡 P1-9 / DATA-006 — `confirm` 成功后 `step` 没回 `'idle'`

| 字段 | 内容 |
| --- | --- |
| 严重度 | 🟡 P1 |
| 修复难度 | easy |
| 关联文件 | `fincontrol-frontend/src/pages/DataPage.jsx:160-179` |

**现象**：`confirm` 成功后 `setStep('confirmed')` 残留。下次再上传新图 → `step === 'confirmed'`，但按钮 disabled 条件 `step !== 'parsed' && step !== 'confirmed'` 看似允许点击，**但用户刷新页面前无法重置成 `idle`**——按钮文案"入库中…"会闪一下。

**代码 `DataPage.jsx:160-179`**：

```jsx
async function confirm() {
  if (!parsedAsset) return
  setStep('confirming')
  try {
    await apiClient.post(
      ENDPOINTS.SNAPSHOT_CONFIRM,
      { userId: 1, snapshotDate, confirmedOverwrite: true, parsedAssets: [parsedAsset] },
      { headers: { 'X-User-Id': '1' } }
    )
    setStep('confirmed')   // ⚠️ 残留态
    await fetchLatest(1)
    await fetchMetaList()
    setFiles([])
    setFilePreviews([])
    setParsedAsset(null)
  } catch (e) {
    setError(e?.message || 'confirm 失败')
    setStep('error')
  }
}
```

**修复方向**：成功后短暂显示 success，然后回 idle：

```jsx
async function confirm() {
  if (!parsedAsset) return
  setStep('confirming')
  try {
    await apiClient.post(...)
    setStep('confirmed')
    await fetchLatest(1)
    await fetchMetaList()
    // 释放 blob（见 GLOBAL-015）
    filePreviews.forEach((p) => p.url && URL.revokeObjectURL(p.url))

    setFiles([])
    setFilePreviews([])
    setParsedAsset(null)

    // 1.5s 后回到 idle，允许下一次上传
    setTimeout(() => setStep('idle'), 1500)
  } catch (e) {
    setError(e?.message || 'confirm 失败')
    setStep('error')
  }
}
```

---

### 🟢 P2 — 可改进（13 条）

#### 🟢 P2-1 / GLOBAL-016 — 用原生 `alert()`

**位置**：`fincontrol-frontend/src/pages/DataPage.jsx:76`

```jsx
if (list.length > 4) {
  alert('最多 4 张图，已自动截取前 4 张')
}
```

**问题**：1990 年代 UI，阻塞主线程，与现代 SPA 体验脱节。

**修复方向**：用 inline error hint + toast 替代：

```jsx
// 1. 用 error state 而不是 alert
if (list.length > 4) {
  setError(`当前选了 ${list.length} 张，已自动取前 4 张`)
}

// 2. 复杂场景抽 toast 组件
function Toast({ message, kind = 'info', onClose }) {
  useEffect(() => {
    const t = setTimeout(onClose, 3000)
    return () => clearTimeout(t)
  }, [onClose])
  return <div className={`toast toast-${kind}`}>{message}</div>
}
```

---

#### 🟢 P2-2 / HOME-015 — `pill` 颜色语义混乱

**位置**：`fincontrol-frontend/src/pages/HomePage.jsx:307-318`

```jsx
let pillCls = 'pill-on'
let pillText = `${c.categoryName}达标`
if (Math.abs(dev) < 0.5) {
  pillCls = 'pill-on'
  pillText = `${c.categoryName}达标`
} else if (dev > 0) {
  pillCls = 'pill-over'      // 红色
  pillText = `${c.categoryName} +${dev.toFixed(2)}%`
} else {
  pillCls = 'pill-under'     // 绿色 ⚠️ 但"缺口"是负向，用绿色暗示"好"是错的
  pillText = `${c.categoryName} ${dev.toFixed(2)}%`
}
```

**问题**：

- `pill-over` 红 = 超配警示 ✅ 合理
- `pill-under` 绿 = "缺口"绿色 ❌ 让人误以为达标
- `pill-on` 蓝 = 达标 ✅ 合理但蓝色与蓝色品牌色冲突

**修复方向**：三档语义改为"是否需要行动"：

```jsx
if (Math.abs(dev) < 0.5) {
  pillCls = 'pill-ok'        // 绿色，达标
  pillText = `✅ ${c.categoryName}达标`
} else if (dev > 0) {
  pillCls = 'pill-warn'      // 橙色，超配警示
  pillText = `⚠️ ${c.categoryName} 超 +${dev.toFixed(2)}%`
} else {
  pillCls = 'pill-action'    // 红色，缺口需补
  pillText = `❗ ${c.categoryName} 缺口 ${(-dev).toFixed(2)}%`
}
```

```css
.pill-ok     { background: #f6ffed; color: #52C41A; }
.pill-warn   { background: #fff7e6; color: #FAAD14; }
.pill-action { background: #fff1f0; color: #FF4D4F; }
```

---

#### 🟢 P2-3 / HOME-009 — `¥` 符号手工拼接间距偏小

**位置**：`fincontrol-frontend/src/pages/HomePage.jsx:527`

```jsx
<div className="hero-value">¥ {formatYuan(totalAll)}</div>
```

**问题**：`¥` 是全角，` ` 是半角空格，视觉上 ¥ 与数字之间间距偏小。

**修复方向**：扩展 `formatYuan` 接受 `withSymbol` 选项，或 CSS 加间距：

```jsx
// 方式 1：扩展 formatters
export function formatYuan(n, opts = {}) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const body = Number(n).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  if (opts.withSymbol) return `¥\u2009${body}`   // thin space (U+2009)
  return body
}

// 方式 2：CSS
.hero-value { font-variant-numeric: tabular-nums; }
.hero-value::before { content: '¥'; margin-right: 0.25em; }
```

---

#### 🟢 P2-4 / HOME-012 — 双面板高度不齐

**位置**：`fincontrol-frontend/src/pages/global.css:91`

```css
.two-col { display: grid; grid-template-columns: 1.1fr 1fr; gap: 16px; margin-bottom: 16px; }
```

**问题**：左"六大类分布"是表格 + 图，右"数据口径说明"是大段文字，左右高度不对齐。

**修复方向**：让数据卡高度自适应或加 sticky：

```jsx
<div className="two-col">
  <SixPiePanel ... />  {/* 高度自增 */}
  <div className="panel panel--sticky">    {/* 右侧在桌面端 sticky */}
    <div className="panel-title">数据口径说明</div>
    <div className="note-box">...</div>
  </div>
</div>
```

```css
@media (min-width: 1024px) {
  .panel--sticky { position: sticky; top: 16px; align-self: start; }
}
```

---

#### 🟢 P2-5 / GLOBAL-007 — 错误信息直接暴露给用户

**位置**：`fincontrol-frontend/src/pages/HomePage.jsx:467-472`

```jsx
if (error) {
  return (
    <div className="page-shell">
      <div className="error">加载失败：{String(error)}</div>
    </div>
  )
}
```

**问题**：把 axios error 对象 `toString()` 直接渲染，可能暴露 axios stack 或业务码给最终用户。

**修复方向**：分类展示：

```jsx
function friendlyError(err) {
  if (typeof err === 'string') return err
  if (err?.code === 0) return '网络异常，请检查后端服务'
  if (err?.code >= 400 && err?.code < 500) return '请求参数错误'
  if (err?.code >= 500) return '服务器错误，请稍后重试'
  return '加载失败，请稍后重试'
}

// 错误态：
if (error) {
  return <StateShell icon="⚠️" title="加载失败" sub={friendlyError(error)} action={<button>重试</button>} />
}
```

> 见 HOME-003 的 StateShell。

---

#### 🟢 P2-6 / GLOBAL-021 — 缺少 favicon

**位置**：`fincontrol-frontend/index.html`

```html
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>FinControl - 个人资产配置控制</title>
  <!-- 缺少 <link rel="icon"> -->
</head>
```

**问题**：浏览器 tab 显示 vite 默认 logo 或空白。

**修复方向**：

```html
<head>
  <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
  <!-- 或 base64 内嵌 emoji 💰 -->
</head>
```

或新增 `fincontrol-frontend/public/favicon.svg`（带 💰 字符）。

---

#### 🟢 P2-7 / GLOBAL-010 — SPA 路由不更新 `document.title`

**位置**：`fincontrol-frontend/src/App.jsx` + 所有 pages

**问题**：`/data`、`/ai` 切换时 tab 标题永远是 `FinControl - 个人资产配置控制`。

**修复方向**：用 `useEffect` 或 `react-helmet-async`：

```jsx
// 简单实现
import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const TITLES = {
  '/':          '首页 · FinControl',
  '/data':      '数据管理 · FinControl',
  '/ai':        'AI 顾问 · FinControl',
  '/config':    '资产配置 · FinControl',
  // ...
}

export default function App() {
  const location = useLocation()
  useEffect(() => {
    document.title = TITLES[location.pathname] || 'FinControl'
  }, [location.pathname])
  // ...
}
```

---

#### 🟢 P2-8 / PERF-002 — `PieChart` 重复实现

**位置**：`fincontrol-frontend/src/pages/HomePage.jsx:196-236`

```jsx
function PieChart({ data, sixTotal }) {
  // ... 60 行内联实现
}
```

**问题**：`fincontrol-frontend/src/components/SixCategoriesPie.jsx` 已经存在，但 `HomePage` 没用它，自己内联了一份简化版。

**修复方向**：

```jsx
// 删除 HomePage.jsx:196-236 的内联 PieChart
import { SixCategoriesPie } from '../components/SixCategoriesPie.jsx'

// 在 SixPiePanel 里替换：
<div className="pie-wrap">
  <SixCategoriesPie data={sixCats} sixTotal={sixTotal} />
</div>
```

---

#### 🟢 P2-9 / PERF-004 — `HomePage.jsx` 单文件 614 行

**位置**：`fincontrol-frontend/src/pages/HomePage.jsx`

**问题**：内联 8 个子组件：

| 组件 | 行号 |
| --- | --- |
| `FundDetailTable` | 55-106 |
| `SixCategoryGroup` | 108-156 |
| `SixPiePanel` | 158-194 |
| `PieChart` | 196-236 |
| `ConfigDeviationTable` | 238-287 |
| `SummaryList` | 289-336 |
| `QuickActions` | 338-379 |
| `RecentOps` | 391-449 |

**修复方向**：拆分到 `components/`：

```
fincontrol-frontend/src/components/home/
├── FundDetailTable.jsx       (含 SixCategoryGroup)
├── SixPiePanel.jsx           (含 PieChart, ConfigDeviationTable)
├── SummaryList.jsx
├── QuickActions.jsx
└── RecentOps.jsx
```

```jsx
// HomePage.jsx 瘦身
import { FundDetailTable } from '../components/home/FundDetailTable.jsx'
import { SixPiePanel } from '../components/home/SixPiePanel.jsx'
import { SummaryList } from '../components/home/SummaryList.jsx'
import { QuickActions } from '../components/home/QuickActions.jsx'
import { RecentOps } from '../components/home/RecentOps.jsx'

export default function HomePage() {
  // 只保留主组件逻辑
}
```

---

#### 🟢 P2-10 / DATA-005 — "上传并解析" 按钮文案不明确

**位置**：`fincontrol-frontend/src/pages/DataPage.jsx:260-268`

```jsx
<button onClick={uploadAndParse} ...>
  {step === 'uploading' ? '上传中…' : step === 'parsing' ? '解析中…' : '上传并解析'}
</button>
```

**问题**：按钮实际是 upload + parse 一步走，仅说"上传"误导。

**修复方向**：保持当前文案但加 tooltip：

```jsx
<button
  onClick={uploadAndParse}
  title="先上传图片，再调用 AI 解析 19 只基金数据"
>
  {step === 'uploading' ? '上传中…' : step === 'parsing' ? '解析中…' : '上传并解析'}
</button>
```

或文案改为 `上传 + 解析（一步）`。

---

#### 🟢 P2-11 / DATA-010 — 解析模式 `single/multi` 无说明

**位置**：`fincontrol-frontend/src/pages/DataPage.jsx:244-247`

```jsx
<select value={mode} onChange={(e) => setMode(e.target.value)} data-testid="mode-select">
  <option value="single">single（推荐，单图逐张）</option>
  <option value="multi">multi（4 图 batch）</option>
</select>
```

**问题**：普通用户不懂 single vs multi 区别。

**修复方向**：下方加帮助文字：

```jsx
<label>
  <span>解析模式</span>
  <select value={mode} onChange={...}>...</select>
  <small className="form-hint">
    <strong>single</strong>：逐张上传，失败可单独重试；<br />
    <strong>multi</strong>：4 张一次性发给 AI，速度快但失败需全部重试。
  </small>
</label>
```

---

#### 🟢 P2-12 / DATA-011 — 技术术语泄漏到 UI

**位置**：`fincontrol-frontend/src/pages/DataPage.jsx:279`

```jsx
<p className="hint">确认将 19-fund 数据写入 asset_raw + asset_snapshot + snapshot_meta</p>
```

**问题**：`asset_raw` / `asset_snapshot` / `snapshot_meta` 是表名，普通用户不需要知道。

**修复方向**：

```jsx
<p className="hint">确认将今日资产数据写入历史记录</p>
```

---

#### 🟢 P2-13 / DATA-012 — modal 中"快照日期"卡位置不当

**位置**：`fincontrol-frontend/src/pages/DataPage.jsx:349-356`

```jsx
<div className="overview-card">
  <div className="label">快照日期</div>
  <div className="value">{snapshotDate}</div>
</div>
```

**问题**：5 列 grid 里有 4 个是资产数据（总资产 / 六大类 / 余额类 / 基金数），第 5 个是日期。日期和资产数据语义不同，混入 grid 视觉割裂。

**修复方向**：把日期拿出来作为 header 副标题：

```jsx
<div className="modal-header">
  <h2>📊 今日资产预览 · {snapshotDate}</h2>
  <button className="modal-close" ...>×</button>
</div>

<div className="overview-summary">
  {/* 现在只剩 4 个资产卡，4 列 grid */}
  <div className="overview-card highlight">
    <div className="label">总资产（含余额类）</div>
    <div className="value">¥{...}</div>
  </div>
  <div className="overview-card">...</div>
  <div className="overview-card">...</div>
  <div className="overview-card">...</div>
</div>
```

---

### ⚪ 备注 — 非 bug（用户设计意图，仅作后人参考）

#### ⚪ HOME-014 — "类内占比 100.00%" 在小计列

| 字段 | 内容 |
| --- | --- |
| 严重度 | ⚪ 非 bug |
| 修复难度 | N/A（无需修改） |
| 关联文件 | `fincontrol-frontend/src/pages/HomePage.jsx:147-153` |

**代码**：

```jsx
<tr className="subtotal-row">
  <td>{cat.categoryName}小计</td>
  <td>{formatYuan(cTotal)}</td>
  <td>{subtotalHolding == null ? '—' : formatSignedAmount(subtotalHolding)}</td>
  <td>{subtotalCumulative == null ? '—' : formatSignedAmount(subtotalCumulative)}</td>
  <td>100.00%</td>
</tr>
```

**Reviewer 初判（错误）**：该列为大类内部所有基金占比之和，按数学必然是 100%，认为"没信息量"。

**用户反馈（2026-07-24）**：⚠️ **这是 Reviewer 的设计语义理解错误**。用户**故意保留** 100%，目的是：

- 显式标注"大类内部子资产比例之和"
- 让明细表的"持仓金额 / 持有收益 / 累计收益 / 类内占比"四列在小计行也保持完整视觉对齐
- 不至于让小计行缺一列看起来像"漏数据"

**修正结论**：

| 字段 | 原值 | 修正后 |
| --- | --- | --- |
| 严重度 | 🟢 P2 | ⚪ 非 bug（设计意图） |
| 修复方向 | 改为 `—` 或去掉 | **保持 100.00%** |

**可选增量建议（不在本次任务范围）**：

1. **可读性增强**：可在 100.00% 后用 `title` 属性 + tooltip 说明"大类内部子资产占比之和"，避免后人误删：
   ```jsx
   <td title="大类内部各基金持仓占比之和（恒为 100%）">100.00%</td>
   ```
2. **数据一致性自检**：可在该列旁边加一个 `±` 标记，提示"类内占比合计已校验"（如有此类后台自检逻辑）。

> 本条从"待修复 bug 列表"移出，仅作为后续可能的微优化备查。

---

## 修复路线图（仅供参考，不在本次任务范围）

| 阶段 | 内容 | 理由 |
| --- | --- | --- |
| 1b.4 之前 | P0 三条（HOME-002 / GLOBAL-015 / HOME-003）+ HOME-001 必修 | 用户首要痛点 + 内存泄漏 |
| 1b.4 同期 | P1 设计 token（5 条）+ 文案（HOME-013 / HOME-015 / DATA-005 / DATA-010 / DATA-011 / DATA-012） | 避免新页面再踩坑 + 与 AI 顾问一起打磨 |
| Phase 2 之前 | P2 剩余（HOME-009 / HOME-012 / GLOBAL-007 / GLOBAL-021 / GLOBAL-010 / PERF-002 / PERF-004 / DATA-006） | 体验细节 + 性能 + 重构 |

> **总修复估算**：约 12~15 个 PR，每个 PR < 200 行 diff。

---

## 不在本次 review 范围

- 后端 Java 代码（独立 review）
- 测试覆盖率（store/api/utils 已有 vitest 测试通过，见 `fincontrol-frontend/src/tests/`）
- 性能压测（仅静态分析）
- 浏览器兼容性实测（仅静态分析，需真实环境跑 playwright）
- 无障碍 a11y 完整审计（仅重点提及 modal / focus trap / aria-label）

---

## 引用

- 已有 phase-1b checklist：`docs/phase-1/checklists/phase-1b.md`
- 决策 27（is_latest 双层）：`docs/phase-1/decisions/decision-27-is-latest-dual-layer.md`
- 1b.3 综合验收报告：`docs/test-records/manual-tests/1b/2026-07-22_phase1b3-comprehensive-acceptance-report.md`
- 1b.3 补救验收报告：`docs/test-records/manual-tests/1b/2026-07-22_phase1b3-remediation-acceptance-plan.md`

---

## Bug ID 索引（方便后续引用）

| ID | 严重度 | 修复难度 | 一句话 |
| --- | --- | --- | --- |
| HOME-001 | 🔴 P0 | easy | 单独刷新首页看不到数据 |
| HOME-002 | 🔴 P0 | easy | 空态缺跳转按钮 |
| HOME-003 | 🔴 P0 | medium | 加载/错误/空态视觉断裂 |
| GLOBAL-001 | 🟡 P1 | medium | `.card` 类命名冲突 |
| GLOBAL-002 | 🟡 P1 | medium | 模态框三套样式重复 |
| GLOBAL-003 | 🟡 P1 | easy | 响应式断点不统一 |
| GLOBAL-004 | 🟡 P1 | easy | 字体大小字面量 |
| GLOBAL-005 | 🟡 P1 | medium | 颜色 hex 失控（5+ 蓝色、2 套正负色） |
| GLOBAL-007 | 🟢 P2 | easy | 错误信息暴露给用户 |
| GLOBAL-010 | 🟢 P2 | easy | SPA 不更新 document.title |
| GLOBAL-015 | 🔴 P0 | easy | blob URL 内存泄漏 |
| GLOBAL-016 | 🟢 P2 | easy | 用原生 `alert()` |
| GLOBAL-021 | 🟢 P2 | easy | 缺少 favicon |
| PERF-002 | 🟢 P2 | medium | PieChart 重复实现 |
| PERF-004 | 🟢 P2 | medium | HomePage.jsx 614 行（应拆分） |
| HOME-007 | 🟡 P1 | easy | 三卡片不等高 |
| HOME-009 | 🟢 P2 | easy | ¥ 符号手工拼接间距偏小 |
| HOME-012 | 🟢 P2 | medium | 双面板高度不齐 |
| HOME-013 | 🟡 P1 | easy | "v1.0-DRAFT" + 技术术语泄漏 |
| HOME-014 | ⚪ 备注 | N/A | "类内占比 100.00%" 是设计意图，非 bug |
| HOME-015 | 🟢 P2 | easy | pill 颜色语义混乱（under 用绿） |
| HOME-016 | 🟡 P1 | easy | 章节序号 1-4 硬编码 |
| DATA-001 | 🟢 P2 | easy | 4 张图上传硬限制（业务允许） |
| DATA-002 | 🟢 P2 | easy | 占位符"建议 0716 数据" |
| DATA-005 | 🟢 P2 | easy | "上传并解析" 文案不明确 |
| DATA-006 | 🟡 P1 | easy | confirm 成功后 step 没回 idle |
| DATA-010 | 🟢 P2 | easy | 解析模式 single/multi 无说明 |
| DATA-011 | 🟢 P2 | easy | "asset_raw/snapshot_meta" 术语泄漏 |
| DATA-012 | 🟢 P2 | easy | modal 中"快照日期"卡位置不当 |

共 **29 条 ID**（3 条已知 + 25 条新增待修 + 1 条 ⚪ 备注 = 29 条）；HOME-001 单独列在"已知问题"中，P2 段少 1 条（原 P2-3 / HOME-014 已撤下）。原计划 26 条新增，撤下 1 条后为 25 条新增。

---

## 修订记录

| 日期 | 内容 |
| --- | --- |
| 2026-07-23 | 初版：3 条已知 + 26 条新增 bug |
| 2026-07-24 | 修订：HOME-014 经用户反馈撤下，移入 ⚪ 备注段；严重度从 🟢 P2 改为 ⚪ 非 bug |

---

**Reviewer 备注**：本次 review 完全基于静态代码阅读，未启动前端 dev server 做视觉走查（PLAN MODE 限制）。所有结论应有 90%+ 准确率，但建议在 1b.4 前做一次实际 browser walkthrough 验证 P0 三条的实际表现。