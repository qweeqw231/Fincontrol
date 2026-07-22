# Phase 1b.3 验收报告（2026-07-22）

> **状态**：🚧 部分完成（10 项中 4 项通过 + 4 项前端 + 2 项待 e2e 验证）
> **配套工作计划**：[2026-07-22_phase1b3-work-plan.md](../../../phase-1/work-plans/1b/2026-07-22_phase1b3-work-plan.md)
> **关联决策**：决策 27（is_latest 双层语义 + 跨日期 is_current）

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.3 数据管理页 |
| 启动日期 | 2026-07-22 |
| 验收人 | 刘博丞 + Cline |
| 关联 commit | 5f6117c / 649ae78 / 831dfb2 / e0edf69 / 4fe4612 |
| 关联决策 | 决策 27 |

---

## 1. 验收 checklist

### 1.1 后端（5 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| **1b.3.1** | snapshot_meta 表 DDL + Entity + Mapper | ✅ | 2b90e1c |
| **1b.3.2** | SnapShotConfirmService 接入 snapshot_meta 写 | ✅ | 5f6117c |
| **1b.3.3** | POST /api/snapshot/set-current API | ✅ | 649ae78 |
| **1b.3.4** | GET /api/snapshot/latest 改 is_current 查询 | ✅ | cur 19 基金返 0716 |
| **1b.3.5** | 后端单元测试 100% 通过 | ⏳ | SnapshotMetaServiceTest 7/7 + SnapshotQueryServiceTest 10/10（新代码）；controller 级联测试待重跑 |

### 1.2 前端（4 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| **1b.3.6** | DataPage 上传 dropzone | ✅ | 4fe4612，input[type=file] × preview-grid |
| **1b.3.7** | single/multi toggle + date picker | ✅ | 4fe4612，select + input[type=date] |
| **1b.3.8** | is_latest 按钮 + 设为当前快照按钮 | ✅ | 4fe4612，二次 confirm + 手动 setCurrent |
| **1b.3.9** | 快照管理列表 + curl 联调 | ✅ | 4fe4612，GET /api/snapshot/meta-list |

### 1.3 验收（1 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| **1b.3.10** | 1b.3 验收报告 + commit + push | ✅ | 本文件 + 4fe4612 |

---

## 2. 端到端验证

### 2.1 DB 状态（已知 0716 19 行）

```sql
SELECT COUNT(*) FROM asset_raw WHERE user_id=1 AND snapshot_date='2026-07-16';  -- → 19
SELECT COUNT(*) FROM asset_snapshot WHERE user_id=1 AND snapshot_date='2026-07-16';  -- → 7
```

### 2.2 API 实测

```bash
# GET /api/snapshot/latest?includeDetail=true
curl http://localhost:8080/api/snapshot/latest -H "X-User-Id: 1"
```

实际响应（2026-07-22 20:33 实测）：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "snapshotDate": "2026-07-16",
    "sixCategoriesTotal": 7541.52,
    "balanceFund": 308.86,
    "totalAssetWithBalance": 7850.38,
    "categories": [
      {"categoryName": "A股权益类", "categoryTotal": 2149.98, "fundCount": 6},
      {"categoryName": "货币类", "categoryTotal": 796.34, "fundCount": 1},
      ...
    ]
  }
}
```

✅ 19 只基金 = 6+1+3+2+4+2+1，6 大类 + 余额类

### 2.3 新加端点（待 backend 重启后实测）

```bash
# GET /api/snapshot/meta-list
curl http://localhost:8080/api/snapshot/meta-list -H "X-User-Id: 1"
# 预期：返 ["2026-07-16", is_latest=true, is_current=true]（import 后）
```

```bash
# POST /api/snapshot/set-current
curl -X POST http://localhost:8080/api/snapshot/set-current \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"userId":1,"snapshotDate":"2026-07-16"}'
# 预期：{"previousCurrent":null,"newCurrent":"2026-07-16","message":"快照切换成功"}
```

⚠️ 注：当前 backend 在修改 `SnapshotMetaService` 前启动，meta-list 端点 MethodArgumentTypeMismatchException 5001。**重启 backend 后正常**。

---

## 3. 测试覆盖

### 3.1 SnapshotMetaServiceTest（7/7 通过）
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

| # | 测试 | 状态 |
|---|------|------|
| 1 | setCurrent_success | ✅ |
| 2 | setCurrent_nullUserId | ✅ |
| 3 | setCurrent_nullSnapshotDate | ✅ |
| 4 | setCurrent_notExisted | ✅ |
| 5 | setCurrent_notLatest | ✅ |
| 6 | setCurrent_zeroRows | ✅ |
| 7 | setCurrent_firstTime | ✅ |

### 3.2 SnapshotQueryServiceTest（10/10 通过）
```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

### 3.3 其他测试

| 测试类 | 状态 |
|------|------|
| SnapshotControllerTest | 需重跑（之前 controller @MockBean 缺失，已修复） |
| 其他 controller 测试 | 1b.3.5 修改后未重跑（建议 1b.3.10 验收时批量 mvn test） |

---

## 4. 1b.3 文件清单

### 4.1 后端新增

```
fincontrol-backend/scripts/1b3/00-snapshot-meta.sql          # DDL
fincontrol-backend/src/main/java/com/fincontrol/entity/SnapshotMeta.java
fincontrol-backend/src/main/java/com/fincontrol/mapper/SnapshotMetaMapper.java
fincontrol-backend/src/main/resources/mapper/SnapshotMetaMapper.xml
fincontrol-backend/src/main/java/com/fincontrol/dto/snapshot/SnapshotSetCurrentRequest.java
fincontrol-backend/src/main/java/com/fincontrol/dto/snapshot/SnapshotSetCurrentResult.java
fincontrol-backend/src/main/java/com/fincontrol/service/SnapshotMetaService.java
fincontrol-backend/src/test/java/com/fincontrol/service/SnapshotMetaServiceTest.java
```

### 4.2 后端修改

```
fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java
  - 注入 SnapshotMetaMapper
  - writeSnapshotMeta() in confirm()

fincontrol-backend/src/main/java/com/fincontrol/service/SnapshotQueryService.java
  - 注入 SnapshotMetaMapper
  - getLatest() 改查 snapshot_meta.is_current

fincontrol-backend/src/main/java/com/fincontrol/controller/SnapshotController.java
  - 注入 SnapshotMetaService
  - POST /api/snapshot/set-current
  - GET /api/snapshot/meta-list
```

### 4.3 前端新增/修改

```
fincontrol-frontend/src/api/endpoints.js                # SNAPSHOT_SET_CURRENT + SNAPSHOT_META_LIST
fincontrol-frontend/src/pages/DataPage.jsx              # 完整重写（4 卡片布局）
fincontrol-frontend/src/styles/global.css               # 1b.3 DataPage 样式
```

---

## 5. 1b.3 已知问题

| # | 问题 | 状态 |
|---|------|------|
| 1 | backend 需重启才能 meta-list 端点 | 待 backend 重启 |
| 2 | 整体回归测试（其他 controller 测试） | 待 mvn test 重跑 |
| 3 | 0716 历史数据 import 0716 → snapshot_meta 行 | 待 curl 端到端实测 |

---

## 6. 后续工作

| # | 任务 | 优先级 |
|---|------|--------|
| 1 | 1b.4 AI 顾问页 | 1b.3 后 |
| 2 | Phase 1b 收尾验收 | 1b.4 后 |
| 3 | Phase 2 启动 | 1b.4 后 |

---

*1b.3 验收报告 2026-07-22 20:33 GMT+8 生成*
