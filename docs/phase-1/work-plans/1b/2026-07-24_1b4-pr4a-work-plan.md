# Phase 1b.4 PR4a 工作计划（P2 UX 小修 · 10 条）

> **状态**：📝 计划定稿中（待 review）
> **日期**：2026-07-24
> **前置门禁**：PR0（5ca93b5）+ PR1（d06daf2）+ PR2 主（fe6a0a2） + PR2.envfix（a6329f0） + PR2.hotfix（0939928） + PR2 报告（1035c9c） + PR3（de946c6） + PR3+（2e41bf9 / cccb132 / 5f0eacb / 1b98b8c / b0dc7a1）全部完成并验收
> **配套验收**：[Phase 1b.4 PR4a 验收计划](../../../test-records/manual-tests/1b/2026-07-24_1b4-pr4a-acceptance-plan.md)
> **路线图**：PR4a（本次）→ PR4b（HomePage 重构 + pill 语义）→ PR5（checklist + v1.0 冻结）

---

## 0. 为什么单独 PR4a

PR0 原方案把 PR4 写成"13+1 条合 1 个 commit"。本计划**拆为 PR4a + PR4b**：

| 子 PR | 内容 | 原因 |
| --- | --- | --- |
| **PR4a（本次）** | UX 小修、文案、工具、favicon、title | 改动面小（~120 行），风险低，可单独 review |
| **PR4b（下一步）** | HomePage 614 行拆分 + PieChart 重构 + pill 语义 + sticky | 改动面大（~400 行），单测保护多，单独 review |

> PR2 主 commit `fe6a0a2` 误删 `.cat-detail` 块由 hotfix `0939928` 修，正是大 PR 单 commit 风险的典型案例。拆 PR4a/b 是该教训的直接响应。

---

## 1. 本 PR 范围（10 条）

按 review 报告 [`2026-07-23_phase1b3-design-bug-list.md`](../../checklists/2026-07-23_phase1b3-design-bug-list.md)：

| Bug ID | 严重度 | 一句话 | 关联文件 |
| --- | --- | --- | --- |
| HOME-009 | 🟢 P2 | `¥` 符号手工拼接间距偏小 | `utils/formatters.js` + 3 个卡片 |
| HOME-014 | ⚪ 备注 | "类内占比 100.00%" 是设计意图，防误删 | `HomePage.jsx` |
| GLOBAL-010 | 🟢 P2 | SPA 路由不更新 `document.title` | `App.jsx` |
| GLOBAL-016 | 🟢 P2 | 用原生 `alert()` | `DataPage.jsx` |
| GLOBAL-021 | 🟢 P2 | 缺少 favicon | `index.html` |
| DATA-002 | 🟢 P2 | 占位符"建议 0716 数据" | `DataPage.jsx:234` |
| DATA-005 | 🟢 P2 | "上传并解析" 文案不明确 | `DataPage.jsx:260` |
| DATA-010 | 🟢 P2 | 解析模式 `single/multi` 无说明 | `DataPage.jsx:244` |
| DATA-011 | 🟢 P2 | 技术术语泄漏到 UI | `DataPage.jsx:279` |
| DATA-012 | 🟢 P2 | modal 中"快照日期"卡位置不当 | `DataPage.jsx` + `data-page.css` |

---

## 2. DoD（PR4a 完成定义）

- [ ] 代码改动完成（见 §5 清单）
- [ ] 单元测试新增 / 改写完成（formatters withSymbol + App title）
- [ ] `npm --prefix fincontrol-frontend test -- --run` 全绿（34 → 预期 ~38）
- [ ] `npm --prefix fincontrol-frontend run build` 通过，无新增 React warning
- [ ] 累计/持有算法硬门禁：`AssetQueryServiceTest` + `CumulativeReturnCard` 测试**零修改**通过
- [ ] puppeteer 截图与 PR2 baseline 对比，差异**仅在** ¥ 间距（HOME-009 合理变化）
- [ ] 1 个 commit + push 到 origin/main
- [ ] 用户 review 通过 → 进入 PR4b

---

## 3. 明确不做（PR4a 范围内）

- ❌ 不重构 HomePage 子组件（留给 PR4b PERF-002/004）
- ❌ 不动 pie 图组件（PR4b PERF-002 处理）
- ❌ 不改 pill 颜色语义（PR4b HOME-015 处理）
- ❌ 不动 sticky 布局（PR4b HOME-012 处理）
- ❌ 不改 v1.0 冻结（PR5 处理）
- ❌ 不动 P1 设计 token（PR2 已完成）
- ❌ 不动业务逻辑（决策 27 / 累计持有 / snapshot_meta / 余额宝 fallback）
- ❌ 不引入新依赖

---

## 4. 实施步骤（6 步）

### Step 1 · `utils/formatters.js` 加 `withSymbol` 选项（HOME-009）

