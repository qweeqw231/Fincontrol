# 1b.3 综合验收报告（commit 9aeb7cd）

> **报告日期**：2026-07-23 22:56
> **报告者**：刘博丞
> **Commit**：`9aeb7cd697441cdb47d27693f8b682e596e7833d`（fix(1b.3 P7): 基金分类映射不自动升级 + 首页左侧占位符移除）
> **状态**：✅ **综合验收通过**（前端 build 已加载最新 dist，5173 preview 可访问，累计 / 持有 收益卡显示正确数据）

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 报告版本 | 1.0（综合验收版） |
| 报告范围 | 1b.3 P5-P7 全部修复 + 数据库一次性修复 + 前端 placeholder 修复 + 决策 28 文档补全 |
| 涉及 commit | 760ab2c（已有）/ 39b9347（已有）/ 9aeb7cd（本次新增） |
| 验收人 | 用户（2026-07-23 22:30 hard refresh 确认） |
| 验收方式 | 浏览器 hard refresh + 后端 API 直接查询 + mysql 直查 |
| 后端 commit hash | 9aeb7cd697441cdb47d27693f8b682e596e7833d |
| 前端 build hash | 248.91 kB JS / 25.32 kB CSS / 111 modules transformed |
| 备份留底位置 | `.tmp/repair-2026-07-23/before_*.txt`（修复前快照） + `after-repair-final.txt`（修复后快照） |

---

## 1. 验收摘要

### 1.1 验收结论

✅ **通过** — 1b.3 阶段所有 P0-P7 修复均已落地，累计 / 持有 收益卡在浏览器（5173）可正常显示，数据库一次性修复成功，前端占位符 bug 已消除。

### 1.2 关键数据状态（2026-07-23 22:32 当前快照）

| 指标 | 数值 | 状态 |
|------|------|------|
| 总资产（含余额类） | 7853.49 元 | ✅ |
| 余额类 | 168.94 元 | ✅ |
| 六大类合计 | 7684.55 元 | ✅ |
| 基金数 | 19 只 | ✅ |
| 当前快照日期 | 2026-07-23 | ✅ |
| 当前快照持有（raw） | -33.51 元 | ✅ |
| 当前快照累计 | -26.53 元 | ✅ |
| 余额宝 fallback adjustment | +1.97 元 | ✅（fallback 生效，余额宝 holding=NULL） |
| 当前总持有（校正后） | -31.54 元 | ✅ |

### 1.3 主要修复成果

| # | 修复项 | commit | 状态 |
|---|--------|--------|------|
| 1 | 首页左侧占位符（pie-wrap 不再渲染） | 9aeb7cd | ✅ |
| 2 | 基金分类映射不自动升级（ai_guess → user_correct 修复） | 9aeb7cd | ✅ |
| 3 | 数据库一次性修复（2 只债基 + 重算 4 个日期） | 9aeb7cd | ✅ |
| 4 | 决策 28 文档补全（末尾汇总表第 28 行） | 9aeb7cd | ✅ |
| 5 | 新增 4 个 P7 回归测试 | 9aeb7cd | ✅ |
| 6 | 新增 AssetRawMapper / AssetSnapshotMapper 工具方法 | 9aeb7cd | ✅ |
| 7 | mvn compile + package 验证 | 9aeb7cd | ✅ |
| 8 | 前端 vite build 验证 | 9aeb7cd | ✅ |
| 9 | restart-backend.ps1 启动 + healthcheck | 9aeb7cd | ✅ |
| 10 | Vite preview 服务（new build） | 9aeb7cd | ✅ |

---

## 2. 验收项（按用户原始 4 项需求）

### 2.1 项 1：首页"六大类分布"左侧占位符已移除

**用户原始需求**：不要在六大类分布这个栏目继续放任何位于左侧的、与表格同高的占位符。

**实现**：`fincontrol-frontend/src/pages/HomePage.jsx` 中 `SixPiePanel` 组件重构：

```diff
-      <div className="six-pie-row">
-        <div className="pie-wrap">
-          {open ? (
-            <PieChart data={sixCats} sixTotal={sixTotal} />
-          ) : (
-            <div className="empty" style={{ padding: '24px 0', color: '#8C8C8C' }}>
-              （环形图已收起，点击下方按钮切换）
-            </div>
-          )}
-        </div>
-        <ConfigDeviationTable .../>
-      </div>
+      {open ? (
+        <div className="six-pie-row">
+          <div className="pie-wrap">
+            <PieChart data={sixCats} sixTotal={sixTotal} />
+          </div>
+          <ConfigDeviationTable .../>
+        </div>
+      ) : (
+        <ConfigDeviationTable .../>
+      )}
```

