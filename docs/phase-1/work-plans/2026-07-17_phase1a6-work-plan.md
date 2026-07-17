# Phase 1a.6 AI 顾问 API 工作计划

**计划日期**：2026-07-17
**子阶段**：整体 Phase 1a.6 〔AI 顾问 API〕
**负责人**：刘博丞
**状态**：`GATE0_PASS`
**配套验收计划**：[`2026-07-17_phase1a6-acceptance-plan.md`](../../test-records/manual-tests/2026-07-17_phase1a6-acceptance-plan.md)
**总进度清单**：[`phase-1a.md`](../checklists/phase-1a.md)
**API 契约**：[`api-contract.md §8`](../../phase-0/api-contract.md)（§8.1 send / §8.2 list / §8.3 get / §8.4 create / §8.5 delete）
**前置报告**：[`2026-07-17_phase1a5-acceptance-report.md`](../../test-records/manual-tests/2026-07-17_phase1a5-acceptance-report.md)
**前置设计**：[`ai-client-split.md`](../designs/ai-client-split.md)〔v1.0 已落定〕

> 本计划先冻结工作范围和验收边界，再开始编码。生产 MySQL 与前端 E2E 按用户策略统一留到 1a.7 冒烟阶段。

---

## Gate 0 冻结结果（2026-07-17）

### 关键决策

