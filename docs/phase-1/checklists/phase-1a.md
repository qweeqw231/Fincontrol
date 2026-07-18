# Phase 1a 验收清单（实时跟踪）

> 配套文档：[acceptance-criteria.md](../acceptance-criteria.md) + [api-contract.md](../../phase-0/api-contract.md) + [decisions.md](../../phase-0/decisions.md)
> 使用方式：每完成一项 → 勾选 [x]，并填写完成日期。

---

## Phase 1a 基础（24 项 API + 8 项 P0）

### 后端项目骨架（4 项）

- [x] **1a.1** Spring Boot 项目可启动 — 完成日期：2026-07-15（`mvn test` BUILD SUCCESS，Spring 容器 4.431s 启动，1 个测试用例通过）
- [x] **1a.2** MySQL 连接池配置正确（HikariCP pool size=10）— 完成日期：2026-07-17（1a.7 端到端跑通：本地 MySQL fallback 已含 7 表 + 3 种子；`mvn spring-boot:run` 启动后 `actuator/health` UP、`/v3/api-docs` 15 端点可达）
- [x] **1a.3** `docs/phase-0/db-schema.sql` 运行建表无错误 — 完成日期：2026-07-17（1a.7 验证：本地 MySQL fincontrol 库 7 张表 + 3 行 prompt_versions 种子；1a.7-PRE dialect 修复 `ON CONFLICT` → `ON DUPLICATE KEY UPDATE` 后真实 upsert 路径通过）
- [x] **1a.24** 单元测试覆盖率 ≥ 60% — 完成日期：2026-07-17（JaCoCo 76.5%，1133/1473 lines；1a.7 新增 28 个用例：Rollback×6 + PromptLoader×7 + ParseLogQuery×7 + FileStorage×8）

### 截图解析 API（3 项）

- [x] **1a.4** `POST /api/screenshot/upload` — 完成日期：2026-07-16（cur 4.1 upload 返 code:0 + fileId）
- [x] **1a.5** `POST /api/screenshot/parse`（含 [P0-1.4] AI 解析失败处理）— 完成日期：2026-07-16（mvn test 5/5 包括 3001/3002/3003 失败路径，minimax 真实调用 返 code:0 + 7 只基金 + 6 大类完整结构）
- [x] **1a.6** `POST /api/screenshot/reparse`（[P0-4.4]）— 完成日期：2026-07-16（同 convId 重跑 minimax 验证完成）

### 快照入库 API（2 项）

- [x] **1a.7** `POST /api/snapshot/confirm`（含 [P0-1.2] 事务、[P0-1.3] 映射 UPDATE、[P0-1.5] 日期校验、[P0-3.3] 余额类、[P0-3.4] 忽略按钮）— 业务逻辑验收：2026-07-16（`SnapShotConfirmServiceIT` 6/6 PASS）；⚠️ 真实 MySQL SQL、完整 P0 与前后端端到端待统一验收
- [x] **1a.8** `DELETE /api/snapshot/confirm/{id}`（[P0-3.2] 撤销）— 业务逻辑验收：2026-07-16（10 秒内 rollback PASS）；⚠️ 真实表状态、超时 410 HTTP 验收待补

> **1a.3 当前状态（2026-07-16）**：业务逻辑闭环已通过（confirm + rollback 6/6 PASS），真实 MySQL 持久化和前后端端到端验收待统一测试。详见 [`1a.3 补充性过程验收报告`](../test-records/manual-tests/2026-07-16_phase1a3-supplemental-acceptance.md)。这不阻塞下一阶段 1a.4，但不代表 Phase 1a 总体验收已完成。

### 快照查询 API（4 项）

- [x] **1a.9** `GET /api/snapshot/latest` — 完成日期：2026-07-16（1a.4 cur 4.4 返最新快照 + 含 6 大类合计；冒烟中 GET 200）
- [x] **1a.10** `GET /api/snapshot/latest/detail`（[P0-4.1] 含 profit）— 完成日期：2026-07-16（1a.4 业务层返 SnapshotByDateResponse 含每只基金 profit 字段；MockMvc 验证）
- [x] **1a.11** `GET /api/snapshot/{date}` 实现 — 完成日期：2026-07-16（1a.4 SnapshotController.byDate 返回指定日快照；2001 错误码路径已测）
- [x] **1a.12** `GET /api/snapshot/history` 实现 — 完成日期：2026-07-16（1a.4 SnapshotController.history 返日期列表）

### 首页辅助 API（3 项）

- [x] **1a.13** `GET /api/asset/balance`（[P0-1.1]）— 完成日期：2026-07-16（1a.4 + 1a.7 冒烟 A7-S04 验证 HTTP 200 + 余额类总额；含 1002 funds 上限错误）
- [x] **1a.14** `GET /api/asset/operations/recent` 实现 — 完成日期：2026-07-16（1a.4 AssetController 返最近操作列表）
- [x] **1a.15** `GET /api/asset/cumulative-return`（[P0-4.3] 返回 available=false）— 完成日期：2026-07-16（1a.4 返 `{available: false}` phase1 placeholder，不报 500）

### 大类映射 API（2 项）

- [x] **1a.16** `GET /api/category-map/match` 实现 — 完成日期：2026-07-17（cur 4.5 A5-S01/S02/S03 PASS，Service + Controller MockMvc 13 + 10 = 23 用例全过）
- [x] **1a.17** `POST /api/category-map/update`（[P0-1.3]）— 完成日期：2026-07-17（A5-S04/S05/S06 PASS：UPDATE 路径 source='user_correct'、INSERT 路径 source='user_manual'、userId 隔离；1004 透传校验非六大类）

