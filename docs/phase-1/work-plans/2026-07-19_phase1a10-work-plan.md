# Phase 1a.10 工作计划：1a.9 真实 E2E 修复 + 后端收尾（双路径并存）

**状态**：`IN_PROGRESS`（已锁定 ground truth 与验收口径，代码层双路径均已实现但缺真实收尾）
**基线**：`ae1fbe0 = origin/main`，39 个未提交/未跟踪路径
**配套决策**：[`docs/phase-0/decisions.md` 决策 8 + 1a.10 后续债段](../../phase-0/decisions.md)
**配套验收计划**：[`2026-07-19_phase1a10-acceptance-plan.md`](../../test-records/manual-tests/2026-07-19_phase1a10-acceptance-plan.md)
**真实 ground truth（你 2026-07-19 14:42 确认）**：
- 支付宝总资产 7,884.68 元；六大类合计 7,563.83 元；余额类 320.85 元
- 唯一基金 19 个；双字段独立：holdingProfit + cumulativeProfit
- 余额宝 holding=NULL、cumulative=1.89
- 国泰黄金ETF联接C 保留差异：holding=-45.25、cumulative=-40.24
- 机器 canonical「港股大中华类」；展示名「港股/大中华类」
- P1/P3/P4 顶部总资产不可见（null），P2 真实 7884.68

> **重要声明**：本轮两个工具并存，**不互相替代**：
> - 工具 A：4 次单图 `/api/screenshot/parse` + `/api/screenshot/parse` × 4 → 后端汇总/confirm
> - 工具 B：1 次 `/api/screenshot/parse-batch` 一次传 4 个 fileId → 一次上游多模态请求 → 后端兜底

---

## 0. 当前真实状态

### 0.1 代码与单测

- `mvn test` 当前 **237 tests / 0 failures** ✅
- VisionModelClient 提取完整资产 JSON 根对象 + 评分
- DedupEngine top/sum 三级判定（top 一致 / top 不一致 / 全 null）
- AiRouter 多图入口 + cache key 包含 prompt 字段已实现
- SnapShotConfirmService 持有/累计双字段同步写

### 0.2 真实 E2E 现状

- 工具 A 单图：minimax 单图 4/4 已通过；**MySQL confirm 尚未跑通**（`asset_raw=0 / asset_snapshot=0`）
- 工具 B 多图：现有 4 图一次请求最终 3002（MiniMax fallback 60s timeout）；豆包 primary 失败原因缺乏审计

### 0.3 MySQL 漂移（已修复部分）

- `fund_category_map.last_seen_at` ✅ 已存在
- `idx_user_last_seen` ✅ 已存在
- `asset_raw.holding_profit/cumulative_profit` 已 nullable ✅
- `category_master` 表 + 7 canonical seed ✅

### 0.4 仍存在的问题

- 余额宝 `holdingProfit=null` 在 confirm 时被默认值 0 覆盖
- FundCategoryResolver per-fund 覆盖 bug（同一 block 内多只基金被最后一只的 user_correct 整体覆盖）
- vision cache key 未含 prompt（v2.7.1 命中旧结果）
- provider `timeoutSeconds=300` 未实际进入 OkHttp
- fixture v3.3 错误假设四页 top 都是 7884.68（实际仅 P2 可见）
- 既有 reports 中多条「PRODUCTION 段 4/4 跑通」「19+1 unique」「4 列 NULL 化」「7+ 张才切豆包」等不实描述

---

## 1. 目标与硬验收

### 1.1 硬指标

1. **顶部总资产三级判定（单图与多图一致）**
   - 任意页面读到「总金额」或「总资产」字样 + 数字 → 记为该页 `top`（关键字顺序未知，匹配任一即可）
   - 4 页 `top` 一致 → `totalAsset=top`、`totalAssetSource="top"`
   - 4 页 `top` 不一致 → 报警 `TOP_INCONSISTENT`、`totalAsset=dedupedFundSum`、`totalAssetSource="visible_sum"`
   - 4 页 `top` 全 null → `totalAsset=dedupedFundSum`、`totalAssetSource="visible_sum"`、**无报警**

2. **去重与加和校验**
   - 20 完整行 → 19 unique
   - unique 加总 = 7884.68
   - top vs dedupedSum 偏差 0%，通过 1% 阈值，无 DISCREPANCY
   - 余额宝 holding=NULL、cumulative=1.89（confirm 时不再被 0 覆盖）
   - 国泰黄金ETF联接C 双收益差异保留：holding=-45.25、cumulative=-40.24
   - 标题行（仅 name、amount 缺失）不写入主记录、也不覆盖已有完整行

