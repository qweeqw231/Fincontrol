# Phase 1b.4 PR0 验收计划（文档门禁）

> **状态**：📝 待执行；PR 0 不动代码，仅验收两份 markdown 文档
> **日期**：2026-07-24
> **需求基线**：[FinControl 全站页面需求说明书](../../../requirements/2026-07-22_fincontrol-page-requirements.md)（v0.1-DRAFT，**本 PR 不冻结 v1.0**）
> **工作计划**：[Phase 1b.4 PR0 工作计划](../../../phase-1/work-plans/1b/2026-07-24_1b4-pr0-work-plan.md)
> **配套 review 报告**：[Phase 1b.3 设计 review 报告](../../../checklists/2026-07-23_phase1b3-design-bug-list.md)

---

## 0. 验收原则

1. **文档门禁先行**：PR 0 不动代码，只验收两份 markdown 文档（工作计划 + 验收计划）的完整性、内部一致性、与 review 报告的可追踪性。
2. **保护边界可验证**：每条"明确不做"都对应到具体代码 / 决策文档的引用，未来 PR 不得违反。
3. **28 条 bug 全映射**：review 报告里 28 条 ID + 1 条 ⚪ 备注必须在工作计划里出现，且每条归属到一个明确的 PR。
4. **签字定稿**：用户作为唯一 owner 直接 approve，无需产品评审会。

---

## 1. 验收层级与状态

| 层级 | 范围 | 证据 |
| --- | --- | --- |
| A. 文档完整性 | 工作计划 + 验收计划 结构、字段、范围护栏 | 文档自检 + 用户 review |
| B. 与 review 报告可追踪 | 28 条 ID → 5 个 PR 的映射 | 工作计划 §3 表格 |
| C. 与 Phase 1b checklist 可关联 | 阶段命名、退出条件 | 工作计划 §10 + 后续 PR 5 同步 |
| D. 范围护栏可验证 | 决策 27 / 累计持有算法 / snapshot_meta / 余额宝 fallback 不被触碰 | 工作计划 §2 + §7 风险表 |
| E. 硬门禁可执行 | 每个 PR 的退出条件 + 单元测试要求 | 工作计划 §8 |

状态标识：`☐ 未执行`、`◐ 部分通过`、`☑ 通过`、`✗ 失败`、`N/A 本阶段不适用`。

---

## 2. PR 0 文档门禁验收

### 2.1 工作计划验收

| ID | 验收项 | 期望 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| WP-001 | 顶部 status / 日期 / 前置 / 配套 / 后续 PR / 冻结时机齐全 | 6 项元数据齐全 | ☐ | |
| WP-002 | §0 为什么需要本阶段 | 引用 review 报告 + 4 类问题归纳 + 用户决策原话 | ☐ | |
| WP-003 | §1 阶段目标（DoD 整体） | 6 项目标包含需求冻结 + 28 条全修 + token 收敛 + 零回归 + 测试 + README | ☐ | |
| WP-004 | §2 明确不做 | ≥ 10 条护栏，覆盖决策 27 / 累计持有 / 1b.5 / 1b.6 / Phase 2/3/5 / 0716 / API / 依赖 | ☐ | |
| WP-005 | §3 已确认问题与根因矩阵 | 28 条 ID + 1 条 ⚪ 备注全部列出；每条标注严重度 + 归属 PR | ☐ | |
| WP-006 | §4 实施门禁与 PR 顺序 | PR 0-5 各自任务表 + DoD | ☐ | |
| WP-007 | §5 需求—工作包追踪矩阵 | 每个需求 ID 对应到一个工作包或标注"硬保护" | ☐ | |
| WP-008 | §6 代码影响面预估 | 前端文件清单 + 后端"不触碰" + 文档清单 | ☐ | |
| WP-009 | §7 风险与缓解 | ≥ 7 条风险，覆盖 token 重构 / StateShell / 累计持有 / 决策 27 / 用户中途暂停 / 上下文窗口 | ☐ | |
| WP-010 | §8 退出条件 | 12+ 项 checkbox，含累计持有 / snapshot_meta 两个硬门禁 | ☐ | |
| WP-011 | §9 时间表 | 6 行 PR + 累计工期 + 不含 1b.5/1b.6 | ☐ | |
| WP-012 | §10 命名约定 | 1b.4 重定义 + 1b.5/1b.6 顺延 | ☐ | |

