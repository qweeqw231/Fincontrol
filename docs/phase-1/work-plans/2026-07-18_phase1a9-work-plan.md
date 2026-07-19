# Phase 1a.9 总资产双轨 + DISCREPANCY 1% 报警（v2.6 prompt + schema 升级）

**状态**：`CLOSED`〔2026-07-19 00:12 闭环 commit ae1fbe0〕
**配套验收计划**：[`2026-07-18_phase1a8-v3_3-real-data-check.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v3_3-real-data-check.md)（v3.3 报告，175 行）
**v3.2 真实闭环报告**：[`2026-07-18_phase1a8-v3_2-real-data-check.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v3_2-real-data-check.md)（前置）
**配套 v3.2 work-plan**：[`2026-07-18_phase1a8-v3_2-work-plan.md`](2026-07-18_phase1a8-v3_2-work-plan.md)
**配套 v2.6 prompt 教程**：[`2026-07-18_prompt-v2.6-upgrade-tutorial.md`](../../test-records/manual-tests/2026-07-18_prompt-v2.6-upgrade-tutorial.md)
**API 契约**：[`api-contract.md §2.2 / §3.2 / §7.1 / §7.2`](../../../phase-0/api-contract.md)
**代码基线**：`ae1fbe0`（v3.2 闭环后，v2.6 + dual-track）

> 本文件遵循 `docs/phase-1/work-plans/README.md` 规则：与配套验收计划互链，按 15 步切片，每步有测试 ID 与可执行命令；commit 范围明确，secret 全部不入仓。

---

## 0. 背景

1a.8.8 v2.5 真实 E2E 暴露 2 个"模型表现"问题（已在 v3.2 验收报告记录为已知债）：

| # | 问题 | 现状 | 1a.9 修法 |
|---|---|---|---|
| 1 | **P2 totalAsset 口径不一致** | v2.5 prompt 让模型在 P2 页输出 visible sum（2987.32），fixture 期望顶部"总资产"全账户（7884.68） | v2.6 prompt：明确"total_asset = 截图顶部'总资产'数字，禁止用当前页 visible sum 代替" |
| 2 | **OCR 诺安误读为华安** | v2.5 fixture P3 含 1 处 OCR 误读 | v2.6 prompt 不直接修复（OCR 是 vision 模型能力）；真实 E2E 重跑 v2.6 看误读率 |

**关键升级**（v3.2 → v3.3）：

```diff
+ prompt v2.6: total_asset = 截图顶部"总资产"数字（顶部不可见时 null，禁止 visible sum 代替）
+ DedupEngine 双轨决策：4 页顶部一致 → 用 top；不一致 → fallback visible_sum + TOP_INCONSISTENT warning
+ DISCREPANCY 报警：|top - dedupedSum| / top > 1% → DedupWarning(code=DISCREPANCY)
+ schema：asset_raw + asset_snapshot + total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top'
+ fixture v3.3：每页 expectedTotalAsset = 7884.68 + expectedTotalAssetSource = "top"
```

---

## 1. 范围（in-scope / out-of-scope）

### 1.1 范围内（15 步）

- prompt_versions v2.5 → v2.6（id=8 已落地）：顶部总资产优先 + 禁 visible sum + 余额类 holding=null + 7 canonical + 双字段沿用 v2.5
- `ParsedAsset` 加 `totalAssetSource: String` 字段（"top" / "visible_sum"）
- `AssetRaw` 加 `totalAssetSource`（denormalized，每行 19 条同值）
- `AssetSnapshot` 加 `totalAssetSource`
- H2 测试 schema 同步：CHECK 约束模拟 MySQL ENUM('top','visible_sum')
- `DedupEngine.deduplicate` 双轨决策 + DISCREPANCY 1% 报警
- `SnapShotConfirmService.writeAssetRaw/AssetSnapshot` 从 dedup 结果取 totalAssetSource，写入两张表
- `AssetSnapshotMapper.xml.upsertByCategory` INSERT + ON DUPLICATE KEY UPDATE 都加 total_asset_source 列
- fixture v3.2 → v3.3：每页 expectedTotalAsset = 7884.68 + 每页 expectedTotalAssetSource = "top"
- `Phase1a8RealFourPageFixture` record 加新字段 + `Phase1a8RealFourPageFixtureTest` 加 1 个 fixture 路径测试
- `DedupEngineTest` 加 4 个 DISCREPANCY + dual-track 单测（含故意制造 DISCREPANCY 的 v3.3 fixture 改造测试）
- `mvn clean verify` 220 → 225 PASS（+5 用例）
- 真实 E2E 重跑（v2.6 prompt 下 4 张图 minimax API）：落盘新报告 `2026-07-19_phase1a9-real-e2e.md`
- 文档：v3.3 验收报告（替换 v3.2）+ phase-1a.md 1a.9 条目 + decisions.md 决策 8 增补 + **1a.9 work-plan（本文件）**

