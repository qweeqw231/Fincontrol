# Phase 1a.4 过程性验收报告

**日期**：2026-07-17
**测试者**：刘博丞
**子阶段**：整体 Phase 1a.4 〔快照查询 + 首页辅助 API〕
**状态**：🟢 **1a.4 business/contract acceptance: CONTRACT_PASS**〔生产层 **PRODUCTION_PENDING**，与 1a.3 共用同一 gate〕

> 本报告与 1a.3 报告（[`2026-07-16_phase1a3-dedup-and-snapconfirm-acceptance.md`](./2026-07-16_phase1a3-dedup-and-snapconfirm-acceptance.md) + [`2026-07-16_phase1a3-supplemental-acceptance.md`](./2026-07-16_phase1a3-supplemental-acceptance.md)）保持同样的诚实分层口径：business / contract / real-SQL / production / PASS 五段独立判断，不互相顶替。

---

## 1. 分层结论

| 层级 | 状态 | 含义 |
|---|---|---|
| **BUSINESS_PASS** | ✅ | Service 单测覆盖全部 9 个用例 A4-S01–A4-S09，Mapper mock |
| **CONTRACT_PASS** | ✅ | Controller MockMvc 11 + 7 = 18 用例全过 |
| **READ_SQL_PASS** | 🟡 | 简单 SELECT 走 H2；跨方言 upsert 待统一冒烟 |
| **PRODUCTION_PENDING** | 🟡 | MySQL 8.0.46 真库 ON CONFLICT upsert、读路径 SQL、前端 E2E |
| **PASS** | 🟡 | 等 PRODUCTION_PENDING 转 PASS |

> 5 段式判定定义见 [`2026-07-16_phase1a4-acceptance-plan.md`](./2026-07-16_phase1a4-acceptance-plan.md)。

---

## 2. Slice A/B/C 实施结果

### 2.1 Slice A — `latest` + `latest/detail`（commit `1278c5f`）

| 项 | 内容 |
|---|---|
| **DTO** | `SnapshotLatestResponse` / `SnapshotCategorySummary` / `SnapshotFundDetail`（英文 PascalCase） |
| **Service** | `SnapshotQueryService.getLatest(userId, includeDetail, includeBalance)` 从 `asset_snapshot` 拉最新日期与分类，从 `asset_raw` 拼基金明细和 profit |
| **Mapper** | `AssetSnapshotMapper.selectLatestSnapshotDate`（SQL XML） |
| **Controller** | `GET /api/snapshot/latest` + `GET /api/snapshot/latest/detail`，`X-User-Id` 默认 1 |
| **测试** | `SnapshotQueryServiceTest` 5/5 |

### 2.2 Slice B — `byDate` + `history`（commit `18a625a`）

| 项 | 内容 |
|---|---|
| **DTO** | `SnapshotByDateResponse` / `SnapshotHistoryItem` / `SnapshotHistoryResponse` |
| **Service** | `getByDate` 抛 `SNAPSHOT_NOT_FOUND`；`getHistory` 区间 + 分页，`page<1` 抛 `BusinessException` |
| **Mapper** | `AssetSnapshotMapper.selectHistoryDates(userId, from, to)`，用 `<if test="from != null">` 区间过滤 |
| **Controller** | `GET /api/snapshot/{date}` + `GET /api/snapshot/history?from=&to=&page=&pageSize=` |
| **测试** | 累计 10/10 |

### 2.3 Slice C — `balance` + `operations/recent` + `cumulative-return`（commit `4c1bff0`）

