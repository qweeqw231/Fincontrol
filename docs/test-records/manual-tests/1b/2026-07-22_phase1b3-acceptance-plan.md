# Phase 1b.3 验收计划（2026-07-22）

> **状态**：原始交付验收计划（保留历史）
> **配套工作计划**：[2026-07-22_phase1b3-work-plan.md](../../../phase-1/work-plans/1b/2026-07-22_phase1b3-work-plan.md)
> **后续补救验收**：[2026-07-22_phase1b3-remediation-acceptance-plan.md](./2026-07-22_phase1b3-remediation-acceptance-plan.md)
> **补救需求基线**：[FinControl 全站页面需求说明书](../../../requirements/2026-07-22_fincontrol-page-requirements.md)
>
> ⚠️ 本文件只验收原始 1b.3 上传与 snapshot_meta 主路径；解析历史计数、实际/目标比例、当前快照一致性、首页与数据页视觉统一由补救验收计划负责。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.3 数据管理页 |
| 验收日期 | 2026-07-22 ~ |
| 验收人 | 刘博丞 + Cline |
| 关联 commit | 1b.3.1 → 1b.3.10 + 1b.3.11~15（Phase 4 追加） |
| 关联决策 | 决策 27 |

---

## 1. 验收 checklist（10 项）

### 1.1 后端（5 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| **1b.3.1** | snapshot_meta 表 DDL + Entity + Mapper | ☐ | 手动 mysql 执行 DDL + mvn compile 通过 |
| **1b.3.2** | SnapShotConfirmService 接入 snapshot_meta 写 | ☐ | 单测 + curl confirm 后 DB 状态正确 |
| **1b.3.3** | POST /api/snapshot/set-current API | ☐ | 单测 + curl 切换成功 |
| **1b.3.4** | GET /api/snapshot/latest 改 is_current 查询 | ☐ | 单测 + curl 返 0716 19 只 |
| **1b.3.5** | 后端单元测试 100% 通过 | ☐ | `mvn test` 输出 |

### 1.2 前端（4 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| **1b.3.6** | DataPage 上传 dropzone（4 张图批量） | ☐ | 浏览器实测 |
| **1b.3.7** | single/multi toggle + date picker | ☐ | 浏览器实测 |
| **1b.3.8** | is_latest 按钮 + 设为当前快照按钮 | ☐ | 浏览器实测 |
| **1b.3.9** | 快照管理列表 + curl 联调 | ☐ | 浏览器实测 |

### 1.3 验收（1 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| **1b.3.10** | 1b.3 验收报告 + commit + push | ☐ | 文档 + 5/5/4/4 通过 |

### 1.2b 前端代码审查补救（5 项，2026-07-23 追加）

> **触发**：2026-07-23 用户运行时报告 5 项 UI/数据问题
> **配套工作计划**：[2026-07-22_phase1b3-work-plan.md §9](../../../phase-1/work-plans/1b/2026-07-22_phase1b3-work-plan.md)

| # | 验收项 | 期望 | 证据 | 关联 Bug |
|---|--------|------|------|---|
| **1b.3.11** | DataPage CSS 加载 | 上传 4 张图后，每张预览图高度 ≤ 120px、不溢出；`<input type="file">` 始终可见；页面非 UA 默认字体（无 Times New Roman） | 浏览器实测截图 | BUG-P4-001/002/003 |
| **1b.3.12** | 最近操作 0 基金异常标注 | summary 文本"解析 0 只基金"后跟橙色 ⚠ 文字"基金数未知" | 浏览器实测截图 | BUG-P4-004 |
| **1b.3.13** | 六大类配置表行高 | DevTools 检查 `.ratio-table tr` 的 `height` 约 36px；总高 7 行 ≈ 252px | DevTools Elements 截图 | BUG-P4-005 |
| **1b.3.14** | 基金小计零值语义 | 港股大中华类 +3.84 + -3.84 小计 = 0.00（不是 —）；若该大类所有 holdingProfit 字段均为 null 才显示 — | Oracle 对比 | BUG-P4-006 |
| **1b.3.15** | 目标比例从 store 读取 | HomePage 顶部不出现硬编码字面量；修改 `useUserConfigStore.targetRatios` 后首页 ConfigDeviationTable 的"目标"列同步刷新 | 浏览器实测 + store 单测 | BUG-P4-007 |

#### 1.2b.1 验收原则

- 本次只验收 1b.3.11~15 共 5 项；其他 1b.2 / 1b.3 验收项保持原状态，不在本轮签字
- 累计/持有收益现行算法、CumulativeReturnCard 组件、决策 27 双层语义、store action 方法、路由结构、侧边栏菜单 — 全部按 1b.2 / 1b.3 既有结论视为通过
- 后续用户提出新需求时只在明确指出的范围内验收

#### 1.2b.2 验收证据要求

