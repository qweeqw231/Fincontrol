# Decision 33 · 1b.4-pr6b 模块 B 大类确认 UX（v2 · 含消失-重现机制）

> **日期**：2026-07-25
> **状态**：✅ v2 已规划（1b.4-pr6b，全量 P0+P1+P2+R5）
> **v1**：2026-07-25 00:17（仅 D1-D4） → **v2**：2026-07-25 00:55（+D5-D7 + R5 消失-重现）
> **触发问题**：
> 1. AI 解析频繁把鹏华纯债债券D / 长城短债债券A / 安信新价值灵活配置混合A 误识别为 A 股权益类
> 2. 首页基金持仓明细显示「鹏华/长城在固收类 + 又被算到 A 股权益类」（双重计入）
> 3. 没有入库前手动纠正大类的 UI（每次 confirm 都依赖 AI 解析 + 后端 dedup 兜底）
> 4. 已 user_correct 的基金下次仍弹确认，重复劳动
> 5. 退出 preview modal 后 state 残留，下次进入直接套用上次的"未表态"状态
> 6. 基金消失-重现无机制，t0/t1 无法追踪

---

## 一、问题与现象（保持 v1）

### 1.1 鹏华/安信/长城 误识别为 A 股权益类
**安信新价值灵活配置混合A** 实际属于**固收类**（用户**事后**修正）→ **R1 测试用例**：preview modal dropdown 默认 A 股权益类 → 用户改固收类 → ✓ → update → 再次 confirm → DedupEngine 采纳 user_correct=固收类

### 1.2 双重计入（v1 已确诊）
详见 `test/1b/2026-07-25-step0-双重计入诊断.md`

### 1.3 入库前大类修正 UI 缺失
v1 已设计（D1 严格阻塞）

### 1.4 已 user_correct 重复弹确认（v2 新增 · R2）
**用户原话**："如果用户手动确认了当前识别的基金的分类，下次再次识别的时候不要出现再次让用户点击确认的情况"

**解决方案**：DataPage 打开 preview modal 前调 `GET /api/category-map/match?funds=...` → 把 user_correct 自动合并到 categoryOverrides → dropdown 默认值 = user_correct.category → badge 显示 ✅ 已确认 → 不计入 pendingCount

### 1.5 退出 modal state 残留（v2 新增 · R4）
**用户原话**："如果退出这个页面需要重新加载"

**解决方案**：DataPage unmount 时清 categoryOverrides / categoryDirty / overridesSaving → 下次进入重新调 match API

### 1.6 消失-重现机制（v2 新增 · R5）
**用户原话**：
- "如果有一个基金，曾经纳入 correct，但某次没出现，则需要特殊标记（自上次确认时间 t0 一直存续=FALSE，第一次消失时间 t1）"
- "如果下次再次出现，也要弹出确认，另外加一个特别提示框：上次确认时间 t0，您可能于 t1 及之前清仓，本次确认之后，更新 t0, t1"

**解决方案（v2 算法）**：见 §三·3.1

---

## 二、决策（v2 · 七条 UX 规则）

### 2.1 核心原则（v2）

**入库前最后一关：preview modal 暴露完整大类修正 UI，已 user_correct 自动套用，消失-重现机制特殊提示，未表态行严格阻塞 confirm。**

### 2.2 七条 UX 规则

| ID | 规则 | 实现位置 |
|---|---|---|
| D1 | **严格阻塞** | preview modal 中只要存在 🤖 ai_guess 行 → 「确认入库」按钮 disabled + tooltip「还有 N 条 AI 猜测未确认，请逐行核对」 |
| D2 | **警告 + 让用户选** | confirm 时检测「dropdown 改动但未点 ✓」的行 → 弹二次确认 modal「有 N 行 dropdown 已改动但未 ✓，是否全部提交？」→ [全部提交] / [仅提交已 ✓ 的] / [返回修改] |
| D3 | **实时联动** | 改 dropdown → 立刻触发 useMemo 重算 → preview modal「各类小计」金额即时刷新 |
| D4 | **双重计入修复** | `SnapShotConfirmService.writeAssetSnapshot()` 前置 `assetSnapshotMapper.updateIsLatestBySnapshotDate()` 清理旧行（v1 已实施） |
| **D5** | **user_correct 自动套用（v2 新增）** | DataPage 打开 preview modal 前调 `/api/category-map/match?funds=...` → 把 user_correct 合并到 categoryOverrides → dropdown 默认值 = user_correct.category → badge = ✅ 已确认（**不弹确认，不计入 pendingCount**） |
| **D6** | **退出 reload（v2 新增）** | DataPage unmount 时清 categoryOverrides / categoryDirty / overridesSaving / pendingReConfirms → 下次进入重新拉 match API → 用户重新表态 |
| **D7** | **消失-重现特别提示（v2 新增）** | fund_category_map.last_seen_snapshot_date / first_missing_snapshot_date 追踪清仓/重现 → preview modal 拿 match API 拿到 first_missing_snapshot_date 非 NULL → 渲染黄色 banner「上次确认 t0，您可能于 t1 及之前清仓，本次确认后将更新 t0/t1」 → 重现事件仍需点 ✓ 确认 |

