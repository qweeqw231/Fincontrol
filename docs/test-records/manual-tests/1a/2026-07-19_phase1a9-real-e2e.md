# 1a.9 真实 E2E 重跑报告（v2.6 prompt 下 HTTP 502 + zero_funds）

**时间**：2026-07-19 01:20 (UTC+8)
**报告人**：fincontrol-1a.9 自动化 e2e 脚本
**配套代码**：commit `ae1fbe0` + v2.6 prompt (`prompt_versions` id=8)
**配套 fixture**：v3.3（每页 expectedTotalAsset=7884.68）
**关联文档**：
- [`2026-07-18_phase1a8-v3_3-real-data-check.md`](2026-07-18_phase1a8-v3_3-real-data-check.md) — 1a.9 架构就绪报告
- [`2026-07-18_prompt-v2.6-upgrade-tutorial.md`](../manual-tests/2026-07-18_prompt-v2.6-upgrade-tutorial.md) — v2.6 prompt 教程
- [`2026-07-18_phase1a9-work-plan.md`](../../phase-1/work-plans/2026-07-18_phase1a9-work-plan.md) — 1a.9 工作计划

---

## 0. 报告状态：❌ **HTTP 502 + zero_funds**

**真实 E2E 在 v2.6 prompt 下失败**。根因不是 1a.9 代码 bug，而是 **v2.6 prompt 规则让模型在"顶部总资产不可见"时过于严格，输出 0 只基金**。

### 失败细节
- **P1 parse failed**：HTTP 502, body `{"code":3003,"message":"...返回 0 只基金...","errorType":"zero_funds"}`
- **aiRawResponse**（v2.6 prompt 输出）：模型判断这是"全部持仓子页面（不是主资产页）"→ total_asset=null → 因没基金数据被 zero_funds 检测判 0 只
- **实际数据**：4 张图是真实支付宝持仓列表，每页**有 6/3/5/6 只完整基金 + 1 只 truncated C**（P1 visible sum = 2954.91）

---

## 1. v2.5 vs v2.6 真实行为对比

| 维度 | v2.5 prompt（commit `811637d`）| v2.6 prompt（commit `ae1fbe0`）|
|---|---|---|
| **P1 顶部"总资产"** | 模型**估算**（visible sum × 1/0.3748）→ 7884.13 | 模型**判 null**（判断是子页非主页）→ 顶部不输出 |
| **P1 totalAsset 字段** | `7884.13`（估算）| `null` |
| **P1 funds 字段** | 模型把可见 6 只基金输出 | 模型**把可见 6 只基金输出 + 1 只 truncated C + 7 类别**（含 4 个 0 类别）|
| **P1 backend 解析** | 6 只完整基金 → code=0 | **0 只完整基金**（按 holdings 字段不完整判断）→ code=3003 |
| **HTTP 状态** | 200 OK | **502**（VisionModelClient 异常路径）|
| **dedup 结果** | 19/19 唯一 + 7884.68 ±0.01 | ❌ **失败**（P1 HTTP 502）|

**关键发现**：v2.5 行为"估算"看似错（fixture 期望 7884.68，v2.5 给 7884.13）但**端到端通了**；v2.6 行为"严格遵守顶部不可见 → null"看似对，但**端到端挂了**。

---

## 2. v2.6 prompt 副作用分析

### 模型推理（截取自 aiRawResponse）

```
The image shows the holdings page, not the main asset overview page.
The total asset would normally be on the previous page (主页).
Here we don't see it. So I should output null for total_asset.
```

模型按 v2.6 prompt 规则"顶部不可见 → null"严格执行，**把非主页场景全部标 null total_asset**。

但 fixture 设计假设 4 张图都是持仓列表页（没"主页"），每页都是子页，**v2.6 prompt 规则与 fixture 设计冲突**。

### zero_funds 检测逻辑（`ScreenshotService.parse`）

```java
boolean zeroFunds = asset.getCategories() == null
        || asset.getCategories().isEmpty()
        || asset.getCategories().stream().allMatch(c -> c.getFunds() == null || c.getFunds().isEmpty());
if (zeroFunds) {
    throw new BusinessException(ErrorCode.VISION_ZERO_FUNDS, ...);
}
```

