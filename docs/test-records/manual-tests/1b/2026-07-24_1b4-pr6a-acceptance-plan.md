# Phase 1b.4 PR6a 验收计划（预览弹窗快照日期可点击修改 · 2-step UX）

> **日期**：2026-07-24
> **配套工作计划**：（本 PR 无独立工作计划，修复范围见 1b.3 设计 review 报告 PR3+ BUG-003 → PR4a DATA-012 删除 → PR6a 恢复）
> **关联决策**：[Decision 13（snapshotDate 来源优先级 + dataTime 字段）](../../phase-1/decisions/decision-13-snapshotdate-source-priority-and-datetime-field.md)
> **关联 commit**：`0f4e09e fix(1b.4-PR6a): 预览弹窗快照日期可点击修改（2-step UX）`

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.4 PR6a（预览弹窗快照日期编辑 · 2-step UX） |
| 完成日期 | 2026-07-24 23:13 |
| 修复人 | Cline（按用户指令：恢复日期编辑 + 2-step 确认门控） |
| 关联 commit | `0f4e09e` |
| 验收 commit | 待用户最终确认 |

---

## 1. PR6a 修复范围

**根因**：
- PR3+ BUG-003 在 PR3+ 中实现了"预览弹窗快照日期可点击编辑（3 select 滚轮）"
- PR4a DATA-012 实施"modal 中快照日期卡移到 header 副标题，overview-summary 5 列 → 4 列"时**显式删除**了 `editingDate/editYear/editMonth/editDay` 编辑状态机：
  ```js
  // PR4a DATA-012：editingDate/editYear/editMonth/editDay 编辑状态机已删除（日期移到 header 副标题）
  ```
- 用户实测时反馈：上传时若日期选错，预览无法修正

**PR6a 方案**：2-step UX 恢复（不破坏现有主结构 + 确认门控）
1. **Step 1（点击触发）**：modal-header 副标题 `modal-date-clickable` 鼠标移上去出现 hover 高亮 + ✎ 提示图标。点击 → 弹出确认问询弹窗："需要修改快照日期吗？当前入库的是 X 快照。取消则返回预览；确认则进入日期选择"
2. **Step 2（确认后展开）**：确认后才出现 3 select 滚轮（年 / 月 / 日），初值 = 当前 snapshotDate
3. **确定**：调用 `setSnapshotDate` 更新本地状态（modal-header 副标题立即更新），用户后续点"确认入库"时 `POST /api/snapshot/confirm` 用新日期
4. **取消 / 背景点击**：任何阶段都返回预览，不影响 AI 解析结果

---

## 2. 改动清单

| 文件 | 性质 | 行数 |
| --- | --- | --- |
| `fincontrol-frontend/src/pages/DataPage.jsx` | 改：+5 state + 4 helper + modal-header 点击 + 2 弹窗 JSX | +98 -2 |
| `fincontrol-frontend/src/styles/data-page.css` | 改：+ .modal-date-clickable / .modal-date-edit-hint / .modal-confirm-backdrop/card/title/sub/actions / .date-edit-row/select | +109 -0 |
| **总 diff** | | **+207 -3（commit `0f4e09e`）** |

后端零改动（复用现有 confirm API）。

---

## 3. 验收清单

### 3.1 自动化验收

- [x] vitest 46/46 通过（PR6 改动无回归）
- [x] vite build 1.20s 通过（无 React warning）
- [x] PR6 改动 100% 在前端，confirm() 逻辑零修改
- [x] modal-header 副标题仍按 PR4a DATA-012 显示 snapshotDate 副标题（Y / M / D 顺序未变）

### 3.2 视觉验收（手动）

| # | 场景 | 预期 | 验证人 |
|---|------|------|---|
| 1 | 打开预览弹窗，鼠标移到 `2026-07-24` | 出现浅蓝高亮 + ✎ 图标变明显 + cursor: pointer |  |
| 2 | 点击 `2026-07-24` | 弹出"需要修改快照日期吗？"确认弹窗（半透明 backdrop + 居中卡片） |  |
| 3 | 确认弹窗点"取消" | 关闭弹窗，预览不变 |  |
| 4 | 确认弹窗点"确认修改" | 进入日期选择（3 select 滚轮：年 / 月 / 日） |  |
| 5 | 日期选择背景点击（非按钮区） | 关闭弹窗（cancelDateEdit） |  |
| 6 | 日期选择点"取消" | 关闭弹窗，预览不变 |  |
| 7 | 日期选择选 2026-07-15 + 点"确定" | modal-header 副标题更新为 "2026-07-15" |  |
| 8 | 修改后点"确认入库" | POST /api/snapshot/confirm 用 2026-07-15 入库 |  |
| 9 | 修改后选"取消" | 关闭日期选择弹窗，preview modal 仍展示 |  |
| 10 | 修改日期 ≠ 当前日期 | 顶部"4 张图与日期不一致"提示不出现（PR3+ 错误处理） |  |
| 11 | 多次连续编辑日期 | 每次只更新本地 snapshotDate，不重跑 AI |  |

### 3.3 后端验收

- [ ] 后端零改动（commit `0f4e09e` 不含 `fincontrol-backend/` 任何文件）
- [ ] confirm API 仍接受 `snapshotDate` 字段（已实现，PR3+ 路径）
- [ ] DedupEngine 仍按 `snapshotDate` 维度 A 分组（已实现）

---

## 4. 风险

- 风险 1：与 PR4a DATA-012 的"header 副标题"重复 → 缓解：modal-header 副标题保留，**新交互是叠加在副标题上**（点击副标题才触发），不破坏 PR4a 的布局
- 风险 2：editYear/Month/Day 初值乱（如 2026-13-32）→ 缓解：select 范围限定（年 2023-2027 / 月 1-12 / 日 1-31），前端不可能选到非法日期
- 风险 3：3 select 的可访问性 → 缓解：保留原 keyboard 操作（Tab + Enter），点击 modal-date-clickable 后可 Tab 到 select

---

## 5. 验收通过标准

- [x] 自动化验收（vitest + build）通过
- [ ] 视觉验收 11 项场景全部通过
- [x] 后端零改动验证（commit diff 确认）

---

## 6. 关联决策 / 文件

- [决策 13 snapshotDate 来源优先级 + dataTime 字段](../../phase-1/decisions/decision-13-snapshotdate-source-priority-and-datetime-field.md)
- [Decision 27 is_latest 双层语义 + 跨日期 is_current](../../phase-1/decisions/decision-27-is-latest-dual-layer.md)
- [1b.3 设计 review 报告 — BUG-003 日期可点击编辑原始定义](../../checklists/2026-07-23_phase1b3-design-bug-list.md)
- [PR4a 验收报告 — DATA-012 实施说明](./2026-07-24_1b4-pr4a-acceptance-report.md)
- [PR4a 验收计划（对照模板）](./2026-07-24_1b4-pr4a-acceptance-plan.md)
- commit `0f4e09e fix(1b.4-PR6a): 预览弹窗快照日期可点击修改（2-step UX）`

---

*完成时间：2026-07-24 23:13*
*验收人：待用户最终确认（PR6a 修复点清晰，预计可快速通过）*
