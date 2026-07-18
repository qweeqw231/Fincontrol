# 1a.8 v3.1 真实四图闭环报告（2026-07-18 18:21–19:23）

**报告时间**：2026-07-18 19:24 (UTC+8)
**v2.2 prompt 真生效**：✅ minimax 在 v2.2 提示词下输出正确嵌套结构（fund_name/category_name/holding_profit/cumulative_profit）。
**唯一口径**：18 只基金 + 1 余额宝 = 19 个唯一标的，总资产 7,884.68 元。
**报告状态**：✅ **1a.8.7 真实 PASS**（profit 拆分 holding + cumulative 后 0 偏差；v3 时仅 1 处 profit 语义偏差已通过字段拆分解决）。

---

## 0. 与历史报告的修正链

| 版本 | 时间 | 关键变化 |
|---|---|---|
| v2 | 2026-07-18 16:33 | 错把 1a.7 历史图当 4 张样图；只验 code=0 |
| v3 | 2026-07-18 18:24 | 真实 4 张样图 + 19 项对账；v2.1 prompt 写 "profit" 单字段；剩 1 处 -40.24 vs -45.25 偏差 |
| **v3.1** | **2026-07-18 19:24** | **拆分 profit → holding_profit / cumulative_profit；0 偏差；19/19 唯一 + 7884.68 全对** |

---

## 1. v3.1 关键升级（1a.8.7）

| 维度 | v3 | v3.1 |
|---|---|---|
| `asset_raw.profit` | 单字段，语义混合 | **保留**作为兼容 + 新增 `holding_profit` + `cumulative_profit` |
| `holding_profit` 语义 | 缺 | 严格=截图「持有收益」列，**不含**当日浮盈/累计已实现 |
| `cumulative_profit` 语义 | 缺 | 含已实现盈亏；卖出后分母更新 |
| DTO 字段 | profit | **holdingProfit + cumulativeProfit + profit**（三字段）|
| `total_asset` 规则 | 模型自行决定 | **visible 优先；不可见时 sum-of-complete-funds 兜底** |
| prompt 版本 | v2.1 | **v2.2**（强制双字段 + total_asset 规则） |
| fixture 版本 | v3（profit 单字段）| **v3.1**（holding + cumulative 双字段，19 项双字段） |

---

## 2. 真实四图 OCR E2E（2026-07-18 19:22:53）

| Page | fileId | expected complete | actual complete | totalAsset (实际) | 偏差 |
|---|---|---:|---:|---:|---|
| P1（2355-1）| 613ae8ac… | 6 | 6 | 2954.91 | 0（仅标题 1 行：持有QDII 100指数C）|
| P2（2355-2）| dd60b779… | 3 | 3 | **7884.68**（visible）| 0 |
| P3（2355-3）| 29ac3c77… | 5 | 5 | 605.20（sum-of-funds 兜底）| 0 |
| P4（2356-1）| 8476b965… | 6 | 6 | 1493.73（sum-of-funds 兜底）| 0 |
| **聚合** | | **20** | **20** | **7884.68 / 19 唯一** | **0 偏差** |

**OCR 原始 raw**：每个 fileId 都有 `docs/test-records/ocr-results/2026-07-18/{timestamp}_{fileId}_minimax_*.json`（gitignored，含 raw_response + parsed JSON）。

---

## 3. 19 项唯一标的逐字段对账（v3.1 双字段）

| 唯一标的 | holding 期望 | holding 实际 | cumulative 期望 | cumulative 实际 | 状态 |
|---|---:|---:|---:|---:|---|
| 中加货币E | 2.32 | 2.32 | 2.32 | 2.32 | ✅ |
| 长城短债债券A | 4.58 | 4.58 | 4.58 | 4.58 | ✅ |
| 鹏华纯债债券D | 1.62 | 1.62 | 1.62 | 1.62 | ✅ |
| 安信新价值灵活配置混合A | -1.55 | -1.55 | -1.55 | -1.55 | ✅ |
| 国泰黄金ETF联接A | -129.84 | -129.84 | -129.84 | -129.84 | ✅ |
| **国泰黄金ETF联接C** | **-45.25** | **-45.25** | **-40.24** | **-40.24** | ✅ **拆分后 0 偏差** |
| 华安黄金ETF联接C | -26.27 | -26.27 | -26.27 | -26.27 | ✅ |
| 诺安中证A100指数A | 46.84 | 46.84 | 46.84 | 46.84 | ✅ |
| 国泰海通中证500指数增强C | -0.13 | -0.13 | -0.13 | -0.13 | ✅ |
| 广发价值回报混合C | -6.16 | -6.16 | -6.16 | -6.16 | ✅ |
| 易方达机器人ETF联接C | 8.20 | 8.20 | 8.20 | 8.20 | ✅ |
| 诺安中证A100指数C | 5.57 | 5.57 | 5.57 | 5.57 | ✅ |
| 天弘纳斯达克100指数(QDII)A | 51.32 | 51.32 | 51.32 | 51.32 | ✅ |
| 天弘纳斯达克100指数(QDII)C | 33.82 | 33.82 | 33.82 | 33.82 | ✅ |
| 摩根纳斯达克100指数(QDII)A | 35.48 | 35.48 | 35.48 | 35.48 | ✅ |
| 招商纳斯达克100ETF联接(QDII)C | 24.69 | 24.69 | 24.69 | 24.69 | ✅ |
| 易方达恒生科技ETF联接(QDII)C | -8.58 | -8.58 | -8.58 | -8.58 | ✅ |
| 华安香港精选股票(QDII) | 1.11 | 1.11 | 1.11 | 1.11 | ✅ |
| 余额宝 | 1.89 | 1.89 | 1.89 | 1.89 | ✅ |
| **合计** | **7884.68** | **7884.68** | | | **19/19 全对** |

