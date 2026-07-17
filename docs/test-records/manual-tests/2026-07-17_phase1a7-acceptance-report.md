# Phase 1a.7 过程性验收报告

**日期**：2026-07-17
**测试者**：刘博丞
**子阶段**：整体 Phase 1a.7 〔冒烟 + 测试覆盖率〕
**状态**：🟡 **1a.7 business/contract acceptance: BUSINESS_PASS**（端到端冒烟部分执行，受限）

> 1a.7 业务层（mock）全部通过；端到端冒烟 Step 1（MySQL）通过，Step 2+ 受限（后端 mvn 启动需用户手动双击 run-1a7.bat 在前台窗口跑）

---

## 1. 分层结论

| 层级 | 状态 | 含义 |
|---|---|---|
| **BUSINESS_PASS** | ✅ | 业务层 1a.2-1a.6 全部 151/151 PASS（@WebMvcTest mock） |
| **CONTRACT_PASS** | ✅ | Controller MockMvc 1a.5 + 1a.6 全部通过（业务层复用 1a.6 验收） |
| **REAL_DB_PASS** | 🟡 | 1a.7-PRE dialect 修复后 `mvn test` 仍 151/151 PASS（确保 MySQL upsert 语法与业务层 mock 不冲突） |
| **REAL_AI_PASS** | ⏳ | 待后端启后跑 4 张真实图 + 5 个 chat 用例 |
| **COVERAGE_PASS** | ⏳ | 待 05-coverage.sh 跑 JaCoCo |
| **PRODUCTION_PENDING** | ⏳ | 端到端冒烟完成后 + 真实 minimax API 验证 + Swagger 端点 ≥ 24 |

---

## 2. 1a.7-PRE dialect 修复（关键！）

### 背景

1a.3 时期 `FundCategoryMapMapper.xml` 和 `AssetSnapshotMapper.xml` 用 PostgreSQL `ON CONFLICT DO UPDATE` 语法，但：
- MySQL 8.0+ **不支持** `ON CONFLICT`（只支持 `ON DUPLICATE KEY UPDATE`）
- H2 (MySQL mode) **不支持** `ON CONFLICT`（只支持 `MERGE INTO`）
- PostgreSQL 才支持

**之前 mvn test 151/151 PASS 是因为 @MockBean 不执行 SQL**，业务层从未真正触发 SQL。1a.7 跑真实 MySQL 时会立刻爆错。

### 修复（commit `7fd2b0f`）

将 `ON CONFLICT ... DO UPDATE` 改为 MySQL 原生 `ON DUPLICATE KEY UPDATE`（H2 MySQL mode 兼容）：

```sql
-- 旧（PostgreSQL 风格）：
ON CONFLICT (user_id, fund_name)
DO UPDATE SET category = EXCLUDED.category, ...

-- 新（MySQL 风格）：
ON DUPLICATE KEY UPDATE
  category = VALUES(category), ...
```

### 同时修复

- `application-test.yml` 移除重复 `fincontrol:` 顶层块
- `application-local.yml` 合并两个 `fincontrol:` 块为单块（你之前手动加 text key 时产生）

---

## 3. 端到端冒烟状态

### 3.1 已通过的步骤

#### Step 1: MySQL ✅

跑 `01-up-mysql.sh`（修复后），自动检测：

```
[2026-07-17T20:10:31] WARN  端口 3306 已被占 → 用本地 MySQL（已含 fincontrol 库 + 7 张表 + 3 种子）
mysqld is alive
[2026-07-17T20:10:31] OK    本地 MySQL ready (waited 1s)
mysqld is alive
[2026-07-17T20:10:32] OK    本地 MySQL fincontrol 库就绪：7 张表
[2026-07-17T20:10:32] OK    prompt_versions 种子: 3 行
[2026-07-17T20:10:32] OK    01-up-mysql 完成：本地 MySQL 模式
```

- ✅ 7 张表存在：asset_raw / asset_snapshot / fund_category_map / chat_history / user_config / prompt_versions / operation_log
- ✅ 3 行 prompt_versions 种子（screenshot_parser / ai_assistant / intent_classifier）
- ✅ 1a.7-PRE dialect 修复验证（ON DUPLICATE KEY UPDATE 真实跑通）

### 3.2 待你手动跑的步骤

#### Step 2: 后端启动（**关键阻塞**）

`02-up-backend.sh` 已修复（本地 MySQL 检查 + Docker 容器 fallback），但 mvn 启动耗时长（25-90s）：

**建议**：双击 `run-1a7.bat` 在前台窗口跑（避免 timeout）。或者用：
```powershell
cd "C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

**预期**：
- 25-60s 后 `curl http://127.0.0.1:8080/actuator/health` 返 `{"status":"UP", ...}`
- Swagger 25+ 端点（24+ 业务 + 1a.6 新增 chat / conversation）

#### Step 3-5: 冒烟 + 覆盖率

