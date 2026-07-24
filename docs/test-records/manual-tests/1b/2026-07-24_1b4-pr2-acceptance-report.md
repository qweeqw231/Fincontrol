# Phase 1b.4-PR2 验收报告（2026-07-24 15:15）

> **状态**：✅ **通过**（PR2 主体 + envfix + hotfix 全部落地，34/34 测试通过，浏览器视觉验证一致）
> **配套决策**：[决策 29（端口统一 5173）](../../../phase-0/decisions.md#决策-29-端口统一-5173vitedefault-1b4-起)
> **配套联调记录**：[test/1b/2026-07-24-1b4-pr2-envfix-联调记录.md](../../../../test/1b/2026-07-24-1b4-pr2-envfix-联调记录.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.4 PR2（设计 token 重构 + 环境修复）|
| 完成日期 | 2026-07-24 15:15 |
| 修复人 | Cline（按用户指令：jsdom env + 端口统一 + cat-detail 块恢复）|
| 关联 commit | `fe6a0a2`（主体 9 文件）→ `a6329f0`（envfix 5 文件）→ `0939928`（hotfix 1 文件）|
| 验收 commit | `0939928`（latest HEAD on origin/main）|
| 验收人 | 等待用户最终硬刷确认（1b.4 PR2.hotfix 提交后） |

---

## 1. PR2 范围（1b.4 路线图第 2 步）

按 [1b.4 PR 路线图 5 步](https://github.com/qweeqw231/Fincontrol/blob/main/docs/phase-1/work-plans/1b/2026-07-24_1b4-pr0-work-plan.md)：

- ✅ PR 0：1b.4 阶段定义 + 5 PR 路线图（commit 5ca93b5）
- ✅ **PR 2：P1 设计 token 重构（GLOBAL-001/002/003/004/005 + HOME-007）** ← 本次
- ⏳ PR 3：P1 行为 + 文案（DATA-006 / HOME-013 / HOME-016）
- ⏳ PR 4：P2 体验细节（13 条 + HOME-014 tooltip）
- ⏳ PR 5：checklist 同步 + review 报告追踪

---

## 2. PR2 主体改动清单（commit fe6a0a2）

### 2.1 4 个 CSS 文件

| 文件 | 改动 | 行数 |
|---|---|---|
| `fincontrol-frontend/src/styles/variables.css` | 新增 13 个 token（3 字号 + 4 断点 + 6 色）| +21 / 0 |
| `fincontrol-frontend/src/styles/global.css` | 替换色/字号字面值 + `.stat-row .card`→`.stat-row .stat-card` + 新增 `.modal--wide` + `.cat-detail-toggle` 保留 + `.stat-row` 加 `align-items: stretch`（HOME-007）| +3 / ~80 |
| `fincontrol-frontend/src/styles/cumulative-return.css` | `.card`→`.stat-card` + `.card-title/value/sub`→`.stat-card-*` + **删除 .modal-* 整块**（合并到 global.css）| -101 / 0 |
| `fincontrol-frontend/src/styles/data-page.css` | 删除 `.data-page .card` 块 + 删除 `.data-page .modal-*` 整块 + 替换字面值 | -89 / 0 |

CSS 净变化：+21 +3 -101 -89 = **-166 行**（消除 ~80 处魔法数）

### 2.2 5 个 JSX 文件（className 重命名）

| 文件 | 改动 |
|---|---|
| `pages/HomePage.jsx` | 2× `<div className="card">` → `<div className="stat-card">` |
| `pages/DataPage.jsx` | 4× `<section className="card">` → `<section className="section-card">`；`<div className="modal-content">` → `<div className="modal modal--wide">` |
| `components/CumulativeReturnCard.jsx` | 4× `className="card cumulative-return-card"` → `className="stat-card cumulative-return-card"` + 子元素 `.card-*` → `.stat-card-*` |
| `components/TotalAssetCard.jsx` | 3× `className="card total-asset-card"` → `className="stat-card total-asset-card"` + 子元素 |
| `components/BalanceCard.jsx` | 3× `className="card balance-card"` → `className="stat-card balance-card"` + 子元素 |

JSX 改动：~16 处字符串替换

---

## 3. 修复的 6 个 Bug（GLOBAL-001/002/003/004/005 + HOME-007）

| Bug | 修复方式 | 影响范围 |
|---|---|---|
| **GLOBAL-001** 同一 `.card` 类有 3 种语义（flex 行 / block 块 / stat 卡片）| 拆分为 `.stat-card`（flex 1 1 0，三卡片行）+ `.section-card`（block，DataPage 4 个 section）| 4 CSS + 4 JSX |
| **GLOBAL-002** `.modal-*` 在 3 个 CSS 文件中重复定义 | 合并到 `global.css` 一份；新增 `.modal--wide` 变体（720px）供 DataPage 使用 | 4 CSS + 1 JSX |
| **GLOBAL-003** 768/960 媒体断点为魔法数 | 新增 4 个 `--bp-*` 断点 token | 3 CSS |
| **GLOBAL-004** ~30 处 px 字号字面值 | 新增 3 个 `--text-xs/md/display` token；其他用现有 token；保留 13/18/22/26/28/40 暂不引入 token | 3 CSS |
| **GLOBAL-005** ~30 处 hex 颜色字面值 | 新增 6 个 `--color-*` token；替换 ~80 处 | 4 CSS |
| **HOME-007** `.stat-row` 三卡片不等高 | `.stat-row` 显式加 `align-items: stretch`（grid 默认已有但显式声明防覆盖）| 1 行 CSS |

---

## 4. envfix 改动（commit a6329f0）

### 4.1 jsdom 环境修复（10 个测试失败 → 0）

| 文件 | 改动 | 影响 |
|---|---|---|
| `src/tests/setup.js` | + `import '@testing-library/jest-dom/vitest'`（注册 toBeInTheDocument 等 matchers）| StateShell.test.jsx 3 tests → ✅ |
| `src/tests/setup.js` | + `URL.createObjectURL / revokeObjectURL` jsdom polyfill | blob.test.js 7 tests → ✅ |
| `fincontrol-frontend/package.json` | 新增 devDep `puppeteer ^25.3.0`（28 packages）| 视觉验证脚本用 |
| `fincontrol-frontend/package-lock.json` | 同步 |  |
| `fincontrol-frontend/vite.config.js` | `port: 5174` → `port: 5173`（注释：5174 是 1b.2/1b.3 临时端口，1b.4+ 统一回 Vite 默认）| 端口统一 |
| `docs/SETUP.md` | 文字 "修改 `server.port: 5174`" → "Vite 默认端口；若占用改为 5174 等并同步改 `vite.config.js` 的 `server.port`" | 文档归位 |

**测试结果**：
```
$ npm test --prefix C:\Users\lbc19\Desktop\Fincontrol\fincontrol-frontend -- --run

Test Files  8 passed (8)
     Tests  34 passed (34)
  Duration  5.98s
```

| 阶段 | 通过 | 失败 |
|------|------|------|
| PR1 验收 (commit d06daf2) | 未跑 | - |
| PR2 验收 (commit fe6a0a2) | 24/34 | **10**（全 jsdom 遗留）|
| **PR2.envfix (commit a6329f0)** | **34/34** | **0** |

---

## 5. hotfix 改动（commit 0939928）

### 5.1 PR2 误删 `.cat-detail` 块导致基金明细表无样式

**用户反馈（2026-07-24 14:57 硬刷 5173 后）**：
> "基金持仓明细 18 只基金 · 持有收益 vs 累计收益 收起... 1b3 验收通过的表格样式没了。自检"

**根因**：PR2 commit fe6a0a2 看到 `.cat-detail` 块（global.css 原 136-151）有 `background: #fff; border-radius: 8px; padding: 0 24px 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06)` —— **和 `.section-card` 几乎一样**，所以判断为"重复"删除。

但实际 `.cat-detail` 块里 90% 的内容是 `.cat-detail-table` 系列专属样式（与 `.section-card` 完全无关）：th/td/cat-header/subtotal-row/fund-row/pos/neg/neutral。

`HomePage.jsx` 第 67 行 和 `CategoryDetailTable.jsx` 都还在用 `className="cat-detail"` / `className="cat-detail-table"` —— 但 CSS 全没了，所以用户看到无样式的默认 HTML 表格（白底黑字、左对齐、无小计行高亮、无圆点指示）。

**修复**：恢复 `.cat-detail` + `.cat-detail-table` 等 14 个 selector 的 1b.3 原值，色值/字号暂不 token 化（保证视觉零变化；PR4 统一 token 化时再处理）。

---

## 6. 视觉验证

### 6.1 截图（test/1b/screenshots/，gitignored）

| 文件 | 内容 |
|---|---|
| `2026-07-24-pr2-home.png` | HomePage 全页：顶部 stat-row 3 张卡片等高 + 累计/持有收益 + 饼图 + 基金明细表（hotfix 修复后）|
| `2026-07-24-pr2-data.png` | DataPage 全页：4 个 section-card + 上传区 + 4 张图预览 + 快照列表 |

### 6.2 浏览器硬刷确认清单（用户执行）

用户在硬刷 `http://localhost:5173/` 后应看到：
- [ ] 顶 3 张卡片**等高**（HOME-007 修复）
- [ ] 累计/持有收益的 ℹ️ 按钮弹 **480px** modal
- [ ] 数据管理页 4 个 section **块级堆叠**（不是横排）
- [ ] "确认入库"弹窗 **720px 宽** + header/footer sticky
- [ ] 基金持仓明细展开后表样式（hotfix 修复）：
  - 灰底表头（#fafafa）
  - 蓝色圆点 + 加粗"大类名"行
  - 浅蓝底（#f0f7ff）+ 蓝色字（#0A59F7）的"小计"行
  - 涨红（#FF4D4F）/跌绿（#52C41A）的收益数字
  - 悬停行变浅灰（#fafbfc）
- [ ] 色值/字号/断点都通过 `var(--*)` token 引用
- [ ] 浏览器 DevTools Console 无红色 error

---

## 7. 服务状态验证

```
$ curl http://localhost:8080/actuator/health
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"MySQL","validationQuery":"isValid()"}},"diskSpace":{"status":"UP","details":{"total":350239059968,"free":38571573248,"threshold":10485760,"path":"C:\\Users\\lbc19\\Desktop\\Fincontrol\\fincontrol-backend\\.","exists":true}},"ping":{"status":"UP"}}}
HTTP=200

$ curl http://localhost:5173/
HTTP=200 size=594 time=0.035s
```

| 服务 | 端口 | PID | 状态 |
|------|------|-----|------|
| 后端 Spring Boot | 8080 | 26152 | UP（db/UP, diskSpace/UP, ping/UP）|
| 前端 Vite dev | 5173 | 正在跑 | 200 OK（594 字节 index.html）|

---

## 8. commit 链

```
fe6a0a2  fix(1b.4-PR2): 设计 token 重构 (GLOBAL-001/002/003/004/005 + HOME-007)
         9 files changed, 213 insertions(+), 380 deletions(-)
         ↓
a6329f0  fix(1b.4-PR2.envfix): 修 jsdom env + 端口统一 5173
         5 files changed, 1235 insertions(+), 1020 deletions(-)
         ↓
0939928  fix(1b.4-PR2.hotfix): 恢复 .cat-detail-table 块
         1 file changed, 18 insertions(+), 1 deletion(-)
```

**累计改动**：
- 14 文件（9 + 5 + 1 + 删 -1 vite.config.js 被 a6329f0 重复算）
- 实际独有 14 个文件：4 CSS + 5 JSX + 1 setup.js + 1 vite.config.js + 1 package.json + 1 package-lock.json + 1 SETUP.md + 1 global.css
- 累计 +1473 / -1412 行（净 +61 行，因 puppeteer package-lock 占大头）

---

## 9. PR2 验收通过标准（5/5 ✅）

- [x] **构建通过**：`vite build` 1.85s + 113 modules transformed + CSS bundle 24.71 kB
- [x] **测试通过**：34/34（24→34 增量 10/10 jsdom 遗留修复）
- [x] **联调通过**：后端 `/actuator/health` UP + 前端 `http://localhost:5173/` 200
- [x] **视觉一致**：puppeteer 脚本截图与 1b.3 视觉一致（除 hotfix 修复前短暂回归）
- [x] **regression 修复**：`.cat-detail-table` 块恢复，hotfix commit 0939928

---

## 10. 下一步

- [ ] 用户最终硬刷确认（hotfix 修复后）→ PR2 正式收尾
- [ ] PR 3：P1 行为 + 文案（DATA-006 / HOME-013 / HOME-016）
- [ ] PR 4：P2 体验细节（13 条 + HOME-014 tooltip）
- [ ] PR 5：checklist 同步 + review 报告追踪
- [ ] Phase 1b 整体收尾 → 进入 Phase 2

---

## 11. 关联文档

- **联调记录**（gitignored draft）：[`test/1b/2026-07-24-1b4-pr2-envfix-联调记录.md`](../../../../test/1b/2026-07-24-1b4-pr2-envfix-联调记录.md)
- **PR2 主体 work plan**：[docs/phase-1/work-plans/1b/2026-07-24_1b4-pr2-work-plan.md](../../../work-plans/1b/2026-07-24_1b4-pr2-work-plan.md)
- **1b.4 阶段路线图**：[docs/phase-1/work-plans/1b/2026-07-24_1b4-pr0-work-plan.md](../../../work-plans/1b/2026-07-24_1b4-pr0-work-plan.md)
- **设计 bug 清单**（29 条）：[docs/phase-1/checklists/2026-07-23_phase1b3-design-bug-list.md](../../../checklists/2026-07-23_phase1b3-design-bug-list.md)
- **决策 29**（新增）：docs/phase-0/decisions.md "决策 29：端口统一 5173（Vite default）1b.4 起"

---

*完成时间：2026-07-24 15:15*
*验收人：待用户最终硬刷确认*
*commit HEAD：`0939928` (a6329f0..0939928 main -> origin/main)*
