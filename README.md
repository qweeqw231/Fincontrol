# FinControl Workspace

> 个人资产配置控制系统 —— 多仓库工作区根目录

FinControl 是一个以控制论为理论基础、以真实个人账户为实验平台、以数学求解器为核心引擎的个人资产配置控制系统。

---

## 项目状态

| Phase | 状态 | 入口 |
|-------|------|------|
| **Phase 0** | ✅ 已收官 | [decisions.md](docs/phase-0/decisions.md) |
| **1a.1** 后端基础设施 | ✅ 已通过（`mvn test` 6/6） | [df6a8ff](https://github.com/qweeqw231/Fincontrol/tree/df6a8ff) |
| **1a.2** 截图解析 API 链 | ✅ 已通过（`mvn test` 6/6，4 个端点 + 5 类错误路径覆盖） | [94fed26](https://github.com/qweeqw231/Fincontrol/tree/94fed26) |
| **1a.2 修复** | ✅ @Transactional 回滚 bug 修复 | [db8728e](https://github.com/qweeqw231/Fincontrol/tree/db8728e) |
| **1a.3** 快照确认与事务 | ✅ 业务逻辑 6/6 + 真实 MySQL 持久化闭环 | [1a.3 报告](docs/test-records/manual-tests/2026-07-16_phase1a3-supplemental-acceptance.md) |
| **1a.4** 快照查询 + 首页辅助 + 大类映射 + AI 顾问 + 解析日志 | ✅ 19 API 全部完成 | — |
| **1a.5** 视觉模型切到 minimax | ✅ DeepSeek 文本 → minimax M3 多模态 | — |
| **1a.6** 文本模型切到 minimax | ✅ | — |
| **1a.7** 真实 MySQL 持久化 + dialect 修复 | ✅ upsert 路径全闭环 | — |
| **1a.8** AI 服务韧性增强（双 provider + Caffeine + resilience4j） | ✅ 架构就绪；豆包 4 model 实测失败（决策 12 暂废） | [1a.8 验收](docs/test-records/manual-tests/2026-07-18_phase1a8-acceptance-report.md) |
| **1a.9** 总资产双轨 + DISCREPANCY 1% 报警 | ✅ prompt v2.6 + DedupEngine 双轨 | [1a.9 报告](docs/test-records/manual-tests/2026-07-18_phase1a8-v3_3-real-data-check.md) |
| **1a.10** 双路径并存（4×单图+confirm / 1×parse-batch） | ✅ 路径 A 全 PASS；路径 B minimax 4 图 batch 偶发 timeout（PRODUCTION_BLOCKED 已知） | [1a.10 真实 E2E](docs/test-records/manual-tests/2026-07-19_phase1a10-real-e2e.md) |
| **Phase 1a 整体验收** | ✅ **全部完成**（mvn test 238/238 + 路径 A 真实 confirm 19/7/19 跑通） | [1a 整体验收报告](docs/test-records/manual-tests/2026-07-19_phase1a-acceptance.md) |
| **1b.1–1b.4** 前端对接 | 🟡 准备启动 | — |
| **Phase 2** 核心业务 | ⏳ 待启动 | — |

**架构文档**：[技术设计文档 v2.0](docs/architecture/技术设计文档v2.docx)（2026-06-10）

**关键评审**：[数据流水线与数据模型评审](docs/architecture-review/data-pipeline-review-2026-07-09.md)（2026-07-09，已锁定决策）

---

## 仓库结构（polyrepo）

```text
Fincontrol/
├── docs/                              ← 跨端架构文档与评审记录
│   ├── README.md                      ← 文档目录索引
│   ├── SETUP.md                       ← 本地开发环境搭建指南（含视觉模型 API Key 配置）
│   ├── phase-0/                       ← Phase 0 决策 / API 契约 / db-schema
│   ├── phase-1/                       ← Phase 1 验收 / 子阶段计划 / 1a.2 测试教程
│   │   ├── checklists/phase-1a.md     ← Phase 1a 实时验收清单（✅ 全部完成）
│   │   └── work-plans/                ← 1a.8 / 1a.9 / 1a.10 工作计划
│   └── test-records/manual-tests/     ← 1a.3 / 1a.7 / 1a.8 / 1a.9 / 1a.10 验收报告
│
├── fincontrol-backend/                ← 【子仓库 1】Spring Boot 后端（✅ Phase 1a 完成）
│   ├── src/main/java/com/fincontrol/   ← controller / service / entity / mapper / config / common / dto / ai
│   ├── src/main/resources/            ← application.yml + application-local.yml
│   └── src/test/java/                 ← 单元测试（Mockito strict）+ H2 集成测试
│
├── fincontrol-frontend/               ← 【子仓库 2】React 前端（🟡 1b 准备启动）
│
├── .gitignore                         ← 工作区级忽略规则
└── README.md                          ← 本文件
```

---

## 视觉模型 API Key（minimax 多模态 + 豆包 fallback）配置

> 1a.5 起从 DeepSeek 切到 minimax M3 多模态（M3 原生支持图像/语音/音乐）。1a.8 增加豆包 ARK 双 provider 路由（vision 3+ 张图路由走豆包 primary）。
>
> **2026-07-19 决策 12**：豆包 vision 4 个 model（1-5-pro / 1-8 / 2-0-pro / 2-1-turbo）实测全部失败（404/429/timeout），**Phase 1a 按 minimax-only 验收通过**。豆包路径代码完整保留，作为未来重新启用时的基础设施。

1. 访问 `https://api.minimaxi.chat/` 注册并获取 API Key
2. 编辑 `fincontrol-backend/src/main/resources/application-local.yml`（已在 .gitignore 内，不入仓）：

   ```yaml
   fincontrol:
     ai:
       vision:
         minimax:
           api-key: <YOUR_MINIMAX_API_KEY_HERE>
           base-url: https://api.minimaxi.com/v1
           model: MiniMax-M3
           timeout-seconds: 300
   ```

3. 重启后端：`mvn spring-boot:run` 生效。

也可以用环境变量覆盖：

```cmd
set VISION_API_KEY=eyJxxxxx...
```

加载优先级：**环境变量 > application-local.yml > application.yml 占位符（启动时抛 5001 强制填）**

> **1a.8 重构**：VisionModelClient 改用 `AiProperties` 构造器注入（`@ConfigurationProperties`），`fincontrol.ai.vision.minimax` + `fincontrol.ai.vision.doubao` 双 provider。代码使用 `AiRouter` 按 `imageCount-threshold=2` 自动选择 primary，互为 fallback。
>
> **1a.10 清理**：原 `fincontrol.vision.*` 段（1a.7 兼容 `@Value` 注入）已删除，唯一视觉配置入口是 `fincontrol.ai.vision.*`。

---

## 后端技术栈

- Java 17 + Spring Boot 3.3.5
- MyBatis-Plus 3.5.9（DAO + SqlSessionFactory 自动配置）
- MySQL 8.0 + HikariCP（连接池 pool size = 10）
- miniMax M3 / OpenAI-compatible chat completions（视觉多模态 primary）
- 豆包 ARK / OpenAI Responses（vision fallback，决策 12 暂时废弃）
- OkHttp 4.12（HTTP 调用，含双 provider 独立连接池 + 5min read timeout）
- springdoc-openapi 2.6 + Swagger UI
- JUnit 5 + Mockito + AssertJ（单元测试）
- resilience4j（重试 + 熔断）
- Caffeine（vision cache，1024 entries / 24h TTL）

---

## 前端技术栈（1b 准备启动）

- React 18 + React Router
- ECharts / Recharts（图表）
- Axios（API 调用）
- Zustand（状态管理）

详见 `fincontrol-frontend/package.json`。

---

## 设计文档导航

| 文档 | 路径 | 说明 |
|------|------|------|
| Phase 0 决策 | [docs/phase-0/decisions.md](docs/phase-0/decisions.md) | 12 项硬约束（决策 1-12） |
| Phase 0 API 契约 | [docs/phase-0/api-contract.md](docs/phase-0/api-contract.md) | 30+ 端点 |
| Phase 1 验收标准 | [docs/phase-1/acceptance-criteria.md](docs/phase-1/acceptance-criteria.md) | 47 项 + 18 项 P0 |
| Phase 1 子阶段计划 | [docs/phase-1/subphase-plan.md](docs/phase-1/subphase-plan.md) | 1a.1–1a.7 + 1b.1–1b.4 |
| Phase 1a 实时清单 | [docs/phase-1/checklists/phase-1a.md](docs/phase-1/checklists/phase-1a.md) | ✅ 全部完成（238/238 + 真实 confirm） |
| Phase 1a 整体验收 | [docs/test-records/manual-tests/2026-07-19_phase1a-acceptance.md](docs/test-records/manual-tests/2026-07-19_phase1a-acceptance.md) | 5 段式 + 路径 A/B 独立判定 |
| 1a.10 真实 E2E | [docs/test-records/manual-tests/2026-07-19_phase1a10-real-e2e.md](docs/test-records/manual-tests/2026-07-19_phase1a10-real-e2e.md) | §9.5b/§9.6 minimax 4 图 batch |
| 1a.10 工作计划 | [docs/phase-1/work-plans/2026-07-19_phase1a10-work-plan.md](docs/phase-1/work-plans/2026-07-19_phase1a10-work-plan.md) | 双路径切片 |
| 1a.8 验收 | [docs/test-records/manual-tests/2026-07-18_phase1a8-acceptance-report.md](docs/test-records/manual-tests/2026-07-18_phase1a8-acceptance-report.md) | 方案 C 路由 + 双 provider |
| 1a.2 测试教程 | [docs/phase-1/testing-guide-1a2.md](docs/phase-1/testing-guide-1a2.md) | curl + mvn test 教程 |

---

## Phase 1 启动检查清单

### Phase 1a 后端（✅ 全部完成）

- [x] **1a.1**：基础设施搭建（Spring Boot + HikariCP pool）
- [x] **1a.2**：截图解析 API 链（4 个端点 + P0-1.4 + P0-4.4）
- [x] **1a.3**：快照入库业务闭环（asset_raw / asset_snapshot / fund_category_map 三表事务；10s rollback）
- [x] **1a.4**：快照查询 + 首页辅助 + 大类映射 + AI 顾问 + 解析日志（19 API）
- [x] **1a.5**：视觉模型切到 minimax 多模态
- [x] **1a.6**：文本模型切到 minimax
- [x] **1a.7**：真实 MySQL 持久化 + dialect 修复
- [x] **1a.8**：AI 服务韧性增强（双 provider 路由 + Caffeine cache + resilience4j retry/CB；架构就绪）
- [x] **1a.9**：总资产双轨 + DISCREPANCY 1% 报警
- [x] **1a.10**：双路径并存（4×单图 + 1×confirm / 1×parse-batch）

### Phase 1b 前端（🟡 准备启动）

- [ ] 1b.1 首页展示（余额类 + 六大类总值 + 六大类明细含 profit）
- [ ] 1b.2 数据管理页面（上传截图 → AI 解析 → 大类确认面板）
- [ ] 1b.3 大类确认面板（余额类下拉 + 10s 撤销 + 忽略按钮）
- [ ] 1b.4 AI 顾问页面（多轮对话 + 意图分类 + system prompt 切换）
- [ ] 1b.5 全局状态管理（Zustand）
- [ ] 1b.6 前端防抖（确认入库按钮 disabled 状态机）
- [ ] 1b.7 累计收益率卡片隐藏
- [ ] 1b.8 截图日期默认值

---

## 本地开发快速启动

```cmd
:: 1. 启动 MySQL，建库 + 跑 schema
mysql -u root -p < docs/phase-0/db-schema.sql
mysql -u root -p < docs/phase-0/seed-data.sql

:: 2. 编辑 application-local.yml 填 miniMax Key
notepad fincontrol-backend\src\main\resources\application-local.yml

:: 3. 后端启动 + 跑全量测试
cd fincontrol-backend
mvn -B test -DfailIfNoTests=false
mvn spring-boot:run

:: 4. 前端启动（待 1b.* 启动时配）
cd fincontrol-frontend
npm install
npm run dev
```

完整说明：[docs/SETUP.md](docs/SETUP.md)。

---

## 与其他项目的关系

- **微控金融（鸿蒙版）**：v1.0 已封存，FinControl 的前身原型
- **DecisionMate**：技术共生项目，共享 React 组件

详见技术设计文档 1.4、1.5 节。

---

## 维护者

- 刘博丞（项目作者）
- 评审者：架构审查助手（2026-07-09 数据流水线评审）

---

*Phase 1a 收尾完成时间：2026-07-19 23:00（Asia/Shanghai）*
