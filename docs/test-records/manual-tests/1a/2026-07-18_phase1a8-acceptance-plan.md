# Phase 1a.8 验收计划（AI 服务韧性增强）

**计划日期**：2026-07-18
**子阶段**：Phase 1a.8 〔AI 多路 fallback + 韧性 + 缓存〕
**配套工作计划**：[`2026-07-18_phase1a8-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-work-plan.md)
**总进度清单**：[`phase-1a.md`](../../phase-1/checklists/phase-1a.md)
**前置报告**：[`2026-07-17_phase1a7-acceptance-report.md`](2026-07-17_phase1a7-acceptance-report.md)

> 本验收计划基于 1a.7 验收中**PRODUCTION 段未达标**的两个问题：minimax 限流 + "1 fund per image" 现象。

---

## 1. 验收目标

按 [`phase1a8-work-plan.md` Gate 0 §决策](#) 拍板的判据：

- **5 段式全 PASS**（其中 PRODUCTION 段由 1a.7 的"75%"提升到 100%）
- **vision 4/4 = 100%**（替代 1a.7 的 3/4）
- **chat 5/5**（与 1a.7 一致）
- **fallback 自动**（HTTP 5xx/超时/429 → 透明切换到豆包/DeepSeek）
- **缓存**（同张图不重复调用 API）
- **业务层单测 0 回归**（仍 179/179 PASS）

## 2. 测试用例（按 ID 排序）

### A8-S00：根因调查（Step 0，1a.8 必做）

| 字段 | 内容 |
|---|---|
| **目标** | 确认 "1 fund per image" 是 minimax 模型能力问题还是代码 JSON 抽取 bug |
| **步骤** | 1) 写 debug 脚本 `scripts/1a8/00-debug-vision.sh` 2) 调 minimax M3 跑 4 张样本图 3) 输出**原始 JSON 全文**（不抽取）+ 我们抽取的基金数 |
| **预期** | (A) 模型 bug：原始 JSON 里有 N 只基金，我们只抽到 1 只 → 修 `extractFirstJsonObject`  <br/>(B) 模型能力 bug：原始 JSON 里就 1 只 → 切豆包（Step 1 必做）<br/>(C) 系统 prompt 问题：原始 JSON 里有多只但漏抽字段 → 调 prompt |
| **实际** | _执行后回填_ |

### A8-S01：VisionModelClient 加豆包支持

| 字段 | 内容 |
|---|---|
| **目标** | 验证 VisionModelClient 能同时调通 minimax (OPENAI_CHAT) 和豆包 (OPENAI_RESPONSES) |
| **替身** | **mock HTTP**（用 okhttp `MockWebServer`），不调真实 API（节省钱 + 稳定） |
| **步骤** | 1) 单元测试用 MockWebServer 模拟豆包 `/api/v3/responses` 返 `{"output": [{"content": [{"type": "output_text", "text": "{...JSON...}"}]}]}` <br/>2) `visionModelClient.callRaw(image, prompt, msg, apiStyle=OPENAI_RESPONSES)` <br/>3) 断言请求体含 `input` 字段 + `input_image` 类型 + 豆包 URL <br/>4) 断言响应解析拿到 content |
| **预期** | 2 个测试都 PASS：minimax 走 `/chat/completions`，豆包走 `/responses` |
| **关联** | 单元测试 `VisionModelClientTest.java` 新增 `@Nested OpenAIResponsesStyle` |

### A8-S02：TextAiClient 加 DeepSeek fallback

| 字段 | 内容 |
|---|---|
| **目标** | 验证 TextAiClient 可以切 baseUrl 到 DeepSeek |
| **替身** | **mock HTTP** |
| **步骤** | 配置 `fincontrol.ai.text.base-url=https://api.deepseek.com/v1`，调用 chat，断言请求打到 DeepSeek URL |
| **预期** | PASS（DeepSeek 是 OpenAI 兼容，零代码改动，只需换 baseUrl） |

### A8-S03：AiRouter 单元测试

| 字段 | 内容 |
|---|---|
| **目标** | 验证 AiRouter 完整版的 retry + circuit breaker + cache + fallback 行为 |
| **替身** | **mock 两个 VisionClient + 两个 TextClient**（用 Mockito） |
| **步骤** | 1) **retry 测试**：primary 第一次抛 SocketTimeoutException，第二次成功 → 不切换 fallback <br/>2) **fallback 测试**：primary 连续 3 次返 502 → 切到 fallback，fallback 成功 <br/>3) **cache 测试**：同张图连续调 2 次，只命中 1 次 provider（第二次 cache hit） <br/>4) **circuit breaker 测试**：连续 5 次失败 → 断路器打开，后续请求直接 fallback（不再试 primary） |
| **预期** | 4 个测试都 PASS |
| **关联** | `AiRouterTest.java`（新） |

