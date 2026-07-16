# FinControl 测试记录目录

> Phase 0 产出。本目录存放 FinControl 项目的测试相关记录，包括验收清单、手动测试记录、API 测试输出。

---

## 目录结构

```
docs/
├── phase-1/
│   ├── work-plans/                                    ← 子阶段实施工作计划
│   │   ├── README.md                                  ← 工作计划规范
│   │   └── 2026-07-16_phase1a4-work-plan.md
│   └── checklists/
│       ├── phase-1a.md                                ← Phase 1a 总体进度清单
│       └── phase-1b.md                                ← Phase 1b 总体进度清单
└── test-records/
    ├── README.md                                      ← 本说明
    ├── manual-tests/                                  ← 验收计划、验收报告、手动测试记录
    │   ├── 2026-07-16_phase1a4-acceptance-plan.md
    │   ├── 2026-07-16_phase1a3-supplemental-acceptance.md
    │   └── ...
    └── api-test-output/                               ← API 测试输出（curl/HTTP Client 结果）
        ├── 2026-07-09_screenshot_parse.json
        └── ...
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
| 手动测试 | `<日期>_<测试名>.md` | `2026-07-09_smoke-test.md` |
| API 输出 | `<日期>_<api-name>.json` | `2026-07-09_screenshot_parse.json` |

---

## 使用流程

### Phase 1a 编码期间

1. 每完成一个 API 实现 → 在 `docs/phase-1/checklists/phase-1a.md` 中勾选对应项
2. 关键测试 → curl 测试并保存响应到 `test-records/api-test-output/`
3. 端到端冒烟测试 → 完成后写 `test-records/manual-tests/<日期>_smoke-test.md`

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
2. 浏览器手动测试 → 截图或记录到 `test-records/manual-tests/`

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
</content>