# Phase 1b.4 PR4a 验收计划（P2 UX 小修 · 10 条）

> **状态**：📝 计划定稿中（待 review）
> **日期**：2026-07-24
> **配套工作计划**：[docs/phase-1/work-plans/1b/2026-07-24_1b4-pr4a-work-plan.md](../../../work-plans/1b/2026-07-24_1b4-pr4a-work-plan.md)
> **前置门禁**：PR0/PR1/PR2/PR3/PR3+ 全部验收完成
> **范围**：10 条 P2 bug + 1 条 ⚪ 备注（HOME-014）
> **不在范围**：PR4b（PERF-002/004 + HOME-012/015）/ PR5（v1.0 冻结）/ P1/P0 修复（已交付）

---

## 0. 验收总目标

确认 PR4a 修复的 10 条 P2 + 1 条 ⚪ 备注 bug **全部生效**，且：

- **累计/持有算法零回归**（硬门禁）
- **1b.3 视觉基线零变化**（HOME-009 ¥ 间距除外，已知合理变化）
- **vitest 全绿 + vite build 无 warning**

---

## 1. 自动化验收（vitest + build）

### 1.1 单元测试矩阵

| ID | 文件 | 用例数 | 期望 |
| --- | --- | --- | --- |
| UT-1 | `tests/utils/formatters.test.js` | 6 | `formatYuan` withSymbol 行为：含 thin space、null/NaN/0 边界 |
| UT-2 | `tests/App.test.jsx` | 4 | 路由 → title 映射：`/`、`/data`、`/unknown` fallback、切换更新 |
| UT-3 | `tests/utils/friendlyError.test.js` | 6 | PR1 已交付，本 PR 不破坏 |
| UT-4 | `tests/utils/blob.test.js` | 7 | PR1 已交付，本 PR 不破坏 |
| UT-5 | `tests/components/home/StateShell.test.jsx` | 2 | PR1 已交付，本 PR 不破坏 |
| UT-6 | `tests/stores/*.test.js` | 现有 | 全部 store 测试（assetSnapshotStore / userConfigStore / operationStore / chatStore / api/client）|

**期望总数**：34 → **~42**（增量 6 formatters + 4 App = 10 用例）

### 1.2 命令

```bash
cd c:\Users\lbc19\Desktop\Fincontrol
npm --prefix fincontrol-frontend test -- --run
```

**通过标准**：Test Files 全绿，Tests 数 ≥ 42，Duration < 10s。

### 1.3 Build

```bash
npm --prefix fincontrol-frontend run build
```

**通过标准**：exit code 0，dist 目录生成，无 React warning。

---

## 2. 后端 smoke test

```bash
mvn -f fincontrol-backend/pom.xml test -DfailIfNoTests=false
```

**通过标准**：BUILD SUCCESS（即使 PR4a 不触碰后端，smoke test 兜底）。

---

## 3. 累计/持有硬门禁

| 检查 | 命令 | 期望 |
| --- | --- | --- |
| `AssetQueryServiceTest` | `mvn -f fincontrol-backend/pom.xml -Dtest=AssetQueryServiceTest test` | 全 pass |
| `CumulativeReturnCard` 视觉 | puppeteer `/` 累计/持有卡片 | 数值与 PR3+ 提交后一致 |
| `phase1_simple` 算法 | 不在 PR4a 范围 | 不触碰 |
| `rawHoldingProfit` / `balanceFundAdjustment` / `balanceFundStatus` 三状态 | 不在 PR4a 范围 | 不触碰 |

**通过标准**：累计/持有相关代码 0 行 diff（仅改 formatters 调用方式的语法）。

---

## 4. 视觉验证（puppeteer · `test/1b/screenshots/`）

### 4.1 截图清单（gitignored）

| 文件 | 内容 | 路由 |
| --- | --- | --- |
| `2026-07-24-pr4a-home.png` | HomePage 全页 | `/` |
| `2026-07-24-pr4a-home-subtotal-tooltip.png` | 六类小计行 hover tooltip | `/` |
| `2026-07-24-pr4a-data-empty.png` | DataPage 空态（占位符 / alert 替换） | `/data` |
| `2026-07-24-pr4a-data-confirm-modal.png` | 确认入库 modal（header 日期 + 4 列 grid） | `/data` |
| `2026-07-24-pr4a-favicon.png` | 浏览器 tab favicon | 任意 |

### 4.2 视觉对照清单

