# FinControl 跨模块一致性架构评审（最终轮）

> 本文档由 2026-07-09 第四轮架构评审对话整理而成，聚焦前三轮评审中未暴露的跨模块矛盾与遗漏。
>
> 与前三轮评审构成完整体系：数据流水线 → 核心算法 → 前端+AI → **跨模块一致性**。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 评审日期 | 2026-07-09 |
| 评审范围 | 跨模块一致性（数据模型 ↔ 业务逻辑 ↔ 前端页面 ↔ API ↔ 开发阶段）|
| 评审者 | 架构审查助手 |
| 关联文档 | FinControl 技术设计文档 v2.0（2026-06-10）|
| 评审聚焦章节 | 第四/五/六/七/八章的跨章节一致性 |
| 评审状态 | ✅ 已完成，待用户确认 |
| 后续阶段 | 评审体系完结，Phase 1 启动前最后一道关 |

---

## 1. 审查范围与方法

本轮评审不引入新主题，而是**追溯前三轮评审中暴露的修改项在跨模块链路上的连锁影响**。

**核心方法**：对每个数据模型字段、每个前端展示元素、每个 API 调用，反查其完整的"写入 → 存储 → 读取 → 展示"链路，寻找任一环节缺失或与其他环节矛盾。

**聚焦 4 个跨模块问题**：

1. 数据模型字段 → 业务逻辑写入/读取路径
2. 前端页面数据 → 后端 API 接口对应
3. Phase 1-3 开发计划覆盖度
4. 跨模块修改的连锁影响

---

## 2. 问题 1：数据模型字段的写入/读取路径审计

### 2.1 asset_raw 字段路径审计

| 字段 | 写入路径 | 读取路径 | 状态 |
|------|---------|---------|------|
| id | auto | n/a | ✅ |
| user_id | default 1 | implicit | 🟡 单用户假设未明示 |
| snapshot_date | 截图解析（5.6.4）| 月度操作台、首页、比例演化 | ✅ |
| fund_name | 截图解析（5.6.4）| 所有展示页面 | ✅ |
| fund_code | NULL（远期预留）| 无读取路径 | 🟢 OK（NULL 字段）|
| category | 截图解析 / fund_category_map | 汇总到 asset_snapshot | ⚠️ 详见 2.5 |
| amount | 截图解析 | 汇总到 asset_snapshot | ✅ |
| profit | 截图解析 | **🔴 无读取路径** | 🔴 **详见下** |
| source | 截图解析 | 数据管理解析日志 | ✅ |
| created_at | auto | 数据管理解析日志 | ✅ |
| is_latest | 截图解析 | 所有数据查询过滤 | ✅ |
| confirmed_at | 截图解析（第一轮补正 3 新增）| is_latest 翻转判定 | 🟡 详见 2.5 |

#### 2.1.1 🔴 **profit 字段写入但无读取路径**

`asset_raw.profit` 字段在 6.2 定义为"持有收益（元）"，由截图解析写入。但遍历第七章所有页面：

- 首页（7.4.1）：仅显示"余额类、六大类总值、累计收益率"卡片，**无逐只基金的持有收益**
- 数据管理（7.4.2）：解析日志显示"日期、基金数量、入库状态"，**无 profit 列**
- 月度操作台（7.4.3）：4 个输入框（总资产、货币类、固收类、高波定投），**无 profit 输入**
- 资产配置（7.4.4）：仅配置目标比例与约束参数，**无 profit**
- 净值曲线（7.4.5）：显示累计净值、累计收益率、累计收益、净值走势、日收益，**无逐只基金 profit**
- 比例演化（7.4.6）：仅大类比例，**无 profit**

**结论**：`asset_raw.profit` 字段是**写入孤儿**——数据被采集、被存储，但永远不被展示。如果未来需要显示"我每只基金赚了多少"，目前没有展示位置。

**建议**：

- 选项 A：在数据管理页面（7.4.2）的解析日志或详情视图中增加 profit 列
- 选项 B：在首页"六大类明细表格"中增加"持有收益"列（7.4.1 末尾折叠表格）
- 选项 C：删除 profit 字段（最激进，但 6.2 已有数据不可变原则，需迁移）
- **推荐 B**：最低成本，最高复用价值

---

### 2.2 asset_snapshot 字段路径审计

| 字段 | 写入路径 | 读取路径 | 状态 |
|------|---------|---------|------|
| id | auto | n/a | ✅ |
| user_id | default 1 | implicit | 🟡 |
| snapshot_date | 截图解析（自动汇总）| 月度操作台、首页、比例演化 | ✅ |
| category | 截图解析 | 所有展示页面 | ✅ |
| total_amount | 自动汇总 | 首页环形图、月度操作台 | ✅ |
| target_ratio | **⚠️ /config 同步**（7.4.4）| 首页、比例演化、月度操作台 | ⚠️ **详见 2.2.1** |
| actual_ratio | 自动计算 | 首页环形图、比例演化 | ✅ |
| balance_fund | 自动 SUM(余额类) | 首页余额类卡片 | ✅ |
| sub_detail | （NULL，远期 LQR）| 无 | 🟢 OK |
| is_latest | 截图解析 | 所有数据查询 | ✅ |

#### 2.2.1 🔴 **target_ratio 字段的同步语义混乱**

**矛盾点**：

