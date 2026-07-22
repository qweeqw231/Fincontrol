# 决策 27 — is_latest 双层语义 + 跨日期 is_current

> **状态**：🚧 设计稿（待 1b.2 补完后端实施 + 前端 UI）
> **日期**：2026-07-22
> **关联子阶段**：1b.2 补完 / 1b.3 数据管理页
> **关联 commit**：本次 1b.2 补完 commit (含 Bug 1+2 修复 + 本决策设计文档)
> **问题源头**：1b.2 验收后 0.72.0 workaround 测试覆盖 0716 19-fund 状态，且 0720 5-fund 残留；前端只展示 5 只基金
> **修复人**：刘博丞 + Cline

---

## 0. 背景

1b.2 验收通过时（2026-07-22 17:30），DB 状态是：
- 0716 19 只基金，totalAssetWithBalance=7850.38，is_latest=true
- 首页 /api/snapshot/latest 正常返回 19 只

随后 0.72.0 workaround 测试通过 curl 单独 confirm 0720，单图返回 5 只（0720-1 截图只识别 5 只）。结果：
- 0720 5 行写入 asset_raw，is_latest=true
- 旧的 0716 19 行 **未翻 is_latest=false**（Bug 1 — `SnapShotConfirmService.writeAssetRaw` 缺少 `updateIsLatestBySnapshotDate` 调用）
- 0720 5 行写入 asset_snapshot，is_latest=true
- 旧 0716 7 行 **同上未翻**

再次 `GET /api/snapshot/latest` 返回：
- `MAX(snapshot_date)` = 2026-07-20（因 0720 也有 is_latest=true）
- 渲染 0720 5 只

**用户反馈**："1b.2 还没有实现完全。前端居然只有五个基金，这是严重的过程性错误。"

---

## 1. 根因分析

### 1.1 Bug 1：`writeAssetRaw` 不翻旧行 is_latest

`AssetRawMapper.updateIsLatestBySnapshotDate(Long userId, LocalDate snapshotDate)` 已存在（1a.8 撤销流程用），但 `SnapShotConfirmService.writeAssetRaw` 调用前**从未**调用它。

```java
// 修复前
private int writeAssetRaw(SnapshotConfirmRequest req, DedupResult dedup) {
    int count = 0;
    // 1a.9：从 dedup 获取 totalAssetSource
    for (CategoryBlock cat : dedup.merged().getCategories()) {
        for (FundLine fund : cat.getFunds()) {
            AssetRaw row = new AssetRaw();
            ...
            row.setIsLatest(true);  // 新行 is_latest=true
            assetRawMapper.insert(row);  // 旧行 is_latest 还是 true！
        }
    }
}
```

后果：再次 confirm 同一日期时，DB 仍有 旧行 + 新行 都 is_latest=true。

### 1.2 Bug 2：`verifyMirror` SUM 不过滤 is_latest

`AssetRawMapper.sumAmountByUserAndDateAndCategory` 和 `selectFundNamesByUserAndDate` 两个 verifyMirror 用的 SQL，**不包含 `AND is_latest = true`**：

```java
@Select("SELECT COALESCE(SUM(amount), 0) FROM asset_raw " +
        "WHERE user_id = #{userId} AND snapshot_date = #{snapshotDate} AND category = #{category}")
```

后果：即使 Bug 1 修了，verifyMirror 校验 2 仍然 sum 全表（旧 + 新 = 2×），触发 5001 镜像校验失败。

### 1.3 之前的误判

我之前在 PLAN 阶段误判：是 cache 命中率问题。是 dedup 引擎没做 fundName dedup。**全错**。

真相：
- DedupEngine 维度 C 一直在做 fundName dedup（`DedupEngine.java` 152-179 行），正确
- cache 命中与否不影响 5001 错误
- 5001 错误是 `SnapShotConfirmService.verifyMirror` 校验 2 抛的，不是 DedupEngine 抛的

---

## 2. 决策 27 设计

### 2.1 双层 is_latest 语义

**层 1 — per-date is_latest**（每自然日）：
- `asset_raw.is_latest`：每 (user_id, snapshot_date) 最多 1 行 is_latest=true
- 旧批次的行 is_latest 翻 false（保留为历史）
- 新批次的行 is_latest=true
- 由 `SnapShotConfirmService.writeAssetRaw` **前**调用 `updateIsLatestBySnapshotDate` 实现

**层 2 — cross-date is_current**（跨日期）：
- 新表 `snapshot_meta` 存日期级元数据
- 每 (user_id) 最多 1 行 is_current=true（同日同 user 唯一）
- 默认 = `MAX(snapshot_date) WHERE is_latest=true`
- 用户可手动切换（前端 UI）

### 2.2 `snapshot_meta` 表设计

```sql
CREATE TABLE snapshot_meta (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  snapshot_date   DATE NOT NULL,
  is_latest       BOOLEAN NOT NULL DEFAULT FALSE,  -- per-date 标记(冗余存 asset_raw 已有)
  is_current      BOOLEAN NOT NULL DEFAULT FALSE,  -- 跨日期当前快照
  confirmed_at    DATETIME NOT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, snapshot_date),
  KEY idx_user_current (user_id, is_current),
  KEY idx_user_latest (user_id, is_latest)
);
```

**为何用新表而非 column on asset_raw/snapshot**：
- `is_current` 是日期级属性，不属于 category / fund 级别
- 多表 join 复杂
- 单独表独立演进（未来加 source / trigger 等）

### 2.3 confirm 流程（含决策 27）