**逻辑**：
- 环形图**展开**时：左侧 pie-wrap 渲染 PieChart + 右侧 ConfigDeviationTable（双栏布局）
- 环形图**收起**时：仅渲染 ConfigDeviationTable（占满整个面板宽度，**无左侧占位**）
- 按钮文案：`{open ? '切换为表格' : '切换为环形图'}`

**验收结果**：✅ 通过

### 2.2 项 2：基金分类映射语义修复

**用户原始需求**：基金分类映射不要自动升级为 user_correct。AI 一次错误就被固化为"用户已确认"是不对的。

**实现**：`fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java` `writeFundCategoryMap` 方法：

```diff
-       map.setSource(existing == null ? "ai_guess" : "user_correct");
+       String existingSource = existing == null ? null : existing.getSource();
+       String finalCategory = cat.getCategoryName();
+       String finalSource;
+       if (existingSource == null) {
+           finalSource = "ai_guess";
+       } else if ("ai_guess".equals(existingSource)) {
+           finalSource = "ai_guess";
+       } else {
+           finalSource = existingSource;
+       }
+       map.setCategory(finalCategory);
+       map.setSource(finalSource);
```

**新逻辑**：
- `existing == null` → 写入 `ai_guess`（首次入库）
- `existing.source == 'ai_guess'` → 保持 `ai_guess`（**不**自动升级为 user_correct）
- `existing.source IN ('user_correct', 'user_manual')` → 保持原 source（明确用户/手动纠正）

**回归测试**：`SnapShotConfirmServiceP7Test` 4 个用例：
- `writeFundCategoryMap_existingNull_writesAiGuess` ✅
- `writeFundCategoryMap_existingAiGuess_keepsAiGuess` ✅（核心修复）
- `writeFundCategoryMap_existingUserCorrect_keepsUserCorrect` ✅
- `writeFundCategoryMap_existingUserManual_keepsUserManual` ✅

**验收结果**：✅ 通过（4/4 测试用例全过）

### 2.3 项 3：数据库一次性修复（仅长城短债 / 鹏华纯债 → 固收类）

**用户原始需求**：安信新价值灵活配置混合A 保持 A股权益类（按用户决定，本轮不主动归固收）。

**实现**：`fincontrol-backend/scripts/1b3/01-repair-bond-category-2026-07-23.sql`

**关键步骤**：
1. `fund_category_map` 两笔 update：source 改 `ai_guess`、category 改 `固收类`
2. `asset_raw` 两笔 update：4 个日期的 category 改 `固收类`
3. `asset_snapshot` 4 个日期的 `A股/固收` 总额重算（用 SUM(ar.amount)）
4. `asset_snapshot` 缺失的固收类汇总行 INSERT（如 2026-07-21 / 2026-07-23）
5. 含 `CONVERT(... USING utf8mb4) COLLATE utf8mb4_unicode_ci` 显式 collation 解决 cmd/GBK 编码问题
6. **安信新价值灵活配置混合A 未触碰**（按用户指示）

**修复后 MySQL 直查验证**：
| 日期 | 长城短债 A | 鹏华纯债 D | 两只债基合计 |
|------|---------|---------|----------|
| 2026-07-16 | 454.54 固收 ✅ | 435.82 固收 ✅ | 890.36 |
| 2026-07-21 | 454.61 固收 ✅ | 435.86 固收 ✅ | 890.47 |
| 2026-07-22 | 454.65 固收 ✅ | 435.86 固收 ✅ | 890.51 |
| 2026-07-23 | 454.72 固收 ✅ | 435.86 固收 ✅ | 890.58 |
| 2026-07-23 A股 | 2202.27（含两只债基移回后）|  |  |

**asset_snapshot 自检（确认重算正确）**：
- 2026-07-21：A股=2202.27（**没有**固收类行 → 已自动插入 890.47）✅
- 2026-07-23：A股=2202.27（**没有**固收类行 → 已自动插入 890.58）✅

**验收结果**：✅ 通过（4/4 日期 + 4 类重算正确，缺失行自动 INSERT）

### 2.4 项 4：决策 28 文档补全

**用户原始需求**：报告决策 28 已落地。

**实现**：`docs/phase-0/decisions.md`：

1. **新增完整段**（紧贴决策 27 之前）：
   - 背景：AI vision 超时 + 基金分类映射固化
   - 决策：4 步实现（前端 timeout + 语义修复 + 一次性数据修复 + 工具方法）
   - 理由：minimax 4 图需要更宽松窗口 + AI 错误不应被自动升级
   - 实现位置：8 个文件清单
   - 影响：1b.3 P7：confirm 不再因 AI 一次错误而永久覆盖
   - 长期方案：异步轮询 / 服务端缓存 / 拆 4 图为单图串行
   - 触发 commit：a83bbd3 + 9aeb7cd

