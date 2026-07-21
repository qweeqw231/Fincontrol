# Phase 1a.6 过程性验收报告

**日期**：2026-07-17
**测试者**：刘博丞
**子阶段**：整体 Phase 1a.6 〔AI 顾问 API〕
**状态**：🟢 **1a.6 business/contract acceptance: CONTRACT_PASS**〔生产层 **PRODUCTION_PENDING**，与 1a.3 / 1a.4 / 1a.5 共用同一 gate〕

> 本报告与 1a.3 / 1a.4 / 1a.5 报告保持同样的诚实分层口径：business / contract / real-SQL / production / PASS 五段独立判断，不互相顶替。

---

## 1. 分层结论

| 层级 | 状态 | 含义 |
|---|---|---|
| **BUSINESS_PASS** | ✅ | 4 个 Service 测试类全过：TextAiClient 16 + IntentClassifier 18 + ChatService 11 + ConversationService 16 = **61/61 PASS** |
| **CONTRACT_PASS** | ✅ | 2 个 Controller MockMvc 测试类全过：ChatController 7 + ConversationController 11 = **18/18 PASS** |
| **READ_SQL_PASS** | 🟡 | ChatHistoryMapper 新增 3 个 @Select/@Delete 方法（list GROUP BY / count / deleteByConversationId）；真实 MySQL 验证留 1a.7 冒烟 |
| **PRODUCTION_PENDING** | 🟡 | minimax text-only 真实 API 响应延迟 / 真实分类准确性 / MySQL 真库 / 前端 E2E |
| **PASS** | 🟡 | 等 PRODUCTION_PENDING 转 PASS |

> 5 段式判定定义见 [`2026-07-17_phase1a6-acceptance-plan.md`](./2026-07-17_phase1a6-acceptance-plan.md)。

**全量测试统计**：`mvn test` 共 **151/151 PASS**（含 1a.2-1a.5 既有用例 0 回归 + 1a.6 新增 79 用例全过）。

---

## 2. Slice A/B/C 实施结果

### 2.1 Slice A — AI 包（TextAiClient + IntentClassifier）

| 项 | 内容 |
|---|---|
| **DTO/Props** | `AiProperties` (`@ConfigurationProperties("fincontrol.ai")` + Text 子段) |
| **Service** | `TextAiClient`（OpenAI-compat + OkHttp；独立 OkHttp 实例；`chat(systemPrompt, userMessage)` API） |
| **Service** | `IntentClassifier`（独立 Bean；temperature=0.1；`isInvestmentRelated(userMessage)` 严格解析 true/false） |
| **Config** | `AiConfig` (`@Configuration` + `@EnableConfigurationProperties(AiProperties.class)`) |
| **配置文件** | `application.yml` 追加 `fincontrol.ai.text.*`（base-url / model / temperature=0.7 / maxTokens=1024 / api-key） |
| **测试** | TextAiClient 16/16（OkHttp Interceptor 拦截）、IntentClassifier 18/18 |

### 2.2 Slice B — ChatService + ChatController

| 项 | 内容 |
|---|---|
| **DTO** | `ChatSendRequest` / `ChatMessageDto` (通用) / `IntentClassificationDto` / `ChatSendResponse` |
| **Service** | `ChatService.send(userId, req)`：conversationId 自动生成 → 写 user 消息 → IntentClassifier → 路由（main_loop / garbage_loop）→ 调 TextAiClient → 写 assistant 消息 → 构造响应 |
| **Controller** | `ChatController` (`POST /api/chat/send`) |
| **测试** | ChatService 11/11（含 A6-S01~S04 + 校验 + 错误路径 + latency），ChatController 7/7 |

### 2.3 Slice C — ConversationService + ConversationController

