# 1b4pr6b 模块 B · 完整工作计划

> **会话时点**：2026-07-25 00:55
> **范围**：C 选项 · P0 + P1 + P2 + R5 全做
> **决策依据**：`docs/phase-1/decisions/decision-33-pr6b-category-ux.md`（v2）
> **总工作量**：~600 行代码 + 30 个测试用例 + 联调

---

## 一、目标与边界

### 1.1 目标（v2 七条 UX 规则）

| ID | 规则 |
|---|---|
| D1 | 严格阻塞 confirm（未表态行不允许入库） |
| D2 | 二次确认 modal（dropdown 改动但未点 ✓ 拦截） |
| D3 | 实时联动各类小计（改 dropdown 立即刷新） |
| D4 | 双重计入修复（writeAssetSnapshot 前清理旧行，v1 已完成） |
| D5 | user_correct 自动套用（调 match API，不重复弹确认） |
| D6 | 退出 reload（DataPage unmount 清 state） |
| D7 | 消失-重现特别提示（fund_category_map + last_seen + first_missing） |

### 1.2 边界（不在本轮范围）

- ❌ fund_name 字符串篡改 bug（鹏华纯债债券D → 鹏华纯债债票D） → 留 pr6c
- ❌ AI prompt 优化
- ❌ CorrectionPage（仍 Phase 2）
- ❌ 批量改 user_correct
- ❌ `is_latest` 同日多快照并行管理（预留未来扩展）
- ❌ `final_report (1).md` 数据源（用户明确不用）

---

## 二、实施步骤（16 个 Step）

### 阶段 A · 计划落盘（已完成）

- ✅ Step 0 决策 33 v2（决策文档更新）
- 🔄 Step 1 工作计划（本文件）
- ⏳ Step 2 验收计划

### 阶段 B · 后端 R5（DB schema + Service + Controller）

- ⏳ Step 3 SQL 迁移脚本 `fincontrol-backend/scripts/1b/03-fund-category-map-add-fields.sql`
  - 加 `last_seen_snapshot_date DATE NULL`
  - 加 `first_missing_snapshot_date DATE NULL`
  - 加注释说明

- ⏳ Step 4 FundCategoryMap entity + Mapper
  - `entity/FundCategoryMap.java` 加 2 字段
  - `mapper/FundCategoryMapMapper.java` 加 `selectMaxLastSeenSnapshotDate(Long userId)` 方法
  - `mapper/FundCategoryMapMapper.xml` 加对应 SQL

- ⏳ Step 5 CategoryMapService + Controller + DTO
  - `dto/category/CategoryMapMatchItem.java` 加 2 字段
  - `service/CategoryMapService.java` match 返回时填充 2 字段
  - `controller/CategoryMapController.java` 不变（DTO 自动）

- ⏳ Step 6 SnapShotConfirmService.confirm 加 R5 消失-重现检测
  - 在 writeFundCategoryMap 之后 / writeSnapshotMeta 之前调用
  - 锚点计算：`anchorDate = MAX(last_seen_snapshot_date) for user_id`
  - 仅当 `snapshotDate >= anchorDate` 时触发检测
  - 处理 knownFunds vs currentFunds 的 4 种情况
  - **pendingReConfirms 缓存**：reappearing funds 列入该集合，返回给前端

- ⏳ Step 7 后端测试（SnapShotConfirmServiceP7Test + 新建 R5 专用测试类）
  - B1-B3 v1 已绿
  - B4: R5-1 confirm 在锚点 → 标 first_missing
  - B5: R5-2 confirm 在锚点 + 重现 → 清 first_missing + pendingReConfirms
  - B6: R5-3 回填（snapshotDate < 锚点）→ 仅入库，不修改
  - B7: R5-4 锚点计算
  - B8: R5-5 writeAssetSnapshot + R5 顺序正确
  - mvn test 全绿

- ⏳ Step 8 重启 backend + curl `/actuator/health` 验 200

### 阶段 C · 前端模块 B（DataPage.jsx + CSS + 测试）

