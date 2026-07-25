# 1b4pr7 验收计划（DATA-016：modal 清理 + setCurrent prompt UX）

> **会话时点**：2026-07-25 11:00
> **范围**：DataPage.jsx 前端单文件改动（Fix 1 + Fix 2 + Fix 3）
> **决策依据**：1b.4-pr7 work-plan（DATA-016）
> **执行后输出**：`docs/test-records/manual-tests/1b/2026-07-25_1b4-pr7-acceptance-report.md`

---

## 一、验收总览（V1-V7）

| 类别 | 编号范围 | 用例数 | 状态 |
|---|---|---|---|
| **主流程：数据写入 + current 解耦 UX** | V1-V2 | 2 | ⏳ |
| **编辑日期场景** | V3-V4 | 2 | ⏳ |
| **兜底路径保留** | V5 | 1 | ⏳ |
| **回归：连锁 bug 不复现** | V6-V7 | 2 | ⏳ |
| **合计** | **V1-V7** | **7** | ⏳ |

---

## 二、V1-V2 · 主流程验收（2 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| **V1** | Upload → parse → 点"入库" → 蓝色"改为当前" | preview modal 自动关闭 → "设为当前吗?" prompt 弹出（标题含 "✅ 已入库 N 只基金" + 日期）→ 点蓝色 → setCurrent 调用 → 主页面 ★ CURRENT 切到新日期 → prompt 关闭 | ☐ |
| **V2** | Upload → parse → 点"入库" → 白色"取消" | preview modal 自动关闭 → "设为当前吗?" prompt 弹出 → 点白色 → setCurrent **不调用** → 主页面 ★ CURRENT **不变** → prompt 关闭 | ☐ |

### V1 关键检查点

- [ ] preview modal 自动关闭（不再需要点 × 或 backdrop）
- [ ] "设为当前吗?" prompt 蓝色按钮 autofocus
- [ ] 主页 ★ CURRENT 切到新日期（用 curl `/api/snapshot/latest?userId=1` 验证）
- [ ] 主页 totalAssetWithBalance 与新日期数据匹配

### V2 关键检查点

- [ ] preview modal 自动关闭
- [ ] prompt 弹出，点白色后无 setCurrent API 请求
- [ ] 主页面 ★ CURRENT 保持原日期（记录下原 current 日期 → V2 后比较）
- [ ] Network tab 无 `POST /api/snapshot/set-current` 请求

---

## 三、V3-V4 · 编辑日期场景（2 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| **V3** | 上传 7/24 → 改 7/23（编辑日期）→ preview → 入库 → 蓝色"改为当前" | preview 标题显示 7/23 → 入库 → prompt 显示 **7/23**（不是 7/24）→ 蓝色 → setCurrent(7/23) → 主页 ★ CURRENT = 7/23 | ☐ |
| **V4** | 上传 7/24 → 改 7/23 → preview → 入库 → 白色"取消" | prompt 显示 **7/23** → 白色 → 不调 setCurrent → 主页 ★ CURRENT 保持原值 | ☐ |

### V3-V4 关键检查点

- [ ] preview modal 标题副标题"📊 今日资产预览 · YYYY-MM-DD" 是编辑后的日期
- [ ] prompt 副标题中的 `<strong>{date}</strong>` 也是编辑后的日期
- [ ] 编辑日期路径下，confirm 写 DB 的 snapshot_date 也是编辑后的日期（curl `/api/snapshot/history` 验证）

---

## 四、V5 · 兜底路径保留（1 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| **V5** | 主列表"设为当前"按钮仍可用 | 在快照管理 section，非当前 current 卡片右侧显示"设为当前"按钮（不变）→ 点击 → 调 setCurrent → ★ CURRENT 切到该日期 | ☐ |

### V5 关键检查点

- [ ] 主列表每张非 current 卡片右侧 "设为当前" 按钮存在且可点击
- [ ] 点击后 Network tab 看到 `POST /api/snapshot/set-current`
- [ ] 点击后 ★ CURRENT 标记切到该日期
- [ ] 与历史 PR3+ 行为一致（不应回退）

---

## 五、V6-V7 · 回归：连锁 bug 不复现（2 用例）

