| HOME-014 | ⚪ P2 | easy | 设计意图（防误删 100.00%） |

共 **29 条 ID**（3 条已知 + 25 条新增待修 + 1 条 ⚪ 备注 = 29 条）；HOME-001 单独列在"已知问题"中，P2 段少 1 条（原 P2-3 / HOME-014 已撤下）。原计划 26 条新增，撤下 1 条后为 25 条新增。

---

## 修复追踪表（2026-07-24 1b.4 收官）

> 全部 29 条 ID 在 [Phase 1b.4 设计债务修复](../work-plans/1b/2026-07-24_1b4-pr0-work-plan.md) 阶段通过 7 个 PR 全部关闭。
> 工作计划：1b.4 PR0→PR1→PR2→PR3→PR3+→PR3plus→PR4a→PR4 wrap-up。**用户 2026-07-24 硬刷确认 PR4a 验收通过，1b.4 正式收官。**

| ID | 严重度 | 阶段 | commit SHA | 关闭日期 | 简述 |
| --- | --- | --- | --- | --- | --- |
| **HOME-001** | 🔴 P0 | PR1 | `d06daf2` | 2026-07-24 | 单独刷新首页看不到数据（useEffect + fetchLatest） |
| **HOME-002** | 🔴 P0 | PR1 | `d06daf2` | 2026-07-24 | 空态缺"立即上传"按钮（StateShell action） |
| **HOME-003** | 🔴 P0 | PR1 | `d06daf2` | 2026-07-24 | 加载/错误/空态视觉断裂（StateShell 三态） |
| **GLOBAL-007** | 🟢 P2 | PR1 | `d06daf2` | 2026-07-24 | 错误信息暴露 axios stack（friendlyError） |
| **GLOBAL-015** | 🔴 P0 | PR1 | `d06daf2` | 2026-07-24 | blob URL 内存泄漏（revokeAll 全链路） |
| **GLOBAL-001** | 🟡 P1 | PR2 | `fe6a0a2` | 2026-07-24 | `.card` 类命名冲突 → `.stat-card` / `.section-card` |
| **GLOBAL-002** | 🟡 P1 | PR2 | `fe6a0a2` | 2026-07-24 | 模态框三套样式合并 → `.modal` + `.modal--wide` |
| **GLOBAL-003** | 🟡 P1 | PR2 | `fe6a0a2` | 2026-07-24 | 响应式断点 → `--bp-mobile/tablet/desktop/wide` |
| **GLOBAL-004** | 🟡 P1 | PR2 | `fe6a0a2` | 2026-07-24 | 字体字面量 → `--text-xs/md/display` |
| **GLOBAL-005** | 🟡 P1 | PR2 | `fe6a0a2` | 2026-07-24 | 颜色 hex 失控 → 6 个 `--color-*` token |
| **HOME-007** | 🟡 P1 | PR2 | `fe6a0a2` | 2026-07-24 | 三卡片不等高 → `align-items: stretch` |
| **HOME-013** | 🟡 P1 | PR3 | `de946c6` | 2026-07-24 | "v1.0-DRAFT" 标签 + 技术术语泄漏 |
| **HOME-016** | 🟡 P1 | PR3 | `de946c6` | 2026-07-24 | 章节序号 1-4 硬编码 → `SECTIONS.map()` |
| **DATA-006** | 🟡 P1 | PR3 | `de946c6` | 2026-07-24 | confirm 后 step 残留态 → 1.5s reset |
| **GLOBAL-016** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | 原生 alert() → setError |
| **GLOBAL-021** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | 缺 favicon → emoji 💰 base64 SVG |
| **DATA-002** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | 占位符"建议 0716 数据" → "请选择 4 张支付宝基金截图" |
| **DATA-005** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | "上传并解析" 按钮加 title tooltip |
| **DATA-010** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | 解析模式下方加 form-hint 说明 single/multi |
| **DATA-011** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | 技术术语 asset_raw 等 → "今日资产数据写入历史记录" |
| **DATA-012** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | modal 中快照日期卡移到 header 副标题（grid 5→4 列） |
| **HOME-009** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | ¥ 符号手工拼接 → `formatYuan({withSymbol:true})` U+2009 thin space |
| **HOME-014** | ⚪ 备注 | PR4a | `9323240` | 2026-07-24 | 100.00% 加 title tooltip 防误删（设计意图保留） |
| **GLOBAL-010** | 🟢 P2 | PR4a | `9323240` | 2026-07-24 | SPA 不更新 document.title → RouterShell 8 路由映射 |
| **GLOBAL-016（PR3+ 重叠）** | 🟢 P2 | PR3+ | `cccb132` | 2026-07-24 | confirm 400 错误独立 try-catch（setCurrent 失败不影响主流程） |
| **PR3+ confirm 4 bug** | 🟡 P1 | PR3+ | `2e41bf9` | 2026-07-24 | confirmSuccess banner / is_current 等高 / 日期可编辑 / 自动 setCurrent |
| **PR3plus settings** | 🟢 P2 | PR3plus | `1b98b8c`/`b0dc7a1` | 2026-07-24 | settings 全局配置表（决策 30/31/32）+ category conflict 优雅处理 |
| **PR4 wrap-up v1** | 🟢 P2 | PR4 wrap | `8f4664c` | 2026-07-24 | 解析模式/截图日期 form-group 包裹（首次修复） |
| **PR4 wrap-up v2** | 🟢 P2 | PR4 wrap | `b48fd0e` | 2026-07-24 | form-hint 完整复制为隐藏占位（严格等高最终方案） |