- ⏳ Step 9 DataPage.jsx 预览 modal 重构
  - 加 5 个 useState（categoryOverrides / categoryDirty / overridesSaving / showSubmitDirtyModal / pendingReConfirms）
  - 加常量 `FUND_CATEGORIES`（8 个）
  - 加派生计算：`getEffectiveCategory` / `rebuiltCategories` / `pendingCount` / `dirtyCount`
  - 加 API 调用：`fetchCategoryOverrides(fundNames)` / `confirmOverride` / `resetOverride` / `handleConfirm` / `doConfirm` / `submitAllDirty` / `submitOnlyVerified`
  - 加 `useEffect cleanup`（D6 退出 reload）
  - 改 preview modal 表格渲染（dropdown + 状态徽章 + 操作按钮 + D7 黄色 banner）
  - 改各类小计数据源（用 rebuiltCategories，D3）
  - 加「确认入库」按钮严格阻塞 + tooltip（D1）
  - 加二次确认 modal（D2）

- ⏳ Step 10 data-page.css ~75 行新增样式
  - `.badge` / `.badge-verified` / `.badge-guess` / `.modal-warning-banner` / `.modal-warning-banner.re-confirm` / `.fund-row-pending` / `.fund-row-verified` / `.category-dropdown` / `.confirm-btn` / `.reset-btn`

- ⏳ Step 11 DataPage.test.jsx 新建 22 用例
  - T1-T19 v1 决策 33 测试
  - T20 D5 match API 自动套用
  - T21 R1 安信新价值用例
  - T22 D7 消失-重现 banner 渲染
  - npm test 全绿

### 阶段 D · 联调 + 验收

- ⏳ Step 12 重启 backend（mvn package + java -jar）
- ⏳ Step 13 重跑 confirm 验证修复（curl 查 latest/detail）
  - 预期：固收类残留行被清
  - 预期：A 股权益类 = 2202.27
  - 预期：sixCategoriesTotal = 6793.97
- ⏳ Step 14 联调记录续写（`test/1b/2026-07-25-1b4-pr6b-联调记录.md`）
- ⏳ Step 15 过程测试报告落盘
  - `test/1b/2026-07-25-r5-消失重现测试报告.md`（R5 B4-B8 详细）
  - `test/1b/2026-07-25-前端测试报告.md`（T1-T22 详细）
- ⏳ Step 16 验收报告（`docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`）
  - V1-V18 全绿

---

## 三、文件清单

### 3.1 新增文件

| 文件 | 内容 |
|---|---|
| `docs/phase-1/work-plans/1b/2026-07-25_1b4-pr6b-work-plan.md` | 本文件 |
| `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-plan.md` | 验收计划 |
| `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md` | 验收报告（执行后） |
| `fincontrol-backend/scripts/1b/03-fund-category-map-add-fields.sql` | R5 DB schema 迁移 |
| `fincontrol-frontend/src/tests/components/data/DataPage.test.jsx` | 前端 22 测试 |
| `test/1b/2026-07-25-r5-消失重现测试报告.md` | R5 测试过程报告 |
| `test/1b/2026-07-25-前端测试报告.md` | 前端测试过程报告 |

### 3.2 修改文件

**后端**：
- `fincontrol-backend/src/main/java/com/fincontrol/entity/FundCategoryMap.java`
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/FundCategoryMapMapper.java`
- `fincontrol-backend/src/main/resources/mapper/FundCategoryMapMapper.xml`
- `fincontrol-backend/src/main/java/com/fincontrol/service/CategoryMapService.java`
- `fincontrol-backend/src/main/java/com/fincontrol/dto/category/CategoryMapMatchItem.java`
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java`（v1 已加 D4 + v2 加 R5）
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapShotConfirmServiceP7Test.java`
- `fincontrol-backend/src/test/java/com/fincontrol/service/DedupEngineTest.java`（+R5 集成测试）

**前端**：
- `fincontrol-frontend/src/pages/DataPage.jsx`
- `fincontrol-frontend/src/styles/data-page.css`

---

## 四、技术要点

### 4.1 R5 锚点算法

```java
LocalDate anchorDate = fundCategoryMapMapper.selectMaxLastSeenSnapshotDate(userId);
boolean isAnchorUpdate = anchorDate == null 
                       || !req.getSnapshotDate().isBefore(anchorDate);