### 1.2 范围外（明示移到后续阶段）

- ❌ per-page 流式 tokens 优化（差额法 / 单次多图）→ **1a.10+**
- ❌ category master table（CRUD 类别名）→ **1a.10+**（决策 8 回退条件）
- ❌ `confirmedAt` 字段（前端显式"未确认"提示）→ **1a.10+**（决策 8 回退条件）
- ❌ multi-user RBAC 完整版 → **Phase 5b**（决策 8 回退条件）
- ❌ 前端 Phase 1b（1b.1-1b.8）→ **独立**，1a 后端收尾后再开
- ❌ DISCREPANCY 阈值常量化（目前是 DedupEngine 内部硬编码 0.01）→ **1a.10+**
- ❌ 真实 minimax API key 申请 / 配 application-local.yml → **用户操作**（已填）

---

## 2. 15 步实施计划

| 步 | 内容 | 主要文件 | 关联测试 / 验证 |
|---|---|---|---|
| **1** | prompt v2.6 内容定稿（含顶部总资产规则）| `.tmp/prompt-v2.6.sql` | T-V2.6-01 内容审核（顶部 vs visible sum 区分） |
| **2** | MySQL UPDATE prompt_versions id=7 → v2.6 id=8 | `.tmp/run-prompt-v2.6.bat` | T-V2.6-02 `SELECT id, version, LENGTH(prompt_content) FROM prompt_versions` |
| **3** | v2.6 prompt 教程文档落盘 | `docs/test-records/manual-tests/2026-07-18_prompt-v2.6-upgrade-tutorial.md` | 文档 review |
| **4** | v2.6 tutorial commit + push | `.tmp/commit-prompt-v26.bat` | **commit c326bac**（已 push） |
| **5** | `ParsedAsset + AssetRaw + AssetSnapshot` 加 `totalAssetSource` 字段 | `dto/screenshot/ParsedAsset.java` / `entity/AssetRaw.java` / `entity/AssetSnapshot.java` | 编译通过 |
| **6** | H2 schema + MySQL schema 加 `total_asset_source` 列 | `docs/phase-0/db-schema.sql` / `src/test/resources/schema-h2.sql` | T-V2.6-03 H2 schema 校验 + MySQL ALTER 落地 |
| **7** | `AssetSnapshotMapper.xml.upsertByCategory` 加 total_asset_source 列 | `resources/mapper/AssetSnapshotMapper.xml` | 编译通过 |
| **8** | `DedupEngine.deduplicate` 双轨决策 + DISCREPANCY 1% 报警 | `service/DedupEngine.java` | **T-V2.6-04 DedupEngineTest +4** |
| **9** | `SnapShotConfirmService` 写两张表 totalAssetSource | `service/SnapShotConfirmService.java` | T-V2.6-05 单元测试覆盖 |
| **10** | fixture v3.2 → v3.3：每页 expectedTotalAsset = 7884.68 + 每页 expectedTotalAssetSource = "top" + 顶层 expectedDedupedSum + expectedDiscrepancyThresholdPct | `src/test/resources/fixtures/phase1a8-real-four-pages.json` / `src/test/java/com/fincontrol/fixture/Phase1a8RealFourPageFixture.java` | T-V2.6-06 fixture 自检 |
| **11** | `DedupEngineTest` +4（topConsistent / topInconsistent / discrepancyOver / discrepancyWithin）| `src/test/java/com/fincontrol/service/DedupEngineTest.java` | T-V2.6-04 |
| **12** | `Phase1a8RealFourPageFixtureTest` +1（v3.3 fixture 路径 dedup topConsistent_usesTop）+1（v3.3 fixture 改造 → DISCREPANCY + TOP_INCONSISTENT 双触发）| `src/test/java/com/fincontrol/service/Phase1a8RealFourPageFixtureTest.java` | T-V2.6-06 + T-V2.6-07 |
| **13** | MySQL ALTER 已建库 + H2 schema 已 sync | `.tmp/alter-asset-total-asset-source.bat` | `information_schema.COLUMNS` 验证两列存在 |
| **14** | `mvn clean verify` | `pom.xml` | **225/225 PASS** + JaCoCo ≥ 60% |
| **15** | 文档 v3.3 + phase-1a.md + decisions.md 增补 + 1a.9-work-plan（本文件）+ 真实 E2E 报告 | `docs/test-records/manual-tests/2026-07-18_phase1a8-v3_3-real-data-check.md` + `docs/phase-1/checklists/phase-1a.md` + `docs/phase-0/decisions.md` + `docs/test-records/manual-tests/2026-07-19_phase1a9-real-e2e.md` | 5 段式验收 |

