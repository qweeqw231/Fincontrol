# 1b.4 PR9 验收计划

**日期**: 2026-07-25 20:24  
**前置**: `2026-07-25_1b4-pr9-work-plan.md`  
**目标 PR**: 1b.4 PR9 — DataPage 类别修正交互 + 弹窗层级修复

---

## 验收范围

7 个 Bug 全部修复 + 不引入新问题。

## 测试方法

- **后端**：单元测试 + 集成验证（curl）
- **前端**：手动测试 + Playwright（如可用）+ Vite build 验证

## 测试前置条件

1. MySQL 80 服务已启动
2. Backend jar 已启动（`java -jar target/fincontrol-backend.jar`）
3. Vite dev server 已启动（`npm run dev`）或 production build 部署
4. 浏览器访问 `http://localhost:5173/`

## Bug 验收用例

### Bug 1: fund_name normalize（服务端 match API）

**前置数据**：
```sql
-- 在 fund_category_map 表里插入一条 user_correct 记录
INSERT INTO fund_category_map (user_id, fund_name, category, source, confirmed_at, last_seen_at)
VALUES (1, '安信新价值', '固收类', 'user_correct', NOW(), NOW());
```

#### TC-1.1: 全角空格匹配
- **步骤**：上传 0720 图片，AI 解析返回 fundName = `安信新价值`（含全角空格）
- **预期**：模态内 dropdown 自动显示"固收类"
- **验证点**：`/api/category-map/match?funds=安信新价值` 返回 `category: "固收类"`, `source: "user_correct"`

#### TC-1.2: 多空格匹配
- **步骤**：AI 解析返回 fundName = `安信  新价值`（含多个空格）
- **预期**：dropdown 显示"固收类"

#### TC-1.3: 完全不匹配
- **步骤**：AI 解析 fundName = `其他基金`
- **预期**：unmatchedFunds 包含该名字，dropdown 显示 AI 原猜

#### TC-1.4: 大小写不敏感
- **步骤**：fundName = `anXin Xin JiaZhi`（小写混合）
- **预期**：dropdown 显示"固收类"（大小写折叠）

### Bug 2: 内联单只确认 modal

#### TC-2.1: 修改 dropdown 后点 ✓ 弹出确认 modal
- **步骤**：在 DataPage 模态内：① dropdown 选 "商品类" → ② 点 "✓ 确认"
- **预期**：弹出"您确认修改【基金名】为【商品类】吗？" modal
- **验证点**：modal 在 z-index 1300（最上层），其他 modal 不可见

#### TC-2.2: 内联 modal 取消 → dropdown 保持 dirty 状态
- **步骤**：① 选 "商品类" → ② 点 "✓ 确认" → ③ 在内联 modal 点 "取消"
- **预期**：modal 关闭，dropdown 仍显示"商品类"，状态列不显示"✓ 已确认"

#### TC-2.3: 内联 modal 确认 → 写库 + 各种刷新
- **步骤**：① 选 "商品类" → ② 点 "✓ 确认" → ③ 在内联 modal 点 "确认修改"
- **预期**：
  - modal 关闭
  - 状态列显示"✓ 已确认"
  - dropdown 恢复"商品类"显示
  - **"各类小计"表格立即刷新**（商品类金额增加，源类别金额减少）
  - 异步触发首页 store 刷新

### Bug 3 + 5: 弹窗层级

#### TC-3.1: 脏 modal 在预览之上
- **步骤**：① 改 dropdown（不点 ✓）→ ② 点"确认入库"
- **预期**：脏 modal 弹出，预览 modal 被遮盖
- **验证点**：脏 modal z-index=1100 > 预览 modal z-index=1000

#### TC-3.2: 脏 modal "返回修改" 干净回到预览
- **步骤**：脏 modal 弹出 → 点 "返回修改"
- **预期**：
  - 脏 modal 关闭
  - 预览 modal 仍存在
  - 用户的 dirty 状态保留（dropdown 仍是脏值）
  - 用户可继续编辑

#### TC-3.3: 批量 modal 在脏 modal 之上
- **步骤**：① 进入 batchMode → ② 选几只基金 → ③ 点 "✓ 批量确认"
- **预期**：批量 modal 弹出，遮盖脏 modal（如果脏 modal 还在）
- **验证点**：批量 modal z-index=1200 > 脏 modal z-index=1100