if (isAnchorUpdate) {
    Set<String> currentFunds = parsedAssets.flatMap(...)
        .map(FundLine::getFundName).collect(Collectors.toSet());
    Set<String> knownFunds = fundCategoryMapMapper.selectFundNamesByUser(userId);
    
    List<ReConfirmEvent> pendingReConfirms = new ArrayList<>();
    for (String g : knownFunds) {
        FundCategoryMap rec = fundCategoryMapMapper.selectByUserAndFundName(userId, g);
        if (currentFunds.contains(g)) {
            rec.setLastSeenSnapshotDate(snapshotDate);
            if (rec.getFirstMissingSnapshotDate() != null) {
                pendingReConfirms.add(new ReConfirmEvent(g, rec.getFirstMissingSnapshotDate()));
                rec.setFirstMissingSnapshotDate(null);
            }
        } else {
            if (rec.getLastSeenSnapshotDate() != null
                && !rec.getLastSeenSnapshotDate().isAfter(snapshotDate)) {
                if (rec.getFirstMissingSnapshotDate() == null) {
                    rec.setFirstMissingSnapshotDate(snapshotDate);
                }
            }
        }
        fundCategoryMapMapper.upsertByFundName(rec);
    }
    // 把 pendingReConfirms 通过 SnapshotConfirmResult 返回给前端
}
```

### 4.2 前端 D5 + D6 + D7 集成

```jsx
async function openPreviewModal() {
    // D5：调 match API → 自动套用 user_correct
    const fundNames = parsedSummary.categories.flatMap(c => c.funds.map(f => f.fundName));
    const matchResp = await apiClient.get(ENDPOINTS.CATEGORY_MAP_MATCH, {
        params: { funds: fundNames.join(',') }
    });
    const overrides = {};
    const reConfirms = {};
    for (const item of matchResp.matchedFunds) {
        overrides[item.fundName] = {
            category: item.category,
            source: item.source,  // user_correct
            confirmedAt: item.confirmedAt,
            mappingId: item.mappingId,
            lastSeenSnapshotDate: item.lastSeenSnapshotDate,
        };
        if (item.firstMissingSnapshotDate) {
            reConfirms[item.fundName] = {
                firstMissingSnapshotDate: item.firstMissingSnapshotDate,
                lastSeenSnapshotDate: item.lastSeenSnapshotDate,
            };
        }
    }
    setCategoryOverrides(overrides);
    setPendingReConfirms(reConfirms);
}

// D6：unmount cleanup
useEffect(() => {
    return () => {
        setCategoryOverrides({});
        setCategoryDirty({});
        setOverridesSaving({});
        setPendingReConfirms({});
    };
}, []);

