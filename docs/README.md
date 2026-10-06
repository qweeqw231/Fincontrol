# FinControl 文档目录

本目录是 FinControl 项目的文档根，按「通用指南 / 架构 / 阶段交付物 / 需求基线 / 测试」组织。

> **原始设计文档**：`architecture/FinControl 技术设计文档v2.docx` 为设计基线，不再修改，所有变更通过评审记录或决策增量补充。
>
> **唯一权威决策源**：`phase-0/decisions.md` 末尾的《决策总结表》（决策 1–36）。**所有决策 ID 必须在该表登记才视为生效**（总表优先原则）；子档未登记视为未生效，代码不可引用。
>
> **Phase 划分 vs 实际交付**：Phase 编号 = 能力里程碑（设计归属）；实际交付时间线（含「提前交付」标注）见 [phase-1/phase-change-log.md](./phase-1/phase-change-log.md)。

---

## 现状速览（2026-10-07）

| 项 | 状态 |
|---|---|
| Phase 0 | ✅ 完成（2026-07-09）|
| Phase 1 | ✅ 完成（2026-07-25，决策 34）|
| Phase 2 | 🟡 提前交付（部分，2026-10-07）：月度校正台 `/correction` 已交付（决策 37）；历史查询 UI / 资产配置未启动 |
| Phase 3 | 🟡 提前交付（部分）：净值曲线 + 比例演化已交付；日收益明细、Dietz/XIRR 未做 |
| Phase 4 | ⏳ 未启动 |
| Phase 5 | 🟡 提前交付（部分）：AI 顾问前端 UI + 季度 LQR-ZOH 联合校正台 `/quarterly` 已交付（决策 37）；多用户 / 移动端未开始 |
| 运行形态 | 单进程 8080 单端口（决策 36，2026-09-30 起）|

---

## 目录结构

```
docs/
├── README.md                                      ← 本文件（导航 + 现状 + 规范）
├── SETUP.md                                       ← 【通用】本地开发环境搭建指南
├── requirements/                                  ← 跨阶段产品与页面需求基线
│   └── 2026-07-22_fincontrol-page-requirements.md ← 八页面、样式、数据口径宪法
│
├── architecture/                                  ← 架构（设计基线 + 四轮评审）
│   ├── FinControl 技术设计文档v2.docx             ← 设计基线（v2.0，2026-06-10）
│   └── review/                                    ← 四轮评审记录（199 个问题）
│
├── phase-0/                                       ← Phase 0 交付物（基础设施）
│   ├── decisions.md                               ← 【权威】决策主表（1–36，汇总表在文末）
│   ├── api-contract.md                            ← REST API 契约（30+ 端点）
│   ├── db-schema.sql                              ← 数据库 Schema
│   └── seed-data.sql                              ← fund_category_map 预录
│
├── phase-1/                                       ← Phase 1 交付物（核心闭环）+ 跨阶段现状文档
│   ├── phase-change-log.md                        ← 【现状】Phase 划分 vs 实际交付
│   ├── USER-MANUAL.md                             ← 使用说明书
│   ├── acceptance-criteria.md                     ← Phase 1 验收标准（历史基线，含状态标注）
│   ├── subphase-plan.md                           ← Phase 1 子阶段计划（历史基线）
│   ├── chat-prompt-issues.md                      ← chat prompt 缺陷追踪
│   ├── testing-guide-1a2.md                       ← 1a.2 测试指南（历史）
│   ├── designs/                                   ← 设计稿（ai-client-split / 1a3-dedup-strategy）
│   ├── decisions/                                 ← 子决策详细档（27 / 30 / 31 / 32 / 33 / 36）
│   ├── work-plans/                                ← 实施工作计划（README + 1a/ + 1b/）
│   └── checklists/                                ← 验收清单（phase-1a / phase-1b / bug list）
│
└── test-records/                                  ← 测试记录（跨阶段）
    ├── README.md                                  ← 目录说明 + 命名规范 + 手动测试记录索引
    ├── manual-tests/                              ← 手动验收记录（0 / 1a / 1b 分目录）
    ├── automated-smoke/                           ← 自动化 smoke 输出
    └── screenshots/                               ← UI 截图（asset，不是 log）
```

---

## 文档导航

### 现状与权威（先读这三份）

| 文档 | 用途 |
|---|---|
| **[phase-0/decisions.md](./phase-0/decisions.md)** | 决策主表（1–36）：唯一权威决策源，汇总表在文末 |
| **[phase-1/phase-change-log.md](./phase-1/phase-change-log.md)** | Phase 划分 vs 实际交付（提前交付偏差 + 当前边界）|
| **[SETUP.md](./SETUP.md)** | 本地开发环境搭建 + Key 配置 + 启动（第一次启动前必读）|

### 架构