**文件**：`fincontrol-frontend/src/utils/formatters.js`

**改动**：扩展 `formatYuan` 接受 `opts.withSymbol`，true 时返回 `¥\u2009{body}`（U+2009 thin space）：

```js
export function formatYuan(n, opts = {}) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const body = Number(n).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  if (opts.withSymbol) return `¥\u2009${body}`
  return body
}
```

**测试**：`fincontrol-frontend/src/tests/utils/formatters.test.js`（如不存在则新建；不破坏现有 `friendlyError.test.js`）：

- `formatYuan(1234.5)` → `'1,234.50'`（默认行为不变）
- `formatYuan(1234.5, { withSymbol: true })` → `'¥\u20091,234.50'`（含 thin space）
- `formatYuan(null, { withSymbol: true })` → `'—'`
- `formatYuan(0, { withSymbol: true })` → `'¥\u20090.00'`
- `formatYuan(NaN, { withSymbol: true })` → `'—'`

### Step 2 · `App.jsx` 加路由 title（GLOBAL-010）

**文件**：`fincontrol-frontend/src/App.jsx`

**改动**：

```jsx
import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

const TITLES = {
  '/':           '首页 · FinControl',
  '/data':       '数据管理 · FinControl',
  '/config':     '资产配置 · FinControl',
  '/correction': '纠错页 · FinControl',
  '/nav':        '净值 · FinControl',
  '/ratio':      '比例 · FinControl',
  '/quarterly':  '季度 · FinControl',
  '/ai':         'AI 顾问 · FinControl',
}

export default function App() {
  const location = useLocation()
  useEffect(() => {
    document.title = TITLES[location.pathname] || 'FinControl'
  }, [location.pathname])
  // ... 现有布局
}
```

**测试**：新增 `fincontrol-frontend/src/tests/App.test.jsx`：

- 渲染 `<MemoryRouter initialEntries={['/']}>` → `document.title === '首页 · FinControl'`
- `MemoryRouter initialEntries={['/data']}` → `'数据管理 · FinControl'`
- `MemoryRouter initialEntries={['/unknown']}` → `'FinControl'`（fallback）
- 切换路由：`rerender(<MemoryRouter initialEntries={['/data']}>)` → title 更新

### Step 3 · `index.html` 加 favicon（GLOBAL-021）

**文件**：`fincontrol-frontend/index.html`

**改动**：在 `<head>` 内 `<title>` 之前加：

```html
<link rel="icon" type="image/svg+xml" href="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'><text y='52' font-size='52'>💰</text></svg>" />
```

emoji 内嵌 base64，无需新文件，无需测试。

### Step 4 · `DataPage.jsx` 6 项文案 + alert 替换 + modal header（DATA-002/005/010/011/012 + GLOBAL-016）

**文件**：`fincontrol-frontend/src/pages/DataPage.jsx`

**改动 1 · GLOBAL-016 / DataPage:73-90**（`handleFiles`）：

```jsx
function handleFiles(e) {
  const list = Array.from(e.target.files || [])
  if (list.length === 0) return
  if (list.length > 4) {
    setError(`最多 4 张图，当前选了 ${list.length} 张，请重新选择`)
    return   // ← 拒绝，不再静默截断
  }
  // ✅ PR1 修复：释放旧的 blob
  revokeAll(filePreviews)
  setFiles(list)
  setFilePreviews(list.map((f) => ({ name: f.name, url: URL.createObjectURL(f) })))
  setStep('idle')
  setError(null)
  setParsedAsset(null)
}
```

**改动 2 · DATA-002**：占位符文案：

```jsx
{filePreviews.length === 0 && (
  <div className="preview-empty">请选择 4 张支付宝基金截图</div>
)}
```

**改动 3 · DATA-005**：上传按钮加 `title`：

```jsx
<button
  onClick={uploadAndParse}
  title="先上传图片，再调用 AI 解析 19 只基金数据"
>
  {step === 'uploading' ? '上传中…' : step === 'parsing' ? '解析中…' : '上传并解析'}
</button>
```

**改动 4 · DATA-010**：解析模式下加帮助文字：

```jsx
<label>
  <span>解析模式</span>
  <select value={mode} onChange={(e) => setMode(e.target.value)} data-testid="mode-select">
    <option value="single">single（推荐，单图逐张）</option>
    <option value="multi">multi（4 图 batch）</option>
  </select>
  <small className="form-hint">
    <strong>single</strong>：逐张上传，失败可单独重试；<br />
    <strong>multi</strong>：4 张一次性发给 AI，速度快但失败需全部重试。
  </small>
</label>
```

**改动 5 · DATA-011**：去掉表名术语：

```jsx
<p className="hint">确认将今日资产数据写入历史记录</p>
```

**改动 6 · DATA-012**：快照日期挪到 modal-header 副标题：

