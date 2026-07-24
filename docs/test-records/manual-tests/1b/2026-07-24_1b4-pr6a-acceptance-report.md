# Phase 1b.4 PR6a 验收报告（2026-07-24 23:14）

> **状态**：✅ **通过**（2-step UX 预览弹窗快照日期可点击修改，46/46 测试通过，build 无 warning）
> **配套联调记录**：[test/1b/2026-07-24-1b4-pr6a-联调记录.md](../../../test/1b/2026-07-24-1b4-pr6a-联调记录.md)
> **配套验收计划**：[docs/test-records/manual-tests/1b/2026-07-24_1b4-pr6a-acceptance-plan.md](./2026-07-24_1b4-pr6a-acceptance-plan.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段 | 1b.4 PR6a（预览弹窗快照日期编辑 · 2-step UX） |
| 完成日期 | 2026-07-24 23:14 |
| 修复人 | Cline（按用户指令：恢复日期编辑 + 2-step 确认门控） |
| 关联 commit | `0f4e09e` |
| 验收 commit | `0f4e09e`（同 commit，主分支已包含） |

---

## 1. PR6a 改动清单

| 文件 | 性质 | 行数 |
| --- | --- | --- |
| `fincontrol-frontend/src/pages/DataPage.jsx` | 改：+5 state（confirmingDateEdit, editingDate, editYear/Month/Day）+ 4 helper + modal-header 点击 + 2 弹窗 JSX | +98 / -2 |
| `fincontrol-frontend/src/styles/data-page.css` | 改：+ .modal-date-clickable / .modal-date-edit-hint / .modal-confirm-backdrop/card/title/sub/actions / .date-edit-row/select | +109 / -0 |
| **总 diff** | | **+207 / -3（commit `0f4e09e`）** |

后端零改动（复用现有 confirm API，commit 不含 `fincontrol-backend/` 任何文件）。

---

## 2. PR6a 修复内容

### 2.1 新增 State（5 个）

```js
// PR6（1b.4 后 PR）：预览弹窗快照日期可点击修改（2-step UX）
const [confirmingDateEdit, setConfirmingDateEdit] = useState(false)  // 确认弹窗显隐
const [editingDate, setEditingDate] = useState(false)                  // 日期选择器显隐
const [editYear, setEditYear] = useState(new Date().getFullYear())     // 年初值
const [editMonth, setEditMonth] = useState(new Date().getMonth() + 1)  // 月初值
const [editDay, setEditDay] = useState(new Date().getDate())           // 日初值
```

### 2.2 新增 Helper（4 个）

```js
function openDateEdit() {
  // 解析当前 snapshotDate 为年/月/日初值
  const parts = (snapshotDate || '').split('-')
  if (parts.length === 3) {
    setEditYear(parseInt(parts[0], 10))
    setEditMonth(parseInt(parts[1], 10))
    setEditDay(parseInt(parts[2], 10))
  }
  setConfirmingDateEdit(true)  // 先弹确认
}
function confirmDateEdit() {
  setConfirmingDateEdit(false)
  setEditingDate(true)
}
function cancelDateEdit() {
  // 取消（无论在确认态还是编辑态）
  setConfirmingDateEdit(false)
  setEditingDate(false)
}
function applyDateEdit() {
  const yyyy = String(editYear).padStart(4, '0')
  const mm = String(editMonth).padStart(2, '0')
  const dd = String(editDay).padStart(2, '0')
  setSnapshotDate(`${yyyy}-${mm}-${dd}`)  // ← 关键：只改前端本地状态，confirm() 已自动用此值
  setEditingDate(false)
}
```

### 2.3 modal-header 副标题改为可点击

```jsx
<span
  className="modal-date modal-date-clickable"     // ← 新增 modal-date-clickable 类
  data-testid="modal-snapshot-date"
  onClick={openDateEdit}                          // ← 点击触发确认弹窗
  role="button"
  tabIndex={0}
  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') openDateEdit() }}
  title="点击修改快照日期"
>
  {snapshotDate} <span className="modal-date-edit-hint">✎</span>   // ← 新增 ✎ 提示
</span>
```

### 2.4 二级弹窗 JSX（叠加在 preview modal 上方，不影响主结构）

```jsx
{/* PR6：点击日期 → 确认弹窗（"需要修改吗？"）→ 点击确认才出现 3 select 滚轮 */}
{confirmingDateEdit && (
  <div className="modal-confirm-backdrop" onClick={cancelDateEdit}>
    <div className="modal-confirm-card" onClick={(e) => e.stopPropagation()}>
      <div className="modal-confirm-title">需要修改快照日期吗？</div>
      <div className="modal-confirm-sub">
        当前入库的是 <strong>{snapshotDate}</strong> 的快照。
        取消则返回预览；确认则进入日期选择。
      </div>
      <div className="modal-confirm-actions">
        <button className="secondary-btn" onClick={cancelDateEdit}>取消</button>
        <button className="primary-btn" onClick={confirmDateEdit}>确认修改</button>
      </div>
    </div>
  </div>
)}

{/* PR6：日期选择弹窗（3 select 滚轮 + 确定/取消） */}
{editingDate && (
  <div className="modal-confirm-backdrop" onClick={cancelDateEdit}>
    <div className="modal-confirm-card" onClick={(e) => e.stopPropagation()}>
      <div className="modal-confirm-title">选择新的快照日期</div>
      <div className="modal-confirm-sub">
        改后预览中"今日资产预览 · X" 会立即更新，AI 解析结果不变，仅改提交日期。
      </div>
      <div className="date-edit-row">
        <select className="date-edit-select" value={editYear} onChange={(e) => setEditYear(parseInt(e.target.value, 10))}>
          {Array.from({ length: 5 }, (_, i) => 2023 + i).map((y) => (<option key={y} value={y}>{y} 年</option>))}
        </select>
        <select className="date-edit-select" value={editMonth} onChange={(e) => setEditMonth(parseInt(e.target.value, 10))}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (<option key={m} value={m}>{m} 月</option>))}
        </select>
        <select className="date-edit-select" value={editDay} onChange={(e) => setEditDay(parseInt(e.target.value, 10))}>
          {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (<option key={d} value={d}>{d} 日</option>))}
        </select>
      </div>
      <div className="modal-confirm-actions">
        <button className="secondary-btn" onClick={cancelDateEdit}>取消</button>
        <button className="primary-btn" onClick={applyDateEdit}>确定</button>
      </div>
    </div>
  </div>
)}
```

### 2.5 CSS（data-page.css 末尾追加 PR6 段）

```css
/* modal-header 副标题可点击样式 */
.data-page .modal-date-clickable { cursor: pointer; border-bottom: 1px dashed transparent; padding: 2px 4px; ... }
.data-page .modal-date-clickable:hover { background: var(--color-bg-blue-light); border-bottom-color: var(--color-primary); }
.data-page .modal-date-edit-hint { margin-left: 6px; opacity: 0.4; transition: opacity 0.15s ease; }
.data-page .modal-date-clickable:hover .modal-date-edit-hint { opacity: 1; }

/* 二级弹窗（确认 + 选择器共用） */
.data-page .modal-confirm-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 1100; display: flex; align-items: center; justify-content: center; padding: 16px; }
.data-page .modal-confirm-card { background: #fff; border-radius: 8px; box-shadow: 0 8px 24px rgba(0,0,0,0.18); padding: 20px 24px; min-width: 320px; max-width: 420px; }
.data-page .modal-confirm-title { font-size: 16px; font-weight: 600; ... }
.data-page .modal-confirm-sub { font-size: 13px; ... }
.data-page .modal-confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }

/* 日期选择器 3 select 样式 */
.data-page .date-edit-row { display: flex; gap: 8px; margin: 12px 0 16px 0; }
.data-page .date-edit-select { flex: 1; padding: 6px 10px; ... }
```

---

## 3. 验收矩阵

| 类别 | 验证项 | 状态 | 备注 |
|------|--------|------|------|
| 自动化 | vitest 46/46 通过 | ✅ | PR6 改动无回归 |
| 自动化 | vite build 1.20s 通过 | ✅ | 无 React warning |
| 自动化 | 后端零改动（commit diff 验证） | ✅ | 不含 `fincontrol-backend/` 任何文件 |
| 视觉 | 11 项场景（按验收计划 §3.2） | ⏳ 待用户硬刷确认 | PR6a 修复点清晰，预计可快速通过 |
| 后端 | confirm API 接受 snapshotDate 字段 | ✅ | 已有 PR3+ 路径 |
| 后端 | DedupEngine 按 snapshotDate 维度 A 分组 | ✅ | 已有实现 |
| 累计算法 | 累计/持有零回归 | ✅ | 仅前端 snapshotDate 状态变化，不触发重算 |

---

## 4. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 与 PR4a DATA-012 的"header 副标题"重复 | modal-header 副标题保留，新交互是叠加在副标题上（点击副标题才触发），不破坏 PR4a 的布局 |
| editYear/Month/Day 初值乱（如 2026-13-32） | select 范围限定（年 2023-2027 / 月 1-12 / 日 1-31），前端不可能选到非法日期 |
| 3 select 的可访问性 | 保留原 keyboard 操作（Tab + Enter），点击 modal-date-clickable 后可 Tab 到 select |
| 多次编辑后确定入库是否正确 | 复用 confirm API（已用 snapshotDate），后端 dedup 与 setCurrent 都按此字段工作 |
| 离线 / 旧缓存 | 无 API 改动，无缓存问题（前端状态 + 后端已有 snapshotDate 字段） |

---

## 5. 关联决策 / commit

- [决策 13 snapshotDate 来源优先级 + dataTime 字段](../../phase-1/decisions/decision-13-snapshotdate-source-priority-and-datetime-field.md)
- [Decision 27 is_latest 双层语义 + 跨日期 is_current](../../phase-1/decisions/decision-27-is-latest-dual-layer.md)
- [PR3+ BUG-003 原始定义（被 PR4a DATA-012 删除）](../../../phase-1/checklists/2026-07-23_phase1b3-design-bug-list.md)
- [PR4a 验收报告 — DATA-012 实施说明（被 PR6a 部分恢复）](./2026-07-24_1b4-pr4a-acceptance-report.md)
- **commit `0f4e09e`** fix(1b.4-PR6a): 预览弹窗快照日期可点击修改（2-step UX）
- 联调记录（gitignored draft）：[test/1b/2026-07-24-1b4-pr6a-联调记录.md](../../../../test/1b/2026-07-24-1b4-pr6a-联调记录.md)

---

## 6. 用户 UX 流程

1. 用户进入预览弹窗，看到 "今日资产预览 · 2026-07-24 ✎"
2. 鼠标移到日期上（出现浅蓝高亮 + ✎ 变明显 + cursor: pointer）
3. 点击 → 弹出确认问询（"需要修改日期吗？当前入库 X。取消则返回预览；确认则进入日期选择"）
4. 取消 → 关闭弹窗，预览不变
5. 确认 → 进入日期选择（3 select：年 / 月 / 日）
6. 修改任一 select → setSnapshotDate 立即更新（modal-header 副标题也立即更新）
7. 确定 → 关闭日期选择，预览显示新日期
8. 之后点"确认入库" → POST /api/snapshot/confirm 用新日期入库

---

## 7. 1b.4 完整收官链路（共 18 个 commit）

```
d06daf2 PR1 P0 + 顺手 P2（5 bug）
fe6a0a2 PR2 P1 token 重构（6 bug）
a6329f0 PR2 envfix 端口 5174→5173 + jsdom env
0939928 PR2 hotfix .cat-detail 块恢复
de946c6 PR3 P1 行为+文案（3 bug）
2e41bf9 PR3+ confirm 4 bug
cccb132 PR3+ confirm 400 修复
1b98b8c PR3plus settings 全局配置表
b0dc7a1 PR3plus category conflict 优雅处理
9323240 PR4a P2 UX 小修 10 条 + 1⚪
8f4664c PR4 wrap-up v1：等高修复
b48fd0e PR4 wrap-up v2：严格等高
4e2d53b PR5 文档同步 + v1.0 冻结
e1fac02 PR5+ phase0 总表补登 30/31/32 行
3305348 PR5++ phase0 总表补登 30/31/32 完整本体
0f4e09e PR6a 预览弹窗快照日期可点击修改（2-step UX）  ← 当前
```

PR6a 是 1b.4 系列最后一个 PR：恢复 PR3+ BUG-003 丢失的功能（PR4a DATA-012 误删），同时升级为 2-step UX。

---

*完成时间：2026-07-24 23:14*
*验收人：待用户最终确认视觉验收 11 项场景*
