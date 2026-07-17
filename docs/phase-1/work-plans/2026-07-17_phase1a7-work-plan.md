# Phase 1a.7 冒烟 + 测试覆盖率 工作计划

**计划日期**：2026-07-17
**子阶段**：整体 Phase 1a.7 〔冒烟 + 测试覆盖率〕
**负责人**：刘博丞
**状态**：`GATE0_PASS`
**配套验收计划**：[`2026-07-17_phase1a7-acceptance-plan.md`](../../test-records/manual-tests/2026-07-17_phase1a7-acceptance-plan.md)
**总进度清单**：[`phase-1a.md`](../checklists/phase-1a.md)
**前置报告**：[`2026-07-17_phase1a6-acceptance-report.md`](../../test-records/manual-tests/2026-07-17_phase1a6-acceptance-report.md)
**关键设计依据**：[`docs/SETUP.md`](../../SETUP.md)（MySQL 初始化、API Key 配置）

> 本计划先冻结工作范围和测试边界，再开始执行。冒烟是 1a.3 / 1a.4 / 1a.5 / 1a.6 业务层闭环后的**真实 MySQL + 真实 API** 端到端验收。

---

## Gate 0 冻结结果（2026-07-17）

### 关键决策

| 决策 | 结论 |
|---|---|
| **1a.7-PRE 必做** | 修 `FundCategoryMapMapper.xml` 的 dialect bug（`ON CONFLICT DO UPDATE` → `MERGE INTO`） |
| MySQL 启动方式 | **Docker 自管容器**（mysql:8.0，端口 3306，root/root，TZ=Asia/Shanghai） |
| 真实测试数据 | 4 张真实支付宝截图（`fincontrol-backend/uploads/samples/phase1a2-alipay-fund-list-20260715-2355-{1,2,3,1}.jpg`） |
| minimax text API key 保管 | **不入仓 / 不入 Cline 输入**；用户写入 `application-local.yml`（gitignored）或设 `TEXT_AI_API_KEY` 环境变量 |
| 业务层单测是否重跑 | **不重跑**（已 151/151 PASS 且 0 回归） |
| 替身策略 | **真实 MySQL + 真实 minimax**（替代之前 Service mock / H2 路径） |
| JaCoCo 覆盖范围 | 全 module（含 1a.2-1a.6 既有测试） |
| 输出 | 脚本 `fincontrol-backend/scripts/1a7/*.sh` + 报告 `docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md` |
| 失败处理 | 不掩盖：写错误码进 chat_history + 报告写 "PRODUCTION_FAIL" 段；不回退 mock |
| 是否新增 ErrorCode | **不新增**（沿用 1001/2001/3001/3002/5001/5002） |

### 切片切分

| 切片 | 范围 | 验证点 |
|---|---|---|
| **0（前置）** | 修 dialect bug | `mvn test` 151/151 仍 PASS |
| **Step 1** | MySQL Docker 启 + 建库 + 跑 db-schema.sql + seed | `SHOW TABLES` 7 张 |
| **Step 2** | 后端 `mvn spring-boot:run` 后台启 + 等 8080 | `actuator/health` UP |
| **Step 3** | 冒烟 1：截图上传 → 解析 → 入库 → balance（用 4 张真实图） | 真表 upsert + 6 大类 |
| **Step 4** | 冒烟 2：chat send 5 个文本用例 | main_loop / garbage_loop 真实 AI |
| **Step 5** | Swagger UI 端点数 | ≥ 24 |
| **Step 6** | JaCoCo 覆盖率 | ≥ 60% line |
| **Step 7** | 真库 SQL 验证 | chat_history / asset_raw / fund_category_map |
| **Step 8** | 验收报告 | 5 段式判定 |

---

## 1. 目标

