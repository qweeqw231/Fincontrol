# Phase 1a.7 冒烟 + 测试覆盖率 验收计划

**计划日期**：2026-07-17
**子阶段**：整体 Phase 1a.7 〔冒烟 + 测试覆盖率〕
**状态**：`GATE0_PASS` 〔端到端冒烟尚未执行〕
**配套工作计划**：[`2026-07-17_phase1a7-work-plan.md`](../../phase-1/work-plans/2026-07-17_phase1a7-work-plan.md)
**API 契约**：[`api-contract.md`](../../phase-0/api-contract.md)
**总账**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)
**关键依赖**：[`docs/SETUP.md`](../../SETUP.md)（MySQL 初始化、API Key）

> 这是编码前冻结的验收计划。**与 1a.2-1a.6 不同**：本轮不重测业务层 mock（已 151/151 PASS 且 0 回归），只做**真实 MySQL + 真实 minimax** 端到端冒烟。

> **Gate 0 决策**：1a.7-PRE 修 dialect bug（`ON CONFLICT DO UPDATE` → `MERGE INTO`）；MySQL Docker 自管；text API key 不入仓（用户写 `application-local.yml`）；4 张真实截图做冒烟 1；5 个合成文本做冒烟 2；替身 = 真实 MySQL + 真实 minimax。

---

## 1. 验收边界

### 本轮必须验证

- **冒烟 1**（A7-S01 ~ A7-S04）：截图上传 → 解析 → 入库 → balance（4 张真实图全过）
- **冒烟 2**（A7-S05 ~ A7-S09）：chat send 5 个文本用例（2 投资 + 2 闲聊 + 1 空）
- **A7-S10**：Swagger UI 端点数 ≥ 24
- **A7-S11**：JaCoCo line 覆盖率 ≥ 60%
- **A7-S12**：真表 SQL 验证（chat_history / asset_raw / fund_category_map / asset_snapshot）

### 本轮不验证

- 业务层单测 151 个（已 PASS，不重跑）
- minimax 模型升级 / prompt 调优
- 前端 E2E
- 真实 AI 回复内容质量（只验证 latencyMs ≤ 阈值 + routedTo + 是否写库）

## 2. 测试层级与替身锁定

| 层级 | 替身 | 目标 |
|---|---|---|
| 端到端冒烟 | **真实 MySQL 8.0 + 真实 minimax vision + minimax text-only** | 替代之前 mock / H2 路径；验证真实 SQL 行为 |
| 覆盖率 | JaCoCo Maven plugin | 全 module line / branch / class 统计 |
| 业务层单测 | **不重跑**（1a.5 / 1a.6 闭环） | 0 回归基线 |

> 禁止失败时把真库切回 mock 来消除失败。失败 → 写错误码进报告 + 写错误进 chat_history。

## 3. 固定测试用例

### 3.1 冒烟 1 — 截图链路（A7-S01 ~ A7-S04）

| ID | 用例 | 替身 | 预期 |
|---|---|---|---|
| **A7-S01** | 4 张真实图全部 `POST /api/screenshot/upload` 返 200 + fileId | 真实 MySQL + 文件落盘 | 4 个 fileId 全部有效（UUID） |
| **A7-S02** | 4 张图全部 `POST /api/screenshot/parse` 返 200 + ParsedAsset | 真实 MySQL + 真实 minimax vision | 4 份 ParsedAsset，6 大类 + 余额类 + fundCount > 0；chat_history 各 2 行（user+assistant） |
| **A7-S03** | 4 张图全部 `POST /api/snapshot/confirm` 返 200，资产落库 | 真实 MySQL upsert（`MERGE INTO` 修后） | `asset_raw` 6+ 行 / `asset_snapshot` 7 行（6 大类 + 余额类） / `fund_category_map` 6+ 行；4 份独立 asset_raw |
| **A7-S04** | `GET /api/asset/balance` 返 200 + 余额类数字 | 真实 SQL `SUM` | balance.amount = asset_raw 中 category='余额类' 之和；与解析输出一致 |

### 3.2 冒烟 2 — chat 多轮（A7-S05 ~ A7-S09）

| ID | 用例 | 输入 | 预期 |
|---|---|---|---|
| **A7-S05** | 投资决策类 → main_loop | `本月应该补仓多少` | `assistantMessage.routedTo='main_loop'` + `promptVersion='ai_assistant v1.0'` |
| **A7-S06** | 投资决策类（同主题） | `海外权益类占比偏高怎么办` | `routedTo='main_loop'` |
| **A7-S07** | 闲聊 → garbage_loop | `今天天气怎么样` | `routedTo='garbage_loop'`，**无** `promptVersion` |
| **A7-S08** | 闲聊（闲聊 + 模型身份询问） | `你是什么模型` | `routedTo='garbage_loop'` |
| **A7-S09** | 空 message | `{"message":""}` | HTTP 400 + code 1001，不写库 |

### 3.3 辅助冒烟（A7-S10 ~ A7-S12）