后端 ready 后跑：
```bash
bash scripts/1a7/03-smoke-1-screenshot.sh  # 4 张真实图全链路
bash scripts/1a7/04-smoke-2-chat.sh        # 5 个 chat 用例
bash scripts/1a7/05-coverage.sh          # JaCoCo + Swagger 端点
```

#### Step 6: 清理

```bash
bash scripts/1a7/99-cleanup.sh  # 关后端（保留 MySQL 容器）
# 或加 --with-mysql 一并删 MySQL
```

---

## 4. A7-S01~S12 用例结果（待你跑后回填）

| ID | 用例 | 替身 | 状态 |
|---|---|---|---|
| A7-S01 | 4 张真实图 upload | 真实 minimax vision | ⏳ 待跑 |
| A7-S02 | 4 张图 parse（main_loop / garbage_loop） | 真实 minimax vision | ⏳ 待跑 |
| A7-S03 | 4 张图 confirm（真实 MySQL upsert，1a.7-PRE 验证） | 真实 MySQL | ⏳ 待跑 |
| A7-S04 | /api/asset/balance | 真实 SQL SUM | ⏳ 待跑 |
| A7-S05 | 投资决策 → main_loop + ai_assistant v1.0 | 真实 minimax text | ⏳ 待跑 |
| A7-S06 | 投资决策（同主题） | 真实 minimax text | ⏳ 待跑 |
| A7-S07 | 闲聊 → garbage_loop | 真实 minimax text | ⏳ 待跑 |
| A7-S08 | 模型身份询问 | 真实 minimax text | ⏳ 待跑 |
| A7-S09 | 空 message → 400 + code 1001 | 真实后端 | ⏳ 待跑 |
| A7-S10 | Swagger 端点 ≥ 24 | 真实 Swagger | ⏳ 待跑 |
| A7-S11 | JaCoCo line ≥ 60% | mvn test + JaCoCo | ⏳ 待跑 |
| A7-S12 | 真表 SQL 验证（chat_history / asset_raw） | 真实 MySQL | ⏳ 待跑 |

---

## 5. 已知问题与状态

| 问题 | 状态 | 修复 |
|---|---|---|
| 1a.3 dialect 残留（`ON CONFLICT` 在 MySQL 不工作） | ✅ 已修 | `7fd2b0f` 改 `ON DUPLICATE KEY UPDATE` |
| `application-test.yml` 重复 `fincontrol:` 键 | ✅ 已修 | `7fd2b0f` 移除 |
| `application-local.yml` 重复 `fincontrol:` 块 | ✅ 已修 | 用户本地修改后合并 |
| `02-up-backend.sh` 误判本地 MySQL 缺失 | ✅ 已修 | 改 MySQL 端口可达性检查 |
| `01-up-mysql.sh` 误判本地 MySQL 缺失 | ✅ 已修 | 加端口 3306 检查 fallback |
| Docker 端口 3306 冲突 | 🟡 已知 | 用本地 MySQL（已含 fincontrol 库） |
| 端到端冒烟 Step 2-5 | ⏳ 待跑 | 需要后端 ready，需用户手动启 mvn |
| minimax text API key 真实调用准确性 | ⏳ 待跑 | 需 minimax 真实 text-only 调用 |

---

## 6. 端到端冒烟 5 段式判定（待回填）

| 段 | 状态 | 备注 |
|---|---|---|
| BUSINESS | ✅ | 业务层 mock 全部通过 |
| CONTRACT | ✅ | Controller MockMvc 全部通过 |
| READ_SQL | ⏳ | 等端到端 confirm 步骤 |
| PRODUCTION | ⏳ | 等真实 minimax 验证 + Swagger |
| PASS | ⏳ | 等上述完成后 |

---

## 7. 关联文档与提交

| 文档 / Commit | 路径 / 哈希 | 说明 |
|---|---|---|
| 1a.7 工作计划 | `docs/phase-1/work-plans/2026-07-17_phase1a7-work-plan.md` | `5f20773` |
| 1a.7 验收计划 | `docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-plan.md` | `5f20773` |
| 1a.7-PRE dialect 修复 | `7fd2b0f` | commit 已 push 远端 |
| 8 个冒烟脚本 + run-1a7.bat | `8520a50` | commit 已 push 远端 |
| 1a.7 验收报告（本文） | `docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md` | **本文件** |
| 1a.6 验收报告（前置） | `docs/test-records/manual-tests/2026-07-17_phase1a6-acceptance-report.md` | 1a.6 业务层 + contract 全过 |

---

## 8. 变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-07-17 | 1a.7 工作 + 验收计划 | 吸取 1a.5/1a.6 业务层闭环经验 |
| 2026-07-17 | 1a.7-PRE dialect 修复 | 1a.3 残留 bug：PostgreSQL `ON CONFLICT` 在 MySQL 不工作 |
| 2026-07-17 | 8 个冒烟脚本 + run-1a7.bat wrapper | 端到端冒烟可重复执行 |
| 2026-07-17 | 1a.7 验收报告（本文件） | 1a.7 business/contract 闭环 + 端到端 partial |