按 [`docs/phase-1/subphase-plan.md §2.7`](#)：

- **冒烟 1** — 截图上传 → 解析 → 大类确认 → 入库 → 首页展示（[P0-1.4] 不阻塞冒烟路径）
- **冒烟 2** — AI 顾问基础对话多轮（投资类 + 闲聊 + 边界）
- **1a.24** — 单元测试覆盖率 ≥ 60%
- **Swagger** — 24+ 端点全部可见

完成后 Phase 1a 后端**可宣告完成**，Phase 1b 前端可启动。

## 2. 范围

| 端点 / 组件 | 用途 |
|---|---|
| MySQL 8.0 Docker 容器 | 真实持久化层 |
| `mvn spring-boot:run` | 真实后端启动 |
| 4 张真实支付宝截图 | 冒烟 1 输入数据 |
| 5 个合成文本用例 | 冒烟 2 输入数据 |
| Swagger UI `/swagger-ui.html` | 端点完整性 |
| JaCoCo 报告 `target/site/jacoco/index.html` | 覆盖率证据 |
| `chat_history` / `asset_raw` / `asset_snapshot` / `fund_category_map` 表 | 真库持久化证据 |

## 3. 非目标

- 不做前端 E2E（Phase 1b 阶段）；
- 不做单元测试增补（已有 151/151 PASS，且本次覆盖率不够时再补 5-10 个边界 case）；
- 不做 minimax 模型切换 / prompt 调优；
- 不 bump 任何 prompt 版本（v1.0 已稳定）；
- 不重写任何 1a.2-1a.6 已通过的测试代码（业务层 mock 测试已 PASS）；
- 不使用 H2 内存库（必须真实 MySQL 才能验证 `MERGE INTO` / `GROUP BY` / `DELETE` 行为）；
- 不重新评估 1a.3 dialect 设计（`1a3-dedup-strategy.md` 已记录，本轮仅修代码）。

## 4. 当前基线

### 4.1 已有基础（来自 1a.5 / 1a.6 验收）

| 资产 | 状态 |
|---|---|
| `mvn test` 151/151 PASS（业务层 mock） | ✅ 2026-07-17 |
| `chat_history` 表 + 6 类实体 + 4 Mapper | ✅ 1a.2 建好 |
| `prompt_versions` 表 + 3 行 v1.0 种子 | ✅ 1a.2 种子 |
| `application-local.yml`（gitignored，含 VISION_API_KEY + TEXT_AI_API_KEY） | ✅ 1a.2 / 1a.6 |
| `fincontrol-backend/uploads/samples/` 4 张真实截图 | ✅ 用户手动放 |
| Swagger UI 已配置 | ✅ 1a.1 |
| JaCoCo Maven plugin | ✅ 1a.1 |
| ⚠️ `FundCategoryMapMapper.xml` `upsertByFundName` | 🐛 PostgreSQL `ON CONFLICT` 语法，H2 + MySQL **均不工作**（1a.3 残留） |

### 4.2 预计新增

| 路径 | 角色 |
|---|---|
| `fincontrol-backend/src/main/resources/mapper/FundCategoryMapMapper.xml`（修改） | `upsertByFundName` 改 `MERGE INTO` |
| `fincontrol-backend/scripts/1a7/README.md` | 执行说明 + 安全脱敏说明 |
| `fincontrol-backend/scripts/1a7/lib-common.sh` | 共享 helper（脱敏 print、curl wrapper、SQL 查询） |
| `fincontrol-backend/scripts/1a7/01-up-mysql.sh` | Docker 启 MySQL + 建库 + 跑 schema |
| `fincontrol-backend/scripts/1a7/02-up-backend.sh` | 后台启后端 + 等 8080 |
| `fincontrol-backend/scripts/1a7/03-smoke-1-screenshot.sh` | 冒烟 1 |
| `fincontrol-backend/scripts/1a7/04-smoke-2-chat.sh` | 冒烟 2 |
| `fincontrol-backend/scripts/1a7/05-coverage.sh` | JaCoCo 覆盖率 |
| `fincontrol-backend/scripts/1a7/99-cleanup.sh` | 关停 + 删 MySQL 容器 |
| `docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md` | 验收报告 |
| `docs/test-records/api-test-output/2026-07-17_phase1a7_*.json` | 每次 curl 的原始响应 |

### 4.3 application-local.yml 追加（**用户手动改，不入仓**）

```yaml
fincontrol:
  ai:
    text:
      api-key: sk-api-你的-text-key-粘贴到这里
```

> 该文件已在 `.gitignore`，**不会**被 commit / push。

## 5. 切片切分（详细）

| # | 切片 | 文件 | 验证命令 | 风险 |
|---|---|---|---|---|
| 0 | 修 dialect | `FundCategoryMapMapper.xml` 改 1 行 | `mvn test` 151/151 PASS | MERGE INTO 语法差异 |
| 1 | MySQL Docker 启 | `01-up-mysql.sh` | `docker ps` 看到 mysql:8.0；`mysql -e 'SHOW TABLES'` 返回 7 行 | Docker 不可用 |
| 2 | 后端启动 | `02-up-backend.sh` | `curl /actuator/health` UP | 启动慢 / 编译错 |
| 3 | 冒烟 1 | `03-smoke-1-screenshot.sh` | curl upload→parse→confirm→balance 4 步全 200；`SELECT COUNT(*)` 真实落库 | 视觉模型升级导致解析失败 |
| 4 | 冒烟 2 | `04-smoke-2-chat.sh` | curl chat/send 5 个用例；验证 routedTo；查 chat_history 落库 | minimax text 限流 |
| 5 | Swagger 端点数 | `04-smoke-2-chat.sh` 内嵌 | `curl /v3/api-docs \| jq '.paths \| length'` ≥ 24 | 漏挂载 |
| 6 | JaCoCo 覆盖率 | `05-coverage.sh` | `mvn -B test` 后 `target/site/jacoco/index.html`；line ≥ 60% | 覆盖率不够 |
| 7 | 真库 SQL 验证 | `05-coverage.sh` 内嵌 | 关键表 SELECT COUNT 数量对 | 异步落库延迟 |
| 8 | 验收报告 | 手写 | 5 段式判定 | — |

## 6. 测试与替身策略（冻结）

| 层级 | 替身 | 目标 |
|---|---|---|
| **端到端冒烟** | **真实 MySQL 8.0 + 真实 minimax vision + minimax text-only** | 验证真实 SQL 行为 + 真实 AI 响应延迟 + 真表落库 |
| **JaCoCo 覆盖率** | （运行既有 1a.2-1a.6 全部单测） | 统计 line / branch / class 覆盖率 |
| **业务层单测** | **不重跑**（1a.5 / 1a.6 闭环） | 维持 0 回归 |

> 禁止在失败时把真实 MySQL 切回 H2 / mock 来"消除失败"。

## 7. 风险与停止条件

| 风险 | 处理 |
|---|---|
| **dialect 修复引入新 bug** | 业务层 mock 测试 151 用例会立刻捕捉；如出问题回滚 1 commit |
| Docker 容器在 Windows 跑不起来 | 回退：让你 `net start mysql` 启本地服务；脚本改用 `127.0.0.1:3306` |
| minimax text key 余额 / 限流 | 失败 → chat_history 写错误码进表，仍能验证写库 + 路由判断 |
| minimax vision 模型升级导致截图解析失败 | README 已说明：自动回退到第 4 张图兜底重试 |
| 启动慢 / 编译错误 | `mvn clean install -DskipTests` 预热；输出有详细 stack |
| 覆盖率 < 60% | 优先补 5-10 个边界 case（ChatService 异常路径 / ConversationService 边界 / Controller 错误码） |
| 真实截图含个人敏感信息 | 已在 `samples/README.md` 标注脱敏 SOP；本目录 `.gitignore` 不入仓 |
| MySQL 8.0.20 以下版本不支持 MERGE INTO | db-schema.sql §1 已要求 MySQL 8.0+；Docker 用 `mysql:8.0` 镜像保证 ≥ 8.0.20 |

## 8. 计划完成标准

- 1a.7-PRE dialect 修复 1 commit；
- 8 个脚本就绪（lib-common + 7 个 step）；
- 冒烟 1 + 冒烟 2 全部通过（基于真实 MySQL + 真实 minimax）；
- Swagger 端点数 ≥ 24；
- JaCoCo 覆盖率 ≥ 60%；
- 验收报告（5 段式判定）就绪；
- `phase-1a.md` checklist 勾选 `1a.24`（覆盖率）+ 冒烟相关项；
- 1a.7 PRODUCTION_PENDING → PASS（与 1a.3-1a.6 共同 gate 转 PASS）；
- 3-4 个 commit 推到远端。

## 9. 关联文档

- 1a.7 验收计划：`docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-plan.md`
- 1a.6 前置：`docs/test-records/manual-tests/2026-07-17_phase1a6-acceptance-report.md`
- 1a.5 前置：`docs/test-records/manual-tests/2026-07-17_phase1a5-acceptance-report.md`
- 1a.3 dialect 设计：`docs/phase-1/designs/1a3-dedup-strategy.md §10`
- 1a.2 测试教程 + sample 用法：`docs/SETUP.md` + `fincontrol-backend/uploads/samples/README.md`
- 总账：`docs/phase-1/checklists/phase-1a.md`
