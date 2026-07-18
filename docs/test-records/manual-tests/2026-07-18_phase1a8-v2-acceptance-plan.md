# Phase 1a.8 v2 验收计划（解析数据正确性 + DedupEngine 真实触发）

**计划日期**：2026-07-18
**配套 v2 work plan**：[`2026-07-18_phase1a8-v2-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-v2-work-plan.md)
**配套 v1 work plan**：[`2026-07-18_phase1a8-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-work-plan.md)（架构已交付）
**v1 真实结果**：[`2026-07-18_phase1a8-acceptance-report.md`](2026-07-18_phase1a8-acceptance-report.md)（4/4 PASS 是 code=0 假象，**数据正确性 FAIL**）

**v2 目标**：1a.8 v2 修解析数据正确性，**保识别精度**（不约束大类命名，由 minimax 自由发挥）+ **DedupEngine 真实触发** + **真实数据对照 fixture**。

---

## 0. v2 验收判据（基于 5 段式 + 你提供的真实数据）

| 段 | v1 状态（**code=0 PASS**）| v2 目标（**数据正确性**）|
|---|---|---|
| BUSINESS | ✅ 51/51 PASS（mock 单测，code=0）| ✅ 真实数据 fixture：4 张图 → `fund_count == 18`（17 + 1 余额宝）+ 7 大类齐全 |
| CONTRACT | ✅ 15 endpoints 沿用 v1 | ✅ 不动 |
| READ_SQL | ✅ schema 增量 | ✅ db-schema.sql §6 prompt_versions v2.0 应用 |
| PRODUCTION | ⚠️ **code=0 PASS**（minimax 漏 ≥ 60% 基金）| ✅ **真实 PASS**：4 张图重跑 → 17 只 + 1 余额宝 全识别 + 总资产 7,884.68 ± 0.01 + DedupEngine 真实去重 |
| COVERAGE | ✅ ≥ 60% | ✅ ≥ 60% |

**v2 关键验收**：**数据正确性 = fund_count == 18 + total_asset == 7884.68**（你提供的真实值），而不是 `code=0`。

---

## 1. 测试用例（v2 升级）

### 1.1 T-V2-01：真实数据单测（自动）
- **目标**：用 4 张真实图做 fixture，断言 18 只基金全识别
- **步骤**：
  1. 把 4 张真实图（`uploads/screenshots/{1a91d2d3, 1be2796f, 5b77e218, 7eb709c6}.jpg`）作为 fixture
  2. 跑 ScreenshotServiceTest.parse_realAlipay_returnsAllFunds：
     - 喂入 1a91d2d3...jpg（7884.68 元那张）
     - 断言 parsed.funds.size() == 18（含 1 余额宝）
     - 断言 parsed.totalAsset == 7884.68 ± 0.01
     - 断言 7 大类齐全（余额类/货币类/固收类/商品类/A股权益类/海外权益类/港股/大中华类）
- **预期**：
  - 若 minimax 漏识别 → 测试 FAIL → 真实 BUG 暴露
  - 若 minimax 全识别 → 测试 PASS → v2 成功

### 1.2 T-V2-02：DedupEngine 真实触发（自动）
- **目标**：验证 DedupEngine 在 ScreenshotService.parse 中真实调用
- **步骤**：
  1. 喂入同一张图 parse 2 次
  2. 第 1 次 fund_count = N
  3. 第 2 次 fund_count == N（无重复）
  4. 断言 DedupEngine 至少被调用 1 次
- **预期**：PASS（你指出的"前后有重复"是 DedupEngine 触发场景）

### 1.3 T-V2-03：4 张图重跑 e2e（半自动 + 人工对照）
- **目标**：真实 e2e 跑通 4 张图，OCR 日志全保存
- **步骤**：
  1. 重启后端（用 v2 prompt）
  2. 4 张图 upload + parse
  3. OCR 日志自动保存 raw response
  4. **人工对照**（你 + 我）：
     - 每张图 fund_count == 18？
     - 每只基金名称、金额、收益与你的明细一致？
     - DedupEngine 是否合并了"前后重复的"行？