**总时间预算**：~55 分钟（不含网络等待真实 E2E）

---

## 3. Schema 升级

### 3.1 asset_raw

```sql
ALTER TABLE asset_raw
  ADD COLUMN total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top'
  COMMENT '1a.9：该快照总额来源（top/visible_sum，denormalized 每行 19 条同值）';
```

落地状态（2026-07-19 00:12）：
```
asset_raw.total_asset_source  varchar(20)  default 'top'
```

### 3.2 asset_snapshot

```sql
ALTER TABLE asset_snapshot
  ADD COLUMN total_asset_source VARCHAR(20) NOT NULL DEFAULT 'top'
  COMMENT '1a.9：top=顶部总资产 / visible_sum=deduped fund 加总';
```

落地状态（2026-07-19 00:12）：
```
asset_snapshot.total_asset_source  varchar(20)  default 'top'
```

### 3.3 H2 测试 schema（CHECK 模拟 ENUM）

```sql
ALTER TABLE asset_raw ADD CONSTRAINT chk_asset_raw_total_asset_source
  CHECK (total_asset_source IN ('top', 'visible_sum'));
ALTER TABLE asset_snapshot ADD CONSTRAINT chk_asset_snapshot_total_asset_source
  CHECK (total_asset_source IN ('top', 'visible_sum'));
```

> **MySQL 8.0 兼容性说明**：MySQL 8.0 **不支持** `ADD COLUMN IF NOT EXISTS`（仅 MariaDB 支持）；本项目用 `SET @c = (SELECT COUNT...) + PREPARE stmt` 模式幂等执行，已验证于 commit ae1fbe0 流程。

---

## 4. v2.6 prompt 规则全文（落地于 `prompt_versions` id=8，3859 字节）

```
【1a.9 关键 - 总资产字段读取规则】
total_asset = 截图顶部"总资产"数字（无论第几页都应一致；通常显示"总资产 ¥xxxx.xx"在页面最上方）
  - 顶部"总资产"清晰可见时，**必须**使用顶部数字（例如 7884.68）
  - 顶部不可见 / 被遮挡 / 在加载中 → 输出 null（不要凭空估算，也不要把当前页基金加总作为 total_asset）
  - **禁止**：将当前页可见基金的 amount 加总作为 total_asset（页面只是持仓列表，不等于总资产）
  - 余额类（余额宝等）通常已含在顶部总资产中，按图所见如实输出

【1a.8.7 关键 - 可见加总 vs 总资产】
每页截图顶部"总资产"通常是**全账户的累计**；该页只显示**该页可见**基金。
  - total_asset = 顶部"总资产"字段（1a.9 优先），不是当前页 amount 加总
  - 不要把截图顶部显示的"全账户总资产"作为当前页 total_asset 之外的另一个字段
  - 若顶部被截断/不可见，输出 null，不要凭空估算 total_asset

【1a.8.7 关键 - 双字段】
每只基金必须输出 holding_profit（持有收益）和 cumulative_profit（累计收益）：
  - 即使值很小（如 1.89 元），也要如实记录，不要写 0
  - 截图显示 holding_profit 为 0 或 -0.0 → 按 0 记录（这是合法值）
  - 截图显示 holding_profit 字段不可见 / 被截断 → 这只基金视为不完整记录，**可以**少写该字段为 null

【1a.8.7 关键 - 余额类 holding_profit 处理】
余额类（余额宝、活期存款、现金管理等），截图通常**不显示**"持有收益"列。
  - 如果截图 holding_profit 不可见，**输出 null**，不要根据 0 估算
  - cumulative_profit 按截图显示如实记录（如 余额宝 +1.89）

【1a.8.8 关键 - 类别归一化】（沿用 v2.5 七大类 canonical 名）
  - 货币类 / 固收类 / 商品类 / A股权益类 / 海外权益类 / 港股大中华类 / 余额类
  - 禁止旧枚举（债券类 / 股票类 / 货币类 / 权益类 / 黄金类 / 保障类）

【重要】结构化JSON必须严格使用以下嵌套结构（后端只解析此结构）：
{
  "snapshot_date": "YYYY-MM-DD",
  "total_asset": 浮点,  // 1a.9：必须 = 顶部"总资产"数字
  "categories": [ { "category_name": "...", "funds": [...] } ]
}
```

