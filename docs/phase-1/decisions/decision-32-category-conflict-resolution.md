# Decision 32 · AI 跨 category 重复分类的优雅处理（user_correct 优先 + CATEGORY_CONFLICT 警告）

> **日期**：2026-07-24
> **状态**：✅ 已实施（1b.4-pr3plus）
> **触发问题**：14号截图上传并解析 → confirm 报 500（`Request failed with status code 500`）
> **真根因**：`DedupEngine.deduplicate()` line 165-171 抛 `BusinessException(INTERNAL_ERROR, ...)`，因为 AI 解析时把 `纳斯达克100ETF联接(QDII)C` 同时分到 `海外权益类` 和 `余额类` 两个 category，dedup 视为"数据矛盾"直接抛错。

---

## 一、问题与现象

### 1.1 现象（2026-07-24 用户报告）

```
我使用14号的样例，把默认7天限制成功更改为180天，上传文件并解析后，报错：
⚠ Request failed with status code 500

刷新页面，180天的设置仍然保留。
目前选择23号的截图可以正常点击上传并解析，没有报错。

首页14号：六大类分布
基于 100.00 元（不含余额类）
大类	金额（元）	占六大类	目标	偏差
货币类	100.00	100.00%	10%	+90.00%
```

### 1.2 14号 vs 23号 区别

- 23号 截图 AI 解析无跨 category 重复，dedup 正常。
- 14号 截图 AI 把 `纳斯达克100ETF联接(QDII)C` 同时分到 `海外权益类` 和 `余额类` 两条 block，dedup 维度 D 冲突 → 抛 500。

### 1.3 AI 给出的"真根因"（3 处需修正）

| 假设 | 实际情况 |
|---|---|
| ❌ `writeFundCategoryMap` 抛 `BusinessException 5001` | **错**。`writeFundCategoryMap` 不抛异常，调用 `upsertByFundName` 静默 upsert。 |
| ❌ upsert 返 200 但 `affected=2` = 0 inserts + 2 updates 是失败 | **错**。MySQL JDBC `ON DUPLICATE KEY UPDATE` 返 2 是正常 UPDATE 路径（INSERT 返 1），不是失败。 |
| ❌ `fund_category_map` 唯一键冲突 | **错**。唯一键 `uk_user_fund` 不会冲突，dedup 之后只会写 1 行。 |
| ✅ 真实根因 | **`DedupEngine.deduplicate()` line 165-171** 抛 `BusinessException(INTERNAL_ERROR, ...)`。 |

证据（`DedupEngine.java` 原 line 165-171）：
```java
if (!Objects.equals(existing.categoryName, categoryName)) {
    throw new BusinessException(
        ErrorCode.INTERNAL_ERROR,
        "fund '" + key + "' 在 " + existing.categoryName + " 与 " + categoryName + " 之间冲突"
    );
}
```

---

## 二、决策（2026-07-24）

### 2.1 核心原则

**AI 解析有不确定性，跨 category 重复不应阻塞入库；映射表的 user_correct 优先级最高。**

### 2.2 三条规则（按优先级）

| 优先级 | 规则 | 实现位置 |
|---|---|---|
| P0 | `fund_category_map.source='user_correct'` 的行**永远不被 AI 覆盖**（保留用户的明确分类） | `SnapShotConfirmService.writeFundCategoryMap`（P7 修复） |
| P1 | DedupEngine 维度 D 冲突时优先采纳 user_correct 的 category；未命中则保留**首次**出现的 category | `DedupEngine.deduplicate()` 冲突分支 |
| P2 | 不抛异常，记一条 `CATEGORY_CONFLICT` warning（含 fundName / keptCategory / aiCategory / resolution） | `DedupEngine.addCategoryConflictWarning` |

### 2.3 DedupInput 新增字段

```java
public record DedupInput(
    List<ParsedAsset> parsedAssets,
    Set<String> existingFundNamesForSnapshot,
    Map<String, String> existingUserCorrectCategories,  // ← 新增
    LocalDate snapshotDate,
    boolean confirmedOverwrite
) {}
```

`existingUserCorrectCategories` 来源：`fund_category_map` 中 `source='user_correct'` 的行，序列化为 `Map<fundName, category>`。

### 2.4 冲突分支逻辑

```java
if (!Objects.equals(existing.categoryName, categoryName)) {
    String userCorrectCategory = input.existingUserCorrectCategories() == null
        ? null : input.existingUserCorrectCategories().get(key);
    if (userCorrectCategory != null
            && !Objects.equals(userCorrectCategory, existing.categoryName)) {
        // 采纳 user_correct：覆盖 existing.categoryName，amount/holding/cumulative 用 AI 最新值
        existing.categoryName = userCorrectCategory;
        existing.amount = fund.getAmount();
        // ...
        addCategoryConflictWarning(warnings, key, existing.categoryName,
            categoryName, "resolved_by_user_correct", userCorrectCategory);
    } else {
        // 无 user_correct → 保留首次出现的 category（LinkedHashMap putIfAbsent 语义）
        addCategoryConflictWarning(warnings, key, existing.categoryName,
            categoryName, "kept_first", null);
    }
}
```

### 2.5 writeFundCategoryMap P7 修复