### 2.2 验收计划验收

| ID | 验收项 | 期望 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| AC-001 | 顶部 status / 日期 / 需求基线 / 工作计划 / review 报告 链接齐全 | 5 项元数据齐全 | ☐ | |
| AC-002 | §0 验收原则 | ≥ 4 条原则 | ☐ | |
| AC-003 | §1 验收层级 | 5 层（A 文档 / B 追踪 / C 关联 / D 护栏 / E 硬门禁） | ☐ | |
| AC-004 | §2 PR 0 文档门禁验收 | WP-001~012 对应的 12 项验证 | ☐ | |
| AC-005 | §3 PR 1-5 验收规划 | 每个 PR 至少 5 项验证项 + 单元测试要求 | ☐ | |
| AC-006 | §4 浏览器功能验收 | 首页 / 数据页 / AI 顾问三路径 | ☐ | |
| AC-007 | §5 视觉验收 | ≥ 12 项 VIS-001~012 矩阵 | ☐ | |
| AC-008 | §6 累计/持有收益成熟逻辑保护验收 | 8 项硬门禁 | ☐ | |
| AC-009 | §7 需求追踪矩阵 | 需求 ID ↔ PR ↔ 验收章节 | ☐ | |
| AC-010 | §8 回归与最终命令 | mvn / npm / build / curl 4 类命令齐全 | ☐ | |
| AC-011 | §9 最终退出条件与签字 | ≥ 10 项 checkbox + 产品 / 实施 双签字行 | ☐ | |

### 2.3 与 review 报告的映射验收

| ID | 验收项 | 期望 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| MAP-001 | HOME-001 / HOME-002 / HOME-003（P0 三条）映射到 PR 1 | 表格中出现且标注归属 PR 1 | ☐ | |
| MAP-002 | GLOBAL-001/002/003/004/005 + HOME-007（P1 token 五条）映射到 PR 2 | 表格中出现 | ☐ | |
| MAP-003 | DATA-006 / HOME-013 / HOME-016（P1 行为三条）映射到 PR 3 | 表格中出现 | ☐ | |
| MAP-004 | 13 条 P2 + 1 条 ⚪ HOME-014 映射到 PR 4 | 表格中出现且 HOME-014 标注"防误删保护" | ☐ | |
| MAP-005 | DATA-001 / DATA-002 在 review 报告中的 P2 标注 | 表格中 DATA-002 标注 P2 在 PR 4；DATA-001 标注 P2 业务允许 | ☐ | |
| MAP-006 | ⚪ HOME-014 用户设计意图说明完整 | 计划文档中保留用户原话"显式标注 / 视觉对齐 / 不显漏数据" | ☐ | |

---

## 3. PR 1-5 验收规划（每个 PR 一个 session 单独执行）

> 每个 PR 完成时按对应章节执行。本节是规划大纲，详细验收项在每个 PR 的 session 内独立成文。

### 3.1 PR 1 验收（P0 三条 + GLOBAL-007）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-001 | 直接打开 `/` 刷新立即看到首页数据 | 浏览器手动：刷新后 < 500ms 显示 | ☐ |
| PR1-002 | 首次访问无数据时空态有"立即上传"按钮 + 点击跳转 `/data` | 浏览器手动 + Playwright | ☐ |
| PR1-003 | 上传 4 张图后 DevTools Memory 中 blob 数量稳定为 4 | DevTools Memory 录制 5 次上传/删除循环 | ☐ |
| PR1-004 | `useEffect` cleanup 卸载时释放所有 blob URL | vitest 模拟 unmount | ☐ |
| PR1-005 | StateShell 三态视觉一致（loading / error / empty） | 浏览器手动 + 截图对比 | ☐ |
| PR1-006 | friendlyError() 不暴露 axios stack / code | vitest 单元测试 | ☐ |
| PR1-007 | 累计/持有四值与 API 原值一致（保护） | `CumulativeReturnCard` 现有测试全绿 + 视觉对比 | ☐ |
| PR1-008 | vitest 全绿 | `npm --prefix fincontrol-frontend test -- --run` 通过 | ☐ |
| PR1-009 | vite build 通过 | `npm --prefix fincontrol-frontend run build` 通过 | ☐ |

