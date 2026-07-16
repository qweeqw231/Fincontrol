# Phase 1a.4 快照查询 + 首页辅助 API 工作计划

**计划日期**：2026-07-16
**子阶段**：整体 Phase 1a.4  〔不是 1a.2 中的 checklist 1a.4 上传接口〕
**负责人**：刘博丞
**状态**：`IN_PROGRESS` 〔Slice A 实现并业务层验证；Slice B/C 待启动〕
**配套验收计划**：[`2026-07-16_phase1a4-acceptance-plan.md`](../../test-records/manual-tests/2026-07-16_phase1a4-acceptance-plan.md)
**总进度清单**：[`phase-1a.md`](../checklists/phase-1a.md)
**API 契约**：[`api-contract.md §3 / §9`](../../phase-0/api-contract.md)
**前置报告**：[`1a.3 补充验收报告`](../../test-records/manual-tests/2026-07-16_phase1a3-supplemental-acceptance.md)

> 本计划先冻结工作范围和验收边界，再开始编码。生产 MySQL upsert 与完整前后端冒烟按当前用户计划留到前端完成后统一验证，不在本轮偷偷改变验收目标。

## Gate 0 冻结结果（2026-07-16）

- latest/历史/余额的字段、筛选条件和分页默认值已按 `api-contract.md` 冻结；
- latest/detail 的 `profit` 唯一来自 `asset_raw`，分类汇总来自 `asset_snapshot`；
- `operations/recent` 冻结为 Phase 1 的**解析活动**：读取当前 user 的 `chat_history` assistant 消息，`operationType=screenshot_parse`，summary 使用“解析 N 只基金/截图解析失败”，不宣称“已入库”；
- `operation_log` 虽已存在于 MySQL schema，但没有 Phase 1 写入链，留到 Phase 2/统一生产验收；
- 所有查询必须带 user 过滤；解析日志查询的 `ChatHistoryMapper` 已补齐 `user_id` 条件和 `X-User-Id` 默认入口；
- 测试层级冻结为 Service mock、Controller MockMvc、必要的只读 H2；MySQL 和前端 E2E 保持 `PRODUCTION_PENDING`。

Gate 0 结论：`PASS`。可以开始 Slice A；本阶段验收计划仍为 `NOT_RUN`，因为业务编码尚未开始。

---

## 1. 目标

实现 Phase 1 后端的只读面，使前端能够完成：

```text
读取最新快照 → 展示六大类/余额/基金明细 → 查询历史日期 → 展示最近操作
```

本阶段是读路径，不负责修复 1a.3 已登记的真实 MySQL upsert 方言问题；但所有 Mapper SQL 和生产待办必须保留清晰记录。

---

## 2. 范围

### 2.1 必须实现的 7 个端点

| API | 用途 |
|---|---|
| `GET /api/snapshot/latest` | 最新快照分类汇总 |
| `GET /api/snapshot/latest/detail` | 最新快照分类 + 每只基金明细，包含 `profit` |
| `GET /api/snapshot/{date}` | 指定日期快照 |
| `GET /api/snapshot/history` | 历史快照日期列表，按日期倒序 |
| `GET /api/asset/balance` | `余额类` 且 `is_latest=true` 的余额汇总 |
| `GET /api/asset/operations/recent` | 最近解析活动（Phase 1 来自 chat_history，不宣称已入库） |
| `GET /api/asset/cumulative-return` | Phase 1 返回 `{ available: false }`，不报 500 |

### 2.2 本阶段非目标

- 不实现真实 MySQL upsert 方言切换；该问题登记在 1a.3 补充报告，留到统一生产验收 gate；
- 不实现前端页面和浏览器端到端；
- 不实现累计收益率计算；
- 不把 H2/mock 测试结果表述成生产数据库通过；
- 不扩展 1a.5 大类映射 API 或 1a.6 AI 顾问 API。

---

## 3. 当前基线

### 3.1 已有基础

- `AssetSnapshotMapper` 已有 `selectLatestByUserAndDate`、`selectLatestBeforeDate`；
- `AssetRawMapper` 已有 category 金额汇总、fund_name 集合等查询；
- `AssetSnapshot` 和 `AssetRaw` 已包含查询需要的日期、分类、金额、收益字段；
- 1a.3 业务层 `SnapShotConfirmServiceIT` 已 6/6 PASS。

### 3.2 预计需要新增/整理

- snapshot query DTO / response view model；
- query Service；
- query Controller；
- 必要的 Mapper SELECT 方法；
- Mapper mock 的 Service 测试；
- Controller contract/MockMvc 测试；
- operations/recent 复用 `ParseLogQueryService` 的 chat_history 派生逻辑；在 Phase 1 只表示解析活动，不能写成“已入库”；
- `ChatHistoryMapper` 查询已补齐 `user_id` 条件，Slice C 仍需通过 A4-S09 验证用户隔离。

