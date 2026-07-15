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
| **1a.5 视觉模型切到 minimax** | ✅ DeepSeek 文本模型 → MiniMax M3 多模态 | 当前仓库 |
| 1a.3 快照入库 | ⏳ 待启动 | — |
| 1a.4 / 1a.6 / 1b.* | ⏳ 待启动 | — |

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
│   └── phase-1/                       ← Phase 1 验收 / 子阶段计划 / 1a.2 测试教程
│
├── fincontrol-backend/                ← 【子仓库 1】Spring Boot 后端
│   ├── src/main/java/com/fincontrol/   ← controller / service / entity / mapper / config / common / dto
│   ├── src/main/resources/            ← application.yml + application-local.yml
│   └── src/test/java/                 ← 单元测试（Mockito strict）
│
├── fincontrol-frontend/               ← 【子仓库 2】React 前端
│
├── .gitignore                         ← 工作区级忽略规则
└── README.md                          ← 本文件
```

---

## 视觉模型 API Key（minimax 多模态）配置

> 1a.2 已从 DeepSeek 切到 MiniMax M3 系列——M3 原生多模态（M3 还覆盖 M2.7 / 图像 / 语音 / 音乐），订阅 TokenPlanPlus 后即可调用。

1. 访问 `https://api.minimaxi.chat/` 注册并获取 API Key
2. 编辑 `fincontrol-backend/src/main/resources/application-local.yml`（已在 .gitignore 内，不入仓）：

   ```yaml
   fincontrol:
     vision:
       api-key: <YOUR_MINIMAX_API_KEY_HERE>
   ```

3. 重启后端：`mvn spring-boot:run` 生效。

也可以用环境变量覆盖：

```cmd
set VISION_API_KEY=eyJxxxxx...
```

加载优先级：**环境变量 > application-local.yml > application.yml 占位符（启动时抛 5001 强制填）**

**注意**：如果实际接口的 schema 与 OpenAI-compatible 不一致，仅需修改 `VisionModelClient.java` 内部 message 构造；其它 12 个文件（Controller / DTO / Service / ErrorCode / 等）不受影响。

---

## 后端技术栈

- Java 17 + Spring Boot 3.3.5
- MyBatis-Plus 3.5.9（DAO + SqlSessionFactory 自动配置）
- MySQL 8.0 + HikariCP（连接池 pool size = 10）
- miniMax M3 / OpenAI-compatible chat completions（视觉多模态）
- OkHttp 4.12（HTTP 调用）
- springdoc-openapi 2.6 + Swagger UI
- JUnit 5 + Mockito + AssertJ（单元测试）

---

## 前端技术栈（待启动）

- React 18 + React Router
- ECharts / Recharts（图表）
- Axios（API 调用）
- Zustand（状态管理）

详见 `fincontrol-frontend/package.json`。

---

## 设计文档导航

| 文档 | 路径 | 说明 |
|------|------|------|
| Phase 0 决策 | [docs/phase-0/decisions.md](docs/phase-0/decisions.md) | 6 项硬约束 |
| Phase 0 API 契约 | [docs/phase-0/api-contract.md](docs/phase-0/api-contract.md) | 30+ 端点 |
| Phase 1 验收标准 | [docs/phase-1/acceptance-criteria.md](docs/phase-1/acceptance-criteria.md) | 47 项 + 18 项 P0 |
| 1a.2 子阶段计划 | [docs/phase-1/subphase-plan.md](docs/phase-1/subphase-plan.md) | 7+4 段子阶段 |
| 1a.2 测试教程 | [docs/phase-1/testing-guide-1a2.md](docs/phase-1/testing-guide-1a2.md) | curl + mvn test 教程 |

---

## Phase 1 启动检查清单

- [x] 0：基础设施搭建（1a.1 通过，`mvn test` 6/6）
- [x] 1a.2：截图解析 API 链（4 个端点 + P0-1.4 + P0-4.4 通过）
- [ ] 1a.3：快照入库（asset_raw / asset_snapshot / fund_category_map 三表事务）
- [ ] 1a.5 起：替换真实视觉模型调用 stub
- [ ] 1b.*：前端实现

---

## 本地开发快速启动

```cmd
:: 1. 启动 MySQL，建库 + 跑 schema
mysql -u root -p < docs/phase-0/db-schema.sql
mysql -u root -p < docs/phase-0/seed-data.sql

:: 2. 编辑 application-local.yml 填 miniMax Key
notepad fincontrol-backend\src\main\resources\application-local.yml

:: 3. 后端启动
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
