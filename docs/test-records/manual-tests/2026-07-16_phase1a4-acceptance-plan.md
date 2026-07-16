# Phase 1a.4 快照查询 + 首页辅助 API 验收计划

**计划日期**：2026-07-16
**子阶段**：整体 Phase 1a.4  〔快照查询 + 首页辅助 API〕
**状态**：`NOT_RUN`
**配套工作计划**：[`2026-07-16_phase1a4-work-plan.md`](../../phase-1/work-plans/2026-07-16_phase1a4-work-plan.md)
**API 契约**：[`api-contract.md §3 / §9`](../../phase-0/api-contract.md)
**总账**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)

> 这是编码前冻结的验收计划。测试 ID、测试目标、fixture 和测试替身先定义，开发过程中不得为了让测试通过而无记录地改变它们。完成测试后，在本文档追加“实际结果”和“遗留问题”，或创建同编号的 acceptance-report 并保留本文件。

---

## 1. 验收边界

### 本轮必须验证

- 7 个查询 API 的 URL、参数、响应包装和错误行为；
- latest/detail 的分类汇总和基金 `profit` 字段；
- 指定日期与历史排序；
- 余额类过滤规则；
- operations/recent 的字段、来源、排序和数量限制；
- cumulative-return 的 Phase 1 占位结构；
- 空数据、用户隔离和基础参数边界。

### 本轮不验证

- MySQL 8 真实 upsert 方言和真实事务提交/回滚；
- 前端浏览器行为；
- MiniMax API；
- 累计收益率算法；
- 1a.5 category-map update 和 1a.6 AI 顾问 API。

这些项目必须在后续统一验收中单独列为 `PRODUCTION_PENDING` 或其他明确状态，不能被本文件的业务层 PASS 覆盖。

---

## 2. 测试层级与替身锁定

| 层级 | 测试范围 | 固定策略 | 通过含义 |
|---|---|---|---|
| Service | 聚合、排序、字段映射、空数据 | Mapper mock，fixture 由本计划定义 | `BUSINESS_PASS` |
| Controller | URL、请求参数、ApiResponse、HTTP status | MockMvc，Service mock | `CONTRACT_PASS` |
| Read SQL | 必要的标准 SELECT | H2 只读辅助或 mapper 测试 | `READ_SQL_PASS`，不代表生产 |
| MySQL | 真实表、索引、SQL、数据状态 | 延后统一执行 | `PRODUCTION_PENDING` |
| Browser E2E | 上传→解析→确认→查询 | 前端 + 后端 + MySQL | 延后统一执行 |

如果某个测试从 mock 改成 H2，或从 H2 改回 mock，必须在“变更记录”中写明原因，并同步修改测试名称和结论标签。

---

## 3. 固定测试用例

| ID | 测试场景 | 层级 | Fixture/替身 | 预期结果 |
|---|---|---|---|---|
| A4-S01 | 没有快照 | Service + Controller | Mapper 返回空集合/空 Optional | 返回契约规定的空数据结构或业务错误；不出现 NPE/500 |
| A4-S02 | 最新快照汇总 | Service | 多分类 snapshot fixture | 日期、分类总额、比例、分类数量和总资产字段正确 |
| A4-S03 | 最新快照详情 | Service + Controller | raw 含多个 fund、amount、profit | 每只基金明细出现，`profit` 与 raw 一致，不从 snapshot 猜收益 |
| A4-S04 | 指定日期快照 | Service + Controller | 两个日期、不同用户 fixture | 只返回指定 user/date 的数据，不能串用户或串日期 |
| A4-S05 | 历史快照列表 | Service + Controller | 多个日期、重复分类 | 日期去重、按日期倒序，分页字段符合契约 |
| A4-S06 | 余额查询 | Service + Controller | 余额类/非余额类 + latest true/false | 只累加 `category='余额类' AND is_latest=true` |
| A4-S07 | 最近操作 | Service + Controller | operation_log 或已确认数据 fixture | 数据来源明确，按约定时间倒序，limit/pageSize 生效，空数据稳定 |
| A4-S08 | 累计收益率占位 | Controller | Service/配置 mock | 返回 `{ available: false }`，HTTP 成功，不执行未实现算法 |
| A4-S09 | 参数和用户隔离 | Controller | 缺 user、非法 date、page/pageSize 边界 | 使用统一 ApiResponse 和错误码；越权/跨 user 数据不可见 |

> `A4-S07` 在实现前必须先确认 `operation_log` 的实际 schema 和项目约定；在数据来源未确定前，不允许用任意表临时拼接并宣称通过。

---

## 4. 每个测试用例的证据格式

开发完成后，每个 ID 必须补充以下字段：

```markdown
### A4-S01
- 测试方法：`...`
- 测试命令：`...`
- 测试替身：`real/mock/H2/MySQL` + 具体返回值
- 预期结果：...
- 实际结果：✅ / ❌
- 证据：测试报告路径、HTTP JSON 路径或日志片段
- 根因/修复：若失败，记录首次根因和最终修复
```

推荐 API 输出路径：

```text
docs/test-records/api-test-output/2026-07-16_phase1a4_<case-id>.json
```

如果尚未执行手动 HTTP 测试，不能填写虚构的 JSON 证据，只能写 `NOT_RUN`。

---

## 5. 验收判定规则

### 业务层通过

满足以下条件时，状态可标记为 `BUSINESS_PASS`：

- A4-S01–A4-S09 的 Service/Controller 约定层测试有实际结果；
- 测试替身与本计划一致，或变更已记录；
- 无未记录的 NPE、500、字段漂移；
- checklist 和工作计划状态同步。

### 完整 1a.4 通过

只有在计划范围内的真实 SQL/读路径证据也完成后，才标记 `PASS`。如果按当前项目策略暂时跳过 MySQL 和前端，则必须写成：

```text
1a.4 business/contract acceptance: PASS
1a.4 production/frontend acceptance: PENDING
```

### 阻塞判定

以下情况标记 `BLOCKED`，不得连续猜测修复：

- API 契约和实现字段矛盾；
- operations/recent 数据来源无法确定；
- 同类错误连续两次没有变化；
- 测试层级或测试替身需要改变但尚未更新本计划。

---

## 6. 实际结果（待执行）

| ID | 实际结果 | 证据 | 状态 |
|---|---|---|---|
| A4-S01 | `NOT_RUN` | — | `PLANNED` |
| A4-S02 | `NOT_RUN` | — | `PLANNED` |
| A4-S03 | `NOT_RUN` | — | `PLANNED` |
| A4-S04 | `NOT_RUN` | — | `PLANNED` |
| A4-S05 | `NOT_RUN` | — | `PLANNED` |
| A4-S06 | `NOT_RUN` | — | `PLANNED` |
| A4-S07 | `NOT_RUN` | — | `PLANNED` |
| A4-S08 | `NOT_RUN` | — | `PLANNED` |
| A4-S09 | `NOT_RUN` | — | `PLANNED` |

**当前验收结论**：`NOT_RUN`  〔编码尚未开始〕

---

## 7. 遗留问题（初始登记）

1. 1a.3 真实 MySQL upsert 方言未验收，按计划不阻塞 1a.4；
2. `prompt_versions` 在 H2 测试 schema 中缺失的 warning 未处理；
3. 测试日期 fixture 应避免长期写死；
4. Phase 1a.4 完成后，需把业务层结果与生产/前端结果分层记录。

---

## 8. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-16 | 创建 A4-S01–A4-S09 验收计划，固定测试层级和替身边界 | 吸取 1a.3 测试目标漂移教训 |