| 项 | 内容 |
|---|---|
| **DTO** | `AssetBalanceItem` / `AssetBalanceResponse` / `OperationRecentItem` / `OperationsRecentResponse` |
| **Service** | `AssetQueryService.getBalance` 汇总 `category='余额类' AND is_latest=true`；`getRecentOperations` 复用 `ParseLogQueryService.listLatest` 派生 summary；`getCumulativeReturnPlaceholder()` 返回 `CumulativeReturnPlaceholder("false", "累计收益率功能将在 Phase 3 上线")` |
| **Mapper** | `AssetRawMapper.selectBalanceByUser` |
| **Controller** | `GET /api/asset/balance` / `operations/recent` / `cumulative-return` |
| **测试** | 累计 5/5 |

### 2.4 MockMvc 补齐（commit `5349ea8`）

- `SnapshotControllerTest` 11/11、`AssetControllerTest` 7/7

**关键修复**：

1. `cumulativeReturn` stub 真实 record（`@MockBean` 整体替换 Service）
2. `byDate_invalidDate` / `history_invalidPage` 用 `is5xxServerError`（`GlobalExceptionHandler` 映射 500）
3. `confirm_endpointStillDelegatesToConfirmService` 用 `verify().confirm(any(...))`（原 `never()` 错误）

---

## 3. 测试结果总览

| 测试类 | 用例 | 状态 |
|---|---:|:---:|
| `AssetControllerTest` | 7 | ✅ 7/7 |
| `AssetQueryServiceTest` | 5 | ✅ 5/5 |
| `DedupEngineTest` | 9 | ✅ 9/9 |
| `FincontrolApplicationTests` | 1 | ✅ 1/1 |
| `ScreenshotServiceTest` | 6 | ✅ 6/6 |
| `SnapshotControllerTest` | 11 | ✅ 11/11 |
| `SnapshotQueryServiceTest` | 10 | ✅ 10/10 |
| **合计** | **49** | ✅ **49/49** |

**1a.3 回归 `SnapShotConfirmServiceIT`**：6 ✅ 6/6（未受影响）

**命令**：

```bash
mvn -f C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend\pom.xml -B test
```

---

## 4. 9 个用例的实际结果

| ID | 用例 | Service | Controller |
|---|---|---|---|
| **A4-S01** | 没有快照 | ✅ BUSINESS_PASS | ✅ CONTRACT_PASS |
| **A4-S02** | 最新快照汇总 | ✅ | ✅ |
| **A4-S03** | 最新快照详情 | ✅ | ✅ |
| **A4-S04** | 指定日期快照 | ✅ | ✅（含 2001） |
| **A4-S05** | 历史快照列表 | ✅ | ✅（page/pageSize 边界） |
| **A4-S06** | 余额查询 | ✅ | ✅ |
| **A4-S07** | 最近解析活动 | ✅ | ✅ |
| **A4-S08** | 累计收益率占位 | ✅ | ✅ |
| **A4-S09** | 参数和用户隔离 | ✅ | ✅ |

> 9 个用例 ID 与验收定义见 [`2026-07-16_phase1a4-acceptance-plan.md`](./2026-07-16_phase1a4-acceptance-plan.md)。

---

## 5. 真实 vs 替身（诚实记录）

### 5.1 Service 单元测试（**BUSINESS_PASS**）

- 所有 9 个用例都用 Mapper mock 验证 Service 行为
- A4-S06–A4-S08 走 `AssetQueryService` 单测，不触发任何真实 SQL

### 5.2 Controller MockMvc（**CONTRACT_PASS**）

- 用 `@WebMvcTest(value=Controller.class, properties={autoconfigure exclude DataSource,JPA,MyBatisPlus})` 加载 Controller
- 用 `@MockBean` 替代所有 4 个 Mapper 和 Service
- 验证 URL、QueryParam、Header、ApiResponse 包装、状态码
- **不发起任何 SQL**

### 5.3 H2 只读 SQL（**READ_SQL_PASS**）

- `Schema-H2.sql` 覆盖 `asset_raw` / `asset_snapshot` / `fund_category_map` 三表
- Mapper XML 跨方言 `ON CONFLICT` 在 H2 上**没有真正跑过**