- **预期**：4 张图 fund_count == 18 + DedupEngine 真实去重 + OCR 日志 4 个文件

### 1.4 T-V2-04：豆包 fallback 路径（不强制 v2 必做，留 1a.9）
- **目标**：验证豆包 OPENAI_RESPONSES 端点真实工作
- **步骤**（v2 可选）：改 application-local.yml minimax key 为 REPLACE_ME → 4 张图并发 → 应切豆包
- **预期**：fallback 路径真实触发，chat_history.used_provider=doubao + fallback_triggered=1
- **v2 决策**：1a.8 v2 优先做数据正确性（步 1-3），fallback 留 1a.9

---

## 2. 真实数据对照清单（你提供的 ground truth）

| # | 大类 | 真实基金名称 | 持仓金额（元）| 累计收益（元）|
|---|---|---|---|---|
| 1 | 余额类 | 余额宝 | 320.85 | +1.89 |
| 2 | 货币类 | 中加货币E | 796.32 | +2.32 |
| 3 | 固收类 | 长城短债债券A | 454.58 | +4.58 |
| 4 | 固收类 | 鹏华纯债债券D | 435.82 | +1.62 |
| 5 | 固收类 | **安信新价值灵活配置混合A** | 298.45 | -1.55 |
| 6 | 商品类 | 国泰黄金ETF联接A | 923.16 | -129.84 |
| 7 | 商品类 | **国泰黄金ETF联接C** | 564.86 | -45.25 |
| 8 | 商品类 | 华安黄金ETF联接C | 156.48 | -26.27 |
| 9 | A 股权益类 | 诺安中证A100指数A | 1267.84 | +46.84 |
| 10 | A 股权益类 | **国泰海通中证500指数增强C** | 289.87 | -0.13 |
| 11 | A 股权益类 | 广发价值回报混合C | 113.84 | -6.16 |
| 12 | A 股权益类 | 易方达机器人ETF联接C | 108.20 | +8.20 |
| 13 | A 股权益类 | 诺安中证A100指数C | 105.57 | +5.57 |
| 14 | 海外权益类 | 天弘纳斯达克100指数(QDII)A | 633.32 | +51.32 |
| 15 | 海外权益类 | 天弘纳斯达克100指数(QDII)C | 308.82 | +33.82 |
| 16 | 海外权益类 | 摩根纳斯达克100指数(QDII)A | 545.48 | +35.48 |
| 17 | 海外权益类 | 招商纳斯达克100ETF联接(QDII)C | 184.69 | +24.69 |
| 18 | 港股/大中华类 | 易方达恒生科技ETF联接(QDII)C | 255.42 | -8.58 |
| 19 | 港股/大中华类 | 华安香港精选股票(QDII) | 121.11 | +1.11 |
| **总** | | **17 只基金 + 1 余额宝 = 18 行** | **7,884.68** | |

**v2 OCR 真实对照**：
- 4 张图 parse → `fund_count == 18`（17 + 1 余额宝）
- `total_asset == 7884.68 ± 0.01`（针对 7884.68 元那张图）
- DedupEngine 把 3 张图里"前后重复的"合并

---

## 3. 阶段化 prompt 路线

| 阶段 | prompt 内容 | 解决什么 | 何时 |
|---|---|---|---|
| **v1.0 (现状)** | 硬指定 6 大类 + 余额类 | 框架约束，minimax 自由发挥易乱 | ✅ 已部署 |
| **v2.0 (本次)** | **不约束大类**——只强调"列全所有基金 + 名称/金额/收益齐全 + 大类你自己能区分的标签" | **保识别精度** | 🟡 本次 v2 commit |
| **v3.0 (1a.9)** | 强制 6 大类回归（**用 v2 训练出的"精度优化"经验** + 具体规则）| **保分类一致** | 1a.9 |