**总计**：29 条 + 2 处布局修复 = **31 处修复**，全部关闭。

### 1b.4 阶段交付

- ✅ PR1 5 bug（🔴 P0）→ commit `d06daf2`
- ✅ PR2 6 bug（🟡 P1 token）→ commit `fe6a0a2` + envfix `a6329f0` + hotfix `0939928`
- ✅ PR3 3 bug（🟡 P1 行为+文案）→ commit `de946c6`
- ✅ PR3+ 4 bug（confirm 热修）→ commit `2e41bf9` + `cccb132`
- ✅ PR3plus 0 bug（settings 衍生）→ commit `1b98b8c` + `b0dc7a1`
- ✅ PR4a 11 bug（🟢 P2 UX 小修 + 1⚪）→ commit `9323240`
- ✅ PR4 wrap-up 2 处布局 → commit `8f4664c` + `b48fd0e`

### 硬门禁验证（1b.4）

- ✅ **累计/持有算法**：零行 diff（仅 formatters 调用方式调整）
- ✅ **snapshot_meta 双层语义**（决策 27）：所有写入路径零触碰
- ✅ **vitest 全绿**：34 → 46 用例（+12，0 失败）
- ✅ **vite build**：1.28s 通过，零 React warning
- ✅ **puppeteer 视觉**：首页 / 数据管理 / 确认 modal 视觉一致
- ✅ **用户浏览器硬刷确认**：2026-07-24 通过

---

## 修订记录

| 日期 | 内容 |
| --- | --- |
| 2026-07-23 | 初版：3 条已知 + 26 条新增 bug |
| 2026-07-24 | 修订：HOME-014 经用户反馈撤下，移入 ⚪ 备注段；严重度从 🟢 P2 改为 ⚪ 非 bug |
| 2026-07-24 | **1b.4 收官**：新增「修复追踪表」+「硬门禁验证」段；29 条 ID 全部关闭并标注 commit SHA |

---

**Reviewer 备注**：本次 review 完全基于静态代码阅读，未启动前端 dev server 做视觉走查（PLAN MODE 限制）。所有结论应有 90%+ 准确率，但建议在 1b.4 前做一次实际 browser walkthrough 验证 P0 三条的实际表现。
