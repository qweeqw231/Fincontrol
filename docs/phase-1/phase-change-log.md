# Phase 变更记录（原教旨划分 vs 实际交付）

> **用途**：记录「Phase 0 决策 6 / 决策 34 定义的 Phase 划分」与「实际交付序列」之间的偏差，显式承认跳跃，降低解释成本。
>
> **定位**：Phase 编号 = **能力里程碑（roadmap）**，用于说明功能归属与设计归属；本文件另立**实际交付序列**，用于说明真实时间线。两者不等同。
>
> **维护**：每次跨越 Phase 边界（提前交付 / 推迟 / 重排）时追加一节，不重写历史。

---

## 1. 原教旨 Phase 划分（决策 6 修订 + 决策 34 重排）

| Phase | 定义 | 状态（按原划分口径） |
|---|---|---|
| Phase 0 | 基础设施：骨架 / db-schema / API 契约 / 四轮评审决策 | ✅ 2026-07-09 完成 |
| Phase 1 | 数据流水线闭环（上传 → 解析 → 确认 → 三表入库 → 首页展示）| ✅ 2026-07-25 完成（决策 34）|
| Phase 2 | 未完成的数据管理 + 资产配置 + 月度操作台 | ⏳ 未启动 |
| Phase 3 | 数据模型先行（nav_history 等 4 表）→ 净值曲线 + 比例演化 + 日收益明细 | ⏳ 未启动 |
| Phase 4 | 已知问题、数据校验、UI 优化、前端收尾 | ⏳ 未启动 |
| Phase 5 | AI 顾问前端 UI、LQR、多用户、移动端 | ⏳ 远期 |

来源：[决策 6](../phase-0/decisions.md)（Phase 计划修订）、[决策 34](../phase-0/decisions.md)（Phase 1 收官与路线重排）。

---

## 2. 实际发生的偏差（截至 2026-09-30）

### 偏差 A：Phase 3 的「净值曲线 + 比例演化」于 1b 末期提前交付（2026-09-28 ~ 09-30）

- 关联 commit：`987b1c1 feat(phase3): 净值曲线 + 比例演化看板 + 外部数据导入`

**已交付**：

- **数据层**：新增 `nav_history` / `nav_milestone` 两张表（DDL 位于 `scripts/import-data/import-data.cjs`，尚未并入 `docs/phase-0/db-schema.sql`）；导入 portfolio_daily_complete_v3（348 个交易日，2025-10-13 ~ 2026-09-25）+ asset_table_total（131 天）到 `asset_raw` / `asset_snapshot` / `snapshot_meta` / `fund_category_map`；从 mcf 静态站点与北极星实证文档补录 8/12 之后的 3 条里程碑 + 10 条 `operation_log` 校正记录（ZOH/LQR + 战术调仓），净值数字已与 nav_history 交叉验证
- **后端**：`NavController` + `NavQueryService`（`/api/nav/history`、`/api/nav/operations`、`/api/ratio/history`）
- **前端**：`NAVPage`（净值走势 + 累加盈亏双图、线性/对数切换、关键事件时间线、校正与操作记录）、`RatioPage`（六大类堆叠面积图四视图 + 首末对比表）；侧边栏启用 `/nav` 与 `/ratio`
- **附带修复**：累计收益卡片降级逻辑（外部导入快照无逐基金收益字段时，改取同账户 `nav_history.cumulative_profit` 累加口径；持有侧无来源则显示「—」；新增 `profitSource` 标识来源）

**未交付（Phase 3 原范围中的欠项）**：

- `daily_returns`（日收益明细）——未做
- `event_log` / `manual_nav_entry` 两张原计划表——未建（里程碑与操作记录实际由 `nav_milestone` + `operation_log` 承担）
- Modified Dietz / XIRR 算法升级（决策 4 v2 的 Phase 3 触发条件）——未做，累计收益仍为 `phase1_simple` 算法标识

**判定**：**Phase 3 身份保留 + 标注「提前交付（部分）」**。不重编号、不改判为 1b.X。

### 偏差 B：AI 顾问前端 UI 也已提前实现（原属 Phase 5）

- 关联 commit：`9d89397 feat(ai+data): AI 顾问界面落盘 + 前序会话遗留修复`
- **已交付**：AIPage 对话界面（会话列表 / 多轮消息 / 置顶 / 删除）+ chatStore + `ChatService` 注入 `KnowledgeBaseService`（RAG 检索）+ 实盘实证知识库（93 条）
- **判定**：决策 34 中「AI 顾问前端 UI 顺延至 Phase 5」的现状描述已过期；实际前端 UI 已可用，仍保留 Phase 5 身份（LQR / 多用户 / 移动端未开始）

### 偏差 C：Phase 2 的部分能力被顺带补齐

- 数据管理页修复批次（2026-09-28 ~ 09-30）：快照确认弹窗与批量提交流程修复、`friendlyError` 错误码映射、axios 业务 message 透传等
- **判定**：属 Phase 2 范畴的局部补强，不改变 Phase 2 主体（历史查询 UI / 资产配置 / 月度操作台）未启动的事实

---

## 3. 当前实际边界（2026-09-30 重定）

**已可用（浏览器可访问）**：

- 首页（`/`）：当前快照 + 累计/持有收益 + 六大类 + 配置偏差
- 数据管理（`/data`）：上传 → 解析 → 确认 → 入库
- 净值曲线（`/nav`）、比例演化（`/ratio`）
- AI 顾问（`/ai`）：对话 + RAG 检索
- 运行形态：单进程（后端托管前端产物，8080 单端口，[决策 36](../phase-0/decisions.md)）

**未开始（原样保留）**：

- 资产配置（`/config`）、月度校正（`/correction`）、季度操作（`/quarterly`）
- Phase 2 数据管理补全项：历史快照列表/分页/日期筛选 UI、解析日志、10 秒撤销、单条忽略、逐文件状态等
- 日收益明细、Dietz/XIRR 升级
- LQR、多用户、移动端

---

## 4. 口径约定（解释成本的下限版本）

> FinControl 按**业务能力里程碑**划分 Phase；实际开发中，净值曲线、比例演化与 AI 顾问界面在 Phase 2 尚未启动时**提前交付**。
>
> 页面归属的 Phase = 该能力的**设计归属**；「提前交付」标注说明其实际交付时间早于所属 Phase。
>
> 当前（2026-09-30）实际边界一句话：**Phase 0 / 1 完成；Phase 3 可视化与 Phase 5 AI 前端已提前部分交付；Phase 2 主体、Phase 4 与 Phase 5 其余项未开始。**

---

## 5. 变更记录（本文件自身的追加表）

| 日期 | 变更 | 触发 |
|---|---|---|
| 2026-09-30 | 建档：追认「Phase 3 净值/比例提前交付」「Phase 5 AI 顾问 UI 提前交付」「Phase 2 局部补强」三项偏差，重定当前边界 | 文档体系整理（决策 36 同期）|

---

## 6. 关联文档

- [决策 6 / 决策 34 / 决策 36](../phase-0/decisions.md) — Phase 划分、重排与运行形态
- [acceptance-criteria.md](./acceptance-criteria.md) — Phase 1 验收标准（历史基线，净值/比例状态已标注）
- [subphase-plan.md](./subphase-plan.md) — Phase 1 子阶段计划（历史基线）
- 关联 commit：`987b1c1`、`9d89397`、`dd89197`