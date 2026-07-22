# Phase 1b.2 工作计划（首页 + 全局状态联动）

> **状态**：📝 待审阅（开工前）
> **编写日期**：2026-07-22
> **编写者**：架构审查助手（Cline 导师模式）
> **配套文档**：
> - [Phase 1b 验收清单](../../checklists/phase-1b.md)
> - [Phase 1b.2 验收计划](../../test-records/manual-tests/1b/2026-07-22_phase1b2-acceptance-plan.md)
> - [Phase 0 决策文档](../../phase-0/decisions.md)（重点：决策 4 v2 / 决策 20 / 决策 21）
> - [Phase 0 API 契约](../../phase-0/api-contract.md)
> - [Phase 1a 使用说明书](../../USER-MANUAL.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段编号 | 1b.2 |
| 里程碑 | 首页 + 全局状态联动 + 累计收益率简化算法落地 |
| 预估工时 | 1.5 天（Day 2 上午 + Day 2 下午 + Day 3 上午）|
| 关联 checklist | 1b.6, 1b.7, 1b.8, 1b.9, 1b.23 |
| 关联 P0 | [P0-4.3]（原 1b.7 隐藏卡片，已修订为渲染卡片）、[P0-4.1]（profit 列）、[P0-3.1]（全局状态）|
| 关联决策 | **决策 4 v2**（累计收益率两阶段）/ 决策 16（Recharts）/ 决策 17（纯 CSS）/ **决策 20**（test/ 规范）/ **决策 21**（screenshots/ 清理）|

---

## 1. 目标

让首页真正"活起来"——基于真实后端数据展示完整资产视图，包含：

1. **三卡片**（余额类 / 六大类总值 / 累计收益率 v2）
2. **六大类环形图**（Recharts PieChart + 图例 + 偏差表）
3. **六大类明细表格**（默认折叠，含 profit 列）
4. **最近操作时间线**（mock 数据 + 真实 0716 数据源）
5. **全局状态联动**：4 store 真实连后端，data 页入库后首页自动 refresh

**额外关键交付**：决策 4 v2 的累计收益率简化算法后端实现 + 端到端联调（用真实 0716 数据验证）。

---

## 2. 范围

### 2.1 必须完成

| 验收项 | checklist | 内容 |
|--------|-----------|------|
| 首页三卡片 | 1b.6 | 余额类 + 六大类总值 + 累计收益率（含决策 4 v2） |
| 六大类环形图 | 1b.7 | Recharts PieChart + 图例 + 偏差表 |
| 六大类明细表格 | 1b.8 | 默认折叠，6 列（含 profit） |
| 最近操作时间线 | 1b.9 | mock + 真实 0716 时间线 |
| 全局状态联动 | 1b.23 | /data confirm 后首页自动刷新（store 联动） |
| **累计收益率简化算法**（决策 4 v2） | 1b.6 附 | 后端实现 + 端到端联调（用 4 张真实图） |

### 2.2 显式不做（边界）

- ❌ 月度操作台/资产配置（1b 后续子阶段或 Phase 2）
- ❌ 净值曲线（Phase 3）
- ❌ 比例演化（Phase 3）
- ❌ 截图日期默认值（决策 13，1b.3 才需要）
- ❌ 撤销/10秒 toast（1b.3）
- ❌ unit test 覆盖率 ≥ 70%（Phase 2 目标）

---

## 3. DoD 验收目标

> 详细验证步骤见配套的 [acceptance-plan.md](../../test-records/manual-tests/1b/2026-07-22_phase1b2-acceptance-plan.md)。

### 3.1 checklist 必达项（5 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| 1b.6 | 首页三卡片（余额类 + 六大类总值 + 累计收益率） | ☐ | 浏览器访问 `/` 截图 + 三卡片数字正确 |
| 1b.7 | 六大类环形图（Recharts）| ☐ | 截图 + 各分类比例与后端 API 一致 |
| 1b.8 | 六大类明细表格（含 profit 列）| ☐ | 表格展开截图 + 6 列数据正确 |
| 1b.9 | 最近操作时间线 | ☐ | 截图 + 时间线条目 ≥ 3 |
| 1b.23 | 全局状态联动 | ☐ | /data confirm 后首页三卡片自动刷新（DevTools Network） |

### 3.2 附加验收（必达）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| A1 | 后端 `AssetQueryService.getCumulativeReturn()` 真实算法实现 | ☐ | `mvn test` 新增 Unit Test PASS |
| A2 | DTO 加 `algorithm: "phase1_simple"` 字段 | ☐ | Swagger UI 显示新字段 |
| A3 | 累计收益率端到端联调（用 0716 真实数据） | ☐ | 4 张图上传后 API 返 `algorithm="phase1_simple"` + 实际百分比正确 |
| A4 | `/test/1b/2026-07-22-联调记录.md` 文件存在 | ☐ | 含"存放位置说明"段 |
| A5 | 根目录 README 1b.2 状态更新 | ☐ | README 行 30 显示 `✅ 已完成` |
| A6 | checklist 1b.2 项勾选 | ☐ | `checklists/phase-1b.md` 1b.6–1b.9, 1b.23 全部 [x] |

---

## 4. 实施步骤（按顺序，10 步）

### Step 1：后端 - 算法实现 + Unit Test（决策 4 v2 落地）

```
文件：
- fincontrol-backend/src/main/java/com/fincontrol/service/AssetQueryService.java
  → 新增方法 getCumulativeReturn()（替换 getCumulativeReturnPlaceholder()）
- fincontrol-backend/src/main/java/com/fincontrol/dto/asset/CumulativeReturnResponse.java
  → 新增字段 algorithm
- fincontrol-backend/src/main/java/com/fincontrol/controller/AssetController.java
  → @GetMapping("/cumulative-return") 改为调用新方法
- fincontrol-backend/src/test/java/com/fincontrol/service/AssetQueryServiceTest.java
  → 新增测试：累计收益率算法（正负值、N=0、仅余额类）
```

**SQL 实现**：
```sql
SELECT
  COALESCE(SUM(cumulative_profit), 0) AS total_cumulative_profit,
  COALESCE(SUM(amount), 0) AS total_amount
FROM asset_raw
WHERE is_latest = 1
  -- full-scope: include 余额类 (口径 A, total-asset view)
```

**算法**：`total_cumulative_profit / total_amount`（避免除以 0，total_amount=0 时返 0%）

### Step 2：后端 - 编译 + 测试 + 重启

```bash
cd fincontrol-backend
mvn test -Dtest=AssetQueryServiceTest -q    # 跑新增测试
mvn test -q                                  # 全量（确认无回归 241→242）
# 重启后端（如果 IDE 没自动热加载）
```

### Step 3：前端 - DTO + endpoint 添加

```
文件：
- fincontrol-frontend/src/api/endpoints.js
  → ASSET_CUMULATIVE_RETURN: '/asset/cumulative-return'
```

### Step 4：前端 - 4 store 加 `fetchLatest()`

**`assetSnapshotStore.js`**：

```js
import { create } from 'zustand'
import { apiClient } from '../api/client'
import { ASSET_BALANCE, ASSET_OPERATIONS_RECENT, SNAPSHOT_LATEST, ASSET_CUMULATIVE_RETURN } from '../api/endpoints'

export const useAssetSnapshotStore = create((set, get) => ({
  balance: null,
  latestSnapshot: null,
  operationsRecent: [],
  cumulativeReturn: null,    // 新增：{ algorithm, totalCumulativeProfit, totalAmount, returnRate }
  loading: false,
  error: null,

  setBalance: (data) => set({ balance: data }),
  setLatest: (data) => set({ latestSnapshot: data }),
  setOperations: (data) => set({ operationsRecent: data }),
  setCumulativeReturn: (data) => set({ cumulativeReturn: data }),

  fetchLatest: async () => {
    set({ loading: true, error: null })
    try {
      const [bal, snap, ops, cum] = await Promise.all([
        apiClient.get(ASSET_BALANCE),
        apiClient.get(SNAPSHOT_LATEST + '?includeDetail=true'),
        apiClient.get(ASSET_OPERATIONS_RECENT + '?limit=5'),
        apiClient.get(ASSET_CUMULATIVE_RETURN),
      ])
      set({
        balance: bal,
        latestSnapshot: snap,
        operationsRecent: ops?.items || [],
        cumulativeReturn: cum,
        loading: false,
      })
    } catch (err) {
      set({ error: err.message || 'fetchLatest failed', loading: false })
    }
  },

  reset: () => set({ balance: null, latestSnapshot: null, operationsRecent: [], cumulativeReturn: null, loading: false, error: null }),
}))
```

**其他 3 store**（userConfigStore / operationStore / chatStore）：同样加 `fetchLatest()`，API：
- `GET /config`
- `GET /operation/list`（如果存在）
- `GET /conversations?type=ai_assistant`

### Step 5：前端 - 6 个新 UI 组件

```
新建：
- fincontrol-frontend/src/components/cards/BalanceCard.jsx
- fincontrol-frontend/src/components/cards/TotalAssetCard.jsx
- fincontrol-frontend/src/components/cards/CumulativeReturnCard.jsx
- fincontrol-frontend/src/components/charts/SixCategoriesPie.jsx
- fincontrol-frontend/src/components/tables/CategoryDetailTable.jsx
- fincontrol-frontend/src/components/timeline/RecentOperationsTimeline.jsx
```

**`CumulativeReturnCard.jsx`**（核心）：
```jsx
import { useAssetSnapshotStore } from '../../stores/assetSnapshotStore'

export const CumulativeReturnCard = () => {
  const cum = useAssetSnapshotStore((s) => s.cumulativeReturn)
  if (!cum) return <Card loading />
  
  const { returnRate, algorithm } = cum
  const isPositive = returnRate >= 0
  const color = isPositive ? 'var(--color-success)' : 'var(--color-error)'
  
  return (
    <Card title="累计收益率">
      <div className="rate" style={{ color }}>
        {isPositive ? '+' : ''}{(returnRate * 100).toFixed(2)}%
      </div>
      <Tooltip content={algorithm === 'phase1_simple' 
        ? '简化版：Σcumulative/Σamount（is_latest=1, 不含余额类）'
        : 'Phase 3 升级为 Modified Dietz / IRR'}>
        <span className="algorithm-tag">{algorithm}</span>
      </Tooltip>
    </Card>
  )
}
```

### Step 6：前端 - HomePage 替换 PlaceholderPage

```
修改：
- fincontrol-frontend/src/pages/HomePage.jsx
  → 移除"PlaceholderPage" import
  → 添加 useEffect(() => useAssetSnapshotStore.getState().fetchLatest(), [])
  → JSX 布局：<BalanceCard> <TotalAssetCard> <CumulativeReturnCard> 横排 → <SixCategoriesPie> 全宽 → <details><CategoryDetailTable/></details> → <RecentOperationsTimeline>
```

### Step 7：前端 - 全局状态联动

`/data` confirm 后调 `useAssetSnapshotStore.getState().fetchLatest()` 重新拉取（1b.3 时实现，现在 1b.2 是首页 useEffect 自动加载）。

### Step 8：端到端联调（用 0716 真实数据）

```bash
# 1. 启动后端
cd fincontrol-backend
mvn spring-boot:run > log/backend.log 2>&1 &

# 2. 启动前端
cd fincontrol-frontend
npm run dev > log/frontend.log 2>&1 &

# 3. 上传 4 张截图
curl -X POST -H "X-User-Id: 1" \
  -F "file=@fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-1.jpg" \
  http://localhost:8080/api/screenshot/upload
# 重复 4 次（fileId 1, 2, 3, 4）

# 4. 触发解析
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"fileId":"<fileId1>","userId":1,"dataTime":"2026-07-16"}' \
  http://localhost:8080/api/screenshot/parse
# 重复 4 次

# 5. confirm
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{...}' \
  http://localhost:8080/api/snapshot/confirm

# 6. 验证累计收益率 API
curl http://localhost:8080/api/asset/cumulative-return | jq
# 期望：{ "code": 0, "data": { "algorithm": "phase1_simple", "totalCumulativeProfit": ..., "totalAmount": ..., "returnRate": ... } }

# 7. 浏览器访问 http://localhost:5173/ 截图三卡片 + 环形图 + 表格 + 时间线
```

**4 张原始截图**（与决策 20 存放位置规范对齐）：
- ✅ `fincontrol-backend/uploads/samples/phase1a10-alipay-fund-list-20260720-0048-{1,2,3,4}.jpg`
- 标号 `0720`（文件名时间戳）｜真实截图数据日期 `0716`｜本次 1b.2 联调联试用
- ⚠️ 不使用 `uploads/screenshots/`（决策 21：那是后端运行时缓存）

### Step 9：写 `/test/1b/2026-07-22-联调记录.md`

按决策 20 规范，包含：
1. **存放位置说明段**：原始截图在 `fincontrol-backend/uploads/samples/...`
2. **何时何地去向说明**：2026-07-22 创建，用于 1b.2 累计收益率端到端联调，归档后保留 30 天可删
3. 联调完整过程（命令序列 + 输出 + 截图引用）

### Step 10：收尾（验收报告 + README + decisions + checklist）

- 写 `docs/test-records/manual-tests/1b/2026-07-22_phase1b2-acceptance-report.md`
- 更新 README.md 1b.2 行（按决策 19）
- 更新 `docs/phase-1/checklists/phase-1b.md` 1b.6–1b.9, 1b.23 全部 [x]
- `git add . && git commit -m "feat(1b.2): 首页 + Recharts 环形图 + 累计收益率简化算法"` && `git push`

---

## 5. 风险与依赖

### 5.1 风险

| # | 风险 | 影响 | 应对 |
|---|------|------|------|
| R1 | 后端 API `/asset/cumulative-return` 旧版只返 `{available:false}`，改返回结构可能影响 1b.4 联调 | 中 | 加 `algorithm` 字段而非删除 available=false（向后兼容） |
| R2 | Recharts PieChart 在小屏幕下渲染异常 | 低 | CSS media query 隐藏图例 |
| R3 | 0716 真实数据的上传可能超时（minimax 限流） | 高 | 失败时改用 0715 数据（`phase1a2-alipay-fund-list-20260715-2355-{1,2,3,4}.jpg` 备用）|
| R4 | Step 8 端到端联调可能产生新 `screenshots/` 缓存，污染样本 | 低 | 决策 21 已规范，1b.2 不依赖 screenshots/ |
| R5 | 1b.3 联调之前需要先停下，确认这个 schema 变更不会破坏后续 | 中 | 验收报告中标注 "phase1_simple → phase3_* 待升级" |

### 5.2 依赖

- **后端已就绪**：全部 24 个 API、决策 4 placeholder、CumulativeReturnResponse DTO
- **前端 1b.1 已就绪**：项目骨架、4 store 空壳、Sidebar、Layout、Vite proxy
- **真实数据源**：0716 4 张图（已在 `uploads/samples/`）

---

## 6. 端到端联调产物（`/test/` 规范）

按 **决策 20**，本次联调产物**仅落盘到 `/test/1b/`**，**不传 GitHub**：

```
test/                                    ← 已 .gitignore
└── 1b/
    └── 2026-07-22-联调记录.md           ← Step 9 写
```

**`2026-07-22-联调记录.md` 必需段落**：
1. **存放位置说明**：4 张原始截图路径 + curl 命令模板
2. **何时何地去向说明**：2026-07-22 创建 / 1b.2 累计收益率联调 / 归档后保留 30 天可删
3. **联调全过程**：命令序列 + 关键响应 + 实际累计收益率值（对比算法预期）

---

## 7. 决策追加总结

本里程碑涉及 decisions.md **3 处更新**（已完成 Step 0 = 修订 4 v2 + 新增 20 + 21）：

| 决策 | 变更 | 影响 1b.2 |
|------|------|----------|
| 决策 4 v2 | 累计收益率卡片两阶段实现 | **必须**：后端算法 + 前端渲染第三张卡片 |
| 决策 20 | test/ 文件夹规范 | **必须**：联调产物放 `/test/1b/` |
| 决策 21 | screenshots/ 清理规范 | **不依赖**：本次不用 screenshots/ |

---

## 8. 验收人

- 计划编写：Cline（架构审查助手）
- 计划审阅：刘博丞（项目作者）
- 实施人：Cline（架构审查助手）
- 验收人：刘博丞（项目作者）

---

## 9. 文档维护

- 实施过程中如发现需要修改本计划，**不在本文件改**，而是在完工后的 `acceptance-report.md` 中记录"需求变更记录"段
- 重大决策追加到 `docs/phase-0/decisions.md`
- checklist 中对应项（1b.6–1b.9, 1b.23）勾选并填完成日期
- README.md 同步更新（决策 19）

---

*文档生成时间：2026-07-22*
*配套验收计划：[acceptance-plan.md](../../test-records/manual-tests/1b/2026-07-22_phase1b2-acceptance-plan.md)*
*前置 1b.1：[work-plan.md](../../test-records/manual-tests/1b/2026-07-21_phase1b1-work-plan.md)*
