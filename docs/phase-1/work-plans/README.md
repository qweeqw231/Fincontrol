# Phase 1 工作计划目录

> 本目录存放每个 Phase 1 子阶段的**实施工作计划**。它与 `docs/test-records/manual-tests/` 下的验收计划/报告配对使用。

## 目录职责

| 目录 | 回答的问题 | 典型内容 |
|---|---|---|
| `docs/phase-1/work-plans/` | 要做什么、怎么拆、做到什么算完成 | 范围、切片、依赖、非目标、风险、停止条件 |
| `docs/test-records/manual-tests/` | 实际测了什么、结果是什么、还剩什么 | 测试 ID、测试替身、命令、输出、缺陷、验收结论 |
| `docs/phase-1/checklists/` | 总体进度到哪里 | 阶段摘要状态和完成日期 |

## 文件配对规范

每个子阶段至少有一对文件，使用相同日期和阶段编号：

```text
docs/phase-1/work-plans/2026-07-16_phase1a4-work-plan.md
docs/test-records/manual-tests/2026-07-16_phase1a4-acceptance-plan.md
```

工作计划必须链接验收计划；验收计划必须反向链接工作计划。测试用例 ID（例如 `A4-S01`）在验收计划中先定义，代码测试方法、命令和结果都引用这些 ID。

## 生命周期

```text
PLANNED → IN_PROGRESS → BUSINESS_PASS → PRODUCTION_PENDING → PASS
                         └──────────────→ BLOCKED
```

- `PLANNED`：范围和 DoD 已冻结，尚未编码；
- `IN_PROGRESS`：正在实现；
- `BUSINESS_PASS`：业务层测试通过，但真实数据库或端到端仍未验收；
- `PRODUCTION_PENDING`：已明确遗留的生产验证项；
- `PASS`：该子阶段定义的全部验收层级和证据均通过；
- `BLOCKED`：存在阻塞问题，必须记录根因和下一步，不得靠改名掩盖。

## 每个工作计划必须包含

1. 目标、范围和明确的非目标；
2. 前置依赖和当前代码基线；
3. 按垂直切片排列的任务；
4. 每个切片的实现文件、测试 ID 和验证命令；
5. 测试替身策略（real / mock / H2 / MySQL）；
6. 风险、停止条件和提交检查点；
7. 与验收计划、checklist、API 契约的链接。

## 规则

- 计划先于代码；验收用例先于测试实现。
- 测试目标或测试替身变化时，必须先更新验收计划并记录原因。
- 业务层通过不能自动宣称真实 MySQL 或前后端端到端通过。
- 失败测试记录不可删除；修复后追加实际结果和根因。