7.4.4 资产配置页面说："保存后写入 user_config 表，并同步更新 asset_snapshot 表的 target_ratio 字段"。

但 asset_snapshot 是**历史快照表**，target_ratio 是**配置字段**。两者结合产生歧义：

**场景**：用户在 2026-07-10 修改商品类目标比例 25% → 30%
- 解读 1（写入最新记录）：仅更新 2026-07-10 那条 snapshot 的 target_ratio=30%。历史快照保留旧值。
- 解读 2（更新所有记录）：更新所有 asset_snapshot 的 target_ratio=30%。历史被改写。
- 解读 3（创建新快照）：在 2026-07-10 之外再创建一条 "配置变更" 类型的 snapshot，标记为新事件。
- 解读 4（仅更新 user_config）：/config 只写 user_config，不动 asset_snapshot。/correction 读取时优先 user_config。

每种解读的影响：

| 解读 | /correction 默认值 | 比例演化看板 | 净值曲线 |
|------|------------------|------------|---------|
| 1 | 用最新快照的 target_ratio | 历史 target_ratio 真实演化 | 净值 vs 当前目标，无历史对比 |
| 2 | 用最新 snapshot 的 target_ratio（实际全表都改了）| 历史 target_ratio 被改写，看不出演化 | 同上 |
| 3 | 用最新 snapshot 的 target_ratio | 历史可看到"目标比例变更"事件 | 净值 vs 当前目标，含变更事件 |
| 4 | 用 user_config.target_ratios | history 显示"已变更的目标比例" | 净值 vs user_config 当前目标 |

**结论**：target_ratio 的写入策略**未在文档中明确**，三种解读都可能导致数据一致性问题。

**建议**：选择 **解读 1**（仅更新最新记录）或 **解读 4**（仅更新 user_config）。前者保持 asset_snapshot 的"历史快照"语义；后者保持"配置与数据分离"的清晰边界。**推荐解读 4**，理由：

- asset_snapshot 严格保持"由数据驱动的快照"，不被配置变更污染
- /correction 读取时优先 user_config，缺失时回退到 asset_snapshot.target_ratio
- 比例演化看板需要"目标比例演化"时，单独建 target_ratio_history 表（与 nav_history 类似）

**新增触发**：如果选解读 4，第二轮评审 P0 4.2.3（目标比例变更历史）需要正式建表，不再是 P3。

---

### 2.3 chat_history 字段路径审计

| 字段 | 写入路径 | 读取路径 | 状态 |
|------|---------|---------|------|
| id | auto | n/a | ✅ |
| user_id | default 1 | implicit | 🟡 |
| conversation_id | 截图解析 / AI 顾问 | 数据管理、AI 顾问 | ✅ |
| role | 截图解析 / AI 顾问 | 数据管理、AI 顾问 | ✅ |
| content | 截图解析 / AI 顾问 | 数据管理、AI 顾问 | ✅ |
| created_at | auto | 数据管理、AI 顾问 | ✅ |
| conversation_type | 截图解析 / AI 顾问 | 侧边栏 | ✅ |

#### 2.3.1 🟡 **chat_history 缺少对话元数据**

7.4.2 说数据管理页面的解析日志"以对话列表形式展示在截图解析对话界面的左侧栏中，每条记录显示日期、基金数量、入库状态"。7.4.7 AI 顾问页面"左侧可切换历史对话列表"。

但 chat_history 表**没有对话标题、最后消息时间、未读标记等元数据字段**。当前能查到的只有按 conversation_id 聚合的 role/content/created_at。**"基金数量""入库状态"如何聚合得到？**

**问题链路**：
- "基金数量"：需要 `SELECT COUNT(*) FROM asset_raw WHERE conversation_id=?`。可计算。
- "入库状态"：需要判断该 conversation 是否有"已确认"的 assistant 消息关联。可计算。
- "对话标题"：截图解析对话标题应该是 "解析 - 2026-07-09"，AI 顾问对话标题应该是首条用户消息摘要。需要存储。

**结论**：所有展示元数据都需要派生查询或新建元数据表。当前实现会增加不必要的 DB 压力。

**建议**：

- 选项 A：保留派生查询，添加数据库索引 `(user_id, conversation_id, created_at)` 已在 6.6 定义
- 选项 B：新建 `conversation_meta` 表，存储 title、last_message_time、message_count、status
- **推荐 A**：MVP 阶段派生查询足够，避免元数据表与 chat_history 同步问题

---

## 3. 问题 2：前端页面数据 → 后端 API 对应审计

### 3.1 API 控制器清单（来自 5.3）

```
fincontrol-backend/src/main/java/com/fincontrol/
├── controller/
│   ├── SnapshotController.java      ← 快照相关
│   ├── AssetController.java         ← 资产相关
│   ├── CorrectionController.java    ← 校正相关
│   └── ChatController.java          ← 对话相关
```

### 3.2 前端页面与 API 对应矩阵

