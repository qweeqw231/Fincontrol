# FinControl Phase 0 决策文档

> Phase 0 产出。本文档最初锁定 6 项 Phase 1 启动前的关键决策，后续按项目演进追加决策 7–34。**每项决策均给出背景、决策、理由、影响范围**，作为 Phase 1+ 编码的硬约束。
>
> 与四轮评审的衔接：本文档对应第四轮评审 P0（6 项）+ 第一轮/第三轮 P0 中需要在 Phase 0 决策的事项。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.1 |
| 编写日期 | 2026-07-09 |
| 最近更新 | 2026-07-25（追加决策 34） |
| 编写者 | 架构审查助手 + 项目作者确认 |
| 文档类型 | 决策锁定（不可轻易回退）|
| 配套文档 | [api-contract.md](./api-contract.md) + [acceptance-criteria.md](../phase-1/acceptance-criteria.md) |

---

## 决策 1：REST API 契约已完成

**状态**：✅ 已完成（详见 [api-contract.md](./api-contract.md)）

**背景**：
- 第二轮评审 P0 4.1 指出 CorrectionInput/Result 接口签名与 4.1 输入变量严重不对齐
- 第四轮评审 P0 3.3.5 指出整个文档缺少完整的"前端 URL → 后端 Controller.method → Request/Response DTO"映射表

**决策**：
- 已产出 [api-contract.md](./api-contract.md)，包含 5 大类、30+ API 端点
- 截图解析（4 个 API）、快照查询（4 个）、快照入库（2 个，含撤销）、月度校正（4 个）、资产配置（2 个）、大类映射（2 个）、对话 AI（5 个）、首页辅助（3 个）、解析日志（1 个）

**影响范围**：
- 前后端可基于本文档并行开发
- Phase 1 编码期间 API 调整需同步更新 [api-contract.md](./api-contract.md)

---

## 决策 2：target_ratio 同步策略锁定（解读 4）

**状态**：✅ 已锁定

**背景**：
- 第四轮评审 P0 2.2.1：target_ratio 字段同步语义有 4 种解读，导致 /config、/correction、比例演化三个页面的数据一致性无法保证
- 7.4.4 原文档说"保存后写入 user_config 表，并同步更新 asset_snapshot 表的 target_ratio 字段"，但 asset_snapshot 是历史快照表，同步会破坏历史数据

**决策（解读 4）**：

> target_ratio 以 `user_config` 为唯一权威源（source of truth），`asset_snapshot.target_ratio` 是月度校正时按当时 `user_config` 同步的冗余快照。

**具体规则**：

1. `/api/config` 保存时，**只写 `user_config` 表**，不触发 `asset_snapshot` 级联更新
2. `/api/correction/defaults` 读取时，**从 `user_config` 读取 targetRatios**，不读 `asset_snapshot`
3. 月度校正执行时（`POST /api/correction/monthly/calculate`），把当时的 `user_config.target_ratios` 同步写入当次 `operation_log.target_ratios` 字段（作为操作当时的快照值）
4. 比例演化看板展示历史 target_ratio 时，按对应日期的 `operation_log` 反查；没有 operation_log 的日期显示 N/A 或"使用当时默认配置"
5. 净值曲线（Phase 3）的"目标对比线"从当前 `user_config` 读取，不读历史

**触发连锁**：
- 第二轮评审 P0 4.2.3"目标比例变更历史"：需要新增 `target_ratio_history` 表（在 `user_config` 写入时追加一行），不再是 P3 远期项，**升级为 Phase 2 任务**

**理由**：
- 严格保持 asset_snapshot 的"由数据驱动的快照"语义，不被配置变更污染
- 配置与数据分离，边界清晰
- 与回填场景（用户修改历史日期的 target_ratio）无冲突

**回退条件**：Phase 5 多用户场景下，若需要"用户修改历史 target_ratio"，需重新评估

---

## 决策 3：profit 字段读取位置确定（首页明细表）

**状态**：✅ 已确定

**背景**：
- 第四轮评审 P0 2.1.1：`asset_raw.profit` 是"写入孤儿"——采集但不展示
- 7.4.1 首页三大卡片（余额类、六大类总值、累计收益率）均不显示 profit
- 数据管理、数据解析日志、月度操作台、资产配置、净值曲线、比例演化均不显示

**决策**：

> profit 字段在首页"六大类明细表格"（7.4.1 末尾，默认折叠）中展示。表格新增"持有收益"列，每只基金显示 `profit` 字段。

**实现细节**：
- 首页调用 `GET /api/snapshot/latest/detail` 获取含每只基金 profit 的数据
- 表格列：基金名称、金额、占比、目标比例、偏差、**持有收益**（新增列）
- 数据流：截图解析 → 写入 `asset_raw.profit` → 汇总到首页展示
- 不影响其他页面

**触发更新**：
- 首页前端组件 `AssetOverview.jsx` 增加 profit 列
- API `GET /api/snapshot/latest/detail` 返回 profit 字段（已在 [api-contract.md](./api-contract.md) 3.2 节定义）

**理由**：
- 最低成本：仅修改首页表格列
- 最高复用：所有 18 只基金的 profit 在一处集中展示
- 与 6.2 数据模型定义对齐（原始数据不可变，但展示位置要补齐）

**回退条件**：未来若需要更详细的浮盈分析（按月、按策略），需重新设计 profit_analytics 表

---

## 决策 4 v2（2026-07-22 修订）：首页"累计收益率"卡片两阶段实现

**状态**：✅ 已锁定（原决策 4"Phase 1 隐藏"已废止，本条为唯一生效版本）

**修订背景**：
- 2026-07-22 1b.2 编码前讨论中发现：原决策 4"直接隐藏"过于保守
- 灰测数据中已有 `asset_raw.cumulative_profit` 字段（决策 7 拆分后保留），是天然数据源
- **新算法**：`累计收益率 ≈ Σcumulative_profit / Σamount`（快照型，忽略时间加权），1 次 SELECT
- 该算法不是真实 IRR，但作为"近似版"可在 Phase 1 启用，Phase 3 升级为 Modified Dietz / XIRR
- 跨模块评审 3.3.1 红标"无数据源"问题，本决策即解决方案

**决策**：

> 首页累计收益率卡片分两阶段实现：
>
> **Phase 1b（1b.2 起）**：渲染卡片，算法 `Σcumulative_profit / Σamount`，API 返 `algorithm: "phase1_simple"`。
> **Phase 3（净值曲线）**：算法升级为 Modified Dietz 或 XIRR，API 返 `algorithm: "phase3_dietz"` / `"phase3_xirr"`，前端 UI 不变，仅 tooltip 文案随之更新。

**算法 SQL（Phase 1b）**：
```sql
SELECT
  COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit,
  COALESCE(SUM(amount), 0) AS total_amount
FROM asset_raw
WHERE is_latest = 1   -- 全口径：分子分母都包含余额类（2026-07-22 用户拍板口径 A / 总资产视角 / 含余额宝）
```

**公式**：`累计收益率 ≈ total_cumulative_profit / total_amount`

**实现细节**：
- 后端 `AssetQueryService.getCumulativeReturn()` 替换现有占位 `getCumulativeReturnPlaceholder()`
- DTO 新增 `algorithm: "phase1_simple"` 字段（Phase 3 时改为 `"phase3_dietz"` / `"phase3_xirr"`）
- 前端 `CumulativeReturnCard` 组件：
  - 显示百分比（如 `+5.67%`）
  - 卡片底部显示算法标识 + tooltip（"简化版：Σcumulative/Σamount"）
- 前端三卡片布局：余额类 / 六大类总值 / 累计收益率（不再分两/三态切换）

**为什么不直接用 IRR**：
- IRR/XIRR 需要 operation_log 表（每笔 cash flow + 日期），Phase 1 不实现
- Phase 3 净值曲线时必须先建 `nav_history` / `manual_nav_entry` / `event_log` 4 张表（跨模块评审 4.3.4 已识别）
- Phase 1b 用"简化版"折中，立刻让用户看到数字，不空挂卡片

**理由**：
- **立即可用**：Phase 1b 不必等 Phase 3 的 4 张表
- **渐进升级**：从快照型 → Modified Dietz → IRR，算法精度随数据成熟度提升
- **前端透明**：UI 不变，仅底层算法 + tooltip 文案变化，避免前端重构
- **决策追溯**：本 v2 修订保留原决策 4 文件存档，明确"Phase 1 隐藏"的旧方案不再适用

**Phase 3 升级触发**：
- 当 `nav_history` 表 + Phase 3a 数据模型落地后，启动 Phase 3b
- 在 `AssetQueryService.getCumulativeReturn()` 中切换分支：
  ```java
  if (phase3NavHistoryAvailable) {
      return computeDietzReturn();   // 或 computeIRRReturn()
  } else {
      return computeSimpleReturn();  // 现有逻辑
  }
  ```
- DTO 字段 `algorithm` 同步更新
- 前端 tooltip 文案从"简化版"升级为"Modified Dietz"或"年化 IRR"

**回退条件**：
- 若 Phase 1 简化版与 Phase 3 IRR 偏差 > 2%，需在 1b.2 验收报告中明确标注"Phase 3 切换时的修正幅度"
- 若用户要求 Phase 1 重新隐藏卡片，回退到 v2 之前的决策（即决策 4 历史版）

---


---

## 决策 5：Phase 1 验收标准追加第一/三轮 P0

**状态**：✅ 已锁定

**背景**：
- 第四轮评审 P0 4.3.1、4.3.2：Phase 1 验收标准（第八章末尾）只写了"截图→解析→入库→首页展示"四步，未覆盖第一/三轮 P0
- 实际 Phase 1 开发中会立即遇到余额类数据路径、AI 解析失败处理、前端防抖等问题

**决策**：

> Phase 1 验收标准在第八章原有 6 项基础上，追加第一轮 P0 全部 6 项 + 第三轮 P0 中与数据入库相关的 7 项。详见 [acceptance-criteria.md](../phase-1/acceptance-criteria.md)。

**追加项概览**：

- 第一轮 P0 全部：余额类数据路径、三表事务、映射 UPDATE、解析失败异常、快照日期校验、前端防抖
- 第三轮 P0 中与数据入库相关：余额类下拉选项、"忽略该条"按钮、10秒撤销、快照日期默认值、Prompt 日期格式

**理由**：
- 避免 P0 在编码阶段反复暴露导致返工
- 验收标准本身就是 Phase 1 完成的定义

---

## 决策 6：Phase 计划修订（新增 Phase 0、拆分 Phase 1、扩展 Phase 3、拆分 Phase 5）

**状态**：✅ 已锁定

**背景**：
- 第四轮评审 P0 4.3.4：Phase 3（2-3 天）实现净值曲线 + 比例演化 + 日收益明细需要 4 张未设计的表，时间不可能
- 第四轮评审 P0 4.3.7：当前 Phase 计划缺少 Phase 0（基础设施搭建）
- 第四轮评审 P0 4.3.3：Phase 1 时间预算过于乐观（3-4 天完成数据流水线 + 首页 + AI 顾问）

**决策（修订版 Phase 计划）**：

### 完整 Phase 计划

| Phase | 核心任务 | 时间 | 验收标准 |
|-------|---------|------|---------|
| **Phase 0** | **基础设施搭建** | **1-2 天** | **见下方** |
| Phase 1a | 后端流水线 | 3-4 天 | 后端 5 个 API + 数据库 + DeepSeek 集成 |
| Phase 1b | 前端对接 | 3-4 天 | 前端 5 个页面 + API 对接 + 状态管理 |
| Phase 2 | 核心业务功能 | 3-4 天 | 资产配置 + 月度操作台 + 历史查询 |
| Phase 3a | 数据模型设计 | 1-2 天 | 4 张新表（nav_history、daily_returns、event_log、manual_nav_entry）|
| Phase 3b | 数据可视化 | 3-4 天 | 净值曲线 + 比例演化 + 日收益明细 |
| Phase 4 | 问题修复 + UI 优化 | 2-3 天 | P1/P2 残留问题处理 + bug 修复 |
| Phase 5a | LQR 实战集成 | 远期 | LQR 算法集成 + α/β/γ 校准 |
| Phase 5b | 多用户 | 远期 | 用户系统 + 数据隔离 |
| Phase 5c | 移动端 | 远期 | 移动端适配 |

### Phase 0 详细验收标准

| # | 项目 | 验收点 |
|---|------|-------|
| 0.1 | 后端项目骨架 | Spring Boot 3.x + Maven + MyBatis-Plus + Java 17 项目结构 |
| 0.2 | 前端项目骨架 | Vite + React 18 + React Router + Axios + Zustand（状态管理）|
| 0.3 | 数据库 Schema | [db-schema.sql](./db-schema.sql)（含所有表：asset_raw、asset_snapshot、fund_category_map、chat_history、user_config、prompt_versions、operation_log 新增）|
| 0.4 | API 契约 | [api-contract.md](./api-contract.md)（已完成）|
| 0.5 | 测试框架 | JUnit 5 + Mockito + Vitest |
| 0.6 | CI 配置（可选）| GitHub Actions 或 GitLab CI 基础流水线 |
| 0.7 | 余额类基金预录入 | fund_category_map 表预录 18 只基金 + 余额宝 + 余额 |
| 0.8 | 18 只基金映射核对 | 手动核对 fund_category_map 全部条目（用户操作）|

### Phase 1a 详细验收标准

| # | 项目 | 验收点 |
|---|------|-------|
| 1a.1 | 后端 API 实现 | [api-contract.md](./api-contract.md) 中 Phase 1 涉及的 API 全部实现（截图上传/解析、快照查询/入库、大类映射、对话列表） |
| 1a.2 | 三表事务 | 截图确认入库 API 使用 @Transactional（第一轮 P0 1.3） |
| 1a.3 | 余额类数据路径 | asset_raw.category 支持"余额类"枚举值（第一轮 P0 1.1） |
| 1a.4 | AI 解析失败处理 | code 3001/3003 错误码 + 失败记录写入 chat_history（第一轮 P0 1.6） |
| 1a.5 | 快照日期校验 | API 1001 错误码 + 前后端双重校验（第一轮 P0 1.4） |
| 1a.6 | 映射 UPDATE 规则 | 7.2 API 实现（第一轮 P0 2.2） |
| 1a.7 | 数据库 Schema | 运行 [db-schema.sql](./db-schema.sql) 建表无错误 |
| 1a.8 | 单元测试 | 核心 Service 类覆盖率 ≥ 60% |

### Phase 1b 详细验收标准

| # | 项目 | 验收点 |
|---|------|-------|
| 1b.1 | 首页展示 | 余额类卡片 + 六大类总值 + 六大类明细表格（含 profit 列，决策 3） |
| 1b.2 | 数据管理页面 | 上传截图 → 调用 DeepSeek → 显示气泡 → 触发确认面板 |
| 1b.3 | 大类确认面板 | 余额类下拉选项（第三轮 P0 2.2.4.3） + 10 秒撤销（第三轮 P0 2.2.2.1） + "忽略该条"按钮（第三轮 P0 2.2.4.5） |
| 1b.4 | AI 顾问页面 | 基础对话 + 意图分类 + system prompt 切换（第三轮 P0 3.2.1） |
| 1b.5 | 全局状态管理 | Zustand stores：assetSnapshotStore、userConfigStore、operationStore |
| 1b.6 | 前端防抖 | 确认入库按钮 disabled 状态机（第一轮 P0 3.2） |
| 1b.7 | 累计收益率卡片 | Phase 1 隐藏（决策 4） |
| 1b.8 | 截图日期默认值 | AI 识别 > 系统当前日 > 用户上次确认日（第三轮 P0 4.1.2） |

**注**：Phase 1 验收标准的完整版（含所有 P0 详细验收项）见 [acceptance-criteria.md](../phase-1/acceptance-criteria.md)。

### 触发连锁