3. **机器 canonical 与展示名**
   - DB / JSON 统一为「港股大中华类」
   - 验收表 / 报告展示为「港股/大中华类」

### 1.2 范围

- 工具 A 真实 confirm 收尾：余额宝 holding=null、resolver per-fund 覆盖、fixture v3.4 真实 top 分布
- 工具 B 一次 4 图增强：cache key 含 prompt、provider timeout 真正生效、primary/fallback 错误审计
- 完整迁移脚本整合
- 5 段式验收（双路径独立判定）
- 既有文档与工作脚本更正

### 1.3 范围外

- multi-user RBAC（Phase 5b）
- Phase 1b 前端
- 自适应 DISCREPANCY 阈值
- 余额类 holding=NULL 之外的余额类数值语义扩展

---

## 2. 实施切片

### 阶段 0：基线文档（首先落盘）

- **S0.1** 新建 `docs/phase-1/work-plans/2026-07-19_phase1a10-work-plan.md`（本文件）
- **S0.2** 新建 `docs/test-records/manual-tests/2026-07-19_phase1a10-acceptance-plan.md`
- **S0.3** 修正 `docs/phase-0/decisions.md` 决策 8 与 1a.10 后续债段（明确双路径 A / B 状态）

### 阶段 1：代码修正

- **S1.1** 修正 `SnapShotConfirmService` 余额宝 holding=null 写入（不再被 0 覆盖）
- **S1.2** 修正 `FundCategoryResolver` per-fund 覆盖（同 block 多基金各自命中 user_correct，不互相覆盖）
- **S1.3** 修正 `VisionModelClient.extractFirstJsonObject`：
  - 接受 `total_amount` / `totalAsset` 同义 key
  - 顶部关键字探测：「总金额」或「总资产」字样 + 紧随数字 → 抽为 `top`
- **S1.4** 修正 `DedupEngine` 三级判定（top 一致 / top 不一致 / 全 null）
- **S1.5** 修正 `AiRouter` cache key：包含 system prompt，避免 v2.7.1 命中旧结果
- **S1.6** 修正 `VisionModelClient` OkHttp `read/write/connect` timeout 真正读取 `AiProperties.Provider.timeoutSeconds`
- **S1.7** 修正 `parseBatch`（路径 B）响应：返回每张图 OCR 审计 + 合并 audit

### 阶段 2：fixture v3.4 与单测

- **S2.1** `phase1a8-real-four-pages.json` v3.4：P1/P3/P4 top=null；P2 top=7884.68；唯一 19 项；余额宝 holding=null
- **S2.2** `DedupEngineTest` 新增「全 null top」场景
- **S2.3** `ScreenshotServiceTest` 路径 A 余额宝 holding=null
- **S2.4** `ScreenshotServiceTest` 路径 B 4 图 batch 完整链路
- **S2.5** `AiRouterTest` 多图顺序 hash / cache key 含 prompt
- **S2.6** `VisionModelClientTest` 4 图 schema（MimiMax chat + 豆包 Responses）
- **S2.7** `CategoryMasterServiceTest` / `CategoryMasterControllerTest`
- **S2.8** `SnapShotConfirmServiceTest` resolver per-fund 覆盖 + 余额宝 holding=null

### 阶段 3：迁移脚本整合

- **S3.1** 新建 `fincontrol-backend/scripts/1a10/00-mysql-migration.sql`：
  - 合并 `1a10-migration-mysql.sql` + `1a10-real-mysql-migration.sql`
  - 余额宝 holding=null 兼容
  - 港股 alias 归一化（`港股/大中华类` → `港股大中华类`）
  - v2.7.1 prompt 入库
- **S3.2** 删除两个未跟踪的 test-resource migration 草稿（避免和正式脚本双轨）
- **S3.3** H2 schema 同步：4 个新增断言（fund_category_map 唯一、category_master 唯一、holding_profit 允许 null、is_active 默认 true）

### 阶段 4：API 契约同步

- **S4.1** `docs/phase-0/api-contract.md` 增 `POST /api/screenshot/parse-batch` 章节
- **S4.2** 增 `GET/POST/PUT/DELETE /api/category-master` 章节
- **S4.3** 增 `parsedAsset.funds[].holdingProfit` / `cumulativeProfit` / `confirmedAt` 字段