**v2 prompt 内容**（db-schema.sql §6 prompt_versions）：
```yaml
screenshot_parser v2.0:
  "你是支付宝资产截图识别助手。任务: 提取截图上**所有**基金/资产信息。

严格规则:
1. **列出每一只基金/资产** — 不能合并、不能省略
2. 每只基金必填 4 个字段:
   - fund_name (基金完整名称)
   - amount (持仓金额, 元)
   - profit (累计收益, 元)
   - category_name (大类名称, 你自己定义合理标签)
3. 类别名称用你认为最准确的: '货币' '固收' '股票' '混合' 'QDII' '海外股票' '港股' '商品' '余额' 等皆可
4. 不要归类'其他'或留空 — 任何识别出的基金必须有一个类别标签
5. snapshot_date 提取截图日期 (YYYY-MM-DD)
6. total_asset 是截图显示的总资产 (元)

返回结构化 JSON, 不要 Markdown, 不要解释。"
```

---

## 4. v2 验收报告模板（实施后产出）

`docs/test-records/manual-tests/2026-07-18_phase1a8-v2-real-data-check.md`：

```markdown
# 1a.8 v2 真实数据对照（2026-07-18 HH:MM-HH:MM）

## OCR 日志
- docs/test-records/ocr-results/2026-07-18/20260718_HHMMSS_{fileId}_minimax.json × N

## 4 张图 parse 真实数据 vs 你提供 ground truth
| # | fileId | totalAsset parse | totalAsset 真实 | 差 | fund_count parse | fund_count 真实 | 差 | DedupEngine 合并行 |
| ... |

## 漏识别基金
- 图 1: 国泰海通中证500指数增强C（minimax 漏识别）
- 图 2: ...

## 错位合并
- 图 3: 易方达机器人ETF联接C 错并入 A 股权益类（应单列）

## 5 段式判定
- BUSINESS: ✅/❌ 真实数据 fixture PASS
- READ_SQL: ✅ prompt_versions v2.0 应用
- PRODUCTION: ✅/❌ 4 张图 fund_count == 18 / DedupEngine 真实去重
- COVERAGE: ✅ ≥ 60%

## 1a.8 v2 真实闭环
✅/❌ 18 只基金全识别 + DedupEngine 工作 + 总资产 7,884.68
```

---

## 5. 失败兜底（v2 失败怎么办）

如果 v2 真实数据对照仍 FAIL（minimax v2 prompt 仍漏识别）：

| 兜底 | 内容 |
|---|---|
| **方案 A**：换豆包 OPENAI_RESPONSES 作为 vision primary（豆包的多模态可能比 minimax M3 更强）| 改 `fincontrol.ai.vision.doubao.api-key` + 改 `fincontrol.ai.vision.minimax.timeout-seconds=1`（让 minimax 立即超时走 fallback）|
| **方案 B**：换 1.0 的 prompt 提示词（"请列出 6 大类..."强制分类）| 改 `screenshot_parser v2.0` 内容 |
| **方案 C**：放弃 1 张图 parse，4 张图合并 → 让 minimax 处理更复杂 schema | 改 `imageCount > 2` 走豆包，让 minimax 只处理 1-2 张图 |

> 这些兜底都是 1a.9 + Phase 1b 范围。1a.8 v2 先做"保识别精度"版，1a.9 再"保分类一致"。

---

## 6. 1a.8 v2 收尾

- 1 个 commit 做完 7 步（OCR 日志 + prompt v2 + DedupEngine + 真实数据单测 + 4 张图重跑 + 1a.8.6 状态回滚 + 报告）
- 切 ACT 模式后立即执行
- 时间预算：1.5-2 小时
- 1a.8 v2 真实闭环标志：4 张图 parse 真实 fund_count == 18 + total_asset == 7884.68 ± 0.01
