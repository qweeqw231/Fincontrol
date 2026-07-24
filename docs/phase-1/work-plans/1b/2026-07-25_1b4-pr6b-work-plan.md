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