# Phase 1b.4 PR1 验收计划（P0 三条 + GLOBAL-007/015）

> **状态**：📝 待执行；PR 1 改前端代码 + 写 3 个 vitest 测试
> **日期**：2026-07-24
> **需求基线**：[FinControl 全站页面需求说明书](../../../requirements/2026-07-22_fincontrol-page-requirements.md)（v0.1-DRAFT，本 PR **不冻结 v1.0**）
> **工作计划**：[Phase 1b.4 PR1 工作计划](../../../phase-1/work-plans/1b/2026-07-24_1b4-pr1-work-plan.md)
> **前置**：[PR 0 工作计划 + 验收计划](./2026-07-24_1b4-pr0-acceptance-plan.md) ✅
> **配套 review 报告**：[Phase 1b.3 设计 review 报告](../../../checklists/2026-07-23_phase1b3-design-bug-list.md)

---

## 0. 验收原则

1. **修复有效**：每个 bug ID 都有对应的代码变更 + 测试覆盖 + 浏览器验证三处证据。
2. **成熟逻辑零回归**：累计/持有算法、决策 27、Phase 1b.3 R1-R4 修复成果**只读不改**。
3. **本 PR 不越界**：P1 / P2 其他条目留给 PR 2-4 验证；CSS 颜色 / 字号 / 断点 / favicon / title / alert 等不在本 PR 范围。
4. **范围护栏可验证**：每个"明确不做"都对应到具体代码 / 文档引用。

---

## 1. 验收层级与状态

| 层级 | 范围 | 证据 |
| --- | --- | --- |
| A. 文档门禁 | 工作计划 + 验收计划 + review 报告映射 | 文档自检 + 用户 review |
| B. 单元测试 | friendlyError / revokeAll / revokeOne / StateShell | `npm test -- --run` 全绿 |
| C. 构建 | vite build 无错无 warning | `npm run build` 通过 |
| D. 累计/持有硬门禁 | 现有 `CumulativeReturnCard` + `AssetQueryServiceTest` 测试 | vitest 全绿（未触碰） |
| E. 浏览器功能 | 首页三态 / 数据页 blob 释放 / 友好错误 | 手动 + DevTools |

状态标识：`☐ 未执行`、`◐ 部分通过`、`☑ 通过`、`✗ 失败`、`N/A 本阶段不适用`。

---

## 2. PR 1 工作计划验收

| ID | 验收项 | 期望 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| WP1-001 | 顶部 metadata 齐全（status / 日期 / 前置 / 配套 / 后续 PR / v1.0 冻结时机） | 6 项齐全 | ☐ | |
| WP1-002 | §0 范围表含 4 条（HOME-001/002/003/GLOBAL-015 + 顺手 GLOBAL-007） | 全列 | ☐ | |
| WP1-003 | §1 DoD 含 6 项（代码 / 测试 / build / 硬门禁 / commit / review） | 全列 | ☐ | |
| WP1-004 | §2 明确不做 ≥ 9 条（业务逻辑 / 1b.5 / 1b.6 / 后端 / 依赖 / v1.0 / P1+P2 / 重构 / CSS / favicon） | 全列 | ☐ | |
| WP1-005 | §3.1 StateShell 设计 + 不依赖新依赖 | 满足 | ☐ | |
| WP1-006 | §3.2 HomePage 改动清单精确（useEffect / StateShell / friendlyError） | 满足 | ☐ | |
| WP1-007 | §3.4 DataPage 改动清单精确（revokeAll 调用点 + cleanup useEffect） | 满足 | ☐ | |
| WP1-008 | §3.5 测试拆分清晰（friendlyError / StateShell / blob 各 1 个测试文件） | 满足 | ☐ | |
| WP1-009 | §4 代码改动清单完整（6 个新文件 + 4 个改动文件 + 3 个测试文件） | 全列 | ☐ | |
| WP1-010 | §7 提交计划：1 个 commit + 1 个 push | 满足 | ☐ | |
| WP1-011 | §8 时间表 ≤ 1 小时 | 满足 | ☐ | |

---

## 3. PR 1 单元测试验收（自动化）

### 3.1 friendlyError 工具测试