// D7：banner 渲染
{pendingReConfirms[fund.fundName] && (
    <div className="modal-warning-banner re-confirm">
        ⚠️ <strong>{fund.fundName}</strong> 上次确认时间 {pendingReConfirms[fund.fundName].lastSeenSnapshotDate}，
        您可能于 {pendingReConfirms[fund.fundName].firstMissingSnapshotDate} 及之前清仓。
        本次确认后，{...} 将被更新为本次的 snapshot_date。
    </div>
)}
```

---

## 五、风险与缓解

| 风险 | 缓解 |
|---|---|
| R5 schema 改动需 MySQL 兼容 | 写 SQL 兼容 8.0（ALTER TABLE + ADD COLUMN IF NOT EXISTS 用 try-catch 包裹） |
| 前端 DataPage.jsx 700+ 行 replace_in_file 反复失败 | 用 write_to_file 整段重写，或分多次小段修改 |
| 测试 mock R5 时序复杂 | 分层：B4-B8 在 SnapShotConfirmServiceP7Test 中分别 mock |
| 联调需要真实截图 | 用户提供 alipay_snapshot.html 或重跑现有快照 |
| `pendingReConfirms` 在 confirm 响应中传递 | 改 `SnapshotConfirmResult` DTO 加字段 |

---

## 六、过程报告

每个 Step 完成后，落盘过程报告到 `test/1b/`：
- 后端测试 → `test/1b/2026-07-25-r5-消失重现测试报告.md`
- 前端测试 → `test/1b/2026-07-25-前端测试报告.md`
- 联调 → 续写 `test/1b/2026-07-25-1b4-pr6b-联调记录.md`

最终验收报告 → `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`

---

## 七、确认

按用户原话"同意，全做，先落盘工作计划，然后是验收计划（均做更新），过程中的测试（重要）报告落盘C:\Users\lbc19\Desktop\Fincontrol\test\1b"，当前任务清单已建立，**接下来按 Step 顺序实施**。

---

## 八、收尾：pr6b 主页 固收类 缺失 bug 修复（2026-07-25 03:30+ · 1b4pr6b-recovery）

> **会话时点**：2026-07-25 03:30
> **触发条件**：联调验收 step 9 发现 homepage `六大类` 总值 6,433.22（应为 7,623.14），固收类 1,189.92 完全不出现在六大类分布
> **根因**：Fix D（commit 92c66ad）`buildLatestResponse()` 只遍历原始 AssetSnapshot 构造 summary；而 AI 把"长城短债 A / 鹏华纯债 D / 安信新价值 A"误归为 A 股权益类，从未在 asset_snapshot 表写固收类行。response.categories 数组里**根本没有固收类 entry**。
> **二次放大**：HomePage 用了 `sumSixTotal(categories) || snap.sixCategoriesTotal`；因为前者返回 6,433.22（5 类相加）是 truthy，把后端算对的 7,623.14 覆盖掉。

### 8.1 修复步骤

#### Step 17.1 后端 `SnapshotQueryService.buildLatestResponse()` —— 让响应 categories 始终含 7 大类（6 类 + 余额）

- 不再只迭代 `snapshots`。改为：以 **effectiveByCat 全集（货币类/固收类/商品类/A股权益类/海外权益类/港股大中华类；余额类按 includeBalance）** 为权威 keySet 构造 summaries。
- 对 `effectiveByCat` 中存在的 category：构造 summary，`categoryTotal = effectiveByCat.get(category)`，`funds = toFundDetailsWithOverride(...)`，fundCount = 该类下有效基金数。
- 对**原始 snapshots 里也存在**的 category：补回 `updatedAt` 等元数据。
- 对 effective 集合里有但 original snapshots 里没的（典型：固收类由 user_correct 引入）：直接生成新 summary。
- 这样保证 `sum(response.categories.非余额.categoryTotal) === response.sixCategoriesTotal` 严格成立。

#### Step 17.2 后端 `buildByDateResponse()` —— 同上修复

#### Step 17.3 后端 `buildHistoryItem()` —— 同上修复（`categoryCount` 也按 effective 集计）

#### Step 17.4 前端 `HomePage.jsx` —— 兜底优先信后端总额

- 把 `const sixTotal = sumSixTotal(categories) || safeNumber(snap.sixCategoriesTotal, 0)`
- 改为 `const sixTotal = safeNumber(snap.sixCategoriesTotal, 0) || sumSixTotal(categories)`
- 这样即便后端 categories 又漏类，前端仍用后端权威总额兜底，避免再次出现"总额缩水"事故。

#### Step 17.5 后端单测 `SnapshotQueryServiceTest` —— 补 1 用例

- **`latest_userCorrectAddsNewCategory_固收类`**
  - mock AssetSnapshot 仅含 5 大类（货币类/商品类/A股权益类/海外权益类/港股大中华类）+ 余额类
  - mock userCorrectMap 含「鹏华纯债债券D → 固收类」「长城短债债券A → 固收类」「安信新价值灵活配置混合A → 固收类」
  - mock asset_raw 三个对应行
  - 断言：`resp.categories` 含固收类 entry、`categoryTotal == 1189.92`、`sixCategoriesTotal == 7623.14`、`totalAssetWithBalance == 7764.08`

#### Step 17.6 重启后端 + 联调 curl 验证

- `scripts/1b/restart-backend.ps1`
- `curl 'http://localhost:8080/api/snapshot/latest?includeDetail=true' -H 'X-User-Id: 1'`
- 断言：`categories` 数组长度 = 7（含固收类）、`sixCategoriesTotal == 7623.14`、`balanceFund == 140.94`、`totalAssetWithBalance == 7764.08`

#### Step 17.7 重启前端 + 浏览器首页截图

- `npm run dev`
- 访问 `http://localhost:5174/`
- 截图核对：固收类行出现、A股权益类从 1,882.50 降至 692.58、固收类 1,189.92、六大类合计 7,623.14、总资产 7,764.08、基金数 18+1

#### Step 17.8 过程报告 + 验收签字

- 续写 `test/1b/2026-07-25-1b4-pr6b-联调记录.md`（v4 收尾版）
- 落 `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`（V6 全绿）

### 8.2 风险

| 风险 | 缓解 |
|---|---|
| 响应 categories 多出"固收类 0"（极端场景：user 当天一个固收类都没有） | 0 金额类别不出现在分布（前端 `cTotal > 0` 已自然隐藏）；response 里仍保留以便后续扩展 |
| `recomputeSixTotal` 路径切换导致老接口行为变化 | 总额字段保持兼容；categories 多一两条 entry 不影响按名字判断的各种 filter |
| user_correctMap 缺失时退化 | 走"原始 snapshots"分支，单测 `latest_ratiosAreRecomputed` 已覆盖 |

### 8.3 文件清单

**修改**：
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapshotQueryService.java`
- `fincontrol-frontend/src/pages/HomePage.jsx`
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapshotQueryServiceTest.java`

**新增**：无
