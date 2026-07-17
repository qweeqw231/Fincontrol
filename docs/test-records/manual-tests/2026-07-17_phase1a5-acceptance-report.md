# Phase 1a.5 过程性验收报告

**日期**：2026-07-17
**测试者**：刘博丞
**子阶段**：整体 Phase 1a.5 〔大类映射 API〕
**状态**：🟢 **1a.5 business/contract acceptance: CONTRACT_PASS**〔生产层 **PRODUCTION_PENDING**，与 1a.3 / 1a.4 共用同一 gate〕

> 本报告与 1a.3 / 1a.4 报告（[`2026-07-16_phase1a3-supplemental-acceptance.md`](./2026-07-16_phase1a3-supplemental-acceptance.md) / [`2026-07-17_phase1a4-acceptance-report.md`](./2026-07-17_phase1a4-acceptance-report.md)）保持同样的诚实分层口径：business / contract / real-SQL / production / PASS 五段独立判断，不互相顶替。

---

## 1. 分层结论

| 层级 | 状态 | 含义 |
|---|---|---|
| **BUSINESS_PASS** | ✅ | Service 单测覆盖全部 6 个用例 A5-S01–A5-S06 + 4 边界 + 2 parseFundsCsv 白盒 + 1 余额类防御，共 13 PASS |
| **CONTRACT_PASS** | ✅ | Controller MockMvc 10 用例全过：match URL/参数/1002/边界 + update URL/body/1004/userId 隔离/余额类 |
| **READ_SQL_PASS** | 🟡 | match 走批量 `IN (...)` select；XML 与 1a.3 同双方言；真实数据库路径待统一冒烟 |
| **PRODUCTION_PENDING** | 🟡 | MySQL 8.0.46 真库 upsert、批量 select、前端 E2E |
| **PASS** | 🟡 | 等 PRODUCTION_PENDING 转 PASS |

> 5 段式判定定义见 [`2026-07-17_phase1a5-acceptance-plan.md`](./2026-07-17_phase1a5-acceptance-plan.md)。

---

## 2. Slice A/B/C 实施结果

### 2.1 Slice A — `match`（1a.16）

| 项 | 内容 |
|---|---|
| **DTO** | `CategoryMapMatchItem`（fundName/category/source/confirmedAt） + `CategoryMapMatchResponse`（matchedFunds + unmatchedFunds） |
| **Service** | `CategoryMapService.match(userId, fundsCsv)`：CSV 解析 → LinkedHashSet 保序去重 → 上限校验（>50 抛 1002）→ 批量查 mapper → 按输入顺序分桶 matched/unmatched |
| **Mapper** | `FundCategoryMapMapper.selectByUserAndFundNames(userId, fundNames)`（XML 批量 IN 查询，避免 N+1） |
| **Controller** | `GET /api/category-map/match?funds=...`，`X-User-Id` 默认 1 |
| **测试** | A5-S01/S02/S03 PASS + 空 CSV / 去重 trim / parseFundsCsv 白盒 + 余额类防御，共 9 个 Service 用例 |

### 2.2 Slice B — `update`（1a.17 / P0-1.3）

| 项 | 内容 |
|---|---|
| **DTO** | `CategoryMapUpdateRequest`（fundName/category/userId 可选）+ `CategoryMapUpdateResponse`（mappingId/fundName/category/source/confirmedAt/updated） |
| **Service** | `CategoryMapService.update(userId, fundName, category)`：校验（1004 非法大类）→ `selectByUserAndFundName` 判断 UPDATE vs INSERT → `upsertByFundName`（XML `ON CONFLICT DO UPDATE SET source = EXCLUDED.source`）→ 回读拿 mappingId/confirmedAt |
| **源标志** | 已存在行 → `source='user_correct'`；新行 → `source='user_manual'` |
| **Controller** | `POST /api/category-map/update`，userId 取自 body 优先，回退 `X-User-Id` header |
| **测试** | A5-S04/S05/S06 PASS + 1004 校验 + 空 fundName 校验 + 余额类允许写入，共 6 个 Service 用例 |

### 2.3 Slice C — MockMvc 契约测试（10 用例）

