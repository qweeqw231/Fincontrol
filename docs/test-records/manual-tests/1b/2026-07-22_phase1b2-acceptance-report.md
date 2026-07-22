# Phase 1b.2 验收报告（2026-07-22）

> **状态**：✅ 1b.2 100% 验收通过
> **关联计划**：[2026-07-22_phase1b2-work-plan.md](../../phase-1/work-plans/1b/2026-07-22_phase1b2-work-plan.md)
> **关联验收清单**：[docs/phase-1/acceptance-criteria.md §5.2 1b.6-1b.9, 1b.23](../../phase-1/acceptance-criteria.md)
> **基础设施修复**：[2026-07-22-step7-联调记录.md](../../../test/1b/2026-07-22-step7-联调记录.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.2（首页 + 全局联动）|
| 完成日期 | 2026-07-22 |
| commit 链 | `60f90eb`（Step 1 主页）→ `9959d0a`（Step 6 联动）→ 1b.2 收尾（决策 24 + step7 联调）|
| 验收人 | 刘博丞 |
| 验收方式 | 浏览器手动 + curl 实测 + Node.js 端到端联调脚本 |

---

## 1. 验收结果

### 1.1 1b.2 核心 4 项（acceptance-criteria §5.2）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| 1b.6 | 首页两卡片（余额类 + 六大类总值）| ✅ | commit `60f90eb`：`BalanceCard.jsx` + `TotalAssetCard.jsx` |
| 1b.7 | 首页六大类环形图（Recharts）| ✅ | commit `60f90eb`：`SixCategoriesPie.jsx`（用 Recharts 替代 ECharts，决策 16）|
| 1b.8 | 首页六大类明细表格（含 profit 列）| ✅ | commit `60f90eb`：`CategoryDetailTable.jsx` |
| 1b.9 | 首页最近操作时间线 | ✅ | commit `60f90eb`：`RecentOperationsTimeline.jsx` |

### 1.2 1b.23 全局状态联动

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| 1b.23 | 全局状态联动测试 | ✅ | commit `9959d0a`：Step 6 `bumpRefresh()` + `DataPage` 占位 confirm 按钮 |

### 1.3 累计收益算法实测（决策 4 v2 / 决策 24 衍生）

| # | 验收项 | 状态 | 实测值 |
|---|--------|------|--------|
| 累计收益卡片真实数据 | ✅ | `GET /api/asset/cumulative-return` → `{available:true, algorithm:"phase1_simple", totalCumulativeProfit:-29.63, totalAmount:7850.38, returnRate:-0.003774}` |

**实测时间**：2026-07-22 13:55（step7 端到端联调，4 张 1a.10 真实截图数据）

**详细数据**：见 `test/1b/2026-07-22-step7-联调记录.md` §3.1

### 1.4 1b.2 acceptance-plan.md 必达项（4/4）

| # | 验收项 | 状态 |
|---|--------|------|
| 1b.6 | 两卡片 | ✅ |
| 1b.7 | 环形图 | ✅ |
| 1b.8 | 明细表 | ✅ |
| 1b.9 | 操作时间线 | ✅ |
| 1b.23 | 全局联动 | ✅ |

**1b.2 100% 收尾。**

---

## 2. 需求变更记录

| # | 原计划 | 变更 | 变更原因 | 变更日期 |
|---|--------|------|---------|---------|
| 1 | 用 4 张 1a.10 真实图跑端到端 | 因 jar 重建失败多次延期，**先修基础设施**（决策 24）| 用户洞察："1b.3、1b.4 都要联调，jar 绕不过去" | 2026-07-22 |
| 2 | 1b.2 不做累计收益卡（决策 4 原始：Phase 1 隐藏）| **1b.2 实现累计收益卡**（决策 4 v2，2026-07-22 拍板）| 简化版算法 `Σcumulative/Σamount` 在 Phase 1 立刻可用，避免卡片空挂 | 2026-07-22 |
| 3 | 手动 `Start-Process java -jar` 启动后端 | **必须用 scripts/1b/restart-backend.ps1**（决策 24）| 手动启动的进程持锁 jar，导致后续 mvn repackage 失败 | 2026-07-22 |

---

## 3. 已知问题（非阻塞）

| # | 问题 | 业务影响 | 处理方式 |
|---|------|----------|----------|
| 1 | step7 Confirm 5001（镜像校验失败：raw=3285.72 vs snap=1642.86）| **0**（累计收益不受影响）| 推 Phase 2 修复：confirm 加 `confirmedOverwrite=true` 参数或镜像校验改"分母用 sum-of-all-funds" |
| 2 | uploads/screenshots/ 缓存无限增长 | 当前 ~180 张 / ~50 MB | Phase 2 补清理脚本（决策 21）|
| 3 | prompt v1.0 缺 FinControl 上下文（决策 15）| AI 答"我是 MiniMax" | 1b.4 AI 顾问页完成后聚合修复 |

---

## 4. 决策追加

✅ **决策 24**：后端 restart 必须用 `scripts/1b/restart-backend.ps1`（已加入 phase-0/decisions.md）

✅ **决策 4 v2 修订**：累计收益率卡片两阶段实现（Phase 1b phase1_simple / Phase 3 dietz/xirr），已加入 phase-0/decisions.md

---

## 5. 截图与日志

- 联调日志：`test/1b/step7-results.log`（完整 step7 输出）
- 联调记录：`test/1b/2026-07-22-step7-联调记录.md`（含实测值、已知问题、决策 24 关联）
- 后端日志：`log/backend-stdout.log` + `log/backend-stderr.log`

---

## 6. 验收签字

- 验收人：刘博丞
- 验收日期：2026-07-22
- 签字：✅（commit 链已固化）
- 下一阶段：**Phase 1b.3 数据管理页**（预计 1.5 天）
