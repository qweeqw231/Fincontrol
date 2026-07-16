# 1a.3 / 1a.4 Dedup Strategy — 5 维度去重

> **状态**：1a.3 启动前的契约填补 — 替代 `api-contract.md` 中缺失的多图 dedup 设计。
> **位置**：方案 A 已确认 = 位置 Y（后端入库前 dedup，1a.3 confirm 端点）+ 位置 Z（新增 parse-batch 端点，1a.4 启动时实现）。
> **对应 checklist**：[P0-1.2] 三表写入事务（保证 dedup 在事务内）+ [P0-1.3] 映射 UPDATE（已含 dedup 的"同 fund_name 二次出现" 处理）。

---

## 1. 核心需求

用户在 1a.2 测试期间观察：
- 1 天内可能上传多张支付宝截图（不同页面：首页 / 收益页 / 持仓页）
- 同一只基金可能在多张图中都出现（如"天弘纳指A" 在首页 + 持仓页都列出）
- 同张图可能因前端 bug 被重复上传
- 不去重会导致：
  - **asset_raw 行重复**（同一 fund_name 出现 2 次）
  - **asset_snapshot 累加错**（6 只基金 + 6 只 = 12 只，实际 6 只）
  - **minimax token 浪费**（重复调用 2 次）

---

## 2. 5 个 Dedup 维度

| 维度 | 触发场景 | 检测键 | 合并策略 | 实现位置 |
|------|----------|--------|----------|----------|
| **A. 同 fileId** | 用户连点 2 次"上传"按钮 | `fileId` | 完全丢弃重复（最新保留） | 1a.3 confirm + 1a.4 parse-batch |
| **B. 同 SHA256** | 同张图反复保存（缓存丢失后重传）| `SHA256(file content)` | 完全丢弃 | 1a.3 confirm |
| **C. 同 fund_name + 同 snapshot_date** | 多张图含同一只基金（最常见）| `fund_name + snapshot_date` | 同一只 fund 取**最后一张图**的数据 | 1a.3 confirm + 1a.4 parse-batch |
| **D. 同 category + 同 snapshot_date** | 多图合并入同一大类 | `category_name + snapshot_date` | 累加金额 + 取最新收益率 | 1a.3 confirm + 1a.4 parse-batch |
| **E. 同 snapshot_date 全量覆盖** | 同一天不同时段的两张图 | `snapshot_date + user_id` | **警告 + 用户确认 overwrite** | 1a.3 confirm |

---

## 3. DedupEngine 设计

**纯 Java 函数式 class**（无 Spring 依赖）— 5 维度全覆盖 + 单测独立可跑。

### 3.1 输入

```java
public record DedupInput(
    List<RawParseRecord> rawRecords,       // 1a.3 confirm 接收的 N 条 ParsedAsset
    Set<String> existingFundIds,           // 当前 user_id + snapshot_date 已存在的 fund_name 集合
    Map<String, ExistingSnapshot> existingSnapshots,  // 同 user_id + snapshot_date 已有快照
    Boolean confirmedOverwrite             // 维度 E 的"用户确认 overwrite"标志
) {}
```

### 3.2 输出

```java
public record DedupResult(
    List<RawParseRecord> filteredRecords,   // 维度 A/B 后保留的 record
    List<MergeAction> mergeActions,         // 维度 C/D 后生成的合并 action
    List<DedupWarning> warnings,            // 维度 E 触发的警告
    DedupReport report                       // 响应里返回的 dedupReport 字段
) {}
```

### 3.2 算法（5 维度串联）

```java
public DedupResult deduplicate(DedupInput input) {
    // 维度 A：去重 fileId
    Map<String, RawParseRecord> byFileId = input.rawRecords().stream()
        .collect(toMap(RawParseRecord::fileId, identity(), (a, b) -> b));  // 保留最后

    // 维度 B：去重 SHA256
    Map<String, RawParseRecord> byHash = byFileId.values().stream()
        .collect(toMap(
            r -> r.sha256(),
            identity(),
            (a, b) -> a.amount() >= b.amount() ? a : b  // 同图取金额大（数据更全）
        ));

    // 维度 C：同 fund_name + snapshot_date 合并
    // 同一只 fund 出现多次 → 取最后一张图
    Map<String, RawParseRecord> byFund = byHash.values().stream()
        .collect(toMap(
            r -> r.fundName() + "@" + r.snapshotDate(),
            identity(),
            (a, b) -> b,  // 保留最后（list 顺序后入优先）
            LinkedHashMap::new
        ));

    // 维度 D：同 category + snapshot_date 累加（SnapShotService 内部处理，DedupEngine 负责输出 record 列表）
    // 这里 DedupEngine 只去重到 fund 级，category 聚合在 AssetSnapshotService 内部

    // 维度 E：同 snapshot_date 已存在 → 警告
    List<DedupWarning> warnings = new ArrayList<>();
    for (String existingDate : input.existingSnapshots().keySet()) {
        warnings.add(new DedupWarning(
            "snapshot_date " + existingDate + " already exists; user must confirm overwrite",
            existingDate
        ));
    }
    if (input.existingSnapshots().isEmpty() || Boolean.TRUE.equals(input.confirmedOverwrite())) {
        warnings.clear();  // 确认覆盖 → 不警告
    }

    return new DedupResult(
        new ArrayList<>(byFund.values()),
        emptyList(),  // mergeActions 在 1a.3 confirm 阶段由 SnapShotService 内部处理
        warnings,
        new DedupReport(
            input.rawRecords().size(),
            byFund.size(),
            input.rawRecords().size() - byFund.size(),  // droppedCount
            warnings
        )
    );
}
```