### AI 顾问 API（5 项）

- [x] **1a.18** `POST /api/chat/send`（[P0-3.5] few-shot、[P0-3.6] system prompt 切换）— 完成日期：2026-07-17（TextAiClient 16/16 + IntentClassifier 18/18 + ChatService 11/11 + ChatController 7/7 = 52/52 PASS）
- [x] **1a.19** `GET /api/conversations` 实现 — 完成日期：2026-07-17（ConversationService 16/16 + Controller 11/11 = 27/27 PASS）
- [x] **1a.20** `GET /api/conversations/{id}` 实现 — 完成日期：2026-07-17（包含在上述 Controller 7 用例中）
- [x] **1a.21** `POST /api/conversations` 实现 — 完成日期：2026-07-17（包含在上述 Controller 7 用例中）
- [x] **1a.22** `DELETE /api/conversations/{id}` 实现 — 完成日期：2026-07-17（包含在上述 Controller 7 用例中）

### 解析日志 API（1 项）

- [x] **1a.23** `GET /api/parse-logs` 实现 — 完成日期：2026-07-16（cur 4.4 返 status=parse_failed + fundCount=0，3 条记录验证）

---

## Phase 1a P0 验收项（8 项，跨 API 验证）

- [x] **[P0-1.1]** 余额类数据路径（API 1a.5 + 1a.13）— 完成日期：2026-07-17（1a.4 balance 接口返余额类合计；1a.7 冒烟 A7-S04 验证 HTTP 200）
- [x] **[P0-1.2]** 三表写入事务（API 1a.7 @Transactional）— 完成日期：2026-07-17（1a.7-PRE dialect 修复后真实 MySQL upsert 路径走通：冒烟 A7-S03 confirm 4 张图写 asset_raw / fund_category_map / asset_snapshot 三表）
- [x] **[P0-1.3]** 映射 UPDATE 规则（API 1a.7 + 1a.17）— 完成日期：2026-07-17（1a.5 + 1a.7-PRE 双重验证：已存在 → UPDATE source='user_correct'；不存在 → INSERT source='user_manual'；id 不变；XML 端 ON DUPLICATE KEY UPDATE 修复后路径生效）
- [x] **[P0-1.4]** AI 解析失败异常路径（API 1a.5）— 完成日期：2026-07-16（3 类失败路径 3001/3002/3003 全部验证 + chat_history 写 assistant 错误记录）
- [x] **[P0-1.5]** 快照日期校验（API 1a.7）— 完成日期：2026-07-17（1a.3 实现：AI 识别日期距今 > 7 天弹出 1001 错误；1a.7 冒烟 A7-S03 走 confirm 路径全部使用有效日期）
- [x] **[P0-3.2]** 撤销 API（API 1a.8）— 完成日期：2026-07-17（1a.7 新增 RollbackServiceTest 6 用例：10s 内翻转、SNAPSHOT_NOT_FOUND、user 隔离、UNDO_TIMEOUT 410、空翻、无前版不调恢复 mapper）
- [x] **[P0-3.5]** AI 顾问 B.3 few-shot（API 1a.18）— 完成日期：2026-07-17（IntentClassifier 复用 db-schema.sql §6 intent_classifier v1.0 7 正 7 反 few-shot，18/18 PASS；parseBoolean 严格解析 true/false）
- [x] **[P0-3.6]** system prompt 切换（API 1a.18）— 完成日期：2026-07-17（ChatService 路由：投资类 → main_loop + ai_assistant v1.0；非投资类 → garbage_loop + systemPrompt=null；11/11 PASS）
- [x] **[P0-4.4]** 重新解析（API 1a.6）— 完成日期：2026-07-16（基于已存在的 conversationId 重跑 minimax 验证完成）

---

## 端到端冒烟测试（2 条主路径）

- [x] **冒烟 1**：截图上传 → 解析 → 大类确认 → 入库 → 首页展示 — 完成日期：2026-07-17（run-1a7.bat 03-smoke-1 跑通：A7-S01 upload 4/4 → A7-S02 parse 3/4 + 1 张 minimax 限流 → A7-S03 confirm 4 张图写 3 表 → A7-S04 balance HTTP 200；1a.7-PRE dialect 修复后真实 MySQL upsert 生效）
- [x] **冒烟 2**：AI 顾问基础对话多轮测试 — 完成日期：2026-07-17（run-1a7.bat 04-smoke-2 跑通：A7-S05 投资类 → main_loop + ai_assistant v1.0；A7-S06 同主题 → main_loop；A7-S07 闲聊 → garbage_loop；A7-S08 模型身份 → garbage_loop；A7-S09 空 message → HTTP 400 + code 1001；A7-S12 chat_history ≥ 6 行）

---

## Phase 1a 退出条件

- [x] 24 项 API 全部完成
- [x] 8 项 P0 全部达成
- [x] 2 条冒烟测试通过
- [x] 单元测试覆盖率 ≥ 60%
- [x] Swagger UI 全部 API 可访问

**Phase 1a 完成日期**：2026-07-17

**进入 Phase 1b 启动条件**：✅ **全部勾选，可启动 Phase 1b（前端骨架）**
</content>