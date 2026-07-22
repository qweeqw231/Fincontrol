# FinControl Phase 0 决策文档

> Phase 0 产出。本文档锁定 6 项 Phase 1 启动前的关键决策。**每项决策均给出背景、决策、理由、影响范围**，作为 Phase 1+ 编码的硬约束。
>
> 与四轮评审的衔接：本文档对应第四轮评审 P0（6 项）+ 第一轮/第三轮 P0 中需要在 Phase 0 决策的事项。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.0 |
| 编写日期 | 2026-07-09 |
| 编写者 | 架构审查助手 |
| 文档类型 | 决策锁定（不可轻易回退） |
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

## 决策总结表

| # | 决策 | 状态 | 关联评审问题 |
|---|------|------|------------|
| 1 | REST API 契约完成 | ✅ | 第四轮 3.3.5 |
| 2 | target_ratio 同步策略 = 解读 4 | ✅ | 第四轮 2.2.1 |
| 3 | profit 字段读取 = 首页明细表 | ✅ | 第四轮 2.1.1 |
| 4 | 累计收益率卡片 Phase 1 隐藏 | ✅ | 第四轮 3.3.1 |
| 5 | Phase 1 验收标准追加 P0 | ✅ | 第四轮 4.3.1 / 4.3.2 |
| 6 | Phase 计划修订（新增 Phase 0）| ✅ | 第四轮 4.3.3 / 4.3.4 / 4.3.7 / 4.3.8 |

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

## 决策总结表（最新，单一份）

| # | 决策 | 状态 | 关联评审问题 / 触发 |
|---|------|------|------------|
| 1 | REST API 契约完成 | ✅ | 第四轮 3.3.5 |
| 2 | target_ratio 同步策略 = 解读 4 | ✅ | 第四轮 2.2.1 |
| 3 | profit 字段读取 = 首页明细表 | ✅ | 第四轮 2.1.1 |
| 4 v2 | 累计收益率卡片（口径 A 全口径含余额类）| ✅ | 第四轮 3.3.1 / 2026-07-22 1b.2 拍板口径 A |
| 5 | Phase 1 验收标准追加 P0 | ✅ | 第四轮 4.3.1 / 4.3.2 |
| 6 | Phase 计划修订（新增 Phase 0）| ✅ | 第四轮 4.3.3 / 4.3.4 / 4.3.7 / 4.3.8 |
| 7 | profit 字段拆分 holding_profit / cumulative_profit | ✅ | 1a.8.7 实测发现 |
| 8 | fund 分类归一化 + 双向 cache | ✅ | 1a.8.8 |
| 9 | 总资产双轨 + DISCREPANCY 1% 报警 | ✅ | 1a.9 |
| 10 | 双路径并存（4×单图+confirm / 1×parse-batch）| ✅ | 1a.10 |
| 11 | 缓存验证（同 JVM 3 HIT 加速 460x）| ✅ | 1a.10 缓存 |
| 12 | 豆包 vision 路径暂时废弃（minimax-only）| ✅ | 1a 收官 |
| 13 | snapshotDate 来源优先级 + dataTime 字段 | ✅ | 1a.10 |
| 14 | fund 分类 AI 辅助 + 用户自定义（1b+）| ⏳ | 1b+ |
| 15 | Chat prompt 已知缺陷（1b+ 聚合修复）| 📝 | 1a.10 |
| 16 | 图表库 = Recharts | ✅ | 1b.7 验收项 ECharts→Recharts 调整 |
| 17 | UI 库 = 纯 CSS（移除 antd）| ✅ | 工科风 + 学习价值 |
| 18 | Phase 1b 文档目录子目录隔离 | ✅ | 文件数量增加后的可维护性 |
| 19 | 阶段验收必更新根目录 README | ✅ | 1b.1 完成时 README 未及时更新 |
| 20 | 根目录 \`test/\` 文件夹规范 | ✅ | 1b.2 联调产物管理 |
| 21 | \`uploads/screenshots/\` 缓存清理规范 | ✅ | 1b.2 测试文件依赖 |
| **22** | 决策总结表位置约定（末尾单一份）| ✅ | 2026-07-22 用户明令规定 |
| **23** | 文档更新必 commit + push | ✅ | 2026-07-22 用户明令规定 |
| **24** | 后端 restart 必须用 scripts/1b/restart-backend.ps1 | ✅ | 1b.2 联调 jar 重建暴露 file lock |
| **25** | 累计收益查询双保险（snapshot_date = MAX 过滤） | ✅ | 1b.2 联调发现测试数据 user_id=19999 混入 |

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