- 第四轮 P0 4.2.3"目标比例变更历史"：升级为 Phase 2 任务，需新增 `target_ratio_history` 表
- 第二轮 P0 4.1.2"nav_history 表"：升级为 Phase 3a 任务
- 第二轮 P0 2.1.10"operation_log 表"：升级为 Phase 1a 任务（数据模型必须先建好）

**理由**：
- Phase 0 拆分让"基础设施 vs 业务逻辑"边界清晰
- Phase 1a/1b 拆分让后端可独立测试 API，前端可基于 mock 数据开发
- Phase 3a/3b 拆分让数据模型先行，图表实现有数据基础
- Phase 5 拆分让三个独立大特性不会互相阻塞

---

---

## Phase 0 → Phase 1a 启动清单

启动 Phase 1a 前必须完成：

- [x] 决策 1：API 契约文档（[api-contract.md](./api-contract.md)）
- [x] 决策 2-6：见本文档
- [x] 决策 5：验收标准（[acceptance-criteria.md](../phase-1/acceptance-criteria.md)）
- [ ] 手动核对 18 只基金 + 余额类基金的 fund_category_map 映射
- [ ] 建表 SQL：[db-schema.sql](./db-schema.sql)（Phase 0.3）
- [ ] 后端项目骨架（Phase 0.1）
- [ ] 前端项目骨架（Phase 0.2）

完成上述 7 项后，方可启动 Phase 1a 后端编码。

---

## 文档维护

- **Phase 1a 编码期间**：根据实际开发调整本文档的决策，需同步更新 [api-contract.md](./api-contract.md) 与 [acceptance-criteria.md](../phase-1/acceptance-criteria.md)
- **Phase 2 启动前**：补齐 target_ratio_history 表设计（决策 2 触发）
- **Phase 3a 启动前**：补齐 nav_history、daily_returns、event_log、manual_nav_entry 4 张表设计

---

## 决策 7：profit 字段拆分为 holding_profit / cumulative_profit（1a.8.7）

**状态**：✅ 已锁定（2026-07-18）

**背景**：
- 1a.7 设计的 `asset_raw.profit` 单字段在 1a.8 真实数据验证中暴露出"语义混合"问题：同一只基金的「持有收益」与「累计收益」在用户发生过卖出操作时不同（如 `国泰黄金ETF联接C` 持有 -45.25 vs 累计 -40.24，差 5.01 元）
- 单字段只能记录一个值，前端展示、汇总累计收益、数据仓库等都需要两个独立字段
- 决策 3（profit 在首页明细表展示）只定义了读取位置，未规定字段语义

**决策**：

> 1. `asset_raw.profit` 保留作为兼容期（写入时与 holding_profit 同步填相同值），不破坏现有 v1.0 schema 与决策 3
> 2. 新增 `asset_raw.holding_profit`（持有收益，严格=截图「持有收益」列，不含当日浮盈/累计已实现）
> 3. 新增 `asset_raw.cumulative_profit`（累计收益，含已实现盈亏；卖出后分母更新）
> 4. ScreenshotService / SnapShotConfirmService 写入时三列同步；AssetQueryService / SnapshotQueryService / DedupEngine 读时优先 holding_profit/cumulative_profit，旧 profit 字段保留兼容
> 5. `total_asset` 规则收紧为：visible 优先（截图实际显示总资产），不可见时 sum-of-complete-funds 兜底（含余额宝/余额类），禁止模型凭空捏造

**Schema 升级**：
- `asset_raw` 表 +2 列：`holding_profit DECIMAL(12,2) NOT NULL DEFAULT 0` + `cumulative_profit DECIMAL(12,2) NOT NULL DEFAULT 0`（与 profit 并列）
- MySQL 与 H2 测试 schema 同步 ALTER

**DTO 升级**：
- `ParsedAsset.FundLine` / `AssetBalanceItem` / `SnapshotFundDetail` 三处都加 `holdingProfit` + `cumulativeProfit`，`profit` 保留

**Fixture 升级**：
- 4 页 fixture 升级到 v3.1，19 项 unique fund 各自含 `expectedHoldingProfit` + `expectedCumulativeProfit`；`国泰黄金 ETF 联接 C` 保留 `holding=-45.25` / `cumulative=-40.24`

**理由**：
- 语义分离是真实数据暴露的硬需求，1a.7 字段设计在 v2 prompt 下被验证无法覆盖所有用户的累计/持有差异
- `profit` 保留保证老读取路径（决策 3 首页明细表）继续工作
- 旧 fixture / 旧测试同时支持 holding/cumulative 字段可分阶段迁移
- `total_asset` 收紧避免模型幻觉（曾把 sum-of-funds 误认成 visible 总额）

**回退条件**：无（schema 已 ALTER；不向后兼容 holding/cumulative 会破坏累计收益展示）

---

## 决策 8：基金类别归一化 + 双向 cache + 清仓可恢复（1a.8.8）

**状态**：✅ 已锁定（2026-07-18）

