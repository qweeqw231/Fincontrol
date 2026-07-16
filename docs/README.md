# FinControl 文档目录

本目录是 FinControl 项目的文档根，按"通用指南 / 架构 / 阶段交付物 / 测试"四类组织。

> **原始设计文档**：`FinControl 技术设计文档v2.docx` 已迁移至 `docs/architecture/`，作为设计基线不再修改，所有变更通过评审记录或 ADR 增量补充。

---

## 目录结构

```
docs/
├── README.md                                    ← 本文件
├── SETUP.md                                     ← 【通用】本地开发环境搭建指南
│
├── architecture/                                ← 架构相关（基线 + 评审 + 决策记录）
│   ├── FinControl 技术设计文档v2.docx           ← 设计基线（v2.0，2026-06-10）
│   ├── review/                                  ← 四轮架构评审记录
│   │   ├── data-pipeline-review-2026-07-09.md           （第一轮）
│   │   ├── algorithm-and-constraints-review-2026-07-09.md （第二轮）
│   │   ├── frontend-and-ai-routing-review-2026-07-09.md     （第三轮）
│   │   └── cross-module-consistency-review-2026-07-09.md    （第四轮）
│   └── adr/                                     ← 架构决策记录（待启用）
│
├── phase-0/                                     ← Phase 0 交付物（基础设施搭建）
│   ├── decisions.md                             ← Phase 0 决策锁定（6 项）
│   ├── api-contract.md                          ← REST API 契约（30+ 端点）
│   ├── db-schema.sql                            ← 数据库 Schema（7 张表）
│   └── seed-data.sql                            ← fund_category_map 预录 20 条
│
├── phase-1/                                     ← Phase 1 交付物（核心闭环）
│   ├── acceptance-criteria.md                   ← Phase 1 验收标准（47 项 + 18 项 P0）
│   ├── work-plans/                              ← 子阶段实施工作计划
│   │   ├── README.md                            ← 工作计划规范
│   │   └── 2026-07-16_phase1a4-work-plan.md
│   └── checklists/                              ← 实时验收清单
│       ├── phase-1a.md                          ← Phase 1a 后端 24 项 API + 8 项 P0
│       └── phase-1b.md                          ← Phase 1b 前端 23 项 UI + 4 项 P0
│
└── test-records/                                ← 测试记录（跨阶段）
    ├── README.md                                ← 测试记录目录说明 + 命名规范
    ├── manual-tests/                            ← 手动测试记录（按日期命名）
    └── api-test-output/                         ← API 测试输出 JSON
```

---

## 文档导航

### 通用指南（所有阶段）

- **[SETUP.md](./SETUP.md)** — 本地开发环境搭建、MySQL 初始化、DeepSeek 配置、启动命令（**第一次启动前必读**）

### 架构（设计基线 + 评审 + ADR）

