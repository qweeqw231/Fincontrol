# Phase 1a.3 dedup + 1a.3.1 + 1a.3.2 过程性验收报告

**日期**：2026-07-16
**测试者**：刘博丞
**Phase**：1a.3（快照确认 + dedup 引擎前置 + 1a.3.1 entity/mapper + 1a.3.2 service 层）
**状态**：✅ **历史阶段 3 环节完成；1a.3.3 / 1a.3.4 后续进展见补充报告**

> 后续补充：[2026-07-16_phase1a3-supplemental-acceptance.md](./2026-07-16_phase1a3-supplemental-acceptance.md)。补充报告确认 1a.3 业务逻辑测试 6/6 PASS，但真实 MySQL 持久化和前后端端到端验收仍待统一测试。

---

## 1. 验收目标

按 `docs/phase-1/subphase-plan.md` 1a.3 拆解：

| 子阶段 | 范围 | 状态 |
|---|---|---|
| **dedup 多图合并** | DedupEngine 5 维 + 9 单测 | ✅ 完成 |
| **1a.3.1** entity + mapper | 3 entity + 3 mapper（含 13 个自定义 SQL） | ✅ 完成 |
| **1a.3.2** service 层 | SnapShotConfirmService + 2 DTO（@Transactional + DedupEngine 集成 + 镜像校验） | ✅ 完成 |
| 1a.3.3 controller 层 | SnapshotController（含 10s 撤销 + 410 过期）| ✅ 代码完成；业务层测试通过，真实 HTTP/DB 待统一验收 |
| 1a.3.4 集成测试 | confirm + rollback 业务测试 | ✅ 6/6 PASS（Mapper mock）；真实持久化待统一验收 |

---

## 2. 测试环境

| 软件 | 版本 | 备注 |
|------|------|------|
| JDK | 17.0.12 LTS | Oracle |
| Maven | 3.9.16 | Apache |
| Spring Boot | 3.3.5 | — |
| MyBatis-Plus | 3.5.9 | — |
| MySQL | 8.0.46 | 远程开发库（生产） |
| H2 | test scope | 单测内存库（MODE=MySQL 兼容） |
| Lombok | 1.18.x | 编译期 |

---

## 3. 环节 A：dedup 多图合并（前置）

### 3.1 设计文档

- **`docs/phase-1/designs/1a3-dedup-strategy.md`**（220 行）
  - 5 维 dedup 完整定义（A fileId / B SHA256 / C fund_name / D category / E same-day）
  - 3 种集成位置选项（X 客户端 / Y 后端入库前 / Z 新增 parse-batch 端点）
  - dedupReport 响应字段（inputRecordCount / mergedRecordCount / droppedCount / warnings）
  - 3 层测试金字塔（unit / integration / e2e）+ 4 项风险缓解

### 3.2 DedupEngine 核心类

- **`fincontrol-backend/src/main/java/com/fincontrol/service/DedupEngine.java`**（~280 行）
  - 纯 Java 函数式 — 无 Spring 依赖 — 单测独立可跑
  - 5 维 dedup 完整实现：
    - **A**：按 `conversationId`（fileId）整张图覆盖
    - **B**：占位（**留 1a.4** 接入 SHA256；当前用 aiRawResponse 简化）
    - **C**：按 `fund_name` 后入优先
    - **D**：按 `category` 累加金额，**跨类冲突抛 `INTERNAL_ERROR`**
    - **E**：检测同日已有 → 警告 `OVERWRITE_REQUIRED`（除非 `confirmedOverwrite=true`）

### 3.3 DedupEngineTest 单测

- **`fincontrol-backend/src/test/java/com/fincontrol/service/DedupEngineTest.java`**（~340 行）
  - 9 个 case（8 覆盖 5 维 + 1 placeholder）
  - **mvn test 9/9 PASS，BUILD SUCCESS，3.148s**