| 前端页面 | 展示数据 | 所需 API（推断）| 文档中是否定义 |
|---------|---------|----------------|-------------|
| 7.4.1 首页 | 余额类卡片 | `GET /api/snapshot/latest?category=余额类` | ❌ |
| 7.4.1 首页 | 六大类总值 | `GET /api/snapshot/six-total` | ❌ |
| 7.4.1 首页 | **累计收益率** | **无数据源**（详见 3.3.1）| 🔴 |
| 7.4.1 首页 | 环形图 | `GET /api/snapshot/latest?categories=六大类` | ❌ |
| 7.4.1 首页 | **最近操作时间线** | **数据源不明**（详见 3.3.2）| 🔴 |
| 7.4.1 首页 | 六大类明细表格 | `GET /api/snapshot/latest?detail=true` | ❌ |
| 7.4.2 数据管理 | 截图上传 | `POST /api/screenshot/upload` | ❌ |
| 7.4.2 数据管理 | 调用 DeepSeek 解析 | `POST /api/screenshot/parse` | ❌ |
| 7.4.2 数据管理 | 确认入库 | `POST /api/snapshot/confirm` | ❌ |
| 7.4.2 数据管理 | 解析日志侧边栏 | `GET /api/conversations?type=screenshot_parse` | ❌ |
| 7.4.2 数据管理 | **"重新解析"按钮** | **无 API**（详见 3.3.3）| 🔴 |
| 7.4.3 月度操作台 | 输入区默认值 | `GET /api/snapshot/latest?fields=V_curr,V_m,V_b,u_high` | ❌ |
| 7.4.3 月度操作台 | 二元一次方程计算 | `POST /api/correction/monthly/calculate` | ⚠️ CorrectionController 提及 |
| 7.4.3 月度操作台 | 取整弹窗实时重算 | `POST /api/correction/monthly/recalculate` | ❌ |
| 7.4.3 月度操作台 | 确认入库 | `POST /api/correction/monthly/confirm` | ❌ |
| 7.4.4 资产配置 | 读取当前配置 | `GET /api/config` | ❌ |
| 7.4.4 资产配置 | 保存配置 | `POST /api/config` | ❌ |
| 7.4.4 资产配置 | 同步 target_ratio | （详见 2.2.1）| ❌ |
| 7.4.5 净值曲线 | 历史净值 | **无表存储**（详见 3.3.4）| 🔴 |
| 7.4.5 净值曲线 | 累计收益率 | 同上 | 🔴 |
| 7.4.5 净值曲线 | 日收益柱状图 | 同上 | 🔴 |
| 7.4.5 净值曲线 | **手动校正入口** | **无 API + 无存储路径** | 🔴 |
| 7.4.6 比例演化 | 最新比例色带 | `GET /api/snapshot/latest?ratios=true` | ❌ |
| 7.4.6 比例演化 | 历史快照列表 | `GET /api/snapshot/history` | ⚠️ SnapshotController 提及 |
| 7.4.6 比例演化 | **子类别颗粒** | sub_detail 字段（未启用）| 🟡 |
| 7.4.7 AI 顾问 | 发送消息 | `POST /api/chat/send` | ⚠️ ChatController 提及 |
| 7.4.7 AI 顾问 | 意图分类 | （集成在 /chat/send 内部）| ⚠️ |
| 7.4.7 AI 顾问 | 历史对话列表 | `GET /api/conversations?type=ai_assistant` | ❌ |
| 7.4.7 AI 顾问 | 加载历史对话 | `GET /api/conversations/:id` | ❌ |
| 7.4.7 AI 顾问 | 新建对话 | `POST /api/conversations` | ❌ |
| 7.4.7 AI 顾问 | **快捷提问按钮** | （前端硬编码问题文本）| ❌ |
| 7.4.8 季度操作台 | 灰显占位 | 无 | ✅ |

#### 3.3 关键缺口

##### 3.3.1 🔴 首页"累计收益率"无数据源

7.4.1 首页卡片显示"累计收益率 +8.42%"。但：

- 累计收益率 = (当前市值 - 累计投入本金) / 累计投入本金
- 当前市值可从 asset_snapshot 计算
- **累计投入本金无存储表**（2nd review P0 4.1.1 已指出）
- nav_history 表也不存在（2nd review P0 4.1.2）

**当前选项**：

- 暂时在首页硬编码一个固定值（8.42%），假装有数据
- 删除首页"累计收益率"卡片，Phase 3 再加
- 临时用 `current_total - 起始值` 计算（但起始值不明）

**推荐**：Phase 1 先隐藏"累计收益率"卡片，Phase 3 净值曲线时同步设计 nav_history 表。

##### 3.3.2 🔴 "最近操作时间线"数据源不明

7.4.1 显示"最近操作（时间线）：最近 3-5 条记录（截图解析、月度操作、手动输入）"。

但：

- 截图解析操作 → chat_history（assistant 角色）
- 月度操作 → operation_log（2nd review 新增）
- 手动输入 → asset_raw（source=manual_input）

**3 种操作在不同表**，如何聚合"最近操作"？需要跨表联合查询或新建 unified_operations 视图。

**建议**：Phase 1 临时方案：从 chat_history 查最近 5 条 conversation_type=screenshot_parse；Phase 2 operation_log 上线后改为 unified_operations 视图。

##### 3.3.3 🔴 "重新解析"按钮无 API

7.4.2 提到"重新解析"按钮。两种实现：

- A：重新调用 DeepSeek API 解析同一张截图
- B：仅重新打开确认面板，让用户重选

两种需要不同 API（A 是 `POST /api/screenshot/reparse`；B 是 `GET /api/screenshot/reopen-confirm/:conversation_id`）。

**建议**：选择 A（更有用），API 为 `POST /api/screenshot/reparse`，body 传 conversation_id。

##### 3.3.4 🔴 净值曲线（7.4.5）数据源完全缺失