| ID | 场景 | 验收标准 | 通过 |
|---|---|---|---|
| **V6** | 修复前 Bug-D：第二次入库同 snapshot_date 不报 500 | V1-V2 完成后，再上传同 4 张图（snapshot_date = 已入库日期）→ parse 成功 → preview → 入库 → **成功**（不报 500）→ prompt 再次弹出（仍可走蓝色或白色） | ☐ |
| **V7** | 修复前 Bug-A：modal 关闭路径不动 current | preview modal 打开后，点 × 或 backdrop 关闭 modal → setCurrent **不调用** → 主页 ★ CURRENT 保持不变 | ☐ |

### V6 关键检查点

- [ ] 第二次入库不再 500（即使 DB 已存在该 snapshot_date 数据，前端仍可成功 confirm）
- [ ] prompt 弹窗正确显示"已入库 N 只基金"（fundCount 来自 parseInfo，不依赖服务端响应）
- [ ] 主页列表多出一条同 snapshot_date 的卡片（决策 27 is_latest 会切换）

### V7 关键检查点

- [ ] 点 × 或 backdrop 后 Network tab 无 setCurrent 请求
- [ ] 主页 ★ CURRENT 保持不变
- [ ] preview modal 内部 state（parsedSummary / categoryOverrides 等）被清空（再点"确认入库（请先预览）"会重新计算）

---

## 六、回归测试（前端已有用例）

| ID | 测试 | 通过 |
|---|---|---|
| R1 | `npm run test` DataPage.test.jsx 22 用例全绿 | ☐ |
| R2 | `npm run test` HomePage.test.jsx 3 用例全绿 | ☐ |
| R3 | `npm run test` 全部前端测试套件无回归 | ☐ |
| R4 | 后端 `mvn test` SnapshotQueryServiceTest + SnapShotConfirmServiceP7Test 全绿（仅前端改，不应影响，但保险） | ☐ |

---

## 七、验收执行流程

### 7.1 准备阶段

1. 重启后端（`scripts/1b/restart-backend.ps1`，决策 24 SOP）—— 仅前端改，保险跑一次
2. 启动前端（`.tmp/start-frontend.ps1`）
3. 验证后端健康：`curl http://localhost:8080/actuator/health` → HTTP 200
4. 浏览器打开 `http://localhost:5174/data`

### 7.2 执行阶段

1. **V1 路径**：upload 4 图 → parse → 入库 → 蓝色"改为当前"
2. **V2 路径**：upload 4 图 → parse → 入库 → 白色"取消"
3. **V3 路径**：upload 4 图（date=7/24）→ 编辑日期 7/23 → 入库 → 蓝色
4. **V4 路径**：upload 4 图（date=7/24）→ 编辑日期 7/23 → 入库 → 白色
5. **V5 路径**：在快照管理列表点"设为当前"
6. **V6 路径**：V1 后再 upload 4 图（同 snapshot_date）→ parse → 入库
7. **V7 路径**：preview 打开后点 × 或 backdrop

### 7.3 报告阶段

1. 填写本验收计划中所有 ☐ → ✅ 或 ❌
2. 输出 `docs/test-records/manual-tests/1b/2026-07-25_1b4-pr7-acceptance-report.md`
3. 过程报告：`test/1b/2026-07-25-1b4-pr7-联调记录.md`（过程中实时更新）
4. 全部通过后 git commit + push

---

## 八、验收签字

| 角色 | 姓名 | 日期 | 签字 |
|---|---|---|---|
| 开发 | Cline | 2026-07-25 | ☐ |
| 测试 | 用户 | 2026-07-25 | ☐ |
| 用户 | 用户 | 2026-07-25 | ☐ |

---

## 九、与决策 33 v3 的一致性确认

- ✅ DATA-016 (Fix 1)：preview modal 真正关闭 + 全 state 清理 → V1-V7
- ✅ DATA-016 (Fix 2)：confirm 不再自动 setCurrent → V1/V2/V3/V4/V6
- ✅ DATA-016 (Fix 3)：新 prompt modal 让用户主动选择 → V1/V2/V3/V4
- ✅ 主列表兜底按钮保留 → V5

---

## 十、待用户最终验收

- [ ] 全部 V1-V7 验证通过后，由用户在 final_report 或 GitHub issue 中确认
- [ ] 决策 33 标记为「v3 · 已实施」
- [ ] 本文件标记为「✅ 已完成」
- [ ] 联调记录最终版落盘
- [ ] 后续推 Fix 4（后端幂等）单独 commit