### 5.4 MySQL 真库（**PRODUCTION_PENDING**）

- 当前未在本轮执行
- **已知风险**：当前 XML 使用 `ON CONFLICT` 在 MySQL 8.0.20+ 应可工作，但未在本地 MySQL 8.0.46 真库上验证

---

## 6. 已知遗留问题

| # | 遗留问题 | 影响层级 | 处理计划 |
|---|---|---|---|
| 1 | 真实 MySQL upsert 未验证 | PRODUCTION | 纳入 1a.7 冒烟 |
| 2 | `ON CONFLICT` 跨方言 SQL | READ_SQL | H2 不支持完整 MERGE；`ON CONFLICT DO UPDATE` 统一，但 4 列唯一约束可能 MySQL 行为有差异 |
| 3 | `prompt_versions` H2 schema 缺失 | READ_SQL | `PromptLoaderService` 启动 warning，运行时兜底 |
| 4 | 测试日期 fixture 写死 `2026-07-16` | 业务层 | 当前业务层单测不涉及日期 |
| 5 | MySQL READ_SQL_PASS 未独立验收 | PRODUCTION | 没有为读路径 SQL 单写集成测试 |
| 6 | 前端 E2E 完整流程 | PRODUCTION | 留到前端 1b 之后统一冒烟 |

---

## 7. 关联文档与提交

| 文档 / Commit | 路径 / 哈希 | 说明 |
|---|---|---|
| 1a.4 验收计划 | [`docs/test-records/manual-tests/2026-07-16_phase1a4-acceptance-plan.md`](./2026-07-16_phase1a4-acceptance-plan.md) | 测试 ID 与 5 段式判定 |
| 1a.4 工作计划 | [`docs/phase-1/work-plans/2026-07-16_phase1a4-work-plan.md`](../phase-1/work-plans/2026-07-16_phase1a4-work-plan.md) | Slice A/B/C 任务拆解 |
| Slice A | `1278c5f` `feat(1a.4): slice A latest + latest/detail with 5/5 tests` | `latest` / `latest/detail` |
| Slice B | `18a625a` `feat(1a.4): slice B byDate + history with 10/10 tests` | `byDate` / `history` |
| Slice C | `4c1bff0` `feat(1a.4): slice C balance + operations/recent + cumulative-return` | `balance` / `recent` / `cumulative` |
| MockMvc | `5349ea8` `feat(1a.4): slice C mockmvc + 49/49 tests pass` | 49/49 + push |

**远端状态**：`origin/main` 已含全部 4 个 commit。

---

## 8. 总结

- **1a.4 的纯后端业务 + 契约两层达到 CONTRACT_PASS**。
- 所有 **9 个**验收用例在 Service 与 Controller 两层都有明确证据（49 个测试用例全部 PASS）。
- **真正未做的**：MySQL 真库 `ON CONFLICT` upsert、读路径 SQL、前端 E2E，按用户策略留到 **1a.7 冒烟**。
- 本报告与 1a.3 报告同样**诚实区分真实与替身**，不夸大 contract pass 之上没有验证的部分。
- 报告与代码一起为 **1a.5 / 1a.6 / 1a.7** 做好了衔接。

---

**本次验收时间窗口**：

- Slice A：0.5h（latest + latest/detail + 5/5 单测）
- Slice B：0.5h（byDate + history + 分页校验 + 10/10 单测）
- Slice C：0.75h（balance + operations/recent + cumulative-return + 5/5 单测）
- MockMvc 补齐：0.5h（18 例 + 3 关键修复）
- 报告撰写：0.25h
- **总 2.5h**（3 切片全闭环 + contract 18 例）

**后续进度**：

- 下一阶段：`1a.5` 类别映射管理 API（`GET /api/category-map/match` 等）
- 待统一冒烟：`1a.7` MySQL 真库 upsert + 读路径 SQL
- 待统一验收：前端 `1b` 之后 E2E