| 用例 | 验证点 |
|---|---|
| `match_defaultUserId_returnsData` | 默认 header `X-User-Id=1`，matchedFunds + unmatchedFunds JSON 结构 |
| `match_explicitUserId_passesThrough` | `X-User-Id: 7` 透传到 service |
| `match_noFundsParam_returnsEmptyData` | 不带 `funds` 参数 → matched/unmatched 都为 `[]` |
| `match_tooManyFunds_returns400And1002` | 51 个 fund → HTTP 400 + code 1002 |
| `update_existingMapping_returnsUpdatedTrue` | body 完整 → code 0 + `updated=true` + `source='user_correct'` |
| `update_newMapping_returnsUpdatedFalse` | body 完整 → code 0 + `updated=false` + `source='user_manual'` |
| `update_invalidCategory_returns400And1004` | category='非法类' → HTTP 400 + code 1004 |
| `update_userIsolationHeader` | `X-User-Id: 9` 透传 |
| `update_bodyUserIdOverridesHeader` | body `userId=42` 覆盖 header `X-User-Id=1` |
| `update_balanceCategoryAllowed` | [P0-3.3] category='余额类' 不被 1004 拦截 |

---

## 3. 测试结果总览

| 测试类 | 用例 | 状态 |
|---|---:|:---:|
| `CategoryMapServiceTest` | 13 | ✅ 13/13 |
| `CategoryMapControllerTest` | 10 | ✅ 10/10 |
| `AssetControllerTest` | 7 | ✅ 7/7（回归通过） |
| `AssetQueryServiceTest` | 5 | ✅ 5/5（回归通过） |
| `DedupEngineTest` | 9 | ✅ 9/9（回归通过） |
| `FincontrolApplicationTests` | 1 | ✅ 1/1（回归通过） |
| `ScreenshotServiceTest` | 6 | ✅ 6/6（回归通过） |
| `SnapshotControllerTest` | 11 | ✅ 11/11（回归通过） |
| `SnapshotQueryServiceTest` | 10 | ✅ 10/10（回归通过） |
| **总计** | **72** | **✅ 72/72** |

> **1a.5 新增 23 用例（A5-S01–S06 + 边界 + 白盒 + Controller）**，既有用例无回归。

---

## 4. A5-S01–A5-S06 用例实际结果

| ID | 实际结果 | 替身 | 关键证据 |
|---|---|---|---|
| **A5-S01** | ✅ PASS | Mapper mock（2 行 ai_guess + user_correct） | `match_allHit` — matchedFunds[0].category='货币类' + source='ai_guess'；matchedFunds[1].source='user_correct'；unmatchedFunds=[] |
| **A5-S02** | ✅ PASS | Mapper mock 只命中 1 个 fund | `match_partialMiss` — unmatchedFunds[0]='未知基金'，按输入顺序 |
| **A5-S03** | ✅ PASS | Mapper mock 空集合 | `match_limitBoundary` — 51 个抛 1002；50 个返回 matched=[] + unmatched=50；51 个不调 mapper（1002 拦截在 CSV 解析后立刻抛） |
| **A5-S04** | ✅ PASS | Mapper mock 返回 existing + afterWrite | `update_existing` — `mappingId=50` 不变；`source='user_correct'`；`updated=true`；upsert 入参 captor 验证 source 字段 |
| **A5-S05** | ✅ PASS | Mapper mock 返回 null + newRow | `update_newInsert` — `mappingId=77`；`source='user_manual'`；`updated=false`；upsert 入参 captor 验证 source 字段 |
| **A5-S06** | ✅ PASS | Mapper mock user1 path | `update_userIsolation` — captor 验证 `userId=1L`；selectByUserAndFundName 两次调用都是 userId=1 |

---

## 5. 5 段式判定具体证据

### 5.1 BUSINESS_PASS ✅

- Service 13/13 PASS（含 6 个验收用例 + 4 边界 + 2 白盒 + 1 防御性）
- 测试替身：Mapper mock（与验收计划 §2 锁定一致，未偷换）
- 无 NPE / 500 / 字段漂移

### 5.2 CONTRACT_PASS ✅

