# Phase 1a.8 v3.2 工作计划（类别归一化 + FundCategoryResolver + last_seen_at + DELETE/reset/stale + 多用户预留）

**状态**：`IN_PROGRESS`〔2026-07-18 草拟，v3.1 真实闭环已 PASS，正在 v3.2 收尾〕
**配套验收计划**：[`2026-07-18_phase1a8-v3_2-acceptance-plan.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v3_2-acceptance-plan.md)
**v3.1 真实验收报告**：[`2026-07-18_phase1a8-v3_1-real-data-check.md`](../../test-records/manual-tests/2026-07-18_phase1a8-v3_1-real-data-check.md)（19/19 唯一 + 7884.68 + 0 偏差）
**代码基线**：`0107b78`（v3.1 fix 推送 main）
**配套 v2 work-plan**：[`2026-07-18_phase1a8-v2-work-plan.md`](2026-07-18_phase1a8-v2-work-plan.md)（v3 → v3.1 升级）
**API 契约**：[`api-contract.md §2.2 / §3.2 / §7.1 / §7.2`](../../../phase-0/api-contract.md)

> 本文件遵循 docs/phase-1/work-plans/README.md 规则：与配套验收计划互链，按 15 步切片，每步有测试 ID 与可执行命令；commit 范围明确，secret 全部不入仓。

---

## 0. 背景

- **v3.1 真实闭环 PASS**：19/19 唯一 + 7884.68 ±0.01 + 国泰黄金C holding -45.25 / cumulative -40.24 双字段拆分对齐 ground truth
- **遗留问题（用户拍板确认）**：
  1. **类别名漂移**：v2.1 prompt 输出"QDII / 商品 / 固收 / 货币"等自由命名，ground truth 是"海外权益类 / 商品类 / 固收类 / 货币类 / A股权益类 / 港股大中华类 / 余额类"7 个 canonical 名 → 模型输出与文档不一致，前端 confirm 页面看到的是自由命名
  2. **确认态丢失风险**：fund_category_map 永不删除，但缺少 `last_seen_at` 字段，无法在前端"复盘"页显示"该基金 X 天未再出现"
  3. **多用户债**：1a.5 CategoryMapController.match 现状不传 userId，1b.x 多用户场景会跨用户命中
- **1a.8.8 范围**：规范化（QDII → 海外权益类）+ 双向 cache（user_correct 优先 / ai_guess 兜底）+ 清仓再出现不弹确认窗 + 用户可主动重置 / 删除 + 顺手补 match 的 userId 漏洞
- **不在 1a.8.8 范围**（明确记录到 1a.9+）：
  - **category master table**（增 / 删 / 改 类别名）：1a.5 + 4 轮评审已锁定 7 大类，CRUD 是 1a.9+ 范围
  - **完整 RBAC + tenant 隔离**：1b.x / Phase 5b 范围

---

## 1. 范围（in-scope / out-of-scope）

### 1.1 范围内（15 步）

- `fund_category_map` 加 `last_seen_at TIMESTAMP NULL` 列（H2 + MySQL + mapper + service 同步更新）
- `common/CategoryEnum.java` 新增：7 canonical + 别名表 + `fromAlias(String)` 校验
- `service/FundCategoryResolver.java` 新增：读时 user_correct 优先 / 写时 ai_guess 兜底 + `last_seen_at` 同步更新
- DTO 升级：`ParsedAsset.FundLine` / `AssetBalanceItem` / `SnapshotFundDetail` 三处都加 `holdingProfit` + `cumulativeProfit`（v3.1 已完成，本轮仅加 `isUserConfirmed: boolean`）
- `service/ScreenshotService.java`：mapToParsedAsset 调 FundCategoryResolver 归一化
- `service/SnapShotConfirmService.java`：writeFundCategoryMap 改二态写（首次 ai_guess / 已确认 user_correct + `last_seen_at = NOW()`）
- `controller/CategoryMapController.java`：补 `match` 的 `?userId=N` + `X-User-Id` 兼容 + `update` 加 userId 校验 + 新增 `DELETE /{userId}/{fundName}` + `POST /reset` + `GET /stale?days=N`
- `prompt_versions` v2.2 → **v2.3**（7 canonical + 别名映射）
- fixture v3.1 → v3.2（类别名全用 canonical；新增 `last_seenAt` 与 `isUserConfirmed` 字段）
- e2e 脚本升级：expected category 走 canonical 校验
- 测试：`CategoryEnumTest` + `FundCategoryResolverTest` + `SnapShotConfirmServiceTest` 扩 + `CategoryMapControllerTest`
- `mvn clean verify` 186 → 预计 195/195 PASS
- 重跑四图 OCR E2E：0 偏差 + 类别名一致
- 文档：decision 8 + phase-1a.md 1a.8.8 ✅ + 已知多用户问题明示

### 1.2 范围外（明示移交给 1a.9+）

- ❌ category master table（增 / 删 / 改 类别名 CRUD）：1a.9+ 单独阶段
- ❌ 完整 RBAC + tenant 隔离：1b.x / Phase 5b
- ❌ 类别名 fallback prompt 策略迁移：1a.9（前端 confirm 页面升级）

---

## 2. 15 步实施计划

| 步 | 内容 | 主要文件 | 关联测试 / 验证 |
|---|---|---|---|
| 1 | `fund_category_map` 加 `last_seen_at TIMESTAMP NULL` + H2 + MySQL schema | `docs/phase-0/db-schema.sql` §3 / `fincontrol-backend/src/test/resources/schema-h2.sql` / `entity/FundCategoryMap.java` / `mapper/FundCategoryMapMapper.java` | MySQL DDL：`ALTER TABLE fund_category_map ADD COLUMN last_seen_at TIMESTAMP NULL AFTER confirmed_at` |
| 2 | 新增 `common/CategoryEnum`（7 canonical + 别名 + `fromAlias(String)` 校验） | `fincontrol-backend/src/main/java/com/fincontrol/common/CategoryEnum.java` | T-V3.2-01 CategoryEnumTest（7 canonical 全映射 + 未知名抛 1004 错） |
| 3 | 新增 `service/FundCategoryResolver` | `fincontrol-backend/src/main/java/com/fincontrol/service/FundCategoryResolver.java` | T-V3.2-02 FundCategoryResolverTest（user_correct 优先 / ai_guess 兜底 / 未知名 fallback） |
| 4 | DTO 加 `isUserConfirmed: boolean` | `dto/screenshot/ParsedAsset.java` / `dto/asset/AssetBalanceItem.java` / `dto/snapshot/SnapshotFundDetail.java` | T-V3.2-03 DTO 字段存在性 |
| 5 | `ScreenshotService.mapToParsedAsset` 调 resolver | `service/ScreenshotService.java` | T-V3.2-04 解析后 FundLine.categoryName = canonical，isUserConfirmed 反映 fund_category_map 状态 |
| 6 | `SnapShotConfirmService.writeFundCategoryMap` 改二态 | `service/SnapShotConfirmService.java` | T-V3.2-05 首次 → ai_guess；已确认 → user_correct + last_seen_at = NOW() |
| 7 | `CategoryMapController` 补 match userId + 新增 DELETE / reset / stale | `controller/CategoryMapController.java` / `mapper/FundCategoryMapMapper.java` | T-V3.2-06 match 跨 user 不命中；T-V3.2-07 DELETE 仅删自己 user；T-V3.2-08 reset 改 ai_guess；stale 列表 |
| 8 | prompt_versions v2.2 → v2.3（7 canonical + 别名映射） | MySQL `UPDATE prompt_versions SET version='v2.3', change_reason='1a.8.8 类别归一化 + 双向 cache' WHERE prompt_name='screenshot_parser'` | T-V3.2 真实四图 OCR E2E 0 偏差 |
| 9 | fixture v3.1 → v3.2（类别名全用 canonical + `lastSeenAt` 字段） | `fincontrol-backend/src/test/resources/fixtures/phase1a8-real-four-pages.json` | T-V3.2-S05 fixture 自检 |
| 10 | e2e 脚本升级：expected category 走 canonical 校验 | `fincontrol-backend/scripts/1a8/01-real-four-page-e2e.ps1` | T-V3.2-S07 E2E 比对 |
| 11 | 测试：`CategoryEnumTest` + `FundCategoryResolverTest` + `SnapShotConfirmServiceTest` 扩 + `CategoryMapControllerTest` | `fincontrol-backend/src/test/java/com/fincontrol/{common,service,controller}/...` | 195/195 PASS |
| 12 | `mvn clean verify` | `fincontrol-backend/pom.xml` | BUILD SUCCESS，195/195 PASS，JaCoCo ≥ 60% |
| 13 | 重跑四图 OCR E2E | `scripts/1a8/01-real-four-page-e2e.ps1` | T-V3.2-S07 0 偏差 + 类别名一致（QDII → 海外权益类 等） |
| 14 | 文档：decision 8 + phase-1a.md 1a.8.8 ✅ + 已知多用户问题明示 | `docs/phase-0/decisions.md` / `docs/phase-1/checklists/phase-1a.md` | 1a.8.8 标 PASS |
| 15 | commit + push origin main | repo 根 | `git add ... && git commit -F .tmp/commit-msg-1a8.8.txt && git push origin main` |

**总时间预算**：~30-45 分钟

---

## 3. Schema 升级

```sql
-- 1a.8.8 step 1：fund_category_map + last_seen_at
ALTER TABLE fund_category_map
  ADD COLUMN last_seen_at TIMESTAMP NULL AFTER confirmed_at;
```

H2 测试 schema 同步。

---

## 4. 别名映射（`CategoryEnum` 内部表）

| Canonical | 别名（AI 常见写法） |
|---|---|
| 货币类 | 货币、货基、货币基金 |
| 固收类 | 固收、债券、纯债、短债、固收+ |
| 商品类 | 商品、黄金、大宗商品 |
| A股权益类 | A股、股票、A股权益、股票指数 |
| 海外权益类 | 海外权益、QDII、海外股票、海外QDII、纳斯达克 |
| 港股大中华类 | 港股、大中华、港股QDII、恒生 |
| 余额类 | 余额、货币基金、余额宝 |

未知名 → fallback 到 raw（前端 confirm 页面高亮）+ 抛 `INVALID_CATEGORY_NAME` 1004 错。

---

## 5. 多用户预留（Q4 拍板顺手补）

`CategoryMapController.match` 现状不传 userId（多用户场景会跨用户命中），1a.8.8 顺手补：
- `match` API 接受 `?userId=N` + `X-User-Id` header 兼容
- `update` API 同步加 userId 校验
- DELETE 路径 `{userId}` 占位 + header 默认 1
- 文档明示：「1a.8 match/update 之前跨 user 命中是已知债；1a.8.8 修复」

---

## 6. 风险规避

- **清仓后再出现** → last_seen_at 保留映射，re-confirm 不弹窗；stale API 提示前端
- **多用户** → match/update 走 userId；DELETE 路径带 userId 预留扩展
- **类别名漂移** → 写端点拒绝非 canonical / 非别名，1004 错
- **fixture 漂移** → 19 项 expected 改 canonical 名（一次性同步）
- **缓存不一致** → 解析路径统一走 FundCategoryResolver（不直读 DB）

---

## 7. 提交清单（commit + push）

```text
fix(1a.8.8): category canonicalization + last_seen_at + multi-user reserved
```

包含 20+ 文件（与 step 1-15 对应）：

- docs/phase-0/db-schema.sql（+last_seen_at + 决策 8 注释）
- docs/phase-0/decisions.md（+决策 8：类别归一化 + 双向 cache + 多用户预留）
- docs/phase-1/checklists/phase-1a.md（1a.8.8 ✅ + 已知多用户债明示已修）
- docs/test-records/manual-tests/2026-07-18_phase1a8-v3_1-real-data-check.md（v3.1 验收报告）
- docs/test-records/manual-tests/2026-07-18_phase1a8-v3_2-acceptance-plan.md（v3.2 验收计划）
- docs/phase-1/work-plans/2026-07-18_phase1a8-v3_2-work-plan.md（本文件）
- fincontrol-backend/src/main/java/com/fincontrol/common/CategoryEnum.java（新）
- fincontrol-backend/src/main/java/com/fincontrol/service/FundCategoryResolver.java（新）
- fincontrol-backend/src/main/java/com/fincontrol/{entity/FundCategoryMap,dto/{screenshot/ParsedAsset,asset/AssetBalanceItem,snapshot/SnapshotFundDetail},service/{ScreenshotService,SnapShotConfirmService},controller/CategoryMapController,mapper/FundCategoryMapMapper}.java（改）
- fincontrol-backend/src/test/resources/{schema-h2.sql,fixtures/phase1a8-real-four-pages.json}（改）
- fincontrol-backend/src/test/java/com/fincontrol/{common,service,controller}/...Test.java（新增）
- fincontrol-backend/scripts/1a8/01-real-four-page-e2e.ps1（升级 expected category canonical）
- MySQL：`UPDATE prompt_versions SET version='v2.3', change_reason='1a.8.8 类别归一化 + 双向 cache + 多用户预留' WHERE prompt_name='screenshot_parser'`

不包含：application-local.yml / uploads/ / ocr-results/ / api-test-output/ / pre-v3.1-failed

---

## 8. 验收判据

详见配套 v3.2 验收计划。1a.8.8 PASS 判据：
- 4/4 upload + 4/4 parse code=0
- 19/19 唯一 + 7884.68 ±0.01
- 19/19 类别名全部 canonical（"海外权益类 / 商品类 / ..."而非"QDII / 商品"）
- 19/19 isUserConfirmed 状态正确（ai_guess 在 confirm 后变 user_correct）
- DELETE 跨 user 隔离；stale 默认 90 天
- 单元 + 集成 + 算法测试 195/195 PASS
- 已知多用户债明示为「1a.8.8 修复」

---

## 9. 引用与回退

- v3.1 真实验收：`2026-07-18_phase1a8-v3_1-real-data-check.md`
- v3 报告（被 v3.1 覆盖）：`2026-07-18_phase1a8-v2-real-data-check.md`（保留 v3 内容）
- v2 / v3.1 work-plan 同目录（保留历史）
- 决策 1-7：参见 `docs/phase-0/decisions.md`（决策 8 为 v3.2 新增）

**回退条件**：无（schema 仅 +1 列，CRUD 与读路径都安全，prompt v2.3 可回退到 v2.2）