**背景**：
- 1a.5 大类映射（[`fund_category_map`](#)）的 `category` 列是 VARCHAR(50)，模型可输出"QDII / 商品 / 固收 / 货币 / 权益类 / 黄金类 / 保障类"等自由命名，而设计文档规定 7 canonical 名（货币类/固收类/商品类/A股权益类/海外权益类/港股大中华类/余额类）。前端 confirm 页面看到的是自由命名，与文档不一致。
- 1a.3 confirm 与 1a.5 update 都可向 `fund_category_map` 写映射，但首次确认后无"last_seen_at"字段，清仓后再出现的基金会被模型重新输出自由命名，导致前端必须重复确认。
- 1a.5 `GET /api/category-map/match` 路由设计未走 userId 参数（仅 `X-User-Id` header），多用户场景下会跨用户命中 mapping。

**决策**：

1. **类别归一化**：新增 `CategoryEnum` 枚举（7 canonical + 别名表），`fromAlias()` 提供模糊匹配；`ScreenshotService.mapToParsedAsset` 与 `SnapShotConfirmService.writeFundCategoryMap` 全部走 `CategoryEnum.fromAlias()` 归一化。块类别名 + 每只基金的 canonical 都经过归一化。
2. **双向 cache**：新增 `FundCategoryResolver`（4 优先级）：
   - (1) `fund_category_map.source='user_correct'` 命中 → canonical + `isUserConfirmed=true`
   - (2) `fund_category_map` 任意 source 命中 → canonical + `isUserConfirmed=false`
   - (3) `CategoryEnum.fromAlias(rawCategory)` 命中 → canonical + `isUserConfirmed=false`
   - (4) fallback → raw + `isUserConfirmed=false`（前端高亮）
3. **`last_seen_at` 字段**：新增 `fund_category_map.last_seen_at TIMESTAMP NULL` 列；upsertByFundName 同步写 `last_seen_at=CURRENT_TIMESTAMP`；新端点 `GET /api/category-map/stale?days=N`（默认 90）列 stale user_correct。
4. **二态写**：`SnapShotConfirmService.writeFundCategoryMap` 区分：首次 upsert → `source='ai_guess'`；已有行 → `source='user_correct'` + last_seen_at 更新。
5. **多用户债修复**：`match`/`update` 接受 `?userId=N` 显式参数；`DELETE /api/category-map/{userId}/{fundName}` 路径带 userId；`POST /api/category-map/reset`（source → ai_guess）让用户主动重置。

**Schema 升级**：
- `fund_category_map` +1 列：`last_seen_at TIMESTAMP NULL`（用户确认后写入）
- MySQL：`ALTER TABLE fund_category_map ADD COLUMN IF NOT EXISTS last_seen_at DATETIME NULL AFTER confirmed_at, ADD INDEX IF NOT EXISTS idx_user_last_seen (user_id, last_seen_at);`
- H2 测试 schema 同步

**DTO 升级**：`ParsedAsset.FundLine` / `AssetBalanceItem` / `SnapshotFundDetail` 三处都加 `isUserConfirmed: boolean`

**Fixture 升级**：`phase1a8-real-four-pages.json` 升 v3.2，类别名统一为 canonical（"港股/大中华类" → "港股大中华类"）

**理由**：
- 类别名漂移导致 confirm UI 与设计文档不一致，影响前端 review 体验 → 归一化强制 7 canonical
- 清仓再出现的弹窗误报 → last_seen_at 让 stale 判定可观察
- 多用户场景下 match 跨用户命中 → userId 显式参数 + DELETE 路径隔离
- ai_guess → user_correct 升级路径让用户主动确认语义保留

**回退条件**：
- 若未来需要 CRUD 类别名（增/删/改 7 canonical），属 1a.9+ 范畴（master table 模式）
- 若前端 confirm UI 需要绕过 user_correct 状态显式提示"未确认"，1a.9 可加 `confirmedAt` 字段
- multi-user RBAC 隔离完整版属 1b.x / Phase 5b

---

### 1a.9 增补（2026-07-18）：总资产双轨 + DISCREPANCY 1% 报警

**背景**：
- 1a.8.8 v2.5 真实 E2E 暴露 P2 totalAsset 口径不一致：fixture 期望顶部"总资产"全账户 7884.68，v2.5 prompt 让模型输出 P2 页 visible sum 2987.32
- 单字段 `total_asset` 无法表达"顶部 vs visible sum"差异，backend DedupEngine 原逻辑只用 merged fund sum，丢失顶部语义

**决策**：

1. **prompt v2.6（id=8，3859 字节）**：
   - `total_asset` 字段 = 截图顶部"总资产"数字（无论第几页都应一致）
   - 顶部不可见时输出 null（不要凭空估算）
   - **禁止**：将当前页可见基金的 amount 加总作为 total_asset
2. **DedupEngine 双轨决策**：
   - 源 1 (top)：4 页顶部"总资产"一致时用顶部值（语义最准）
   - 源 2 (visible_sum)：顶部不可用 / 4 页不一致时 fallback 到 deduped fund 加总
   - `merged.totalAssetSource` 字段标 `"top"` 或 `"visible_sum"`
3. **DISCREPANCY 报警（1% 阈值）**：
   - `|top - dedupedSum| / top > 1%` → `DedupWarning(code=DISCREPANCY)`（不阻塞，落地供前端显示）
   - 4 页顶部不一致 → `DedupWarning(code=TOP_INCONSISTENT)` + fallback visible_sum
4. **Schema 升级**：
   - `asset_raw.total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top'`（denormalized，每行 19 条同值）
   - `asset_snapshot.total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top'`
   - MySQL 8.0.46 已 ALTER + H2 CHECK 约束同步（`CHECK (total_asset_source IN ('top','visible_sum'))`）
5. **DTO 升级**：`ParsedAsset` / `AssetRaw` / `AssetSnapshot` 都加 `totalAssetSource: String` 字段

**Prompt 走样实证（v3.2 之前）**：
- fixture v3.3：每页 `expectedTotalAsset=7884.68`（v2.6 prompt 顶部一致）
- v2.5 fixture：P2 visible sum=2987.32 → 偏差 62% → 验证 DISCREPANCY 报警能正确检测此类问题

**理由**：
- top 优先：Alipay 顶部"总资产"是全账户权威值，per-page visible sum 是部分页面统计，语义本就不同
- visible_sum 兜底：部分页面顶部被截断/遮挡时仍能给出一个合理值
- 1% 阈值：敏感但不过敏，P2/P3 visible sum 天然会偏差大能立即发现
- total_asset_source denormalized：前端明细表展示需要每行都有 source，便于溯源

**回退条件**：
- 如果 v2.6 prompt 触发更多错误 → `DELETE FROM prompt_versions WHERE id=8` 让 v2.5 复活（PromptLoader `ORDER BY id DESC`）
- 如果 DISCREPANCY 阈值 1% 太敏感 → 改 `DedupEngine.DISCREPANCY_THRESHOLD_PCT` 常量（待加）
- 如果未来想明确区分多用户资产总额 → 引入 user_id-scope 的 top 优先（Phase 5b）

**测试覆盖（5 个新增）**：
- `DedupEngineTest.dedup_totalAsset_topConsistentAcrossPages_usesTop`：4 页 top 一致 → totalAssetSource=top
- `DedupEngineTest.dedup_totalAsset_topInconsistentAcrossPages_fallsBackToDedupedSum`：4 页不一致 → TOP_INCONSISTENT warning + fallback
- `DedupEngineTest.dedup_totalAsset_topVsDedupedSumDiscrepancyOver1Percent_emitsWarning`：偏差 > 1% → DISCREPANCY warning
- `DedupEngineTest.dedup_totalAsset_topVsDedupedSumDiscrepancyWithin1Percent_noWarning`：偏差 <= 1% → 无 warning
- `Phase1a8RealFourPageFixtureTest.dedup_totalAsset_topConsistentAcrossPages_usesTop`：fixture v3.3 4 页 → 7884.68 + top
- `Phase1a8RealFourPageFixtureTest.dedup_v3_3FixtureWithBadP2Top_emitsBothWarnings`：fixture v3.3 P2 top 改成 2987.32 → TOP_INCONSISTENT + totalAssetSource=visible_sum

---

### 1a.10 后续债（2026-07-19 真实 E2E 暴露；14:48 接手重写为「双路径并存」）

**状态**：`IN_PROGRESS`（路径 A 真实 confirm 待收尾 + 路径 B 一次 4 图 provider 真实验收待执行）。本轮确认两个工具并存，**不互相替代**。

**真实 ground truth（用户 14:42 确认）**：
- 支付宝总资产 7,884.68 元；六大类合计 7,563.83 元；余额类 320.85 元
- 唯一基金 19 个；双字段独立 holding + cumulative
- 余额宝 holding=NULL、cumulative=1.89
- 国泰黄金ETF联接C 保留差异：holding=-45.25、cumulative=-40.24
- 机器 canonical「港股大中华类」；展示名「港股/大中华类」
- P1/P3/P4 顶部总资产不可见（null），P2 真实 7884.68

**顶部总资产三级判定（单图与多图共用）**：
- 任意页面读到「总金额」或「总资产」字样 + 数字 → 记为该页 `top`
- 4 页 `top` 一致 → `totalAsset=top`、`totalAssetSource="top"`
- 4 页 `top` 不一致 → 报警 `TOP_INCONSISTENT`、`totalAsset=dedupedFundSum`、`totalAssetSource="visible_sum"`
- 4 页 `top` 全 null → `totalAsset=dedupedFundSum`、`totalAssetSource="visible_sum"`、**无报警**

**两条工具路径**：
- 工具 A：4 次单图 `/api/screenshot/parse` + 后端汇总/`/api/screenshot/confirm`。当前 MySQL `asset_raw=0 / asset_snapshot=0`，真实 confirm 尚未跑通。
- 工具 B：1 次 `/api/screenshot/parse-batch` 一次传 4 个 fileId → 一次上游多模态请求 → 后端兜底去重 + top/sum 校验。当前真实 4 图请求最终 3002（MiniMax fallback 60s timeout）；豆包 primary 失败原因缺乏审计。

**仍存在的实质问题（按优先级）**：
1. 余额宝 `holdingProfit=null` 在 confirm 时被默认值 0 覆盖（H2 集成 SQL 日志已直接证明）
2. `FundCategoryResolver` per-fund 覆盖 bug：同一 block 内多只基金被最后一只的 `user_correct` 整体覆盖
3. vision cache key 未含 prompt（v2.7.1 命中旧结果）
4. provider `timeoutSeconds=300` 未实际进入 OkHttp（当前硬编码 MiniMax 60s / 豆包 120s）
5. fixture v3.3 错误假设四页 top 都是 7884.68（实际仅 P2 可见）
6. 既有 reports 中多条「PRODUCTION 段 4/4 跑通」「19+1 unique」「4 列 NULL 化」「7+ 张才切豆包」等不实描述

**最终硬验收（双路径独立）**：

工具 A：
- 4 张样图 × 1 次单图 parse → 4/4 code=0
- 1 次 confirm → MySQL 19 raw（余额宝 holding=NULL） / 7 snapshot / 19 map；镜像一致
- `totalAsset=7884.68`、`totalAssetSource="top"`、偏差 0、无 DISCREPANCY

工具 B：
- 1 次 4 图 parse-batch → code=0、merged unique=19、fund sum=7884.68、`totalAsset=7884.68`、偏差 0
- 若真实 provider 4 图仍超时 → **诚实标 PRODUCTION_BLOCKED**，不冒充 PASS

**详细执行计划**：`docs/phase-1/work-plans/2026-07-19_phase1a10-work-plan.md`
**详细验收计划**：`docs/test-records/manual-tests/2026-07-19_phase1a10-acceptance-plan.md`

> 旧 plan 中"只放宽 zero_funds 即可""四页顶部都应为 7884.68""MySQL 迁移均已落地"等表述不再作为验收事实。

---

## 决策 12（Phase 1a 收尾新增）：豆包 vision 路径暂时废弃，Phase 1a 按 minimax-only 验收通过

**状态**：✅ 已锁定（2026-07-19）

**背景**：
- Phase 1a.8（1a.10 计划内）设计双 provider 互为 fallback：minimax + 豆包 ARK
- 真实 e2e 测试在 1.5 小时内（17:50 ~ 20:55）尝试了 4 个豆包 vision model（1-5-pro / 1-8 / 2-0-pro / 2-1-turbo），**4 个全部失败**：
  - HTTP 404 InvalidEndpointOrModel.NotFound（2-0-pro / 1-8）
  - HTTP 429 RequestBurstTooFast（1-5-pro on /responses）
  - 5 分钟 readTimeout（2-1-turbo on /chat/completions）
- 唯一稳定工作的路径是 minimax，路径 A 和路径 B 全部通过 minimax fallback 跑通

**决策**：
- **Phase 1a 验收按 minimax-only 路径通过**（单图 minimax / 多图 minimax fallback）
- **豆包 vision 路径代码保留不删**（callOpenAiChatDoubao、5 参数 callRaw 重载等基础设施）
- **1a.11+ 重新评估**：联系 ARK 客服 / 评估其他 vision provider（Azure Computer Vision / 阿里云视觉智能 / 腾讯云图像识别等）

**理由**：
- 4 个 model 全部失败的 pattern 强烈指向「ARK 账户没开通 vision 模型权限」
- minimax 完全可替代豆包在本 Phase 的角色（都是 OpenAI Chat Completions 兼容接口）
- 强制等豆包修好不阻塞 Phase 1a 进入 Phase 1b

**影响范围**：
- 代码：无（豆包路径代码完整保留）
- 测试：路径 A 4/4 + 路径 B 4/4 都通过 minimax 跑通（19 funds / 7884.68）
- 监控：vision failure 审计会记录豆包 primary 失败（已有 §9.3 修复，commit 8de7139）
- 部署：生产路径完全收敛到 minimax
- 文档：phase-1a.md 1a.10.B5 标为「部分完成」并说明

**关键 commit**：
- `8de7139` fix(1a.10): 路径 B 多图一次传跑通（§9.2/§9.3/§9.4 修复）
- `184e6c9` 1a.10 report sec 9.5b: doubao-seed-2-1-turbo-260628 with chat/completions
- `c71da93` docs(1a.10): 报告 sec 9.6 - seed-2-1-turbo + chat/completions 实测
- `20c3539` refactor(1a.10): AiRouter.callRaw 改用 5 参数重载

**Phase 1a 退出条件重新确认**：
- ✅ 24 项 API 全部完成
- ✅ 8 项 P0 全部达成
- ✅ 2 条冒烟测试通过
- ✅ 路径 A 真实 confirm 跑通（19 funds / 7884.68）
- ✅ 路径 B 真实 4 图一次传跑通（minimax fallback 19 funds / 7884.68）
- ✅ mvn test 238/238 PASS
- ✅ 7 个 commit push 至 origin/main

→ **Phase 1a 通过，可进入 Phase 1b**


---

## 决策 13：snapshotDate 来源优先级 + dataTime 字段（2026-07-20）

**状态**：✅ 已锁定（2026-07-20 00:45 收尾落地）

**背景**：

20260716 灰测发现 AI 自动提取的 `snapshotDate` 不可信（P1=2025-12-30, P2=2025-01-20, P4=2025-07-21，全错或 null），用户实际数据时间 2026-07-16 22:39 与 watcher 文件名时间戳 2026-07-20 00:48 不一致。

**决策**：

`snapshotDate` 来源优先级（最终值写入 `asset_raw.snapshot_date` / `asset_snapshot.snapshot_date`）：
1. **首选**：前端从 EXIF DateTimeOriginal 提取 + 用户确认（`dataTime` 字段）
2. **fallback**：用户不输入 → 后端 `LocalDate.now()`（前端弹警告"数据时间可能不准确"）
3. **最差**：AI 模型提取（仅作辅助显示，**不写库**）

**实现**：

后端（已完成 2026-07-20 00:45）：
- `ScreenshotParseRequest` / `ScreenshotBatchParseRequest` 新增 `dataTime: LocalDate` 字段（optional）
- `ScreenshotService.parse()` / `parseBatch()` 接受 dataTime 覆盖 AI 提取的 snapshotDate
- mvn compile 通过（BUILD SUCCESS）
- mvn test 通过（238/238，无回归）

前端（决策 13 派生，1b+ follow-up）：
- 上传 UI 显示"AI 提取时间: <data>" + 用户可手动改
- 透传到后端 `dataTime` 字段
- 远期（暂不考虑）：用户截图加备注，model 解析（避免多一个 prompt）

**理由**：
- AI 提取日期不稳定（多模态模型擅长分类不擅长精确日期），不直接信任
- EXIF DateTimeOriginal 是图像本身的拍摄时间，最准确
- 业务语义：用户上传的是 7-16 截图，data 应该是 7-16，不是上传日 7-20

**回退条件**：
- 1b 前端未实现时，dataTime 字段保持 null，走 AI 提取路径（接受 5.02 元类似误差）
- 4 张图属于同一时点（同一账户同一日），dataTime 共享同一值

---

## 决策 14（follow-up 建议）：fund 分类 AI 辅助 + 用户自定义（1b+）

**状态**：⏳ 待前端实现

**背景**：
- 后端 FundCategoryResolver 4 优先级：user_correct > any-source > fromAlias > raw
- 后端 7 大类无"混合类"（1a.5 已淘汰），mixed-asset 边界需前端 AI 辅助 + 用户 override
- 灰测发现安信新价值灵活配置混合A 类别误分类（AI 归 A股权益类，DeepSeek 归 固收类）

**建议**：
- 前端 1b+ 设计 fund 分类 override 机制
- UI：每个 fund 显示 AI 默认分类，用户可下拉/搜索 7 大类 或 自定义文本
- 后端已存在 `POST /api/category-map/update` 端点接受 user_override
- 数据：调用现有 `fund_category_map` 表，source='user_correct' 覆盖 source='ai_guess'

**优先级**：1b+ 必做（mixed-asset 边界 case 影响 ≥1% 用户）

---

## 决策 13 实施补全（2026-07-20 02:00）

**状态**：✅ 已实施

**实施位置**：
- `ScreenshotParseRequest.java` / `ScreenshotBatchParseRequest.java` / `ScreenshotReparseRequest.java`：新增 `dataTime: LocalDate` 字段（optional）
- `ScreenshotService.java`：
  - 新增私有方法 `applyDataTimeOverride(asset, dataTime, ctx)`（单元测试可见）
  - `parse()` / `parseBatch()` / `reparse()` 三处调用，覆盖 AI 提取的 snapshotDate
  - 优先级：`req.dataTime`（前端 EXIF / 用户选择） > `mapToParsedAsset` AI 解析值 > `LocalDate.now()`（dedupDate 兜底）
- `ScreenshotServiceTest.java`：新增 3 个单元测试（dataTime override / null / helper 直接测）

**核心代码**（`ScreenshotService.applyDataTimeOverride`）：

```java
static void applyDataTimeOverride(ParsedAsset asset, LocalDate dataTime, String ctx) {
    if (asset == null) return;
    if (dataTime == null) {
        log.debug("决策13: dataTime=null，ctx={} 保持 AI 解析 snapshot_date={}", ctx, asset.getSnapshotDate());
        return;
    }
    String old = asset.getSnapshotDate();
    String neu = dataTime.toString();
    asset.setSnapshotDate(neu);
    log.info("决策13: dataTime override ctx={} AI={} → 实际={}", ctx, old, neu);
}
```

**单元测试**：`mvn test` 241/241 PASS（原 238 + 新增 3）

**E2E 验证**（2026-07-20 02:00 fresh JVM）：
- dataTime=2026-07-15 + 灰测图 a808：response.snapshotDate=**2026-07-15**（AI 提取 2026-01-24 被覆盖）✅
- dataTime=2026-07-16 + 灰测图 7ac58b：response.snapshotDate=**2026-07-16**（AI 提取 2026-01-26 被覆盖）✅
- 同图 + dataTime + cache HIT（Replay 3）：response 时间 0.117s，snapshotDate 仍正确覆盖 ✅

**与 cache 关系**：
- 缓存键 = (fileId, promptVersion, imageCount)，**不含 dataTime**
- 缓存内容 = AI 推理结果（含 AI 提取的 snapshotDate）
- dataTime 在缓存返回后被 override
- 同图 24h 内 cache HIT，无论 dataTime 传什么，AI 结果一致；dataTime 在返回前覆盖
- 设计合理性：既保证性能（cache 复用 AI 结果）又保证灵活性（用户可指定任意日期）

**已修复历史债务**：原 `replace_in_file` 工具问题导致 Service 覆盖逻辑未生效（仅 DTO 字段补了），本次通过 `write_to_file` 完整重写 ScreenshotService.java 解决

**灰测仍 PASS**（19/19 fund、19/19 amount、18/19 holding+cumulative、top=7850.38 全部对齐 DeepSeek）

**前端 follow-up**（1b+）：
- 从 EXIF DateTimeOriginal 提取真实数据日期，透传到 `dataTime` 字段
- UI 提供日期选择/确认弹窗
- 用户不选时 fallback 到 today（最差兜底）
- 远期：支持用户对图加注（"这张是 7-16 的补传"）作为 prompt 上下文


---

## 决策 15：Chat prompt 已知缺陷（1a.10 实测发现，待 1b+ 聚合修复）

**状态**：📝 记录中（1a.10 真实 E2E + 灰测 3 例 chat 用例发现）

### ⚠️ 重要澄清：两个 prompt 版本空间

项目内有**两套独立的 prompt 版本空间**，决策 15 涉及的是 **chat prompt（文本对话模型）**，**不是 vision prompt（截图解析）**：

| prompt_name | 用途 | 模型 | 当前版本 | 版本号常数位置 |
|---|---|---|---|---|
| `ai_assistant` | **Chat 文本对话**（决策 15 相关）| minimax 文本 | **v1.0**（硬编码 `ChatService.PROMPTVersion="ai_assistant v1.0"`）| `ChatService.java:40` |
| `screenshot_parser` | 截图解析（1a.9 升级）| minimax 多模态 | **v2.7.1**（1a.10 过程性验收报告迭代）| DB `prompt_versions` 表（SELECT ORDER BY id DESC LIMIT 1）|

> **2026-07-20 21:42 用户澄清**：之前我混淆了这两个 prompt 空间。**vision prompt (screenshot_parser) v2.7.1 是真的**（1a.10 过程性验收报告已升级），但 **chat prompt (ai_assistant) v1.0 是另一个独立版本号**。本次决策 15 全部关于 chat prompt。

### 触发场景（2026-07-20 17:08 复测 chat API）

| # | 用户输入 | intent | routedTo | promptVersion | AI 回复要点 |
|---|---|---|---|---|---|
| 1 | "我的黄金持续低迷，我应该怎么办，割肉吗？" | ✅ true | main_loop | ai_assistant v1.0 | "规则系统只按比例调配，不预测市场，**不建议割肉**..." |
| 2 | "今天星期几？" | ❌ false | garbage_loop | garbage_loop | "抱歉无法得知当前日期" |
| 3 | "我们这个系统是做什么的？" | ❌ false | garbage_loop | garbage_loop | "由上海稀宇科技开发的 MiniMax AI..."（**答非所问**） |

**复测响应原始数据**（`fincontrol-backend/logs/chat-test-{1,2,3}-resp.json`）：
- promptVersion: `ai_assistant v1.0`（确认当前版本）
- intent.classification.latencyMs: 600-833ms
- assistantMessage.latency: 2.3-7.7s

### 根因分析（2 个独立问题，针对 chat prompt `ai_assistant v1.0`）

#### 问题 A：Prompt v1.0 缺 FinControl 系统上下文
- **现状**：`ai_assistant v1.0` prompt 内容存于 DB `prompt_versions` 表（`prompt_name='ai_assistant'`），由 `ChatService` 通过 `PromptLoaderService` 加载
- **内容**：未含"你是 FinControl..."的角色定义（pure investment model）
- **后果**：AI 答"我是 MiniMax"（minimax 训练数据来源），用户无法识别 FinControl 系统
- **优先级**：**中**（仅影响 meta 类问题；正常投资类问题回答正确）

#### 问题 B：IntentClassifier 不识别 self-intro 类
- **现状**：IntentClassifier 训练样本缺 "我们这个系统是做什么的" → 应识别为 `system_intro` 路由
- **后果**：meta 问题被误判为 `garbage_loop`，走 fallback prompt（"我无法回答"）
- **优先级**：**中**

### 不立即修复的原因（2026-07-20 与用户达成共识）

1. prompt 反复改会污染 vision cache（cache 键含 promptVersion）
2. 单元测试 mock AI 客户端不易覆盖真实 prompt 行为
3. **1b+ 真实用户流量才能定 prompt 优化方向**（避免凭空设计）
4. 节省开发时间（3-4 小时临时优化 vs 0 小时记录 + 1b+ 聚合）

### 修复时机

**1b+ 前端完成后聚合修复**（届时已具备端到端测试能力 + 真实用户流量）。

### 修复内容

| 修改 | 详情 |
|---|---|
| **Prompt v1.0 → v1.1** | DB 表 `prompt_versions` 插入新行 `prompt_name='ai_assistant', version='v1.1'`，在 system prompt 头部添加："你是 FinControl（个人资产配置控制系统），基于控制论反馈环帮助用户做资产配置分析。..."（~50 tokens）|
| **ChatService.PROMPTVersion** | 同步更新为 `"ai_assistant v1.1"` |
| **IntentClassifier 训练样本** | 补充：self-intro / 闲聊 / 命令类 50+ 样本，新增 `system_intro` intent 类别 |
| **单测覆盖** | 补 3 个用例（黄金类 / 星期类 / 系统介绍类）|

### 延迟项（**非本次范围**）

- **系统时钟问题**（用例 2）：用户 2026-07-20 决定推 **Phase 4 UX 优化**再处理
  - 阶段 4 短期方案：1a.11+ 注入 `LocalDate.now().toString()` 到 prompt 上下文
  - 阶段 4 远期方案：1b+ 前端传时间
  - **本决策 15 不含此项**（单独追踪）

### 详细跟踪

见 `docs/phase-1/chat-prompt-issues.md`（聚合所有 chat prompt 缺陷 + 修复 checklist + 后续新增问题登记表）

### 完成判定

1b+ 前端完成 + 真实流量跑 2 周 + 聚合 ≥ 5 类相似问题后统一修复

---

---

## 决策 16：图表库选型 = Recharts（2026-07-21）

**状态**：✅ 已锁定

**背景**：Phase 1a 时前端脚手架沿用了 ECharts（与后端图表组件复用）。Phase 1b 启动时发现：
- ECharts API 是 option 对象（命令式），React 心智负担重
- 包体大（~1MB），与"工科风 + 简洁"原则不符
- Recharts 是 React-native 组件（声明式），与 React 心智一致
- 包体小（~100KB）

**决策**：Phase 1b 及之后图表统一用 Recharts。移除 `echarts` + `echarts-for-react`。

**影响**：
- package.json：移除 2 个包，新增 `recharts`
- 1b.2 首页环形图改用 `<PieChart><Pie data={...} />`
- 1b.3 数据管理无影响
- Phase 3 净值曲线仍用 Recharts（趋势线、面积图）

---

## 决策 17：UI 库选型 = 纯 CSS（2026-07-21）

**状态**：✅ 已锁定

**背景**：Phase 0 时前端脚手架装了 `antd`（Ant Design），是 antd 5.x（~700KB）。Phase 1b 启动时发现：
- antd 样式与"工科风"不符（圆角大、颜色鲜）
- 包体大（~700KB）
- 简历价值：手写 CSS 展示前端功底
- Phase 1b 只需极少量组件（Toast / Modal / Button），纯 CSS < 50 行可实现

**决策**：Phase 1b 不引入任何 UI 组件库，纯手写 CSS。移除 `antd`。

**影响**：
- package.json：移除 `antd`
- 自实现：`Button` / `Toast` / `Modal` 等基础组件（每个 < 50 行 CSS + JSX）
- Phase 2/3 仍坚持此原则（除非 antd 的复杂组件如 DatePicker / Table 能显著降低工作量）

---

## 决策 18：Phase 1b 文档目录约定（2026-07-21）

**状态**：✅ 已锁定

**背景**：Phase 1a 阶段的 work-plan 和 acceptance 文档散落在 docs/phase-1/work-plans/ 和 docs/test-records/manual-tests/ 根目录，文件多不便查找。

**决策**：Phase 1b 起按以下子目录隔离：
- docs/phase-1/work-plans/0/ = Phase 0 工作计划（暂留）
- docs/phase-1/work-plans/1a/ = Phase 1a 工作计划（已存在 10 份）
- docs/phase-1/work-plans/1b/ = Phase 1b 工作计划（新建）
- docs/test-records/manual-tests/0/ = Phase 0 验收报告
- docs/test-records/manual-tests/1a/ = Phase 1a 验收报告（已存在 30+ 份）
- docs/test-records/manual-tests/1b/ = Phase 1b 验收报告（新建）

**影响**：所有未来 Phase 继续此约定，保持目录结构清晰。

**注**：phase-0/decisions.md 本身保持文件位置（不在子目录），作为全局决策的单一权威源。
1b.1 实装确认：✅ 已完成，59 文件 rename，8 个新文件创建。

---

## 决策 19 详述：阶段验收必更新根目录 README（2026-07-21）

**状态**：✅ 已锁定

**背景**：1b.1 实装完成（commit `60f90eb`，34 文件 / 16 用例 100% PASS / 联调 HTTP 200）时，根目录 `README.md` 的"项目状态"表未及时更新（仍显示"🟡 准备启动"），导致对外展示与实际进度脱节。

**决策**：未来每次**阶段性质验收完成时**（如 1b.1 / 1b.2 / 1b.3 / 1b.4 / Phase 2 整体等），必须同步更新根目录 `README.md` 的"项目状态"段，包括：
- 当前所处阶段
- 验收日期
- 验收报告链接
- 下一阶段状态（⏳ 待启动 / 🟡 进行中 / ✅ 已完成）

**影响**：
- 文档驱动开发的纪律强化
- 任何协作者打开仓库根目录就能看到最新进度
- 与"工作流：文档优先 → 计划 → 实施 → 验收 → 追加文档"的工作公约一致

**触发时机清单**：
- 1b.1 实装完毕（✅ 已补 1b.1/2/3/4 行 + 整体行）
- 1b.2 / 1b.3 / 1b.4 完成时
- Phase 1b 整体完成时
- Phase 2 / 3 / 4 / 5 各阶段完成时
- 任何子仓库级别的重要变更

**实装参照**：1b.1 完成时 README 行 28 从单行 "1b.1–1b.4 前端对接 | 🟡 准备启动" 拆为 5 行（1b.1/2/3/4 + 整体），准确反映完成度。

---

## 决策 20 详述：根目录 \`test/\` 文件夹规范（2026-07-22）

**状态**：✅ 已锁定

**背景**：
- Phase 1b 进入 1b.2 编码后，前后端联调联试需求显著增加（特别是累计收益率简化算法的真实数据验证）
- 当前的'过程性小测试记录、临时脚本、联调产物'散落在 /tmp/、项目根、聊天工具输出等地方，事后无法追溯
- 测试数据涉及个人实盘账户（0720 标号的 4 张支付宝截图），**绝不能上传 GitHub**

**决策**：

> 从 1b.2 起，所有**过程性测试产物**统一存放在**仓库根目录 /test/** 文件夹，**永不提交 GitHub**。

**目录结构**：

\`\`\`
<repo-root>/test/                          ← 不上传 GitHub（已在 .gitignore）
├── 1b/                                    ← 按 Phase 隔离
│   ├── 2026-07-22-upload-4-screenshots.md  ← 联调过程记录
│   ├── start-frontend.sh                  ← 临时启动脚本
│   └── screenshots-html/                  ← 上传后的截图副本（可选）
├── 2/
└── ...
\`\`\`

**每个测试文件必须包含**：
1. **存放位置说明段**：原始测试数据在哪（如'4 张原始截图：fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-{1,2,3,4}.jpg'）
2. **何时何地去向说明**：何时创建、何用于哪个阶段、归档后是否可删除

**.gitignore 已包含**：test/ 已在 1b.1 完工时添加。

**影响**：
- 过程性产物可追溯（一旦 webview 崩溃或会话中断，能从 /test/ 恢复）
- 敏感测试数据（如真实截图）永不入仓
- 联调产物与正式验收报告（docs/test-records/manual-tests/）物理隔离

**回退条件**：如 Phase 2+ 联调产物激增导致 /test/ 杂乱，可改为按 1b/2/3/4/5/ 子目录 + 时间戳前缀。

---

## 决策 21 详述：\`uploads/screenshots/\` 缓存清理规范（2026-07-22）

**状态**：✅ 已锁定

**背景**：
- 1b.2 编码前用户指出：uploads/screenshots/ 目录文件数量'随着测试次数增加而增加'——经查证，这是**后端运行时缓存的截图文件**（36 进制 hash 文件名如 05588f9b...jpg）
- 该目录**不是**测试样本归档区（那是 uploads/samples/，由 FileSystemWatcher 重命名）
- 1a 期间累计 ~180 张缓存文件（~50 MB）未清理，每次 POST /api/screenshot/upload 都会创建一张新缓存
- 当前**没有任何清理机制**：缓存会无限增长

**决策**：

> 1. **1b.2 测试不依赖 uploads/screenshots/**（用 samples/ 目录的 4 张 fixed 测试样本联调）
> 2. **Phase 2 末期补清理脚本**：scripts/cleanup-screenshots-cache.ps1，按**保留最近 N 天**策略清理
> 3. **当前**（2026-07-22）：保留现有 180 张缓存，作为 1a 历史样本；1b.2 测试时只读 samples/ 目录
> 4. **未来**：后端可考虑加 cleanup 钩子（@Scheduled 每周清理一次，保留 7 天内）

**两个 uploads/ 子目录的角色区分**：

| 目录 | 角色 | 文件名规范 | 是否清理 |
|---|---|---|---|
| uploads/samples/ | 手动归档（FileSystemWatcher 重命名后的永久样本）| {phase}-{vendor}-{scenario}-{yyyyMMdd-HHmm}-{seq}.jpg | 否（历史归档） |
| uploads/screenshots/ | 运行时缓存（后端接收上传的临时存储）| 36进制uuid.jpg | 是（7 天清理） |

**1b.2 测试数据源**（仅用 samples/，不用 screenshots/）：
- ✅ fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-{1,2,3,4}.jpg
- 标号 0720（文件名时间戳）｜真实截图数据日期 0716｜本次 1b.2 联调联试用

**影响**：
- 1b.2 联调不会产生新缓存（samples/ 是只读样本，由后端扫描并归类为 is_latest=true）
- Phase 2 添加清理脚本后，screenshots/ 大小可控
- 后端短期可不动；长期建议加 @Scheduled 自动清理

**回退条件**：如后端运行依赖 screenshots/ 中的中间文件（如解析失败的暂存），则不在 7 天清理范围内。

---

## 决策 22 详述：决策总结表位置约定（2026-07-22）

**状态**：✅ 已锁定

**背景**：
- 2026-07-22 用户发现：之前的"决策总结表（持续追加）"块在决策 18 后、决策 19 详情前，位置错误（应是文档最末尾）
- 这导致每个新增决策都要"顺手更新中间表"，维护路径扭曲
- 用户明令规定：决策总结表必须位于整个文档的**最后**，且每次**新增决策必须就地更新末尾的汇总表行**

**决策**：

> \`docs/phase-0/decisions.md\` 的 **决策总结表始终位于文档最末尾**。每新增一条决策：
> 1. 先在合适位置插入"决策 N 详述"段（可按时间或主题分组）
> 2. **然后**刷新文档最末尾的"决策总结表"，新增一行 #N
> 3. 永不**重复**插入中间的旧表块

**维护机制**：
- 旧的"决策总结表（持续追加）"块已删除（2026-07-22，已在此决策前移除）
- 现在的策略是**单一时点表**——文档末尾的"决策总结表（追加后）"始终是最新完整视图
- 如需历史快照，看 git log（决策追加时建议同时 commit）

**不允许的操作**：
- ❌ 在文档中间再加任何形式的"决策总结表"
- ❌ "## 决策总结表（持续追加）"这种标题（已废止）
- ❌ 保留两份以上的汇总表

**影响**：减少文档维护错误；保证未来读文档的人只看一份汇总表

**回退条件**：如未来文档结构变化（如改为多文件），本约定自动失效，需重新约定。

---

## 决策 23 详述：文档更新必 commit + push（2026-07-22）

**状态**：✅ 已锁定

**背景**：
- 2026-07-22 用户明令：每次做出文档更新（work-plan / acceptance-plan / acceptance-report / decisions 等）后都必须先 commit
- 如果网络连通则一并 push（git push origin main），不要等用户提醒
- 此前 1b.1 / 1a 期间多次出现"文档写完没及时 commit"的情况，导致 webview 崩溃时丢失进度

**决策**：

> 任何对仓库根目录或 docs/ 下的 markdown 文档做出可工作的更新后：
> 1. 立刻 `git add <files>`
> 2. 立刻 `git commit -m "<type>(<scope>): <subject>"`（commit message 简明）
> 3. 检测网络：能 push 就 `git push origin main`
> 4. 如 push 失败（proxy / 401 / 离线），在 commit message 后追加 `(push-deferred)`，并把"待 push"清单写到 `/test/` 下次提醒

**commit message 约定（沿用 1a 风格）**：
- `docs: <一句话描述>` — 纯文档
- `docs(1b.X): <一句话描述>` — 阶段性文档
- `fix(docs): <一句话描述>` — 修复（乱码、错位、漏字等）
- `chore(decisions): <一句话描述>` — decisions 维护

**不在本决策范围内的提交**：
- src/ 下的代码修改 → 走正常 dev workflow（先 plan → 实施 → 验证 → 文档 → commit）
- log/ 临时调试产物 → 不提交（.gitignore 已含）
- test/ 联调记录 → 不提交（决策 20）

**影响**：
- webview 崩溃或会话中断时，进度可从 git log 恢复
- 决策 19 "阶段验收必更新 README" 的 commit 时机更明确
- 与 "文档优先 → 计划 → 实施 → 验收 → 追加文档" 工作流一致

**回退条件**：如未来改用其他 VCS（如 svn / pijul），本约定自动失效。

---

## 决策 24：后端 restart 必须用 `scripts/1b/restart-backend.ps1`（2026-07-22）

**状态**：✅ 已锁定

**背景**：
- 1b.2 step 7 端到端联调发现：手动 `Start-Process java -jar` 启动的后端进程（PID 40308）持锁 `target/fincontrol-backend.jar`
- 后续 `mvn package` 反复在 `spring-boot-maven-plugin:repackage` 阶段失败：`Unable to rename ... to .jar.original` (file lock)
- 用户洞察："1b.3、1b.4 都要联调，jar 绕不过去，这是把雷放到后面炸了"

**决策**：
所有后端代码改动后，**必须**用 `scripts/1b/restart-backend.ps1` 脚本重启后端，**禁止**手动 `Start-Process java -jar` 或 `mvn spring-boot:run`。脚本保证以下 5 步幂等：

1. 杀 java.exe（**只杀 fincontrol 后端，保留 VSCode JDT-LS**）
2. 清 `fincontrol-backend/target/`
3. `mvn -f pom.xml package -B -DskipTests`（rebuild）
4. 启动新后端（重定向 stdout/stderr 到 `log/`）
5. 30s 内 healthcheck（`GET /actuator/health` = UP）

**影响范围**：
- 1b.3 / 1b.4 联调：每次改后端代码后必须用脚本，jar 失败风险 → 0
- Phase 2/3：所有后端改动继承此规范
- 文档要求：任何新 dev 必须先看 `scripts/README.md` 了解此 SOP

**理由**：
- 手动操作幂等性差（容易漏杀进程、忘清 target、忘健康检查）
- 脚本化后**单点失败可重试**（每次跑都从干净状态开始）
- 决策 23（commit + push）要求所有文档化，SOP 写在脚本 + decisions.md 两处

**回退条件**：无（jar 重建是 Phase 1+ 所有联调的基础设施）

**实现位置**：`scripts/1b/restart-backend.ps1`（纯 ASCII 版，避免 Windows GBK 解析错误）

*最近更新：2026-07-22 追加决策 24（后端 restart SOP） + 1b.2 step 7 端到端联调通过*
*触发：1b.2 联调 jar 重建暴露 file lock，用户洞察“1b.3/1b.4 都要联调，jar 绕不过去”*

---

## 决策 25 v3：累计 + 持有 收益 + 双保险 + 展示层 Smart Fallback（2026-07-22）

**状态**：✅ 已锁定（v1：累计 + 双保险 / v2：扩展持有 + 双列 + 历史 / v3：展示层余额宝 Smart Fallback）

**v2 → v3 变更动机**（2026-07-22 15:20）：
- 1b.2 step7 联调发现：接口返 holdingProfit = -36.55，**用户人工核对认为应是 -34.65**（差 1.90 = 余额宝 cumulative 1.90）
- 根因：原图解析时**余额宝的「持有收益」字段显示为空**（用户尊重原图 → holding=NULL），但累计 1.90
- 1a 解析层设计原则：尊重原图（holding=NULL 不补 0 也不补 cumulative），但展示时需校正
- Phase 2 重构：写库层 confirm 时对「余额类 + holding IS NULL」自动补 holding=cumulative
- 1b.2 范围内：只在 Service 层做"展示层 smart fallback"，不动数据层，确保 Phase 2 重构时不影响调用方

**背景**：
- 1b.2 累计收益接口在 step7 端到端联调中暴露**测试数据污染**问题：
  - `asset_raw` 表共 38 行 is_latest=1
  - 拆分：`user_id=1` 19 行（2026-07-16）+ `user_id=19999` 19 行（2026-07-19）
  - mapper SQL 查 `user_id=1`，所以接口返 -29.63（正确）
  - 但未来如果 confirm 流程漏 user_id 过滤、或测试数据混用 user_id=1，会导致**累计收益混入旧数据**
- 1a.3 confirm 流程的 `SnapShotConfirmService.writeAssetRaw` line 210 直接 `assetRawMapper.insert(row)`，**缺前置** `UPDATE asset_raw SET is_latest=0 WHERE user_id=? AND snapshot_date<>?`（防同 user 旧 snapshot_date 残留）
- 用户明令："**应该把已经确认的 is_latest 置为零**（假设用户不会回补曾经的数据，这个点也要明确）"
- 1b.2 实装后用户反馈：累计 vs 持有 概念不清，**前端需双列展示 + ℹ️ 弹窗**让用户理解两个概念
  - 累计 = Σcumulative_profit（含已实现，自建仓以来所有盈亏）
  - 持有 = Σholding_profit（仅当前持仓的浮盈/亏，不含已实现）
- 未来实现历史查询：不需要加 asset 字段，靠 `snapshot_date` 区分历史（`is_latest=0` 即历史）
- **未来 is_latest 扩展**（Phase 2）：对于一个自然日允许多次上传时，需要 (user_id, snapshot_date, version) 三元组；多个自然日之间需要标定"哪天是最新上传"——但 1b.2 暂不动

**决策**：
累计收益查询采用**数据层 0 改动 + 展示层 smart fallback**机制（v3 一次性定稿）：

1. **数据层 0 改动**（已完成）：`asset_raw` 表 + `is_latest` 字段 + confirm 流程都不动。1a 解析时**尊重原图**（余额宝 holding=NULL 不补 0）。
2. **算法层双保险**（已完成）：`AssetRawMapper.sumReturnFieldsByUser` SQL 加 `snapshot_date = (SELECT MAX(snapshot_date) FROM ...)` 子查询过滤 + `snapshot_date = MAX(snapshot_date)` 返回。即使 is_latest 标错，也只取最新一天。
3. **DTO 扩展**（已完成）：`CumulativeReturnResponse` 加 4 字段：
   - `totalHoldingProfit`：校正后持有 = raw + adjustment
   - `rawHoldingProfit`：原值 = -36.55
   - `balanceFundAdjustment`：余额宝校正值 = +1.90
   - `balanceFundStatus`：`'normal'` / `'included'` / `'excluded_unknown'`
4. **展示层 Smart Fallback**（v3 新增）：`AssetQueryService.computeBalanceAdjustment()` 私有方法查余额类：
   - 余额类 + holding IS NULL + cumulative 非空 → adjustment=cumulative, status=`included`
   - 余额类 + holding IS NULL + cumulative IS NULL → adjustment=0, status=`excluded_unknown`
   - 余额类 + holding 非空 → adjustment=0, status=`normal`
   - 没余额类持仓（清仓/从未持有）→ adjustment=0, status=`normal`
5. **前端双列 + ℹ️ 弹窗**（已完成）：`CumulativeReturnCard` 拆"累计 / 持有"两列，每列显示"率 + 额"，4 个 ℹ️ 按钮弹 InfoModal。
6. **ℹ️ 弹窗行为**（v3 新增）：
   - 持有收益的 ℹ️ 弹窗**根据 status 动态显示**：
     - `status='normal'`：不显示提示（正常展示，无调整）
     - `status='included'`：「原持有收益 X 元，加上余额宝的累计收益 Y 元，持有收益更正为 Z 元」
     - `status='excluded_unknown'`：「余额宝未解析（可能清仓或解析错误），请检查您的持仓」
   - 累计收益的 ℹ️ 弹窗不变（始终显示累计定义）
7. **测试数据清理**（已完成）：`DELETE FROM asset_raw WHERE user_id != 1`（2026-07-22 已执行）。
8. **不回补假设**：用户**不会**要求从已删除/已标记的 snapshot 恢复数据。累计收益永远按"最新一天"算，历史回放由 1a.4 `/api/snapshot/{date}` 接口承担。

**v3 vs v2 关键区别**：
- v2：直接返 `totalHoldingProfit`（原 SUM 值）
- v3：返 `totalHoldingProfit`（校正后）+ `rawHoldingProfit`（原值）+ `balanceFundAdjustment`（调整值）+ `balanceFundStatus`（状态）。前端展示用 `totalHoldingProfit`，ℹ️ 弹窗根据 `status` 决定显示内容。

**算法层 SQL**（当前生效，v2 含 holding + 元数据）：
```sql
SELECT 
  COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit,
  COALESCE(SUM(holding_profit), 0) AS total_holding_profit,
  COALESCE(SUM(amount), 0) AS total_amount,
  COUNT(*) AS fund_count,
  MAX(snapshot_date) AS snapshot_date
FROM asset_raw
WHERE user_id = #{userId}
  AND is_latest = 1
  AND snapshot_date = (SELECT MAX(snapshot_date) FROM asset_raw
                       WHERE user_id = #{userId} AND is_latest = 1)
```

**累计 vs 持有 区别**（前端双列定义弹窗）：
| 指标 | 公式 | 定义 |
|---|---|---|
| 累计收益率 | Σcumulative_profit / Σamount | 自建仓以来所有盈亏（含已实现，partial sell 也算入）|
| 累计收益 | Σcumulative_profit（元）| 累计盈亏绝对值 |
| 持有收益率 | Σholding_profit / Σamount | 当前仍持仓的浮盈率（不含已实现）|
| 持有收益 | Σholding_profit（元）| 当前持仓的浮盈/亏绝对值 |

**Phase 2 重构路径**（不阻碍此次实现）：
- **写库层修正**（Phase 2）：`SnapShotConfirmService.writeAssetRaw` 对 `category='余额类' AND holding_profit IS NULL` 自动 `holding=cumulative`
- **is_latest 扩展**（Phase 2）：从单一 bool 字段升级为 `(user_id, snapshot_date, version)` 三元组表，多次上传不再冲突
- **历史查询接口**（Phase 2）：`GET /api/asset/cumulative-return/at-date?userId=&snapshotDate=` 按 `snapshot_date = ?` 过滤（不限 is_latest）
- **Service 方法替换路径**（Phase 2）：`computeBalanceAdjustment()` 改方法体即可（调用方不变），因为展示层 fallback 始终冗余防护

**写库层 SQL**（Phase 2 实施，本决策待补）：
```java
// 在 SnapShotConfirmService.writeAssetRaw 函数 insert 前
assetRawMapper.updateIsLatestByUserExcludingDate(userId, req.getSnapshotDate());
// + 余额类 fallback（Phase 2）
assetRawMapper.fillMissingBalanceHolding(userId);  // UPDATE asset_raw SET holding_profit=cumulative_profit WHERE category='余额类' AND holding_profit IS NULL AND cumulative_profit IS NOT NULL
```

**历史查询接口**（Phase 2 实施，本决策预留）：
```java
// 新增 mapper 方法（Phase 2）
@Select("SELECT ... FROM asset_raw WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
Map<String, Object> sumReturnFieldsByUserAndDate(...);
```

**未来扩展**：
- Phase 3 升级为 Modified Dietz / XIRR 时，DTO 字段 `algorithm` 从 `phase1_simple` 改为 `phase3_dietz` / `phase3_xirr`
- 净值曲线（`/nav-history` 页面）调用 `at-date` 接口画历史曲线

**影响范围**：
- 1b.2 联调：累计收益接口稳定返 7 字段（-29.63 / -36.55 / -34.65 / 1.90 / 'included' / 7850.38 / 2026-07-16 / 19）
- 1b.3/1b.4：写库层加固 + 历史接口 + is_latest 扩展 Phase 2 补，不阻塞 1b 推进
- Phase 2：必须实现写库层加固（移除展示层 fallback）+ 累计收益每天接口 + is_latest 三元组

**理由**：
- 数据层 0 改动：保护原图解析语义，Phase 2 重构时不破已存储数据
- 展示层 smart fallback：1b.2 立刻可用，不等 Phase 2
- Service 方法封装：Phase 2 替换简单（只改方法体，不动调用方）
- 不阻碍 is_latest 扩展：未来三元组化时本补丁不破

**回退条件**：无（用户不补旧 snapshot，决策 25 v3 永久生效）

**实现位置**：
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetRawMapper.java`（已修：方法名 `sumReturnFieldsByUser` + 加 fund_count + snapshot_date）
- `fincontrol-backend/src/main/java/com/fincontrol/dto/asset/CumulativeReturnResponse.java`（v3 加 4 字段：rawHoldingProfit / balanceFundAdjustment / adjustedHoldingProfit / balanceFundStatus + totalHoldingProfit 改用校正后值）
- `fincontrol-backend/src/main/java/com/fincontrol/service/AssetQueryService.java`（v3 加 `computeBalanceAdjustment()` 私有方法）
- `fincontrol-frontend/src/components/CumulativeReturnCard.jsx`（v3 持有 ℹ️ 弹窗按 status 动态显示）
- `fincontrol-frontend/src/styles/cumulative-return.css`（新建：双列网格 + 模态框 + 通用 .card）

*最近更新：2026-07-22 升级决策 25 → v3（数据层 0 改动 + 展示层 smart fallback） + Phase A 已 commit+push*
*触发：1b.2 step7 联调发现 holding=-36.55 与用户预期 -34.65 差 1.90 元，根因是余额宝 holding 字段原图为 NULL；用户确认"不修解析结果，只在展示层做校正 + 提示文案"*

**背景**：
- 1b.2 累计收益接口在 step7 端到端联调中暴露**测试数据污染**问题：
  - `asset_raw` 表共 38 行 is_latest=1
  - 拆分：`user_id=1` 19 行（2026-07-16）+ `user_id=19999` 19 行（2026-07-19）
  - mapper SQL 查 `user_id=1`，所以接口返 -29.63（正确）
  - 但未来如果 confirm 流程漏 user_id 过滤、或测试数据混用 user_id=1，会导致**累计收益混入旧数据**
- 1a.3 confirm 流程的 `SnapShotConfirmService.writeAssetRaw` line 210 直接 `assetRawMapper.insert(row)`，**缺前置** `UPDATE asset_raw SET is_latest=0 WHERE user_id=? AND snapshot_date<>?`（防同 user 旧 snapshot_date 残留）
- 用户明令："**应该把已经确认的 is_latest 置为零**（假设用户不会回补曾经的数据，这个点也要明确）"
- 1b.2 实装后用户反馈：累计 vs 持有 概念不清，**前端需双列展示 + ℹ️ 弹窗**让用户理解两个概念
  - 累计 = Σcumulative_profit（含已实现，自建仓以来所有盈亏）
  - 持有 = Σholding_profit（仅当前持仓的浮盈/亏，不含已实现）
- 未来实现历史查询：不需要加 asset 字段，靠 `snapshot_date` 区分历史（`is_latest=0` 即历史）

**决策**：
累计收益查询采用**双保险 + 持有扩展 + 双列展示**机制（v2 一次性定稿）：

1. **算法层加固**（已完成）：`AssetRawMapper.sumReturnFieldsByUser` SQL 加 `snapshot_date = (SELECT MAX(snapshot_date) FROM ...)` 子查询过滤 + `snapshot_date = MAX(snapshot_date)` 返回。即使 is_latest 标错，也只取最新一天。
2. **DTO 扩展**（已完成）：`CumulativeReturnResponse` 加 5 字段（`totalHoldingProfit` / `holdingReturnRate` / `snapshotDate` / `fundCount` + 原有 cumulative / rate / amount / algorithm / available / message）
3. **写库层加固**（Phase 2 实施）：`SnapShotConfirmService.writeAssetRaw` 写新 batch 前先 `UPDATE asset_raw SET is_latest=0 WHERE user_id=? AND snapshot_date<>?`。
4. **前端双列 + ℹ️ 弹窗**（已完成）：`CumulativeReturnCard` 拆"累计 / 持有"两列，每列显示"率 + 额"，4 个 ℹ️ 按钮弹 InfoModal 显示定义 / 公式 / 算法。
5. **测试数据清理**（已完成）：`DELETE FROM asset_raw WHERE user_id != 1`（2026-07-22 已执行）。
6. **不回补假设**：用户**不会**要求从已删除/已标记的 snapshot 恢复数据。累计收益永远按"最新一天"算，历史回放由 1a.4 `/api/snapshot/{date}` 接口承担。
7. **未来历史查询接口**（Phase 2 实施）：新增 `GET /api/asset/cumulative-return/at-date?userId=&snapshotDate=`，按 `snapshot_date = ?` 过滤（不限 is_latest）。

**算法层 SQL**（当前生效，v2 含 holding + 元数据）：
```sql
SELECT 
  COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit,
  COALESCE(SUM(holding_profit), 0) AS total_holding_profit,
  COALESCE(SUM(amount), 0) AS total_amount,
  COUNT(*) AS fund_count,
  MAX(snapshot_date) AS snapshot_date
FROM asset_raw
WHERE user_id = #{userId}
  AND is_latest = 1
  AND snapshot_date = (SELECT MAX(snapshot_date) FROM asset_raw
                       WHERE user_id = #{userId} AND is_latest = 1)
```

**累计 vs 持有 区别**（前端双列定义弹窗）：
| 指标 | 公式 | 定义 |
|---|---|---|
| 累计收益率 | Σcumulative_profit / Σamount | 自建仓以来所有盈亏（含已实现，partial sell 也算入）|
| 累计收益 | Σcumulative_profit（元）| 累计盈亏绝对值 |
| 持有收益率 | Σholding_profit / Σamount | 当前仍持仓的浮盈率（不含已实现）|
| 持有收益 | Σholding_profit（元）| 当前持仓的浮盈/亏绝对值 |

**写库层 SQL**（Phase 2 实施，本决策待补）：
```java
// 在 writeAssetRaw 函数 insert 前
int oldCount = assetRawMapper.updateIsLatestByUserExcludingDate(
    userId, req.getSnapshotDate(), isLatest = true
);
```

**历史查询接口**（Phase 2 实施，本决策预留）：
```java
// 新增 mapper 方法（Phase 2）
@Select("SELECT COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit, " +
        "COALESCE(SUM(holding_profit), 0) AS total_holding_profit, " +
        "COALESCE(SUM(amount), 0) AS total_amount " +
        "FROM asset_raw " +
        "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate}")
Map<String, Object> sumReturnFieldsByUserAndDate(...);
```

**未来扩展**：
- Phase 3 升级为 Modified Dietz / XIRR 时，DTO 字段 `algorithm` 从 `phase1_simple` 改为 `phase3_dietz` / `phase3_xirr`
- 净值曲线（`/nav-history` 页面）调用 `at-date` 接口画历史曲线

**影响范围**：
- 1b.2 联调：累计收益接口稳定返 5 字段（-29.63 / -36.55 / 7850.38 / 2026-07-16 / 19）
- 1b.3/1b.4：写库层加固 + 历史接口 Phase 2 补，不阻塞 1b 推进
- Phase 2：必须实现写库层加固 + 累计收益每天接口
- Phase 3：升级算法标识（前端 UI 不变，仅 tooltip 文案变）

**理由**：
- 算法层双保险：写库层 bug 时仍正确
- 不回补假设：累计/持有只看最新一天，避免历史数据干扰
- 测试数据清理：38 → 19 行（user_id=1 单 user），消除污染
- 双列 + ℹ️ 弹窗：用户清晰理解"累计"和"持有"区别（不混淆）
- 不加 asset 字段：未来历史查询靠 `snapshot_date` 区分即可

**回退条件**：无（用户不补旧 snapshot，决策 25 v2 永久生效）

**实现位置**：
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetRawMapper.java`（已修：方法名 `sumReturnFieldsByUser`）
- `fincontrol-backend/src/main/java/com/fincontrol/dto/asset/CumulativeReturnResponse.java`（已修：加 5 字段）
- `fincontrol-backend/src/main/java/com\fincontrol\service\AssetQueryService.java`（已修：toBigDecimal + String.valueOf 防 java.sql.Date cast）
- `fincontrol-frontend/src/components/CumulativeReturnCard.jsx`（已重写：双列 + InfoModal 4 弹窗）
- `fincontrol-frontend/src/styles/cumulative-return.css`（新建：双列网格 + 模态框 + 通用 .card）

*最近更新：2026-07-22 追加决策 25 v2（累计 + 持有 + 双列 + 历史查询） + 1b.2 累计/持有双列实装完成*
*触发：1b.2 step 7 端到端联调 + 用户对累计/持有概念澄清需求 + 未来历史查询架构明确*

---

## 决策 26：parse 模式开关 single \| multi（前端 toggle）（2026-07-22）

**状态**：✅ 已锁定

**背景**：
- 1a.10 阶段已实现双路径并存（4×单图 confirm / 1×parse-batch），但 1a.10 总结中明确路径 B（一次 4 图 batch）存在 PRODUCTION_BLOCKED 风险（minimax 4 图 timeout）
- 1b.2 联合调试继续验证路径 B 不稳定：minimax 60s timeout 频繁命中，用户被迫手动拆分为 4 张单图重试
- 用户原话："真实场景中 4 张图就是同一时点（同一账户同一日），但 minimax 4 图 batch 偶发超时不能依赖，只能以单图为主路"
- 决策 25 v3 已修复累计/持有展示层 fallback，但 parse 路径的可验证性仍需加固

**决策**：

> `parse-batch` 端点新增 `mode=single|multi` 参数，1b.2 1b.3 以 **single 为默认主路**、**multi 为可选备选**，避免 4 图 batch 超时阻塞联调。

1. **后端 - ScreenshotController**：新增 `mode` 参数（默认 `single`），转发到 ScreenshotService。
2. **后端 - ScreenshotService**：新增 `parseBatchSingle()` 方法：
   - 串行调用 N 次 `parse(fileId)`，避开单次 4 图请求的超时上限
   - merge 多结果（DedupEngine 复用现有逻辑）
   - 任一文件失败 → retry 1 次，retry 仍失败则整体返回失败
3. **后端 - parseBatch(req)**：当 `mode=multi` 时保留 1a.10 原 multi 行为；当 `mode=single`（默认）走 `parseBatchSingle` 路径。
4. **前端 - HomePage**：1b.2 联合调试 UI 增加 `mode` 切换开关（默认 single），上传 4 张图时由 toggle 控制走哪条路径。
5. **验证**：1b.2 验收中以 single 模式跑通 19-fund / 7850.38 元完整 confirm，作为 1b.3/1b.4 的可重复路径。

**实现位置**：
- `fincontrol-backend/src/main/java/com/fincontrol/controller/ScreenshotController.java`（新增 `mode` 参数与路由分支）
- `fincontrol-backend/src/main/java/com/fincontrol/service/ScreenshotService.java`（新增 `parseBatchSingle()` 串行入口，保留 `parseBatch()` 1a.10 multi 行为）
- `fincontrol-frontend/src/pages/HomePage.jsx`（新增 toggle 组件，默认 single）

**影响范围**：
- 1b.2：mode=single 跑通，1b.2 acceptance report 验证可重复
- 1b.3/1b.4：前端默认 single，多图场景不再依赖 provider 一次成功
- 1a.10 PRODUCTION_BLOCKED 在 single 路径下被绕开
- Phase 2+：multi 可作为优化路径重新启用（需 provider 异步轮询配套）

**理由**：
- 4 张图是同一时点的同一份数据，串行解析不影响业务语义（每张独立 dedup，merge 后等价）
- 单图超时上限 60s 足够覆盖 4 张图片（按 sequential 估算总耗时 ≤4×60s）
- 用户可以选择 multi 验证 provider 稳定性，但默认 single 保证联调不会阻塞
- 决策 28（前端 timeout 60s→120s）单独生效，不依赖此决策

**回退条件**：无（single 是默认主路，multi 可选保留，不互斥）

**关联 commit**：commit `dcaae96 feat(1b.2): decision 26 parse mode switch + HomePage redesign`

*最近更新：2026-07-22 追加决策 26（parse 模式开关 single 主路 / multi 备选） + 1b.2 联合调试路径可重复验证*
*触发：1a.10 PRODUCTION_BLOCKED + minimax 4 图 batch timeout 频发，用户明令"以 single 为主路"*

---

## 决策 27 详述：is_latest 双层语义 + 跨日期 is_current（2026-07-22）

**状态**：🚧 设计稿（1b.2 补完后端实施 + 1b.3 前端 UI）

**背景**：
- 1b.2 验收签字后 0.72.0 workaround 测试覆盖 0716 19-fund 状态（5 只基金残留）
- 1b.2 补完阶段发现 Bug 1：`SnapShotConfirmService.writeAssetRaw` 缺少 `updateIsLatestBySnapshotDate` 调用，旧行 is_latest 不翻
- Bug 2：`AssetRawMapper.sumAmountByUserAndDateAndCategory` 等 verifyMirror SQL 不加 `AND is_latest=true`，触发 5001 镜像校验

**决策**：
1. **双层 is_latest 语义**：`asset_raw.is_latest` 改为 per-date（每自然日最多 1 行 true），跨日期通过新表 `snapshot_meta.is_current` 表达
2. **新表 `snapshot_meta`**：(id, user_id, snapshot_date, is_latest, is_current, confirmed_at, created_at, updated_at)，UNIQUE (user_id, snapshot_date)
3. **新 API `POST /api/snapshot/set-current`**：切换跨日期当前快照，前端 UI 可手动选择
4. **`GET /api/snapshot/latest` 改为 is_current 查询**（不再 MAX(snapshot_date)）

**Bug 修复（本次 commit 同步上线）**：
- `SnapShotConfirmService.writeAssetRaw` 加 `assetRawMapper.updateIsLatestBySnapshotDate(userId, snapshotDate)` 调用
- `AssetRawMapper.sumAmountByUserAndDateAndCategory` 加 `AND is_latest = true`
- `AssetRawMapper.selectFundNamesByUserAndDate` 加 `AND is_latest = true`

**影响范围**：
- 1b.2 补完：DB 清理 + Bug 修复 + 决策 文档（同 commit f42b323）
- 1b.3：前端上传 UI + toggle + date picker + is_latest 按钮（决策 27 §2.5）
- 1b.4+：snapshot_meta 表新增 + Service 接入 + set-current API

**理由**：
- 避免 1b.2 验收后 5-Fund 状态覆盖 19-Fund 状态的错误重现
- 按用户手动确认日期切换（不要全靠 MAX 推断）
- 维持历史可追溯（旧行 is_latest=false 保留）
- 与 §2.1 设计文档 [decision-27-is-latest-dual-layer.md](../phase-1/decisions/decision-27-is-latest-dual-layer.md) 配套

**实现位置**：
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java`（Bug 1 修复，commit f42b323）
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetRawMapper.java`（Bug 2 修复，commit f42b323）
- `docs/phase-1/decisions/decision-27-is-latest-dual-layer.md`（设计文档，commit f42b323）

**回退条件**：无（决策 27 永久生效）

*最近更新：2026-07-22 追加决策 27（is_latest 双层语义） + 1b.2 补完 Bug 修复*
*触发：1b.2 验收后 5-Fund 状态覆盖 + 用户对 upload/dedup 校验机制质疑 + 决策 22 末尾汇总表约定*

---

## 决策 28：AI vision 前端 timeout 临时延长 + 基金分类映射不自动升级（2026-07-23）

**状态**：✅ 已锁定（2026-07-23）

**背景**：
- 2026-07-23 22:09 重新解析 4 张图时，AI vision 单图 / 批处理频繁超过 60s（minimax 路径），前端 axios 主动 abort
- 4 张图属于同一时点（同一账户同一日），即使 batch 也偶尔超时
- 现有 60s timeout 对 4 张图（特别是 0720 标号 4 张真实截图）不够宽松
- 1b.3 P7 真实联调时发现：AI 视觉模型在 2026-07-21 / 2026-07-23 把两只短债/纯债误归 A股权益类，confirm 流程又把 ai_guess 错误地升级为 user_correct，导致后续 confirm 一直被错误映射覆盖，固收类消失

**决策**：

1. **前端 axios timeout 临时延长 60s → 120s**（仅 1b.3 一次性放宽）
   - `fincontrol-frontend/src/api/client.js`：`timeout: 60000` → `timeout: 120000`（AI vision 单图/批处理易超时）
   - 长期方案待用户指示：异步轮询 / 服务端缓存 / 减少 AI timeout / 拆 4 图为单图串行
2. **基金分类映射生命周期修复**（SnapShotConfirmService.writeFundCategoryMap）：
   - `existing == null` → `source='ai_guess'`（首次入库）
   - `existing.source == 'ai_guess'` → 保持 `ai_guess`，**不**自动升级为 user_correct
   - `existing.source == 'user_correct' / 'user_manual'` → 保持原 source（明确用户映射）
3. **一次性数据修复脚本** `fincontrol-backend/scripts/1b3/01-repair-bond-category-2026-07-23.sql`：
   - 把误归 A股 的两只短债/纯债移回固收类
   - 重算 4 个日期的 asset_snapshot 总额
   - 安信新价值灵活配置混合A 维持 A股权益类（按用户决定，本轮不主动归固收）
4. **新增 mapper 工具方法**（供 Phase 2 复用，本轮暂不调用）：
   - `AssetRawMapper.updateCategoryByUserAndFundAndDates(userId, fundName, newCategory, snapshotDates)`
   - `AssetSnapshotMapper.recalcTotalAmountByUserAndDateAndCategory(userId, date, categories)`
   - `AssetSnapshotMapper.insertSummaryIfMissing(snap)`

**实现位置**：
- `fincontrol-frontend/src/api/client.js`（commit a83bbd3，timeout 60000 → 120000）
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java`（writeFundCategoryMap 语义修复）
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetRawMapper.java`（updateCategoryByUserAndFundAndDates）
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetSnapshotMapper.java`（recalcTotalAmountByUserAndDateAndCategory + insertSummaryIfMissing）
- `fincontrol-backend/src/main/resources/mapper/AssetSnapshotMapper.xml`（recalc / insertSummaryIfMissing 实现）
- `fincontrol-backend/src/main/resources/mapper/FundCategoryMapMapper.xml`（无改动，使用现有 upsertByFundName）
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapShotConfirmServiceP7Test.java`（4 个回归测试）
- `fincontrol-backend/scripts/1b3/01-repair-bond-category-2026-07-23.sql`（一次性数据修复）
- `fincontrol-frontend/src/pages/HomePage.jsx`（六大类分布收起时不再渲染左侧 .pie-wrap 占位）

**理由**：
- 60s → 120s：minimax 4 图真实场景需要更宽松窗口，但不替代长期异步轮询方案
- ai_guess 不自动升级：原本 `existing == null ? 'ai_guess' : 'user_correct'` 逻辑把 AI 一次错误"固化"为"用户已确认"，需严格区分自动 AI 写入 vs 明确用户纠正
- 一次性数据修复：定向精准修复 2 只债基（按用户指示安信新价值不动），不批量改动其他基金/日期/类别

**影响**：
- 1b.3 P7：confirm 不再因 AI 一次错误而永久覆盖正确分类
- 1b.3 验证：4 张图重试后 A股=2199.40 / 固收=890.47 / 余额=219.92 / 总额=7825.05（与决策 25v3 修复结果一致）
- 后续：若需进一步减少超时，参考长期方案清单（异步轮询 / 服务端缓存 / 拆 4 图为单图串行）

**回退条件**：
- 若前端 timeout 120s 仍不够 → 引入异步轮询 + task_id 机制
- 若 Phase 2 决定将 ai_guess 升级 → 写库层加固（决策 25v3 Phase 2 路径）+ 用户明确 override

**长期方案**（待用户指示）：
1. 异步轮询：前端 POST 解析 → 后端返回 task_id → 轮询 GET task 状态
2. 服务端缓存：相同 (fileIds, promptVersion) 复用上次结果
3. 减少 AI timeout：调小 provider `timeoutSeconds` + 重试 + fallback
4. 拆 4 图为单图串行：4 次单图 confirm，独立超时控制

*最近更新：2026-07-23 22:13 决策 28 落地（前端 timeout 120s + writeFundCategoryMap 语义修复 + 一次性数据修复）*
*触发：1b.3 P7 真实联调发现 AI 误归导致后续 confirm 一直被错误映射覆盖（fund_category_map 错误固化）*

---

## 决策 29：端口统一 5173（Vite default，1b.4 起）（2026-07-24）

**状态**：✅ 已锁定

**背景**：
- 1b.2 / 1b.3 阶段为避开 vite dev 冲突，前端端口被临时改为 `5174`（写入 `vite.config.js`）
- 2026-07-24 1b.4 PR2 用户反馈 "我自己也访问不了前端" + 要求把所有相关配置改回 5173
- 5174 是临时测试端口（仅 1b.2/1b.3 使用），无历史价值，统一回 Vite 默认 5173 是用户强制要求
- PR2 commit `fe6a0a2` 未动端口（聚焦 token 重构），envfix commit `a6329f0` 落盘端口统一

**决策**：

> 1b.4+ 起所有前端开发服务器统一回 **Vite 默认 5173**。`5174` 仅在 5173 端口被占用时作为临时 fallback（且**必须**同步改 `vite.config.js` 的 `server.port` 与本决策注释）。

**实现位置**：
- `fincontrol-frontend/vite.config.js`：`port: 5174` → `port: 5173`（加注释说明 5174 是 1b.2/1b.3 临时端口）
- `docs/SETUP.md`：文字 "修改 `server.port: 5174`" → "Vite 默认端口；若占用改为 5174 等并同步改 `vite.config.js` 的 `server.port`"
- `docs/test-records/manual-tests/1b/2026-07-22_phase1b2-acceptance-plan.md` 等 4 个 1b.2/1b.3 历史报告里 "访问 localhost:5174"：**不动**（历史档案保留当时端口）

**理由**：
- Vite 默认 5173 是社区共识，与 React/Vue 模板、`npm run dev` 默认行为对齐
- 端口冲突时 fallback 链（kill / `--port` flag / 改 vite.config.js）已成熟，不需要"专属 5174"作为占位
- 文档 + 配置双改保证团队 onboarding 零歧义（新 dev 跑 `npm run dev` 即可访问 5173）

**回退条件**：
- 1b.4+ 期间若发现 5173 被其他服务长期占用 → 临时改 5174 并在 commit message 注明「5173 conflict」
- 不建立 "5174 = 1b.4 默认" 之类的子约定（避免再次漂移）

**关联 commit**：
- `a6329f0` fix(1b.4-PR2.envfix): 端口 5174→5173 + jsdom env 修复（5 files / +1235 -1020）
- `0939928` fix(1b.4-PR2.hotfix): 恢复 .cat-detail-table 块（不涉及端口）

**影响**：
- 1b.4+ 阶段所有 dev workflow 走 5173
- 1b.5+/1b.6+ 阶段验收报告默认 URL 改写为 `http://localhost:5173/`
- 与 Vite 社区文档、所有 npm 模板默认对齐，新成员上手零学习成本

*最近更新：2026-07-24 15:16 决策 29 落地（端口统一 5173，1b.4 起）*
*触发：1b.4 PR2 用户反馈 "我自己也访问不了前端"，明令改回 5173 并要求相关配置全部更正*

---

## 决策 30：取消硬编码历史限制（2026-07-24）

**状态**：✅ 已锁定（2026-07-24，1b.4 PR3plus 实施）

**背景**：
- `SnapShotConfirmService.validateRequest()` 中硬编码 `daysDiff > 7` 拒绝超过 7 天的截图
- 2026-07-24 1b.4 PR3plus 用户实拍 7/13/7/14 截图，距 7/24 = 10-11 天，被 400 拒绝
- 这些截图是**真实历史数据**，用户有合理需求上传更早的截图
- 硬编码 7 天无法满足个性化需求（有人想 14 天，有人想 30 天）

**决策**：

> 取消硬编码 7 天历史限制。改为从 `settings` 表读 `max_snapshot_age_days`。

**具体规则**：

1. **后端改动**（SnapShotConfirmService）：
   - 删除 `if (daysDiff > 7) throw` 硬编码
   - 注入 `SettingsService settingsService`
   - 校验时 `int maxAge = settingsService.getMaxSnapshotAgeDays(req.getUserId());`
   - `if (daysDiff > maxAge) throw ...`
   - 当 `maxAge === -1` 时表示不限制，永远不超

2. **前端改动**（DataPage）：
   - "修改历史限制" 按钮 + 4 步 modal（HistoryLimitDialog）
   - 显示当前 days（从 store 读，不硬编码）
   - 5 选项：7 / 14 / 30 / 180 / -1（不限制）
   - 保存调 `PUT /api/settings/{userId}/max-snapshot-age-days`
   - 上传前预校验用 store.maxSnapshotAgeDays

**实现位置**：
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java`（删除硬编码 7）
- `fincontrol-backend/src/main/java/com/fincontrol/service/SettingsService.java`（getMaxSnapshotAgeDays/setMaxSnapshotAgeDays）
- `fincontrol-backend/src/main/java/com/fincontrol/controller/SettingsController.java`（GET/PUT）
- `fincontrol-backend/scripts/1b3/01-settings.sql`（建表 + 默认 7）
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapShotConfirmServiceP7Test.java`（回归测试 5 个）
- `fincontrol-frontend/src/components/data/HistoryLimitDialog.jsx`（4 步 modal）
- `fincontrol-frontend/src/stores/userConfigStore.js`（加 maxSnapshotAgeDays state + actions）
- `fincontrol-frontend/src/api/endpoints.js`（SETTINGS_MAX_AGE_GET/UPDATE）

**理由**：
- 用户需求驱动（上传真实历史数据是核心场景）
- 灵活可配置（不同用户可以选不同窗口期）
- 数据层 vs 硬编码：硬编码 7 天是早期 MVP快速选择，现在有 settings 表可以更灵活
- 不影响 1b.4 之前的代码路径（只是限制放宽，不会破坏其他逻辑）

**回退条件**：
- Phase 2 如果决定对所有用户默认 30 天（更长窗口期），调整 default 7 → 30 + DB init value

**关联 commit**：`1b98b8c feat(pr3plus): 历史限制可配置 + settings 全局配置表 (decision 30/31)`

*最近更新：2026-07-24 决策 30 落地（取消硬编码历史限制）*
*触发：1b.4 PR3plus 用户实拍 7/13/7/14 截图被 7 天硬限拒绝*

---

## 决策 31：引入 settings 全局配置表（2026-07-24）

**状态**：✅ 已锁定（2026-07-24，1b.4 PR3plus 实施）

**背景**：
- 项目目前**没有"全局配置"概念**。所有可调参数都散落在代码硬编码里（例：`SnapShotConfirmService` 中 `daysDiff > 7`）
- 决策 30 配套：第一个需要配置的参数 `max_snapshot_age_days` 不能塞进 `user_config`（user_config 面向用户偏好，不是系统级配置）
- 未来可能加：auto_set_current_after_confirm、monthly_dca_reminder_day、enable_ai_vision 等

**决策**：

> 引入 `settings` 表作为全局配置存储。单 userId PK + 几个 K-V 字段（当前 1 个 max_snapshot_age_days，预留扩展）。

## 表结构

```sql
CREATE TABLE settings (
  user_id BIGINT PRIMARY KEY COMMENT '用户 ID（单用户 MVP 默认 1，多用户预留）',
  max_snapshot_age_days INT NOT NULL DEFAULT 7 COMMENT '历史限制天数；-1 = 不限制；其他有效值：7/14/30/180',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '全局配置表（PR3plus 决策 31）';
```

## 字段约束

- `user_id` 主键（多用户预留）
- `max_snapshot_age_days` 枚举值：`{-1, 7, 14, 30, 180}`
  - `-1` = 不限制
  - `7/14/30/180` = 限定天数（对应前端可选项）

## API 设计

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/settings/{userId}/max-snapshot-age-days` | 读取某用户的 max_snapshot_age_days（找不到返 7） |
| PUT | `/api/settings/{userId}/max-snapshot-age-days` | 更新（upsert）某用户的 max_snapshot_age_days |

PUT 请求体：
```json
{ "days": 7 }
```

PUT 响应体：
```json
{ "userId": 1, "maxSnapshotAgeDays": 7, "updatedAt": "2026-07-24T18:30:00" }
```

## 实施细节

### 后端
- `Settings.java` (Entity, Lombok @Data)
- `SettingsMapper.java` + `SettingsMapper.xml`
- `SettingsService.java` (`getMaxSnapshotAgeDays(userId)`, `setMaxSnapshotAgeDays(userId, days)`)
- `SettingsController.java` (GET/PUT)
- `SnapShotConfirmService` 注入 `SettingsService`，validateRequest 改读 settings

### 前端
- `userConfigStore.js` 加 `maxSnapshotAgeDays` state + `setMaxSnapshotAgeDays(days)` action（**调 API**，不用 localStorage）
- `api/endpoints.js` 加 `SETTINGS_MAX_AGE(userId)` + `SETTINGS_MAX_AGE_UPDATE(userId)`
- `HistoryLimitDialog.jsx` (新建) — 4 步 modal
- `DataPage.jsx` 集成按钮 + 预校验

## 扩展性

未来可能加的配置项（举例）：
- `auto_set_current_after_confirm` (BOOLEAN, default true) — confirm 后自动设 is_current
- `monthly_dca_reminder_day` (INT, default 25) — 每月 DCA 提醒日
- `enable_ai_vision` (BOOLEAN, default true) — 是否启用 AI vision

`settings` 表预留 `key-value` 形式扩展（后续 PR 可加 `setting_key` + `setting_value` JSON 列），MVP 阶段先存单行结构化字段。

**理由**：
- 第一个真实需求（决策 30 配套）
- 解耦系统配置 vs 用户偏好（user_config 不可复用）
- 多用户预留（userId PK 避免单点）
- 简单结构起步（不强求 key-value，未来需要时迁移）

**回退条件**：
- Phase 2 如果配置项 > 5 个，迁移到 `settings_kv` (key, value_json) 通用结构

**关联 commit**：`1b98b8c feat(pr3plus): 历史限制可配置 + settings 全局配置表 (decision 30/31)`

*最近更新：2026-07-24 决策 31 落地（引入 settings 全局配置表）*
*触发：1b.4 PR3plus 决策 30 配套*

---

## 决策 32：AI 跨 category 重复分类的优雅处理（user_correct 优先 + CATEGORY_CONFLICT 警告）（2026-07-24）

**状态**：✅ 已锁定（2026-07-24，1b.4 PR3plus 实施）

**背景**：
- 2026-07-24 1b.4 PR3plus 用户把默认 7 天限制成功更改为 180 天，上传 14 号样例并解析后报 500
- 真根因：`DedupEngine.deduplicate()` line 165-171 抛 `BusinessException(INTERNAL_ERROR, ...)`，因为 AI 解析时把 `纳斯达克100ETF联接(QDII)C` 同时分到 `海外权益类` 和 `余额类` 两个 category，dedup 视为"数据矛盾"直接抛错
- AI 跨 category 重复分类是真实数据问题（多模态模型对某些边界基金判断不稳定）
- 不能因为 1 次 AI 误判就阻塞整个入库流程（用户体验差）

**决策**：

> AI 解析有不确定性，跨 category 重复不应阻塞入库；映射表的 user_correct 优先级最高。

## 三条规则（按优先级）

| 优先级 | 规则 | 实现位置 |
|---|---|---|
| P0 | `fund_category_map.source='user_correct'` 的行**永远不被 AI 覆盖**（保留用户的明确分类） | `SnapShotConfirmService.writeFundCategoryMap`（P7 修复） |
| P1 | DedupEngine 维度 D 冲突时优先采纳 user_correct 的 category；未命中则保留**首次**出现的 category | `DedupEngine.deduplicate()` 冲突分支 |
| P2 | 不抛异常，记一条 `CATEGORY_CONFLICT` warning（含 fundName / keptCategory / aiCategory / resolution） | `DedupEngine.addCategoryConflictWarning` |

## DedupInput 新增字段

```java
public record DedupInput(
    List<ParsedAsset> parsedAssets,
    Set<String> existingFundNamesForSnapshot,
    Map<String, String> existingUserCorrectCategories,  // ← 新增
    LocalDate snapshotDate,
    boolean confirmedOverwrite
) {}
```

`existingUserCorrectCategories` 来源：`fund_category_map` 中 `source='user_correct'` 的行，序列化为 `Map<fundName, category>`。

## 冲突分支逻辑

```java
if (!Objects.equals(existing.categoryName, categoryName)) {
    String userCorrectCategory = input.existingUserCorrectCategories() == null
        ? null : input.existingUserCorrectCategories().get(key);
    if (userCorrectCategory != null
            && !Objects.equals(userCorrectCategory, existing.categoryName)) {
        // 采纳 user_correct：覆盖 existing.categoryName，amount/holding/cumulative 用 AI 最新值
        existing.categoryName = userCorrectCategory;
        existing.amount = fund.getAmount();
        // ...
        addCategoryConflictWarning(warnings, key, existing.categoryName,
            categoryName, "resolved_by_user_correct", userCorrectCategory);
    } else {
        // 无 user_correct → 保留首次出现的 category（LinkedHashMap putIfAbsent 语义）
        addCategoryConflictWarning(warnings, key, existing.categoryName,
            categoryName, "kept_first", null);
    }
}
```

## writeFundCategoryMap P7 修复

**修复前**（line 284-293，P7 修复不完整）：
```java
String finalCategory = cat.getCategoryName();  // ← 永远用 AI 的 category
String finalSource;
if (existingSource == null) finalSource = "ai_guess";
else if ("ai_guess".equals(existingSource)) finalSource = "ai_guess";
else finalSource = existingSource;
map.setCategory(finalCategory);  // ← 覆盖了 user_correct 的 category！
map.setSource(finalSource);
```

**修复后**（line 290-313）：
```java
String existingCategory = existing == null ? null : existing.getCategory();
String finalCategory, finalSource;
if (existingSource == null) {
    // 首次入库
    finalCategory = aiCategory;
    finalSource = "ai_guess";
} else if ("ai_guess".equals(existingSource)) {
    // AI 可更新自己的 guess
    finalCategory = aiCategory;
    finalSource = "ai_guess";
} else {
    // user_correct / user_manual：保留用户分类，拒绝 AI 覆盖
    finalCategory = existingCategory != null ? existingCategory : aiCategory;
    finalSource = existingSource;
}
```

## 前端 UI 透明化

预览 modal 顶部加黄色 banner：
```jsx
{parsedSummary.categoryConflictCount > 0 && (
    <div className="conflict-warning">
        ⚠ AI 解析时把 {parsedSummary.categoryConflictCount} 只基金分到了多个大类，
        系统已自动保留 fund_category_map 中的 user_correct 分类（若无则保留首次出现的分类）。
        请在「CorrectionPage」核对，或后续接入映射表维护功能后手动调整。
    </div>
)}
```

## 影响面

| 维度 | 影响 |
|---|---|
| API 行为 | `POST /api/snapshot/confirm` 维度 D 冲突从抛 500 → 返 200（带 CATEGORY_CONFLICT warning） |
| 数据 | asset_raw / asset_snapshot 不受影响（写库时 fund 已 dedup） |
| 映射表 | fund_category_map 仍被 upsert，P7 修复后 user_correct 行被正确保护 |
| 警告 UX | 前端 preview modal 顶部 banner 提示用户有多少只基金被自动 dedup |
| 回归 | 23号（无冲突）行为不变；14号 之前 100.00 元 stale 数据被 overwrite 抹掉 |

## 单元测试

| 测试 | 场景 | 预期 |
|---|---|---|
| R1 | existing==null → 写入 source='ai_guess' | ✅ 通过 |
| R2 | existing.source='ai_guess' → 保持 ai_guess | ✅ 通过 |
| **R3** | existing.source='user_correct' → 写 existing.category，source='user_correct' | ✅ 通过（之前会失败） |
| R4 | existing.source='user_manual' → 保持 user_manual | ✅ 通过 |
| **R5** | AI 跨 category → 不抛异常 + 保留首次 + CATEGORY_CONFLICT warning | 新增 ✅ |
| **R6** | AI 跨 category + user_correct 存在 → 用 user_correct + 无 CATEGORY_CONFLICT warning | 新增 ✅ |
| **R7** | writeFundCategoryMap 收到 user_correct existing → 写 existing 的 category，不是 AI 的 | 新增 ✅ |

**理由**：
- AI 解析的不确定性 + 用户映射的确定性 → user_correct 最高优先级
- 跨 category 重复是真实数据问题，不能阻塞入库
- 保留首次出现的 category（LinkedHashMap putIfAbsent 语义）= 默认行为，无需额外配置
- CATEGORY_CONFLICT warning 给前端显示供用户核对

**回退条件**：
- Phase 2 如果引入"用户映射表维护 UI"（路径 `/correction` 或新增 `/category-map` 页面），用户可主动 review + override 自动 dedup 结果

**关联 commit**：`b0dc7a1 fix(pr3plus): resolve category conflicts and null fund count`

*最近更新：2026-07-24 决策 32 落地（AI 跨 category 重复分类的优雅处理）*
*触发：1b.4 PR3plus 14号 截图 confirm 500 修复*

---

## 决策 33 详述：1b.4-pr6b 模块 B 大类确认 UX 完整实施（v2 1b.4-pr6b）

**状态**：✅ 已锁定（2026-07-25 全部 P0+P1+P2+R5 完成 + 主页 V6 修复）

**背景**：
- 1b.4 PR6b 阶段（2026-07-25）将决策 32 提出的"AI 重复分类优雅处理"具象化为端到端 UX
- 7 个触发问题已全部在 v2 决策落地：AI 误归 A 股权益类、首页双重计入、入库前手动纠正 UI 缺失、user_correct 重复弹确认、退出 modal state 残留、消失-重现无机制、V6 收尾 Fix D 不完整
- 关键场景：用户实盘 19 只基金，其中 3 只（鹏华纯债/长城短债/安信新价值）AI 频繁误归 A 股权益类

**决策（七条 UX 规则 + V6 修复）**：

1. **D1 严格阻塞**：preview modal 中存在 🤖 ai_guess 行 → 「确认入库」按钮 disabled + tooltip「还有 N 条 AI 猜测未确认」
2. **D2 二次确认 modal**：confirm 时检测「dropdown 改动但未点 ✓」 → 弹 modal「全部提交 / 仅已 ✓ 的 / 返回修改」
3. **D3 实时联动**：改 dropdown → useMemo 重算 → preview「各类小计」即时刷新
4. **D4 双重计入修复**（决策 32 基础）：`SnapShotConfirmService.writeAssetSnapshot()` 前置 `assetSnapshotMapper.updateIsLatestBySnapshotDate()` 清理旧行
5. **D5 user_correct 自动套用**：DataPage 打开 preview modal 前调 `/api/category-map/match?funds=...` → 把 user_correct 合并到 categoryOverrides → dropdown 默认值 = user_correct.category → badge = ✅ 已确认
6. **D6 退出 reload**：DataPage unmount 时清 categoryOverrides / categoryDirty / overridesSaving / pendingReConfirms
7. **D7 消失-重现特别提示**：fund_category_map 新增 `last_seen_snapshot_date` + `first_missing_snapshot_date` 字段；match API 返 first_missing_snapshot_date 非 NULL → 黄色 banner「上次确认 t0，您可能于 t1 及之前清仓」
8. **V6 收尾**（1b.4-pr6b-recovery）：
   - **Bug A — 主页固收类缺失**：`SnapshotQueryService.buildLatestResponse()` 仅迭代原始 AssetSnapshot 构造 summary，AI 误归导致固收类行从未写入 → 修复为 `buildCanonicalSummaries()` 永远以 6 大类 canonical 全集为 key
   - **Bug B — HomePage stale-cache**：useEffect `[]` + `!snap` 守卫 + 未订阅 `refreshCounter` → 修复为订阅 refreshCounter + deps `[refreshCounter]`，每次 counter 变化都 fetchLatest(1)

**Schema 升级**（R5 消失-重现）：
```sql
ALTER TABLE fund_category_map ADD COLUMN last_seen_snapshot_date DATE NULL;
ALTER TABLE fund_category_map ADD COLUMN first_missing_snapshot_date DATE NULL;
```

**锚点算法**（基于 last_seen MAX，与 is_current 解耦）：
- 任意 `snapshotDate >= anchorDate` → 新锚点（forward inference）
- 任意 `snapshotDate < anchorDate` → 回填（仅入库，不修改 last_seen/first_missing）
- 为什么不用 `is_current`：`snapshot_meta.is_current` = 首页展示日期（用户选/默认），**不一定是最新的**

**实现位置**：
- 后端 `SnapshotQueryService.java` 新增 `buildCanonicalSummaries()`（V6 修复）
- 后端 `SnapShotConfirmService.java` confirm 内 R5 锚点检测（D7 实现）
- 前端 `DataPage.jsx` 5 个 useState（categoryOverrides / categoryDirty / overridesSaving / showSubmitDirtyModal / pendingReConfirms）
- 前端 `HomePage.jsx` useEffect deps `[refreshCounter]`（V6 修复）
- 前端 `AssetSnapshotStore` 新增 `bumpRefresh` + `refreshCounter`（V6 修复）

**验收口径（V1-V6 · 53 用例 · 用户亲自跑通）**：
- V1 22 用例 前端 DataPage.test.jsx T1-T22 全绿
- V2 8 用例 后端 SnapShotConfirmServiceP7Test B1-B8 全绿
- V3 7 用例 联调端到端
- V4 4 用例 双重计入修复
- V5 4 用例 消失-重现机制
- **V6 8 用例 主页固收类 + HomePage stale-cache（本次新增）**
- 总计 53/53 通过

**回退条件**：
- 若 Phase 2 引入"用户映射表维护 UI"，Decision 33 D5-D7 中「user_correct 自动套用 + 消失-重现」可重审
- V6 修复（refreshCounter 订阅）若未来引入更精细的 store 选择器（如 useShallow），可改用更细粒度订阅优化重渲染

**关联 commits**：
- `1b98b8c feat(pr3plus): 历史限制可配置 + settings 全局配置表`
- `b0dc7a1 fix(pr3plus): resolve category conflicts and null fund count`
- `a6329f0 fix(1b.4 PR2): port 5173`
- `f42b323 1b4-pr3plus work`
- `1b4pr6b` 系列 6 个 commit（D1-D7 实现）
- **`9207b2b fix(pr6b-recovery): V6 — homepage 固收类 + stale-cache 双 bug 修复**（V6 收尾核心 commit，已 push origin main）

*最近更新：2026-07-25 V6 收尾完成 + 用户验收通过（commit 9207b2b push origin main）*
*触发：1b.4-pr6b 阶段收尾，V6 修复主页固收类缺失（-1,189.92 元）与 stale-cache（绕一圈 workaround）*

---

## 决策 34：Phase 1 里程碑收官与后续阶段重排（2026-07-25）

**状态**：✅ 已锁定（用户确认；立即生效）

### 一、背景

- Phase 0 原始设计将 Phase 1 的核心验收定义为“截图上传 → API 解析 → 入库 → 首页展示”的数据流水线闭环。
- 后续实施时，为了便于前后端并行开发，又把历史快照查询、解析日志、撤销/重试等数据管理补充项，以及 AI 顾问前端页面，一并拆进了 Phase 1b 的细粒度计划。
- 实际开发证明：当前核心资产链路已经真实跑通并完成用户验收；1b.4 的设计债务和确认 UX 也已于 2026-07-25 通过 PR7 收官。但历史查询 UI 仍需补全，`/ai` 当前仍是占位页，继续把这些扩展项作为 Phase 1 退出门槛会放大范围、延迟可交付里程碑。
- 因此需要恢复“按业务交付闭环划分顶层 Phase、按技术任务管理内部 backlog”的原则，使项目能够以可演示的 Phase 1 成果进入后续业务开发与公开展示。

### 二、决策

> **Phase 1 以核心数据流水线闭环正式收官；未完成的数据管理能力归入 Phase 2，AI 顾问前端对话界面顺延至 Phase 5，前端整体质量收尾归入 Phase 4。**

具体规则如下：

1. **Phase 1 完成口径**
   - 必须闭环：4 张资产截图上传、视觉模型解析、基金去重与分类确认、`asset_raw / fund_category_map / asset_snapshot` 三表事务入库、`snapshot_meta` 当前快照切换、首页当前资产展示。
   - 以上链路以真实用户端到端验收为准；当前 PR7 V1–V8 已通过，故 Phase 1 核心里程碑标记为完成。
   - AI 顾问后端 API、意图分类、prompt 路由和 provider 基础设施已经完成，保留为 Phase 1a 的后端能力；**不以 AI 顾问前端 UI 是否完成作为 Phase 1 退出条件**。

2. **Phase 2：未完成的数据管理 + 核心业务功能**
   - 补齐数据管理前端：历史快照列表/分页/日期筛选、指定日期快照详情、解析日志、失败重试/重新解析、10 秒撤销、单条忽略、已有日期覆盖提示、必要的逐文件状态与数据质量提示。
   - 实现资产配置页面（目标比例、预算、阈值及配置持久化）。
   - 实现月度操作台（默认值、求解、取整确认、操作审计）。
   - 历史快照查询后端 API 已在 Phase 1a 具备，Phase 2 主要完成完整产品化 UI、联动和验收，不重复设计同一套数据契约。

3. **Phase 3：数据模型与可视化**
   - 先完成净值/日收益/事件等必要数据模型，再实现净值曲线、比例演化和日收益明细。
   - 继续遵循“数据模型先行，前端不自行猜测历史数据”的约束。

4. **Phase 4：已知问题、数据校验与前端整体收尾**
   - 处理 Phase 2/3 实施后暴露的异常处理、数据质量、跨页面状态、加载/空态/错误态、可访问性和 UI 一致性问题。
   - 此前讨论的“1b.7 前端整体验收与收尾”不单独设置为新的顶层阻塞阶段，统一归入本 Phase 4。
   - 1b.4 已提前完成的设计债务修复不回退，也不阻止未来 Phase 4 做全系统统一加固。

5. **Phase 5：拓展性功能**
   - AI 顾问前端对话界面（多轮消息、会话管理、上下文快照标注、意图/路由展示）顺延至此阶段。
   - 同阶段继续安排 LQR 实战集成、多用户、移动端等远期能力；各子能力可独立排期，不互相构成硬依赖。

### 三、顶层路线表（本决策生效后的权威版本）

| 阶段 | 核心任务 | 状态 | 退出条件 |
|---|---|---|---|
| **Phase 0** | 基础设施、契约、Schema、评审决策 | ✅ 完成 | 基础设施可启动、契约锁定 |
| **Phase 1** | 数据流水线闭环 | ✅ 完成（2026-07-25） | 上传 → 解析 → 确认 → 入库 → 首页展示 |
| **Phase 2** | 未完成的数据管理 + 资产配置 + 月度操作台 | ⏳ 待启动 | 历史查询、配置和月度操作可用并验收 |
| **Phase 3** | 数据模型与净值/比例/日收益可视化 | ⏳ 待启动 | 数据模型先行，三类看板可用 |
| **Phase 4** | 已知问题修复、数据校验、UI 优化、前端收尾 | ⏳ 待启动 | P1/P2 残留和跨页面质量门禁收敛 |
| **Phase 5** | AI 对话 UI、LQR、多用户、移动端等扩展 | ⏳ 远期 | 按独立子能力分别验收 |

### 四、对历史 1b 编号的处理

- `1b.4` 的现行历史含义保留为“设计债务修复”，其 PR0–PR7 和验收报告不改写。
- 旧滚动计划中的 `1b.5 数据管理完整流程`、`1b.6 AI 顾问完整版` 仅作为历史排期记录保存；从本决策起，前者的产品化内容归 Phase 2，后者归 Phase 5。
- 不新增一个独立的顶层 `1b.7`；前端整体收尾并入 Phase 4。
- 历史 checklist、work-plan 和验收报告记录当时的编号与语境，不追写历史；根 README 和后续新计划以本决策为当前路线依据。

### 五、影响与约束

- **不回退成熟代码**：决策 25/27/33、累计/持有收益口径、`snapshot_meta` 双层语义、confirm 幂等和当前快照链路继续有效。
- **不伪造未完成能力**：当前 `/ai` 明确为占位页；历史查询完整 UI、解析日志、撤销等未完成项在 README 中列为 Phase 2/4 backlog。
- **API 复用优先**：Phase 2 优先使用已存在的 `/api/snapshot/history`、`/api/snapshot/{date}`、`/api/parse-logs` 和 rollback/reparse API，避免重复造轮子。
- **测试口径分层**：Phase 1 以专项验收和真实 E2E 作为里程碑证据；全量测试 fixture/config 债务单独记录，不把历史 `241/241` 误写成当前全量状态。
- **文档同步**：根 README 必须同步本决策，突出可演示闭环、技术架构、验证证据和当前边界，作为公开项目入口。

### 六、理由

1. 顶层 Phase 应以用户可验证的业务闭环定义，而不是以某个前端页面是否完成定义。
2. 历史查询天然服务于资产配置和月度操作的决策回顾，归入 Phase 2 能保持核心业务语义连续。
3. AI 对话 UI 与数据流水线没有硬依赖，且前端工作量曾被低估，顺延至 Phase 5 不会破坏现有 AI 后端能力。
4. 将质量收尾统一放入 Phase 4，能避免每完成一个页面就重复开一个新的“收尾阶段”。
5. 先形成可演示、可验证、可写入简历的 Phase 1 成果，符合当前项目交付和公开展示需要。

### 七、关联文档

- [Phase 0 决策文档](./decisions.md)
- [Phase 1 验收清单](../phase-1/checklists/phase-1a.md)
- [Phase 1b 验收清单（历史编号）](../phase-1/checklists/phase-1b.md)
- [全站页面需求 v1.0](../requirements/2026-07-22_fincontrol-page-requirements.md)
- [1b.4-pr7 最终验收报告](../test-records/manual-tests/1b/2026-07-25_1b4-pr7-acceptance-report.md)

*最近更新：2026-07-25 用户确认 Phase 1 里程碑收官，并将未完成数据管理归入 Phase 2、AI 顾问前端 UI 顺延至 Phase 5。*

---

## 决策总结表（追加后）

> 决策 22 规定：本汇总表始终位于文档最末尾。
> 每次新增决策须就地追加一行（按决策顺序）。
>
> **总表优先原则（2026-07-24 PR5 补充）**：本表是项目决策的**唯一权威汇总**，最早、最全、最权威。
> 各阶段子决策文档（`docs/phase-N/decisions/decision-XX-*.md`）保留**详细背景 + 实施细节**作为子档，但**所有决策 ID 必须在此总表追加一行**才能视为正式锁定。
> 子档若未在总表登记，视为未生效决策，代码不可引用。
> 1b.4 三项决策 30 / 31 / 32 于 2026-07-24 首次总表登记（PR5 收官）。

| # | 决策 | 状态 | 关联评审 | 触发 commit |
|---|------|------|----------|-------------|
| 1 | REST API 契约完成 | ✅ | 第四轮 3.3.5 | Phase 0 |
| 2 | target_ratio 同步策略 = 解读 4 | ✅ | 第四轮 2.2.1 | Phase 0 |
| 3 | profit 字段读取 = 首页明细表 | ✅ | 第四轮 2.1.1 | Phase 0 |
| 4 v2 | 累计收益率卡片两阶段实现 | ✅ | 第四轮 3.3.1 | 1b.2 |
| 5 | Phase 1 验收标准追加 P0 | ✅ | 第四轮 4.3.1 / 4.3.2 | Phase 0 |
| 6 | Phase 计划修订（新增 Phase 0）| ✅ | 第四轮 4.3.3 / 4.3.4 / 4.3.7 / 4.3.8 | Phase 0 |
| 7 | profit 字段拆分 holding_profit / cumulative_profit（1a.8.7）| ✅ | 1a.8 语义混合 | 1a.8.7 |
| 8 | 基金类别归一化 + 双向 cache + 清仓可恢复（1a.8.8）| ✅ | 1a.8 category 映射 | 1a.8.8 |
| 12 | 豆包 vision 路径暂时废弃（Phase 1a minimax-only）| ✅ | 1a.10 阶段 | 1a.10 |
| 13 | snapshotDate 来源优先级 + dataTime 字段 | ✅ | 1a.10 EXIF 覆盖 | 1a.10 |
| 14 | fund 分类 AI 辅助 + 用户自定义（follow-up）| 🚧 | 1b+ follow-up | 1b+ |
| 15 | Chat prompt 已知缺陷（待 1b+ 聚合修复）| 🚧 | 1a.10 实测 | 1a.10 |
| 16 | 图表库选型 = Recharts（决策 16）| ✅ | 1b.1 | 1b.1 |
| 17 | UI 库选型 = 纯 CSS（决策 17）| ✅ | 1b.1 | 1b.1 |
| 18 | Phase 1b 文档目录约定 | ✅ | 1b.1 | 1b.1 |
| 19 | 阶段验收必更新根目录 README | ✅ | 1b.1 | 1b.1 |
| 20 | 根目录 `test/` 文件夹规范 | ✅ | 1b.2 | 1b.2 |
| 21 | `uploads/screenshots/` 缓存清理规范 | ✅ | 1b.2 | 1b.2 |
| 22 | 决策总结表位置约定（末尾 + 追加新行）| ✅ | 1b.2 | 1b.2 |
| 23 | 文档更新必 commit + push | ✅ | 1b.2 | 1b.2 |
| 24 | 后端 restart 必须用 scripts/1b/restart-backend.ps1 | ✅ | 1b.2 联调 jar 重建 file lock | 7794e81 |
| 25 v3 | 持有收益 Smart Fallback（余额宝 holding=NULL 用 cumulative 替代）| ✅ | 1b.2 累计/持有双列 | 1b.2 |
| 26 | parse 模式开关 single \| multi（前端 toggle）| ✅ | 1b.2 验证稳定性 | 1b.2 |
| 27 | is_latest 双层语义 + 跨日期 is_current | 🚧 | 1b.2 5-Fund 状态覆盖 Bug | f42b323 |
| 28 | AI vision 前端 timeout 临时延长 60s→120s + 基金分类映射不自动升级（1b.3 P7）| ✅ | 1b.3 P7 联调 | a83bbd3 |
| 29 | 端口统一 5173（Vite default，1b.4 起）| ✅ | 1b.4 PR2 用户反馈"我访问不了前端" | a6329f0 |
| 30 | 取消硬编码历史限制（改读 settings.max_snapshot_age_days）| ✅ | 1b.4 PR3plus 用户实拍 7/13/7/14 截图被 7 天硬限拒绝 | 1b98b8c |
| 31 | 引入 settings 全局配置表（userId PK + max_snapshot_age_days 默认 7）| ✅ | 1b.4 PR3plus 决策 30 配套 | 1b98b8c |
| 32 | AI 跨 category 重复分类的优雅处理（user_correct 优先 + CATEGORY_CONFLICT 警告）| ✅ | 1b.4 PR3plus 14号 截图 confirm 500 修复 | b0dc7a1 |
| 33 | 1b.4-pr6b 模块 B 大类确认 UX 完整实施（D1-D7 + V6 主页固收类 + HomePage stale-cache 修复）| ✅ | 1b.4 PR6b 阶段 + V6 收尾 | 9207b2b |
| 34 | Phase 1 核心闭环收官；未完成数据管理归入 Phase 2；AI 顾问前端 UI 顺延 Phase 5；前端整体收尾归入 Phase 4 | ✅ | 用户 2026-07-25 路线重排 | docs(决策34) |
