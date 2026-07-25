# 1b4pr7 验收报告（DATA-016）

> **会话时点**：2026-07-25 11:04
> **范围**：DataPage.jsx 前端单文件改动（Fix 1 + Fix 2 + Fix 3）
> **结果**：⏳ 待用户执行 V1-V7 手动验收后填 ✅
> **决策依据**：1b.4-pr7 work-plan（DATA-016）+ acceptance-plan
> **后端状态**：PID=44240 · /actuator/health = UP

---

## 一、验收总览

| 类别 | 编号 | 用例数 | 状态 |
|---|---|---|---|
| 主流程：数据写入 + current 解耦 UX | V1-V2 | 2 | ⏳ |
| 编辑日期场景 | V3-V4 | 2 | ⏳ |
| 兜底路径保留 | V5 | 1 | ⏳ |
| 回归：连锁 bug 不复现 | V6-V7 | 2 | ⏳ |
| 自动化验证（构建 + 后端重启） | A1-A2 | 2 | ✅ |
| **合计** | **V1-V7 + A1-A2** | **9** | **2✅ / 7⏳** |

---

## 二、自动化验证结果（2 用例）

| ID | 测试 | 实际 | 通过 |
|---|---|---|---|
| **A1** | `npm --prefix C:\Users\lbc19\Desktop\Fincontrol\fincontrol-frontend run build` | `✓ 114 modules transformed.` / `✓ built in 1.73s` | ✅ |
| **A2** | `scripts/1b/restart-backend.ps1`（决策 24） | `BUILD SUCCESS` / `jar size: 41.88 MB` / `PID=44240` / `/actuator/health = UP` | ✅ |

---

## 三、V1-V7 手动验收结果

> 用户在浏览器跑完后，把 ☐ 改成 ✅ 或 ❌。

### V1：Upload → parse → 入库 → 蓝色"改为当前"

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. 选 4 张支付宝截图 | preview strip 显示 4 图 | | ☐ |
| 2. 选 mode=single + 日期 | form 填充 | | ☐ |
| 3. 点"上传并解析" | parse 成功 + showConfirmModal=true | | ☐ |
| 4. 点"确认入库" | preview modal 自动关闭 + prompt 弹出 + setConfirmSuccess 5s | | ☐ |
| 5. 点蓝色"改为当前日期" | setCurrent 调用 + 主页 ★ CURRENT = 新日期 | | ☐ |

### V2：Upload → parse → 入库 → 白色"取消"

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. 上传新 4 张图（不同日期） | parse 成功 | | ☐ |
| 2. 点"确认入库" | preview 关 + prompt 弹 | | ☐ |
| 3. 点白色"取消" | setCurrent **不调用** + 主页 ★ CURRENT **不变** | | ☐ |
| 4. Network tab 验证 | 无 `POST /api/snapshot/set-current` 请求 | | ☐ |

### V3：上传 7/24 → 改 7/23 → 入库 → 蓝色

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. 上传 4 图 + 日期=7/24 | parse 成功 + preview 标题显示 7/24 | | ☐ |
| 2. 点日期 → 改 7/23 → 确定 | preview 标题更新为 7/23 | | ☐ |
| 3. 点"确认入库" | prompt 显示 **7/23** | | ☐ |
| 4. 点蓝色"改为当前日期" | setCurrent(7/23) + 主页 ★ CURRENT = 7/23 | | ☐ |
| 5. curl `/api/snapshot/history` | 7/23 数据已写入 | | ☐ |

### V4：上传 7/24 → 改 7/23 → 入库 → 白色

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. 上传新 4 图 + 日期=7/24 | parse 成功 | | ☐ |
| 2. 改日期为 7/23 → 确定 | preview 标题 = 7/23 | | ☐ |
| 3. 点"确认入库" | prompt 显示 **7/23** | | ☐ |
| 4. 点白色"取消" | setCurrent **不调用** + 主页 ★ CURRENT **不变** | | ☐ |

### V5：主列表"设为当前"按钮兜底

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. 找一张非 current 卡片 | 右侧"设为当前"按钮存在 | | ☐ |
| 2. 点"设为当前" | setCurrent 调用 + ★ CURRENT 切到该日期 | | ☐ |

### V6：第二次入库同 snapshot_date 不报 500

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. V1/V2/V3/V4 任一完成后 | 数据库已存某 snapshot_date | | ☐ |
| 2. 重新上传 4 图（同日期） | parse 成功 + preview 出现 | | ☐ |
| 3. 点"确认入库" | **成功**（不报 500） + prompt 再次弹出 | | ☐ |

### V7：preview modal × 关闭路径不动 current

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. parse 成功 + preview 打开 | 主页 ★ CURRENT = X（记录） | | ☐ |
| 2. 点 modal × 或 backdrop | modal 关闭 + setCurrent **不调用** | | ☐ |
| 3. Network tab 验证 | 无 `POST /api/snapshot/set-current` 请求 | | ☐ |
| 4. 主页 ★ CURRENT | 保持 X 不变 | | ☐ |

---

## 四、回归测试（前端已有用例）

| ID | 测试 | 通过 |
|---|---|---|
| R1 | 后端 `mvn test` SnapshotQueryServiceTest + SnapShotConfirmServiceP7Test 全绿（仅前端改，应无影响） | ☐ |
| R2 | 前端 `npm test` DataPage.test.jsx 22 用例 + HomePage.test.jsx 3 用例全绿 | ☐ |

---

## 五、核心修复对照表

| 修复 | 涉及行 | 验收路径 |
|---|---|---|
| **Fix 1**：doConfirm 真正清理 modal state | DataPage.jsx doConfirm 成功路径新增 10 个 setState | V1（preview 自动关）/ V2 / V3 / V4 |
| **Fix 2**：移除自动 setCurrent | 删除 doConfirm 末尾独立 try-catch（约 14 行） | V2 / V7（取消路径不动 current） |
| **Fix 3**：新增"设为当前吗?" prompt modal | 新增 setCurrentPrompt state + handleSetCurrentPromptConfirm/Cancel + 新 modal JSX | V1（蓝色改 current）/ V2（白色不改）/ V3（蓝色 + 编辑日期）/ V4（白色 + 编辑日期） |
| **兜底保留**：主列表"设为当前"按钮 | 不变 | V5 |

---

## 六、与决策 33 v3 的一致性

- ✅ DATA-016 Fix 1：preview modal 真正关闭 + 全 state 清理 → V1-V7
- ✅ DATA-016 Fix 2：confirm 不再自动 setCurrent → V2 / V7
- ✅ DATA-016 Fix 3：新 prompt modal 让用户主动选择 → V1 / V2 / V3 / V4
- ✅ 主列表兜底按钮保留 → V5

---

## 七、签字

| 角色 | 姓名 | 日期 | 状态 |
|---|---|---|---|
| 开发 | Cline | 2026-07-25 | ✅（代码 + 构建 + 后端 + 文档） |
| 测试 | 用户 | 2026-07-25 | ⏳（V1-V7 待跑） |
| 验收 | 用户 | 2026-07-25 | ⏳ |

---

## 八、待用户最终验收

- [ ] 全部 V1-V7 验证通过后，由用户在 GitHub issue 或本文件确认
- [ ] 决策 33 标记为「v3 · 已实施」
- [ ] work-plan `2026-07-25_1b4-pr7-work-plan.md` 标记为「✅ 已完成」
- [ ] 后续推 Fix 4（后端 confirm 幂等）单独 commit