### 2.3 canonical 类别列表（v1 不变）

dropdown 选项 = 7 canonical + "余额类" = 8 个，常量硬编码在 DataPage.jsx：

```jsx
const FUND_CATEGORIES = [
  '货币类', '固收类', '商品类', 'A股权益类',
  '海外权益类', '港股大中华类', '余额类',
]
```

---

## 三、消失-重现机制（v2 详细设计 · D7）

### 3.1 数据结构（fund_category_map 新增 2 字段）

```sql
ALTER TABLE fund_category_map ADD COLUMN last_seen_snapshot_date DATE NULL COMMENT '上次有该基金的 confirm snapshot_date';
ALTER TABLE fund_category_map ADD COLUMN first_missing_snapshot_date DATE NULL COMMENT '第一次发现该基金缺失的 confirm snapshot_date';
```

### 3.2 锚点计算（基于 last_seen MAX，与 is_current 解耦）

```java
LocalDate anchorDate = fundCategoryMapMapper.selectMaxLastSeenSnapshotDate(userId);
boolean isAnchorUpdate = anchorDate == null 
                       || !req.getSnapshotDate().isBefore(anchorDate);
// snapshotDate >= anchorDate → 新锚点（forward inference）
// snapshotDate <  anchorDate → 回填（仅入库，不修改 last_seen/first_missing）
```

**为什么不用 `is_current`**：
- `snapshot_meta.is_current` = 首页展示日期（用户选/自动默认），**不一定是最新的**
- `asset_raw.is_latest` = 同 snapshot_date 内多快照顺序（默认后覆盖前，预留未来扩展）
- "最新 snapshot_date" 标记 = **缺失**，本决策用 `last_seen MAX` 推断

### 3.3 算法（SnapShotConfirmService.confirm 内）

```java
if (isAnchorUpdate) {
    Set<String> currentFunds = parsedAssets.flatMap(...)
        .map(FundLine::getFundName).collect(Collectors.toSet());
    Set<String> knownFunds = fundCategoryMapMapper.selectFundNamesByUser(userId);
    
    for (String g : knownFunds) {
        FundCategoryMap rec = fundCategoryMapMapper.selectByUserAndFundName(userId, g);
        if (currentFunds.contains(g)) {
            // 本次有 → 更新 last_seen
            rec.setLastSeenSnapshotDate(snapshotDate);
            // 如果之前标记 first_missing → 重现事件（pending_re_confirm）
            if (rec.getFirstMissingSnapshotDate() != null) {
                pendingReConfirms.add(new ReConfirmEvent(g, rec.getFirstMissingSnapshotDate()));
                rec.setFirstMissingSnapshotDate(null);
            }
        } else {
            // 本次没 → 可能是清仓
            if (rec.getLastSeenSnapshotDate() != null
                && !rec.getLastSeenSnapshotDate().isAfter(snapshotDate)) {
                // last_seen ≤ snapshotDate 且 本次缺 → 第一次发现消失
                if (rec.getFirstMissingSnapshotDate() == null) {
                    rec.setFirstMissingSnapshotDate(snapshotDate);
                }
            }
        }
        fundCategoryMapMapper.upsertByFundName(rec);
    }
}
```

### 3.4 回填场景处理（关键 · 回答用户的"双向比对"疑问）

