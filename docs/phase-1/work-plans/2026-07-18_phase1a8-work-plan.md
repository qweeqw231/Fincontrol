# Phase 1a.8 AI 服务韧性增强 工作计划

**计划日期**：2026-07-18
**子阶段**：Phase 1a.8 〔AI 多路 fallback + 韧性 + 缓存〕
**负责人**：刘博丞
**状态**：`GATE0_PASS`
**配套验收计划**：[`2026-07-18_phase1a8-acceptance-plan.md`](../../test-records/manual-tests/2026-07-18_phase1a8-acceptance-plan.md)
**总进度清单**：[`phase-1a.md`](../checklists/phase-1a.md)
**前置报告**：[`2026-07-17_phase1a7-acceptance-report.md`](../../test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md)
**关键设计依据**：[`docs/SETUP.md`](../../SETUP.md)（API Key 配置）、[`docs/phase-0/api-contract.md`](../../phase-0/api-contract.md)（错误码 3001/3002/3003）

> 本计划基于 1a.7 验收中的两个**未达标点**：
> 1. minimax vision API 限流（4/4 parse 中 1 张 502 + 1 张 reparse 504 → 75% 成功率）
> 2. "1 fund per image" 现象（3 张成功 parse 的图都只识别 1 只基金，疑似抽取/模型问题）
> 1a.7 验收 5 段式判定 PASS（4 段明确 + 1 段待补），但用户要求 4/4 = 100% 才算 1a 闭环。

---

## Gate 0 冻结结果（2026-07-18）

### 关键决策

| 决策 | 结论 |
|---|---|
| **必做的根因调查** | 1a.8 启动后**第一件事**调 minimax M3 看 4 张图的**原始响应**，确认 "1 fund" 是模型能力问题还是代码 JSON 抽取 bug |
| Vision 主备关系 | **豆包 doubao-seed-1-8-251228 primary** + minimax M3 fallback（用户判断豆包更准 + minimax 限流频繁） |
| Chat 主备关系 | **minimax M3 (text) primary** + DeepSeek V3 fallback（minimax 当前可用，DeepSeek 兜底） |
| Fallback 触发条件 | **HTTP 5xx / 网络超时 / HTTP 429** → 自动透明切换（HTTP 4xx = 参数错，不切换） |
| AiRouter 深度 | **完整版**：circuit breaker（连续失败切 fallback）+ retry（重试 N 次再切）+ cache（同张图不重复调用） |
| 豆包 API 形态 | 使用 **OpenAI Python SDK 的 `responses` API**（`/api/v3/responses`，input 而非 messages）— 与 minimax 的 OpenAI `chat/completions` 不同，需要 `apiStyle` 字段分支 |
| DeepSeek API 形态 | **OpenAI 完全兼容**（`/v1/chat/completions`），只需 baseUrl 切换 |
| 替身策略 | 真实豆包 + 真实 DeepSeek（minimax 已验证过） |
| 失败处理 | primary 失败 → fallback 成功 = 业务侧无感；primary + fallback 都失败 = 抛 5001/5002 错误码 |
| 监控埋点 | chat_history 中记录 `used_provider` 和 `fallback_triggered` |
| API key 保管 | **不入仓**；`DOUBAO_VISION_API_KEY` 和 `DEEPSEEK_CHAT_API_KEY` 走 env var 优先 + application-local.yml 占位（gitignored） |
| 是否新增 ErrorCode | **不新增**（沿用 1001/2001/3001/3002/5001/5002 + 3003） |
| 输出 | 脚本 `fincontrol-backend/scripts/1a8/*.sh` + 报告 `docs/test-records/manual-tests/2026-07-18_phase1a8-acceptance-report.md` |
| 1a 闭环判据 | **vision 4/4 = 100%** + chat 5/5 + minimax 限流自动 fallback 成功（**必须** 4/4 才算 1a 闭环） |

### 切片切分

| 切片 | 范围 | 验证点 |
|---|---|---|
| **0（前置调查）** | debug 脚本：调 minimax M3 跑 4 张样本图，输出**原始 JSON 全文** + 我们抽取的基金数 | 确认 "1 fund per image" 根因（代码 bug / 模型能力）|
| **Step 1** | `VisionModelClient` 加 `apiStyle` 枚举（OPENAI_CHAT / OPENAI_RESPONSES），支持豆包 | 单元测试：豆包 + minimax 两路径都能调通（mock HTTP） |
| **Step 2** | `TextAiClient` 加 fallback（DeepSeek baseUrl）+ resilience4j Retry/CircuitBreaker + caffeine cache | 单元测试：DeepSeek fallback 路径 |
| **Step 3** | `AiRouter`（chat / vision 两路由）+ Caffeine cache（SHA-256(file) 为 key） + chat_history 监控字段 | 单元测试：primary 失败 → fallback 成功；同张图二次调用命中 cache |
| **Step 4** | `scripts/1a8/` 端到端验证脚本 + 跑全量 smoke（chat 5/5 + vision 4/4 + 故意 kill 豆包流量看 fallback） | 实际跑通 + 报告 5 段式 |
| **Step 5** | 写 1a.8 验收报告 + 更新 phase-1a.md checklist + 更新 subphase-plan.md §2.8 | docs 同步 |

---

## 1. 目标

