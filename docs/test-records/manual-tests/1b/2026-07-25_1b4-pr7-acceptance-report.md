# 1b4pr7 验收报告（DATA-016 · 含 Fix 4+5）

> **会话时点**：2026-07-25 11:00（v1）/ 11:50（v2：Fix 5 增量）
> **验收者**：用户（亲自跑通）
> **范围**：DataPage.jsx 前端单文件改动（Fix 1 + Fix 2 + Fix 3）+ 后端 SnapShotConfirmService（Fix 4 + Fix 5）
> **结果**：⏳ 待用户执行 V1-V8 手动验收后填 ✅
> **后端状态**：PID=5384 · /actuator/health=UP · jar 41.88 MB
> **前端状态**：Vite dev server ready in 570ms · HTTP 200

---

## 一、验收总览（V1-V8 · 8 用例）

| 类别 | 编号 | 用例数 | 状态 |
|---|---|---|---|
| 主流程：数据写入 + current 解耦 UX | V1-V2 | 2 | ⏳ |
| 编辑日期场景 | V3-V4 | 2 | ⏳ |
| 兜底路径保留 | V5 | 1 | ⏳ |
| 回归：连锁 bug 不复现 | V6-V7 | 2 | ⏳ |
| **Fix 5 加测：后端三表幂等** | **V8** | **1** | ⏳ |
| **合计** | **V1-V8** | **8** | ⏳ |

---

## 二、自动化验证（2 用例）

| ID | 测试 | 实际 | 通过 |
|---|---|---|---|
| **A1** | `npm --prefix C:\Users\lbc19\Desktop\Fincontrol\fincontrol-frontend run build` | `✓ 114 modules transformed.` / `✓ built in 1.73s` | ✅ |
| **A2** | `mvn -f C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend\pom.xml -o test -Dtest=SnapShotConfirmServiceP7Test` | `Tests run: 9, Failures: 0, Errors: 0` / `BUILD SUCCESS`（P7-R1~R4 + B9-B13） | ✅ |
| **A3** | `scripts/1b/restart-backend.ps1`（决策 24） | `BUILD SUCCESS` / `jar 41.88 MB` / `PID=5384` / `/actuator/health=UP` | ✅ |

---

## 三、V1-V8 手动验收结果

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
| 4. 点白色"取消" | setCurrent **不调用** + 主页 ★ CURRENT 保持原值 | | ☐ |

### V5：主列表"设为当前"按钮兜底

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. 找一张非 current 卡片 | 右侧"设为当前"按钮存在 | | ☐ |
| 2. 点"设为当前" | setCurrent 调用 + ★ CURRENT 切到该日期 | | ☐ |

### V6：第二次入库同 snapshot_date 不报 500（**Fix 4 + Fix 5 重点**）

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. V1/V2/V3/V4 任一完成后 | 数据库已存某 snapshot_date | | ☐ |
| 2. 重新上传 4 张图（同日期） | parse 成功 + preview 出现 | | ☐ |
| 3. 点"确认入库" | **成功**（不报 500） + prompt 再次弹出 | | ☐ |
| 4. 后端 stdout.log | `1b.3.2 confirm done: raw=N snapshot=N map=N meta=N`（无 SQLIntegrityConstraintViolationException） | | ☐ |

### V7：preview modal × 关闭路径不动 current

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. parse 成功 + preview 打开 | 主页 ★ CURRENT = X（记录） | | ☐ |
| 2. 点 modal × 或 backdrop | modal 关闭 + setCurrent **不调用** | | ☐ |
| 3. Network tab 验证 | 无 `POST /api/snapshot/set-current` 请求 | | ☐ |
| 4. 主页 ★ CURRENT | 保持 X 不变 | | ☐ |

### V8：Fix 5 端到端（**preview 不闪退 + 二次入库 + 翻旧行**）