| # | Case | 维度 | 输入 | 期望 |
|---|------|------|------|------|
| 1 | `dedup_dropsDuplicateFileId` | A | 3 record（2× fileId=A + 1× B） | merged=2, fileId=A amount=200（后入优先） |
| 2 | `dedup_sha256NotImplementedYet` | B 占位 | 3 record（fileId 全部=A 但 minimax 返不同 aiRawResponse） | merged=1（仅 A 维度生效），amount=300 |
| 3 | `dedup_mergesSameFundNameDifferentScreens` | C | 2 record（同 fund_name 天弘纳指A，不同 fileId，amount 100 vs 200） | merged=1, amount=200（后入优先） |
| 4 | `dedup_aggregatesByCategory` | D | 2 record（不同 fund 同 category） | merged=2, 权益类 categoryTotal=1000 |
| 5 | `dedup_throwsOnFundNameCategoryConflict` | D 冲突 | 2 record（同 fund 不同 category） | 抛 `INTERNAL_ERROR` 含 "天弘纳指A" + "权益类" + "商品类" |
| 6 | `dedup_warnsOnExistingSnapshotDate` | E | existingFundNames 非空 + 未确认 | 1 个 warning `OVERWRITE_REQUIRED` 含 "2026-07-16" + "2" |
| 7 | `dedup_noWarnWhenConfirmedOverwrite` | E | existingFundNames 非空 + confirmedOverwrite=true | 0 warning |
| 8 | `dedup_combinesAllDimensions` | 全维度 | img1 + img2 + img3（fileId=img1 重复） | merged=2（A 维度整图替换后 img1 余额宝 100 丢失），warnings=0，total=1100 |
| 9 | `dedup_emptyInput` | 边界 | 0 record | merged=0, warnings=[] |

### 3.4 Git 状态

- commit `1457a84` ✅ 已 push 远端
  - `docs/phase-1/designs/1a3-dedup-strategy.md`
  - `DedupEngine.java`（280 行）
  - `DedupEngineTest.java`（340 行）

---

## 4. 环节 B：1a.3.1 entity + mapper

### 4.1 3 个 entity

- **`fincontrol-backend/src/main/java/com/fincontrol/entity/AssetRaw.java`**（65 行）
  - 12 字段：id / user_id / snapshot_date / fund_name / fund_code / category / amount / profit / source / created_at / is_latest / confirmed_at
  - `@TableName("asset_raw") + @TableField` 字段映射
- **`fincontrol-backend/src/main/java/com/fincontrol/entity/AssetSnapshot.java`**（65 行）
  - 11 字段：id / user_id / snapshot_date / category / total_amount / target_ratio / actual_ratio / balance_fund / sub_detail / is_latest / created_at / updated_at
- **`fincontrol-backend/src/main/java/com/fincontrol/entity/FundCategoryMap.java`**（42 行）
  - 5 字段：id / user_id / fund_name / category / source / confirmed_at

### 4.2 3 个 mapper（继承 BaseMapper<> + 自定义 SQL）

- **`AssetRawMapper.java`**（65 行） — 4 个自定义 SQL：
  - `sumAmountByUserAndDateAndCategory`（维度 D 镜像校验）
  - `selectFundNamesByUserAndDate`（同 user+date 下 fund_name 集合）
  - `updateIsLatestBySnapshotDate`（1a.8 撤销）
  - `selectAllFundNamesByUser`
- **`AssetSnapshotMapper.java`**（95 行） — 5 个自定义 SQL：
  - `upsertByCategory`（`ON DUPLICATE KEY UPDATE`）
  - `countLatestByUserAndDateAndCategory`（维度 D 镜像校验）
  - `updateIsLatestBySnapshotDate`（1a.8 撤销）
  - `selectLatestBeforeDate`（恢复上一版）
  - `selectLatestByUserAndDate`（1a.4 启动用）
- **`FundCategoryMapMapper.java`**（65 行） — 4 个自定义 SQL：
  - `upsertByFundName`（`ON DUPLICATE KEY UPDATE`）
  - `selectByUserAndFundName`（1a.5 GET /api/category-map/match）
  - `selectFundNamesByUser`（维度 D 镜像校验）
  - `selectFundNamesByUserAndSnapshotDate`（维度 E 检测）

### 4.3 Git 状态

- commit `2c8520f` ✅ 已 push 远端
  - 6 个新文件（363 行增）

### 4.4 1a.3.1 单测

- **未做**（移到 1a.3.4 集成测试阶段）— 单元测试阶段与集成测试阶段分离

---

## 5. 环节 C：1a.3.2 SnapShotConfirmService

### 5.1 DTO

- **`dto/snapshot/SnapshotConfirmRequest.java`**（~50 行 + @Data + @NoArgsConstructor + @AllArgsConstructor）
  - 7 字段：userId / snapshotDate / snapshotNote / parsedAssets / isIgnored / confirmedOverwrite / includeBalance
  - `@JsonFormat(pattern = "yyyy-MM-dd")` on snapshotDate
- **`dto/snapshot/SnapshotConfirmResult.java`**（~50 行 + @Data + @Builder + @NoArgsConstructor + @AllArgsConstructor）
  - 6 字段：assetRawInserted / assetSnapshotUpserted / dedupReport / warnings / rollbackAvailable / rollbackDeadline
  - `@Builder` 支持链式构造

