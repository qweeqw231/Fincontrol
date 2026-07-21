# Phase 1a.5 大类映射 API 验收计划

**计划日期**：2026-07-17
**子阶段**：整体 Phase 1a.5 〔大类映射 API〕
**状态**：`GATE0_PASS` 〔业务编码尚未开始〕
**配套工作计划**：[`2026-07-17_phase1a5-work-plan.md`](../../phase-1/work-plans/2026-07-17_phase1a5-work-plan.md)
**API 契约**：[`api-contract.md §7.1 / §7.2`](../../phase-0/api-contract.md)
**总账**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)

> 这是编码前冻结的验收计划。测试 ID 与 5 段式判定先定义，开发过程不允许为通过测试而无记录地改变测试替身。完成测试后追加实际结果与遗留问题。

> **Gate 0 决策**：`match` 上限 50、复用 `fund_category_map` 表 + UNIQUE KEY、P0-1.3 UPDATE 规则、Service mock + Controller MockMvc、不做真实 SQL 冒烟（留 1a.7）。

---

## 1. 验收边界

### 本轮必须验证

- `GET /api/category-map/match` 的 URL、参数、响应包装、userId 隔离；
- `POST /api/category-map/update` 的 body、URL、响应包装、2001 等异常码；
- P0-1.3 UPDATE 规则：已存在则 UPDATE（id 不变、source='user_correct'），不存在则 INSERT（source='user_manual'）；
- source 字段污染检查；
- 上限 50 边界；
- userId 隔离；
- 空数据、空 funds 列表。

### 本轮不验证

- MySQL 8.0.46 真库 upsert 行为（留 1a.7）；
- 前端 E2E（留前端 1b）；
- 累计收益率；
- 1a.6 AI 顾问 prompt。

---

## 2. 测试层级与替身锁定

| 层级 | 替身 | 目标 |
|---|---|---|
| Service | Mapper mock | match 查找 / update P0-1.3 规则 / source 标志 |
| Controller MockMvc | Service mock | URL / 参数 / ApiResponse / 2001 等异常码 |
| H2 简单路径 | 真表 | 仅 verify Mapper 调用 + 基础 CRUD |
| MySQL | 暂不 | 留 1a.7 |

如果某个测试从 mock 改成 H2（或反向），必须在"变更记录"中写明原因，并同步修改测试名称与结论标签。

---

## 3. 固定测试用例

| ID | 用例 | 层级 | Fixture/替身 | 预期结果 |
|---|---|---|---|---|
| A5-S01 | `match` 全部命中 | Service + Controller | Mapper 返回所有映射 | 返回 `{matchedFunds: [...], unmatchedFunds: []}` |
| A5-S02 | `match` 部分未命中 | Service + Controller | Mapper 返回部分映射，部分 fund 不存在 | 未命中 fund 列入 `unmatchedFunds` |
| A5-S03 | `match` 上限 50 | Service + Controller | `funds` 含 51 个 | 抛 `BusinessException(1002, ...)` 或返回 400 |
| A5-S04 | `update` 已存在 → UPDATE | Service + Controller | 同 (user_id, fund_name) 已存在 | id 不变，`source='user_correct'`，`category` 更新 |
| A5-S05 | `update` 不存在 → INSERT | Service + Controller | 同 (user_id, fund_name) 不存在 | 新增 row，`source='user_manual'` |
| A5-S06 | `update` userId 隔离 | Service + Controller | userId=1 与 userId=2 各自写同 fund | 两 user 各自独立；userId=1 的 update 不影响 userId=2 |

> A5-S03 上限 50 与 `api-contract.md §7.1` 一致；超限响应（400 vs 业务异常码 1002）由 Service 决定，Controller 仅透传。

---

## 4. 每个测试用例的证据格式

开发完成后，每个 ID 必须补充以下字段：

```markdown
### A5-S01
- 测试方法：`...`
- 测试命令：`...`
- 测试替身：`mapper mock` / `service mock` + 关键 stub
- 预期结果：...
- 实际结果：✅ / ❌
- 证据：测试报告路径或日志片段
- 根因/修复：若失败，记录首次根因和最终修复
```

推荐 API 输出路径：

```
docs/test-records/api-test-output/2026-07-17_phase1a5_<case-id>.json
```

如果尚未执行手动 HTTP 测试，不能填写虚构的 JSON 证据，只能写 NOT_RUN。

## 5. 验收判定规则

### 业务层通过

满足以下条件时，状态可标记为 BUSINESS_PASS：

- A5-S01–A5-S06 的 Service 单测有实际结果；
- 测试替身与本计划一致，或变更已记录；
- 无未记录的 NPE、500、字段漂移；
- checklist 和工作计划状态同步。

### 完整 1a.5 通过

只有在计划范围内的真实 SQL 读路径证据也完成后，才标记 PASS。如果按当前项目策略暂时跳过 MySQL 和前端，则必须写成：

```
1a.5 business/contract acceptance: PASS
1a.5 production/frontend acceptance: PENDING
```

### 阻塞判定

以下情况标记 BLOCKED，不得连续猜测修复：

- API 契约和实现字段矛盾；
- 1a.3 confirm 内部写入与 update 端点冲突未协调；
- 同类错误连续两次没有变化；
- 测试层级或测试替身需要改变但尚未更新本计划。

---

## 6. 实际结果（待执行）

| ID | 实际结果 | 证据 | 状态 |
|---|---|---|---|
| A5-S01 | NOT_RUN | — | PLANNED |
| A5-S02 | NOT_RUN | — | PLANNED |
| A5-S03 | NOT_RUN | — | PLANNED |
| A5-S04 | NOT_RUN | — | PLANNED |
| A5-S05 | NOT_RUN | — | PLANNED |
| A5-S06 | NOT_RUN | — | PLANNED |

**当前验收结论**：NOT_RUN 〔业务编码尚未开始〕

---

## 7. 关联文档与提交

| 文档 / Commit | 路径 / 哈希 | 说明 |
|---|---|---|
| 1a.5 工作计划 | `docs/phase-1/work-plans/2026-07-17_phase1a5-work-plan.md` | Gate 0 冻结与切片 |
| 1a.5 验收计划 | `docs/test-records/manual-tests/2026-07-17_phase1a5-acceptance-plan.md` | 本文件 |
| 1a.4 前置 | `docs/test-records/manual-tests/2026-07-17_phase1a4-acceptance-report.md` | 49/49 测试通过 |

---

## 8. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-17 | 创建 A5-S01–A5-S06 验收计划，固定测试层级与替身边界 | 吸取 1a.3/1a.4 测试替身/真实路径漂移教训 |
| 2026-07-17 | Gate 0 冻结 match 上限 50 + 复用 fund_category_map + P0-1.3 UPDATE 规则 | 与 1a.3 confirm 共用同一 update 路径，避免冲突 |
