# Phase 1a.8 v3.2 验收计划（类别归一化 + FundCategoryResolver + last_seen_at + DELETE/reset/stale + 多用户预留）

**状态**：`GATE0_PASS`〔2026-07-18 草拟，配套 v3.1 真实闭环已 PASS〕
**配套工作计划**：[`2026-07-18_phase1a8-v3_2-work-plan.md`](../../../phase-1/work-plans/2026-07-18_phase1a8-v3_2-work-plan.md)
**v3.1 真实验收报告**：[`2026-07-18_phase1a8-v3_1-real-data-check.md`](2026-07-18_phase1a8-v3_1-real-data-check.md)（19/19 唯一 + 7884.68 + 0 偏差）
**API 契约**：[`api-contract.md §2.2 / §3.2 / §7.1 / §7.2`](../../../phase-0/api-contract.md)

> 本文件遵循 docs/test-records/README.md 规则：测试 ID 命名、实际结果回填、失败兜底。落地后用同一文件追加"实际结果"列；不另建 acceptance report。

---

## 0. Gate 0 冻结

### 0.1 v3.1 真实闭环确认（已 PASS）

- 4 张用户样图 `uploads/samples/phase1a2-alipay-fund-list-20260715-{2355-1, 2355-2, 2355-3, 2356-1}.jpg`
- 19/19 唯一 + 7884.68 ±0.01
- holding_profit + cumulative_profit 双字段 0 偏差
- 186/186 单元 + 集成 + 算法测试 PASS
- commit `0107b78` 推送 origin main

### 0.2 v3.2 必解决

1. 类别名漂移（v2.1 输出"QDII/商品/固收/货币" vs ground truth "海外权益类/商品类/固收类/货币类/..."）
2. 清仓后再出现不弹窗（保留 mapping + last_seen_at）
3. 多用户债（match 不传 userId）顺手补

### 0.3 v3.2 不做

- ❌ category master table（1a.9+）
- ❌ 完整 RBAC（1b.x / Phase 5b）
- ❌ prompt fallback 策略迁移（1a.9）

---

## 1. 测试边界

| 层级 | 数据/替身 | 目的 | 是否必需 |
|---|---|---|---|
| Unit | Mockito + CategoryEnum | 7 canonical 全映射 + 未知名抛 1004 | ✅ |
| Unit | Mockito + FundCategoryResolver | user_correct 优先 / ai_guess 兜底 / 未知名 fallback | ✅ |
| Unit | Mockito + CategoryMapMapper | CRUD SQL（selectByUserCorrect / selectStale / upsertWithSource / updateLastSeen / DELETE） | ✅ |
| Integration | H2 内存 + real SnapShotConfirmService | ai_guess 首次写入 / user_correct 重置 / last_seen_at 同步 / category mirror 验证 | ✅ |
| Algorithm | fixture v3.2 + real DedupEngine | 19 项全部 canonical 类别名解析 | ✅ |
| Production E2E | 真实 4 张图 + minimax v2.3 | 0 偏差 + 类别名一致（QDII → 海外权益类）| ✅ |

真实 fixture 与生产 prompt v2.3 是两类证据：fixture 不能证明模型真识别 canonical，模型能输出 canonical 但 fixture 没此字段也无意义。两者必须分别通过。

---

## 2. 测试用例（T-V3.2-01 ~ T-V3.2-08）

### T-V3.2-01 CategoryEnum canonical 全映射

- **目标**：7 canonical + 别名表 100% 命中
- **步骤**：
  1. `CategoryEnum.fromAlias("QDII")` → "海外权益类"
  2. `CategoryEnum.fromAlias("QDII/海外股票")` → "海外权益类"
  3. `CategoryEnum.fromAlias("港股")` → "港股大中华类"
  4. `CategoryEnum.fromAlias("纯债")` → "固收类"
  5. `CategoryEnum.fromAlias("基金")` → null（未知名）
  6. `CategoryEnum.canonical` 列表 7 个