| 文档 | 用途 |
|---|---|
| **[architecture/FinControl 技术设计文档v2.docx](./architecture/FinControl%20技术设计文档v2.docx)** | 原始设计基线（v2.0，11 章 + 2 附录）|
| **architecture/review/** | 四轮评审记录（数据流水线 / 核心算法 / 前端+AI / 跨模块一致性，199 个问题）|

### 产品与页面需求

| 文档 | 用途 |
|---|---|
| **[requirements/2026-07-22_fincontrol-page-requirements.md](./requirements/2026-07-22_fincontrol-page-requirements.md)** | 侧边栏八页面需求、全局样式、数据口径宪法 |

### Phase 0 / Phase 1 交付物

| 文档 | 用途 |
|---|---|
| **[phase-0/api-contract.md](./phase-0/api-contract.md)** | REST API 契约（30+ 端点 + 请求/响应/错误码）|
| **[phase-0/db-schema.sql](./phase-0/db-schema.sql)** + **[seed-data.sql](./phase-0/seed-data.sql)** | 数据库 Schema + 预录数据 |
| **[phase-1/acceptance-criteria.md](./phase-1/acceptance-criteria.md)** | Phase 1 验收标准（历史基线）|
| **[phase-1/subphase-plan.md](./phase-1/subphase-plan.md)** | Phase 1 子阶段划分 + DoD（历史基线）|
| **[phase-1/USER-MANUAL.md](./phase-1/USER-MANUAL.md)** | 使用说明书（含 AI 路由 / 缓存 / 数据模型说明）|
| **[phase-1/checklists/phase-1a.md](./phase-1/checklists/phase-1a.md)** + **[phase-1b.md](./phase-1/checklists/phase-1b.md)** | 验收清单（历史实时清单）|
| **[phase-1/decisions/](./phase-1/decisions/)** | 子决策详细档（27 / 30 / 31 / 32 / 33 / 36）|
| **[phase-1/work-plans/](./phase-1/work-plans/)** | 各 PR 工作计划（1a / 1b 分目录）|

### 测试

| 文档 | 用途 |
|---|---|
| **[test-records/README.md](./test-records/README.md)** | 测试记录目录说明 + 命名规范 + **手动测试记录索引** |
| **[test-records/manual-tests/](./test-records/manual-tests/)** | 手动验收记录（0 / 1a / 1b）|

---

## 文档命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| 评审记录 | `<主题>-review-<日期>.md` | `data-pipeline-review-2026-07-09.md` |
| 决策主表 | `decisions.md`（在 `phase-0/` 内）| `phase-0/decisions.md` |
| 决策子档 | `decision-<编号>-<主题>.md`（在 `phase-N/decisions/` 内，**必须同步登记主表**）| `decision-36-single-process-runtime.md` |
| Phase 变更记录 | `phase-change-log.md`（在 `phase-1/` 内）| `phase-1/phase-change-log.md` |
| Phase 验收标准 | `acceptance-criteria.md`（在 `phase-N/` 内）| `phase-1/acceptance-criteria.md` |
| Phase 验收清单 | `checklists/phase-<n>.md` | `phase-1/checklists/phase-1a.md` |
| 子阶段工作计划 | `<日期>_<phase>-work-plan.md`（在 `phase-1/work-plans/<1a\|1b>/` 下） | `2026-07-25_1b4-pr9-work-plan.md` |
| 验收计划 / 报告 | `<日期>_<scope>-acceptance-plan.md` / `-acceptance-report.md`（在 `test-records/manual-tests/<phase>/` 下） | `2026-07-25_1b4-pr7-acceptance-report.md` |
| API 契约 | `api-contract.md`（在 `phase-N/` 内）| `phase-0/api-contract.md` |
| 数据库脚本 | `<类型>-schema.sql`（在 `phase-N/` 内）| `phase-0/db-schema.sql` |
| 跨阶段需求 | `<日期>_<主题>-requirements.md`（在 `requirements/` 下） | `2026-07-22_fincontrol-page-requirements.md` |
| 搭建指南 | `SETUP.md`（根目录，跨阶段）| `SETUP.md` |

> 历史档案（`test-records/`、已归档 checklists 等）**不追写、不改名**；规范只约束新增文档。

---

## 文档维护原则

1. **原始设计文档不修改**：`architecture/FinControl 技术设计文档v2.docx` 视为设计基线，所有变更通过评审记录或决策增量补充。
2. **评审记录不可改写历史**：评审结论只追加，不修改已确认内容。如需更正，追加新评审并说明关联。
3. **决策登记规范（总表优先）**：
   - 任何决策生效必须同时满足：① 正文或子档存在；② `phase-0/decisions.md` 末尾《决策总结表》有对应行。缺一不可。
   - 代码 / 文档中引用「决策 N」时，N 必须能在主表汇总行中找到；**引用前先查主表**。
   - 子档（`phase-N/decisions/decision-XX-*.md`）只补充背景与实施细节，主表行才是生效标志。
   - 可选校验：`node scripts/check-decision-refs.cjs`（扫全仓「决策 N」/ `decision-N-*` 引用与主表比对，发现未登记引用即报错退出）。
4. **文档归属**：新增文档按类型归位 —— 决策 → 主表 + `phase-N/decisions/`；测试记录 → `test-records/`；实施计划 → `phase-1/work-plans/`；跨阶段现状说明 → `phase-1/` 顶层（如 `phase-change-log.md`）；需求 → `requirements/`。不确定时先确认，勿新建顶层目录。
5. **跨越 Phase 边界必须记录**：出现「提前交付 / 推迟 / 重排」时，向 [phase-1/phase-change-log.md](./phase-1/phase-change-log.md) 追加一节（不重写历史），并同步根目录 README 的「当前状态」表。
6. **阶段验收必更新根目录 README**（决策 19）：完成阶段性验收时更新根 README 的「当前状态」段（阶段、日期、证据链接）。
7. **文档更新必 commit + push**（决策 23）：文档更新后立刻提交；网络可达则推送。
8. **验收清单实时更新**：编码期间每完成一项即在对应 checklist 勾选并填完成日期（历史清单不追写）。
9. **内部引用使用相对路径**：跨目录引用用 `../phase-N/xxx.md` 形式，便于整体移动。

---

## 历史归档说明

- `phase-1/checklists/*`、`phase-1/testing-guide-1a2.md`、`phase-1/designs/*`、`test-records/manual-tests/*` 为**历史档案**，记录当时的编号与语境，不做追写。
- `architecture/adr/`：曾规划但**未启用**（无实际目录）；当前决策体系由 `phase-0/decisions.md` 主表 + `phase-N/decisions/` 子档承担，不另设 ADR。