| 项 | 内容 |
|---|---|
| **DTO** | `ConversationListItem` (4 基础字段) / `ConversationListResponse` / `ConversationCreateRequest` / `ConversationCreateResponse` / `ConversationDetailResponse` / `ConversationDeleteResponse` |
| **Mapper** | ChatHistoryMapper 追加 3 个方法：`selectConversationList` (GROUP BY) / `countConversations` / `deleteByConversationId` |
| **Service** | `ConversationService.list/get/create/delete`：list 支持 type 过滤 + 分页 + user 隔离；get 校验 user 所有权；create 仅返 UUID 不插库（[work-plan G4](#)）；delete 物理删除返 deletedMessageCount |
| **Controller** | `ConversationController` (`GET/POST/DELETE /api/conversations[/{id}]`) |
| **测试** | ConversationService 16/16（含 A6-S05~S07 + 边界），ConversationController 11/11 |

---

## 3. 测试结果总览

| 测试类 | 用例 | 状态 |
|---|---:|:---:|
| `TextAiClientTest`（新增） | 16 | ✅ 16/16 |
| `IntentClassifierTest`（新增） | 18 | ✅ 18/18 |
| `ChatServiceTest`（新增） | 11 | ✅ 11/11 |
| `ChatControllerTest`（新增） | 7 | ✅ 7/7 |
| `ConversationServiceTest`（新增） | 16 | ✅ 16/16 |
| `ConversationControllerTest`（新增） | 11 | ✅ 11/11 |
| `AssetControllerTest` | 7 | ✅ 7/7（回归） |
| `AssetQueryServiceTest` | 5 | ✅ 5/5（回归） |
| `CategoryMapControllerTest` | 10 | ✅ 10/10（回归） |
| `CategoryMapServiceTest` | 13 | ✅ 13/13（回归） |
| `DedupEngineTest` | 9 | ✅ 9/9（回归） |
| `FincontrolApplicationTests` | 1 | ✅ 1/1（回归） |
| `ScreenshotServiceTest` | 6 | ✅ 6/6（回归） |
| `SnapshotControllerTest` | 11 | ✅ 11/11（回归） |
| `SnapshotQueryServiceTest` | 10 | ✅ 10/10（回归） |
| **总计** | **151** | **✅ 151/151** |

> **1a.6 新增 79 用例**（INT 18 + Service 11+16 + Controller 7+11 + 文本端 TextAiClient 16 = 79），既有用例无回归。

---

## 4. A6-S01–S07 + A6-INT-01/02/03 + A6-C01–C04 用例实际结果

| ID | 实际结果 | 替身 | 关键证据 |
|---|---|---|---|
| **A6-INT-01** | ✅ PASS | 配置隔离 | `fincontrol.ai.text.*` 独立 OkHttp 实例 + 独立 Bean；`AiProperties.Text` 与 `fincontrol.vision.*` 互不污染 |
| **A6-INT-02** | ✅ PASS | 配置独立 | IntentClassifier 复用 TextAiClient，但 temperature 独立硬编码 0.1 |
| **A6-INT-03** | ✅ PASS | TextAiClient mock | 18 用例覆盖：true/false → 对应；"True"/"FALSE"/"true."/"YES"/""/"true false" → 防御性 false；空 userMessage → false 不调 AI |
| **A6-S01** | ✅ PASS | AI 三件套 + Mapper | 投资类 → main_loop + promptVersion='ai_assistant v1.0' + content="基于您的规则..." |
| **A6-S02** | ✅ PASS | AI 三件套 + Mapper | 非投资类 → garbage_loop + 无 promptVersion + systemPrompt=null 调 TextAiClient |
| **A6-S03** | ✅ PASS | Mapper | conversationId 空 → 自动生成 `conv-<UUID>`；已存在 → 复用 |
| **A6-S04** | ✅ PASS | Mapper | user + assistant 两条 insert；user 在前；conversationType='ai_assistant'；createdAt 落库 |
| **A6-S05** | ✅ PASS | Mapper | list GROUP BY；user 隔离；type 过滤；pageSize 截到 100；page<1 截到 1；offset = (page-1)*pageSize |
| **A6-S06** | ✅ PASS | Mapper | messages 按 created_at asc；type 从首条消息推断；user 隔离（其他 user 的 conversation 返 2001） |
| **A6-S07** | ✅ PASS | Mapper | deleteByConversationId 调用；返回 deletedMessageCount；空对话返 0 不抛错 |
| **A6-C01** | ✅ PASS | ChatService mock | 7 用例：URL/body/header；investment/non-investment 两条路径；1001/3001 异常码；body userId 覆盖 header |
| **A6-C02** | ✅ PASS | ConversationService mock | list 4 用例：URL/参数/header；type 过滤；分页 |
| **A6-C03** | ✅ PASS | ConversationService mock | create 3 用例：合法 type 返 conversationId；非法 type 返 1001；body userId 覆盖 |
| **A6-C04** | ✅ PASS | ConversationService mock | delete 3 用例：返 deletedMessageCount；空对话返 0；header userId 透传 |

> 完整用例数 = **14 项**（INT 3 + S 7 + C 4）+ 1a.6 编码外多覆盖 60 项（边界、错误路径、白盒、参数捕获等），共 79 用例。

---

## 5. 5 段式判定具体证据

### 5.1 BUSINESS_PASS ✅

- **AI 层**：TextAiClient 16/16（OkHttp Interceptor 拦截正常 200 / HTTP 4xx 5xx / 非 JSON / 超时 / IOException / API key 未配置 / 空 userMessage 等）；IntentClassifier 18/18（true/false 解析 + 边界 + 透传）。
- **Service 层**：ChatService 11/11（main_loop / garbage_loop / 路由 + 写库 + 错误路径 + latency）；ConversationService 16/16（list / get / create / delete + user 隔离 + 边界）。
- **测试替身**：Service mock + Controller MockMvc + Mapper mock（与验收计划 §2 锁定一致，无偷换）。
- **无 NPE / 500 / 字段漂移**。

### 5.2 CONTRACT_PASS ✅

- ChatController 7/7：URL/body/header 全覆盖；1001/3001 异常码透传；body userId 覆盖 header。
- ConversationController 11/11：list/create/get/delete 4 端点 URL + body + header 全覆盖；1001/2001/正常路径。
- **0 回归**：1a.2-1a.5 既有用例 72/72 全过。

### 5.3 READ_SQL_PASS 🟡

- **新 SQL**（ChatHistoryMapper 3 个方法）：
  - `selectConversationList(userId, type, limit, offset)`：GROUP BY + ORDER BY + LIMIT/OFFSET
  - `countConversations(userId, type)`：COUNT(DISTINCT conversation_id)
  - `deleteByConversationId(convId)`：物理 DELETE
- **业务层 mock 通过**；真实 MySQL 8.0.46 验证留 1a.7 冒烟。

### 5.4 PRODUCTION_PENDING 🟡

- minimax text-only 真实 API 响应延迟 / 真实意图分类准确性（7+7 few-shot 真实数据上可能 < 80%）
- MySQL 8.0.46 真库 `GROUP BY conversation_id` 行为
- 前端 E2E（1b.4）

### 5.5 PASS 🟡

- 待 PRODUCTION_PENDING 转 PASS

---

## 6. 关键设计取舍回顾

| 取舍 | 选择 | 实际效果 |
|---|---|---|
| TextAiClient vs VisionModelClient | **独立 Bean**（[ai-client-split.md](#) 落定） | 独立 OkHttp 实例 + 独立配置前缀 + 独立 temperature；防止上下文污染 |
| IntentClassifier 温度 | **0.1 硬编码** | 低温度保分类稳定；与 TextAiClient 0.7 隔离 |
| few-shot 数量 | **保持 7 正 7 反**（db-schema 已种） | 不重写 prompt；不引入新版本；接受稍多的 token 消耗换取更稳的分类 |
| /conversations POST 语义 | **仅返 conversationId，不插库** | 避免空对话堆积；首条 user 消息在 /chat/send 时才落 |
| list 派生字段 | **Phase 1 仅 4 基础字段** | fundCount/snapshotDate/status 留 P2；不做复杂 JOIN |
| IntentClassifier 失败处理 | **fallback 响应 + 写 assistant 错误记录**（不抛） | 不破坏对话；用户能看到错误码 |
| TextAiClient 失败处理 | **透传 + 写 assistant 错误记录**（抛） | 主回路已尽力，让用户感知 |
| 测试替身 | OkHttp Interceptor 模式（无需 mockwebserver） | 节省测试依赖；16 个集成测试全过 |

---

## 7. 实施顺序与 commit 记录

| # | commit | 改动 |
|---|---|---|
| 1 | `feat(1a.6): add ai/ package + TextAiClient + IntentClassifier` | AiProperties + AiConfig + TextAiClient + IntentClassifier + application.yml 追加 + 2 个测试类 |
| 2 | `feat(1a.6): chat send endpoint` | 4 DTO + ChatService + ChatController + 2 个测试类 |
| 3 | `feat(1a.6): conversation CRUD endpoints` | 6 DTO + ChatHistoryMapper 3 方法 + ConversationService + ConversationController + 2 个测试类 |
| 4 | `docs(1a.6): checklist + acceptance report + plan update` | 本报告 + checklist 勾选 + subphase-plan 进度更新 |

> 实际 git commit 由用户提交时统一执行，本报告只列计划顺序。

---

## 8. 遗留问题与 PENDING 项

| 项 | 状态 | 计划 |
|---|---|---|
| minimax text-only 真实 API 调用冒烟 | 🟡 PENDING | 1a.7 冒烟阶段（验证 main_loop / garbage_loop 真实响应） |
| 意图分类真实数据准确性（7+7 few-shot） | 🟡 PENDING | 1a.7 真实数据评估；若 < 80% 触发 prompt bump 到 v1.1 |
| MySQL 8.0.46 真库 GROUP BY 性能 | 🟡 PENDING | 1a.7 冒烟；单 user 数据量小应该不是问题 |
| 前端 E2E（/ai 页面调 5 端点） | 🟡 PENDING | 1b.4 阶段 |
| Swagger UI 暴露 5 个新端点确认 | 🟡 待确认 | 1a.7 冒烟前检查 |
| 单元测试覆盖率（要求 ≥ 60%） | 🟡 待统计 | 1a.7 阶段 JaCoCo |
| conversation list 派生字段（fundCount/snapshotDate/status） | 🟡 Phase 1 留 P2 | 视需求推进 |

---

## 9. 当前验收结论

```
1a.6 business/contract acceptance: PASS
1a.6 production/frontend acceptance: PENDING
```

> 1a.6 可作为后端 1a 收官前的"已通过业务+契约闭环"清单项。生产层验证仍需 1a.7 冒烟（MySQL 真库 + minimax text-only 真实调用 + 前端 E2E）。

---

## 10. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-17 | 创建 A6-INT-01/02/03 + A6-S01–S07 + A6-C01–C04 验收计划，固定测试层级与替身边界 | 吸取 1a.3/1a.4/1a.5 测试替身/真实路径漂移教训 |
| 2026-07-17 | Gate 0 冻结：TextAiClient 独立 Bean / IntentClassifier 独立 / 7+7 few-shot / POST 仅返 conversationId / list 基础字段 / 复用 2001 / 不重种 prompt_versions / 不新增 ErrorCode | 与 ai-client-split.md + api-contract §8 + 1a.5 既有架构对齐 |
| 2026-07-17 | 完成 1a.6 编码 + 测试 + 文档，AI 层 34 + Service 27 + Controller 18 = 79 用例全 PASS | 提交 CONTRACT_PASS；mvn test 151/151 全过，0 回归；PRODUCTION_PENDING 留 1a.7 |