### 3.2 PR 2 验收（P1 token 五条 + HOME-007）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR2-001 | `variables.css` 新增 ≥ 4 个 token 组（业务色 / 设计色 / 字号 / 断点） | 文件审查 | ☐ |
| PR2-002 | 全项目 CSS 中 `#0A59F7` 等字面量 → `var(--color-primary)` | grep 验证字面量归零 | ☐ |
| PR2-003 | `.card` 拆为 `.stat-card` + `.section-card`，三处 CSS 不再有 `.card` 类冲突 | DOM class 名审查 | ☐ |
| PR2-004 | `.modal` base + variants 合并为一套，CumulativeReturnCard 弹窗和 DataPage 入库预览视觉一致 | 截图对比 | ☐ |
| PR2-005 | 字号字面量（15 / 26 / 44 等）改 `var(--text-md/display)` | grep 验证 | ☐ |
| PR2-006 | 断点 768 / 960 改 `var(--bp-tablet/desktop)` | grep 验证 | ☐ |
| PR2-007 | 首页三卡片等高 | 截图对比（浏览器宽度 1280 / 960 / 768 三档） | ☐ |
| PR2-008 | CumulativeReturnCard 内部业务代码未触碰（累计持有算法保护） | `AssetQueryServiceTest` 全绿 + `CumulativeReturnCard` 测试全绿 | ☐ |
| PR2-009 | vitest 全绿 | npm test 通过 | ☐ |

### 3.3 PR 3 验收（P1 行为 + 文案三条）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR3-001 | `confirm` 成功后 1.5s 内 step 重置为 idle | vitest 集成测试 + 浏览器手动 | ☐ |
| PR3-002 | DataPage 章节标题数组化（DOM 中不再有 `<h2>1. ... <h2>2. ...` 等硬编码） | DOM 审查 | ☐ |
| PR3-003 | 数据口径说明卡无 `v1.0-DRAFT` 标签 | 浏览器审查元素 | ☐ |
| PR3-004 | 数据口径说明卡无 `<code>` 标签里的技术术语（snapshot_meta.is_current / 0.00 / — 等） | DOM 审查 | ☐ |
| PR3-005 | vitest 全绿 | npm test 通过 | ☐ |

### 3.4 PR 4 验收（P2 体验细节 + ⚪ 防误删保护）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR4-001 | `formatYuan(n, { withSymbol: true })` thin space 间距均匀 | 视觉对比 hero / cards | ☐ |
| PR4-002 | 路由切换 SPA `document.title` 跟随 | 浏览器手动 | ☐ |
| PR4-003 | 全项目无 `alert()` 调用（grep 验证） | grep 验证 | ☐ |
| PR4-004 | 浏览器 tab 有 favicon（💰 图标） | 浏览器 tab 截图 | ☐ |
| PR4-005 | HomePage 内联 PieChart 已删除，改用 `components/SixCategoriesPie.jsx` | git diff 审查 | ☐ |
| PR4-006 | HomePage 文件行数从 614 → ≤ 250 行 | wc -l fincontrol-frontend/src/pages/HomePage.jsx | ☐ |
| PR4-007 | components/home/ 5 个子组件文件创建 | ls 检查 | ☐ |
| PR4-008 | 数据口径卡 desktop sticky | 浏览器手动长滚动 | ☐ |
| PR4-009 | pill 三档语义（ok/warn/action）+ emoji | 截图对比 | ☐ |
| PR4-010 | DataPage 占位符文案无 `0716` | DOM 审查 | ☐ |
| PR4-011 | DataPage 文案 4 项优化（按钮 / 模式 / 术语 / 卡位置） | DOM 审查 + 视觉对比 | ☐ |
| PR4-012 | ⚪ 100% 占位有 `title` tooltip 提示"大类内部子资产占比之和（恒为 100%）" | 浏览器 hover 验证 | ☐ |
| PR4-013 | vitest 全绿 | npm test 通过 | ☐ |