**用户原问**："如果用户再上传了更久远的一张，其中存在更久远的基金在那个久远的日子之前清仓的，这个怎么判定是否清仓？是不是需要在一个上传日里面，同时向前和向后比对？"

**答**：**不需要双向比对，但需要"以锚点为基准的单向 forward inference"**。

- 仅当 `snapshotDate >= anchorDate` 时触发消失-重现检测
- 历史回填仅 forward-only 入库（不入锚点状态）
- 这避免了双向比对的状态污染问题

### 3.5 前端渲染（D7 · 消失-重现特别提示）

```jsx
{firstMissingSnapshotDate && (
  <div className="modal-warning-banner re-confirm">
    ⚠️ <strong>{fundName}</strong> 上次确认时间 {lastSeenSnapshotDate}，您可能于 {firstMissingSnapshotDate} 及之前清仓。
    本次确认后，{lastSeenSnapshotDate} 与 {firstMissingSnapshotDate} 将被更新为本次的 snapshot_date。
  </div>
)}
```

CSS：
```css
.modal-warning-banner.re-confirm {
  background: #FFF7E6;
  border: 1px solid #FFD591;
  color: #874D00;
}
```

---

## 四、前端 useState 与派生（v2 · 5 个 useState）

```jsx
// 模块 B 新增 5 个 useState（v2 比 v1 多 1 个）
const [categoryOverrides, setCategoryOverrides] = useState({})
// shape: { [fundName]: { category, source: 'user_correct', confirmedAt, mappingId,
//                       lastSeenSnapshotDate, firstMissingSnapshotDate (可选) } }

const [categoryDirty, setCategoryDirty] = useState({})
// shape: { [fundName]: '用户改了 dropdown 但未 ✓ 的 category' }

const [overridesSaving, setOverridesSaving] = useState({})
// shape: { [fundName]: bool }

const [showSubmitDirtyModal, setShowSubmitDirtyModal] = useState(false)

const [pendingReConfirms, setPendingReConfirms] = useState({})
// v2 新增：D7 消失-重现事件缓存（preview modal 显示用）
// shape: { [fundName]: { firstMissingSnapshotDate, lastSeenSnapshotDate } }
```

---

## 五、关键交互细节（v2 完整版）

| 行为 | 实现 |
|---|---|
| preview modal 打开前 | 调 `GET /api/category-map/match?funds=...` → 拿 user_correct + last_seen + first_missing |
| dropdown 默认值 | `categoryOverrides[fundName]?.category ?? categoryDirty[fundName] ?? c.categoryName` |
| 行底色 | `isOverridden ? '#F6FFFA'` : `'#FFFBEA'`（绿/黄） |
| 状态徽章 | `isOverridden` → ✅ 已确认（绿底）；否则 → 🤖 ai_guess（黄底） |
| 消失-重现 banner（D7） | `firstMissingSnapshotDate` 非 NULL → 黄色 banner「您可能于 t1 及之前清仓」 |
| 「✓ 确认」按钮 disabled | `!isDirty \|\| saving` |
| 「✓ 确认」点击 | 调 `confirmOverride(fundName, dirty)` → 成功后写 categoryOverrides + 清 categoryDirty |
| 「↺ 重置」点击 | 调 `resetOverride(fundName)` → 成功后从 categoryOverrides 移除 |
| 「确认入库」按钮 disabled | `pendingCount > 0 \|\| step === 'confirming'`（D1 严格阻塞） |
| 二次确认 modal 触发 | `dirtyCount > 0` 时拦截 confirm → 显示 3 选项 |
| **modal 关闭 state 重置** | D6：DataPage unmount 时清 categoryOverrides / categoryDirty / overridesSaving / pendingReConfirms |
| **preview modal 重开** | D5：每次重新调 match API → user_correct 重新套用 |

---

## 六、修改文件清单（v2 完整）

**新增**：
- `docs/phase-1/decisions/decision-33-pr6b-category-ux.md`（本文件 · v2）
- `docs/phase-1/work-plans/1b/2026-07-25_1b4-pr6b-work-plan.md`
- `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-plan.md`
- `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`（执行后）
- `test/1b/2026-07-25-step0-双重计入诊断.md`（已完成）
- `test/1b/2026-07-25-1b4-pr6b-联调记录.md`（执行中续写）
- `test/1b/2026-07-25-r5-消失重现测试报告.md`（R5 单元 + 集成测试报告）
- `test/1b/2026-07-25-前端测试报告.md`（DataPage.test.jsx 20+ 用例）
- `fincontrol-frontend/src/tests/components/data/DataPage.test.jsx`
- `fincontrol-backend/scripts/1b/03-fund-category-map-add-fields.sql`（R5 schema 迁移）

