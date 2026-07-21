# Phase 1a.5 大类映射 API 工作计划

**计划日期**：2026-07-17
**子阶段**：整体 Phase 1a.5 〔大类映射 API〕
**负责人**：刘博丞
**状态**：`GATE0_PASS`
**配套验收计划**：[`2026-07-17_phase1a5-acceptance-plan.md`](../../test-records/manual-tests/2026-07-17_phase1a5-acceptance-plan.md)
**总进度清单**：[`phase-1a.md`](../checklists/phase-1a.md)
**API 契约**：[`api-contract.md §7.1 / §7.2`](../../phase-0/api-contract.md)
**前置报告**：[`2026-07-17_phase1a4-acceptance-report.md`](../../test-records/manual-tests/2026-07-17_phase1a4-acceptance-report.md)

> 本计划先冻结工作范围和验收边界，再开始编码。生产 MySQL 与前端 E2E 按用户策略统一留到 1a.7 冒烟阶段。

---

## Gate 0 冻结结果（2026-07-17）

### 关键决策

| 决策 | 结论 |
|---|---|
| `match` 接口 `funds` 上限 | **50 个**（按 `api-contract.md §7.1`） |
| 是否新增表/字段 | **否**，复用现有 `fund_category_map` |
| 是否新增唯一索引/触发器 | **否**，复用 `UNIQUE KEY uk_user_fund (user_id, fund_name)`（1a.3 已建） |
| 真实 SQL 冒烟 | **不做**，留 1a.7 |
| 测试替身 | Service mock + Controller MockMvc |
| `funds` 解析 | 逗号分隔 `String`，去空去重；> 50 抛 `BusinessException(1002, ...)` |
| `update` source 标志 | 已存在 → UPDATE，source 固定 `user_correct`；不存在 → INSERT，source 固定 `user_manual` |
| 1a.3 复用 | 1a.3 confirm 已使用同一条 update 路径；本轮只是单独暴露 REST 端点 |

---

## 1. 目标

按 [`docs/phase-1/subphase-plan.md`](../../phase-1/subphase-plan.md) 1a.5 段：

- **1a.16** `GET /api/category-map/match?funds=...` — 批量查询当前 user 的大类映射
- **1a.17** `POST /api/category-map/update` — **[P0-1.3] UPDATE 规则**：已存在则 `UPDATE fund_category_map SET category=?, source='user_correct', confirmed_at=NOW()`（id 不变）；不存在则 `INSERT ... source='user_manual'`

完成后让前端 1b 大类确认面板能直接调这两个端点，替换 1a.3 confirm 阶段临时写入的逻辑。

## 2. 范围

| 端点 | 用途 |
|---|---|
| `GET /api/category-map/match?funds=...` | 批量查（最多 50），返回 `{matchedFunds: [...], unmatchedFunds: [...]}` |
| `POST /api/category-map/update` body `{fundName, category, userId}` | 按 P0-1.3 规则 UPDATE 或 INSERT |

## 3. 非目标

- 不做真实 MySQL 冒烟（留 1a.7 统一做）；
- 不做前端 E2E（留前端 1b 之后）；
- 不实现 1a.6 AI 顾问 prompt；
- 不实现累计收益率；
- 不新增表/字段/索引/触发器。

## 4. 当前基线

### 4.1 已有基础

- `FundCategoryMap` entity 已有（`user_id, fund_name, category, source, confirmed_at`）；
- `FundCategoryMapMapper extends BaseMapper<FundCategoryMap>` 已有；
- `db-schema.sql` 已创建 `fund_category_map` 表 + `UNIQUE KEY uk_user_fund (user_id, fund_name)`；
- 1a.3 confirm 阶段已经走过同一条 `INSERT ... ON CONFLICT` 风格的更新路径。

### 4.2 预计新增

- `CategoryMapService.match(List<String> funds, Long userId)` — 查映射
- `CategoryMapService.update(Long userId, String fundName, String category)` — 按 P0-1.3 规则 UPDATE/INSERT
- `CategoryMapController` — 暴露 1a.16/1a.17 端点
- DTO：`CategoryMapMatchResponse` / `CategoryMapUpdateRequest`
- `CategoryMapServiceTest` 5–6 用例（Service mock）
- `CategoryMapControllerTest` 5+ 用例（Controller MockMvc）

## 5. 切片切分

| 切片 | 范围 | 测试 ID |
|---|---|---|
| Slice A | match 端点：Service.match + Controller GET match | A5-S01–A5-S03 |
| Slice B | update 端点：Service.update + Controller POST update | A5-S04–A5-S06 |
| Slice C | MockMvc 补齐 + 文档 | — |

## 6. 测试与替身策略（冻结）

| 层级 | 替身 | 目标 |
|---|---|---|
| Service 单测 | Mapper mock | match 查找 / update P0-1.3 规则 / source 标志 |
| Controller MockMvc | Service mock | URL / 参数 / ApiResponse / 2001 等异常码 |
| H2 简单路径 | 真表 | 仅 verify Mapper 调用 + 基础 CRUD |
| MySQL 真库 | 暂不 | 留 1a.7 |

禁止在测试失败后无记录地把 real Mapper 改成 mock 来"消除失败"。

## 7. 风险与停止条件

| 风险 | 处理 |
|---|---|
| `match` 上限 50 与前端批量需求冲突 | 与前端确认 chunk 大小 |
| `update` 与 1a.3 confirm 内部写入冲突 | 1a.3 confirm 不再直接写 fund_category_map，update 端点成为唯一入口 |
| H2 跨方言 upsert | 1a.3 已经验证；本轮复用同一路径 |
| Source 字段污染 | 测试明确 source 断言；UPDATE → 'user_correct'，INSERT → 'user_manual' |

## 8. 计划完成标准

- 2 个端点（1a.16 / 1a.17）实现 + 业务层 + 契约层 PASS；
- A5-S01–A5-S06 全部有证据路径；
- 测试替身未偷换；
- `docs/phase-1/checklists/phase-1a.md` 准确更新；
- MySQL 真库与前端 E2E 仍标 `PRODUCTION_PENDING`，留 1a.7 冒烟；
- 形成 1a.5 验收报告后才能进入下一阶段。

## 9. 关联文档

- 1a.4 前置：`docs/test-records/manual-tests/2026-07-17_phase1a4-acceptance-report.md`
- 1a.5 验收计划：`docs/test-records/manual-tests/2026-07-17_phase1a5-acceptance-plan.md`
- 1a.4 工作计划：`docs/phase-1/work-plans/2026-07-16_phase1a4-work-plan.md`
- API 契约：`docs/phase-0/api-contract.md §7.1 / §7.2`
- 总账：`docs/phase-1/checklists/phase-1a.md`