### 5.2 SnapShotConfirmService 主类

- **`service/SnapShotConfirmService.java`**（~290 行 + @Service + @Transactional + 4 步流程 + 镜像校验）
  - 4 个 mapper 注入：AssetRawMapper, AssetSnapshotMapper, FundCategoryMapMapper, DedupEngine
  - 4 步流程：
    1. **Step 0 dedup 阶段**（不写库）— 调 DedupEngine.deduplicate(input, existingFundNames, date, overwrite)，维度 A/B/C/D/E 全部
    2. **Step 1 警告检查** — `OVERWRITE_REQUIRED` 警告 + `!confirmedOverwrite` → 返 warnings 不写库
    3. **Step 2 写三表** — `writeAssetRaw`（fund 粒度）+ `writeAssetSnapshot`（category 粒度，`balanceFund` 仅 `余额类`）+ `writeFundCategoryMap`（fund 粒度）
    4. **Step 3 镜像校验** — fund 集合相等 + category 总额相等（容差 0.01）+ is_latest=1 唯一
  - `@Transactional` 异常触发全局回滚
  - **Step 4 响应 + 1a.8 撤销钩子**：`rollbackAvailable=true` + `rollbackDeadline = now+10s`
  - **日期校验 [P0-1.5]** — 与系统当前日相差 > 7 天抛 `INVALID_SNAPSHOT_DATE`

### 5.3 Git 状态

- commit `51d36cc` ✅ 已 push 远端
  - 3 个新文件（356 行增）

---

## 6. DoD（Definition of Done）状态

| # | 验收项 | 状态 |
|---|--------|------|
| dedup 设计文档（5 维 + 3 位置 + 检验） | ✅ 220 行 |
| DedupEngine 5 维实现 | ✅ 280 行 |
| DedupEngineTest 9/9 PASS | ✅ 3.148s |
| 1a.3.1 3 entity + 3 mapper（含 13 个自定义 SQL） | ✅ 6 文件 363 行 |
| 1a.3.2 SnapShotConfirmService（@Transactional + DedupEngine 集成 + 镜像校验）| ✅ 3 文件 356 行 |
| **mvn compile 30 source files 无错** | ✅ 10.282s BUILD SUCCESS |
| Git push origin 累计 3 commit | ✅ 1457a84 / 2c8520f / 51d36cc |
| 1a.3.3 SnapshotController（POST/DELETE 端点）| ✅ 代码完成；业务层通过，真实 HTTP/DB 待统一验收 |
| 1a.3.4 confirm + rollback 业务测试 | ✅ 6/6 PASS（Mapper mock）；真实持久化待统一验收 |
| 1a.7 / 1a.8 / P0-1.2 / P0-3.2 | 🟡 已完成业务层勾选；真实 MySQL、HTTP 410 和完整 P0 待统一验收 |

---

## 7. 关联文档

- `docs/phase-1/designs/1a3-dedup-strategy.md`（220 行）— 5 维 dedup 设计 + 集成位置选项
- `docs/phase-1/subphase-plan.md`（1a.3 拆解）
- `docs/phase-1/checklists/phase-1a.md`（验收 checklist 7/24 项已勾）
- `docs/phase-1/acceptance-criteria.md`（1a.7 / 1a.8 / P0-1.2/1.3/1.5/3.2 共 6 项待勾）

---

**本次验收时间窗口**：
- dedup：1.5h（设计 + DedupEngine + 9 单测 + 2 修编译 + 1 修测试期望值）
- 1a.3.1：0.5h（3 entity + 3 mapper + 13 自定义 SQL）
- 1a.3.2：0.75h（2 DTO + SnapShotConfirmService + 镜像校验 + 1 修 Lombok 注解）
- **总 2.75h**（3 环节全闭环）

## 后续进度（由补充报告更新）

- `1a.3.3` SnapshotController + `1a.8` rollback：代码已完成；业务层通过，真实 HTTP/DB 待统一验收。
- `1a.3.4` confirm + rollback：`SnapShotConfirmServiceIT` 已 6/6 PASS；本轮使用 Mapper mock，真实持久化待统一验收。
- `1a.3` 补充性报告：[`2026-07-16_phase1a3-supplemental-acceptance.md`](./2026-07-16_phase1a3-supplemental-acceptance.md)。
- 下一阶段：整体子阶段 `1a.4` 快照查询 + 首页辅助 API。