### 3.3 单测覆盖矩阵

| 测试方法 | 维度 | 输入 | 期望输出 |
|----------|------|------|----------|
| `dedup_dropsDuplicateFileId` | A | 3 record（2 个 fileId=A + 1 个=B）| 2 record（A + B）|
| `dedup_dropsDuplicateSha256` | B | 3 record（同 SHA256）| 1 record |
| `dedup_mergesSameFundNameDifferentScreens` | C | 2 record（同一 fund_name 不同图）| 1 record（取后入）|
| `dedup_warnsOnExistingSnapshotDate` | E | 2 record + 已存在 snapshot_date | 1 warning |
| `dedup_noWarnWhenConfirmedOverwrite` | E | 同上 + confirmedOverwrite=true | 0 warning |
| `dedup_combinesAllDimensions` | ABCDE | 5 record 全维度混合 | 1 record + 0 warning |
| `dedup_emptyInput` | - | 0 record | 0 record + 0 warning |
| `dedup_preservesFundIdAcrossCategories` | D | 同一 fund_name 出现在不同 category | 报错（数据冲突）→ 警告 + skip |

---

## 4. 1a.3 confirm 端点集成

### 4.1 端点契约补充（api-contract.md v1.1 增量）

```
POST /api/snapshot/confirm
  Request:
    snapshotDate:   string YYYY-MM-DD     [P0-1.5]
    parsedAssets:    ParsedAsset[]         // 多 ParsedAsset（来自多张图）
    confirmedOverwrite: bool = false      // [P0-1.5] 同日存在时必须 true
  Response 200:
    assetRawInserted:  int                // 实际写入行数
    assetSnapshotUpserted: int            // 实际 upsert 行数
    dedupReport:                         // 新增字段
      inputRecordCount:  int
      mergedRecordCount:  int
      droppedCount:       int
      warnings:           DedupWarning[]
```

### 4.2 dedupReport 响应示例

```json
{
  "data": {
    "assetRawInserted": 14,
    "assetSnapshotUpserted": 6,
    "dedupReport": {
      "inputRecordCount": 21,
      "mergedRecordCount": 14,
      "droppedCount": 7,
      "warnings": [
        {"code": "OVERWRITE_REQUIRED", "message": "snapshot_date 2026-07-16 已存在; 设 confirmedOverwrite=true 可覆盖"}
      ]
    }
  }
}
```

### 4.3 事务边界（与 [P0-1.2] 一致）

```
@Transactional
public SnapshotConfirmResult confirm(SnapshotConfirmRequest req) {
    // 1. dedup 阶段（读 + 计算）
    DedupResult dedup = dedupEngine.deduplicate(...);

    // 2. 写 asset_raw 阶段（写）
    for (RawRecord r : dedup.filteredRecords()) {
        assetRawMapper.insert(r);
    }

    // 3. 写 asset_snapshot 阶段（写 + 镜像 update）
    for (CategoryBlock b : dedup.mergedCategories()) {
        assetSnapshotMapper.upsert(b);
    }

    // 4. 镜像校验（防前 1.3 文档的"明细存在但汇总缺失"）
    verifyRawAndSnapshotConsistency();

    // 任一步失败 → 整体回滚
}
```

---

## 5. 1a.4 `parse-batch` 端点（位置 Z）

### 5.1 端点契约

```
POST /api/screenshot/parse-batch
  Request:
    fileIds: string[]                     // 多 fileId 数组
    userId: long
  Response 200:
    conversations: string[]              // 多个 conversationId
    merged: ParsedAsset                   // 5 维度 dedup + 合并后的最终 ParsedAsset
    dedupReport: object                   // 同 4.2
    aiRawResponses: string[]              // 多个 minimax 原始响应（用于前端调试）
```

### 5.2 调用流程

```
1. 文件上传: N 张图 → N 个 fileId
2. parse-batch: N 个 fileId → 
   a. 并行调 minimax N 次（OkHttp async）→ 收集 N 个 ParsedAsset
   b. DedupEngine 5 维度去重 + 合并 → 1 个 merged ParsedAsset
   c. 响应前端 merged + dedupReport
3. 用户确认 → confirm 端点（走 4.3 事务）
4. confirm 内部对 merged 做 fund_name 校验 + asset_raw 写 + asset_snapshot upsert
```

### 5.3 性能与并发

- N 张图调 N 次 minimax：60-300s × N（用 async 桶限 5 并发）
- 1 张图 ≈ 1-2 RMB 折算（minimax M3 input/output tokens）
- 3 张图（用户典型场景）= 3-6 RMB / 次入库

