# FinControl 测试记录目录

> 本目录存放 FinControl 项目的测试相关记录。
> **重要划分**：
> - `manual-tests/`     ← 人手写：验收计划 / 验收报告 / 手测结果
> - `automated-smoke/`  ← 脚本写：自动化 smoke 输出（log + API 响应）
> 严格区分，不互相混淆。

---

## 目录结构

```
docs/
├── phase-1/
│   ├── work-plans/                                    ← 子阶段实施工作计划（1a / 1b 分目录）
│   │   ├── README.md                                  ← 工作计划规范
│   │   ├── 1a/                                        ← Phase 1a 各 PR 计划
│   │   └── 1b/                                        ← Phase 1b 各 PR 计划
│   └── checklists/
│       ├── phase-1a.md                                ← Phase 1a 总体进度清单
│       └── phase-1b.md                                ← Phase 1b 总体进度清单
└── test-records/
    ├── README.md                                      ← 本说明 + 手动测试记录索引
    ├── manual-tests/                                  ← ★ 人手写（验收计划 / 报告 / 手测记录）
    │   ├── 0/                                         ← Phase 0 验收
    │   ├── 1a/                                        ← Phase 1a
    │   └── 1b/                                        ← Phase 1b
    ├── automated-smoke/                               ← ★ 脚本写（自动化 smoke 输出）
    └── screenshots/                                   ← UI 截图（asset，不是 log）
```

> 注意：实时验收清单已移至 `docs/phase-1/checklists/` 下，与 phase-1 交付物同目录。

---

## 文件命名规范

| 文件类型 | 命名规则 | 示例 |
|---------|---------|------|
| 工作计划 | `<日期>_<phase>-work-plan.md`（在 `phase-1/work-plans/` 下） | `2026-07-16_phase1a4-work-plan.md` |
| 验收清单 | `phase-<n>-<a>.md`（在 phase-N/checklists/ 下）| `phase-1/checklists/phase-1a.md` |
| 验收计划 | `<日期>_<phase>-acceptance-plan.md`（在 `manual-tests/` 下） | `2026-07-16_phase1a4-acceptance-plan.md` |
| 验收报告 | `<日期>_<scope>-acceptance.md`（在 `manual-tests/` 下） | `2026-07-16_phase1a3-supplemental-acceptance.md` |
| 手动测试 | `<日期>_<测试名>.md`（在 `manual-tests/` 下） | `2026-07-09_smoke-test.md` |
| **自动化 smoke log** | `<日期>_<phase>-smoke.log`（在 `automated-smoke/<phase>/` 下）| `2026-07-17_phase1a7-smoke.log` |
| **自动化 API 输出** | `api-test-output/<日期>_<api-name>.json`（在 `automated-smoke/<phase>/` 下，gitignored） | `api-test-output/2026-07-09_screenshot_parse.json` |

---

## 手动测试记录索引（2026-09-30 建档）

> 全部历史记录**保留原地**，本索引仅用于导航（不改名、不合并、不归档）。同一专题通常「计划 + 报告」成对（先 acceptance-plan 后 acceptance-report）。
>
> 2026-09-30：删除 1 份重复文件 `2026-07-22_phase1b2-completion-report.md`（与 `2026-07-22_phase1b2-acceptance-report.md` MD5 完全相同的副本，内容未丢失）。

### Phase 0（`manual-tests/0/`，1 份）

| 日期 | 文件 | 类型 |
|---|---|---|
| 2026-07-09 | `0/2026-07-09_phase0-acceptance.md` | 验收 |

### Phase 1a（`manual-tests/1a/`，28 份）