7.4.5 显示累计净值、累计收益率、累计收益、净值走势、日收益柱状图、关键事件标记、手动校正。

- 累计净值：需要 `current_total / 初始本金`
- 累计收益率：需要 nav_history
- 日收益：需要 daily_returns 表
- 关键事件：需要 event_log 表
- **手动校正**：需要 manual_nav_entry 表

**4 个表全都不存在**。净值曲线 Phase 3（2-3 天）从零搭建 4 个表 + 前端图表 + 后端 API，**时间严重不足**。

**建议**：

- Phase 3 启动前必须先设计 4 张表（nav_history、daily_returns、event_log、manual_nav_entry）
- 或者拆分 Phase 3 为 3a（数据模型）+ 3b（图表）
- 或者推迟净值曲线到 Phase 4

##### 3.3.5 🟡 完整 REST API 契约文档缺失

整个文档没有一张完整的"前端 URL → 后端 Controller.method → Request/Response DTO"映射表。

只有 5.3 列出 4 个 Controller 的存在，但**每个 Controller 暴露哪些 endpoints、接收什么参数、返回什么结构**，全部缺失。

**影响**：

- 前端开发无法与后端并行（必须等后端实现后才能联调）
- 接口变更无追溯
- ADR 无法记录关键 API 决策

**建议**：Phase 1 启动前必须产出 `docs/api-contract.md`，列出所有 API endpoints。

---

## 4. 问题 3：Phase 1-3 开发计划覆盖度审计

### 4.1 当前阶段划分（来自第八章）

| Phase | 核心任务 | 时间 | 验收标准 |
|-------|---------|------|---------|
| Phase 1 | 数据流水线闭环 | 3-4天 | 截图上传→API解析→入库→首页展示完整闭环 |
| Phase 2 | 核心业务功能 | 2-3天 | 资产配置+月度操作台+历史查询可用 |
| Phase 3 | 数据可视化 | 2-3天 | 净值曲线+比例演化+日收益明细可用 |
| Phase 4 | 已知问题修复 | 2-3天 | 异常处理+数据校验+UI优化完成 |
| Phase 5 | 拓展性功能 | 远期 | LQR集成+多用户+移动端适配 |

### 4.2 Phase 1 详细验收标准（来自第八章末尾）

- 用户在 Web 前端上传支付宝截图
- 后端调用 DeepSeek API 解析图片，返回 JSON
- 解析结果正确写入 asset_raw 表，自动汇总写入 asset_snapshot 表
- 首页从后端 API 获取最新快照数据并正确展示六大类金额和占比
- AI顾问页面可进行多轮对话，对话历史存入 chat_history 表
- 所有 API 接口支持 user_id 参数（默认值 1）

### 4.3 关键遗漏

#### 4.3.1 🔴 Phase 1 计划与第一轮评审 P0 不对齐

第一轮评审 P0（已在 6 项 P0 列出）需要在 Phase 1 启动前解决：

- asset_raw.category 增加"余额类"
- 三表写入加事务注解
- 用户修改映射的 UPDATE 规则
- AI 解析失败异常路径
- 快照日期校验规则
- 前端防抖

**但 Phase 1 验收标准未提及这些 P0**。如果按当前 Phase 1 验收标准开发，这些 P0 可能被遗漏。

**建议**：Phase 1 验收标准修订——在原有 6 项基础上追加 P0 验收项。

#### 4.3.2 🔴 Phase 1 计划与第三轮评审 P0 不对齐

第三轮评审 P0（12 项）：

- 全局状态管理方案
- 大类确认面板二次确认弹窗 + 10秒撤销
- 余额类纳入下拉列表
- 确认面板"忽略该条"按钮
- AI 顾问 B.3 Prompt few-shot
- 系统 prompt 切换实现
- 快照日期默认值优先级
- Prompt B.1 日期格式约束
- 等等

**Phase 1 计划未提及任何第三轮 P0**。但这些 P0 都是 Phase 1 开发中会立即遇到的问题。

#### 4.3.3 🟡 Phase 1 时间预算过于乐观

Phase 1 = 3-4 天，包含：

- 截图上传 + DeepSeek 解析（第一轮 P0 6 项 + 第三轮 P0 12 项）
- 首页展示（涉及 profit 字段读取问题、累计收益率数据源问题）
- AI 顾问多轮对话（涉及第三轮 P0 系统 prompt 切换、few-shot 分类器）

**估算**：仅 P0 修复就需要至少 1-2 天。剩下 2-3 天完成三大功能闭环（截图解析、数据写入、AI 对话）**严重不足**。

**建议**：将 Phase 1 拆分为 1a（数据流水线闭环）和 1b（AI 顾问多轮对话），每个 3-4 天。

#### 4.3.4 🔴 Phase 3 净值曲线时间严重不足

Phase 3 = 2-3 天，要实现：

- 净值曲线（含累计净值、累计收益率、累计收益 3 张卡片 + 折线图 + 日收益柱状图 + 关键事件标记）
- 比例演化
- 日收益明细

**前置依赖**：需要 nav_history、daily_returns、event_log、manual_nav_entry 4 张表（2nd review P0 4.1.2）。这些表在 Phase 2 才设计，Phase 3 直接用？Phase 2 已结束，无设计时间。

**建议**：

