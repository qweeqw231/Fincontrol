# Phase 1a.6 AI 顾问 API 验收计划

**计划日期**：2026-07-17
**子阶段**：整体 Phase 1a.6 〔AI 顾问 API〕
**状态**：`GATE0_PASS` 〔业务编码尚未开始〕
**配套工作计划**：[`2026-07-17_phase1a6-work-plan.md`](../../phase-1/work-plans/2026-07-17_phase1a6-work-plan.md)
**API 契约**：[`api-contract.md §8`](../../phase-0/api-contract.md)
**总账**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)

> 这是编码前冻结的验收计划。测试 ID 与 5 段式判定先定义，开发过程不允许为通过测试而无记录地改变测试替身。完成测试后追加实际结果与遗留问题。

> **Gate 0 决策**：TextAiClient 与 VisionModelClient 拆为独立 Bean；IntentClassifier 独立 Bean（temperature=0.1）；few-shot 7 正 7 反；/conversations POST 仅返回 conversationId 不插 chat_history；list 基础字段；conversationId 不存在复用 2001；Service mock + Controller MockMvc；不重种 prompt_versions；不新增 ErrorCode。

---

## 1. 验收边界

### 本轮必须验证

- /chat/send 投资决策类 → main_loop（带 ai_assistant system prompt），`routedTo='main_loop'`，`promptVersion='ai_assistant v1.0'`
- /chat/send 非投资决策类 → garbage_loop（无 system prompt），`routedTo='garbage_loop'`，**无** `promptVersion`
- 意图分类响应严格解析 `true`/`false`；其他文本 → 默认 false（防御）
- chat_history 写入 user + assistant 两条；role/content/created_at 字段对齐
- /conversations 列表按 user_id 隔离，支持 type 过滤与分页
- /conversations/{id} 详情 messages 按 created_at asc
- /conversations POST 创建 → 返回 conversationId，不插 chat_history
- /conversations/{id} DELETE → 物理删除 chat_history 记录，返回 deletedMessageCount
- TextAiClient 与 VisionModelClient 独立 Bean（不同 @Service 名 / 不同配置前缀）
- 修改 text 端 temperature 不影响 vision 端（配置隔离）

### 本轮不验证

- MySQL 8.0.46 真库 prompt_versions / chat_history upsert（留 1a.7）
- minimax text-only 真实响应延迟与准确性（留 1a.7 冒烟）
- 前端 E2E（留 1b.4）
- conversation list 的 fundCount/snapshotDate/status 派生字段（Phase 1 留 P2）
- 累计收益率

## 2. 测试层级与替身锁定

| 层级 | 替身 | 目标 |
|---|---|---|
| TextAiClient 单测 | OkHttp + ObjectMapper stub | HTTP 调用 + 响应抽取 + 异常码映射 |
| IntentClassifier 单测 | TextAiClient mock | 路由分类 + few-shot 响应解析 |
| ChatService 单测 | TextAiClient + IntentClassifier + ChatHistoryMapper 三 mock | main_loop/garbage_loop 选择 + chat_history 写入 |
| ConversationService 单测 | ChatHistoryMapper mock | list/get/create/delete 逻辑 |
| Controller MockMvc | Service mock + 全 Mapper MockBean | URL/参数/body/header/异常码 |
| 真实 minimax text-only | 暂不 | 留 1a.7 冒烟 |

如果某个测试从 mock 改成真表（或反向），必须在"变更记录"中写明原因。

## 3. 固定测试用例

### 3.1 AI 层（A6-INT）

| ID | 用例 | 层级 | Fixture/替身 | 预期结果 |
|---|---|---|---|---|
| **A6-INT-01** | TextAiClient ≠ VisionModelClient Bean | 单元 + 集成 | Spring 上下文 + AiProperties | 两个 Bean 是不同实例；`fincontrol.ai.text.*` 修改不影响 `fincontrol.vision.*` |
| **A6-INT-02** | IntentClassifier 独立 Bean | 单元 | Spring 上下文 | `IntentClassifier` 注入同一 TextAiClient，但 temperature 配置独立（0.1 vs TextAiClient 0.7） |
| **A6-INT-03** | IntentClassifier few-shot 解析 | 单元 | TextAiClient mock 返 `true`/`false`/`其他` | `true`→true；`false`→false；其他→false（防御）+ log warn |

### 3.2 ChatService 层（A6-S）

| ID | 用例 | 层级 | Fixture/替身 | 预期结果 |
|---|---|---|---|---|
| **A6-S01** | /chat/send 投资决策类 → main_loop | Service | IntentClassifier=true + TextAiClient mock | `routedTo='main_loop'`，`promptVersion='ai_assistant v1.0'`，assistant 消息含 AI 返回内容 |
| **A6-S02** | /chat/send 非投资决策类 → garbage_loop | Service | IntentClassifier=false + TextAiClient mock | `routedTo='garbage_loop'`，**无** `promptVersion`，systemPrompt=null 传入 TextAiClient |
| **A6-S03** | /chat/send conversationId 不存在 → 自动创建 | Service | ChatHistoryMapper 返空 | 自动生成 UUID，先写 user 消息，再走路由 |
| **A6-S04** | /chat/send 写 chat_history | Service | ChatHistoryMapper mock | user + assistant 两条 insert；conversationType='ai_assistant'；created_at 落库 |

### 3.3 ConversationService 层（A6-S）

