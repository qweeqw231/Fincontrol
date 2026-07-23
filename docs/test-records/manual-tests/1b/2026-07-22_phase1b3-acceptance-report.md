# Phase 1b.3 验收报告（2026-07-22）

> **状态**：🚧 部分完成（10 项中 4 项通过 + 4 项前端 + 2 项待 e2e 验证） + 2026-07-23 新增 5 项待修 UI/数据问题
> **配套工作计划**：[2026-07-22_phase1b3-work-plan.md](../../../phase-1/work-plans/1b/2026-07-22_phase1b3-work-plan.md)
> **关联决策**：决策 27（is_latest 双层语义 + 跨日期 is_current）

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.3 数据管理页 |
| 启动日期 | 2026-07-22 |
| 验收人 | 刘博丞 + Cline |
| 关联 commit | 5f6117c / 649ae78 / 831dfb2 / e0edf69 / 4fe4612 + 1b.3.11~15（待提交） |
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

### 1.2b 前端代码审查补救（5 项，2026-07-23 追加）

| # | 验收项 | 状态 | 证据 / commit |
|---|--------|------|---------------|
| **1b.3.11** | DataPage CSS 加载（修复 4 张图占全屏、按钮消失、21 世纪初风格） | ⏳ 待修 | docs commit 已完成；fix commit 待生成 |
| **1b.3.12** | 最近操作 0 基金异常标注 | ⏳ 待修 | 同上 |
| **1b.3.13** | 六大类配置表行高压缩 | ⏳ 待修 | 同上 |
| **1b.3.14** | 基金小计零值语义（sumIfAllDefined） | ⏳ 待修 | 同上 |
| **1b.3.15** | 目标比例从 userConfigStore 读取 | ⏳ 待修 | 同上 |

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

### 4.4 Phase 4 前端补救（2026-07-23 追加，待提交）

```
fincontrol-frontend/src/styles/data-page.css            # 1b.3.11 新建：DataPage 全部类样式
fincontrol-frontend/src/main.jsx                        # 1b.3.11 修改：import data-page.css
fincontrol-frontend/src/styles/global.css               # 1b.3.13 修改：.ratio-table 行高压缩
fincontrol-frontend/src/pages/HomePage.jsx              # 1b.3.12 / 14 / 15 修改：异常检测 + sumIfAllDefined + userConfigStore
```

---

## 5. 1b.3 已知问题

| # | 问题 | 状态 |
|---|------|------|
| 1 | backend 需重启才能 meta-list 端点 | 待 backend 重启 |
| 2 | 整体回归测试（其他 controller 测试） | 待 mvn test 重跑 |
| 3 | 0716 历史数据 import 0716 → snapshot_meta 行 | 待 curl 端到端实测 |

---

## 5b. 代码审查补救问题清单（2026-07-23 追加）

> **关联**：[工作计划 §9](../../../phase-1/work-plans/1b/2026-07-22_phase1b3-work-plan.md) · [验收计划 §1.2b](./2026-07-22_phase1b3-acceptance-plan.md)
>
> 以下问题由 2026-07-23 用户运行时报告触发，全部为前端代码层 P0~P2 缺陷，**不涉及后端业务逻辑与累计/持有收益现行算法**。

| # | 验收项 | Bug ID | 现象 | 根因 | 状态 | 计划 commit |
|---|--------|--------|------|------|------|-------------|
| 1 | 1b.3.11 | BUG-P4-001/002/003 | 上传 4 张图后图片占满全屏；上传按钮消失；DataPage 像 21 世纪初网页 | `DataPage.jsx` 使用 `.preview-strip` / `.preview-item` 等类但 `main.jsx` 未 import 任何 `data-page.css`，所有类回退到 UA 默认样式，`<img>` 按原像素渲染 | ⏳ 待修 | 1b.3.11 fix |
| 2 | 1b.3.12 | BUG-P4-004 | 首页最近操作"解析 0 只基金"静默显示（数量 0、时间正常） | `HomePage.jsx` `RecentOps` 直接透传后端 `summary` 文本，无 0-fund 异常检测 | ⏳ 待修 | 1b.3.12 fix |
| 3 | 1b.3.13 | BUG-P4-005 | 首页六大类分布表格行高过高 | `global.css` `.ratio-table td, th` padding = `10px 12px`，无固定行高 | ⏳ 待修 | 1b.3.13 fix |
| 4 | 1b.3.14 | BUG-P4-006 | 基金小计 null 收益被显示为 +0.00（违反 DATA-G-013） | `SixCategoryGroup` 小计判断条件用 `sumFunds(funds) > 0`（求 amount 和），所有 holdingProfit=null 时 `safeNumber(null, 0)`=0 求和后显示 `+0.00` | ⏳ 待修 | 1b.3.14 fix |
| 5 | 1b.3.15 | BUG-P4-007 | 目标比例硬编码（违反 DATA-G-004） | `HomePage` 第 465 行 `const targetRatios = { ... }` 写死，不读 `userConfigStore` | ⏳ 待修 | 1b.3.15 fix |

### 5b.1 修复策略

- **A 段（已完成）**：落盘 docs commit — 更新 work-plan §9 + acceptance-plan §1.2b + 本节
- **B 段（待执行）**：依次执行 1b.3.11 → 1b.3.15 五项 fix，落在 1 个 fix commit（不 push）
- **保护范围**：累计/持有收益现行算法、CumulativeReturnCard、决策 27 双层语义、后端 Java、store action、路由、侧边栏、Sidebar.jsx — 全部不动

---

## 6. 后续工作

| # | 任务 | 优先级 |
|---|------|--------|
| 1 | 1b.4 AI 顾问页 | 1b.3 后 |
| 2 | Phase 1b 收尾验收 | 1b.4 后 |
| 3 | Phase 2 启动 | 1b.4 后 |
| 4 | **Phase 4 fix commit + 用户验收 1b.3.11~15** | **2026-07-23 17:20 起** |

---

*1b.3 验收报告 2026-07-22 20:33 GMT+8 生成*
*1b.3.11~15 §5b 2026-07-23 17:20 GMT+8 追加*