- 将净值曲线的 4 张表设计提前到 Phase 2 末尾
- 或者 Phase 3 拆分为 3a（数据模型 + 后端 API）和 3b（前端图表）
- 或者净值曲线推迟到 Phase 4

#### 4.3.5 🟡 Phase 2 缺乏"跨页面状态共享"实现

第三轮 P0 1.5.1：全局状态管理方案。Phase 2 实现月度操作台时，需要：

- /config 修改后 /correction 自动看到新值
- /data 上传后 /correction 默认值更新

如果 Phase 2 不实现全局状态管理，/correction 永远拿到旧数据。

**建议**：Phase 2 启动前补齐 Zustand（或其他）状态管理方案。

#### 4.3.6 🟡 Phase 4 "已知问题修复"职责不清

"已知问题"如果指：

- A：第一/二/三轮评审的 P1/P2 问题
- B：Phase 1-3 编码中发现的 bug

两种解读工作量差异巨大。

**建议**：Phase 4 明确为"补齐 P1/P2 问题"，编码期 bug 修复不应等到 Phase 4（瀑布模式风险）。

#### 4.3.7 🟡 缺失的 Phase 0

整个开发计划缺少 **Phase 0：基础设施搭建**。包括：

- 后端项目骨架（Maven、Spring Boot 初始化、依赖管理）
- 前端项目骨架（Vite + React 初始化、目录结构、状态管理库选型）
- 数据库 Schema 初始化脚本
- API 契约文档（详见 3.3.5）
- 测试基础设施（单元测试框架、E2E 框架）
- CI/CD 流水线（可选）

**建议**：显式增加 Phase 0（1-2 天）。

#### 4.3.8 🟡 Phase 5 过于模糊

Phase 5 同时包含 LQR、多用户、移动端——3 个独立大特性。

**建议**：拆分为 5a（LQR 实战集成）、5b（多用户）、5c（移动端）。

---

## 5. 问题 4：跨模块修改的连锁影响（关键 14 条）

> 以下是前三轮评审修改项在跨模块链路上的连锁影响清单。每条都涉及 ≥ 3 个模块。

### 链 1：余额类修复的跨模块连锁

**起点**：第一轮 P0 1.1：asset_raw.category 增加"余额类"
**连锁**：

1. `asset_raw.category` 枚举：+ "余额类"
2. `fund_category_map.category`：必须支持"余额类"映射
3. `asset_snapshot.balance_fund`：SELECT SUM 逻辑（第一轮 P0 已澄清）
4. 第三轮 P0 2.2.4.3：5.6.4 下拉选择器必须包含"余额类"
5. 截图解析 Prompt B.1：必须告诉 AI "余额类 = 余额宝 + 余额"
6. 首页余额类卡片：data source 已 OK（第一轮已澄清）
7. **未覆盖**：fund_category_map 表结构是否需要新增"余额类"基金的历史映射数据？

**结论**：余额宝、余额需要手动预录入 fund_category_map。

---

### 链 2：取整交互的跨模块连锁

**起点**：第二轮 P0 2.1.1：取整重算公式缺失
**连锁**：

1. 后端 `CorrectionStrategy.execute`：返回值增加 `roundingDeviation` 字段
2. 第二轮 3.4.2：CorrectionResult 输出补齐
3. 前端取整弹窗：实时重算 UI（第三轮已识别）
4. `operation_log` 表（第二轮 3.2.4）：存储 rounding_strategy + deviation_m + deviation_b
5. **边界处理**：取整偏差是否触发某个边界？文档未明示
6. **累计效应**：连续多月取整偏差累积是否触发边界？第三轮 5.2.1 已识别但标注"不采纳"

**结论**：取整偏差与边界处理的耦合未明确。

---

### 链 3：confirmed_at 字段的跨模块连锁

**起点**：第一轮 P0 1.3：confirmed_at 字段补充语义
**连锁**：

1. `asset_raw.confirmed_at`：新增字段（已确认）
2. 第一轮补正：is_latest 翻转 SQL：`ORDER BY confirmed_at DESC, id DESC`
3. 前端首页"已入库 · 2026-06-09 20:30"：使用 confirmed_at 还是 created_at？
4. 第二轮 3.2.4：`operation_log.confirmed_at`：与 asset_raw.confirmed_at 是否一致语义？
5. **第三轮 4.3.3**：首页"最新快照"按 snapshot_date 还是 confirmed_at 排序？

**结论**：confirmed_at 的语义已定义，但跨表（asset_raw vs operation_log）和跨页面（首页 vs 数据管理）的应用一致性需统一。

---

### 链 4：target_ratio 同步的跨模块连锁

**起点**：7.4.4 资产配置页 target_ratio 同步规则不清
**连锁**：

1. `/config` 写入 `user_config.target_ratios`（明确）
2. `asset_snapshot.target_ratio`：写入策略（详见 2.2.1 的 4 种解读）
3. `/correction` 输入框：从 user_config 还是 asset_snapshot 读？
4. 首页环形图目标线：从 user_config 还是 asset_snapshot 读？
5. 比例演化看板：历史 target_ratio 怎么显示？

**结论**：target_ratio 的写入策略 + 读取优先级未统一，将导致前端展示与后端数据不一致。

---

### 链 5：AI 系统 prompt 切换的跨模块连锁

**起点**：第三轮 P0 3.2.1：system prompt 切换实现未定义
**连锁**：