### 3.5 PR 5 验收（checklist 同步 + review 报告追踪）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR5-001 | `docs/phase-1/checklists/phase-1b.md` 含 1b.4 段落 + 新阶段命名（1b.5/1b.6） | 文件审查 | ☐ |
| PR5-002 | `docs/phase-1/checklists/2026-07-23_phase1b3-design-bug-list.md` 末尾含"修复追踪表"（每条 ID × PR × commit SHA） | 文件审查 | ☐ |
| PR5-003 | `README.md` 进度章节同步（1b.4 完成 / 1b.5 待启动） | 文件审查 | ☐ |
| PR5-004 | git log 显示 6 个 commit（PR 0-5） | `git log --oneline -6` | ☐ |
| PR5-005 | origin/main 已包含 1b.4 全部 6 个 commit | `git log origin/main --oneline -6` | ☐ |

---

## 4. 累计/持有收益成熟逻辑保护验收（硬门禁，跨所有 PR）

任何 PR 失败都不得以"视觉已完成"验收。

### 4.1 数据语义不变

| 展示格 | API 字段 | 现行语义 | 验收 |
| --- | --- | --- | --- |
| 累计收益率 | `returnRate` | `Σ cumulativeProfit / Σ amount`，现行口径 | 与 API 一致 |
| 持有收益率 | `holdingReturnRate` | `Σ holdingProfit（含现行余额宝调整）/ Σ amount` | 与 API 一致 |
| 累计收益额 | `totalCumulativeProfit` | 当前口径累计收益合计 | 与 API 一致 |
| 持有收益额 | `totalHoldingProfit` | 当前口径持有收益合计 | 与 API 一致 |

- [ ] PR 2/4 中禁止修改 `CumulativeReturnCard` 内部业务代码（仅允许调整容器布局）；
- [ ] `phase1_simple` 标识保留；
- [ ] `snapshotDate` 和 `fundCount` 保留；
- [ ] `rawHoldingProfit` / `balanceFundAdjustment` / `balanceFundStatus` 现行处理不变；
- [ ] `status=normal/included/excluded_unknown` 的 Info 文案分支不变；
- [ ] 四个 Info 入口均可打开和关闭；
- [ ] 旧的 `AssetQueryServiceTest` 和 `CumulativeReturnCard` 相关 fixture 全部通过。

### 4.2 布局硬保护

桌面端必须可读为以下 2×2 矩阵，而非四段无标注数字：

|  | 累计 | 持有 |
| --- | --- | --- |
| 收益率 | 值 + Info | 值 + Info |
| 收益额 | 值 + Info | 值 + Info |

窄屏可改为两列缩放或两组纵向排列，但四个语义标签不得消失。

---

## 5. snapshot_meta 双层语义保护（硬门禁）

任何 PR 都不得触碰：

- ❌ `snapshot_meta` 写入路径；
- ❌ `is_current` 权威源；
- ❌ `set-current` 业务逻辑；
- ❌ confirm 事务中的双层语义。

验证方法：决策 27（`docs/phase-1/decisions/decision-27-is-latest-dual-layer.md`）相关测试套件全绿。

---

## 6. 浏览器功能验收（1b.4 整体完成后，PR 5 之后）

### 6.1 首页

1. 打开 `/`，等待加载完成；
2. 验证 Hero + 三张卡片 + 左图右表 + 折叠明细顺序正确；
3. 对照 API 验证金额、日期、收益四值；
4. 验证刷新后立即看到数据（HOME-001 修复）；
5. 验证空态有"立即上传"按钮（HOME-002 修复）；
6. 验证三态视觉一致（HOME-003 修复）；
7. 验证累计/持有卡片 4 个 Info 弹窗可正常打开（成熟逻辑保护）；
8. 验证 pill 三档语义正确（HOME-015 修复）；
9. 验证数据口径卡无 `v1.0-DRAFT` 标签和无技术术语（HOME-013 修复）；
10. 验证 100% 占位有 tooltip（HOME-014 防误删保护）；
11. 模拟一个辅助 API 失败，确认主快照仍显示 + 错误不暴露 axios stack（GLOBAL-007）。

### 6.2 数据管理

1. 选择 4 张图，验证 n/4 和缩略图；
2. 验证上传 → 删除 → 重新上传 5 次后 DevTools Memory 中 blob 数量稳定（GLOBAL-015 修复）；
3. 验证单次 `confirm` 成功后 1.5s 内 step 重置（DATA-006 修复）；
4. 验证 modal 标题"今日资产预览" + 日期在 header 副标题（DATA-012 修复）；
5. 验证占位符文案无 `0716`（DATA-002 修复）；
6. 验证解析模式 `single/multi` 下方有说明文字（DATA-010 修复）；
7. 验证"上传并解析"按钮有 tooltip（DATA-005 修复）；
8. 验证术语文案无 `asset_raw / snapshot_meta`（DATA-011 修复）；
9. 验证"立即上传 →"按钮可正常跳转到 `/data`（HOME-002 修复）；
10. 验证弹窗视觉与 CumulativeReturnCard 弹窗一致（GLOBAL-002 修复）。