| 步骤 | 期望 | 实际 | 通过 |
|---|---|---|---|
| 1. upload 4 张图（任意日期）→ 入库 | preview 不闪退（Fix 1） + 弹 prompt（Fix 3） | | ☐ |
| 2. 选蓝色/白色 | prompt 关 + 主页 OK | | ☐ |
| 3. 再 upload 4 张图（同日期）→ 入库 | **不报 500**（Fix 5） + 弹 prompt | | ☐ |
| 4. DB 验证：asset_snapshot | 仅显示新批次行（Fix 5 DELETE 清扫生效） | | ☐ |
| 5. DB 验证：asset_raw | 仅显示新批次行 | | ☐ |
| 6. DB 验证：fund_category_map | 已确认 fund 的 source='ai_guess' 不被自动升级 | | ☐ |

---

## 四、修改文件清单

### 4.1 修改
| 文件 | 改动行数 | commit | 说明 |
|---|---|---|---|
| `fincontrol-frontend/src/pages/DataPage.jsx` | +75 / -21 | `915f1ec` | Fix 1+2+3 |
| `fincontrol-backend/.../AssetRawMapper.java` | +38 | `5055015` | Fix 4 upsertByFundName |
| `fincontrol-backend/.../AssetSnapshotMapper.java` | +22 | `<TBD>` | Fix 5 deleteByUserAndDateAndCategory |
| `fincontrol-backend/.../SnapShotConfirmService.java` | +40 / -1 | `5055015` + `<TBD>` | Fix 4+5：writeAssetRaw 改 upsert + writeAssetSnapshot/writeFundCategoryMap 头 DELETE |
| `fincontrol-backend/.../SnapShotConfirmServiceP7Test.java` | +115 | `5055015` + `<TBD>` | B9-B13 测试 |

### 4.2 新增
| 文件 | 说明 |
|---|---|
| `docs/phase-1/work-plans/1b/2026-07-25_1b4-pr7-work-plan.md` | v1 + v2 Fix 5 增量 |
| `docs/test-records/.../2026-07-25_1b4-pr7-acceptance-plan.md` | v1 + V8 增量 |
| `docs/test-records/.../2026-07-25_1b4-pr7-acceptance-report.md` | 本文件 |
| `test/1b/2026-07-25-1b4-pr7-联调记录.md` | 联调过程 |
| `test/1b/2026-07-25-1b4-pr7-测试报告.md` | 自动化验证 |

---

## 五、commit 历史

| Commit | 时刻 | 内容 |
|---|---|---|
| `915f1ec` | 11:06 | fix(1b.4-pr7): DataPage modal state cleanup + setCurrent prompt UX |
| `5055015` | 11:27 | fix(1b.4-pr7): SnapShotConfirmService asset_raw idempotent upsert (Fix 4) |
| `<TBD>` | 11:55 | fix(1b.4-pr7): SnapShotConfirmService asset_snapshot idempotent DELETE (Fix 5) |

---

## 六、决策一致性确认

- ✅ DATA-016 (Fix 1)：preview modal 真正关闭 + 全 state 清理 → V1-V8
- ✅ DATA-016 (Fix 2)：confirm 不再自动 setCurrent → V1/V2/V3/V4/V6/V8
- ✅ DATA-016 (Fix 3)：新 prompt modal 让用户主动选择 → V1/V2/V3/V4/V8
- ✅ 主列表兜底按钮保留 → V5
- ✅ Fix 4 (asset_raw 幂等 upsert) → V6
- ✅ Fix 5 (asset_snapshot + fund_category_map DELETE 清扫) → V6/V8

---

## 七、签字

| 角色 | 姓名 | 日期 | 状态 |
|---|---|---|---|
| 开发 | Cline | 2026-07-25 | ✅（代码 + 构建 + 后端 + 文档 + 测试） |
| 测试 | 用户 | 2026-07-25 | ⏳（V1-V8 待跑） |
| 验收 | 用户 | 2026-07-25 | ⏳ |

---

## 八、待用户最终验收

- [ ] 全部 V1-V8 验证通过后，由用户在 GitHub issue 或本文件确认
- [ ] 决策 33 标记为「v3 · 已实施」
- [ ] work-plan `2026-07-25_1b4-pr7-work-plan.md` 标记为「✅ 已完成」
- [ ] 联调记录最终版落盘