---

## 4. 垂直切片

### Slice A：latest + latest/detail（优先）

**目标**：打通第一条完整读链：Mapper → QueryService → Controller → 测试。

任务：

1. 冻结 latest 和 latest/detail 响应字段；
2. 查询最新日期和分类 snapshot；
3. detail 通过 raw 数据组装 fund 明细和 `profit`；
4. 处理无数据、用户隔离和空明细；
5. 完成 `A4-S01`–`A4-S03`。

完成门槛：只读链可独立编译，Service + Controller 测试通过，响应字段与契约一致。

### Slice B：date + history

任务：

1. 实现指定日期过滤；
2. 实现历史日期去重、倒序和分页结构；
3. 复用 Slice A 的响应模型，避免两套字段命名；
4. 完成 `A4-S04`–`A4-S05`。

完成门槛：空日期、单日期、多日期、分页边界均有固定 fixture。

### Slice C：balance + operations + cumulative-return

任务：

1. balance 只统计 `category='余额类' AND is_latest=true`；
2. 复用 `ParseLogQueryService` 生成 operations/recent 的解析活动摘要，排序按 `created_at DESC, id DESC`，默认 limit=5；
3. cumulative-return 返回固定的 Phase 1 占位结构；
4. 完成 `A4-S06`–`A4-S09`。

完成门槛：字段来源和空数据行为写入验收计划，不能以“暂时返回空”代替未决策的数据来源。

---

## 5. 测试和替身策略（冻结）

| 层级 | 允许的依赖 | 目标 | 是否作为生产验收 |
|---|---|---|---|
| Service 单测 | Mapper mock | 聚合、排序、字段映射、边界 | 否 |
| Controller 测试 | MockMvc + Service mock | URL、参数、ApiResponse、HTTP 状态 | 否 |
| 只读 H2 辅助测试 | H2 SELECT | 验证简单读 SQL 和 schema 映射 | 否 |
| MySQL 真库 | MySQL 8 | 真实 SQL、索引、事务、数据状态 | 延后统一验收 |
| 前后端冒烟 | 后端 + 前端 + MySQL | 上传→解析→确认→查询闭环 | 延后统一验收 |

禁止在测试失败后无记录地把 real Mapper 改成 mock，或把测试名称从 integration 改成 unit 来“消除失败”。若策略变化，先更新配套验收计划并写明原因。

---

## 6. 开发顺序与检查点

1. **Gate 0：契约冻结**：完成验收计划中的 A4-S01–S09 字段、输入、预期和替身定义；
2. **Gate 1：Slice A**：只做 latest + detail，编译和定向测试通过后再进入 Slice B；
3. **Gate 2：Slice B**：date + history，复用已通过的响应模型；
4. **Gate 3：Slice C**：balance + operations + cumulative-return；
5. **Gate 4：文档同步**：更新验收计划的实际结果、checklist 和 subphase 状态；
6. **Gate 5：提交**：每个切片单独 commit，commit message 必须包含 `1a.4`。

### 每次失败的处理规则

- 先分类：编译、Bean、Controller 契约、Service 聚合、Mapper SQL、fixture；
- 同类错误连续两次没有进展，暂停改代码，建立最小复现；
- 不同时修改多个层级；
- 每次修复后记录“根因、改动、验证命令、结果”；
- 测试名称、测试目标、测试替身不允许在未更新验收计划的情况下改变。

---

## 7. 风险与暂停条件

| 风险 | 处理 |
|---|---|
| latest/detail 字段来源不清 | 先以 `api-contract.md` 冻结，profit 必须追溯到 raw |
| operations/recent 没有清晰表来源 | Gate 0 已冻结 Phase 1 使用 chat_history；operation_log 留待后续 |
| H2/MySQL SELECT 差异 | 先用标准 SELECT，必要时做最小 SQL 复现 |
| 1a.3 upsert 方言再次干扰 | 记录为外部 pending，不把它混入 1a.4 读路径 |
| 空数据导致 NPE | A4-S01 固定为第一批测试 |
| DTO 字段漂移 | 所有字段先写验收计划，Controller 测试锁定 JSON |

暂停条件：若发现 API 契约本身矛盾、operations 数据来源不存在、或测试层级需要改变，先更新计划并停在当前 Gate，不直接扩大范围。

---

## 8. 计划完成标准

- 7 个端点均有明确 Controller/Service/Mapper 或占位实现；
- A4-S01–S09 均有测试结果和证据路径；
- 业务层测试通过，且没有未记录的测试替身变化；
- `docs/phase-1/checklists/phase-1a.md` 更新为准确状态；
- 真实 MySQL 和前后端端到端若未执行，明确标记 `PRODUCTION_PENDING`；
- 形成 1a.4 验收报告后，才能进入下一阶段或变更范围。
