# Phase 1b.3 工作计划（2026-07-22）

> **状态**：原始交付计划（保留历史）
> **配套验收计划**：[2026-07-22_phase1b3-acceptance-plan.md](../../../test-records/manual-tests/1b/2026-07-22_phase1b3-acceptance-plan.md)
> **关联决策**：决策 27（is_latest 双层语义 + 跨日期 is_current）
> **后续补救**：[1b.3 补救工作计划](./2026-07-22_phase1b3-remediation-plan.md) · [补救验收计划](../../../test-records/manual-tests/1b/2026-07-22_phase1b3-remediation-acceptance-plan.md)
>
> ⚠️ 本计划记录最初上传/确认/snapshot_meta 交付，不覆盖后续发现的解析历史计数、比例加载、跨日期余额及首页视觉问题；这些问题统一进入补救计划，避免改写原交付历史。

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

---

## 9. Phase 4：前端代码审查补救（2026-07-23）

> **状态**：🔧 待实施
> **触发**：2026-07-23 用户运行时报告 5 项 UI/数据问题
> **配套验收更新**：[2026-07-22_phase1b3-acceptance-plan.md §1.2b](../../../test-records/manual-tests/1b/2026-07-22_phase1b3-acceptance-plan.md)
> **配套报告更新**：[2026-07-22_phase1b3-acceptance-report.md §5b](../../../test-records/manual-tests/1b/2026-07-22_phase1b3-acceptance-report.md)
>
> ⚠️ 本阶段只修用户明确点名的 5 个症状；累计/持有收益现行算法、决策 27 双层语义、后端 Java 代码、store action 方法、路由结构、侧边栏菜单全部保持不动。
> 后续若用户验收通过后提出新需求，只在用户明确指出的范围内修改。

### 9.1 用户报告 5 项问题与根因

| ID | 用户报告 | 根因 | 关联需求 |
|---|---|---|---|
| **BUG-P4-001** | 上传 4 张图后图片占满全屏 | `DataPage.jsx` 使用 `.preview-strip` / `.preview-item` 等类，但 `main.jsx` 未 import 任何 `data-page.css`，所有类回退到 UA 默认样式，`<img>` 按原像素渲染 | DATA-001 |
| **BUG-P4-002** | 上传按钮消失 | 同 BUG-P4-001 根因；`<input type="file">` 被块级预览 div 推下/挤出可视区 | DATA-001 |
| **BUG-P4-003** | 数据管理页像 21 世纪初网页 | 同 BUG-P4-001 根因；浏览器回退到 Times New Roman / 灰色边框 / 无圆角阴影 | 全局样式 |
| **BUG-P4-004** | 首页最近操作数量显示 0 | `HomePage.jsx` `RecentOps` 直接透传后端 `summary` 文本；后端 AI 解析失败/模型未返回时 summary 退化为"解析 0 只基金"字符串，前端无异常检测 | DATA-G-012、HOME-008 |
| **BUG-P4-005** | 首页六大类分布表格行高过高 | `global.css` `.ratio-table td, th` padding = `10px 12px`，无固定行高；6 行 + 合计 ≈ 280px | 全局样式 |
| **BUG-P4-006**（衍生）| 基金小计 null 收益被显示为 +0.00 | `SixCategoryGroup` 小计判断条件用 `sumFunds(funds) > 0`（求 amount 和），所有 holdingProfit=null 时 `safeNumber(null, 0)`=0 求和后显示 `+0.00`，违反"全 null 才显示 —" | DATA-G-013、HOME-005 |
| **BUG-P4-007**（衍生）| 目标比例硬编码 | `HomePage` 第 465 行 `const targetRatios = { '货币类': 10, ... }` 写死，不读 `userConfigStore` | DATA-G-004 |

### 9.2 实施任务

| # | 任务 | 优先级 | 验收 | 关联 Bug |
|---|------|--------|------|---|
| 1b.3.11 | 新建 `fincontrol-frontend/src/styles/data-page.css`（约 200 行）+ `main.jsx` 引入 | P0 | 4 张图预览 ≤ 120px 高；上传按钮始终可见；非 21 世纪初风格 | BUG-P4-001/002/003 |
| 1b.3.12 | `HomePage.jsx` `RecentOps` 加 `isZeroFundAnomaly` 检测 + 异常标注 | P0 | "解析 0 只基金" 后跟 ⚠"基金数未知" | BUG-P4-004 |
| 1b.3.13 | `global.css` `.ratio-table` 压缩 padding 到 `8px 10px`、固定 `height: 36px` | P1 | 六大类配置表行高 ≈ 36px | BUG-P4-005 |
| 1b.3.14 | `HomePage.jsx` `SixCategoryGroup` 用 `sumIfAllDefined` 重写小计 | P1 | 港股 +3.84 + -3.84 小计 = 0.00；全 null 才 — | BUG-P4-006 |
| 1b.3.15 | `HomePage.jsx` `targetRatios` 改 `useUserConfigStore` 订阅 | P2 | 配置修改后首页偏差表同步刷新 | BUG-P4-007 |

### 9.3 保护范围（明确不做）

- ❌ 不重写 `CumulativeReturnCard.jsx`（累计/持有收益现行标准）
- ❌ 不改 `phase1_simple` 算法与余额宝 fallback 逻辑
- ❌ 不动决策 27 is_latest/is_current 双层语义
- ❌ 不改后端任何 Java 代码
- ❌ 不改 store action 方法
- ❌ 不改路由结构
- ❌ 不改侧边栏菜单
- ❌ 不改 `Sidebar.jsx` 与未启用页面的灰显处理

### 9.4 实施顺序

1. **A 段（文档）**：先更新 work-plan / acceptance-plan / acceptance-report → 1 个 docs commit + push
2. **B 段（代码）**：依次执行 1b.3.11 → 1b.3.15 → 1 个 fix commit（不 push）

### 9.5 退出条件（DoD）

- [ ] docs commit 已 push 到 origin
- [ ] 5 项代码修复全部落地
- [ ] `mvn -f fincontrol-backend/pom.xml test` 仍全绿（无后端改动，理应不受影响）
- [ ] `npm --prefix fincontrol-frontend run build` 通过
- [ ] 浏览器视觉验收：DataPage 4 张图不溢出、上传按钮可见、首页六大类表格行高正常、最近操作"0 只基金"有 ⚠ 标注
- [ ] 累计/持有收益现行标准无任何回归
- [ ] 后续用户提出新需求时严格按"只改用户指出的范围"执行

---

*1b.3 Phase 4 工作计划 2026-07-23 17:18 GMT+8 追加*