- **预期**：1-4 命中 canonical；5 返回 null；6 长度 = 7

### T-V3.2-02 FundCategoryResolver 优先级

- **目标**：user_correct 优先 / ai_guess 兜底 / 未知名 fallback
- **步骤**：
  1. mock mapper：fund "国泰黄金C" → user_correct (海外权益类)
  2. mock mapper：fund "新基金X" → ai_guess (商品类)
  3. mock mapper：fund "完全陌生Y" → null
  4. 调用 resolver.resolve("国泰黄金C", rawCategory="QDII", userId=1)
  5. 调用 resolver.resolve("新基金X", rawCategory="商品", userId=1)
  6. 调用 resolver.resolve("完全陌生Y", rawCategory="其他", userId=1)
- **预期**：4 → "海外权益类"（用户覆盖 AI）；5 → "商品类"（兜底）；6 → "其他"（fallback）

### T-V3.2-03 DTO 字段存在性

- **目标**：ParsedAsset.FundLine / AssetBalanceItem / SnapshotFundDetail 三处都加 `isUserConfirmed: boolean`
- **步骤**：反射检查三个 DTO 类的字段
- **预期**：isUserConfirmed 字段存在 + 类型 boolean

### T-V3.2-04 解析后类别归一化

- **目标**：ScreenshotService 调 resolver 归一化 AI 输出
- **步骤**：
  1. mock minimax 返回 `"funds": [{"fund_name": "X", "amount": 1, "profit": 2, "category_name": "QDII"}]`
  2. mock resolver.resolve("X", "QDII", 1) → "海外权益类"
  3. 调用 service.parse（mocked）
- **预期**：返回 ParsedAsset 中 fund X 的 categoryName = "海外权益类" 且 isUserConfirmed=true（来自 user_correct）

### T-V3.2-05 二态写

- **目标**：首次 upsert source='ai_guess'；已确认 upsert source='user_correct' + last_seen_at=NOW()
- **步骤**：
  1. 调用 confirm 一次（首次），mock mapper 接收 upsert（source='ai_guess'）
  2. mock mapper 模拟后续 select 返回 source='user_correct'
  3. 再次调用 confirm（已确认），mock mapper 接收 update（source='user_correct'，last_seen_at）
- **预期**：步骤 1 含 ai_guess；步骤 3 含 user_correct + last_seen_at

### T-V3.2-06 match 多用户隔离

- **目标**：match 跨 user 不命中
- **步骤**：
  1. mock mapper：user 1 fund "X" → category A
  2. mock mapper：user 2 fund "X" → category B
  3. 调用 match（userId=1）
  4. 调用 match（userId=2）
- **预期**：步骤 3 返回 A；步骤 4 返回 B（不串号）

### T-V3.2-07 DELETE 跨用户隔离

- **目标**：DELETE 仅删自己 user 的映射
- **步骤**：
  1. mock mapper：deleteByUserAndFundName(userId, fundName) → affected 1
  2. mock mapper：deleteByUserAndFundName(userId=2, fundName) → affected 0
  3. 调 DELETE（X-User-Id=1）
  4. 调 DELETE（X-User-Id=2）
- **预期**：步骤 3 成功 200；步骤 4 2001（不删别人）

### T-V3.2-08 reset + stale

- **目标**：reset 改 ai_guess；stale 默认 90 天
- **步骤**：
  1. POST /reset/{userId}/{fundName} → mock mapper 接收 update source='ai_guess'
  2. GET /stale（无 days 参数）→ 默认 90
  3. GET /stale?days=7 → 自定义 7
- **预期**：1 改 source；2 默认 90；3 自定义

---

## 3. E2E 真实四图验证（T-V3.2-S07）

- **目标**：真实 minimax 跑 fixture v3.2 → 19 项 unique 类别名全 canonical
- **步骤**：
  1. 启动后端（v2.3 prompt）
  2. 跑 `scripts/1a8/01-real-four-page-e2e.ps1`（PowerShell）
  3. 校验 `summary.json`：unique=19/19, total=7884.68±0.01, holding=expected, cumulative=expected, isUserConfirmed 全部 true