1. 后端 ChatController：每次 API 调用都重新组装 messages（第三轮已澄清）
2. `chat_history`：是否记录使用的 prompt 类型？
3. 第三轮 3.4.4：intent_classifier 未纳入 `prompt_versions` 表
4. 前端对话气泡：是否显示"现在使用 ZOH prompt"标识？
5. **多 prompt 版本兼容**：如果 ai_assistant 升级到 v2.0，旧对话用 v1.0 的 system prompt，如何标注？

**结论**：prompt 版本切换与历史对话的回放兼容性未设计。

---

### 链 6：V_curr 不含余额类的跨模块连锁

**起点**：第二轮已澄清 V_curr 不含余额类
**连锁**：

1. 月度操作台输入框"当前总资产"：用户输入什么？
   - 选项 A：六大类合计（不含余额类）→ 符合 V_curr 定义
   - 选项 B：总资产（含余额类）→ 与 V_curr 定义冲突
2. `CorrectionInput.totalAsset`：澄清为六大类合计
3. 首页"六大类总值"卡片：示例 ¥6,480.91 符合
4. 首页"余额类"卡片：单独显示，不混入
5. **未覆盖**：7.4.3 输入框的 placeholder 文案需要明示"不含余额类"

**结论**：V_curr 不含余额类需在 7.4.3 输入框 UI 上明示，否则用户可能误输入。

---

### 链 7：operation_log 表的跨模块连锁

**起点**：第二轮 P0 2.1.10：新增 operation_log 表
**连锁**：

1. 月度操作台结果：写入 operation_log（明确）
2. LQR 季度操作台：写入 operation_log.source='quarterly_correction'（明确）
3. 比例演化看板：是查 asset_snapshot 还是 operation_log？两者都是历史数据
4. 首页"最近操作时间线"（第三轮 3.3.2）：是否包含 operation_log？
5. **未覆盖**：operation_log 与 asset_snapshot 的数据如何关联？同一日期有两个记录源？

**结论**：operation_log 的角色是"操作流水"还是"操作结果快照"，定位不清。

**建议**：定位为"操作流水"（仅记录操作，不重复存快照值），历史快照仍以 asset_snapshot 为主。

---

### 链 8：截图解析失败的跨模块连锁

**起点**：第一轮 P0 1.6：解析失败异常路径
**连锁**：

1. `chat_history`：失败记录写入（role=assistant，content=错误详情，conversation_type=screenshot_parse）
2. 第三轮 P0 4.2：日期格式校验：失败时是否有默认日期？
3. 第三轮 2.2.4：失败时确认面板是否弹出？大概率不弹出
4. 7.4.2 解析日志侧边栏：是否显示失败记录？状态字段值是什么？
5. **未覆盖**：失败时是否创建 conversation_id？连续失败如何计数？

**结论**：解析失败的完整数据生命周期未定义。

---

### 链 9：sub_detail JSON 字段的跨模块连锁

**起点**：6.3 asset_snapshot.sub_detail JSON 字段预留
**连锁**：

1. 第二轮：LQR 季度校正结果写入 sub_detail
2. 7.4.6 比例演化看板：是否读取 sub_detail？
3. 第二轮 4.2.2：sub_detail JSON Schema 未形式化
4. **未覆盖**：Phase 3 比例演化看板开发时，是否会误读 sub_detail 字段导致解析失败？

**结论**：sub_detail 字段的读取路径只有 LQR，比例演化看板不应读取（避免未定义结构的 JSON 解析错误）。

---

### 链 10：用户配置项的跨模块连锁

**起点**：6.5 user_config 表
**连锁**：

1. 第二轮 P0 1.1.6：U_high 缺少 high_vol_dca_budget 配置
2. 第二轮 P0 3.2.13：carry_over_amount 字段
3. 第一轮 5.1：source 枚举扩展（re_parse、import_csv 等）
4. 第三轮 P0 2.2.4.3：下拉选择器动态读取 vs 硬编码六大类？
5. **当前 user_config 字段**：purchase_threshold、monthly_budget_limit 是标量。新增字段（high_vol_dca_budget、carry_over_amount）也是标量 OK。但 target_ratios 是 Map，如何存？config_value VARCHAR(500) 是否够？

**结论**：user_config 表的 config_value VARCHAR(500) 是否能容纳 target_ratios Map（JSON 字符串约 200 字符）需要确认。

---

### 链 11：撤销机制的跨模块连锁

**起点**：第三轮 P0 2.2.2.1：10秒撤销提示
**连锁**：

1. 前端 toast UI（明确）
2. 后端 DELETE/rollback API：删除哪些记录？
   - 删除刚写入的 asset_raw
   - 删除刚写入的 fund_category_map（如有新增）
   - 恢复 asset_snapshot 旧 is_latest 状态
3. 三表事务：第一轮 P0 1.3 已要求 @Transactional。撤销是否在同一事务中？
4. chat_history：删除"已入库"消息还是改为"已撤销"？
5. **未覆盖**：撤销后用户再点确认入库，是创建新记录还是恢复？

**结论**：撤销 API 与三表事务的耦合未设计。

---

### 链 12：全局状态管理的跨模块连锁

**起点**：第三轮 P0 1.5.1：Zustand 全局状态
**连锁**：