### 6.3 SPA 全局

1. 验证 `document.title` 跟随路由变化（GLOBAL-010 修复）；
2. 验证 favicon 显示 💰 图标（GLOBAL-021 修复）；
3. 验证响应式断点（768 / 960 / 1280）下首页三卡片等高（GLOBAL-003 + HOME-007 修复）；
4. 验证 modal 在窄屏可横向滚动（GLOBAL-002 修复）。

---

## 7. 视觉验收（VIS-001 ~ VIS-012）

| ID | 检查项 | 期望 | 状态 |
| --- | --- | --- | --- |
| VIS-001 | 页面背景 | `#F9FAFB`（variables.css `--color-bg`） | ☐ |
| VIS-002 | 卡片 | 白色、8px、`0 2px 8px rgba(0,0,0,0.06)` | ☐ |
| VIS-003 | 主色 | Header/主按钮 `#0A59F7`（统一 `--color-primary`） | ☐ |
| VIS-004 | 金融色 | 正/超配红（`#FF4D4F`）；负/低配绿（`#52C41A`）；零灰（`#8C8C8C`） | ☐ |
| VIS-005 | 警告态 | 待确认使用 `#FFF3E0` 背景 | ☐ |
| VIS-006 | 表格 | 表头背景、分隔线、数字右对齐、分组/小计清晰 | ☐ |
| VIS-007 | 间距 | 卡片 gap 16px（`--space-4`），无文字贴边 | ☐ |
| VIS-008 | 按钮 | 4px 圆角，disabled/hover/focus 可辨 | ☐ |
| VIS-009 | 1280px 桌面 | Hero、三卡、双栏无溢出；三卡片等高 | ☐ |
| VIS-010 | ≤768px 移动 | 单列或合理折行，表格横向滚动 | ☐ |
| VIS-011 | 可访问性 | 不只靠颜色表达；按钮有 label/focus | ☐ |
| VIS-012 | 跨页一致性 | 首页 + 数据页 + CumulativeReturnCard 弹窗 + DataPage 弹窗视觉一致 | ☐ |

视觉对比允许因侧边栏和 React 组件产生布局差异，但不得偏离色彩、层次、密度和数据口径。

---

## 8. 需求追踪矩阵

| 需求 ID | 工作包 | 自动化 | 手工验收 |
| --- | --- | --- | --- |
| HOME-001 | PR 1 | §3.1 PR1-001~002 | §6.1 第 4 步 |
| HOME-002 | PR 1 | §3.1 PR1-002 | §6.1 第 5/9 步 |
| HOME-003 | PR 1 | §3.1 PR1-005~006 | §6.1 第 6 步 |
| HOME-007 | PR 2 | §3.2 PR2-007 | §7 VIS-009 |
| HOME-013 | PR 3 | §3.3 PR3-003~004 | §6.1 第 9 步 |
| HOME-014（⚪） | PR 4 | §3.4 PR4-012 | §6.1 第 10 步 |
| HOME-015 | PR 4 | §3.4 PR4-009 | §6.1 第 8 步 |
| DATA-002 | PR 4 | §3.4 PR4-010 | §6.2 第 5 步 |
| DATA-005 | PR 4 | §3.4 PR4-011 | §6.2 第 7 步 |
| DATA-006 | PR 3 | §3.3 PR3-001 | §6.2 第 3 步 |
| DATA-010 | PR 4 | §3.4 PR4-011 | §6.2 第 6 步 |
| DATA-011 | PR 4 | §3.4 PR4-011 | §6.2 第 8 步 |
| DATA-012 | PR 4 | §3.4 PR4-011 | §6.2 第 4 步 |
| GLOBAL-001 | PR 2 | §3.2 PR2-003 | §7 VIS-002 |
| GLOBAL-002 | PR 2 | §3.2 PR2-004 | §7 VIS-012 |
| GLOBAL-003 | PR 2 | §3.2 PR2-006 | §7 VIS-010 |
| GLOBAL-004 | PR 2 | §3.2 PR2-005 | §7 VIS-002 |
| GLOBAL-005 | PR 2 | §3.2 PR2-002 | §7 VIS-003 / VIS-004 |
| GLOBAL-007 | PR 1 | §3.1 PR1-006 | §6.1 第 11 步 |
| GLOBAL-010 | PR 4 | §3.4 PR4-002 | §6.3 第 1 步 |
| GLOBAL-015 | PR 1 | §3.1 PR1-003~004 | §6.2 第 2 步 |
| GLOBAL-016 | PR 4 | §3.4 PR4-003 | grep 验证 |
| GLOBAL-021 | PR 4 | §3.4 PR4-004 | §6.3 第 2 步 |
| PERF-002 | PR 4 | §3.4 PR4-005 | git diff 审查 |
| PERF-004 | PR 4 | §3.4 PR4-006~007 | wc -l 验证 |
| HOME-009 | PR 4 | §3.4 PR4-001 | §6.1 视觉 |
| HOME-012 | PR 4 | §3.4 PR4-008 | §6.1 视觉 |
| HOME-016 | PR 3 | §3.3 PR3-002 | DOM 审查 |
| 全局样式约束 | PR 2 | §3.2 全部 | §7 全部 |

