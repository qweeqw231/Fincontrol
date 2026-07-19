# Phase 1a.10 验收计划（双路径并存：A 汇总/confirm + B 一次多图）

**状态**：`GATE0_PASS`（2026-07-19 草拟）
**配套工作计划**：[`2026-07-19_phase1a10-work-plan.md`](../../../phase-1/work-plans/2026-07-19_phase1a10-work-plan.md)
**真实 ground truth（你 2026-07-19 14:42 确认）**：
- 支付宝总资产 7,884.68 元；六大类合计 7,563.83 元；余额类 320.85 元
- 唯一基金 19 个；双字段独立：holdingProfit + cumulativeProfit
- 余额宝 holding=NULL、cumulative=1.89
- 国泰黄金ETF联接C 保留差异：holding=-45.25、cumulative=-40.24
- 机器 canonical「港股大中华类」；展示名「港股/大中华类」
- P1/P3/P4 顶部总资产不可见（null），P2 真实 7884.68
- 路径 A 单图 4/4 已通过；**MySQL confirm 尚未跑通**（`asset_raw=0 / asset_snapshot=0`）
- 路径 B 一次 4 图现有 4 图一次请求最终 3002（MiniMax fallback 60s timeout）

> **双路径独立判定**：本验收计划**不**把工具 A 的 4/4 算到工具 B 的头上；A 没过 → 路径 A 标 PRODUCTION_PENDING；B 没过 → 路径 B 标 PRODUCTION_BLOCKED。

---

## 1. 顶部总资产三级判定（单图与多图共用）

| 情景 | totalAsset | totalAssetSource | 报警 |
|---|---|---|---|
| 至少 1 页读到「总金额」或「总资产」+ 数字，且 4 页数值一致 | top 数值 | `top` | 无 |
| 4 页数值不一致 | dedupedFundSum | `visible_sum` | `TOP_INCONSISTENT` |
| 4 页全 null | dedupedFundSum | `visible_sum` | 无 |
| 有 top 且 \|top − dedupedSum\| / top > 阈值（默认 1%） | top | `top` | `DISCREPANCY` |

**关键字匹配**：响应中若出现「总金额」或「总资产」字样（顺序未知，匹配任一即可），后随数字即视为该页 `top`。

---

## 2. 工具 A 验收：逐图识别 + 后端汇总/confirm

### A-BUSINESS
- SnapShotConfirmServiceTest / ScreenshotServiceTest / CategoryMasterServiceTest 全部 PASS
- 余额宝 confirm 后 holding IS NULL（不是 0）
- 同一 block 多只基金各自命中 user_correct，不互相覆盖

### A-CONTRACT
- 现有 API 行为不变；新增字段 `confirmedAt`
- `parse` 响应包含 `top` / `dedupedFundSum` / `totalAssetSource`

### A-READ_SQL
- `fund_category_map.category='港股大中华类'` 全部归一（19 行）
- 余额宝 holding IS NULL 至少 1 行

### A-PRODUCTION（硬门槛）
- 4 张样图 × 1 次 `/api/screenshot/parse` → 4/4 code=0
- 1 次 `/api/screenshot/confirm` → 用户 19999 的 MySQL 三表镜像：
  - `asset_raw` 19 行，余额宝 holding=NULL
  - `asset_snapshot` 7 行
  - `fund_category_map` 19 行，category 全部为 7 canonical 之一
- `totalAsset=7884.68`、`totalAssetSource="top"`
- 偏差 0%，无 DISCREPANCY

### A-COVERAGE
- JaCoCo ≥ 60%

---

## 3. 工具 B 验收：一次多图识别

### B-BUSINESS
- AiRouterTest：多图顺序 hash、cache key 含 prompt、primary/fallback 错误审计
- VisionModelClientTest：4 图 schema（MiniMax chat + 豆包 Responses）各 1 用例
- ScreenshotServiceTest：4 图 batch 完整链路（mock minimax 4 图 + 真实 DedupEngine）

### B-CONTRACT
- `POST /api/screenshot/parse-batch` Request：userId + fileIds 1-10 个
- Response：`code=0 / data.imageCount=4 / data.parsedAsset / data.dedupReport / data.usedProvider / data.fallbackTriggered`
- 4 张图都有 provider 审计

### B-READ_SQL
- 与 A 共享

### B-PRODUCTION（硬门槛）
- 1 次 `/api/screenshot/parse-batch` 传 4 个 fileId → code=0
- `data.parsedAsset`：19 unique、fund sum=7884.68
- `data.dedupReport`：inputRecordCount=20、mergedRecordCount=19、droppedCount=1
- `data.parsedAsset.totalAsset=7884.68`、`totalAssetSource="top"`
- 偏差 0%，无 DISCREPANCY

### B-COVERAGE
- JaCoCo ≥ 60%

> **关键**：若真实 provider 4 图仍超时，**B-PRODUCTION 段诚实标 PRODUCTION_BLOCKED**，绝不拿 A 路径的 4/4 顶替。

---

## 4. 字段语义对账

- 字段名（机器层）：`holdingProfit` / `cumulativeProfit` / `confirmedAt` / `totalAsset` / `totalAssetSource` / `topTotalAsset` / `dedupedFundSum`
- 字段语义：
  - `holdingProfit`：严格 = 截图「持有收益」列；余额类允许 NULL
  - `cumulativeProfit`：含已实现盈亏；余额类可 NULL
  - `topTotalAsset`：合并后的顶部总资产（任一页非 null 即采纳，否则 null）
  - `dedupedFundSum`：唯一基金加总（不含任何标题行）
  - `totalAssetSource`：`top` | `visible_sum`

---

## 5. 引用

- 工作计划：[`2026-07-19_phase1a10-work-plan.md`](../../../phase-1/work-plans/2026-07-19_phase1a10-work-plan.md)
- 决策 8：[`docs/phase-0/decisions.md`](../../../phase-0/decisions.md)
- checklist：[`docs/phase-1/checklists/phase-1a.md`](../../../phase-1/checklists/phase-1a.md)
- 真实 E2E：[`2026-07-19_phase1a10-real-e2e.md`](2026-07-19_phase1a10-real-e2e.md)
- 1a.9 errata：[`2026-07-19_phase1a9-real-e2e.md`](2026-07-19_phase1a9-real-e2e.md)