`VISION_ZERO_FUNDS` (code 3003) → Spring Boot `ControllerAdvice` 转 HTTP 502。

### fixture 模型期望 vs v2.6 prompt 行为

| 路径 | fixture 期望 | v2.6 prompt 实际 | 偏差 |
|---|---|---|---|
| P1 top 总资产 | `7884.68` | `null`（模型判主页）| ❌ 期望固定值，模型给 null |
| P1 visible sum | fixture 不校验（v2.5 行为 2954.91）| 模型输出 6 只基金 2954.91 | ✓ 巧合一致 |
| zero_funds 检测 | 6 完整 + 1 truncated = 7 funds | 模型给 7 funds 但 holding=null 不算完整 | ❌ 7 只都没 holding → 判 0 只 |

---

## 3. 解决路径（1a.10+ 范畴）

### 方案 A：放宽 zero_funds 检测（**推荐**）
改进 `ScreenshotService.parse` 的 zeroFunds 判断逻辑：
- **当前**：所有 fund 的 `holding_profit` 都被判 incomplete → zero_funds
- **改进**：检查 `aiRawResponse` 字符串是否包含 "零只基金"或"0 funds"或模型明确说"no funds"等关键短语，**而不是只检查 Java 对象**
- 或者：检查 `categories[].funds[].name` 非空 + `amount` 非 null（fund 名称+金额可见 → 算 1 只完整基金）

### 方案 B：v2.7 prompt 修订
- 改"顶部不可见 → null"为"顶部不可见 → 不输出 total_asset 字段但 fund 仍完整输出"
- 或："每页都输出 fund，无论是否主页"
- 副效应：v2.5 行为（估算）已能端到端通，v2.6 反而挂了——**v2.7 应在 v2.5 + v2.6 取长补短**

### 方案 C：分场景 fixture
- 真实 4 张图数据 = 持仓列表页（v2.5 行为）
- fixture 期望 = 顶部主页 7884.68（v2.6 行为）
- **fixture 与真实数据不一致** → 1a.9 验收逻辑需调整

---

## 4. 当前状态盘点

### 4.1 已闭环（commit 范围）

| 组件 | 状态 | 验证 |
|---|---|---|
| prompt_versions v2.6（id=8, 3859 字节）| ✅ MySQL 落地 | `SELECT id, version FROM prompt_versions WHERE id=8` = v2.6 |
| 1a.9 work-plan.md | ✅ 落盘 | `docs/phase-1/work-plans/2026-07-18_phase1a9-work-plan.md` 9 节 + 15 步 |
| 代码 + schema + fixture + 5 测试 | ✅ mvn 226/226 PASS | `mvn clean verify` 1m21s |
| MySQL ALTER (asset_raw + asset_snapshot + total_asset_source) | ✅ 落地 | `information_schema.COLUMNS` 验证两列存在 |
| DedupEngine dual-track + DISCREPANCY 1% | ✅ 单元测试覆盖 | DedupEngineTest +4 + Phase1a8RealFourPageFixtureTest +1 |
| v3.3 fixture（每页 top=7884.68）| ✅ 单元测试 + H2 集成 | fixture 路径下 0% 偏差 = 不报警（设计正确）|
| v3.3 fixture 改造（bad P2 top）| ✅ 单元测试 | 触发 TOP_INCONSISTENT（设计正确）|
| 文档 v3.3 + phase-1a.md + decisions.md | ✅ 5 段式验收 | commit ae1fbe0 已推 origin/main |
| Work-plan + commit c326bac (prompt tutorial) | ✅ 已推 | origin/main c326bac |

### 4.2 真实 E2E 部分（commit 3 候选）

| 组件 | 状态 | 备注 |
|---|---|---|
| Spring Boot 启动（`java -jar target/fincontrol-backend.jar`）| ✅ HTTP 200 | `/actuator/health` UP |
| 真实 E2E PS 脚本调用 | ⚠️ **HTTP 502** | v2.6 prompt 副作用（zero_funds）|
| MySQL 数据写入 | ❌ 未触发（HTTP 502 阻止）| — |
| DISCREPANCY 报警触发 | ❌ 未触发（HTTP 502 阻止）| — |
| 诺安误读率 v2.5 vs v2.6 | ❌ 未对比（HTTP 502 阻止）| — |