文件：`fincontrol-frontend/src/tests/utils/formatters.friendlyError.test.js`

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-101 | null / undefined → "未知错误" | 通过 | ☐ |
| PR1-102 | string → 直接返回 | 通过 | ☐ |
| PR1-103 | axios 网络异常（code 0）→ "网络异常，请检查后端服务" | 通过 | ☐ |
| PR1-104 | axios 业务码 4xx → "请求参数错误" | 通过 | ☐ |
| PR1-105 | axios 业务码 5xx → "服务器错误，请稍后重试" | 通过 | ☐ |
| PR1-106 | axios stack 长字符串 → 通用文案（不暴露 stack） | 通过 | ☐ |
| PR1-107 | 短错误消息 → 透传 | 通过 | ☐ |

### 3.2 blob URL 释放工具测试

文件：`fincontrol-frontend/src/tests/utils/blob.test.js`

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-201 | revokeAll：释放所有有 url 的项 | 通过 | ☐ |
| PR1-202 | revokeAll：空数组 → 不调用 | 通过 | ☐ |
| PR1-203 | revokeAll：revokeObjectURL 抛错时不影响其他项 | 通过 | ☐ |
| PR1-204 | revokeOne：释放单个项 | 通过 | ☐ |
| PR1-205 | revokeOne：没有 url → 不调用 | 通过 | ☐ |
| PR1-206 | revokeOne：revokeObjectURL 抛错时不抛出 | 通过 | ☐ |

### 3.3 StateShell 组件测试

文件：`fincontrol-frontend/src/tests/components/home/StateShell.test.jsx`

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-301 | 渲染 icon / title / sub / action | 通过 | ☐ |
| PR1-302 | 不传 sub / action 也能正常渲染 | 通过 | ☐ |

### 3.4 累计/持有收益成熟逻辑保护（硬门禁）

> 本 PR 不应触碰 `CumulativeReturnCard.jsx` 内部业务代码。

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-401 | 现有 `CumulativeReturnCard` 测试套全绿 | 通过 | ☐ |
| PR1-402 | `AssetQueryServiceTest` 全绿（后端零改动也跑一遍） | 通过 | ☐ |
| PR1-403 | `git diff fincontrol-frontend/src/components/CumulativeReturnCard.jsx` 无内容 | 满足 | ☐ |
| PR1-404 | `git diff fincontrol-backend/` 无内容 | 满足 | ☐ |

### 3.5 构建验收

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-501 | `npm --prefix fincontrol-frontend test -- --run` 全绿 | 通过 | ☐ |
| PR1-502 | `npm --prefix fincontrol-frontend run build` 通过 | 通过 | ☐ |
| PR1-503 | build 输出无新增 React warning | 满足 | ☐ |
| PR1-504 | 后端 smoke：`mvn -f fincontrol-backend/pom.xml test -DfailIfNoTests=false` 全绿 | 通过 | ☐ |

---

## 4. PR 1 浏览器功能验收（手动 + DevTools）

### 4.1 首页（HOME-001 / HOME-002 / HOME-003 / GLOBAL-007）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-601 | 直接打开 `http://localhost:5173/` 刷新立即看到首页数据 | 浏览器手动：刷新后 < 500ms 显示 | ☐ |
| PR1-602 | 首次访问无数据时空态有"立即上传 →"按钮 + 点击跳转 `/data` | 浏览器手动 + Playwright | ☐ |
| PR1-603 | StateShell 三态视觉一致（loading / error / empty） | 浏览器手动 + 截图对比 | ☐ |
| PR1-604 | 错误态显示友好提示（如"网络异常，请检查后端服务"），不暴露 axios stack | 浏览器手动 + DevTools 审查 | ☐ |
| PR1-605 | 错误态有"重试"按钮 + 点击重新拉数据 | 浏览器手动 | ☐ |
| PR1-606 | 累计/持有卡片 4 个 Info 弹窗可正常打开（成熟逻辑保护） | 浏览器手动 | ☐ |

### 4.2 数据管理（GLOBAL-015）

| ID | 验收项 | 期望 | 状态 |
| --- | --- | --- | --- |
| PR1-701 | 上传 4 张图：浏览器 Memory 面板看到 4 个 blob URL 创建 | DevTools Memory 录制 | ☐ |
| PR1-702 | 删除 1 张：URL.revokeObjectURL 被调用 1 次 | DevTools Memory 录制 | ☐ |
| PR1-703 | 重新上传 4 张：旧 3 个被 revoke，新 4 个创建 | DevTools Memory 录制 | ☐ |
| PR1-704 | confirm 成功：所有 4 个 blob URL 被 revoke | DevTools Memory 录制 | ☐ |
| PR1-705 | 切换页面（导航走 `/` → `/data`）：DataPage unmount 时所有 blob 被 revoke | DevTools Memory 录制 | ☐ |