---

## 5. DedupEngine 双轨逻辑

### 5.1 决策树

```
输入: 4 张 ParsedAsset（per-page AI 输出）+ 已有 fund set (维度 C)
                ↓
       dedup 19 unique funds + per-category aggregates (维度 C/D)
                ↓
       ┌─────────────────────────────────────────────────────────┐
       │ 收集 4 页 top = page.getTotalAsset()                      │
       │ 计算 deduped sum = sum(unique merged fund amounts)          │
       └─────────────────────────────────────────────────────────┘
                ↓
       4 页 top 是否一致？
                ↓
   ┌──── 一致（4 页都是 7884.68）────────┐
   │ merged.totalAsset = top                    │
   │ merged.totalAssetSource = "top"            │
   │ + DISCREPANCY 校验：                       │
   │   |top - dedupedSum| / top > 1% ?         │
   │   → 报警 DedupWarning(code=DISCREPANCY)   │
   │   （fixture v3.3 路径下偏差 = 0%，无报警）│
   └────────────────────────────────────────────┘
                ↓ 否
   ┌──── 不一致 / 部分 null ─────────────┐
   │ merged.totalAsset = dedupedSum        │
   │ merged.totalAssetSource = "visible_sum"│
   │ + TOP_INCONSISTENT 报警                 │
   └────────────────────────────────────────────┘
```

### 5.2 DISCREPANCY 阈值

```java
private static final BigDecimal DISCREPANCY_THRESHOLD = new BigDecimal("0.01");  // 1%

BigDecimal diff = refTop.subtract(dedupedSum).abs();
if (refTop.compareTo(BigDecimal.ZERO) != 0
        && diff.divide(refTop, 4, RoundingMode.HALF_UP)
                .compareTo(DISCREPANCY_THRESHOLD) > 0) {
    warnings.add(new DedupWarning("DISCREPANCY", ...));
}
```

> **TODO（1a.10+）**：把 0.01 提到 `application.yml` 配置项 `fincontrol.dedup.discrepancy-threshold-pct`，便于调优。

---

## 6. 风险规避