| 决策 | 结论 |
|---|---|
| TextAiClient 与 VisionModelClient 是否拆分 Bean | **拆**（[ai-client-split.md §3.1/§3.2](#) 已落定） |
| IntentClassifier Bean 独立性 | **独立 Bean**（temperature=0.1 单独配置，复用 TextAiClient 客户端） |
| few-shot 数量 | **保持现有 7 正 7 反**（db-schema.sql §6 已种） |
| /conversations POST 创建语义 | **仅返回 conversationId**，不插 chat_history（直到 /chat/send 才写第一条 user 消息） |
| /conversations GET list 派生字段 | Phase 1 仅基础字段（conversationId/type/createdAt/lastMessageAt）；fundCount/snapshotDate/status 派生留 P2 |
| /chat/send conversationId 不存在错误码 | 复用 2001（SNAPSHOT_NOT_FOUND） |
| 测试层级 | Service mock（Mapper + TextAiClient + IntentClassifier 三 mock）+ Controller MockMvc；**不做**真实 minimax text-only 调用 |
| 是否重种 prompt_versions | **不重种**；现有 v1.0 三种已满足 P0-3.5/3.6 |
| 是否新增 ErrorCode | **不新增**；1001/2001/3001/3002/5001/5002 已覆盖 |

### 切片切分

| 切片 | 范围 | 测试 ID |
|---|---|---|
| **Slice A** | `ai/` 包新建 + TextAiClient + IntentClassifier | A6-INT-01/02/03 |
| **Slice B** | ChatService + ChatController + 5 个 DTO | A6-S01~S04 |
| **Slice C** | ConversationService + ConversationController + 4 个 DTO | A6-S05~S07 |
| **Slice D** | MockMvc 补齐 + 文档 | A6-C01~C04 |

---

## 1. 目标

按 [`docs/phase-1/subphase-plan.md §2.6`](#)：

- **1a.18** `POST /api/chat/send` — 投资意图分类 + main_loop / garbage_loop 路由（**[P0-3.5]** few-shot + **[P0-3.6]** system prompt 切换）
- **1a.19** `GET /api/conversations` — 列表（含 type 过滤、分页）
- **1a.20** `GET /api/conversations/{id}` — 详情
- **1a.21** `POST /api/conversations` — 创建空对话
- **1a.22** `DELETE /api/conversations/{id}` — 删除对话（含 chat_history 物理删除）

完成后让前端 1b.4 AI 顾问页能直接调这 5 个端点。

## 2. 范围

| 端点 | 用途 | 关联 |
|---|---|---|
| `POST /api/chat/send` | 消息发送 + 意图路由 + chat_history 写入 | [P0-3.5] [P0-3.6] |
| `GET /api/conversations` | 列表（type/page/pageSize） | — |
| `GET /api/conversations/{id}` | 详情（messages 数组） | — |
| `POST /api/conversations` | 创建空对话（type + userId） | — |
| `DELETE /api/conversations/{id}` | 物理删除 + 返回 deletedMessageCount | — |

## 3. 非目标

- 不做真实 minimax text-only 调用冒烟（留 1a.7 统一做）；
- 不做前端 E2E（留 1b.4）；
- 不实现 conversation_meta 表（沿用 chat_history GROUP BY 派生）；
- 不实现 list 接口的 fundCount/snapshotDate/status 派生字段（Phase 1 仅基础）；
- 不 bump prompt 版本号（v1.0 已满足 P0）；
- 不重构 VisionModelClient（已稳定运行，1a.5 验收通过）。

## 4. 当前基线

### 4.1 已有基础

| 资产 | 状态 |
|---|---|
| `ChatHistory` entity | ✅ 含 ROLE_USER/ROLE_ASSISTANT + CONVERSATION_TYPE_AI_ASSISTANT/SCREENSHOT_PARSE 常量 |
| `ChatHistoryMapper` | ✅ 含 `selectByConversationIdOrderByCreatedAt` + `selectAssistantByType` |
| `prompt_versions` 表 | ✅ 3 行 v1.0 种子：screenshot_parser / ai_assistant / intent_classifier |
| `PromptLoaderService` | ✅ 启动预热缓存，按 name 取 prompt_content |
| `VisionModelClient` | ✅ 视觉 Bean，OpenAI-compat + OkHttp |
| `application.yml` `fincontrol.vision` | ✅ base-url/model/api-key/timeout 已有 |
| minimax M3 平台 | ✅ vision 端已验证可用；text-only 端待 1a.6 验证 |

### 4.2 预计新增

| 路径 | 角色 |
|---|---|
| `ai/TextAiClient.java` | 文本 Bean，与 VisionModelClient 隔离 |
| `ai/IntentClassifier.java` | 独立 Bean，temperature=0.1，复用 TextAiClient |
| `ai/AiProperties.java` | `@ConfigurationProperties("fincontrol.ai")` 统一配置（text 子段） |
| `dto/chat/ChatSendRequest.java` | send body |
| `dto/chat/ChatMessageDto.java` | 通用 message（role/content/createdAt + 可选 routedTo/promptVersion） |
| `dto/chat/IntentClassificationDto.java` | {result, latencyMs} |
| `dto/chat/ChatSendResponse.java` | {conversationId, userMessage, assistantMessage, intentClassification} |
| `dto/conversation/ConversationListItem.java` | 列表项 |
| `dto/conversation/ConversationListResponse.java` | {items, total} |
| `dto/conversation/ConversationCreateRequest.java` | {type, userId} |
| `dto/conversation/ConversationCreateResponse.java` | {conversationId, type, createdAt} |
| `dto/conversation/ConversationDetailResponse.java` | {conversationId, type, messages[]} |
| `dto/conversation/ConversationDeleteResponse.java` | {deletedMessageCount} |
| `service/ChatService.java` | 编排 send 流程 |
| `service/ConversationService.java` | 编排 CRUD |
| `controller/ChatController.java` | `POST /api/chat/send` |
| `controller/ConversationController.java` | `GET/POST/DELETE /api/conversations[/{id}]` |
| `test/.../service/ChatServiceTest.java` | A6-S01~S04 + 边界 |
| `test/.../service/ConversationServiceTest.java` | A6-S05~S07 + 边界 |
| `test/.../service/TextAiClientTest.java` | A6-INT-01/02 |
| `test/.../service/IntentClassifierTest.java` | A6-INT-03 + few-shot 边界 |
| `test/.../controller/ChatControllerTest.java` | URL/body/header/异常码 |
| `test/.../controller/ConversationControllerTest.java` | URL/body/list 分页/异常 |

### 4.3 配置文件追加（`application.yml`）

```yaml
fincontrol:
  ai:
    text:
      base-url: https://api.minimaxi.com/v1
      model: MiniMax-Text-01
      temperature: 0.7
      max-tokens: 1024
      timeout-seconds: 60
      api-key: ${TEXT_AI_API_KEY:REPLACE_ME_TEXT_AI_KEY}
```

> vision / text 各自一套 api-key，互不污染；TEXT_AI_API_KEY 默认与 VISION_API_KEY 同源（minimax M3 文本/视觉复用 key），但允许环境变量覆盖。

## 5. 切片切分（详细）

| # | 切片 | 文件 | 验证命令 | 风险 |
|---|---|---|---|---|
| 1 | Slice A — AI 包 + TextAiClient + IntentClassifier | `ai/TextAiClient.java`, `ai/IntentClassifier.java`, `ai/AiProperties.java`, `application.yml` 追加 text 段 | `mvn compile` + `TextAiClientTest` + `IntentClassifierTest` | minimax text-only schema 与 vision 是否一致 |
| 2 | Slice B — ChatService + ChatController + 5 DTO | `service/ChatService.java`, `controller/ChatController.java`, `dto/chat/*` (5 个) | `ChatServiceTest` 4+ 用例 | 意图分类响应解析 |
| 3 | Slice C — ConversationService + ConversationController + 4 DTO | `service/ConversationService.java`, `controller/ConversationController.java`, `dto/conversation/*` (4 个) | `ConversationServiceTest` 5+ 用例 | list GROUP BY 性能 |
| 4 | Slice D — MockMvc + 文档 | `ChatControllerTest` 5+ + `ConversationControllerTest` 5+ | `mvn test` | — |

## 6. 测试与替身策略（冻结）

| 层级 | 替身 | 目标 |
|---|---|---|
| **TextAiClientTest** | OkHttp + ObjectMapper stub | OkHttp 响应 JSON → content 抽取；HTTP 4xx/5xx → 3001；超时 → 3002 |
| **IntentClassifierTest** | TextAiClient mock | few-shot 输出 `true`/`false` 解析；非布尔 → false 防御 |
| **ChatServiceTest** | TextAiClient + IntentClassifier + ChatHistoryMapper 三 mock | 路由选择 / chat_history 写入 / promptVersion 暴露 |
| **ConversationServiceTest** | ChatHistoryMapper mock | list GROUP BY / get 详情 / create 返回 / delete 物理删 |
| **ChatControllerTest** | ChatService mock + 全 Mapper MockBean | URL/body/header/异常码透传 |
| **ConversationControllerTest** | ConversationService mock + 全 Mapper MockBean | URL/body/list 分页/异常 |
| **真实 minimax text-only** | 不做 | 留 1a.7 冒烟（验证 main_loop / garbage_loop 真实响应） |

> 禁止在测试失败后无记录地把 mock 替身改成真表来"消除失败"。

## 7. 风险与停止条件

| 风险 | 处理 |
|---|---|
| minimax text-only schema 与 vision 不一致 | TextAiClientTest 优先跑通；不通过则按 ai-client-split.md §3.2 调整请求体拼接 |
| 意图分类 7+7 few-shot 准确性 | 业务层单测覆盖解析；真实准确性留 1a.7 冒烟 |
| /conversations list 大数据量性能 | 仅 Phase 1 基础字段；如 GROUP BY 超 200ms 切到下次优化 |
| IntentClassifier 与 ChatService 循环依赖 | IntentClassifier 注入 TextAiClient，不注入 ChatService；单向依赖 |
| ai_assistant prompt 是否需要 bump | **审计**现有 prompt，若投资规则变化（target_ratios 等）则 bump 到 v1.1；否则保持 v1.0 |
| 与 1a.2 ScreenshotService 视觉调用冲突 | TextAiClient 独立 OkHttpClient 实例（connectTimeout=15s / readTimeout=60s） |

## 8. 计划完成标准

- 5 个 endpoint 全部实现 + 业务层 + 契约层 PASS；
- A6-S01~S07 + A6-INT-01/02/03 全部有证据路径；
- A6-C01~C04（Controller MockMvc）全部有证据路径；
- 测试替身未偷换；
- `docs/phase-1/checklists/phase-1a.md` 准确更新；
- MySQL 真库与前端 E2E 仍标 `PRODUCTION_PENDING`，留 1a.7 冒烟；
- 形成 1a.6 验收报告后才能进入下一阶段。

## 9. 关联文档

- 1a.6 验收计划：`docs/test-records/manual-tests/2026-07-17_phase1a6-acceptance-plan.md`
- 1a.5 前置：`docs/test-records/manual-tests/2026-07-17_phase1a5-acceptance-report.md`
- ai-client-split 设计：`docs/phase-1/designs/ai-client-split.md`
- API 契约：`docs/phase-0/api-contract.md §8`
- 总账：`docs/phase-1/checklists/phase-1a.md`