| 日期 | 文件 | 类型 |
|---|---|---|
| 2026-07-20 | `1a/2026-07-20_phase1a10-cache-gray-test-acceptance.md` | 验收（缓存灰测）|
| 2026-07-19 | `1a/2026-07-19_phase1a10-real-e2e.md` | 报告（真实 E2E）|
| 2026-07-19 | `1a/2026-07-19_phase1a10-acceptance-plan.md` | 计划 |
| 2026-07-19 | `1a/2026-07-19_phase1a9-real-e2e.md` | 报告（真实 E2E）|
| 2026-07-19 | `1a/2026-07-19_phase1a-acceptance.md` | 验收（Phase 1a 整体）|
| 2026-07-18 | `1a/2026-07-18_prompt-v2.6-upgrade-tutorial.md` | 教程（1a.9 prompt）|
| 2026-07-18 | `1a/2026-07-18_prompt-v2.3-upgrade-tutorial.md` | 教程（prompt）|
| 2026-07-18 | `1a/2026-07-18_phase1a8-vision-routing-results.md` | 报告（路由实测）|
| 2026-07-18 | `1a/2026-07-18_phase1a8-v3_3-real-data-check.md` | 报告（真实数据复核）|
| 2026-07-18 | `1a/2026-07-18_phase1a8-v3_2-real-data-check.md` | 报告 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-v3_2-acceptance-plan.md` | 计划 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-v3_1-real-data-check.md` | 报告 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-v2-real-data-check.md` | 报告 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-v2-acceptance-plan.md` | 计划 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-process-report.md` | 过程报告 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-acceptance-report.md` | 报告 |
| 2026-07-18 | `1a/2026-07-18_phase1a8-acceptance-plan.md` | 计划 |
| 2026-07-17 | `1a/2026-07-17_phase1a7-acceptance-report.md` | 报告 |
| 2026-07-17 | `1a/2026-07-17_phase1a7-acceptance-plan.md` | 计划 |
| 2026-07-17 | `1a/2026-07-17_phase1a6-acceptance-report.md` | 报告 |
| 2026-07-17 | `1a/2026-07-17_phase1a6-acceptance-plan.md` | 计划 |
| 2026-07-17 | `1a/2026-07-17_phase1a5-acceptance-report.md` | 报告 |
| 2026-07-17 | `1a/2026-07-17_phase1a5-acceptance-plan.md` | 计划 |
| 2026-07-17 | `1a/2026-07-17_phase1a4-acceptance-report.md` | 报告 |
| 2026-07-16 | `1a/2026-07-16_phase1a4-acceptance-plan.md` | 计划 |
| 2026-07-16 | `1a/2026-07-16_phase1a3-supplemental-acceptance.md` | 报告（补充）|
| 2026-07-16 | `1a/2026-07-16_phase1a3-dedup-and-snapconfirm-acceptance.md` | 报告 |
| 2026-07-16 | `1a/2026-07-16_phase1a-phase1a2-acceptance.md` | 验收 |

### Phase 1b（`manual-tests/1b/`，24 份）

| 日期 | 文件 | 类型 |
|---|---|---|
| 2026-07-25 | `1b/2026-07-25_1b4-pr9-acceptance-plan.md` | 计划 |
| 2026-07-25 | `1b/2026-07-25_1b4-pr8-acceptance-report.md` | 报告 |
| 2026-07-25 | `1b/2026-07-25_1b4-pr8-acceptance-plan.md` | 计划 |
| 2026-07-25 | `1b/2026-07-25_1b4-pr7-acceptance-report.md` | 报告（Phase 1 收官）|
| 2026-07-25 | `1b/2026-07-25_1b4-pr7-acceptance-plan.md` | 计划 |
| 2026-07-25 | `1b/2026-07-25_1b4-pr6b-acceptance-report.md` | 报告 |
| 2026-07-25 | `1b/2026-07-25_1b4-pr6b-acceptance-plan.md` | 计划 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr6a-acceptance-report.md` | 报告 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr6a-acceptance-plan.md` | 计划 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr4a-acceptance-report.md` | 报告 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr4a-acceptance-plan.md` | 计划 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr3plus-acceptance-plan.md` | 计划 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr2-acceptance-report.md` | 报告 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr1-acceptance-plan.md` | 计划 |
| 2026-07-24 | `1b/2026-07-24_1b4-pr0-acceptance-plan.md` | 计划 |
| 2026-07-22 | `1b/2026-07-22_phase1b3-runtime-verification.md` | 验证 |
| 2026-07-22 | `1b/2026-07-22_phase1b3-remediation-acceptance-plan.md` | 计划（补救）|
| 2026-07-22 | `1b/2026-07-22_phase1b3-comprehensive-acceptance-report.md` | 报告（综合）|
| 2026-07-22 | `1b/2026-07-22_phase1b3-acceptance-report.md` | 报告 |
| 2026-07-22 | `1b/2026-07-22_phase1b3-acceptance-plan.md` | 计划 |
| 2026-07-22 | `1b/2026-07-22_phase1b2-acceptance-report.md` | 报告 |
| 2026-07-22 | `1b/2026-07-22_phase1b2-acceptance-plan.md` | 计划 |
| 2026-07-21 | `1b/2026-07-21_phase1b1-acceptance-report.md` | 报告 |
| 2026-07-21 | `1b/2026-07-21_phase1b1-acceptance-plan.md` | 计划 |

> 注：1b.2 的「计划 → 报告」闭环见 `2026-07-22_phase1b2-acceptance-plan.md` + `2026-07-22_phase1b2-acceptance-report.md`（被 1b.2 work-plan §292 引用）。

---

## 使用流程

### Phase 1a 编码期间

1. 每完成一个 API 实现 → 在 `docs/phase-1/checklists/phase-1a.md` 中勾选对应项
2. 关键测试 → curl 测试并保存响应到 `automated-smoke/<phase>/api-test-output/`
3. 端到端冒烟测试 → 完成后写 `manual-tests/<日期>_smoke-test.md`（手写总结）
4. 自动化 smoke 跑完后 → 脚本自动写 `automated-smoke/<phase>/<日期>_<phase>-smoke.log`

### Phase 1a.4 起：计划、实现、验收三步闭环

1. 先创建或更新 `docs/phase-1/work-plans/<日期>_<phase>-work-plan.md`；
2. 同时创建 `docs/test-records/manual-tests/<日期>_<phase>-acceptance-plan.md`，先锁定测试 ID、测试目标、fixture 和 real/mock/H2/MySQL 边界；
3. 按工作计划的垂直切片实现，每个切片单独编译和测试；
4. 每次失败先分类并记录根因，不能无记录地改变测试替身或测试名称；
5. 测试完成后在验收计划中追加实际结果，必要时生成 acceptance report；
6. 最后同步 `phase-1a.md`、工作计划状态和验收报告状态。

工作计划和验收计划必须互相链接，并共享阶段编号和测试用例 ID。

### Phase 1b 编码期间

1. 每完成一个 UI 验收项 → 在 `docs/phase-1/checklists/phase-1b.md` 中勾选
2. 浏览器手动测试 → 截图或记录到 `manual-tests/`

### Phase 1 结束

1. 编写 `2026-07-XX_phase1a_summary.md` 总结测试覆盖率
2. 编写 `2026-07-XX_phase1b_summary.md` 总结 UI 测试情况

---

## 测试报告模板（手动测试）

```markdown
# 测试名称：XXXX