```jsx
<div className="modal-header">
  <h2>📊 今日资产预览 · {snapshotDate}</h2>
  <button className="modal-close" onClick={onClose}>×</button>
</div>
<div className="overview-summary">  {/* 5 列 → 4 列 grid */}
  {/* 删除原快照日期 overview-card */}
  <div className="overview-card highlight">
    <div className="label">总资产（含余额类）</div>
    ...
  </div>
  {/* 其它 3 张卡 */}
</div>
```

**配套 CSS**：`fincontrol-frontend/src/styles/data-page.css`：

- 确认 `.overview-summary` grid-template-columns 从 `repeat(5, 1fr)` 改为 `repeat(4, 1fr)`
- 如 `.modal-header h2` 缺样式，补：`font-size: var(--text-lg); font-weight: 600;`

### Step 5 · `HomePage.jsx` 改 ¥ + 小计行 tooltip（HOME-009 + HOME-014）

**文件**：`fincontrol-frontend/src/pages/HomePage.jsx`

**改动 1 · HOME-009**：把 3 处 `¥ {formatYuan(...)}` 改用 `withSymbol: true`：

```jsx
<div className="hero-value">{formatYuan(totalAll, { withSymbol: true })}</div>
```

（其它两处：`formatYuan(sixTotal, { withSymbol: true })` / `formatYuan(balanceTotal, { withSymbol: true })`）

**改动 2 · HOME-014 ⚪**：六类小计行加 title tooltip：

```jsx
<tr className="subtotal-row">
  <td>{cat.categoryName}小计</td>
  <td>{formatYuan(cTotal)}</td>
  <td>{subtotalHolding == null ? '—' : formatSignedAmount(subtotalHolding)}</td>
  <td>{subtotalCumulative == null ? '—' : formatSignedAmount(subtotalCumulative)}</td>
  <td title="大类内部各基金持仓占比之和（恒为 100%）">100.00%</td>
</tr>
```

**注**：HOME-014 是 ⚪ 设计意图，**仅**加防误删 tooltip，不改 100.00% 数值。

### Step 6 · 3 个卡片 ¥ withSymbol 同步（HOME-009 视觉一致）

**文件**：
- `fincontrol-frontend/src/components/TotalAssetCard.jsx`
- `fincontrol-frontend/src/components/BalanceCard.jsx`
- `fincontrol-frontend/src/components/CumulativeReturnCard.jsx`

**改动**：扫 `¥ {formatYuan(...)}` 模式，改为 `{formatYuan(..., { withSymbol: true })}`。

> 注：累计收益 `formatSignedAmount(...)` 是带正负号的，不加 ¥。只改"金额"类（持仓金额、总资产、六大类合计、余额类合计），不改"收益率"。

---

## 5. 代码改动清单

| 文件 | 性质 | 行数预估 |
| --- | --- | --- |
| `src/utils/formatters.js` | 改：formatYuan 增 withSymbol | +5 / 0 |
| `src/App.jsx` | 改：useLocation + useEffect + TITLES | +22 / 0 |
| `src/index.html` | 改：favicon link | +1 / 0 |
| `src/pages/DataPage.jsx` | 改：6 项 + alert 替换 + modal header | +18 / -8 |
| `src/pages/HomePage.jsx` | 改：3 处 ¥ + 1 处 title | +3 / -3 |
| `src/components/TotalAssetCard.jsx` | 改：¥ withSymbol | +1 / -1 |
| `src/components/BalanceCard.jsx` | 改：¥ withSymbol | +1 / -1 |
| `src/components/CumulativeReturnCard.jsx` | 改：¥ withSymbol（如有） | +1 / -1 |
| `src/styles/data-page.css` | 改：grid 4 列 + modal-header h2 | +8 / -2 |
| `src/tests/utils/formatters.test.js` | 改/新增：withSymbol 用例 | +25 / 0 |
| `src/tests/App.test.jsx` | 新增 | ~35 |

**总 diff**：~120 行（含测试）

---

## 6. 验证清单

### 6.1 自动化

- `npm --prefix fincontrol-frontend test -- --run` 全绿
- `npm --prefix fincontrol-frontend run build` 通过
- `mvn -f fincontrol-backend/pom.xml test -DfailIfNoTests=false` smoke test 通过

### 6.2 累计/持有硬门禁

- `AssetQueryServiceTest` 全部 pass（PR1 已设，本 PR 不触碰）
- `CumulativeReturnCard` 现有测试全部 pass（PR1 已设，本 PR 仅调容器布局不变）

### 6.3 视觉验证（puppeteer）

