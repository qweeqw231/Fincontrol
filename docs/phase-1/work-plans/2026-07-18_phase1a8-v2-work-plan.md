# Phase 1a.8 v2 工作计划（解析数据正确性 + DedupEngine 真实触发）

**计划日期**：2026-07-18（v2 追加在 v1 之后）
**配套 v1**：[`2026-07-18_phase1a8-work-plan.md`](2026-07-18_phase1a8-work-plan.md)（架构已完成）
**配套 v1 验收**：[`2026-07-18_phase1a8-acceptance-report.md`](../../test-records/manual-tests/2026-07-18_phase1a8-acceptance-report.md)（code=0 真 PASS，数据准确性 FAIL）
**配套 v2 验收**：[`2026-07-18_phase1a8-v2-acceptance-plan.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v2-acceptance-plan.md)
**v1 真实状态**：
- ✅ 架构就绪（VisionModelClient 双 provider + AiRouter + resilience4j + Caffeine）
- ✅ code=0 真 PASS（4/4 vision + 5/5 chat）
- ❌ **数据正确性 FAIL**（minimax 漏 ≥ 60%：海外权益 4 只全漏 + 港股 2 只全漏 + 余额类 1 只漏 + A 股 5 错合并 4）

**v2 目标**：在 v1 架构基础上，**保识别精度**（让 minimax 列全 17 只基金 + 1 余额宝 + 7 大类齐全），**真实数据对照 fixture** + **DedupEngine 真实触发**。

**v3 目标（1a.9）**：在前 v2 经验基础上，**回归大类分类**（用具体规则如"QDII 归海外权益 / 易方达恒生归港股"等）。

---

## 0. 阶段化 prompt 路线（采纳你的洞察）

| 阶段 | prompt 内容 | 解决什么 | 何时 |
|---|---|---|---|
| **v1.0 (现状)** | 硬指定 6 大类 + 余额类 | 框架约束，minimax 自由发挥易乱 | ✅ 已部署（v1 commit） |
| **v2.0 (v2 commit)** | **不约束大类命名**——只强调"列全所有基金 + 名称/金额/收益齐全 + 大类你自己能区分的标签" | **保识别精度**（你的核心要求） | 🟡 本次 v2 commit |
| **v3.0 (1a.9)** | 强制 6 大类回归（**用 v2 训练出的"精度优化"经验** + 具体规则）| **保分类一致** | 1a.9 |

## 1. v2 计划范围

### 1.1 范围（in-scope）
- ✅ **prompt v2 改造**（db-schema.sql §6 prompt_versions）
  - 删硬指定大类，**只强调"列全所有基金 + 名称/金额/收益齐全"**
  - minimax 大类标签自由发挥（"货币/固收/股票/混合/QDII/海外股票/港股/商品/余额"等）
  - 不约束归类，**只保证 18 行基金（17 + 余额宝）全部识别**
- ✅ **OCR 识别日志**（`docs/test-records/ocr-results/{date}/`）
  - 每次真实 parse 后保存 raw response + parsed JSON + timestamp + usedProvider + fileId
  - 文件名：`{timestamp}_{fileId}_{usedProvider}.json`
  - 用于**人工对照标准答案 + 后续 v3 prompt 改进**
- ✅ **DedupEngine 真实触发**
  - 1a.3 已建 `service/DedupEngine.java`
  - 你指出的"前后有重复的"是 DedupEngine 触发场景
  - 检查 DedupEngine 是否被 ScreenshotService.parse 调用，未调则接入
- ✅ **真实数据单测 fixture**（`ScreenshotServiceTest.parse_realAlipay_returnsAllFunds`）
  - 用 4 张真实图（含 7884.68 元那张 + 3 张基金图）做 fixture
  - 断言 `fund_count == 18`（17 + 1 余额宝）+ DedupEngine 工作
- ✅ **4 张图重跑真实 e2e** + OCR 日志 + 人工对照（你给的真实明细 17 只 + 1 余额宝）
- ✅ **诚实回滚 1a.8 4/4 PASS 假象**
  - `phase-1a.md` 1a.8.6 改回 ⚠️（数据正确性待 1a.8 v2 修）
  - `2026-07-18_phase1a8-vision-routing-results.md` 加"4/4 PASS 是 code=0 假象"段
  - `2026-07-18_phase1a8-acceptance-report.md` PRODUCTION 段改回 ⚠️ + 1a.8.6 ⚠️
  - 1a.8 work plan v1.0 status 改回 ⚠️（不重新规划 v1，由 v2 接管）

### 1.2 范围外（out-of-scope，留 1a.9 或以后）
- ❌ 大类强制分类（v3 处理）
- ❌ 6 大类目标比例校验（Phase 2 资产配置逻辑）
- ❌ UI confirm 面板（Phase 1b）
- ❌ 豆包 OPENAI_RESPONSES schema 完整验证（1a.9 主动注入故障）
- ❌ TextAiClient DeepSeek fallback 调用实现（1a.9）

## 2. 7 步实施计划

| 步 | 内容 | 时间 | 关联 |
|---|---|---|---|
| **1** | 建 OCR 日志目录（gitignored）`docs/test-records/ocr-results/2026-07-18/` | 1 min | gitignore |
| **2** | 改 `ScreenshotService.parse` 加 raw response 写盘逻辑 | 10 min | Java 改动 |
| **3** | 改 `db-schema.sql §6 prompt_versions` 中 `screenshot_parser` 为 v2.0 内容 | 5 min | SQL seed |
| **4** | 检查 + 启用 `DedupEngine`（1a.3 已建）在 `ScreenshotService.parse` 中调用 | 20 min | Java 改动 |
| **5** | 改 `ScreenshotServiceTest` 加 `parse_realAlipay_returnsAllFunds` 真实数据 fixture | 30 min | 单测 |
| **6** | 重启后端 + 4 张图重跑真实 e2e + OCR 日志 + 人工对照 17 只 + 1 余额宝 | 30 min | e2e + 你对照 |
| **7** | 改 phase-1a.md 1a.8.6 + acceptance-report.md PRODUCTION 段 + routing-results.md 加"code=0 假象"段 | 10 min | doc 落盘 |
| **总** | | **~1.5-2h** | |

## 3. 验收段（详细见 v2-acceptance-plan.md）

| 段 | DoD |
|---|---|
| **BUSINESS** | ScreenshotServiceTest.parse_realAlipay_returnsAllFunds PASS（fund_count == 18，7 大类齐全）|
| **CONTRACT** | 不动（沿用 v1） |
| **READ_SQL** | db-schema.sql §6 prompt_versions.screenshot_parser = v2.0（ON DUPLICATE KEY UPDATE）|
| **PRODUCTION** | 4 张图重跑 → 真实数据与人工对照清单对比：**漏 0 只**（17 只 + 1 余额宝全识别）+ DedupEngine 真实去重 |
| **COVERAGE** | JaCoCo ≥ 60% |

## 4. 阶段化 prompt 设计（采纳你的洞察）

```sql
-- db-schema.sql §6 prompt_versions UPDATE 'screenshot_parser' = v2.0
INSERT INTO prompt_versions (prompt_name, prompt_content, version, change_reason) VALUES
('screenshot_parser',
 '你是支付宝资产截图识别助手。任务: 提取截图上**所有**基金/资产信息。

严格规则:
1. **列出每一只基金/资产** — 不能合并、不能省略
2. 每只基金必填 4 个字段:
   - fund_name (基金完整名称)
   - amount (持仓金额, 元)
   - profit (累计收益, 元)
   - category_name (大类名称, 你自己定义合理标签)
3. 类别名称用你认为最准确的: ''货币'' ''固收'' ''股票'' ''混合'' ''QDII'' ''海外股票'' ''港股'' ''商品'' ''余额'' 等皆可
4. 不要归类''其他''或留空 — 任何识别出的基金必须有一个类别标签
5. snapshot_date 提取截图日期 (YYYY-MM-DD)
6. total_asset 是截图显示的总资产 (元)

返回结构化 JSON, 不要 Markdown, 不要解释。',
 'v2.0',
 '1a.8 v2 修：保识别精度，不约束大类命名（v3 在 1a.9 回归大类分类）')
ON DUPLICATE KEY UPDATE
  prompt_content = VALUES(prompt_content),
  change_reason = VALUES(change_reason);
```

## 5. 你提供的真实数据（OCR 对照 fixture ground truth）

> 注：4 张图（`uploads/screenshots/` 已存）+ 你描述的 7884.68 元那张是其中之一

| 大类 | 真实只数 | 真实基金名称（按金额排序）|
|---|---|---|
| 余额类 | 1 | 余额宝（320.85）|
| 货币类 | 1 | 中加货币E（796.32）|
| 固收类 | 3 | 长城短债债券A（454.58）、鹏华纯债债券D（435.82）、**安信新价值灵活配置混合A**（298.45）|
| 商品类 | 3 | 国泰黄金ETF联接A（923.16）、**国泰黄金ETF联接C**（564.86）、华安黄金ETF联接C（156.48）|
| A 股权益类 | 5 | 诺安中证A100指数A（1267.84）、天弘纳斯达克100（不算 A 股）、**国泰海通中证500指数增强C**（289.87）、广发价值回报混合C（113.84）、易方达机器人ETF联接C（108.20）、诺安中证A100指数C（105.57）|
| 海外权益类 | 4 | 天弘纳斯达克100指数(QDII)A（633.32）、天弘纳斯达克100指数(QDII)C（308.82）、摩根纳斯达克100指数(QDII)A（545.48）、招商纳斯达克100ETF联接(QDII)C（184.69）|
| 港股/大中华类 | 2 | 易方达恒生科技ETF联接(QDII)C（255.42）、华安香港精选股票(QDII)（121.11）|
| **总计** | **17 只 + 1 余额宝 = 18 行** | 总资产 7,884.68 元 |

**v2 prompt 真实数据对照**（必须 fund_count == 18 + total_asset == 7884.68 ± 0.01）：

## 6. 1a.8 v2 commit 范围

1 个 commit 做完 7 步：
- `fix(1a.8-v2): OCR log + prompt v2 (no class constraint) + DedupEngine + real-data fixture`
- 4 个文件变更：
  - `db-schema.sql` (§6 prompt_versions v2.0)
  - `service/ScreenshotService.java` (raw response 写盘 + DedupEngine 调用)
  - `test/.../ScreenshotServiceTest.java` (parse_realAlipay_returnsAllFunds)
  - `docs/test-records/manual-tests/2026-07-18_phase1a8-v2-real-data-check.md` (人工对照报告)

## 7. 1a.8 v2 风险

| 风险 | 缓解 |
|---|---|
| minimax v2 prompt 仍漏 1-2 只 | OCR 日志 + 你人工对照，每只基金逐行对比 |
| DedupEngine 接入 bug 导致去重错误 | 单测对比 4 张图重跑前后 fund_count |
| 真 4 张图 fixture 数量 < 18 | 4 张图只覆盖 1 张 7884.68 + 3 张"3 个基金"等——可能没覆盖全部 17 只；1a.9 用历史测试图扩展 fixture |
| 用户不切 ACT 模式 | 落盘 doc + 你切 ACT 后立即执行 7 步 |
| prompt v2 改了后 v1 单测要更新 | ScreenshotServiceTest 1a.2 时期的单测 5/5（mock 假 JSON）可能因 v2 prompt 改 string 内容失败——重写为 mock v2 prompt 的 JSON |

## 8. 1a.8 v2 必办（用户确认后切 ACT 立即执行）

1. 改 db-schema.sql §6 prompt_versions v2.0
2. 改 ScreenshotService.java：
   - 加 raw response 写盘（解析后立即 copy 到 OCR 日志）
   - 接入 DedupEngine.dedupeByFundName(funds)
3. 改 ScreenshotServiceTest.java：
   - 删 v1 mock 测试的硬编码 JSON（与 v1 prompt 绑定）
   - 加 `parse_realAlipay_returnsAllFunds`（用你提供的 4 张图 fixture）
4. 重启后端 + 4 张图重跑 + OCR 日志 + 人工对照
5. 改 phase-1a.md 1a.8.6 改回 ⚠️ + 报告 PRODUCTION 改 ⚠️ + routing-results 加"code=0 假象"段
6. commit + push

> 切 ACT 模式后我立即执行 8 步。预计 1.5-2 小时。