**日期**：YYYY-MM-DD
**测试者**：刘博丞
**Phase**：1a / 1b

## 测试目标

简要说明本次测试要验证的功能。

## 测试步骤

1. 启动后端：`mvn spring-boot:run`
2. 启动前端：`npm run dev`
3. 打开浏览器：http://localhost:5173
4. ...

## 预期结果

- 步骤 1：后端在 8080 端口启动
- 步骤 2：前端在 5173 端口启动
- 步骤 3：看到首页
- ...

## 实际结果

- 步骤 1：✅ 后端启动成功
- 步骤 2：✅ 前端启动成功
- 步骤 3：✅ 首页正常
- ...

## 问题与修复

- 问题 1：xxx
- 修复：xxx

## 验收

- [ ] 全部通过
- [ ] 部分通过（待修复）
- [ ] 不通过（需返工）
```

---

## API 测试输出格式

保存 curl 测试结果的标准格式：

```json
{
  "test_case": "POST /api/screenshot/parse - 截图解析",
  "date": "2026-07-09",
  "request": {
    "url": "http://localhost:8080/api/screenshot/parse",
    "method": "POST",
    "body": {
      "fileId": "uuid-test-001",
      "userId": 1
    }
  },
  "response": {
    "http_status": 200,
    "body": {
      "code": 0,
      "data": {
        "conversationId": "uuid-test-001",
        "snapshotDate": "2026-06-09",
        "..."
      }
    }
  },
  "passed": true,
  "notes": "正常解析 18 只基金"
}
```

---

## 维护原则

1. **测试记录不可删除**：失败的测试记录也是项目资产，不要删除
2. **日期命名**：使用测试完成日期，不用创建日期
3. **计划先于代码**：Phase 1a.4 起，工作计划和验收计划必须先于实现创建
4. **测试边界不可漂移**：real/mock/H2/MySQL 策略变化必须记录原因并同步测试名称与结论
5. **手动测试**：Phase 1a 关键路径必须手动跑通，不只依赖单元测试
6. **API 输出**：curl 结果保存为 JSON 便于后续回溯
7. **路径引用**：跨目录引用使用相对路径（如 `../phase-1/checklists/phase-1a.md`）
8. **manual-tests vs automated-smoke 严格区分**：
   - `manual-tests/` 放人写的 markdown（计划/报告/手测结果）
   - `automated-smoke/` 放脚本写的 log + JSON（smoke 输出）
   - 混放会导致后人读 git log 时不知道是脚本写的还是手写的