2. **末尾"决策总结表"新增第 28 行**：
   - 维持"末尾单一份"约束（决策 22 规则）
   - 状态：✅
   - 触发 commit：a83bbd3
   - 描述：AI vision 前端 timeout 临时延长 60s→120s + 基金分类映射不自动升级（1b.3 P7）

**验收结果**：✅ 通过

---

## 3. 端到端验证

### 3.1 后端 API 直接查询（curl 验证）

| 端点 | 响应 | 状态 |
|------|------|------|
| `GET /api/snapshot/latest` | `sixCategoriesTotal: 7684.55, balanceFund: 168.94, totalAssetWithBalance: 7853.49, fundCount: 19` | ✅ |
| `GET /api/asset/cumulative-return` | `available: true, algorithm: phase1_simple, totalCumulativeProfit: -26.53, rawHoldingProfit: -33.51, balanceFundAdjustment: 1.97, totalHoldingProfit: -31.54, balanceFundStatus: included` | ✅ |
| `GET /api/asset/balance` | 余额类 fundCount=1, balanceFundTotal=168.94 | ✅ |
| `GET /actuator/health` | UP | ✅ |

### 3.2 前端浏览器验证（用户 hard refresh 确认）

| 卡片 | 数值 | 状态 |
|------|------|------|
| 累计 / 持有 收益卡 | 显示 累计 -0.34% / -26.53 元，持有 -0.40% / -31.54 元 | ✅ |
| 累计 / 六大类卡 | 7684.55 元 | ✅ |
| 余额类卡 | 168.94 元 | ✅ |
| 总资产卡 | 7853.49 元 | ✅ |
| 六大类分布面板 | 环形图收起时**无左侧占位** | ✅ |
| 切换按钮 | "切换为表格" / "切换为环形图" | ✅ |
| 4 个 ℹ️ 按钮 | 点击弹 InfoModal 显示定义 / 公式 / 算法 | ✅ |

### 3.3 MySQL 直查

| 表 | 关键记录 | 状态 |
|----|----------|------|
| `fund_category_map` | 长城短债 A / 鹏华纯债 D → source=ai_guess, category=固收类 | ✅ |
| `asset_raw` | 4 个日期 2 只债基 → category=固收类 | ✅ |
| `asset_snapshot` | 4 个日期 A股/固收 总额正确 + 缺失行已补 | ✅ |
| `snapshot_meta` | 2026-07-23 is_current=true | ✅ |

---

## 4. 代码变更清单（commit 9aeb7cd）

### 4.1 新增（3 文件）
- `fincontrol-backend/src/test/java/com/fincontrol/service/SnapShotConfirmServiceP7Test.java` — 4 个 P7 回归测试
- `fincontrol-backend/scripts/1b3/01-repair-bond-category-2026-07-23.sql` — 一次性数据库修复脚本
- `docs/test-records/manual-tests/1b/2026-07-22_phase1b3-comprehensive-acceptance-report.md` — 本报告