### A8-S04：end-to-end 真实 API 冒烟（核心验收）

| 字段 | 内容 |
|---|---|
| **目标** | 真实跑豆包 + minimax + DeepSeek，验证 4/4 vision + 5/5 chat + fallback 链路 |
| **替身** | **真实 API**（豆包 + DeepSeek key 已在 application-local.yml）|
| **步骤** | 1) 跑 `scripts/1a8/03-e2e-smoke.sh`（封装 1a.7 的 smoke + AI router 验证） <br/>2) 4 张截图依次 parse，记下每张用了哪个 provider 和是否 fallback <br/>3) 5 个 chat 文本用例发请求，记下 provider <br/>4) 故意把 VISION_API_KEY 改成无效值（用 `MYSQL_PORT=3307` 容器跑 minimax 模拟限流），看豆包是否接管 |
| **预期** | 1) 4/4 vision PASS，3 张用豆包，1 张用 minimax（或反过来，**全过即可**）<br/>2) 5/5 chat PASS，4 个用 minimax text，1 个用 DeepSeek（或反过来）<br/>3) 限流时豆包自动接管，2 张图全部 parse 成功 |
| **关联** | 真实 API，依赖 application-local.yml 配齐 key |

### A8-S05：监控埋点（chat_history 字段）

| 字段 | 内容 |
|---|---|
| **目标** | 验证 chat_history 表新增了 `used_provider` 和 `fallback_triggered` 字段 |
| **步骤** | 1) 跑 e2e smoke 后查询 chat_history 最新几条 <br/>2) 断言字段值正确（如 `used_provider='doubao'` `fallback_triggered=true`） |
| **预期** | 字段存在且值合理 |

### A8-S06：cache 幂等性

| 字段 | 内容 |
|---|---|
| **目标** | 验证同张图二次调用命中 cache，不消耗 API 配额 |
| **步骤** | 1) 跑 parse 同张图 2 次 <br/>2) 第二次应在 cache 返回（不会调 API）<br/>3) 验证 provider 调用计数器 = 1 |
| **预期** | 第二次返回相同 convId，provider 调用只 1 次 |

## 3. 5 段式判定

| 段 | 1a.7 状态 | 1a.8 目标（方案 C）|
|---|---|---|
| **BUSINESS** | ✅ 179/179 PASS | ✅ 仍 179/179（0 回归）|
| **CONTRACT** | ✅ MockMvc 全过 | ✅ 仍全过 |
| **READ_SQL** | ✅ dialect 修复 | ✅ 仍过 |
| **PRODUCTION** | 🟡 75% (3/4 vision) | ✅ **100% (4/4 vision + 5/5 chat)** — **方案 C 路由** |
| **COVERAGE** | ✅ 76.5% | ✅ 仍 ≥ 60% |

**1a.8 方案 C 路由判定**（PRODUCTION 段细项）：
- ✅ **1-2 张图** → minimax OPENAI_CHAT 纯快路径（不 fallback）
- ✅ **3+ 张图** → 豆包 OPENAI_RESPONSES primary + minimax fallback
- ✅ **豆包失败** → 切 minimax（互为 fallback）
- ✅ **minimax 失败（1-2 张场景）** → 报错 5001（不切）
- ✅ **chat_history 记录** `used_provider`（minimax/doubao/deepseek）

## 4. 验收结论格式

1a.8 验收报告 `2026-07-18_phase1a8-acceptance-report.md` 末尾必须包含：

```text
- BUSINESS:       ✅ / ❌ (实际数据)
- CONTRACT:       ✅ / ❌
- READ_SQL:       ✅ / ❌
- PRODUCTION:     ✅ / ❌
- COVERAGE:       ✅ / ❌
- PASS:           ✅ / ❌ (5 段全过)

遗留问题（如有）：xxx

Phase 1a 闭环：✅ / ❌ (本 1a.8 报告即 1a 总验收收尾)
```

## 5. 关联文档

- 工作计划：[`phase1a8-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-work-plan.md)
- 1a.7 验收报告：[`2026-07-17_phase1a7-acceptance-report.md`](2026-07-17_phase1a7-acceptance-report.md)
- API 契约（错误码）：[`docs/phase-0/api-contract.md`](../../../phase-0/api-contract.md)
- AI 客户端配置：`fincontrol-backend/src/main/resources/application.yml`
- 端到端冒烟脚本：`fincontrol-backend/scripts/1a8/03-e2e-smoke.sh`