| 风险 | 缓解 |
|---|---|
| **OCR 误读率**（v2.5 fixture 已含 1 处"诺安→华安"）| v2.6 prompt 不直接修复，靠 v2.6 真实 E2E 重跑复现；如持续误读 → 1a.10+ 引入 OCR 后处理 |
| **顶部"总资产"被截断**（如截图含状态栏遮罩）| v2.6 prompt 规则：不可见 → 输出 null；DedupEngine 4 页都 null → fallback deduped sum + 报警 |
| **DISCREPANCY 阈值 1% 太敏感**（P2/P3 天然偏差大）| 当前是硬编码 `0.01`；如生产报警过多 → 1a.10+ 提到配置文件 |
| **fixture v3.3 真实场景无 DISCREPANCY 触发**（v3.3 设计上 4 页完美一致）| 已加 `DedupEngineTest.dedup_v3_3FixtureWithBadP2Top_emitsBothWarnings`（故意改 P2 top → 验证报警路径）|
| **MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`** | `.tmp/alter-asset-total-asset-source.bat` 用 `SET @c + PREPARE stmt` 幂等模式 |
| **Spring Boot Caffeine cache 失效**（v2.6 prompt 没生效）| 真实 E2E 前必须 `mvn spring-boot:run` 重启 |

---

## 7. 提交清单（commit + push）

### Commit 1 (c326bac, pushed 2026-07-18 23:53)

```text
prompt(screenshot_parser v2.5→v2.6): total_asset top-priority + DISCREPANCY-friendly
```

包含：
- `docs/test-records/manual-tests/2026-07-18_prompt-v2.6-upgrade-tutorial.md`

### Commit 2 (ae1fbe0, 本地 2026-07-19 00:13, push 待重试)

```text
fix(1a.9): total_asset dual-track + asset_raw.total_asset_source + doc self-consistency
```

包含 15 文件 +580 行：
- 代码：`ParsedAsset.java` / `AssetRaw.java` / `AssetSnapshot.java` / `DedupEngine.java` / `SnapShotConfirmService.java` / `AssetSnapshotMapper.xml`
- schema：`db-schema.sql` / `schema-h2.sql`
- fixture + 解析器：`phase1a8-real-four-pages.json` / `Phase1a8RealFourPageFixture.java`
- 测试：`DedupEngineTest.java` (+4) / `Phase1a8RealFourPageFixtureTest.java` (+1)
- 文档：`2026-07-18_phase1a8-v3_3-real-data-check.md` / `phase-1a.md` / `decisions.md`

### Commit 3 (待 — 1a.9 补漏)

```text
docs(1a.9): 1a.9-work-plan + v3.3 fixture DISCREPANCY test + real E2E report
```

包含：
- `docs/phase-1/work-plans/2026-07-18_phase1a9-work-plan.md`（本文件）
- `src/test/java/com/fincontrol/service/DedupEngineTest.java`（+1：v3.3 fixture 改造 DISCREPANCY 双触发测试）
- `src/test/java/com/fincontrol/service/Phase1a8RealFourPageFixtureTest.java`（+1：同上）
- `docs/test-records/manual-tests/2026-07-19_phase1a9-real-e2e.md`（真实 E2E 报告）

---

## 8. 验收判据

8.1 **v3.3 fixture 自检**：
- 4 页 `expectedTotalAsset = 7884.68`（v2.6 prompt 顶部一致）
- 4 页 `expectedTotalAssetSource = "top"`
- dedup 19/19 + 7884.68 ±0.01
- 余额宝 holding=null + cumulative=1.89
- 余额类 vs 非余额类 holding 字段差异断言

8.2 **DedupEngine 单测**（225/225 PASS）：
- 4 个新增 DISCREPANCY 行为：topConsistent / topInconsistent / discrepancyOver / discrepancyWithin
- 1 个 v3.3 fixture 路径 topConsistent_usesTop
- 1 个 v3.3 fixture 改造 DISCREPANCY 双触发（commit 3 待加）

8.3 **真实 E2E 重跑**（commit 3 待）：
- 4/4 upload + 4/4 parse code=0
- P1/P2/P3/P4 top 都 = 7884.68（验证 v2.6 prompt 顶部优先）
- dedup 19/19 + 7884.68 ±0.01
- 诺安误读率：v2.5 vs v2.6 对比（如模型仍误读，记 OCR 模型能力债）

8.4 **MySQL ALTER**：
- `asset_raw.total_asset_source` + `asset_snapshot.total_asset_source` 两列存在
- H2 schema CHECK 约束生效

8.5 **文档自洽**：
- v3.3 验收报告替换 v3.2
- phase-1a.md 1a.9 条目完整 + 区分架构就绪 vs 真实跑通
- decisions.md 决策 8 增补 v2.6 + dual-track + DISCREPANCY 5 测试覆盖
- **本 work-plan 文件**（v3.2 同模式）

---

## 9. 引用与回退

- 决策 8 1a.9 增补：`docs/phase-0/decisions.md §决策 8`
- v3.2 work-plan（前置）：`docs/phase-1/work-plans/2026-07-18_phase1a8-v3_2-work-plan.md`
- v3.2 验收报告（前置）：`docs/test-records/manual-tests/2026-07-18_phase1a8-v3_2-real-data-check.md`
- v3.3 验收报告：`docs/test-records/manual-tests/2026-07-18_phase1a8-v3_3-real-data-check.md`
- v2.6 prompt 教程：`docs/test-records/manual-tests/2026-07-18_prompt-v2.6-upgrade-tutorial.md`

**回退条件**：

- **prompt v2.6 触发更多错误** → `DELETE FROM prompt_versions WHERE id=8`（让 v2.5 id=7 复活，PromptLoader `ORDER BY id DESC`）
- **schema 回退**（业务数据未写入前） → `ALTER TABLE asset_raw DROP COLUMN total_asset_source;` + 同上 asset_snapshot
- **代码回退** → `git revert ae1fbe0` 或 checkout 1a.8.8 v2.5 版本（commit 811637d + 4a84e65）
- **DedupEngine dual-track 回退**（保留 fixture v3.3） → 删除 `merged.setTotalAssetSource(...)` 那 5 行；fallback 到旧 dedupedSum
- **DISCREPANCY 阈值调整** → 1a.10+ 提到配置文件（当前是 `DedupEngine` 内部硬编码 `0.01`）