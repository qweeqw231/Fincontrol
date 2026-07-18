# docs/test-records/automated-smoke/

> **自动化 smoke 输出**（与 `manual-tests/` 严格区分语义）。

## 目录结构

```
automated-smoke/
├── README.md
├── 1a7/                          # Phase 1a.7 阶段
│   ├── 2026-07-17_phase1a7-smoke.log     # ★ commit（历史回溯）
│   └── api-test-output/                   # ✗ gitignore（每次跑都变）
└── <新阶段>/                     # 未来：1b1/, 2a1/, ...
```

## 与 manual-tests/ 的区别

| 维度 | `manual-tests/` | `automated-smoke/` |
|---|---|---|
| **语义** | 手测 + 验收计划 + 验收报告 | 自动化脚本（smoke）跑出的输出 |
| **写入方** | 人（写 markdown） | 脚本（lib-common.sh 自动写） |
| **内容** | 计划 / 报告 / 手动测试结果 | smoke log + API 响应 JSON |
| **commit** | 是（资产） | smoke log 提交，api-test-output 不提交 |
| **命名** | `<日期>_<阶段>-{plan/report}.md` | `<日期>_<阶段>-smoke.log` |

## 命名约定

- **smoke log**：`automated-smoke/<阶段>/<日期>_<阶段>-smoke.log`（与 manual-tests 命名风格一致）
- **api-test-output**：`automated-smoke/<阶段>/api-test-output/*.json`（脚本自动生成，无需命名）
- **阶段子目录**：与 `scripts/smoke/<阶段>/` 一一对应

## 写脚本时的路径约定

在 smoke 脚本的 `lib-common.sh` 中：

```bash
# 自动化输出（Git 跟踪或部分跟踪）
LOG_DIR="$PROJECT_ROOT/docs/test-records/automated-smoke/<phase>"
LOG_FILE="$LOG_DIR/<日期>_<阶段>-smoke.log"
API_OUT_DIR="$LOG_DIR/api-test-output"  # gitignored

# 验收报告（手动写，不通过脚本）
PROJECT_ROOT/docs/test-records/manual-tests/<日期>_<阶段>-acceptance-{plan|report}.md
```

## .gitignore 配合

```gitignore
# 自动化测试输出（不提交，每次跑都变）
docs/test-records/automated-smoke/**/api-test-output/
```

smoke log 本身**会** commit（保留历史记录）；api-test-output 永远不 commit。