#### TC-3.4: 内联单只确认 modal 在所有弹窗之上
- **步骤**：① 脏 modal 弹 → ② 关闭脏 → ③ 进入 batchMode → ④ 弹批量 → ⑤ 在预览内 dropdown 改 → ⑥ 点 ✓ 确认
- **预期**：内联 modal 弹出，遮盖批量 modal
- **验证点**：内联 modal z-index=1300 > 批量 z-index=1200

### Bug 4: 单只 ✓ 确认后首页刷新

#### TC-4.1: 单只确认后首页 stat-card 同步
- **步骤**：① 在 DataPage 单只确认某 fund 分类 → ② 立即切到首页
- **预期**：首页 "六大类总值" / "余额类" / FundDetailTable 反映新分类
- **验证点**：`/api/snapshot/latest` 返回的 categories 已包含新分类

#### TC-4.2: 批量确认后首页刷新
- **步骤**：① 在 DataPage batch 模式确认几只 fund → ② doConfirm 后切到首页
- **预期**：首页全部刷新

#### TC-4.3: 单只确认失败不刷首页
- **步骤**：后端故意 500 → 单只确认失败
- **预期**：
  - 错误 toast 显示
  - dropdown 保持 dirty 状态
  - 首页不刷新（因为写库失败）

### Bug 5: 批量 modal 层叠（与 TC-3.3 重复）

### Bug 6: 各类小计实时刷新

#### TC-6.1: 改 dropdown 后立即刷新（不点 ✓）
- **步骤**：① dropdown 选 "商品类" → ② 不点 ✓
- **预期**：
  - "各类小计"表格立即更新（商品类金额+该 fund 金额，源类别金额减少）
  - 状态列保持"🤖 ai_guess"
  - dropdown 仍显示"商品类"

#### TC-6.2: 不入库也能刷新（前端主路径）
- **步骤**：① 改 dropdown → ② 看 "各类小计" 表
- **预期**：立即更新，不需要"确认入库"操作

#### TC-6.3: 回滚 dirty 后立即恢复
- **步骤**：① 改 dropdown 到 "商品类" → ② 改回原值
- **预期**：表格恢复到原状

### Bug 7: success banner 宽度

#### TC-7.1: 入库成功后 banner 宽度 ≤ 30vw
- **步骤**：完整入库流程（确认 + set current）
- **预期**：
  - 第一个 banner `✓ 来自 2026-07-21 的 N 只基金入库成功！` 宽度 ≤ 30vw
  - 第二个 banner `✓ 首页展示日期 2026-07-21 确认成功！返回首页即可查看当日基金明细` 宽度 ≤ 30vw
  - 两个 banner 都不挡侧边栏"首页"项

#### TC-7.2: banner 文本过长省略号
- **步骤**：构造超长文本（mock）
- **预期**：超出宽度自动省略（text-overflow: ellipsis）

## 测试报告落盘

测试过程中产生的报告落盘 `test/1b/`：

| 步骤 | 报告文件 | 内容 |
|---|---|---|
| Step 1 | `test/1b/2026-07-25-1b4-pr9-step1-后端normalize.md` | TC-1.1 ~ TC-1.4 单元 + 集成测试 |
| Step 2 | `test/1b/2026-07-25-1b4-pr9-step2-前端modal联调.md` | TC-2 ~ TC-5 联调测试 |
| Step 3 | `test/1b/2026-07-25-1b4-pr9-step3-7bug回归测试.md` | TC-6 ~ TC-7 回归测试 |
| Step 4 | `test/1b/2026-07-25-1b4-pr9-集成验证报告.md` | 最终汇总 |

## 通过标准

- [ ] 7 个 Bug 验收用例全部通过
- [ ] 后端 `mvn test` 全绿
- [ ] 前端 `npm run build` 无错误
- [ ] 不引入新的 console error / warning
- [ ] 不破坏现有功能（PR8 验收通过的 19 项保留 OK）

## 决策记录

- TC-1.1 通过 = Bug 1 修复完整
- TC-2.3 通过 = Bug 2 修复完整
- TC-3.1 ~ TC-3.4 通过 = Bug 3 + 5 修复完整
- TC-4.1 通过 = Bug 4 修复完整
- TC-6.1 通过 = Bug 6 修复完整
- TC-7.1 通过 = Bug 7 修复完整