| 检查项 | 期望 |
| --- | --- |
| 首页 `/` 总资产卡 | `¥\u2009{金额}` 间距均匀 |
| 首页 `/` 六大类合计卡 | 同上 |
| 首页 `/` 余额类合计卡 | 同上 |
| 首页 `/` 六类小计行 | hover 100.00% 显示 tooltip |
| 首页 `/` 浏览器 tab | 💰 favicon + 标题 `首页 · FinControl` |
| `/data` 浏览器 tab | `数据管理 · FinControl` |
| `/data` 占位符 | `请选择 4 张支付宝基金截图`（无 0716） |
| `/data` 上传按钮 | hover tooltip "先上传图片..." |
| `/data` 解析模式 | 下方 form-hint 文字说明 single/multi |
| `/data` 确认入库 modal | header `📊 今日资产预览 · {日期}`；4 列 grid（无日期卡） |
| `/data` 上传 5 张图 | setError 红字提示，不再 alert |

### 6.4 浏览器 console

- 无红色 error / warning

---

## 7. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| `withSymbol` 调用方遗漏 | 全仓 grep `¥ \{formatYuan` 确认 6 处全部替换；Step 6 卡片同步 |
| TITLES 路由不全 | 8 个路由；其余 fallback `FinControl`（用户能看到是 SPA）|
| favicon emoji 在某些 UA 不渲染 | emoji tab icon 是渐进增强；vite 默认 favicon 作为降级 |
| DATA-012 modal-header h2 样式缺 | data-page.css PR2 已统一 `.modal-header`，但需补 h2 字号 |
| HOME-014 tooltip 文案长 | 中等长度（22 字），hover 时浏览器原生 tooltip 自动换行 |
| App.test.jsx 用 MemoryRouter | PR1 已装 `@testing-library/react` + `react-router-dom`，可直接复用 |

---

## 8. 提交计划

1 个 commit：

```
fix(1b.4-PR4a): P2 UX 小修 10 条（HOME-009/014 + GLOBAL-010/016/021 + DATA-002/005/010/011/012）

- HOME-009 formatYuan(n, { withSymbol: true }) thin space；3 卡片同步
- HOME-014 ⚪ 六类小计行加 title tooltip（防误删 100.00%）
- GLOBAL-010 App.jsx 路由切换 document.title
- GLOBAL-016 DataPage handleFiles > 4 张改 setError，移除 alert
- GLOBAL-021 index.html emoji 💰 favicon
- DATA-002 占位符"建议 0716 数据" → "请选择 4 张支付宝基金截图"
- DATA-005 上传按钮加 title tooltip
- DATA-010 解析模式加 form-hint
- DATA-011 技术术语 asset_raw 等 → "今日资产数据写入历史记录"
- DATA-012 快照日期挪到 modal-header 副标题，grid 5 列 → 4 列

测试：formatters withSymbol 6 case + App.test.jsx 4 case（路由/title/fallback）

参考 docs/phase-1/checklists/2026-07-23_phase1b3-design-bug-list.md
参考 docs/phase-1/work-plans/1b/2026-07-24_1b4-pr0-work-plan.md §4.5
```

push → 用户 review → 进入 PR4b。

---

## 9. 后续 PR 衔接

| PR | 内容 | 工期 | 累计 |
| --- | --- | --- | --- |
| PR4a（本次）| 10 条 UX 小修 | 0.5 天 | 0.5 |
| PR4b | PERF-002/004 + HOME-012 + HOME-015 | 1 天 | 1.5 |
| PR5 | checklist 同步 + review 报告追踪 + v1.0 冻结 | 0.5 天 | 2.0 |

PR4b 范围：拆分 HomePage 614 行 → components/home/、删除内联 PieChart 改用 SixCategoriesPie、HOME-012 sticky、HOME-015 pill 三档语义（pill-ok/warn/action）。

PR5 范围：`phase-1b.md` 同步 + `2026-07-23_phase1b3-design-bug-list.md` 末尾加修复追踪表 + `README.md` 进度章节 + `requirements v0.1-DRAFT → v1.0` 冻结。

---

## 10. 引用

- 1b.3 设计 review 报告：[docs/phase-1/checklists/2026-07-23_phase1b3-design-bug-list.md](../../checklists/2026-07-23_phase1b3-design-bug-list.md)
- 1b.4 PR0 阶段定义：[docs/phase-1/work-plans/1b/2026-07-24_1b4-pr0-work-plan.md](./2026-07-24_1b4-pr0-work-plan.md)
- 1b.4 PR2 验收报告：[docs/test-records/manual-tests/1b/2026-07-24_1b4-pr2-acceptance-report.md](../../../test-records/manual-tests/1b/2026-07-24_1b4-pr2-acceptance-report.md)
- 联调记录（gitignored draft）：[test/1b/2026-07-24-1b4-pr4a-联调记录.md](../../../../test/1b/2026-07-24-1b4-pr4a-联调记录.md)

---

*完成时间：待用户 review 后 commit*
*commit HEAD：待定*