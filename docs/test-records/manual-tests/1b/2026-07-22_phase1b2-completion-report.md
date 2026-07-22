# Phase 1b.2 补完报告（2026-07-22 19:37）

> **状态**：🚧 部分完成（后端 Bug 修复 + DB 清理 + 设计文档）
> **配套决策**：[decision-27-is-latest-dual-layer.md](../../../phase-1/decisions/decision-27-is-latest-dual-layer.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.2 补完（紧急） |
| 完成日期 | 2026-07-22 19:37 |
| 修复人 | 刘博丞 + Cline |
| 关联 commit | 3769e31 → 75ea631 → dcaae96 → 3e6b89b → 2449b5b → new commit |
| 关联决策 | 决策 27（is_latest 双层语义 + 跨日期 is_current） |
| 关联 commit 内容 | Bug 1+2 修复 + DB 清理 + 决策 27 设计文档 |

---

## 1. 问题源头

1b.2 验收通过时（2026-07-22 17:30），DB 状态是 0716 19 只 is_latest=true。

随后 0.72.0 workaround 测试通过 curl 单图 confirm 0720，单图返回 5 只。结果：
- 0720 5 行写入 asset_raw，is_latest=true
- **Bug 1**：旧的 0716 19 行未被翻 is_latest=false（`SnapShotConfirmService.writeAssetRaw` 缺少 `updateIsLatestBySnapshotDate` 调用）
- 反复 confirm 同一日期时，DB 仍有 旧行 + 新行 都 is_latest=true

`GET /api/snapshot/latest` 返回：
- `MAX(snapshot_date)` = 2026-07-20（因 0720 也有 is_latest=true）
- 渲染 0720 5 只基金

**用户反馈**："1b.2 还没有实现完全。前端居然只有五个基金，这是严重的过程性错误。"

---

## 2. 根因分析

### 2.1 Bug 1：`writeAssetRaw` 不翻旧行 is_latest

`AssetRawMapper.updateIsLatestBySnapshotDate(Long userId, LocalDate snapshotDate)` 已存在（1a.8 撤销流程用），但 `SnapShotConfirmService.writeAssetRaw` 调用前**从未**调用它。

### 2.2 Bug 2：`verifyMirror` SUM 不过滤 is_latest

`AssetRawMapper.sumAmountByUserAndDateAndCategory` 和 `selectFundNamesByUserAndDate` 两个 verifyMirror 用的 SQL，**不包含 `AND is_latest = true`**。

### 2.3 之前的误判

我在 PLAN 阶段误判了：
- 是 cache 命中率问题 ❌
- 是 dedup 引擎没做 fundName dedup ❌

真相：
- DedupEngine 维度 C 一直在做 fundName dedup，正确
- 5001 错误是 `SnapShotConfirmService.verifyMirror` 校验 2 抛的，不是 DedupEngine 抛的

---

## 3. 修复清单

### 3.1 后端 Bug 修复（本次 commit）

| # | Bug | 修复 | 文件 |
|---|-----|------|------|
| 1 | writeAssetRaw 不翻旧行 | 加 `assetRawMapper.updateIsLatestBySnapshotDate(...)` 调用 | `SnapShotConfirmService.java` |
| 2 | sumAmountByUserAndDateAndCategory 不过滤 | 加 `AND is_latest = true` | `AssetRawMapper.java` |
| 3 | selectFundNamesByUserAndDate 不过滤 | 加 `AND is_latest = true` | `AssetRawMapper.java` |

### 3.2 DB 清理（本次 commit）

- `DELETE FROM asset_raw WHERE snapshot_date='2026-07-20'` (5 行)
- `DELETE FROM asset_snapshot WHERE snapshot_date='2026-07-20'` (3 行)
- 0716 19 行保留 is_latest=1

### 3.3 决策 27 设计文档（本次 commit）

`docs/phase-1/decisions/decision-27-is-latest-dual-layer.md`：
- 双层 is_latest 语义（per-date + is_current）
- `snapshot_meta` 表设计
- confirm 流程图
- 新 API：POST /api/snapshot/set-current
- 前端 UI 范围（推到 1b.3）

### 3.4 后续 commit 待实施（用户 Q2=A 已同意 1b.2 紧急扩）

- 新表 `snapshot_meta` DDL
- `SnapshotMeta` Entity + Mapper
- `SnapShotConfirmService` 接入 snapshot_meta 写
- 新 API `POST /api/snapshot/set-current`
- `GET /api/snapshot/latest` 改为 is_current 查询
- 1b.2 工作计划 + 验收计划 紧急更新

### 3.5 1b.3 后续

- DataPage 上传框架 + toggle + date picker + is_latest 按钮
- 完整前端 UI（决策 27 §2.5）

---

## 4. 验收

### 4.1 DB 验证

```sql
SELECT snapshot_date, COUNT(*) AS raw_count, SUM(is_latest) AS latest_count
FROM asset_raw WHERE user_id=1 GROUP BY snapshot_date;
```
期望：
- 0716: raw_count=19, latest_count=19
- (无 0720 行)

执行结果：
```
snapshot_date  raw_count  latest_count
2026-07-16     19         19
```

### 4.2 API 验证

```bash
curl -s "http://localhost:8080/api/snapshot/latest?includeDetail=true" -H "X-User-Id: 1"
```

期望：
- snapshotDate = "2026-07-16"
- sixCategoriesTotal = 7541.52
- balanceFund = 308.86
- totalAssetWithBalance = 7850.38
- categories = 7 类（A股/海外/商品/固收/货币/港股/余额）
- 19 只基金

实测结果：
```json
{
  "snapshotDate": "2026-07-16",
  "sixCategoriesTotal": 7541.52,
  "balanceFund": 308.86,
  "totalAssetWithBalance": 7850.38,
  "categories": [
    {"categoryName": "A股权益类", "fundCount": 6, ...},
    {"categoryName": "余额类", "fundCount": 1, ...},
    {"categoryName": "商品类", "fundCount": 3, ...},
    {"categoryName": "固收类", "fundCount": 2, ...},
    {"categoryName": "海外权益类", "fundCount": 4, ...},
    {"categoryName": "港股大中华类", "fundCount": 2, ...},
    {"categoryName": "货币类", "fundCount": 1, ...}
  ]
}
```

✅ 19 只基金 = 6+1+3+2+4+2+1

### 4.3 前端 HomePage 验证

- 访问 localhost:5174
- 看到 19 只基金明细
- 6 大类环形图显示 6 类 + 余额类
- 累计/持有收益卡片正确

---

## 5. 未完成项

| 项 | 优先级 | 计划 |
|---|--------|------|
| 1b.2 工作计划 + 验收计划 紧急更新 | P0 | 本次 commit 后续 |
| 决策 27 后端代码实施（snapshot_meta 表 + Service） | P0 | 1b.2 后续 commit |
| 决策 27 新 API（POST /api/snapshot/set-current） | P1 | 1b.2 后续 commit |
| 1b.3 前端 UI（上传框架 + toggle + date picker） | P0 | 1b.3 子阶段 |
| 1b.4 AI 顾问页 | P1 | 1b.4 子阶段 |
| Phase 1b 收尾验收 | P0 | 1b.3 + 1b.4 完成 |

---

## 6. 教训

1. **过程性错误要被重视**：0.72.0 workaround 测试覆盖了 0716 19-fund 状态，本应单独建测试环境或先备份
2. **is_latest 语义要明确**：决策 27 之前是单层（per-row），导致 1 bug 1 隐藏
3. **verifyMirror 必须过滤 is_latest**：否则 SUM 会累加历史行
4. **不要在 PLAN 阶段轻易给方案**：误判（cache、dedup）浪费时间

---

*文档 2026-07-22 19:37 GMT+8 生成*