- **预期**：
  - 19/19 唯一
  - 19 项 category 全部是 "海外权益类 / 商品类 / 固收类 / A股权益类 / 货币类 / 港股大中华类 / 余额类" canonical 名
  - 总额 7884.68
  - 0 偏差

---

## 4. 5 段式验收

| 段 | 证据 | 状态 |
|---|---|---|
| **BUSINESS** | T-V3.2-01 ~ T-V3.2-08 单元测试 7 用例 + CategoryMapControllerTest 4 用例（match userId / DELETE / reset / stale） = 预计 ~12 用例；fixtureTest 与现有 186 用例 = **~198 PASS** | ⏳ 落地后 |
| **CONTRACT** | CategoryMapController API 签名不破坏前端；DTO 加 isUserConfirmed 是新字段 | ⏳ |
| **READ_SQL** | `prompt_versions v2.3` 上库；`fund_category_map.last_seen_at` MySQL+H2 同步；mirror 验证 19 行 | ⏳ |
| **PRODUCTION** | 真实四图 OCR E2E：19/19 唯一 + 7884.68 ±0.01 + 类别全 canonical | ⏳ 落地后 |
| **COVERAGE** | JaCoCo ≥ 60% | ⏳ |

---

## 5. 硬性 PASS 判据

只有同时满足以下全部条件，才可把 1a.8.8 标 ✅：

1. 正确 4 张 `uploads/samples` 图片
2. 4/4 upload + 4/4 parse code=0
3. 四页完整记录 6/3/5/6
4. 20 → 19（DEDUP dropped=1）
5. 19 名称全部命中 + amount/holding/cumulative/canonical-category 全部一致
6. 总额 7,884.68 ±0.01
7. OCR 至少 4 个
8. 19 项 category 全部 canonical（"海外权益类 / 商品类 / 固收类 / A股权益类 / 货币类 / 港股大中华类 / 余额类"），0 个为 "QDII / 商品 / 固收 / 货币" 等自由命名
9. 19 项 isUserConfirmed 状态正确（user_correct 全部 true；ai_guess 0）
10. 已知多用户债明示为「1a.8.8 修复」
11. `mvn clean verify` 195/195 PASS
12. 报告诚实 / 无秘密 / 无临时产物

---

## 6. 失败兜底

| 失败类型 | 必须动作 | 禁止动作 |
|---|---|---|
| 类别名漂移（AI 写出 canonical 之外的） | prompt 升 v2.4 加别名 + 输出 schema 强制 | 静默接受 + 写 1004 错 |
| fund_category_map 跨 user 命中 | SQL 强制带 user_id；前端传 X-User-Id | 沿用 match 不带 user |
| 清仓后再出现弹窗误报 | 改 ai_guess 重置 prompt + 静默 | 强制弹窗 |
| last_seen_at 未更新 | 修 writeFundCategoryMap 更新逻辑 | 静默跳过 |
| 多用户债未修复 | Q4 顺手补 match 的 userId | 推到 1b.x |

---

## 7. 实际结果（落地后回填）

| 时间 | T-V3.2-S07 | 实际结果 | 根因/证据 | 后续 |
|---|---|---|---|---|
| ⏳ | ⏳ | ⏳ | ⏳ | ⏳ |

---

## 8. 引用与回退

- 工作计划：`2026-07-18_phase1a8-v3_2-work-plan.md`
- v3.1 真实验收报告：`2026-07-18_phase1a8-v3_1-real-data-check.md`
- v2 报告（被 v3.1 覆盖）：`2026-07-18_phase1a8-v2-real-data-check.md`
- 决策 1-8：参见 `docs/phase-0/decisions.md`

**回退条件**：v2.3 prompt 可降级为 v2.2（保持兼容）；CategoryEnum 不动；FundCategoryResolver 移除即可回退