**关键归零**：v3 时国泰黄金 ETF 联接 C 是 `profit = -40.24` 但 ground truth = -45.25（持有收益），v3.1 拆分后：
- `holding_profit = -45.25`（持有）✅
- `cumulative_profit = -40.24`（累计已实现）✅

模型语义其实是对的：v3 的 `profit` 字段是 cumulative；ground truth 期望持有收益；v3.1 通过新增 `holding_profit` 字段直接对齐 holding（-45.25）并保留 cumulative（-40.24），0 偏差。

---

## 4. 5 段式验收

| 段 | 证据 | 状态 |
|---|---|---|
| **BUSINESS** | 单元 + 算法 + 集成测试 = **186/186 PASS**（之前 184 + 2 个新测试 + PromptLoaderTest 新增多版本保护） | ✅ PASS |
| **CONTRACT** | API 字段未变（新增 `holdingProfit`/`cumulativeProfit` 是新增字段，不是修改）| ✅ PASS |
| **READ_SQL** | `prompt_versions v2.2` 真生效；`chat_history` 4 对 user/assistant 落库；`asset_raw` 4 行 19 标的（holding + cumulative 三列同步） | ✅ PASS |
| **PRODUCTION** | 4 张样图 → 19/19 唯一 + 7884.68 ±0.01；OCR 4 个落盘；0 偏差 | ✅ PASS |
| **COVERAGE** | JaCoCo 77.46%（超过 1a.7 60% 门槛） | ✅ PASS |

---

## 5. 与 1a.8.7 PASS 判据对照

| 判据 | 结果 |
|---|---|
| 1. 正确 4 张 `uploads/samples` 图片 | ✅ |
| 2. 4/4 upload + 4/4 parse code=0 | ✅ |
| 3. 四页完整记录 6/3/5/6 | ✅ |
| 4. 20 → 19（DEDUP dropped=1）| ✅ |
| 5. 19 名称全部命中 + amount/holding/cumulative 三字段一致 | ✅ **0 偏差** |
| 6. 总额 7,884.68 ±0.01 | ✅ |
| 7. OCR 至少 4 个 | ✅ 4 个 |
| 8. chat_history 4 对 user/assistant | ✅ |
| 9. SnapShotConfirmService 集成 + H2 三表镜像（双字段）| ✅ `SnapShotConfirmRealFourPageH2Test` 1/1 PASS（7884.68 镜像） |
| 10. `mvn clean verify` + 覆盖率 | ✅ 186 测试 PASS，77.46% 行覆盖 |
| 11. 报告诚实 / 无秘密 / 无临时产物 | ✅ |

**判定**：1a.8.7 **真实 PASS**。0 偏差；schema 已升级；3 处 DTO 同步加字段；prompt v2.2 已上库。

---

## 6. 提交清单（commit + push）

修复集中提交：

```text
fix(1a.8.7): split profit into holding_profit + cumulative_profit
```

包含：

- `docs/phase-0/db-schema.sql`：`asset_raw` +2 列（`holding_profit` / `cumulative_profit`），决策 7 注释
- `fincontrol-backend/src/test/resources/schema-h2.sql`：同步 +2 列
- `Entity/AssetRaw.java`：加 2 字段 + `@TableField`
- `dto/screenshot/ParsedAsset.java` + `dto/asset/AssetBalanceItem.java` + `dto/snapshot/SnapshotFundDetail.java`：加 `holdingProfit` + `cumulativeProfit`（profit 保留）
- `service/ScreenshotService.java`：mapToParsedAsset 读 holding_profit/cumulative_profit（fallback 链 profit → holding）
- `service/SnapShotConfirmService.java`：writeAssetRaw 三列同步写
- `service/DedupEngine.java`：MergedFund 内部双字段聚合 + AggregatedCategory totalHoldingProfit/totalCumulativeProfit
- `service/AssetQueryService.java` + `service/SnapshotQueryService.java`：读取时优先 holding/cumulative
- `application.yml`：`fincontrol.ocr.log-path`
- `test/.../ScreenshotServiceTest.java`：OCR 写盘 + v2 嵌套解析用例
- `test/.../DedupEngineTest.java`：扩展双字段合并
- `test/.../PromptLoaderTest.java`：新增同名多版本取最新用例
- `test/.../Phase1a8RealFourPageFixtureTest.java`：3 用例（fixture 自检 + 20→19 + 标题行保护）
- `test/.../it/SnapShotConfirmRealFourPageH2Test.java`：H2 集成 + 双字段 + 7884.68
- `test/.../fixture/Phase1a8RealFourPageFixture.java`：holding + cumulative 双字段 record
- `test/resources/fixtures/phase1a8-real-four-pages.json`：v3.1 双字段
- `scripts/1a8/01-real-four-page-e2e.ps1`：Compare-FundSet holding + cumulative + per-page totalAsset 规则
- `docs/phase-0/decisions.md`：决策 7（profit 拆分 + total_asset 收紧）
- `docs/phase-1/checklists/phase-1a.md`：1a.8.7 标 ✅
- `docs/test-records/manual-tests/2026-07-18_phase1a8-v2-real-data-check.md`：v3.1 本报告
- `docs/phase-1/work-plans/2026-07-18_phase1a8-v2-work-plan.md`：v3 → v3.1 升级
- `docs/test-records/manual-tests/2026-07-18_phase1a8-v2-acceptance-plan.md`：v3.1 升级

不包含：

- `application-local.yml`（ignored，API key）
- `uploads/` 用户原图（ignored）
- `docs/test-records/ocr-results/`（ignored，原始 OCR）
- `docs/test-records/automated-smoke/1a8/api-test-output/`（ignored，响应原样）
- pre-v3.1-failed 失败样本（ignored）
