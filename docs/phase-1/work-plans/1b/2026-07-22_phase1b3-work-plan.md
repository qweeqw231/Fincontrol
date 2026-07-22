# Phase 1b.3 工作计划（2026-07-22）

> **状态**：🚧 启动中
> **配套验收计划**：[2026-07-22_phase1b3-acceptance-plan.md](../../../test-records/manual-tests/1b/2026-07-22_phase1b3-acceptance-plan.md)
> **关联决策**：决策 27（is_latest 双层语义 + 跨日期 is_current）

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.3 数据管理页 |
| 启动日期 | 2026-07-22 |
| 责任人 | 刘博丞 + Cline |
| 配套 commit | f42b323（1b.2 补完）→ 511f8d7（归档） |
| 关联决策 | 决策 27 |
| 之前 status | 1b.2 验收通过 |

---

## 1. 目标

**前端可上传基金快照**：DataPage 提供 4 张图批量上传 → single/multi 解析 → date picker 选日期 → confirm → 10s 撤销。

支撑该 UX 的关键设计是 **决策 27：is_latest 双层语义 + 跨日期 is_current**。

---

## 2. 拆分（先后端后前端）

### Phase 1: 后端（决策 27 实施）

| # | 任务 | 优先级 | 验收 |
|---|------|--------|------|
| 1b.3.1 | snapshot_meta 表 DDL + Entity + Mapper | P0 | 编译通过 + 手动 mysql 执行 DDL |
| 1b.3.2 | SnapShotConfirmService 接入 snapshot_meta 写 | P0 | 单测 + curl 实测 |
| 1b.3.3 | POST /api/snapshot/set-current API | P1 | 单元测试 + curl |
| 1b.3.4 | GET /api/snapshot/latest 改 is_current 查询 | P0 | 单测 + curl |
| 1b.3.5 | 后端单元测试（JUnit/Mock） | P0 | 100% 通过 |

### Phase 2: 前端集成（后端单元测试通过后）

| # | 任务 | 优先级 | 验收 |
|---|------|--------|------|
| 1b.3.6 | DataPage 上传 dropzone（4 张图批量） | P0 | curl 端到端 |
| 1b.3.7 | single/multi toggle + date picker | P0 | 浏览器实测 |
| 1b.3.8 | is_latest 按钮 + 设为当前快照按钮 | P0 | 浏览器实测 |
| 1b.3.9 | 快照管理列表 + curl 联调 | P1 | 浏览器实测 |

### Phase 3: 验收

| # | 任务 | 优先级 |
|---|------|--------|
| 1b.3.10 | 1b.3 验收报告 + commit + push | P0 |

---

## 3. SQL 文件位置

按既有约定（fincontrol-backend/scripts/1a10/00-mysql-migration.sql）：

```
fincontrol-backend/scripts/1b3/00-snapshot-meta.sql          ← 决策 27 snapshot_meta 表 DDL
fincontrol-backend/scripts/1b3/01-bug1-bug2-fixes.sql       ← Bug 1+2 修复对应的 SQL（可选）
fincontrol-backend/src/test/resources/fixtures/1b3-*.sql   ← 测试 fixture
```

**初始化方式**：SQL 脚本只存盘，**手动 mysql 执行**（用户确认）。

**历史数据导入**：0716 19 只必须通过截图解析 + confirm 流程（即 parseBatchSingle + /api/snapshot/confirm），不能直接 SQL INSERT。

---

## 4. 后端架构变更

### 4.1 新增表

```sql
CREATE TABLE snapshot_meta (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  snapshot_date   DATE NOT NULL,
  is_latest       BOOLEAN NOT NULL DEFAULT FALSE,
  is_current      BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_at    DATETIME NOT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, snapshot_date),
  KEY idx_user_current (user_id, is_current),
  KEY idx_user_latest (user_id, is_latest)
);
```

### 4.2 新增 Java 类

```
fincontrol-backend/src/main/java/com/fincontrol/entity/SnapshotMeta.java
fincontrol-backend/src/main/java/com/fincontrol/mapper/SnapshotMetaMapper.java
fincontrol-backend/src/main/resources/mapper/SnapshotMetaMapper.xml
```