| ID | 用例 | 验证方式 |
|---|---|---|
| **A7-S10** | Swagger UI 端点数 ≥ 24 | `curl /v3/api-docs \| jq '.paths \| length'` |
| **A7-S11** | JaCoCo line 覆盖率 ≥ 60% | `mvn -B test` 后读 `target/site/jacoco/index.html`（解析 XML report） |
| **A7-S12** | 真表 SQL 验证 | 跑完冒烟 1+2 后：<br>`SELECT COUNT(*) FROM chat_history WHERE conversation_type='ai_assistant'` ≥ 4<br>`SELECT COUNT(*) FROM fund_category_map` ≥ 6<br>`SELECT COUNT(*) FROM asset_raw WHERE user_id=1` ≥ 6 |

> 完整用例数 = **12 项**（4 + 5 + 3）。

## 4. 每个测试用例的证据格式

```markdown
### A7-S01
- 执行时间：2026-07-17 15:30
- 执行命令：`./03-smoke-1-screenshot.sh`
- 替身：真实 MySQL + 真实 minimax
- 预期：4 个 fileId 全部有效
- 实际：✅ / ❌
- 证据：`docs/test-records/api-test-output/2026-07-17_phase1a7_s01_upload.json` 4 份响应
- 根因/修复：若失败，记录首次根因和最终修复
```

> 每次 curl 输出保存到 `docs/test-records/api-test-output/2026-07-17_phase1a7_<case-id>_<step>.json`。

## 5. 验收判定规则

### 业务层通过

业务层 151/151 已 PASS，**1a.7 不再重测**。

### 端到端 PASS

满足以下条件时标记 PASS：
- A7-S01~S04 冒烟 1 4 用例全部 200；
- A7-S05~S08 冒烟 2 4 用例全部符合路由预期；A7-S09 抛 1001；
- A7-S10 Swagger 端点数 ≥ 24；
- A7-S11 JaCoCo line ≥ 60%；
- A7-S12 真表 SQL 关键表数据完整。

### 阻塞判定

以下情况标记 BLOCKED：
- minimax API 限流 / 失败 3 次以上；
- MySQL 容器无法启动且本地 MySQL 不可用；
- 覆盖率 < 50%（补 5+ case 仍达不到 60%）；
- 任何冒烟 1 用例因 SQL 错误（如 `MERGE INTO` 语法失败）block；
- 1a.7-PRE dialect 修复未提交或未验证。

## 6. 实际结果（待执行）

| ID | 实际结果 | 证据 | 状态 |
|---|---|---|---|
| A7-S01 | NOT_RUN | — | PLANNED |
| A7-S02 | NOT_RUN | — | PLANNED |
| A7-S03 | NOT_RUN | — | PLANNED |
| A7-S04 | NOT_RUN | — | PLANNED |
| A7-S05 | NOT_RUN | — | PLANNED |
| A7-S06 | NOT_RUN | — | PLANNED |
| A7-S07 | NOT_RUN | — | PLANNED |
| A7-S08 | NOT_RUN | — | PLANNED |
| A7-S09 | NOT_RUN | — | PLANNED |
| A7-S10 | NOT_RUN | — | PLANNED |
| A7-S11 | NOT_RUN | — | PLANNED |
| A7-S12 | NOT_RUN | — | PLANNED |

**当前验收结论**：NOT_RUN 〔端到端尚未执行〕

## 7. 关联文档与提交

| 文档 / Commit | 路径 / 哈希 | 说明 |
|---|---|---|
| 1a.7 工作计划 | `docs/phase-1/work-plans/2026-07-17_phase1a7-work-plan.md` | Gate 0 冻结与切片 |
| 1a.7 验收计划 | `docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-plan.md` | 本文件 |
| 1a.7 验收报告 | `docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md` | 执行后回填 |
| 1a.7 脚本目录 | `fincontrol-backend/scripts/1a7/*.sh` | 8 个可重复执行脚本 |
| 1a.7-PRE 修复 | `fix(1a.7-pre): upsertByFundName use MERGE INTO` | 1 commit |
| 1a.6 前置 | `docs/test-records/manual-tests/2026-07-17_phase1a6-acceptance-report.md` | 151/151 PASS |

## 8. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-17 | 创建 A7-S01~S12 验收计划，固定端到端替身（真实 MySQL + 真实 minimax） | 吸取 1a.3-1a.6 业务层闭环 + 1a.7 必须真实库验证的经验 |
| 2026-07-17 | Gate 0 冻结：1a.7-PRE dialect 修复 / Docker 自管 MySQL / 4 张真实图 / text API key 不入仓 / 不重测业务层 | 配合 1a.5-1a.6 业务层 PASS，专注端到端真实库验证 |
| 2026-07-17 | 1a.7-PRE 修 `FundCategoryMapMapper.xml` `upsertByFundName`（`ON CONFLICT` → `MERGE INTO`） | 1a.3 残留 dialect bug：H2 + MySQL 均不支持 PostgreSQL `ON CONFLICT DO UPDATE` 语法；改 `MERGE INTO`（SQL 2003）跨方言兼容 |
