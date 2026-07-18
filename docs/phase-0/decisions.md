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

## 决策 4：首页"累计收益率"卡片 Phase 1 隐藏

**状态**：✅ 已锁定

**背景**：
- 第四轮评审 P0 3.3.1：首页"累计收益率"卡片在 Phase 1 无数据源
- 累计收益率 = (当前市值 - 累计投入本金) / 累计投入本金
- 累计投入本金无存储表（第二轮 P0 4.1.1），nav_history 表也不存在（第二轮 P0 4.1.2）
- Phase 1 不实现净值曲线（Phase 3 才实现）

**决策**：

> Phase 1 首页"累计收益率"卡片**默认隐藏**，使用 `isPhase1Mode` 标志控制。Phase 3 上线时同步显示。

**实现细节**：
- `AssetOverview.jsx` 顶部三卡片改为两卡片（余额类、六大类总值）
- 卡片渲染条件：`{isPhase1Mode ? <TwoCards /> : <ThreeCards />}`
- `isPhase1Mode` 通过 build config 或环境变量控制（默认 true）
- API `GET /api/asset/cumulative-return` 在 Phase 1 返回 `{ available: false }`，前端据此判断（已在 [api-contract.md](./api-contract.md) 9.3 节定义）

**触发更新**：
- 首页组件结构调整
- `asset/cumulative-return` API 即使在 Phase 1 也要实现（返回 available=false），避免前端硬编码

**理由**：
- 避免 Phase 1 出现"显示假数据"的反模式
- 卡片位置保留，Phase 3 直接启用
- 与净值曲线功能同步上线（累计收益率依赖净值数据）

**回退条件**：Phase 3 启动时，需先设计 nav_history 表（第二轮 P0 4.1.2）

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
</content>
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