| ID | 用例 | 层级 | Fixture/替身 | 预期结果 |
|---|---|---|---|---|
| **A6-S05** | /conversations list 按 user_id 隔离 + type 过滤 + 分页 | Service | ChatHistoryMapper mock | items 数组 + total 数字；type='screenshot_parse' 不混入 ai_assistant |
| **A6-S06** | /conversations/{id} 详情 | Service | ChatHistoryMapper mock | messages 按 created_at asc；type 从首条消息推断 |
| **A6-S07** | /conversations/{id} DELETE 物理删除 | Service | ChatHistoryMapper mock | deleteByConversationId 调用；返回 deletedMessageCount |

### 3.4 Controller MockMvc 层（A6-C）

| ID | 用例 | 层级 | Fixture/替身 | 预期结果 |
|---|---|---|---|---|
| **A6-C01** | /chat/send URL/参数/header/异常码 | Controller MockMvc | ChatService mock | 200/code 0；conversationId 不存在返 2001（HTTP 404） |
| **A6-C02** | /conversations list URL 分页/异常 | Controller MockMvc | ConversationService mock | 200/code 0；page<1 → 5001 |
| **A6-C03** | /conversations create 空对话 | Controller MockMvc | ConversationService mock | 200/code 0；conversationId 为 UUID |
| **A6-C04** | /conversations/{id} DELETE | Controller MockMvc | ConversationService mock | 200/code 0；返回 deletedMessageCount |

> 完整用例数 = **14 项**（INT 3 + S 7 + C 4）。

## 4. 每个测试用例的证据格式

开发完成后，每个 ID 必须补充以下字段：

```markdown
### A6-S01
- 测试方法：`...`
- 测试命令：`...`
- 测试替身：`...`
- 预期结果：...
- 实际结果：✅ / ❌
- 证据：测试报告路径或日志片段
- 根因/修复：若失败，记录首次根因和最终修复
```

推荐 API 输出路径：

```
docs/test-records/api-test-output/2026-07-17_phase1a6_<case-id>.json
```

如果尚未执行手动 HTTP 测试，不能填写虚构的 JSON 证据，只能写 NOT_RUN。

## 5. 验收判定规则

### 业务层通过

满足以下条件时，状态可标记为 BUSINESS_PASS：

- A6-INT-01/02/03 的 AI 层单测有实际结果；
- A6-S01–S07 的 Service 单测有实际结果；
- 测试替身与本计划一致，或变更已记录；
- 无未记录的 NPE、500、字段漂移；
- checklist 和工作计划状态同步。

### 完整 1a.6 通过

只有在计划范围内的真实 SQL 读路径证据也完成后，才标记 PASS。如果按当前项目策略暂时跳过 MySQL 和前端，则必须写成：

```
1a.6 business/contract acceptance: PASS
1a.6 production/frontend acceptance: PENDING
```

### 阻塞判定

以下情况标记 BLOCKED，不得连续猜测修复：

- API 契约和实现字段矛盾；
- 真实 minimax text-only schema 与现有 TextAiClient 实现不一致且 3 次调整未通过；
- 同类错误连续两次没有变化；
- 测试层级或测试替身需要改变但尚未更新本计划。

## 6. 实际结果（待执行）

| ID | 实际结果 | 证据 | 状态 |
|---|---|---|---|
| A6-INT-01 | NOT_RUN | — | PLANNED |
| A6-INT-02 | NOT_RUN | — | PLANNED |
| A6-INT-03 | NOT_RUN | — | PLANNED |
| A6-S01 | NOT_RUN | — | PLANNED |
| A6-S02 | NOT_RUN | — | PLANNED |
| A6-S03 | NOT_RUN | — | PLANNED |
| A6-S04 | NOT_RUN | — | PLANNED |
| A6-S05 | NOT_RUN | — | PLANNED |
| A6-S06 | NOT_RUN | — | PLANNED |
| A6-S07 | NOT_RUN | — | PLANNED |
| A6-C01 | NOT_RUN | — | PLANNED |
| A6-C02 | NOT_RUN | — | PLANNED |
| A6-C03 | NOT_RUN | — | PLANNED |
| A6-C04 | NOT_RUN | — | PLANNED |

**当前验收结论**：NOT_RUN 〔业务编码尚未开始〕

## 7. 关联文档与提交

| 文档 / Commit | 路径 / 哈希 | 说明 |
|---|---|---|
| 1a.6 工作计划 | `docs/phase-1/work-plans/2026-07-17_phase1a6-work-plan.md` | Gate 0 冻结与切片 |
| 1a.6 验收计划 | `docs/test-records/manual-tests/2026-07-17_phase1a6-acceptance-plan.md` | 本文件 |
| 1a.5 前置 | `docs/test-records/manual-tests/2026-07-17_phase1a5-acceptance-report.md` | 72/72 测试通过 |
| ai-client-split 设计 | `docs/phase-1/designs/ai-client-split.md` | v1.0 已落定 |
| API 契约 | `docs/phase-0/api-contract.md §8` | 5 个端点契约 |

## 8. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-17 | 创建 A6-INT-01/02/03 + A6-S01–S07 + A6-C01–C04 验收计划，固定测试层级与替身边界 | 吸取 1a.3/1a.4/1a.5 测试替身/真实路径漂移教训 |
| 2026-07-17 | Gate 0 冻结：TextAiClient 独立 Bean / IntentClassifier 独立 / 7+7 few-shot / POST 仅返 conversationId / list 基础字段 / 复用 2001 / 不重种 prompt_versions / 不新增 ErrorCode | 与 ai-client-split.md + api-contract §8 + 1a.5 既有架构对齐 |