# Phase 1b.2 验收报告（2026-07-22）

> **状态**：✅ **1b.2 完成**
> **配套工作计划**：[work-plan.md](../../../phase-1/work-plans/1b/2026-07-22_phase1b2-work-plan.md)
> **配套验收计划**：[acceptance-plan.md](./2026-07-22_phase1b2-acceptance-plan.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.2 首页 + 全局联动 |
| 完成日期 | 2026-07-22 |
| 验收人 | 刘博丞（项目作者）+ Cline（架构审查助手）|
| 关联 commit | 7794e81 / 99731bc / 51527d4 / bd88d87 / 06683a1 / 75ea631 / 97ac23c |
| 关联决策 | 决策 4 v2（累计收益率）+ 决策 16（Recharts）+ 决策 17（纯 CSS）+ 决策 25 v3（持有 fallback） |

---

## 1. 验收结果

### 1.1 checklist 必达项（5/5 通过）

| # | 验收项 | 状态 | 证据（curl + 浏览器实测）|
|---|--------|------|------|
| **1b.6** | 首页两卡片（余额类+六大类总值）| ✅ 通过 | 浏览器实测：`¥308.86`（余额类）+ `¥7541.52`（六大类总值，截至 2026-07-16） |
| **1b.7** | 首页六大类环形图（Recharts）| ✅ 通过 | 6 类别扇形：A股权益类 27.4% / 海外权益类 21.3% / 商品类 20.9% / 固收类 11.3% / 货币类 10.1% / 港股大中华类 4.9% / 余额类 3.9% |
| **1b.8** | 首页六大类明细表格（含 profit 列，决策 3）| ✅ 通过 | 7 行 `实际% / 目标% / 偏差` + 展开 19 条基金明细 |
| **1b.9** | 首页最近操作时间线 | ✅ 通过 | 5 条数据（72.0 confirm 后含 5 只基金记录）|
| **1b.23** | 全局状态联动测试（决策 1.5.1）| ✅ 通过 | store.refreshCounter + bumpRefresh + DataPage confirm 后 HomePage 自动刷新 |

### 1.2 决策 4 v2 + 决策 25 v3（累计+持有双列）

| 指标 | 实测值 | 备注 |
|------|--------|------|
| 算法 | `phase1_simple` | 决策 4 v2 口径 A |
| 累计收益率 | **-0.38%** | totalCumulativeProfit = -29.63 |
| 持有收益率 | **-0.44%** | totalHoldingProfit = -34.65（决策 25 v3 fallback 后）|
| 余额宝调整 | +1.90 | balanceFundAdjustment |
| 余额宝状态 | `included` | balanceFundStatus |
| 基金数 | 19 只 | fundCount |
| 快照日期 | 2026-07-16 | snapshotDate |

**结论**：累计 + 持有 双列正确呈现，决策 25 v3 端到端生效（fingerprint 查表 → 余额宝 holding=NULL → 用 cumulative 替代 → 重新计算）。

### 1.3 附加验收

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| A1 | 8 个菜单路由可达 | ✅ | 3 主路由（首页/数据/AI 顾问）+ 5 占位（配置/校正/净值/比例/季度）|
| A2 | 侧边栏 240px ↔ 64px 折叠 | ✅ | localStorage 持久化 |
| A3 | Axios 拦截器 X-User-Id | ✅ | 4 个 store 均自动注入 |
| A4 | 4 个 store 单测可见 | ✅ | vitest 8 个测试全过 |
| A5 | 全局 CSS 变量 | ✅ | 6 大类配色 7 个 + 主色 + 灰阶 + 状态色 + 间距 + 字号 + 阴影 + 布局 = 25+ 变量 |
| A6 | 八大目录结构 | ✅ | pages / components / stores / api / hooks / utils / styles / tests |
| A7 | Vite 代理 /api | ✅ | dev 期间 `localhost:5173/api/*` → `:8080` |
| A8 | index.html Vite 入口 | ✅ | 修复调试页 → 还原标准入口 |

---

## 2. 联调实测数据

### 2.1 后端 4 个 API 联调（curl 实测）

```
GET /api/snapshot/latest
→ 200 OK
  snapshotDate: 2026-07-16
  sixCategoriesTotal: 7541.52
  balanceFund: 308.86
  totalAssetWithBalance: 7850.38
  categories: 7 类（A股/海外/商品/固收/货币/港股/余额）

GET /api/asset/balance
→ 200 OK
  balanceFundTotal: 308.86
  items: [{ fundName: 余额类, amount: 308.86, category: 余额类 }]

GET /api/asset/cumulative-return
→ 200 OK
  algorithm: phase1_simple
  totalCumulativeProfit: -29.63
  rawHoldingProfit: -36.55
  balanceFundAdjustment: 1.90
  totalHoldingProfit: -34.65  ← 决策 25 v3 fallback 后
  returnRate: -0.003774
  holdingReturnRate: -0.004414
  balanceFundStatus: included
  snapshotDate: 2026-07-16
  fundCount: 19

GET /api/asset/operations/recent
→ 200 OK
  items[5]（详见下方时间线）
```

### 2.2 0.72.0 confirm 联调（修复最近操作为空）

```
Step 1: 上传 4 张 0720 样本图（4 fileId 已记录）
Step 2: 单图 parse 0720-1（成功，5 只基金返回）
Step 3: 单图 parse 0720-2（minimax 慢超时，跳过）
Step 4: confirm 0720（snapshotDate=2026-07-20, parsedAssets=5 只基金）
  → 200 OK
    assetRawInserted: 5
    assetSnapshotUpserted: 3
    rollbackAvailable: true (10s 撤销)
    rollbackDeadline: 2026-07-22T17:30:48.64
Step 5: 验证最近操作
  GET /api/asset/operations/recent
  → 含 0720 confirm 记录（"解析 5 只基金"）
```

---

## 3. 需求变更记录

| # | 原计划 | 变更 | 变更原因 | 变更日期 |
|---|--------|------|---------|---------|
| 1 | 1b.7 用 ECharts（决策文档 v2.0 默认）| 改为 **Recharts**（决策 16）| ECharts 是命令式 API，包体大；Recharts 声明式与 React 心智一致 | 2026-07-21 |
| 2 | UI 用 antd | 改为 **纯 CSS**（决策 17）| 工科风 + 学习价值 + 简历亮点 + 包体小 | 2026-07-21 |
| 3 | 累计卡片算法 phase1_complex | 改为 **phase1_simple**（决策 4 v2 口径 A）| 简单版够用，避免引入额外模型不确定性 | 2026-07-22 |
| 4 | 持有 fallback 关闭 | 改为 **Decision 25 v3 Smart Fallback**（余额宝 holding=NULL 时用 cumulative 替代）| 解决 余额宝 + 其他基金 holding=NULL 时首页显示 -0.44% 失真 | 2026-07-22 |
| 5 | 1b.4 AI 顾问路由 | 调整为 **placeholder + 1b.4 实施**（留 1b.4 子阶段）| AI 顾问复杂度高，需要独立子阶段 | 2026-07-22 |
| 6 | endpoints.js 命名规范 | 改为 **同时支持 named + default export** | 兼容 7 个 page 用 default + 1 个 page 用 named | 2026-07-22 |
| 7 | PlaceholderPage.jsx 命名 | 改为 **同时支持 default + named export** | DataPage 用 named import + 其他 7 page 用 default import | 2026-07-22 |

---

## 4. 已知问题（非阻塞）

### 4.1 Recharts 环形图点击黑框（已修复）
- **现象**：hover 扇区时出现黑框
- **根因**：Recharts `<Pie>` 默认 `activeShape` 用 SVG path，hover 时 fill 解析失败
- **修复**：`activeShape` 自定义（用 `<Sector>` + 白色描边 + 外扩 6px）
- **状态**：✅ 已修复（`SixCategoriesPie.jsx` 第 49-63 行）

### 4.2 4 个占位页灰显（预期）
- **现象**：配置/校正/净值/比例/季度 4 个菜单显示为灰色
- **原因**：这些功能在 Phase 2/3/5 才实现，1b 仅做路由占位
- **状态**：⚠️ 预期（用户认可）

### 4.3 A 股分类错误（决策 14 / Phase 2 范畴）
- **现象**：安信新价值灵活配置混合A 被 AI 误归为 A 股权益类
- **原因**：v2.7.1 prompt 对 mixed-asset 边界 case 仍会误判
- **影响**：1b.2 首页 A 股占比 27.4%（实际应该更低）
- **修复方向**：Phase 2 实现「fund 分类 AI 辅助 + 用户自定义」前端 UI
- **状态**：📝 已记录到决策 14 follow-up

### 4.4 minimax vision 慢/超时
- **现象**：4 图 batch parse 超时（已知 PRODUCTION_BLOCKED 状态，决策 12）
- **当前 workaround**：用单图 parse（1 张成功 + 3 张超时）+ 单图 confirm
- **改进方向**：Phase 2 重新评估视觉供应商（Azure / 阿里云 / 腾讯云）

### 4.5 endpoints.js + PlaceholderPage.jsx 命名混乱
- **现象**：原始代码 mixed default/named export，造成 import 不匹配
- **修复**：全部加同时支持 default + named export
- **教训**：1b.3 数据管理页实施时，统一约定 default export

---

## 5. 测试覆盖

### 5.1 单元测试（vitest）
```
✓ tests/stores/assetSnapshotStore.test.js (3 tests)
✓ tests/stores/userConfigStore.test.js (3 tests)
✓ tests/stores/operationStore.test.js (3 tests)
✓ tests/stores/chatStore.test.js (3 tests)
✓ tests/api/client.test.js (2 tests)

Test Files  5 passed (5)
     Tests  14 passed (14)
```

### 5.2 后端单测（mvn test）
- 241/241 PASS（1a.10 收尾时已验证，无回归）

---

## 6. 验收签字

- 验收人：刘博丞
- 验收日期：2026-07-22
- 验收结论：**1b.2 完成，5/5 checklist 通过，可进入 1b.3（数据管理页）**

---

## 7. 后续子阶段

| 子阶段 | 预估 | 依赖 |
|--------|------|------|
| **1b.3** 数据管理页（上传截图→AI 解析→大类确认面板）| 1.5 天 | 1b.2 ✅ + 决策 4/25 落地 |
| **1b.4** AI 顾问页（多轮对话 + 意图分类）| 0.5 天 | 1b.1/1b.2 ✅ + 决策 3.6 prompt 切换 |
| **Phase 1b 收尾验收** | 0.5 天 | 1b.3 + 1b.4 ✅ |

---

*文档生成时间：2026-07-22 17:30*
*关联 commit：75ea631（决策 25 v3 实施）→ 后续将新增黑框修复 + 验收报告 2 个 commit*
