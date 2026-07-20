# Chat Prompt 已知缺陷跟踪（决策 15）

> **创建时间**：2026-07-20  
> **来源**：1a.10 真实 E2E + 灰测复测（chat 3 例）  
> **目标**：1b+ 前端完成后聚合修复（避免 1a 期间反复修改污染 vision cache）  
> **决策 15**：见 `docs/phase-0/decisions.md`

---

## 目录

1. [前置：两个 prompt 版本空间](#前置两个-prompt-版本空间)
2. [复测记录（2026-07-20 17:08）](#复测记录2026-07-20-1708)
3. [已识别问题清单](#已识别问题清单)
4. [修复计划](#修复计划)
5. [后续新增问题（1b+ 期间补充）](#后续新增问题1b-期间补充)

---

## 前置：两个 prompt 版本空间

项目内有**两套独立的 prompt 版本空间**，本文档只针对 **chat prompt（文本对话）**：

| prompt_name | 用途 | 模型 | 当前版本 | 版本号位置 |
|---|---|---|---|---|
| `ai_assistant` | **Chat 文本对话**（本文档范围）| minimax 文本 | **v1.0** | `ChatService.PROMPTVersion="ai_assistant v1.0"` |
| `screenshot_parser` | 截图解析（1a.9 升级）| minimax 多模态 | v2.7.1（**不在本文档范围**）| DB `prompt_versions` 表 |

> ⚠️ **不要混淆这两个 prompt**。vision prompt v2.7.1 是真的（1a.10 过程性验收报告已升级），但 chat prompt 仍为 v1.0。

---

## 1. 复测记录（2026-07-20 17:08）

### 测试环境
- 后端 PID 22408，fincontrol-backend v0.1.0（commit ec66e26）
- minimax 文本模型 + ChatService + IntentClassifier
- 3 个测试用例覆盖：投资类 / 时间类 / 系统介绍类

### 复测原始数据

| # | 用户输入 | HTTP | 耗时 | intent.result | routedTo | promptVersion |
|---|---|---|---|---|---|---|
| 1 | "我的黄金持续低迷，我应该怎么办，割肉吗？" | 200 | 5.3s | true | main_loop | ai_assistant v1.0 |
| 2 | "今天星期几？" | 200 | 2.3s | false | garbage_loop | garbage_loop |
| 3 | "我们这个系统是做什么的？" | 200 | 7.7s | false | garbage_loop | garbage_loop |

### AI 回复原文

**用例 1（✅ 优秀）**：
> 我理解你对黄金表现低迷感到焦虑。但根据你的规则系统，系统只按比例调配，不预测市场，所以不建议割肉。当前建议是：按月归集资金后，进行低风险资产的自动校准（保持货币 10%、固收 15% 的比例），剩余资金继续按比例定投，包括商品类资产（黄金等）。这样可以确保你的整体配置符合预设比例，同时避免因短期波动做出情绪化的决策。

**用例 2（⚠️ AI 行为合理，但缺系统时钟）**：
> 抱歉，我无法得知当前的日期或星期几。不过，如果你告诉我今天的日期，我可以帮你计算今天是星期几。请告诉我今天的日期吧！

**用例 3（❌ 答非所问）**：
> 我们这个系统是一个由上海稀宇科技有限公司开发的 AI 助理平台，名为 MiniMax AI。这个系统旨在为用户提供智能化的帮助和支持，具体功能包括：1. 信息查询和解**答**：回答各种问题...（⚠️ 应答 FinControl）

---

## 2. 已识别问题清单（针对 `ai_assistant v1.0`）

### 问题 #1：Prompt v1.0 缺 FinControl 系统上下文（决策 15 问题 A）
- **严重度**：中（meta 类问题答错，正常投资类问题 OK）
- **影响**：用例 3（"我们这个系统是做什么的"）答成 MiniMax 而非 FinControl
- **根因**：`ai_assistant v1.0` prompt 内容存于 DB `prompt_versions` 表（`prompt_name='ai_assistant'`），缺"你是 FinControl..."角色定义
- **修复方案**：
  - DB `prompt_versions` 插入新行 `version='v1.1'`，在 system prompt 头部添加：
    ```
    你是 FinControl（个人资产配置控制系统），基于控制论反馈环帮助用户做资产配置分析。
    你的核心能力是解读用户的资产截图、给出大类配置建议、回答投资相关问题。
    对于系统外的通用问题礼貌拒绝并引导回资产配置话题。
    ```
  - 同步更新 `ChatService.PROMPTVersion` 为 `"ai_assistant v1.1"`
  - ~50 tokens 增量
- **工作量**：~30 分钟（含 prompt 调优 + 单测）

### 问题 #2：IntentClassifier 不识别 self-intro 类（决策 15 问题 B）
- **严重度**：中
- **影响**：用例 3 被误判为 `garbage_loop`，没走主循环 prompt
- **根因**：IntentClassifier 训练样本缺 "我们这个系统是做什么的" → 应识别为 `system_intro` 路由
- **修复方案**：
  - IntentClassifier 训练样本补充：self-intro / 闲聊 / 命令类 50+ 样本
  - 增加 `system_intro` intent 类别
  - 单测覆盖：补 3 个用例（黄金类 / 星期类 / 系统介绍类）
- **工作量**：~2 小时（样本采集 + 模型微调或规则匹配）

### 问题 #3（已排除）：系统时钟（用例 2）
- **状态**：⏸️ **延迟到 Phase 4**（用户 2026-07-20 决定）
- **原因**：非核心功能，Phase 4 优化用户体验时再处理
- **追踪位置**：Phase 4 计划（待创建 `docs/phase-4/ux-improvements.md`）

---

## 3. 修复计划

### 触发条件（满足任一即修复）
- [ ] 1b+ 前端完成（具备端到端测试能力）
- [ ] 真实用户流量跑 2 周
- [ ] 聚合 chat prompt 缺陷 ≥ 5 类

### 修复内容清单
- [ ] 问题 #1：Prompt v1.0 → v1.1（含 FinControl 角色定义）
- [ ] 问题 #2：IntentClassifier 训练样本补充 + new intent `system_intro`
- [ ] 单测补 3 个用例（覆盖决策 15 的 3 个原 case）
- [ ] 真实流量灰测：再跑 50+ chat 用例验证

### 修复流程
1. 创建分支 `feature/chat-prompt-v1.1`
2. 修改 DB `prompt_versions` 插入 v1.1 行（`prompt_name='ai_assistant'`）
3. 改 `ChatService.PROMPTVersion = "ai_assistant v1.1"`
4. 改 `IntentClassifier` 训练样本 + new intent
5. 跑 241/241 单测
6. 灰测 50+ chat 用例（数据来自真实流量）
7. commit + push → `v0.2.0` tag

### 风险评估
- ⚠️ prompt 改会污染 vision cache（key 含 promptVersion，TTL 24h）— 改后 24h 内可能命中率下降
- ⚠️ IntentClassifier 改可能误分类其他类型 — 必须 100% 通过旧单测 + 新增 50+ 用例
- ⚠️ minimax 文本模型可能升级 → 重新校准 prompt

---

## 4. 后续新增问题（1b+ 期间补充）

> **填表说明**：1b+ 期间发现新的 chat prompt 缺陷请追加到本表。**问题归类原则**：
> - 投资类问答问题 → 修复 1a.11+
> - 系统时钟/闲聊/UX → 延后 Phase 4
> - prompt 内容偏差 → 加入本表聚合修复

| ID | 日期 | 用例 | intent | routedTo | 现象 | 归类 | 状态 |
|---|---|---|---|---|---|---|---|
| #1 | 2026-07-20 | "我们这个系统是做什么的？" | garbage_loop | garbage_loop | AI 答成 MiniMax | prompt 缺陷 | 决策 15 |
| #2 | 2026-07-20 | "我们这个系统是做什么的？" | garbage_loop | garbage_loop | meta 类意图未识别 | intent 缺陷 | 决策 15 |
| #3 | 2026-07-20 | "今天星期几？" | garbage_loop | garbage_loop | AI 无系统时钟 | UX 缺陷 | 延后 Phase 4 |

---

## 附：相关文件

- `docs/phase-0/decisions.md` 决策 15
- `docs/phase-1/USER-MANUAL.md` §11.2 1b+ follow-up 表
- `docs/test-records/manual-tests/2026-07-19_phase1a10-real-e2e.md` §9.9 后续 follow-up
- `fincontrol-backend/src/main/java/com/fincontrol/service/ChatService.java:40` `PROMPTVersion` 常量
- `fincontrol-backend/logs/chat-test-{1,2,3}-resp.json` 原始响应

---

*最后更新：2026-07-20 21:46（Asia/Shanghai）*
