# Phase 1a 验收清单（实时跟踪）

> 配套文档：[acceptance-criteria.md](../acceptance-criteria.md) + [api-contract.md](../../phase-0/api-contract.md) + [decisions.md](../../phase-0/decisions.md)
> 使用方式：每完成一项 → 勾选 [x]，并填写完成日期。

---

## Phase 1a 基础（24 项 API + 8 项 P0）

### 后端项目骨架（4 项）

- [x] **1a.1** Spring Boot 项目可启动 — 完成日期：2026-07-15（`mvn test` BUILD SUCCESS，Spring 容器 4.431s 启动，1 个测试用例通过）
- [ ] **1a.2** MySQL 连接池配置正确（HikariCP pool size=10）— 完成日期：____
- [ ] **1a.3** `docs/phase-0/db-schema.sql` 运行建表无错误 — 完成日期：____
- [ ] **1a.24** 单元测试覆盖率 ≥ 60% — 完成日期：____

### 截图解析 API（3 项）

- [ ] **1a.4** `POST /api/screenshot/upload` — 完成日期：____
- [ ] **1a.5** `POST /api/screenshot/parse`（含 [P0-1.4] AI 解析失败处理）— 完成日期：____
- [ ] **1a.6** `POST /api/screenshot/reparse`（[P0-4.4]）— 完成日期：____

### 快照入库 API（2 项）

- [ ] **1a.7** `POST /api/snapshot/confirm`（含 [P0-1.2] 事务、[P0-1.3] 映射 UPDATE、[P0-1.5] 日期校验、[P0-3.3] 余额类、[P0-3.4] 忽略按钮）— 完成日期：____
- [ ] **1a.8** `DELETE /api/snapshot/confirm/{id}`（[P0-3.2] 撤销）— 完成日期：____

### 快照查询 API（4 项）

- [ ] **1a.9** `GET /api/snapshot/latest` — 完成日期：____
- [ ] **1a.10** `GET /api/snapshot/latest/detail`（[P0-4.1] 含 profit）— 完成日期：____
- [ ] **1a.11** `GET /api/snapshot/{date}` 实现 — 完成日期：____
- [ ] **1a.12** `GET /api/snapshot/history` 实现 — 完成日期：____

### 首页辅助 API（3 项）

- [ ] **1a.13** `GET /api/asset/balance`（[P0-1.1]）— 完成日期：____
- [ ] **1a.14** `GET /api/asset/operations/recent` 实现 — 完成日期：____
- [ ] **1a.15** `GET /api/asset/cumulative-return`（[P0-4.3] 返回 available=false）— 完成日期：____

### 大类映射 API（2 项）

- [ ] **1a.16** `GET /api/category-map/match` 实现 — 完成日期：____
- [ ] **1a.17** `POST /api/category-map/update`（[P0-1.3]）— 完成日期：____

### AI 顾问 API（5 项）

- [ ] **1a.18** `POST /api/chat/send`（[P0-3.5] few-shot、[P0-3.6] system prompt 切换）— 完成日期：____
- [ ] **1a.19** `GET /api/conversations` 实现 — 完成日期：____
- [ ] **1a.20** `GET /api/conversations/{id}` 实现 — 完成日期：____
- [ ] **1a.21** `POST /api/conversations` 实现 — 完成日期：____
- [ ] **1a.22** `DELETE /api/conversations/{id}` 实现 — 完成日期：____

### 解析日志 API（1 项）

- [ ] **1a.23** `GET /api/parse-logs` 实现 — 完成日期：____

---

## Phase 1a P0 验收项（8 项，跨 API 验证）

- [ ] **[P0-1.1]** 余额类数据路径（API 1a.5 + 1a.13）— 完成日期：____
- [ ] **[P0-1.2]** 三表写入事务（API 1a.7 @Transactional）— 完成日期：____
- [ ] **[P0-1.3]** 映射 UPDATE 规则（API 1a.7 + 1a.17）— 完成日期：____
- [ ] **[P0-1.4]** AI 解析失败异常路径（API 1a.5）— 完成日期：____
- [ ] **[P0-1.5]** 快照日期校验（API 1a.7）— 完成日期：____
- [ ] **[P0-3.2]** 撤销 API（API 1a.8）— 完成日期：____
- [ ] **[P0-3.5]** AI 顾问 B.3 few-shot（API 1a.18）— 完成日期：____
- [ ] **[P0-3.6]** system prompt 切换（API 1a.18）— 完成日期：____

---

## 端到端冒烟测试（2 条主路径）

- [ ] **冒烟 1**：截图上传 → DeepSeek 解析 → 大类确认 → 入库 → 首页展示 — 完成日期：____
- [ ] **冒烟 2**：AI 顾问基础对话多轮测试 — 完成日期：____

---

## Phase 1a 退出条件

- [ ] 24 项 API 全部完成
- [ ] 8 项 P0 全部达成
- [ ] 2 条冒烟测试通过
- [ ] 单元测试覆盖率 ≥ 60%
- [ ] Swagger UI 全部 API 可访问

**Phase 1a 完成日期**：________________

**进入 Phase 1b 启动条件**：上述全部勾选
</content>