---

## 9. 回归与最终命令

```cmd
:: 后端 smoke test（无改动也应跑一遍）
mvn -f fincontrol-backend/pom.xml test -DfailIfNoTests=false

:: 前端单元测试 + 构建
npm --prefix fincontrol-frontend test -- --run
npm --prefix fincontrol-frontend run build

:: 后端 smoke API
curl -s "http://localhost:8080/api/snapshot/latest?includeDetail=true" -H "X-User-Id: 1"
curl -s "http://localhost:8080/api/asset/balance" -H "X-User-Id: 1"
curl -s "http://localhost:8080/api/asset/cumulative-return" -H "X-User-Id: 1"
curl -s "http://localhost:8080/api/asset/operations/recent?limit=5" -H "X-User-Id: 1"

:: 累计/持有算法保护（决策 4 v2 + 决策 25 v3）
curl -s "http://localhost:8080/api/asset/cumulative-return" -H "X-User-Id: 1"
:: 必须包含 rawHoldingProfit / balanceFundAdjustment / balanceFundStatus 字段
```

期望：后端 + 前端全量通过；累计/持有响应字段稳定不变；`npm run build` 无新增 React warning。

---

## 10. 最终退出条件与签字

- [ ] DOC-001~011（WP-001~012 + AC-001~011）全部通过；
- [ ] MAP-001~006 与 review 报告 ID 映射完整；
- [ ] PR 1-5 各自验收章节（§3.1-§3.5）的 checkbox 全绿；
- [ ] 累计/持有收益成熟逻辑保护验收（§4）全部通过（**硬门禁**）；
- [ ] snapshot_meta 双层语义保护（§5）通过（**硬门禁**）；
- [ ] 浏览器功能验收（§6）全路径通过；
- [ ] VIS-001~012 视觉验收通过；
- [ ] 回归命令（§9）全绿；
- [ ] 无超范围成熟逻辑重构；
- [ ] 综合验收报告 `docs/test-records/manual-tests/1b/2026-07-24_1b4-comprehensive-acceptance-report.md` 已建立并链接证据。

| 角色 | 姓名 | 日期 | 结论 |
| --- | --- | --- | --- |
| 产品验收 | 刘博丞 | ____ | ____ |
| 实施/复核 | Cline | ____ | ____ |

---

## 11. 签字（PR 0 阶段）

PR 0 完成后由用户在 attempt_completion 返回时声明 approve（或要求调整）。

| PR | 状态 | 签字日期 | 用户反馈 |
| --- | --- | --- | --- |
| PR 0（本 PR） | 📝 计划定稿中 | ____ | ____ |
| PR 1 | ☐ 未启动 | | |
| PR 2 | ☐ 未启动 | | |
| PR 3 | ☐ 未启动 | | |
| PR 4 | ☐ 未启动 | | |
| PR 5 | ☐ 未启动 | | |
| 1b.4 整体 | ☐ 未完成 | | |

---

*本文件是规划阶段的验收基线，不提前勾选未实际执行的代码与视觉验收项。*