---

## 5. 累计/持有收益成熟逻辑保护验收（硬门禁）

任何 PR 失败都不得以"视觉已完成"验收。

### 5.1 数据语义不变

| 展示格 | API 字段 | 现行语义 | 验收 |
| --- | --- | --- | --- |
| 累计收益率 | `returnRate` | `Σ cumulativeProfit / Σ amount` | 与 API 一致 |
| 持有收益率 | `holdingReturnRate` | `Σ holdingProfit（含余额宝调整）/ Σ amount` | 与 API 一致 |
| 累计收益额 | `totalCumulativeProfit` | 当前口径累计收益合计 | 与 API 一致 |
| 持有收益额 | `totalHoldingProfit` | 当前口径持有收益合计 | 与 API 一致 |

- [ ] 本 PR 不修改 `CumulativeReturnCard` 内部业务代码（git diff 验证）；
- [ ] `phase1_simple` / `rawHoldingProfit` / `balanceFundAdjustment` / `balanceFundStatus` 现行处理不变；
- [ ] 旧的 `AssetQueryServiceTest` 和 `CumulativeReturnCard` 相关 fixture 全部通过。

### 5.2 布局硬保护

桌面端必须可读为 2×2 矩阵（收益率 + 收益额 × 累计 + 持有），4 个 Info 入口均可打开和关闭。

---

## 6. snapshot_meta 双层语义保护（硬门禁）

- [ ] 本 PR 不触碰 `snapshot_meta` 写入路径；
- [ ] 不修改 `is_current` 权威源；
- [ ] `git diff fincontrol-backend/` 无内容。

---

## 7. 范围护栏验证

| ID | 护栏 | 验证方法 | 状态 |
| --- | --- | --- | --- |
| PR1-901 | 不改业务逻辑 | §3.4 + §5 | ☐ |
| PR1-902 | 不动 1b.5 / 1b.6 | git diff 检查 | ☐ |
| PR1-903 | 不重写后端 | `git diff fincontrol-backend/` 无内容 | ☐ |
| PR1-904 | 不引入新依赖 | `package.json` 无 diff | ☐ |
| PR1-905 | 不冻结 v1.0 | `requirements` 文件无 diff | ☐ |
| PR1-906 | 不做 P1 / P2 其他条目 | git diff 检查（无 CSS 字面量 / favicon / title / alert 等） | ☐ |
| PR1-907 | 不重构 HomePage / DataPage 内部结构 | git diff 检查（仅替换三态分支 + 加 useEffect + 加 revokeAll 调用） | ☐ |

---

## 8. 回归与最终命令

```cmd
:: 后端 smoke test（零改动也应跑一遍）
mvn -f fincontrol-backend/pom.xml test -DfailIfNoTests=false

:: 前端单元测试 + 构建
npm --prefix fincontrol-frontend test -- --run
npm --prefix fincontrol-frontend run build

:: 累计/持有算法保护（决策 4 v2 + 决策 25 v3）
:: 响应字段必须包含 rawHoldingProfit / balanceFundAdjustment / balanceFundStatus
curl -s "http://localhost:8080/api/asset/cumulative-return" -H "X-User-Id: 1"
```

期望：后端 + 前端全量通过；累计/持有响应字段稳定不变；`npm run build` 无新增 React warning。

---

## 9. 最终退出条件与签字

- [ ] WP1-001~011 全部通过；
- [ ] PR1-101~107 / PR1-201~206 / PR1-301~302 单元测试全绿；
- [ ] PR1-401~404 累计/持有硬门禁通过；
- [ ] PR1-501~504 构建 + 后端 smoke 全绿；
- [ ] PR1-601~606 首页浏览器功能通过；
- [ ] PR1-701~705 数据管理 blob 释放通过（DevTools Memory 验证）；
- [ ] §5 累计/持有数据语义不变；
- [ ] §6 snapshot_meta 保护通过；
- [ ] §7 范围护栏 7 项验证通过；
- [ ] §8 回归命令全绿；
- [ ] 1 个 commit + push 到 origin/main 完成；
- [ ] 用户最终 review 通过。

| 角色 | 姓名 | 日期 | 结论 |
| --- | --- | --- | --- |
| 产品验收 | 刘博丞 | ____ | ____ |
| 实施/复核 | Cline | ____ | ____ |

---

*本文件是 PR 1 的验收基线，不提前勾选未实际执行的代码与视觉验收项。*