---

## 6. dedupReport 响应字段

```java
public record DedupReport(
    int inputRecordCount,        // 用户上传的原始 record 数
    int mergedRecordCount,        // 5 维度去重后最终 record 数
    int droppedCount,             // inputRecordCount - mergedRecordCount
    List<DedupWarning> warnings  // 维度 E 等触发的警告
) {}

public record DedupWarning(
    String code,         // "OVERWRITE_REQUIRED" | "CATEGORY_CONFLICT" | "DATA_INCOMPLETE"
    String message,
    Map<String, Object> context    // 灵活补充上下文（如 fundName / 旧 amount）
) {}
```

---

## 7. 检验 / 测试方法

### 7.1 单元测试

`DedupEngineTest` 覆盖 5 维度 + 边界 case（见 §3.3 矩阵）。8 个 case + Mockito 5 个。

### 7.2 集成测试

`SnapshotConfirmServiceIntegrationTest` 用 H2 内存库：
- mock N 个 ParsedAsset → confirm → 查 asset_raw / asset_snapshot 表 → 验证行数与合并结果
- 重点：**维度 A** 同 fileId 校验幂等性、**维度 C** 跨图同 fund 去重

### 7.3 端到端测试

```cmd
rem 准备 2 张同一天的截图
copy C:\Users\test1.png C:\Users\test2.png
rem 两次 upload
curl ... /upload (test1) → fileId_1
curl ... /upload (test2) → fileId_2
rem parse-batch
curl ... /parse-batch -d '["fileId_1", "fileId_2"]'
rem 验证 dedupReport
mysql> SELECT COUNT(*) FROM asset_raw WHERE user_id=1 AND snapshot_date='2026-07-16';
rem 应 = 14（如果 2 张图各 7 只基金）而非 21
```

### 7.4 dedupReport 端到端可读性

响应中保留 `dedupReport.inputRecordCount` / `mergedRecordCount` / `droppedCount` 三个数 — 前端 UI 弹窗显示：
> "您上传了 21 只基金，自动合并为 14 只（去重 7 只同基金重复）"

---

## 8. 实施路线

| 步骤 | 内容 | 工期 | 关联 |
|------|------|------|------|
| 8.1 | `DedupEngine` 核心类 + 单测 8 case | 0.5 天 | 立即（独立可测）|
| 8.2 | 1a.3 confirm 端点集成 dedup | 0.5 天 | 1a.3 主任务内 |
| 8.3 | 1a.4 `parse-batch` 端点 | 0.5-1 天 | 1a.4 子阶段 |
| 8.4 | 维度 D（category 累加）的边界 case 测试 | 0.25 天 | 8.1 内 |

**总** 1.5-2 天（独立可测 → confirm 集成 → parse-batch）

---

## 9. 与现有契约的差异

| 现有契约 | 1a.3 增量（本文档） | 差异说明 |
|----------|---------------------|----------|
| 1a.5 `POST /api/screenshot/parse` 接收单 fileId | **保留不变** | 单图 parse 仍可用 |
| 1a.7 `POST /api/snapshot/confirm` 接收单 ParsedAsset | **修改为多 ParsedAsset[]** | confirm 接收 dedup 后的 record 列表 |
| 1a.23 `GET /api/parse-logs` | **保留** | 不影响 |
| ❌ 无 | 1a.4 新增 `POST /api/screenshot/parse-batch` | 多图 + dedup 端点 |

**契约版本**：v1.1（1a.3 启动后生效）

---

## 10. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| minimax 同一只基金返回不同字段（如 A 图 amount=100，B 图 amount=105）| 中 | 维度 C 取后入可能丢失正确值 | A 图先入 B 图后入时"后入优先"；用户可在 confirm 前 review |
| 维度 D 跨图 category 累加：6 大类 + 1 余额类 | 中 | 6 大类各自累加 + 余额类特殊处理 | confirm 响应分类输出 6 大类 + 1 余额类 |
| 维度 E 同日 overwrite 用户漏点确认 | 低 | 数据丢失 | confirm 响应 warnings 数组强制显示 |
| minimax API 限流（3 张图并发调用）| 中 | parse-batch 失败 | OkHttp 桶限 5 并发 + 指数退避 |

---

**关联文档**：
- `docs/phase-0/api-contract.md` — 主契约（v1.0 → v1.1 更新时引用本文档）
- `docs/phase-1/subphase-plan.md` — 1a.3 / 1a.4 子阶段计划
- `docs/phase-1/acceptance-criteria.md` — 1a.7 / 1a.8 / 1a.9 / 1a.23 acceptance 项
- `docs/phase-1/testing-guide-1a2.md` — 1a.2 现有测试模式（参考格式）

**实施状态**：
- [x] 设计文档（本文件）— 2026-07-16
- [ ] `DedupEngine` Java class + 单测 — 立即
- [ ] 1a.3 confirm 集成 — 1a.3 主任务内
- [ ] 1a.4 parse-batch 端点 — 1a.4 子阶段
