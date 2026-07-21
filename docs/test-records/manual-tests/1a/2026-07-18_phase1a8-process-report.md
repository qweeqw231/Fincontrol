# Phase 1a.8 过程性进度报告（非验收报告）

**报告日期**：2026-07-18
**状态**：🟡 **进行中**（非 1a.8 闭环）
**类型**：**过程性报告**（不是验收通过）
**目的**：诚实记录当前进展 + 当前问题 + 解决方案 + 未完成部分，留待 1a.9 / Phase 1b 启动前继续
**配套**：[`2026-07-18_phase1a8-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-work-plan.md) + [`2026-07-18_phase1a8-acceptance-plan.md`](2026-07-18_phase1a8-acceptance-plan.md)（待实施完成后用）

> **重要声明**：本报告是**过程性记录**，不是 1a.8 验收结论。
> 1a.8 闭环需要等代码改造完成 + smoke 4/4 = 100% + 5-segment 判定通过。

---

## 1. 当前进展（2026-07-18 截至 11:44）

### ✅ 已完成（已 commit + push）
| Commit | 内容 |
|---|---|
| `e100dc9` | 1a.8 docs 写好：work plan + acceptance plan + phase-1a.md + subphase-plan.md + README 更新 |
| `e5c48f0` | Step 0 根因调查 + smoke 显示 bug 修复（"1 fund per image" 之谜 = smoke log 显示 bug）|

**Step 0 关键发现**（来自 `docs/test-records/automated-smoke/1a8/00-investigation-report.md`）：
- ✅ **minimax M3 数据正常**——5-6 只基金每张图都正确返回
- ❌ **"1 funds" 是 smoke log 显示 bug**——`grep -c` 数行不数匹配，JSON 是单行所以 6 个匹配都算 1 行
- ✅ **修 smoke 脚本**：`grep -c` → `grep -o | wc -l`

**全 4 张图实际数据**（从 `docs/test-records/automated-smoke/1a7/api-test-output/` 翻出）：

| 截图 | 实际基金数 | 备注 |
|---|---|---|
| 2355-1.jpg | **6** | 余额宝 + 长城短债 + 鹏华纯债 + 国泰黄金 + 天弘纳指 + 摩根纳指 |
| 2355-2.jpg | **未保存**（见下）| 推测 5-6 |
| 2355-3.jpg | **5** | 华安黄金 + 华安港股 + 广发价值 + 易方达机器人 + 诺安A100 |
| 2356-1.jpg | **未保存**（minimax 502 限流）| 推测 5-6 |
| **小计** | **11+推测 ≈ 19** | — |

### 🟡 已完成（待 commit）
- `AiProperties.java` 加了 `Fallback` inner class（chat fallback config 字段）

### 🟡 解决方案已拍板（待实施）— **更新为方案 C**（折中版）
- **方案 C：按 imageCount 路由**
  - **1-2 张图：minimax OPENAI_CHAT primary（纯快路径，失败不 fallback）** — 80% 日常场景
  - **3+ 张图：豆包 OPENAI_RESPONSES primary + minimax fallback** — 1-2 张场景下豆包限流概率上升，保命路径
  - **vision 互为 fallback**：豆包失败 → 切 minimax；minimax 失败 → 不切（已尽力）
- **apiStyle 枚举（两个都实现）**：
  - `OPENAI_CHAT`（minimax：`/chat/completions`，`messages[]`，`image_url`）
  - `OPENAI_RESPONSES`（豆包：`/api/v3/responses`，`input[]`，`input_image`）
- **chat_history 监控字段**：`used_provider` (VARCHAR(20)) + `fallback_triggered` (TINYINT(1))
- **路由阈值配置**：`imageCountThreshold`（默认 2）
- **chat 端**：minimax M3 text primary（保持），DeepSeek V3 fallback（1a.8 暂不实现）

---

## 2. 当前问题

### 2.1 真实问题（必须解决）

#### 问题 A：minimax vision API 限流
- **症状**：1a.7 smoke 实证，4 张图 1 张（2356-1）返 HTTP 502
- **根因**：minimax M3 vision 端**限流阈值较低**
- **影响**：vision 端到端仅 75%（3/4 = 75%）通过
- **用户要求**：4/4 = 100% 才算 1a 闭环

#### 问题 B：4 张图 = 4 次串行 API 调用 = 高频请求
- **症状**：即使 minimax 不限流，连续 4 次 vision API 也容易触发速率限制
- **根因**：当前架构 = 1 张截图 = 1 次 vision API 调用，无 cache 无 batch
- **影响**：单用户上传 4 张图 = 4 次串行调用，触发限流概率高

#### 问题 C：限流导致业务失败
- **症状**：1 张图解析失败 = 该张图 confirm 时也失败（无 ParsedAsset 数据）
- **影响**：用户体验"4 张图只能入库 3 张"或"重试 N 次才能成功"

### 2.2 次要问题

#### 问题 D：chat_history 无 fallback 字段
- **现状**：chat_history 没有 `used_provider` / `fallback_triggered` 字段
- **影响**：无法审计"这条消息是哪个 AI 回答的"（调试用）
- **优先级**：低（1a.8 不实施，1a.9 再加）

#### 问题 E：当前 backend 没有 retry / circuit breaker
- **现状**：`VisionModelClient.callRaw()` 单次调用，失败即抛
- **影响**：瞬时网络抖动 = 直接失败
- **优先级**：低（豆包稳定后再说）

### 2.3 已排除的问题（Step 0 调查证实）

| 假设 | 实际 | 证据 |
|---|---|---|
| ❌ minimax 模型能力问题 | ✅ **5-6 只基金都正确返回** | 翻 1a.7 api-test-output JSON |
| ❌ JSON 抽取 bug | ✅ **extractFirstJsonObject 工作正常** | 6/5 只基金都正确进入 `data.categories[].funds[]` |
| ❌ prompt 设计问题 | ✅ **prompt 设计合理** | minimax 按 prompt 输出正确结构 |
| ❌ 数据库丢失数据 | ✅ **数据正常**（wipe 是测试副作用） | docker compose 之前数据有 |

---

## 3. 解决方案（一致同意）

**方案 F：豆包 primary + Caffeine cache**（最小可行）

### 3.1 架构对比

| 维度 | 当前 | 目标（方案 F）|
|---|---|---|
| **vision primary** | minimax M3（限流）| **豆包 doubao-seed-1-8-251228**（字节自家，限流宽松）|
| **vision fallback** | 无 | 暂不实现（豆包稳定后再说）|
| **chat primary** | minimax M3 text | minimax M3 text（保持）|
| **chat fallback** | 无 | DeepSeek V3（1a.8 暂缓）|
| **cache** | 无 | **Caffeine 本地 cache**（SHA-256(file) 为 key）|
| **retry** | 1 次（smoke 脚本层）| 应用代码层 retry（豆包稳定后）|
| **circuit breaker** | 无 | 应用代码层 CB（豆包稳定后）|

### 3.2 vision 端详细设计

```text
[用户上传截图]
     │
     ▼
[ScreenshotController.upload]
     │ (file stored, fileId returned)
     ▼
[用户点击"入库"]
     │
     ▼
[ScreenshotService.parse]
     │
     ▼
[计算 SHA-256(file) hash]
     │
     ▼
[Caffeine cache lookup by hash]
     │           │
   HIT         MISS
     │           │
     ▼           ▼
[返回缓存]   [VisionModelClient.callRaw]
                 │
                 ▼
             [apiStyle = OPENAI_RESPONSES]
                 │ (豆包 OpenAI SDK /responses schema)
                 ▼
             [POST ark.cn-beijing.volces.com/api/v3/responses]
                 │
                 ▼
             [Caffeine cache.put(hash, rawResponse)]
                 │
                 ▼
            [extractFirstJsonObject + mapToParsedAsset]
                 │
                 ▼
            [返回 ParsedAsset 给用户]
```

### 3.3 实施步骤（待后续 session 执行）

1. **application.yml 切 base-url 到豆包**（5 min）
2. **VisionModelClient 加 `apiStyle` 枚举**（OPENAI_CHAT / OPENAI_RESPONSES）（1.5h）
3. **豆包 OpenAI Responses API 实现**（`/api/v3/responses`，`input` 而非 `messages`，`input_image` 而非 `image_url`）（1.5h）
4. **Caffeine cache by SHA-256**（0.5h）
5. **smoke 重新跑 4/4 + minimax 不被触发**（0.5h）
6. **1a.8 验收报告**（5-segment 判定）（0.5h）

### 3.4 豆包 key 由用户填

```yaml
# fincontrol-backend/src/main/resources/application-local.yml
fincontrol:
  vision:
    base-url: https://ark.cn-beijing.volces.com/api/v3
    model: doubao-seed-1-8-251228
    api-key: sk-xxx  # <-- 用户填
    timeout-seconds: 300
```

文件在 `.gitignore` 里，不入仓。

### 3.5 不做的事（明确划清）

- ❌ **不实现完整 fallback 链**（用户明确说不需要）
- ❌ **不改 minimax 调用**（保持兼容，万一以后要回退）
- ❌ **不写 AiRouter 类**（直接改 VisionModelClient 切 base-url）
- ❌ **不加 retry / circuit breaker**（豆包限流更宽松，先观察）
- ❌ **不写 chat 端 DeepSeek fallback**（chat 没限流问题）
- ❌ **不加 chat_history 监控字段**（1a.9 再加）

---

## 4. 未完成部分

### 4.1 代码
- [ ] application.yml 切 base-url 到豆包（5 min）
- [ ] VisionModelClient 加 `apiStyle` 枚举（1.5h）
- [ ] 豆包 OpenAI Responses API 实现（1.5h）
- [ ] Caffeine cache by SHA-256（0.5h）
- [ ] smoke 重新跑 4/4 + minimax 不被触发（0.5h）

### 4.2 配置
- [ ] 用户填豆包 API key 到 `application-local.yml`
- [ ] 用户填 DeepSeek API key 到 `application-local.yml`（1a.9 再用）

### 4.3 文档
- [ ] 1a.8 验收报告（5-segment 判定，等实施完成）
- [ ] phase-1a.md 完成 1a.8 进度
- [ ] subphase-plan.md §2.8 标 ✅

### 4.4 风险
- **如果豆包也限流**（极小概率），1a.8 验收报告里**诚实记录**"已知限制"，1a.9 再加 minimax fallback
- **chat 端 DeepSeek fallback 暂不做**，依赖 minimax 限流频率（之前没出现过）

---

## 5. ⚠️ 警醒：测试副作用导致 2355-2 response 数据丢失

### 5.1 事件描述

**问题**：在做 1a.8 容器化验证（Layer 1 + Layer 2）时，跑了 `docker compose down -v`，**清除了 Docker volume `fincontrol-mysql-data`**，包括：
- `chat_history` 表里的所有 assistant 内容
- `asset_raw` / `asset_snapshot` 表的确认数据
- `fund_category_map` 的新插入

### 5.2 影响

| 数据 | 1a.7 smoke 跑完时 | 跑 `docker compose down -v` 后 |
|---|---|---|
| 2355-1 response 文件 | ✅ 在 `api-test-output/` | ✅ 还在（host 文件）|
| 2355-2 response 文件 | ✅ 在 `api-test-output/` | ❌ 丢失（数据在 chat_history 表里被 wipe）|
| 2355-3 response 文件 | ✅ 在 `api-test-output/` | ✅ 还在 |
| 2356-1 response 文件 | ✅ 在 `api-test-output/` | ✅ 还在 |
| 1a.7 smoke 写入的 asset_* 数据 | ✅ 在 MySQL | ❌ 全部丢失 |

### 5.3 教训

1. **`docker compose down -v` 会清空 volume 数据** — 在做容器化测试时容易忘记
2. **API response 写文件 ≠ 数据持久化** — 我们的 smoke 脚本把 response 写到 host 文件，但 chat_history 表里的副本随 volume 一起 wipe 了
3. **验证环境与生产环境应该隔离** — 测试容器时应该用 test profile + 独立 volume，不应影响 1a.7 的真实数据
4. **1a.8 smoke 重跑时需要重新产生所有数据** — 包括 upload + parse + confirm 4 张图

### 5.4 改进措施（1a.8 实施时）

- [ ] `docker-compose.yml` 加 `fincontrol-mysql-test` 独立 service（端口 3308，避免影响 3306 数据）
- [ ] smoke 脚本加 `--no-volumes` 选项（明确 wipe 前提示用户）
- [ ] 重要数据持久化在 `api-test-output/`（host 文件）作为唯一可信源
- [ ] 1a.8 实施时**先备份**现有 chat_history 和 asset_* 数据，再做容器化测试

---

## 6. 关联文档

- 工作计划：[`2026-07-18_phase1a8-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-work-plan.md)
- 验收计划（待用）：[`2026-07-18_phase1a8-acceptance-plan.md`](2026-07-18_phase1a8-acceptance-plan.md)
- Step 0 调查报告：[`docs/test-records/automated-smoke/1a8/00-investigation-report.md`](../automated-smoke/1a8/00-investigation-report.md)
- Phase 1a 总体进度：[`phase-1/checklists/phase-1a.md`](../../../phase-1/checklists/phase-1a.md)
- 子阶段总览：[`phase-1/subphase-plan.md`](../../../phase-1/subphase-plan.md)

---

**报告结束** | 撰写：刘博丞 | 撰写时间：2026-07-18 11:47 | 状态：🟡 进行中