1. 涉及所有 7 个页面（首页、数据管理、月度、配置、净值、比例、AI）
2. `/data` 上传 → 触发 assetSnapshotStore 更新 → 首页、月度、比例自动响应
3. `/config` 修改 → 触发 userConfigStore 更新 → 月度、首页、比例自动响应
4. **未覆盖**：什么时候 fetch 后端？什么时候读 store？什么时候 force refresh？
5. **性能影响**：每次 store 变化是否触发所有订阅者重渲染？

**结论**：全局状态管理需要明确"何时 fetch、何时读缓存、何时 force refresh"的策略。

---

### 链 13：首页"累计收益率"的跨模块连锁

**起点**：3.3.1 已指出无数据源
**连锁**：

1. 首页卡片需要数据（明确）
2. 净值曲线（Phase 3）也需要（明确）
3. 第二轮 P0 4.1.1：累计投入本金无存储
4. 第二轮 P0 4.1.2：nav_history 表缺失
5. **相互依赖**：首页累计收益率与净值曲线共用数据源，但 Phase 1 与 Phase 3 跨度大

**结论**：首页累计收益率卡片建议 Phase 1 隐藏，与净值曲线同步上线。

---

### 链 14：profit 字段的跨模块连锁

**起点**：2.1.1 已指出写入孤儿
**连锁**：

1. 截图解析写入 profit（明确）
2. 数据模型定义 profit（明确）
3. 没有任何页面读取 profit
4. **未来需求**：可能想看"我每只基金赚了多少"或"累计浮盈"
5. **数据迁移风险**：6.1 说"原始数据不可变"，现在加读取位置不影响历史数据

**结论**：建议在数据管理页面或首页明细表格增加 profit 列，最小成本激活该字段。

---

## 6. 跨模块问题的总体分类

| 类别 | 数量 | 代表问题 |
|------|-----|---------|
| 🔴 数据源缺失 | 5 | profit 孤儿、累计收益率无源、净值曲线 4 表缺失、target_ratio 同步、最近操作时间线 |
| 🔴 API 契约缺失 | 1 | 完整 REST API 文档未产出 |
| 🔴 跨模块一致性 | 6 | 14 条连锁影响链 |
| 🟡 Phase 划分问题 | 5 | Phase 1 时间预算、Phase 3 时间不足、Phase 0 缺失、Phase 5 拆分 |
| 🟡 文档表述模糊 | 4 | target_ratio 4 种解读、operation_log 定位、撤销语义、profit 字段 |

---

## 7. 最关键的 10 个跨模块矛盾

按优先级排列：

1. **🔴 target_ratio 同步策略有 4 种解读**（问题 2.2.1）—— 阻塞 /config、/correction、比例演化三个页面的数据一致性
2. **🔴 profit 字段写入但无读取路径**（问题 2.1.1）—— 字段定义与功能脱节
3. **🔴 首页"累计收益率"无数据源**（问题 3.3.1）—— Phase 1 必须隐藏或临时方案
4. **🔴 完整 REST API 契约文档缺失**（问题 3.3.5）—— 阻塞前后端并行开发
5. **🔴 Phase 3 净值曲线 4 张表完全缺失**（问题 4.3.4）—— 2-3 天不可能完成
6. **🔴 Phase 1 计划与第一/三轮 P0 不对齐**（问题 4.3.1、4.3.2）—— P0 可能被遗漏
7. **🟡 Phase 0 基础设施搭建未显式**（问题 4.3.7）—— 项目骨架、CI/CD、测试框架
8. **🟡 14 条跨模块连锁链未逐条评估影响范围**（问题 5）—— 任一修复可能产生副作用
9. **🟡 operation_log 表的角色定位**（链 7）—— 操作流水 vs 操作快照
10. **🟡 撤销 API 与三表事务的耦合**（链 11）—— 撤销是"删除并恢复"还是"标记作废"

---

## 8. 跨模块问题汇总与优先级

| 优先级 | 数量 | 修复时机 |
|-------|-----|---------|
| 🔴 P0（阻塞 Phase 1 编码）| 6 个 | Phase 1 启动前 |
| 🟡 P1（Phase 1-2 启动前补齐）| 8 个 | Phase 1-2 期间 |
| 🟠 P2（Phase 2-3 期间处理）| 10 个 | Phase 2-3 期间 |
| 🟢 P3（Phase 5 / 远期）| 5 个 | Phase 5 / 远期 |

### 8.1 P0 详细清单（Phase 1 启动前必解决）

| 编号 | 主题 | 修复内容 |
|-----|------|---------|
| 2.1.1 | profit 字段孤儿 | 首页明细表或数据管理页增加 profit 列 |
| 2.2.1 | target_ratio 同步语义 | 选择解读 4（仅写 user_config），/correction 读 user_config |
| 3.3.1 | 累计收益率无源 | Phase 1 隐藏卡片，Phase 3 与净值曲线同步 |
| 3.3.5 | REST API 契约缺失 | Phase 0 产出 docs/api-contract.md |
| 4.3.1 | Phase 1 与第一轮 P0 不对齐 | Phase 1 验收标准追加第一轮 P0 6 项 |
| 4.3.2 | Phase 1 与第三轮 P0 不对齐 | Phase 1 验收标准追加第三轮 P0 12 项 |

### 8.2 P1 详细清单