- Controller 10/10 PASS
- URL / 参数 / body / header 全部覆盖
- 1002 / 1004 错误码透传正确（HTTP 400）
- userId 隔离：header 缺省、显式、body 覆盖三种路径都覆盖
- 余额类防御：[P0-3.3] 不被 1004 拦截

### 5.3 READ_SQL_PASS 🟡

- match 路径走批量 `IN (...)` select（XML 已实现，H2 + MySQL 8.0.20+ 兼容）
- update 路径走 `ON CONFLICT DO UPDATE` upsert（与 1a.3 confirm 共用 XML，1a.3.5 已双方言验证）
- 真实 MySQL 8.0.46 验证留 1a.7 冒烟阶段统一跑

### 5.4 PRODUCTION_PENDING 🟡

- MySQL 真库 ON CONFLICT upsert 行为
- MySQL 真库批量 select 行为
- 前端 E2E（留前端 1b 阶段）

### 5.5 PASS 🟡

- 待 PRODUCTION_PENDING 转 PASS

---

## 6. 关键设计取舍回顾

| 取舍 | 选择 | 实际效果 |
|---|---|---|
| 1a.3 confirm 是否 refactor 改走 1a.5 update | 不 refactor | 1a.3 维持直接调用 upsertByFundName（始终 source='user_correct'）；1a.5 update 单独维护"新行 user_manual / 已存在 user_correct"分支。两条路径 XML 共用，零行为冲突 |
| match 是 N+1 还是批量 XML | 批量 XML | 新增 5 行 XML + 1 行 mapper 接口；Service 测试一次性 stub List<FundCategoryMap>，可读性 + 性能都好 |
| 是否校验 category ∈ 6 大类 | 校验抛 1004 | ErrorCode 1004 现成定义；早 fail 防脏数据；余额类白名单允许（[P0-3.3]） |
| userId 来源 | header 优先，body fallback | 与既有 `X-User-Id` 约定一致；body 覆盖便于压测 |

---

## 7. 实施顺序与 commit 记录

| # | commit | 改动 |
|---|---|---|
| 1 | `feat(1a.5): add ErrorCode 1002 + category-map DTOs` | ErrorCode + CategoryEnum + 4 个 DTO |
| 2 | `feat(1a.5): category-map service + controller + batch mapper` | Mapper 接口 + XML 批量 select + Service + Controller |
| 3 | `test(1a.5): A5-S01–A5-S06 service + controller tests` | 13 + 10 个测试用例 |
| 4 | `docs(1a.5): checklist + acceptance report + plan update` | checklist 勾选 + 验收报告 + subphase-plan 进度 |

> 实际 git commit 由用户提交时统一执行，本报告只列计划顺序。

---

## 8. 遗留问题与 PENDING 项

| 项 | 状态 | 计划 |
|---|---|---|
| MySQL 8.0.46 真库 upsert / select 验证 | 🟡 PENDING | 1a.7 冒烟阶段统一跑 |
| 前端 E2E（确认面板调 match / update） | 🟡 PENDING | 1b 阶段 |
| Swagger UI 暴露 2 个新端点 | 🟡 待确认 | 1a.7 冒烟前检查 |
| 单元测试覆盖率（要求 ≥ 60%） | 🟡 待统计 | 1a.7 阶段 JaCoCo |

---

## 9. 当前验收结论

```
1a.5 business/contract acceptance: PASS
1a.5 production/frontend acceptance: PENDING
```

> 1a.5 可作为后端 1a 收官前的"已通过业务+契约闭环"清单项。生产层验证仍需 1a.7 冒烟（MySQL 真库 + 前端 E2E）。

---

## 10. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-17 | 创建 A5-S01–A5-S06 验收计划，固定测试层级与替身边界 | 吸取 1a.3/1a.4 测试替身/真实路径漂移教训 |
| 2026-07-17 | Gate 0 冻结 match 上限 50 + 复用 fund_category_map + P0-1.3 UPDATE 规则 | 与 1a.3 confirm 共用同一 update 路径，避免冲突 |
| 2026-07-17 | 完成 1a.5 编码 + 测试 + 文档，13 Service + 10 Controller + 6 验收用例全 PASS | 提交 CONTRACT_PASS；PRODUCTION_PENDING 留 1a.7 |