### 4.3 后续债（**已发现，明示 1a.10+**）

| 债 | 范畴 | 阻塞 |
|---|---|---|
| **zero_funds 检测过于严格**（v2.6 prompt 副作用）| **1a.10+** | v2.7 prompt 修订 或 放宽 zero_funds 检测逻辑 |
| per-page 流式 tokens 浪费 | 1a.10+ | 差额法 / 单次多图 |
| category master table | 1a.10+ | 决策 8 回退条件 |
| multi-user RBAC | Phase 5b | 决策 8 回退条件 |
| Phase 1b 前端 | 独立 | 后端收尾后再开 |
| DISCREPANCY 阈值常量化 | 1a.10+ | 当前硬编码 0.01 |

---

## 5. 5 段式验收

| 段 | 评估 | 备注 |
|---|---|---|
| **BUSINESS** | ✅ 226/226 PASS | mvn 单元测试全过 |
| **CONTRACT** | ⚠️ 真实 e2e 暴露 zero_funds 副作用 | API 契约不变；zero_funds 错误码行为符合 v2.5 设计 |
| **READ_SQL** | ✅ MySQL + H2 同步 | 226/226 通过 H2 集成测试 |
| **PRODUCTION** | ❌ 真实 e2e HTTP 502 | v2.6 prompt 在真实数据下过度严格 |
| **COVERAGE** | ✅ JaCoCo ~77% | 1a.9 新增代码全部覆盖 |

---

## 6. 总结与建议

### 1a.9 范围完成度
- ✅ 架构就绪（代码 + schema + fixture + 测试 + 文档）
- ✅ 单元闭环（226/226 PASS）
- ❌ **真实端到端闭环**（v2.6 prompt 在真实数据下挂 zero_funds）

### 推荐下一步
1. **v2.7 prompt 修订**（1a.10+ 范围）：在 v2.6 基础上加规则"即使顶部不可见也输出 fund（仅 total_asset 留 null）"，让模型不输出 0 只基金
2. **放宽 zero_funds 检测**（1a.10+ 范围）：检查 `aiRawResponse` 关键短语 + fund 名称+金额非空（不只是 holding）
3. **真实 E2E 重跑**（v2.7 后）：落盘新报告，验证 4/4 = 100% 通过

### 1a.9 commit 3 决策
- **不推** commit 3（work-plan + new test 已经在 ae1fbe0 里）—— 因为 commit 3 候选是 e2e 报告，但 e2e 失败无意义
- 改：v2.6 prompt 副作用 列入 1a.10+ 债，本报告作为诚实记录落盘**不入仓**（不入 git，因为是失败记录 + 文件 gitignored）

### git 状态
- origin/main：c326bac (commit 1: prompt tutorial) + ae1fbe0 (commit 2: code + docs)
- 本地：ae1fbe0 + working tree modifications
- 本报告落盘：本地 `docs/test-records/manual-tests/2026-07-19_phase1a9-real-e2e.md`（gitignored via `**/tmp/` 模式外的 test-records manual-tests 不被 gitignore）

---

## 7. 引用

- v3.3 报告：`2026-07-18_phase1a8-v3_3-real-data-check.md`（架构就绪，1a.9 闭环）
- v2.6 教程：`2026-07-18_prompt-v2.6-upgrade-tutorial.md`（MySQL UPDATE 步骤）
- 1a.9 工作计划：`docs/phase-1/work-plans/2026-07-18_phase1a9-work-plan.md`
- e2e 脚本：`fincontrol-backend/scripts/1a8/01-real-four-page-e2e.ps1`
- aiRawResponse 记录：`docs/test-records/ocr-results/2026-07-19/20260719_00*_test-file-id-001_minimax_*.json`（v2.6 prompt 原始输出）

---

**v2.6 prompt 在真实数据下失败。1a.9 范围（dual-track + DISCREPANCY + schema + 文档）已完成，但 v2.6 prompt 副作用导致真实 e2e 不通。1a.10+ 需修订 v2.7 prompt 或放宽 zero_funds 检测。**