| Bug | 检查项 | 期望 |
| --- | --- | --- |
| **HOME-009** | 首页总资产卡 `¥` 间距 | `¥` 后有 thin space（U+2009），视觉间距均匀 |
| **HOME-009** | 六大类合计卡 / 余额类合计卡 / TotalAssetCard / BalanceCard / CumulativeReturnCard | 同步间距调整 |
| **HOME-014** ⚪ | 六类小计行 `100.00%` 列 | hover 显示 tooltip "大类内部各基金持仓占比之和（恒为 100%）" |
| **GLOBAL-010** | `/` tab title | `首页 · FinControl` |
| **GLOBAL-010** | `/data` tab title | `数据管理 · FinControl` |
| **GLOBAL-010** | `/config` tab title | `资产配置 · FinControl` |
| **GLOBAL-010** | `/correction` tab title | `纠错页 · FinControl` |
| **GLOBAL-010** | `/nav` tab title | `净值 · FinControl` |
| **GLOBAL-010** | `/ratio` tab title | `比例 · FinControl` |
| **GLOBAL-010** | `/quarterly` tab title | `季度 · FinControl` |
| **GLOBAL-010** | `/ai` tab title | `AI 顾问 · FinControl` |
| **GLOBAL-016** | `/data` 上传 5 张图 | 红色错误提示，无 alert 弹窗 |
| **GLOBAL-021** | 浏览器 tab favicon | 💰 emoji 显示 |
| **DATA-002** | `/data` 占位符 | `请选择 4 张支付宝基金截图`（无 0716） |
| **DATA-005** | `/data` 上传按钮 | hover 显示 tooltip |
| **DATA-010** | `/data` 解析模式 select 下方 | form-hint 文字说明 single/multi |
| **DATA-011** | `/data` 确认入库 modal `<p className="hint">` | `确认将今日资产数据写入历史记录` |
| **DATA-012** | `/data` 确认入库 modal | header 副标题含日期；grid 4 列（无日期卡）|

---

## 5. 浏览器 console 验证

```bash
# 通过 puppeteer 脚本捕获 console.error / console.warn
```

**通过标准**：无 React key warning、无 propType warning、无红色 error。

---

## 6. 手动走查（用户执行）

| 步骤 | 操作 | 期望 |
| --- | --- | --- |
| 1 | 打开 `http://localhost:5173/` | 总资产卡 `¥\u2009{金额}` 间距均匀 |
| 2 | 滚到基金明细，hover 任一小计行 `100.00%` | 显示 tooltip |
| 3 | 查看浏览器 tab | `首页 · FinControl` + 💰 favicon |
| 4 | 点侧栏 `/data` | tab 切换为 `数据管理 · FinControl` |
| 5 | 看占位符 | `请选择 4 张支付宝基金截图` |
| 6 | 上传按钮 hover | 显示 tooltip |
| 7 | 看解析模式 | 下方说明文字 |
| 8 | 选 4 张图 → 上传并解析 → 弹确认入库 modal | header `📊 今日资产预览 · {日期}`，4 列 grid |
| 9 | modal `<p className="hint">` | `确认将今日资产数据写入历史记录` |
| 10 | 选 5 张图上传 | 红色错误提示，无 alert |
| 11 | F12 console | 无 React warning / 红色 error |

---

## 7. 服务状态验证

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:5173/
```

**通过标准**：后端 UP，前端 200。

---

## 8. PR4a 验收通过标准（5/5）

- [ ] **自动化**：`npm --prefix fincontrol-frontend test -- --run` 全绿（~42 用例）
- [ ] **构建**：`npm run build` 通过，无 warning
- [ ] **后端 smoke**：`mvn test -DfailIfNoTests=false` BUILD SUCCESS
- [ ] **硬门禁**：`AssetQueryServiceTest` 全绿 + 累计/持有代码 0 行 diff
- [ ] **视觉**：puppeteer 截图 + 用户手动走查 11 步全部 ✅

---

## 9. commit 链

```
PR4a 主 commit:
  fix(1b.4-PR4a): P2 UX 小修 10 条（HOME-009/014 + GLOBAL-010/016/021 + DATA-002/005/010/011/012）

  ↓ (后续)
PR4b 主 commit:
  fix(1b.4-PR4b): HomePage 重构 + pill 语义 + sticky

  ↓ (后续)
PR5 commit:
  docs(1b.4-PR5): 同步 phase-1b + review 追踪表 + v1.0 冻结
```

---

## 10. 下一步

- [ ] PR4a commit + push → 用户 review
- [ ] PR4b 工作计划 + 验收计划（在 PR4a 验收后另起）
- [ ] PR5 文档同步 + v1.0 冻结（在 PR4b 验收后另起）
- [ ] Phase 1b 整体收尾 → 进入 Phase 2

---

## 11. 关联文档

- PR4a 工作计划：[docs/phase-1/work-plans/1b/2026-07-24_1b4-pr4a-work-plan.md](../../../work-plans/1b/2026-07-24_1b4-pr4a-work-plan.md)
- 1b.4 PR0 路线图：[docs/phase-1/work-plans/1b/2026-07-24_1b4-pr0-work-plan.md](../../../work-plans/1b/2026-07-24_1b4-pr0-work-plan.md)
- 1b.4 PR2 验收报告：[docs/test-records/manual-tests/1b/2026-07-24_1b4-pr2-acceptance-report.md](./2026-07-24_1b4-pr2-acceptance-report.md)
- 1b.3 设计 review 报告：[docs/phase-1/checklists/2026-07-23_phase1b3-design-bug-list.md](../../../checklists/2026-07-23_phase1b3-design-bug-list.md)
- 联调记录（gitignored draft）：[test/1b/2026-07-24-1b4-pr4a-联调记录.md](../../../../test/1b/2026-07-24-1b4-pr4a-联调记录.md)

---

*完成时间：待 PR4a commit 后回填*
*验收人：待用户最终确认*