| 验收项 | 必交证据 |
|---|---|
| 1b.3.11 | DataPage 浏览器截图（4 张图选中后状态）+ `<input>` 元素 DevTools 截图 |
| 1b.3.12 | HomePage "最近操作" 截图（含 ⚠ 标注） |
| 1b.3.13 | DevTools Elements 面板 `.ratio-table tr` 截图 |
| 1b.3.14 | 浏览器 DOM 文本截图（港股小计 0.00） |
| 1b.3.15 | HomePage 顶部 + 目标偏差表截图 + 修改 `useUserConfigStore` 后的刷新截图 |

---

## 2. 验收实测清单

### 2.1 DB 验收（1b.3.1）

```sql
-- 检查表是否存在
SHOW TABLES LIKE 'snapshot_meta';

-- 检查表结构
DESC snapshot_meta;

-- 检查索引
SHOW INDEX FROM snapshot_meta;
```

**期望**：
- 表存在
- 11 列（id, user_id, snapshot_date, is_latest, is_current, confirmed_at, created_at, updated_at + 3 索引）
- UNIQUE KEY uk_user_date
- KEY idx_user_current, idx_user_latest

### 2.2 单元测试（1b.3.5）

```bash
mvn -f fincontrol-backend/pom.xml test -DfailIfNoTests=false
```

**期望**：
- 所有测试通过
- SnapshotMetaMapperTest / SnapShotConfirmServiceTest / SnapshotControllerTest 100%

### 2.3 端到端（1b.3.10）

#### 路径 A：curl 4 张图 + confirm 写入 snapshot_meta

```bash
# 1. 上传 4 张图
FILE_IDS=$(curl -s -F "file=@phase1a10-alipay-fund-list-20260720-0048-1.jpg" \
  -F "file=@phase1a10-alipay-fund-list-20260720-0048-2.jpg" \
  -F "file=@phase1a10-alipay-fund-list-20260720-0048-3.jpg" \
  -F "file=@phase1a10-alipay-fund-list-20260720-0048-4.jpg" \
  http://localhost:8080/api/screenshot/upload | jq -r '.data.fileId')

# 2. parse-batch single mode
PARSED=$(curl -s -X POST "http://localhost:8080/api/screenshot/parse-batch?mode=single" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d "{\"userId\":1,\"fileIds\":$FILE_IDS,\"dataTime\":\"2026-07-16\"}")

# 3. confirm
curl -s -X POST "http://localhost:8080/api/snapshot/confirm" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d "{\"userId\":1,\"snapshotDate\":\"2026-07-16\",\"confirmedOverwrite\":true,\"parsedAssets\":[$PARSED]}"

# 4. 验证 snapshot_meta
mysql -e "SELECT * FROM snapshot_meta WHERE user_id=1;"
```

**期望**：
- 4 张图上传成功
- parse-batch 返回 19-fund ParsedAsset
- confirm 200 OK + assetRawInserted=19
- snapshot_meta 1 行：user_id=1, snapshot_date=2026-07-16, is_latest=true, is_current=true

### 路径 B：set-current 切换

```bash
# 1. 上传 0720 数据并 confirm
# 2. set-current 切换到 0716
curl -s -X POST "http://localhost:8080/api/snapshot/set-current" \
  -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"userId":1,"snapshotDate":"2026-07-16"}'

# 3. GET latest 验证
curl -s "http://localhost:8080/api/snapshot/latest?includeDetail=true" -H "X-User-Id: 1"
```

**期望**：
- set-current 200 OK
- 0716 is_current=true, 0720 is_current=false
- GET latest 返 0716 19 只

#### 路径 C：前端 4 张图 → 19 只

- 访问 localhost:5174（DataPage）
- 上传 4 张图 → 单图/多图 toggle → date picker 选 0716 → confirm
- 首页 19 只基金渲染

---

## 3. 回归测试

### 3.1 1b.2 验收签字 5 项（决策 25 v3 累计/持有双列）

- [ ] 1b.6 余额类 + 六大类总值
- [ ] 1b.7 六大类环形图
- [ ] 1b.8 六大类明细表格
- [ ] 1b.9 最近操作时间线
- [ ] 1b.23 全局状态联动

### 3.2 1b.2 补完 (decision 27 §3.1)

- [ ] DB 19 只状态保持
- [ ] /api/snapshot/latest 返 19 只基金
- [ ] Bug 1+2 修复未回归

---

## 4. 已知风险

| 风险 | 缓解 |
|------|------|
| snapshot_meta 表 DDL 与现有 schema 冲突 | 执行前先查 show tables / show index |
| GET latest 改 is_current 后，旧快照无 is_current=true | 1b.3.1 同时跑 confirm 流程 import 0716 |
| 前端上传 4 张图可能因 cache 命中错误 | 决策 26 single 模式串行 parse 失败 1 次重试 |

---

## 5. 验收签字

- 验收人：刘博丞
- 验收日期：___
- 验收结论：___

---

*1b.3 验收计划 2026-07-22 20:01 GMT+8 启动*
*1b.3.11~15 2026-07-23 17:20 GMT+8 追加*