**修复前**（line 284-293，P7 修复不完整）：
```java
String finalCategory = cat.getCategoryName();  // ← 永远用 AI 的 category
String finalSource;
if (existingSource == null) finalSource = "ai_guess";
else if ("ai_guess".equals(existingSource)) finalSource = "ai_guess";
else finalSource = existingSource;
map.setCategory(finalCategory);  // ← 覆盖了 user_correct 的 category！
map.setSource(finalSource);
```

**修复后**（line 290-313）：
```java
String existingCategory = existing == null ? null : existing.getCategory();
String finalCategory, finalSource;
if (existingSource == null) {
    // 首次入库
    finalCategory = aiCategory;
    finalSource = "ai_guess";
} else if ("ai_guess".equals(existingSource)) {
    // AI 可更新自己的 guess
    finalCategory = aiCategory;
    finalSource = "ai_guess";
} else {
    // user_correct / user_manual：保留用户分类，拒绝 AI 覆盖
    finalCategory = existingCategory != null ? existingCategory : aiCategory;
    finalSource = existingSource;
}
```

### 2.6 前端 UI 透明化

预览 modal 顶部加黄色 banner：
```jsx
{parsedSummary.categoryConflictCount > 0 && (
    <div className="conflict-warning">
        ⚠ AI 解析时把 {parsedSummary.categoryConflictCount} 只基金分到了多个大类，
        系统已自动保留 fund_category_map 中的 user_correct 分类（若无则保留首次出现的分类）。
        请在「CorrectionPage」核对，或后续接入映射表维护功能后手动调整。
    </div>
)}
```

---

## 三、不在本次范围（未来 PR）

1. **用户映射表维护 UI**：用户手动设置 `user_correct` 的 CRUD 界面。
   - 路径：`/correction` 或新增 `/category-map` 页面
   - API：`/api/category-map` POST/PUT/DELETE 已有（参考 `CategoryMapControllerTest.java`），前端待补
2. **prompt 优化**：减少 AI 跨 category 重复的概率（v3 prompt 已经在改进）
3. **批量改 user_correct**：批量界面 + 历史快照智能重分类

---

## 四、影响面

| 维度 | 影响 |
|---|---|
| API 行为 | `POST /api/snapshot/confirm` 维度 D 冲突从抛 500 → 返 200（带 CATEGORY_CONFLICT warning） |
| 数据 | asset_raw / asset_snapshot 不受影响（写库时 fund 已 dedup） |
| 映射表 | fund_category_map 仍被 upsert，P7 修复后 user_correct 行被正确保护 |
| 警告 UX | 前端 preview modal 顶部 banner 提示用户有多少只基金被自动 dedup |
| 回归 | 23号（无冲突）行为不变；14号 之前 100.00 元 stale 数据被 overwrite 抹掉 |

---

## 五、相关决策 / 文件

- 决策 30：max_snapshot_age_days 默认值（PR3plus）
- 决策 31：settings 全局配置表（PR3plus）
- 决策 27：snapshot_meta is_latest 双层同步
- 1a.3 决策 8：fund_category_map 二态写（ai_guess / user_correct / user_manual）

修改文件：
- `fincontrol-backend/src/main/java/com/fincontrol/service/DedupEngine.java`
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java`
- `fincontrol-backend/src/main/java/com/fincontrol/service/ScreenshotService.java`
- `fincontrol-frontend/src/pages/DataPage.jsx`
- `fincontrol-frontend/src/styles/data-page.css`
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapShotConfirmServiceP7Test.java`（新增 R5/R6/R7）

## 六、单元测试

| 测试 | 场景 | 预期 |
|---|---|---|
| R1 | existing==null → 写入 source='ai_guess' | ✅ 通过 |
| R2 | existing.source='ai_guess' → 保持 ai_guess | ✅ 通过 |
| R3 | existing.source='user_correct' → 写 existing.category，source='user_correct' | ✅ 通过（之前会失败） |
| R4 | existing.source='user_manual' → 保持 user_manual | ✅ 通过 |
| **R5** | **AI 把同一 fund 分到 2 个 category → 不抛异常 + 保留首次 + CATEGORY_CONFLICT warning** | **新** ✅ |
| **R6** | **AI 跨 category + user_correct 已存在 → 用 user_correct + 无 CATEGORY_CONFLICT warning** | **新** ✅ |
| **R7** | **writeFundCategoryMap 收到 user_correct existing → 写 existing 的 category，不是 AI 的** | **新** ✅ |

---

## 七、附：本次 14号 数据复盘

- AI 解析的 ParsedAsset：
  - 海外权益类：[纳斯达克100ETF联接(QDII)C, 100.00, +5.32]
  - 余额类：[余额宝, 100.00, +0.50, 纳斯达克100ETF联接(QDII)C, 100.00, +5.32]  ← 重复
- 修复前 dedup：throw "fund '纳斯达克100ETF联接(QDII)C' 在 海外权益类 与 余额类 之间冲突" → 500
- 修复后 dedup：保留首次（海外权益类），丢弃余额类里的重复 + CATEGORY_CONFLICT warning
- 入库：19 只基金归位（海外权益类 1 只 + 余额类 1 只 + 其他 5 大类）
- 首页：六大类分布显示 100% 货币类（用户之前 stale 的测试值被 overwrite 抹掉 → 14号 重新入库后用真实分类）