**修改**：
- `fincontrol-backend/src/main/java/com/fincontrol/entity/FundCategoryMap.java`（+2 字段）
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/FundCategoryMapMapper.java`（+selectMaxLastSeenSnapshotDate）
- `fincontrol-backend/src/main/resources/mapper/FundCategoryMapMapper.xml`（+SQL）
- `fincontrol-backend/src/main/java/com/fincontrol/service/CategoryMapService.java`（返回新字段）
- `fincontrol-backend/src/main/java/com/fincontrol/controller/CategoryMapController.java`（DTO 加字段）
- `fincontrol-backend/src/main/java/com/fincontrol/dto/category/CategoryMapMatchItem.java`（+2 字段）
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java`（v1 D4 已加 + v2 R5 加消失-重现检测）
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapShotConfirmServiceP7Test.java`（D4 mock + R5 用例）
- `fincontrol-backend/src/test/java/com/fincontrol/service/DedupEngineTest.java`（+R5 集成用例）
- `fincontrol-frontend/src/pages/DataPage.jsx`（v1 ~150 行 + v2 +30 行）
- `fincontrol-frontend/src/styles/data-page.css`（v1 ~60 行 + v2 +15 行 D7 banner）

---

## 七、单元测试（v2 完整 25+ 用例）

### 7.1 前端 vitest + RTL（DataPage.test.jsx · 22 用例）

| ID | 测试 |
|---|---|
| T1 | 渲染基金明细表，dropdown 默认 = AI 原猜 |
| T2 | dropdown 选项 = 7 canonical + 余额类 |
| T3 | 未表态 badge = 🤖 ai_guess + 黄背景 |
| T4 | 改 dropdown 不点 ✓ 不调 API |
| T5 | 改 dropdown → 「确认入库」仍禁用（D1） |
| T6 | 「确认入库」+ pendingCount=0 → 启用 |
| T7 | 点 ✓ 调 update 1 次 + 正确 body |
| T8 | ✓ 后 badge ✅ + 行绿 + 按钮变 ↺ |
| T9 | ↺ 调 reset 1 次 |
| T10 | ↺ 后 badge 🤖 + dropdown 归位 |
| T11 | 改 dropdown 后各类小计实时联动（D3） |
| T12 | ✓ 期间按钮 loading + 禁用 |
| T13 | update 失败 → setError + badge 保持原状 |
| T14 | modal 关闭重开 overrides 重新加载（D6） |
| T15 | D2 二次确认：dirtyCount=0 不弹 |
| T16 | D2 二次确认：dirtyCount>0 弹 modal |
| T17 | D2 [全部提交] → 调 update N 次 + confirm |
| T18 | D2 [仅提交已 ✓ 的] → 清 dirty + confirm |
| T19 | D2 [返回修改] → 关闭二次确认 modal |
| **T20 (v2 新增)** | **D5：mock match API 返回 user_correct → dropdown 默认值=user_correct.category，badge=✅ 不计入 pendingCount** |
| **T21 (v2 新增)** | **R1 安信新价值用例：match 返回 user_correct=A 股权益类 → dropdown 默认 A 股 → 用户改固收 → ✓ → update → 再次 confirm → DedupEngine 采纳 user_correct=固收** |
| **T22 (v2 新增)** | **D7：match 返回 first_missing_snapshot_date 非 NULL → 黄色 banner 渲染** |

### 7.2 后端 JUnit（SnapShotConfirmServiceP7Test + DedupEngineTest · 8 用例）

| ID | 测试 | 状态 |
|---|---|---|
| B1 | D4-R1：writeAssetSnapshot 前调 updateIsLatestBySnapshotDate | v1 ✅ |
| B2 | D4-R2：两表对称 | v1 ✅ |
| B3 | 决策 32 R6：user_correct 采纳（已有） | 已有 |
| **B4 (v2 新增)** | **R5-1：confirm snapshotDate=锚点 → 标记 first_missing_snapshot_date** | v2 待写 |
| **B5 (v2 新增)** | **R5-2：confirm snapshotDate=锚点 + 重现 → 清 first_missing_snapshot_date + pendingReConfirms** | v2 待写 |
| **B6 (v2 新增)** | **R5-3：回填 snapshotDate < 锚点 → 仅入库，不修改 last_seen / first_missing** | v2 待写 |
| **B7 (v2 新增)** | **R5-4：锚点计算（anchorDate = MAX(last_seen_snapshot_date)）** | v2 待写 |
| **B8 (v2 新增)** | **R5-5：writeAssetSnapshot + R5 顺序正确（先 D4 清理再 R5 锚点更新）** | v2 待写 |

---

## 八、验收口径（v2 完整 V1-V18）

### 8.1 前端 DataPage.test.jsx（T1-T22）
- [ ] 22 个用例全绿

### 8.2 后端单测（B1-B8）
- [ ] B1-B3 v1 已绿
- [ ] B4-B8 v2 待补，全绿

### 8.3 联调端到端
- [ ] 上传 4 张截图 → preview modal 出现 19 行
- [ ] **R1**：match 返回安信 user_correct=A 股 → dropdown 默认 A 股 → 改固收 → ✓ → 再 confirm → asset_raw 安信 category=固收
- [ ] **D5**：match 返回 user_correct → dropdown 自动套用 + badge ✅ + 不阻塞
- [ ] **D7**：清仓 → 重现 → 黄色 banner 渲染
- [ ] **D6**：退出 modal → 重新进入 → 重新调 match → 重置
- [ ] **D1+D2**：严格阻塞 + 二次确认 modal
- [ ] **D3**：实时联动各类小计

### 8.4 双重计入修复
- [ ] 重跑 confirm → 旧 asset_snapshot[固收类, 890.58] 被清掉
- [ ] A 股权益类 total = 2202.27（不含鹏华/长城）
- [ ] sixCategoriesTotal = 6793.97（-890.58）
- [ ] totalAssetWithBalance = 6962.91

### 8.5 消失-重现机制（R5 端到端）
- [ ] 上传 D1（含 G）→ G last_seen=D1
- [ ] 上传 D2 > D1（无 G）→ G first_missing=D2 + banner 预告
- [ ] 上传 D3 > D2（含 G）→ G first_missing 清空 + pendingReConfirm + 黄色 banner 提示 + 用户 ✓ → first_missing 保持清空
- [ ] 回填 D_old < D1 → 不修改任何字段，仅入库

---

## 九、不在本次范围（避免扩大改动）

1. **fund_name 字符串篡改 bug**（match 接口发现"鹏华纯债债券D" → "鹏华纯债**债票**D"） → 留作 pr6c 独立 PR
2. **AI prompt 优化** → 独立 PR
3. **CorrectionPage 实现** → 仍按 Phase 2 计划
4. **批量改 user_correct** → 后续 PR
5. **`is_latest` 同日多快照并行管理** → 当前默认后覆盖前，预留未来扩展（用户原话）
6. **decision-32 R5/R6/R7**（已有）继续保留

---

## 十、附：本会话产出文件清单

| 阶段 | 文件 |
|---|---|
| v1（已完成） | `test/1b/2026-07-25-step0-双重计入诊断.md`<br>`docs/phase-1/decisions/decision-33-pr6b-category-ux.md`（v1）<br>`test/1b/2026-07-25-1b4-pr6b-联调记录.md`（v1）<br>SnapShotConfirmService.java + SnapShotConfirmServiceP7Test.java（v1 D4 修复） |
| v2（本版本） | 本文件 `decision-33-pr6b-category-ux.md`（v2）<br>`docs/phase-1/work-plans/1b/2026-07-25_1b4-pr6b-work-plan.md`<br>`docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-plan.md`<br>`fincontrol-backend/scripts/1b/03-fund-category-map-add-fields.sql` |
| 实施中 | DataPage.jsx / data-page.css / DataPage.test.jsx / R5 schema 迁移 / R5 service 更新 / R5 测试 |
| 完成后 | `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md`<br>过程测试报告 `test/1b/2026-07-25-r5-消失重现测试报告.md` 和 `test/1b/2026-07-25-前端测试报告.md` |