| 文档 | 用途 |
|------|------|
| **[architecture/FinControl 技术设计文档v2.docx](./architecture/FinControl%20技术设计文档v2.docx)** | 原始设计基线（v2.0，11 章 + 2 附录） |
| **architecture/review/** 四份评审 | 数据流水线 → 核心算法 → 前端+AI → 跨模块一致性（覆盖 199 个问题）|
| **architecture/adr/**（待启用）| Phase 4+ 启用 Michael Nygard 模板的 ADR |

#### 四轮评审记录

##### 2026-07-09 数据流水线与数据模型评审（第一轮）

- **范围**：截图上传 → API 解析 → 大类确认 → 数据写入的全流程；fund_category_map 匹配逻辑；同日多次更新规则；asset_raw / asset_snapshot 字段覆盖度；source 枚举完整性
- 文件：`architecture/review/data-pipeline-review-2026-07-09.md`
- 状态：✅ 已完成，决策已锁定
- 关键产出：47 个问题（🔴 P0 6 / 🟡 P1 10 / 🟠 P2 11 / 🟢 P3 5）

##### 2026-07-09 核心算法与业务约束规则评审（第二轮）

- **范围**：二元一次方程组求解器输入变量；三层约束处理规则的取整交互；约束优先级（4.2 月度 vs 4.3.5 LQR）；LQR 策略模式接口兼容性；五大边界处理规则
- 文件：`architecture/review/algorithm-and-constraints-review-2026-07-09.md`
- 状态：✅ 已完成，决策已锁定
- 关键产出：63 个问题（🔴 P0 18 / 🟡 P1 25 / 🟠 P2 15 / 🟢 P3 5）

##### 2026-07-09 前端交互逻辑与 AI 顾问分流设计评审（第三轮）

- **范围**：页面跳转关系与导航闭环；大类确认面板误操作防护；AI 顾问意图分类器边界与回路切换；快照日期默认值与字段映射
- 文件：`architecture/review/frontend-and-ai-routing-review-2026-07-09.md`
- 状态：✅ 已完成
- 关键产出：60 个问题（🔴 P0 12 / 🟡 P1 28 / 🟠 P2 15 / 🟢 P3 5）

##### 2026-07-09 跨模块一致性架构评审（第四轮·最终轮）

- **范围**：数据模型字段 → 业务逻辑路径审计；前端页面数据 → 后端 API 对应审计；Phase 1-3 开发计划覆盖度；14 条跨模块修改连锁影响
- 文件：`architecture/review/cross-module-consistency-review-2026-07-09.md`
- 状态：✅ 已完成
- 关键产出：29 个问题（🔴 P0 6 / 🟡 P1 8 / 🟠 P2 10 / 🟢 P3 5）、14 条连锁链、Phase 计划修订建议

### Phase 0 交付物（基础设施搭建）

| 文档 | 用途 |
|------|------|
| **[phase-0/decisions.md](./phase-0/decisions.md)** | Phase 0 决策锁定（6 项硬约束） |
| **[phase-0/api-contract.md](./phase-0/api-contract.md)** | REST API 契约（30+ 端点 + 请求/响应/错误码）|
| **[phase-0/db-schema.sql](./phase-0/db-schema.sql)** | 数据库 Schema（7 张表 + 索引 + 注释 + 初始化数据）|
| **[phase-0/seed-data.sql](./phase-0/seed-data.sql)** | fund_category_map 预录 20 条（18 只基金 + 余额类）|

### Phase 1 交付物（核心闭环）

| 文档 | 用途 |
|------|------|
| **[phase-1/acceptance-criteria.md](./phase-1/acceptance-criteria.md)** | Phase 1 验收标准（47 项 + 18 项 P0）|
| **[phase-1/checklists/phase-1a.md](./phase-1/checklists/phase-1a.md)** | Phase 1a 后端 24 项 API + 8 项 P0 实时验收 |
| **[phase-1/checklists/phase-1b.md](./phase-1/checklists/phase-1b.md)** | Phase 1b 前端 23 项 UI + 4 项 P0 实时验收 |

### 测试记录（跨阶段）

- **[test-records/README.md](./test-records/README.md)** — 测试记录目录结构 + 命名规范 + 模板
- **[phase-1/work-plans/README.md](./phase-1/work-plans/README.md)** — Phase 1 子阶段工作计划规范
- `test-records/manual-tests/` — 手动测试记录（按日期命名）
- `test-records/api-test-output/` — API 测试输出 JSON（按日期命名）

---

## 文档命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| 评审记录 | `<主题>-review-<日期>.md` | `data-pipeline-review-2026-07-09.md` |
| ADR | `ADR-<编号>-<主题>.md` | `ADR-008-balance-category-data-path.md` |
| Phase 决策 | `decisions.md`（在 phase-N/ 内）| `phase-0/decisions.md` |
| Phase 验收标准 | `acceptance-criteria.md`（在 phase-N/ 内）| `phase-1/acceptance-criteria.md` |
| Phase 验收清单 | `checklists/phase-<n>.md` | `phase-1/checklists/phase-1a.md` |
| 子阶段工作计划 | `<日期>_<phase>-work-plan.md`（在 `phase-1/work-plans/` 下） | `phase-1/work-plans/2026-07-16_phase1a4-work-plan.md` |
| API 契约 | `api-contract.md`（在 phase-N/ 内）| `phase-0/api-contract.md` |
| 数据库脚本 | `<类型>-schema.sql`（在 phase-N/ 内）| `phase-0/db-schema.sql` |
| 补充设计 | `<主题>-<版本>.md` | `data-model-supplement-v1.md` |
| 搭建指南 | `SETUP.md`（根目录，跨阶段）| `SETUP.md` |

---

## 文档维护原则

1. **原始设计文档不修改**：`architecture/FinControl 技术设计文档v2.docx` 视为设计基线，所有变更通过评审记录或 ADR 增量补充。
2. **评审记录不可改写历史**：评审结论只追加，不修改已确认内容。如需更正，追加新评审并说明关联。
3. **Phase 决策锁定**：`phase-0/decisions.md` 中的 6 项决策为硬约束，Phase 1+ 编码期间不可轻易回退。
4. **验收清单实时更新**：Phase 1a/1b 编码期间，每完成一项即在对应 checklist 中勾选并填写完成日期。
5. **内部引用使用相对路径**：跨目录引用时使用 `../phase-N/xxx.md` 形式，便于整体移动。

---

## 当前状态（截至 2026-07-09）

### Phase 0 完成度

- ✅ **Phase 0 文档产出**（4 份）：decisions.md、api-contract.md、db-schema.sql、seed-data.sql
- ✅ **后端骨架**（4 个）：pom.xml、application.yml、.gitignore、FincontrolApplication.java
- ✅ **前端骨架**（2 个）：package.json、.gitignore
- ✅ **通用文档**（2 份）：SETUP.md、README.md（本文档）
- ✅ **测试记录目录**（3 份）：test-records/README.md、phase-1a.md、phase-1b.md

### 四轮评审汇总（199 个问题）

| 轮次 | 范围 | 问题数 | 🔴 P0 | 🟡 P1 | 🟠 P2 | 🟢 P3 |
|------|------|-------|--------|--------|--------|--------|
| 第一轮 | 数据流水线 + 数据模型 | 47 | 6 | 10 | 11 | 5 |
| 第二轮 | 核心算法 + 业务约束 | 63 | 18 | 25 | 15 | 5 |
| 第三轮 | 前端 + AI 顾问 | 60 | 12 | 28 | 15 | 5 |
| 第四轮 | 跨模块一致性 | 29 | 6 | 8 | 10 | 5 |
| **合计** | | **199** | **42** | **71** | **51** | **20** |

---

## 启动顺序（5 步）

1. **阅读**：`SETUP.md`
2. **数据库**：执行 `docs/phase-0/db-schema.sql` + `docs/phase-0/seed-data.sql`
3. **配置环境变量**：`DEEPSEEK_API_KEY=sk-...`
4. **启动后端**：终端 1 → `cd fincontrol-backend && mvn spring-boot:run`
5. **启动前端**：终端 2 → `cd fincontrol-frontend && npm install && npm run dev`

启动后访问：
- 前端：http://localhost:5173
- 后端 Swagger：http://localhost:8080/swagger-ui.html
- 后端 API 文档：http://localhost:8080/v3/api-docs

---

## 下一步建议

1. **执行 Phase 0 实体搭建**（按 `SETUP.md`）
2. **Phase 1a 后端编码**（按 `phase-1/checklists/phase-1a.md`）
3. **Phase 1b 前端编码**（按 `phase-1/checklists/phase-1b.md`）
4. **设计文档 v2.1 合并**：将四轮评审 + Phase 0 决策整合入 `architecture/FinControl 技术设计文档v2.docx`