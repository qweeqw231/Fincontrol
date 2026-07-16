# minimax vision/text Prompt Bean 拆分设计

**版本**：v1.0
**日期**：2026-07-17
**作者**：刘博丞
**状态**：✅ 已落定（用于指导 1a.6 AI 顾问实现）

> 本设计回答：「minimax 替代 deepseek」不仅仅是 OCR，**`/api/chat/send` 同样使用 minimax M3，但 prompt 与视觉任务完全独立**。两者必须拆为 2 个独立 Bean，避免上下文污染。

---

## 1. 背景

`docs/phase-0/decisions.md` 早期方案：DeepSeek 单模型承担"OCR + 顾问对话"。
当前实际部署：minimax M3 多模态统一承担**两类任务**，但两类 prompt **完全不同**：

| 端点 | 模型 | 任务类型 | 上下文 | Prompt 关键诉求 |
|---|---|---|---|---|
| `POST /api/screenshot/parse` | minimax M3 多模态 | vision | 截图 + 输出 JSON Schema | "按 schema 输出 6 大类 + 余额类" |
| `POST /api/screenshot/reparse` | minimax M3 多模态 | vision | 同上 | 同上 |
| `POST /api/chat/send` | minimax M3 文本 | text-only | 用户问题 + 投资规则 + 历史 | "main_loop/garbage_loop 路由 + 严格按规则回答" |

> ⚠️ **关键风险**：如果两类调用共用同一个 `ChatClient` / `PromptTemplate`，文本对话会带上视觉 schema，OCR 又会被对话历史污染 prompt。

---

## 2. 设计原则

1. **模型可以同源**（都调 minimax 平台），但 **Bean 必须拆分**；
2. **Prompt 文件必须拆分**（YAML / classpath 资源分别存放）；
3. **配置项必须拆分**（不同 `temperature`、不同 `maxTokens`、不同 `modelVersion`）；
4. **监控与日志标签必须拆分**（便于成本归因与排障）。

---

## 3. 推荐的 Bean 拆分

```text
fincontrol-backend/src/main/java/com/fincontrol/ai/
├── VisionAiClient.java          # 视觉 Bean：parse / reparse 专用
├── TextAiClient.java            # 文本 Bean：chat/send 专用
├── IntentClassifier.java        # 文本侧辅助：main_loop / garbage_loop 路由
├── AiProperties.java            # @ConfigurationProperties("fincontrol.ai")
└── AiConfig.java                # @Configuration，定义两个 Bean 的装配

src/main/resources/ai/
├── vision-prompt.yml           # 视觉 prompt（截图→JSON 分类）
└── text-prompt.yml             # 文本 prompt（main_loop / garbage_loop）
```

### 3.1 `VisionAiClient`（视觉）

- 仅注入 `AiProperties.vision` 子段；
- `temperature = 0.2`（低温度，结构化输出优先）；
- `maxTokens = 4096`（支持多分类明细）；
- `responseFormat = JSON_OBJECT`（严格 Schema）；
- `modelVersion = "minimax-m3-vision"`（带 vision 后缀的版本）；
- 暴露方法：`ParsedAsset parse(byte[] imageBytes, ParseHints hints)`。

### 3.2 `TextAiClient`（文本）

- 仅注入 `AiProperties.text` 子段；
- `temperature = 0.7`（更自然、更发散）；
- `maxTokens = 1024`（短回复）；
- `responseFormat = TEXT`（不强制 JSON）；
- `modelVersion = "minimax-m3-text"`（纯文本模型变体）；
- 暴露方法：`String chat(List<ChatMessage> history, String userRules)`。

### 3.3 `IntentClassifier`

- 纯文本分类（是否"投资决策类"问题）；
- 复用 `TextAiClient` 或独立 Bean；如独立则 `temperature = 0.1` 保证稳定性；
- 返回 `boolean isInvestmentRelated`。

---

## 4. 资源与配置示例

### 4.1 `application.yml`（仅展示 AI 段）

```yaml
fincontrol:
  ai:
    base-url: https://api.minimaxi.chat/v1
    api-key: ${MINIMAX_API_KEY:placeholder}
    vision:
      model: minimax-m3-vision
      temperature: 0.2
      max-tokens: 4096
      prompt-file: classpath:ai/vision-prompt.yml
    text:
      model: minimax-m3-text
      temperature: 0.7
      max-tokens: 1024
      prompt-file: classpath:ai/text-prompt.yml
```

### 4.2 `ai/vision-prompt.yml`

```yaml
template: |
  你是 FinControl 的资产截图识别助手。请按以下 JSON Schema 输出支付宝/微信等
  资产详情截图中的所有基金与余额：
  {
    "snapshot_date": "YYYY-MM-DD",
    "categories": [
      { "category_name": "...", "category_total": 数字, "category_percentage": 数字, "funds": [...] }
    ]
  }
  严格按 schema 输出；找不到的字段填 null。
```

### 4.3 `ai/text-prompt.yml`

```yaml
main_loop: |
  你是 FinControl AI 顾问。严格按用户的资产配置规则回答投资问题。
  不预测市场，不推荐具体买卖；建议以"是否需要月度校正"为收口。
  规则：{rules}

garbage_loop: |
  你是闲聊助手，不输出投资建议；只回答一般性问题。
```

---

## 5. 在现有代码中的落地

### 5.1 现状（待重构）

- `VisionModelClient` 已经存在（替代了原 `DeepSeekClient`），但可能与未来 text 调用混在一起；
- `ScreenshotService` 直接调用 `VisionModelClient`；
- 未来 1a.6 的 `ChatService` 即将引入，不能与 `VisionModelClient` 共用。

### 5.2 重构步骤

1. 新建 `com.fincontrol.ai` 包；

2. 拆分 `VisionModelClient` → `VisionAiClient` + `TextAiClient`；

3. Prompt 文件从硬编码移到 `ai/vision-prompt.yml` 和 `ai/text-prompt.yml`；

4. `ScreenshotService` 注入 `VisionAiClient`；

5. 1a.6 `ChatService` 注入 `TextAiClient` 与 `IntentClassifier`；

6. 测试：

   - 单测用 `MockBean` 替换 `VisionAiClient` / `TextAiClient`；
   - 集成测试用真实 minimax key 跑回归（与现有 `ScreenshotServiceTest` 流程一致）。

---

## 6. 与验收 / 计划的衔接

- 本设计在 1a.6 工作计划里作为"前置 Gate 0"；

- 1a.6 验收计划新增 2 个用例：

  - **A6-INT-01** `VisionAiClient.parse()` 与 `TextAiClient.chat()` 是不同 Bean，且 Prompt 文件路径不重叠；
  - **A6-INT-02** 修改 text 端 `temperature` 不影响视觉端。

- 1a.7 冒烟阶段会跑一次**双端端到端**：
  - 上传 1 张截图 → parse 成功 → ask 1 个投资问题 → chat 正确路由 main_loop。

---

## 7. 总结

- "minimax 替代 deepseek" 不只是工具替换，是**两套独立 prompt Bean**；
- 视觉与文本必须分离 Bean、配置、Prompt、日志；
- 1a.6 实施时严格按本设计走，避免上下文污染。