### 阶段 5：真实 E2E 验证

- **S5.1** 停掉当前运行后端（PID 71476）
- **S5.2** `mvn clean verify`：≥ 245/245 PASS
- **S5.3** 启动新 jar
- **S5.4** 路径 A 真实 confirm：4 次单图 + 1 次 confirm 写入 MySQL（用户 ID 19999）
- **S5.5** 路径 B 真实 4 图 parse-batch：成功 → 标 PASS；失败 → 标 PRODUCTION_BLOCKED

### 阶段 6：本地分组 commit，不 push

- `fix(1a.10): 路径 A 余额宝 holding=null + resolver per-fund 覆盖`
- `feat(1a.10): 路径 A fixture v3.4 真实 top 分布 + totalAsset 三级判定`
- `feat(1a.10): 路径 B cache key 含 prompt + provider timeout 真实生效 + primary/fallback 错误审计`
- `feat(1a.10): category_master canonical 7 类 + DB-driven alias + port /api/category-master`
- `chore(1a.10): MySQL 迁移脚本整合（合并迁移 + 删除未跟踪草稿）`
- `test(1a.10): 路径 A confirm + 路径 B 4 图 + AiRouter + VisionModelClient 4 图 + CategoryMaster`
- `docs(1a.10): 双路径工作计划 + 验收计划 + decisions 1.10 后续债 + checklist 更正`
- 提交后 `git diff --check` 通过；`main` 仅 ahead `origin/main`

### 阶段 7：关闭后端

- `Get-Process java | Stop-Process -Force`
- 确认 `tasklist /FI "IMAGENAME eq java.exe"` 为空

---

## 3. 测试策略

| 层级 | 数据/替身 | 目的 | 是否必需 |
|---|---|---|---|
| Unit | Mockito + CategoryEnum | 7 canonical 全映射 | ✅ |
| Unit | Mockito + FundCategoryResolver | per-fund 覆盖 | ✅ |
| Unit | DedupEngineTest | top 一致 / 不一致 / 全 null 三级 | ✅ |
| Unit | VisionModelClientTest | 4 图 schema | ✅ |
| Unit | AiRouterTest | 多图顺序 hash / cache key | ✅ |
| Unit | CategoryMasterServiceTest | CRUD / alias index / 冲突检测 | ✅ |
| Integration | H2 内存 + real SnapShotConfirmService | 余额宝 holding=null / per-fund 覆盖 / 镜像校验 | ✅ |
| Integration | H2 内存 + real AiRouter + MockWebServer | 多图 provider fallback | ✅ |
| Production E2E | 真实 4 张图 + minimax | 工具 A：4/4 + confirm 写入 MySQL | ✅ |
| Production E2E | 真实 4 张图 + minimax/豆包 | 工具 B：4 图一次 + merged=19 / sum=7884.68 | ✅ |

---

## 4. 风险与停止条件

| 风险 | 缓解 | 停止条件 |
|---|---|---|
| 路径 B 豆包 primary 4 图超时 | cache key 含 prompt + provider timeout=300 + 错误审计 | 真实 E2E 连续 2 次失败 → 标 PRODUCTION_BLOCKED |
| 余额宝 confirm 仍被 0 覆盖 | Service 层直接 set null、跳过默认值 | 单测与真实 confirm 都不通过 → 标 BLOCKED |
| vision cache 仍命中旧 prompt | cache key = hash(file) + SHA-256(prompt) | 实测同图两次不同 prompt 不应复用 |
| fixture 与真实 ground truth 不符 | v3.4 全部按你 14:42 确认的 19 项逐字段写入 | 单测对账任一字段不通过 → 标 BLOCKED |

---

## 5. 引用

- 决策 1-8：[`docs/phase-0/decisions.md`](../../phase-0/decisions.md)
- 真实 E2E 报告：[`docs/test-records/manual-tests/2026-07-19_phase1a10-real-e2e.md`](../../test-records/manual-tests/2026-07-19_phase1a10-real-e2e.md)（已更正）
- checklist：[`docs/phase-1/checklists/phase-1a.md`](../../phase-1/checklists/phase-1a.md)（PRODUCTION 段拆 A / B）
- API 契约：[`docs/phase-0/api-contract.md`](../../phase-0/api-contract.md)