| 编号 | 主题 |
|-----|------|
| 3.3.2 | 最近操作时间线数据源 |
| 3.3.3 | 重新解析按钮 API |
| 4.3.3 | Phase 1 时间预算 |
| 4.3.5 | 跨页面状态管理 |
| 5-链 4 | target_ratio 同步 4 解读统一 |
| 5-链 5 | prompt 版本兼容 |
| 5-链 7 | operation_log 角色定位 |
| 5-链 11 | 撤销 API 语义 |

### 8.3 P2 详细清单

| 编号 | 主题 |
|-----|------|
| 3.3.4 | 净值曲线 4 表设计 |
| 4.3.4 | Phase 3 时间拆分 |
| 4.3.6 | Phase 4 职责明确 |
| 4.3.7 | Phase 0 增加 |
| 4.3.8 | Phase 5 拆分 |
| 5-链 2 | 取整偏差与边界耦合 |
| 5-链 6 | V_curr 输入框 UI |
| 5-链 8 | 解析失败生命周期 |
| 5-链 9 | sub_detail 读取策略 |
| 5-链 12 | 全局状态管理 fetch 策略 |

### 8.4 P3 详细清单

| 编号 | 主题 |
|-----|------|
| 5-链 1 | 余额宝预录入 fund_category_map |
| 5-链 3 | confirmed_at 应用一致性 |
| 5-链 9 | sub_detail 形式化 |
| 5-链 10 | user_config 字段长度 |
| 5-链 13 | 累计收益率与净值曲线同步上线 |

---

## 9. 分阶段修复计划

### Phase 0 启动前补齐（本周内）

- [ ] 产出 `docs/api-contract.md`：列出所有 REST API endpoints
- [ ] target_ratio 同步策略锁定（推荐解读 4）
- [ ] profit 字段读取位置确定（推荐首页明细表）
- [ ] 首页"累计收益率"卡片 Phase 1 临时隐藏
- [ ] Phase 1 验收标准修订：追加第一/三轮 P0

### Phase 0（1-2 天）：基础设施

- [ ] 后端项目骨架（Maven + Spring Boot + MyBatis-Plus）
- [ ] 前端项目骨架（Vite + React + Zustand）
- [ ] 数据库 Schema 初始化脚本
- [ ] 测试框架（JUnit + Mockito + Vitest）

### Phase 1（拆分为 1a + 1b）

- [ ] 1a（数据流水线，3-4 天）：修复第一/三轮 P0 中与数据流水线相关的项
- [ ] 1b（AI 顾问，3-4 天）：修复第三轮 P0 中与 AI 相关的项

### Phase 2（核心业务，3-4 天）

- [ ] target_ratio 同步实现（解读 4）
- [ ] 跨页面状态管理实现
- [ ] operation_log 表设计 + 写入路径
- [ ] 最近操作时间线数据源统一

### Phase 3（数据可视化，4-5 天，扩展时间）

- [ ] 净值曲线 4 表设计（nav_history、daily_returns、event_log、manual_nav_entry）
- [ ] 净值曲线后端 API
- [ ] 净值曲线前端图表
- [ ] 比例演化看板

### Phase 4（问题修复 + UI 优化，2-3 天）

- [ ] P1/P2 残留问题处理
- [ ] 已知 bug 修复

### Phase 5（远期，拆分为 5a + 5b + 5c）

- [ ] 5a：LQR 实战集成
- [ ] 5b：多用户
- [ ] 5c：移动端

---

## 10. 评审结论

本轮评审发现 **29 个具体问题**（🔴 P0 6 个、🟡 P1 8 个、🟠 P2 10 个、🟢 P3 5 个），加 **14 条跨模块连锁影响链**。

**核心发现**：

1. **5 个 🔴 数据源缺失**：profit 字段、累计收益率、净值曲线 4 表、target_ratio 同步、最近操作时间线——这些是 Phase 1-3 必须解决的硬阻塞
2. **完整 REST API 契约文档缺失**：阻塞前后端并行开发
3. **Phase 计划与 P0 不对齐**：第一/三轮 P0 在 Phase 验收标准中未明确点名
4. **Phase 3 时间预算严重不足**：2-3 天实现净值曲线（4 张表 + 后端 + 前端）不可能
5. **14 条跨模块连锁链**：任一修改都会影响多个模块，需要逐条评估

**四轮评审汇总（170 个问题）**：

| 轮次 | 范围 | 问题数 | P0 |
|------|------|-------|-----|
| 第一轮 | 数据流水线 + 数据模型 | 47 | 6 |
| 第二轮 | 核心算法 + 业务约束 | 63 | 18 |
| 第三轮 | 前端 + AI 顾问 | 60 | 12 |
| 第四轮 | 跨模块一致性 | 29 | 6 |
| **合计** | | **199** | **42** |

（说明：跨三轮评审部分 P0 项有重叠，实际独立 P0 项约 30 个左右，详见 docs/README.md 合并 P0 清单）

**Phase 0 启动前必须补齐的 6 项**：

1. REST API 契约文档
2. target_ratio 同步策略
3. profit 字段读取位置
4. 首页"累计收益率"卡片 Phase 1 隐藏
5. Phase 1 验收标准追加第一/三轮 P0
6. Phase 计划修订（Phase 0 增加、Phase 1 拆分、Phase 3 时间扩展、Phase 5 拆分）

**评审体系完结**：四轮评审覆盖了数据流水线、核心算法、前端交互、AI 分流、跨模块一致性五大维度。建议将本轮产出与前三轮合并，作为设计文档 v2.1 的输入。