### 4.3 修改 Java 类

```
fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java
  - confirm() 内嵌 snapshot_meta 写

fincontrol-backend/src/main/java/com/fincontrol/service/SnapshotQueryService.java
  - getLatest() 改查 snapshot_meta.is_current

fincontrol-backend/src/main/java/com/fincontrol/controller/SnapshotController.java
  - 新增 POST /api/snapshot/set-current
```

### 4.4 新增 DTO

```
fincontrol-backend/src/main/java/com/fincontrol/dto/snapshot/SnapshotSetCurrentRequest.java
fincontrol-backend/src/main/java/com/fincontrol/dto/snapshot/SnapshotSetCurrentResult.java
```

---

## 5. 端到端流程

```
前端 DataPage
  ↓ POST /api/screenshot/upload (4 张图)
后端返回 4 个 fileId
  ↓ POST /api/screenshot/parse-batch?mode=single 4 fileId + dataTime
后端串行 parse 4 次 → DedupEngine → 19-fund ParsedAsset
  ↓ POST /api/snapshot/confirm 19-fund + snapshotDate + confirmedOverwrite=true
后端 SnapShotConfirmService:
  Step 0: DedupEngine.deduplicate
  Step 1: 检查 OVERWRITE_REQUIRED
  Step 2: 写 asset_raw + asset_snapshot + fund_category_map
          + snapshot_meta (NEW: 翻旧 is_current=false, 写新 is_current=true)
  Step 3: verifyMirror
  Step 4: 1a.8 撤销钩子（10s 窗口）
  ↓ 返回 SnapshotConfirmResult
前端 store.bumpRefresh() + fetchLatest()
  ↓ GET /api/snapshot/latest
后端 SnapshotQueryService.getLatest()
  改查 snapshot_meta.is_current = true
  ↓ 返回 SnapshotLatestResponse（19 只基金）
前端 HomePage 重新渲染 19 只
```

---

## 6. 验收清单

### 6.1 后端单元测试（1b.3.5）

- [ ] SnapshotMetaMapper 的 insert / selectByUserAndDate / updateIsCurrent / selectCurrent / setCurrent 方法单测 100% 通过
- [ ] SnapShotConfirmService.confirm() 调用 snapshot_meta 写后，DB 状态正确
- [ ] POST /api/snapshot/set-current 切换 is_current 后，GET /api/snapshot/latest 返回新 date
- [ ] 同一日期 2 次 confirm → 旧行 is_latest=false，新行 is_latest=true

### 6.2 端到端

- [ ] 0716 19 行通过 parse + confirm 流程写入 snapshot_meta
- [ ] GET /api/snapshot/latest 改返回 0716（is_current=true）
- [ ] 4 张图批量上传 → 19 只基金 → HomePage 显示 19 只

### 6.3 决策 27 完整性

- [ ] snapshot_meta 表 DDL 存盘
- [ ] Entity + Mapper + XML
- [ ] confirm 流程接入 snapshot_meta 写
- [ ] set-current API
- [ ] GET latest 改 is_current 查询

---

## 7. 风险与回滚

| 风险 | 缓解 |
|------|------|
| snapshot_meta 表 DDL 与现有 schema 冲突 | 执行前先查 show tables / show index |
| SNAPSHOT_LATEST 改成 is_current 查询后，旧快照无 is_current=true | 1b.3.1 同时按"批量导入历史数据"路径走 confirm 流程 |
| confirm 流程异常 | 1a.8 已有 10s 撤销钩子 |

---

## 8. 后续 commit

- 1b.3.1: snapshot_meta DDL + Entity + Mapper
- 1b.3.2: SnapShotConfirmService 接入 snapshot_meta 写
- 1b.3.3: set-current API
- 1b.3.4: GET latest 改 is_current
- 1b.3.5: 单元测试
- 1b.3.6-1b.3.9: 前端
- 1b.3.10: 1b.3 验收

---

*1b.3 工作计划 2026-07-22 20:00 GMT+8 启动*
