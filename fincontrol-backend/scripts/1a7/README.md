# 1a.7 端到端冒烟脚本

> 1a.7 〔冒烟 + 测试覆盖率〕实施脚本。
> 替身 = 真实 MySQL 8.0（Docker 容器）+ 真实 minimax vision + minimax text-only API。

---

## ⚠️ 安全第一：API Key 不入仓

所有脚本**绝不**打印、记录、提交任何 API key。脚本统一用 `${VAR:***}` 兜底，
即使变量未设也只显示 `***` 而非真实值。

| Key | 来源 | 用途 |
|---|---|---|
| `VISION_API_KEY` | `application-local.yml` `fincontrol.vision.api-key` | 1a.5 视觉模型（截图 parse） |
| `TEXT_AI_API_KEY` | `application-local.yml` `fincontrol.ai.text.api-key` | 1a.6 文本模型（chat send） |

> `application-local.yml` 已在 `.gitignore`；两个 key 仅在你本机可见。

---

## 前置依赖

| 工具 | 检查命令 | 1a.7 要求 |
|---|---|---|
| Docker | `docker --version` | 20.10+ |
| curl | `curl --version` | 7.x+ |
| jq | `jq --version` | 1.6+（可选；fallback 用 grep+cut） |
| Java JDK | `java --version` | 17 |
| Maven | `mvn --version` | 3.9+ |

> MySQL 由 Docker 提供（`mysql:8.0` 镜像），无需本地装 MySQL。

---

## 8 个脚本一览

| # | 脚本 | 用途 | 用时 |
|---|---|---|---|
| 0 | `lib-common.sh` | 共享 helper（脱敏 print / curl / SQL / JSON） | — |
| 1 | `01-up-mysql.sh` | Docker 启 MySQL + 建库 + 跑 schema + seed | 30s |
| 2 | `02-up-backend.sh` | 后台启 `mvn spring-boot:run` + 等 8080 | 25s |
| 3 | `03-smoke-1-screenshot.sh` | 4 张真实图全链路：upload→parse→confirm→balance | 60s |
| 4 | `04-smoke-2-chat.sh` | 5 个 chat 用例：投资类 / 闲聊 / 空 | 30s |
| 5 | `05-coverage.sh` | `mvn test` + `mvn jacoco:report` + Swagger 端点统计 | 60s |
| 6 | `99-cleanup.sh` | 关后端 + 删 MySQL 容器（可选项） | 10s |

---

## 执行顺序（推荐）

```bash
cd fincontrol-backend

# Step 0：确认 application-local.yml 已合并 fincontrol: 单块并填了 text key
cat src/main/resources/application-local.yml | head -30

# Step 1：启 MySQL
bash scripts/1a7/01-up-mysql.sh

# Step 2：后台启后端
bash scripts/1a7/02-up-backend.sh

# 等待后端启动完成（脚本会自检）
# Step 3：冒烟 1（截图）
bash scripts/1a7/03-smoke-1-screenshot.sh

# Step 4：冒烟 2（chat）
bash scripts/1a7/04-smoke-2-chat.sh

# Step 5：覆盖率 + Swagger
bash scripts/1a7/05-coverage.sh

# Step 6：清理（可选）
bash scripts/1a7/99-cleanup.sh
```

---

## 输出位置

- **stdout** — 实时显示
- **`docs/test-records/manual-tests/2026-07-17_phase1a7-smoke.log`** — 完整日志
- **`docs/test-records/api-test-output/2026-07-17_phase1a7_*.json`** — 每次 curl 的原始响应
- **`fincontrol-backend/target/site/jacoco/index.html`** — JaCoCo 覆盖率报告

---

## 失败处理

| 失败 | 行为 |
|---|---|
| minimax API 限流 / 失败 | 写错误进 chat_history；冒烟 2 走 garbage_loop 兜底；脚本不退出 |
| MySQL Docker 不可用 | 报清晰错误，退出 |
| JaCoCo < 60% | 写报告 "覆盖率不足"，**不**退出（让人判断是否补 case） |
| 后端启动超时（>60s） | 报日志最后 30 行，退出 |

---

## 风险与停止条件

| 风险 | 应对 |
|---|---|
| 网络不稳（minimax 限流） | 重试 1 次；仍失败则降级为"冒烟 1 完整 + 冒烟 2 partial" |
| Docker 在 Windows 跑不起来 | 改用本地 MySQL（修改脚本 MYSQL_HOST 为 localhost） |
| 4 张真实图 minimax 解析失败 | README 指引回退到第 4 张图 |
| 1a.7-PRE dialect 修复有遗漏 | 冒烟 1 confirm 步骤会爆错；先回滚 dialect 修复 |

---

## 与既有文档的衔接

- 工作计划：`docs/phase-1/work-plans/2026-07-17_phase1a7-work-plan.md`
- 验收计划：`docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-plan.md`
- 验收报告（执行后生成）：`docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md`