```
POST /api/snapshot/confirm
   ↓
Step 0: DedupEngine.deduplicate (5 维度)
   ↓
Step 1: 检查 OVERWRITE_REQUIRED
   - 同 (user_id, snapshot_date) 已有 is_latest=true → 警告
   - 前端弹窗"是否覆盖 0716 已有 19 只？"
   - 用户确认 confirmedOverwrite=true → 继续
   ↓
Step 2: 写三表
   - asset_raw: 翻旧行 is_latest=false → 写新行 is_latest=true
   - asset_snapshot: UPSERT（已自动 is_latest=true）
   - fund_category_map: 同步
   - snapshot_meta (NEW):
     - 翻 (user_id) 全部 is_current=false
     - INSERT (user_id, snapshot_date, is_latest=true, is_current=true)
     - UPSERT 唯一键
   ↓
Step 3: verifyMirror
   - sum(asset_raw.amount WHERE is_latest=true) = asset_snapshot.total_amount
   - selectFundNamesByUserAndDate WHERE is_latest=true
   - count is_latest=true per (user, date, category) = 1
   ↓
Step 4: 1a.8 撤销钩子（10s 窗口）
```

### 2.4 新 API

**POST /api/snapshot/set-current**（决策 27 接入）

```
POST /api/snapshot/set-current
Header: X-User-Id: 1
Body: { "snapshotDate": "2026-07-16" }
Response: 200 OK
{
  "userId": 1,
  "snapshotDate": "2026-07-16",
  "previousCurrent": "2026-07-20",
  "newCurrent": "2026-07-16"
}
```

**业务逻辑**：
- 仅切换 `snapshot_meta.is_current`，不动 asset_raw / asset_snapshot
- 验证 snapshot_date 存在且 is_latest=true
- 翻旧 current is_current=false
- 写新 current is_current=true
- 事务内执行

**GET /api/snapshot/current**（查询当前快照）

```
GET /api/snapshot/current?includeDetail=true
Response: 200 OK + SnapshotLatestResponse (复用现有)
```

注：现有的 `GET /api/snapshot/latest` 改为返回 `is_current=true` 的快照（而非 `MAX(snapshot_date)`）。

### 2.5 前端 UI（1b.3 实施）

DataPage 加 3 个块：

1. **上传框架**（dropzone + multi-select）
   - 4 张图批量上传
   - 显示 progress

2. **解析选项 toggle**
   - single/multi 切换按钮（决策 26）
   - date picker（pick snapshot date）
   - "设为当前快照" checkbox（默认 true）

3. **快照管理**
   - 列表：所有 is_latest=true 的 (snapshot_date, fund_count, total)
   - 每行操作：查看 / 切为当前 / 撤销
   - 顶部"当前快照" 标记

具体实施推到 1b.3（按用户 Q2=A 答：1b.2 紧急扩到包含决策 27 + 简易前端；1b.3 完整 UI）。

---

## 3. 修复实施清单

### 3.1 本次 commit 已完成

| # | 改动 | 文件 | 状态 |
|---|------|------|------|
| 1 | Bug 1 修复：writeAssetRaw 前调用 updateIsLatestBySnapshotDate | `SnapShotConfirmService.java` | ✅ |
| 2 | Bug 2 修复 1：sumAmountByUserAndDateAndCategory 加 AND is_latest=true | `AssetRawMapper.java` | ✅ |
| 3 | Bug 2 修复 2：selectFundNamesByUserAndDate 加 AND is_latest=true | `AssetRawMapper.java` | ✅ |
| 4 | DB 清理：DELETE 0720 5 行 + 0716 19 行 is_latest=1 保留 | SQL | ✅ |
| 5 | 决策 27 设计文档（本文件） | `docs/phase-1/decisions/decision-27-is-latest-dual-layer.md` | ✅ |

### 3.2 后续 commit 待实施（1b.2 补完后端）

| # | 改动 | 优先级 |
|---|------|--------|
| 6 | 新表 `snapshot_meta` DDL | P0 |
| 7 | `SnapshotMeta` Entity + Mapper | P0 |
| 8 | `SnapShotConfirmService` 接入 snapshot_meta 写 | P0 |
| 9 | 新 API `POST /api/snapshot/set-current` | P1 |
| 10 | `GET /api/snapshot/latest` 改为 is_current 查询 | P1 |
| 11 | 1b.2 工作计划 + 验收计划 紧急更新 | P0 |
| 12 | 写补完报告 + 验收 | P0 |
| 13 | commit + push | P0 |

### 3.3 1b.3 后续

- DataPage 上传框架 + toggle + date picker + is_latest 按钮
- 完整前端 UI（决策 27 §2.5）

---

## 4. 风险与回滚

### 4.1 风险

- **R1**：升级期间并发 confirm 同一日期可能竞态 → 依靠 `@Transactional` 隔离
- **R2**：snapshot_meta 表新增对历史数据无影响（默认 is_current=false）
- **R3**：前端 UI 推到 1b.3，本次提交不影响前端

### 4.2 回滚

- revert 本 commit → Bug 1+2 修复可独立回滚
- snapshot_meta 表新增 DDL 可独立 DROP（不影响 asset_raw/asset_snapshot）
- DB 清理不可逆（已删 0720 5 行），但 0720 是已知错误数据

---

## 5. 验收清单（1b.2 补完验收）

- [x] DB 状态恢复 0716 19 只 is_latest=1
- [x] /api/snapshot/latest 返回 19 只基金 + 6+1 categories
- [ ] 后端 Bug 1+2 修复编译通过 + 重启
- [ ] 决策 27 文档
- [ ] 1b.2 工作计划 + 验收计划 紧急更新
- [ ] 补完报告
- [ ] commit + push

---

*本文档 2026-07-22 19:37 GMT+8 生成*