解决 1a.7 验收中的两个**遗留问题**，让 Phase 1a 真正闭环：

- **问题 1**：minimax vision API 限流导致 4/4 parse 只能 3/4 通过
- **问题 2**：成功 parse 的图只识别 1 只基金（疑似模型或代码问题）

完成后 1a 的 5 段式验收变为：
- BUSINESS：179/179 PASS
- CONTRACT：MockMvc 全过
- READ_SQL：1a.7-PRE dialect 修复 + 真实 MySQL upsert
- **PRODUCTION：vision 4/4 + chat 5/5（豆包 primary + 多路 fallback）** ← 本次要达成
- COVERAGE：76.5%

## 2. 范围

| 端点 / 组件 | 用途 |
|---|---|
| 豆包 ARK API | vision primary（之前是 minimax）|
| DeepSeek API | chat fallback（之前 minimax 单点）|
| `minimax M3` | vision fallback + chat primary（保留）|
| resilience4j（已在 pom.xml）| Retry + CircuitBreaker + TimeLimiter |
| caffeine（已在 pom.xml）| 同图 cache（SHA-256 为 key）|
| `AiRouter` | 统一 chat/vision 入口，try primary → fallback 链 |
| `application.yml` | `fincontrol.ai.fallback.{chat, vision}` 配置段 |

## 3. 非目标

- 不做模型评测（哪个更准的对比表）
- 不做前端改造（Phase 1b 范围）
- 不动业务层单测（已 179/179 PASS，0 回归）
- 不重写 1a.2-1a.6 已通过的任何代码
- 不替换 minimax（**只改主备顺序**，豆包 primary，minimax fallback）
- 不做 prompt 调优（如果根因是模型能力 → 切豆包；如果是代码 bug → 修代码）
- 不新增 ErrorCode（沿用 1001/2001/3001/3002/5001/5002 + 3003）

## 4. 前置依赖 & 现状

- ✅ Phase 1a.7 5 段式验收 PASS（4 段明确 + 1 段 PRODUCTION 待补）
- ✅ `VisionModelClient` 用 OpenAI `chat.completions` 调 minimax M3
- ✅ `TextAiClient` 用 OpenAI `chat/completions` 调 minimax M3 (text)
- ✅ resilience4j 已在 pom.xml（但未在客户端代码使用）
- ✅ caffeine 已在 pom.xml
- ⚠️ `application.yml` 已支持 `DB_HOST/DB_PORT/VISION_API_KEY/TEXT_AI_API_KEY` env var
- ❌ 缺豆包 ARK client 类
- ❌ 缺 DeepSeek baseUrl 配置
- ❌ 缺统一 AiRouter 入口
- ❌ 缺 cache 层
- ❌ 缺 retry / circuit breaker 注解

## 5. 关键风险

| 风险 | 缓解 |
|---|---|
| 豆包 API key 缺失（用户后填）| application.yml 写占位，启动时 TextAiClient/VisionModelClient 抛 5001 |
| 豆包 ARK API 实际 schema 与用户 Python 示例不一致 | Step 0 先用 debug 脚本验证；如果不一致，临时切到 OpenAI 兼容的 `chat.completions`（部分 ARK 端点也支持） |
| DeepSeek API key 缺失 | 同上占位 + 启动检查 |
| 1 fund 之谜根因是模型能力 → 切豆包也不能解决 | 兜底：在 1a.8 验收报告里诚实记录 "豆包也是 1 fund" 算作已知限制 → 进 Phase 2 处理 |
| 1 fund 之谜根因是 JSON 抽取 bug | Step 1 直接修 `extractFirstJsonObject`，4 张图全测 |
| caffeine cache 把"已经修正后的结果"也缓存住 | key 包含文件 hash + parse 模式（first/all），reparse 强制不走 cache |

## 6. 停止条件

- 端到端 smoke **4/4 vision + 5/5 chat = 9/9 100%**（不再允许 75%）
- minimax 限流时豆包 fallback 自动接管（用脚本故意断网验证）
- 业务层单测仍 179/179 PASS（0 回归）
- chat_history 中能看到 `used_provider` 和 `fallback_triggered` 字段

## 7. 提交检查点

| 阶段 | commit 消息前缀 |
|---|---|
| 1a.8 work plan + acceptance plan + docs | `docs(1a.8): add work plan, acceptance plan, update README` |
| Step 0 根因调查 | `investigate(1a.8): debug minimax M3 raw response for "1 fund" mystery` |
| Step 1-3 客户端 + Router | `feat(1a.8): add Doubao vision + DeepSeek chat + AiRouter with cache/retry/CB` |
| Step 4 端到端 smoke | `test(1a.8): end-to-end 4/4 vision + 5/5 chat + fallback validation` |
| Step 5 验收 + checklist | `chore(1a.8): 5-segment acceptance report, update phase-1a.md` |

## 8. 相关文档

- 1a.7 验收报告（前置）：`docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md`
- 1a.7 计划（前置）：`docs/phase-1/work-plans/2026-07-17_phase1a7-work-plan.md`
- API 契约：`docs/phase-0/api-contract.md`（错误码 3001/3002/3003）
- 1a 总进度清单：`docs/phase-1/checklists/phase-1a.md`
- 子阶段总览：`docs/phase-1/subphase-plan.md` §2.8（本计划）