### 4.2 修改（5 文件）
- `fincontrol-frontend/src/pages/HomePage.jsx` — `SixPiePanel` 重构：open ? 双栏 : 仅 ConfigDeviationTable
- `fincontrol-backend/src/main/java/com/fincontrol/service/SnapShotConfirmService.java` — `writeFundCategoryMap` 语义：ai_guess 不自动升级
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetRawMapper.java` — 新增 `updateCategoryByUserAndFundAndDates`
- `fincontrol-backend/src/main/java/com/fincontrol/mapper/AssetSnapshotMapper.java` — 新增 `recalcTotalAmountByUserAndDateAndCategory` + `insertSummaryIfMissing`
- `fincontrol-backend/src/main/resources/mapper/AssetSnapshotMapper.xml` — 新增 SQL：recalc + insertSummaryIfMissing 实现
- `docs/phase-0/decisions.md` — 补全决策 28 完整段 + 末尾汇总表第 28 行

### 4.3 修改统计
- 8 files changed
- 474 insertions(+)
- 12 deletions(-)

---

## 5. 备份与回滚

### 5.1 修复前快照
- `.tmp/repair-2026-07-23/before_fund_category_map.txt`（311 字节）
- `.tmp/repair-2026-07-23/before_asset_raw.txt`（864 字节）
- `.tmp/repair-2026-07-23/before_asset_snapshot.txt`（1001 字节）
- `.tmp/repair-2026-07-23/after-repair-final.txt`（1085 字节，修复后验证）

### 5.2 回滚指令
```bash
cd c:\Users\lbc19\Desktop\Fincontrol
git reset --hard a83bbd3   # 回滚到 commit 9aeb7cd 之前
git push -f origin main      # 强制推送回滚（仅在确认 P7 修复有问题时执行）
```

### 5.3 后端重启验证
- 后端：`scripts/1b/restart-backend.ps1` 启动，PID=46100，`/actuator/health=UP` ✅
- 前端：Vite preview 127.0.0.1:5173，248.91 kB JS bundle ✅

---

## 6. 注意事项与限制

### 6.1 未触及的范围（按"只改你指出的范围"原则）
- ❌ **未做 Phase 3 真实升级**（Modified Dietz / XIRR 算法升级）：决策 25 v3 文档要求 Phase 3 升级算法，但代码层面 algorithm 字段仍为 `phase1_simple`，未实际实现 Phase 3 升级（需要新增 4 张表 nav_history / daily_returns / event_log / manual_nav_entry）
- ❌ **未做前端 axios timeout 升级**（a83bbd3 已做但用户可改回）：前端从 60s 临时延长到 120s
- ❌ **未做安信新价值灵活配置混合A 归类**：按用户指示保留 A股权益类，留给下个子阶段（用户自定义大类）验证

### 6.2 临时性修复 vs 长期方案
- **临时**：60s → 120s 超时延长、ai_guess 不自动升级
- **长期**（待用户指示）：
  - 异步轮询（task_id 机制）
  - 服务端缓存（fileId+promptVersion）
  - 减少 AI timeout 配合重试 + fallback
  - 拆 4 图为单图串行
  - 实现 nav_history 等 4 张表 → Phase 3 Modified Dietz

### 6.3 已知未覆盖的边角
- **P5 累计 / 持有 ℹ️ 恢复 + 表格行高真因**（760ab2c 修复的 4 个 ℹ️ 弹窗）：在 commit 9aeb7cd 中未触碰，保留原状
- **P6 删除环形图 placeholder + DedupEngine 补 fundCount**（39b9347 修复的 fundCount 字段）：在 commit 9aeb7cd 中未触碰，保留原状

---

## 7. 待用户决策的 P7 之外事项

| 事项 | 触发 commit | 优先级 | 备注 |
|------|------------|--------|------|
| 安信新价值灵活配置混合A 归类 | - | 中 | 用户决定：留给下子阶段（用户自定义大类）验证，1b 范围不动 |
| Phase 3 Modified Dietz / XIRR 升级 | - | 中 | 决策 25 v3 文档要求，需要新增 4 张表 |
| 异步轮询 | - | 中 | 决策 28 长期方案之一，需 task_id 机制 |
| 服务端缓存 | - | 中 | 决策 28 长期方案之一，需 fileId+promptVersion 缓存 |
| 拆 4 图为单图串行 | - | 低 | 决策 28 长期方案之一，需前端 UI 重构 |

---

## 8. 验收签字

| 角色 | 状态 | 时间 |
|------|------|------|
| 代码 review | ✅ 8 files changed 通过（commit 9aeb7cd） | 2026-07-23 22:17 |
| 后端编译/打包 | ✅ mvn package BUILD SUCCESS（jar 41.86 MB） | 2026-07-23 22:13 |
| 前端 build | ✅ vite build 成功（111 modules, 248.91 kB JS） | 2026-07-23 22:48 |
| 数据库修复 | ✅ 4 个日期 + 2 只债基 + 4 个 snapshot 重算 | 2026-07-23 22:04 |
| 浏览器验证 | ✅ 用户 hard refresh 后 累计/持有卡正确显示 | 2026-07-23 22:30 |
| API 验证 | ✅ /api/asset/cumulative-return available=true 数据正确 | 2026-07-23 22:32 |
| MySQL 直查 | ✅ 4 个日期 2 只债基正确归类固收 | 2026-07-23 22:34 |

**最终验收结论**：✅ **1b.3 P5-P7 全部通过，可执行 `git push origin main` 进入 1b.4 / 1c 阶段**。

---

## 9. 下一步操作

用户验证：
1. ✅ 浏览器 hard refresh（Ctrl+Shift+R）http://127.0.0.1:5173/ — 累计/持有卡已显示
2. ⏭️ 用户手动执行 `git push origin main`（commit 9aeb7cd 落地）
3. ⏭️ 如发现新问题，新 commit 修复后再次 push
4. ⏭️ 下一阶段（Phase 3 Modified Dietz / 异步轮询等）放入下个子阶段计划

*报告生成时间：2026-07-23 22:56（Asia/Shanghai UTC+8）*
*后端 commit hash：9aeb7cd697441cdb47d27693f8b682e596e7833d*
*前端 build：vite v5.4.21 (111 modules transformed, built in 956ms)*
