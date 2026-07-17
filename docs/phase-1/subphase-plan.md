# FinControl Phase 1 子阶段计划与验收目标

> Phase 0 已收官，Phase 1 启动前的实施蓝图。
>
> **本文档定位（与既有文档协同，不替代）：**
>
> | 文档 | 角色 | 关系 |
> |------|------|------|
> | [`acceptance-criteria.md`](./acceptance-criteria.md) | 验收全集（24+23 项 + 18 项 P0）| 总账，本文不重列验收项，只引用编号 |
> | [`checklists/phase-1a.md`](./checklists/phase-1a.md)、[`checklists/phase-1b.md`](./checklists/phase-1b.md) | 实时勾选清单（含完成日期栏）| 每个子阶段通过 = 可勾选对应 checklist 项 |
> | [`../phase-0/decisions.md`](../phase-0/decisions.md) | 决策锁定（API 契约、target_ratio、profit、卡片隐显等 6 项）| 子阶段不得违反决策 |
> | [`../phase-0/api-contract.md`](../phase-0/api-contract.md) | API 契约 | 子阶段必须严格遵循 |
> | **`subphase-plan.md`（本文）** | 子阶段划分 + 每阶段计划 + 验收目标（DoD）| 为编码提供"拆完即可动手"的颗粒度 |

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 文档版本 | v0.2（1a.1 + 1a.2 已闭环，更新进度跟踪） |
| 编写日期 | 2026-07-15 |
| 最近更新 | 2026-07-16（1a.3 业务逻辑闭环 6/6；真实 MySQL 持久化待统一验收） |
| 配套文档 | acceptance-criteria.md / checklists/ / api-contract.md / decisions.md / test-records/manual-tests/2026-07-16_phase1a-phase1a2-acceptance.md |
| 适用阶段 | Phase 1a（后端）+ Phase 1b（前端）|

---

## 1. 拆分原则

1. **可闭环**：每个子阶段可在 **半天 ~ 1 天**内完成编码 + 本地联调 + 自测，不必跨日等待外部输入。
2. **可验收**：每个子阶段都有客观 DoD（"看到 X、拿到 Y、勾选 N 项 checklist"），不靠主观判断。
3. **可串联**：子阶段之间通过 API、文档或联调事件明确衔接，前置未通过不强行启动后置。
4. **不重不漏**：所有子阶段的 checklist 编号（1a.1~1a.24、1b.1~1b.23、P0-*）覆盖 = `acceptance-criteria.md` 的全集。
5. **不破坏决策**：任何子阶段不得违反 `decisions.md` 的 6 项锁定（API 契约、target_ratio 解读 4、profit 在首页明细表、累计收益率隐藏、P0 验收追加、Phase 计划修订）。

---

## 2. Phase 1a 子阶段（后端，7 段，预估 3-4 天）

### 1a.1 后端基础设施确认（0.5 天）

**目标**：Phase 0 已搭骨架的 Spring Boot 工程在本机拉起、连库、Swagger 可访问。

**范围**（关联 checklist 项）：
- 1a.1 Spring Boot 项目可启动
- 1a.2 MySQL 连接池配置正确（HikariCP pool size=10）
- 1a.3 `docs/phase-0/db-schema.sql` 运行建表无错误

**交付物**：
- 本地 `mvn spring-boot:run` 启动成功日志
- `application-local.yml` 配置 + 本地 MySQL 连通截图/SQL 查询输出
- Swagger UI（`/swagger-ui.html`）首屏可加载

**验收目标（DoD）**：
- [x] `GET /actuator/health` 返回 `UP` — 完成日期：2026-07-15
- [x] 数据库 7 张表全部建好（`asset_raw` / `asset_snapshot` / `fund_category_map` / `chat_history` / `user_config` / `prompt_versions` / `operation_log`）— 完成日期：2026-07-15
- [x] `pom.xml` 关键依赖完整（Spring Web、MyBatis-Plus、MySQL 驱动、Swagger、Validation、Actuator、JDBC）— 完成日期：2026-07-15

**前置依赖**：Phase 0 完成（已具备）。

**实际进度**：✅ **2026-07-15 完整闭环**（mvn test 1/1 PASS，Spring 容器 4.431s 启动，H2 Pool OK）。详细报告见 `docs/test-records/manual-tests/2026-07-16_phase1a-phase1a2-acceptance.md §3`。

---

### 1a.2 截图解析 API 链（0.5–1 天）

**目标**：完整实现"上传 → 解析 → 重解析 → 失败日志"4 个 API，对接 DeepSeek。

**范围**：
- 1a.4 `POST /api/screenshot/upload`
- 1a.5 `POST /api/screenshot/parse`（含 **[P0-1.4]** AI 解析失败异常路径）
- 1a.6 `POST /api/screenshot/reparse`（**[P0-4.4]**）
- 1a.23 `GET /api/parse-logs`

**交付物**：
- 4 个 Controller + Service + DeepSeek 客户端 + 文件存储抽象
- chat_history 失败记录写入逻辑（[P0-1.4] 三类错误码 3001/3002/3003）
- parse_logs 列表返回（status='parse_failed' 可见）

**验收目标（DoD）**：
- [x] 上传一张支付宝截图能拿到返回的 conversationId — 完成日期：2026-07-16
- [x] 解析返回正确 JSON（参照 api-contract.md 2.2）— 完成日期：2026-07-16
- [x] 人为构造一个 0 只基金返回，验证错误码 3001 — 完成日期：2026-07-16
- [x] 人为让 DeepSeek 返回非 JSON，验证错误码 3002 — 完成日期：2026-07-16
- [x] chat_history 中查到 `conversation_type='screenshot_parse'` 的失败行 — 完成日期：2026-07-16
- [x] reparse 用同一 conversationId 重新走通 — 完成日期：2026-07-16

**前置依赖**：1a.1 通过。

**实际进度**：✅ **2026-07-16 完整闭环**（minimax M3 真实调用 code:0 + 7 只基金 + 6 大类，mvn test 7/7 PASS，5 类错误码契约全覆盖）。详细报告见 `docs/test-records/manual-tests/2026-07-16_phase1a-phase1a2-acceptance.md §4`。

**重要决策（1a.5 期间）**：发现 DeepSeek 是纯文本模型（无 `image_url` 多模态支持），已切换到 minimax M3（TokenPlanPlus）作为视觉模型后端——这是 Phase 0 决策的反转，详见 `docs/test-records/manual-tests/2026-07-16_phase1a-phase1a2-acceptance.md §2.2`。

---

### 1a.3 快照确认与事务（1 天，本阶段最关键）

**目标**：打通"三表事务 + 余额类路径 + 日期校验 + 撤销"完整闭环。

**范围**：
- 1a.7 `POST /api/snapshot/confirm`（**[P0-1.2]** 事务、**[P0-1.3]** 映射 UPDATE、**[P0-1.5]** 日期校验、**[P0-3.3]** 余额类下拉、**[P0-3.4]** 忽略按钮）
- 1a.8 `DELETE /api/snapshot/confirm/{id}`（**[P0-3.2]** 10 秒撤销 + 410 过期）

**交付物**：
- `@Transactional` Service + asset_raw / fund_category_map / asset_snapshot 三表写入
- isIgnored 字段过滤（不写入数据库）
- DELETE 撤销（含 transaction 镜像 rollback）+ 过期返 410
- API 1001 日期校验错误码

**验收目标（DoD）**：
- [ ] 正常确认：三类表全部写入，is_latest 翻转正确
- [ ] 人为让中间一步抛错，整体回滚（明细存在但汇总缺失的状态不复现）
- [ ] 修改映射走 UPDATE 不是 INSERT（id 不变，source='user_correct'）
- [ ] AI 识别日期距今天 > 7 天，弹出 1001 错误
- [ ] 当 date 已有记录，确认面板顶部"该日期已有数据"提示
- [ ] 10 秒内撤销成功，超时撤销返 410
- [ ] ignored=true 的记录不在数据库

**前置依赖**：1a.2 通过（需要先有可解析的 JSON 才能 confirm）。

**实际进度（2026-07-16 补充）**：🟡 **业务逻辑闭环通过，真实持久化验收待统一测试**。

- `1a.3.3` SnapshotController + `1a.8` rollback 代码已完成；
- `1a.3.4` `SnapShotConfirmServiceIT` 已验证 6/6 PASS；
- 本轮使用 `@MockBean` Mapper 验证 Service 编排、dedup、镜像校验和 10 秒撤销，不等同于真实 MySQL XML SQL 验收；
- 真实 MySQL upsert、事务实际回滚、HTTP 410、前后端完整冒烟留到前端完成后统一验证。

补充报告：[`2026-07-16_phase1a3-supplemental-acceptance.md`](../test-records/manual-tests/2026-07-16_phase1a3-supplemental-acceptance.md)。

---

### 1a.4 快照查询 + 首页辅助 API（0.5 天）

**目标**：把 Phase 1 后端"只读面"全部补齐，前端可独立开发首页、数据管理历史。

**范围**：
- 1a.9 `GET /api/snapshot/latest`
- 1a.10 `GET /api/snapshot/latest/detail`（**[P0-4.1]** 含 profit）
- 1a.11 `GET /api/snapshot/{date}`
- 1a.12 `GET /api/snapshot/history`
- 1a.13 `GET /api/asset/balance`（**[P0-1.1]**）
- 1a.14 `GET /api/asset/operations/recent`
- 1a.15 `GET /api/asset/cumulative-return`（**[P0-4.3]** 返回 `available=false`）

**验收目标（DoD）**：
- [ ] latest.detail 的 profit 字段非空，且与 asset_raw.profit 一致
- [ ] balance = SELECT SUM WHERE category='余额类' AND is_latest=true
- [ ] cumulative-return 返回 `{ available: false }`，不报 500
- [ ] history 按日期倒序，`operations/recent` 列表字段对齐 api-contract.md

**前置依赖**：1a.3 通过（需要先入库数据才能查询验证）。

---

### 1a.5 大类映射 API（0.25 天）

**目标**：把 fund_category_map 的查询与 UPDATE 抽出独立 API，配合 1a.3 复用。

**范围**：
- 1a.16 `GET /api/category-map/match`
- 1a.17 `POST /api/category-map/update`（**[P0-1.3]** UPDATE 规则）

**验收目标（DoD）**：
- [x] match 查询返回该基金名对应的现行大类（source 标识可见）— 完成日期：2026-07-17
- [x] update 执行后：行数不变（不 INSERT 新行）、id 不变、source='user_correct'、confirmed_at 更新 — 完成日期：2026-07-17

**前置依赖**：1a.3 通过（confirm 时已写入映射，update 才有上下文）。

**实际进度**：✅ **2026-07-17 完整闭环**（cur 4.5：Service 13/13 + Controller 10/10 PASS；ErrorCode 1002 新增；XML 批量 select 加 1 条避免 N+1；A5-S01–A5-S06 验收用例全过；mvn test 72/72 无回归）。详细报告见 `docs/test-records/manual-tests/2026-07-17_phase1a5-acceptance-report.md`。

---

### 1a.6 AI 顾问 API（0.5–1 天）

**目标**：实现对话 CRUD + Prompt 切换 + 意图分类。

**范围**：
- 1a.18 `POST /api/chat/send`（**[P0-3.5]** few-shot、**[P0-3.6]** system prompt 切换）
- 1a.19 `GET /api/conversations`
- 1a.20 `GET /api/conversations/{id}`
- 1a.21 `POST /api/conversations`
- 1a.22 `DELETE /api/conversations/{id}`

**交付物**：
- intent_classifier v1.0 Prompt + 5 正 5 反 few-shot 示例
- `prompt_versions` 表录入 `intent_classifier v1.0` 行
- `ai_assistant v1.0` 与"垃圾回路无 system prompt"双分支
- routedTo 标识在响应中暴露

**验收目标（DoD）**：
- [x] 投资决策类问题 → main_loop（带 ai_assistant system prompt）— 完成日期：2026-07-17
- [x] 闲聊/技术支持/写作/模型身份询问 → garbage_loop（无 system prompt）— 完成日期：2026-07-17
- [x] 响应中 `routedTo` 字段与走过的回路一致 — 完成日期：2026-07-17
- [x] conversation 列表CRUD 全部走通 — 完成日期：2026-07-17
- [x] chat_history 写入正确（role/user|assistant、content 不丢失）— 完成日期：2026-07-17

**前置依赖**：1a.1 通过（独立模块，可与 1a.4 并行）。

**实际进度**：✅ **2026-07-17 完整闭环**（cur 4.6：TextAiClient 16/16 + IntentClassifier 18/18 + ChatService 11/11 + ChatController 7/7 + ConversationService 16/16 + ConversationController 11/11 = 79/79 PASS；mvn test 151/151 全过，0 回归）。详细报告见 `docs/test-records/manual-tests/2026-07-17_phase1a6-acceptance-report.md`。

---

### 1a.7 冒烟 + 测试覆盖率（0.5 天）

**目标**：两条主路径冒烟全过、覆盖率达标、Swagger 全 API 暴露。

**范围**：
- 冒烟 1：截图上传 → 解析 → 大类确认 → 入库 → 首页展示（**[P0-1.4]** 不阻塞冒烟路径）
- 冒烟 2：AI 顾问基础对话多轮
- 1a.24 单元测试覆盖率 ≥ 60%
- 全部 API 在 Swagger UI 可调用

**验收目标（DoD）**：
- [ ] 冒烟 1 端到端通过（覆盖 1a.2~1a.4 全部 API）
- [ ] 冒烟 2 多轮对话历史可查
- [ ] `mvn test` 通过，覆盖率报告 ≥ 60%（JaCoCo）
- [ ] Swagger `/swagger-ui.html` 列出全部 ≥ 24 个端点

**前置依赖**：1a.2–1a.6 全部通过。

---

## 3. Phase 1b 子阶段（前端，4 段，预估 3-4 天）

> Phase 1b 启动条件：Phase 1a 全部 24 项 API + 8 项 P0 + 2 条冒烟完成。
> 前端在 1a 期间可用 mock 数据并行开发骨架与首页，但不进入"端到端联调"。

### 1b.1 前端骨架 + 全局状态（0.5 天）

**目标**：Vite + React 工程跑起来，3 路由可达，4 store 生效。

**范围**：
- 1b.1 Vite + React 项目可启动
- 1b.2 React Router 配置（/、/data、/ai）
- 1b.3 Zustand stores（**[P0-3.1]**：assetSnapshotStore、userConfigStore、operationStore、chatStore）
- 1b.4 Axios 拦截器（X-User-Id 默认 1）
- 1b.5 侧边栏布局（240px，可折叠）

**验收目标（DoD）**：
- [ ] `npm run dev` 启动，`/`、`/data`、`/ai` 互相跳转可达
- [ ] 4 个 store 单元测试可见、可读、可写
- [ ] Axios 拦截器自动注入 `X-User-Id: 1`
- [ ] 侧边栏点击折叠按钮收起/展开

**前置依赖**：Phase 1a.1 通过（后端能起来，前端才知道 base URL）。

---

### 1b.2 首页 + 全局状态联动（1 天）

**目标**：首页完整呈现"两卡片 + 图表 + 明细 + 时间线"，且修改 /data 后首页自动响应。

**范围**：
- 1b.6 首页两卡片（**[P0-4.3]** 累计收益率隐藏）
- 1b.7 六大类环形图（ECharts）
- 1b.8 明细表格（**[P0-4.1]** 含 profit 列）
- 1b.9 最近操作时间线
- 1b.23 全局状态联动测试（**[P0-3.1]**）

**验收目标（DoD）**：
- [ ] 加载首页调用 `GET /api/snapshot/latest`、`/api/asset/balance`、`/api/asset/cumulative-return`
- [ ] 余额类卡片数字 = balance.amount
- [ ] 六大类总值 = snapshot.totalAmount
- [ ] 累计收益率卡片**不显示**（isPhase1Mode=true，cumulative-return 返 `{available:false}`）
- [ ] 明细表"持有收益"列显示每只基金 profit
- [ ] /data 入库成功后，首页自动刷新（store 联动）

**前置依赖**：1b.1 + Phase 1a.4 通过。

---

### 1b.3 数据管理页 + 大类确认面板（1.5 天，本阶段最复杂）

**目标**：从"上传截图"到"入库成功"的全前端路径与防误操作全部到位。

**范围**：
- 1b.10 上传 → 解析气泡（**[P0-1.4]** 失败时气泡显示"解析失败"+"重新解析"按钮）
- 1b.11 大类确认面板 UI（灰底预填、黄底高亮）
- 1b.12 下拉选项含余额类（**[P0-3.3]**）
- 1b.13 "忽略该条"按钮（**[P0-3.4]**）
- 1b.14 二次确认弹窗（**[P0-3.2]** 显示各类别合计、偏差）
- 1b.15 10 秒"已入库 · 撤销"toast（**[P0-3.2]** 调 DELETE /api/snapshot/confirm/{id}）
- 1b.16 快照日期默认值（**[P0-3.7]** AI 识别 > 系统当前日 > 用户上次确认日）
- 1b.17 "该日期已有数据"提示（**[P0-1.5]**）
- 1b.18 确认入库按钮防抖（**[P0-1.6]** disabled 状态机）
- 1b.19 解析日志侧边栏

**验收目标（DoD）**：
- [ ] 上传文件即显示气泡（loading → 成功 / 失败）
- [ ] 解析失败有"重新解析"按钮，调用 1a.6 `/api/screenshot/reparse`
- [ ] 确认面板下拉里有"余额类"且可被选中
- [ ] "忽略该条"后，该行不参与入库（前端 isIgnored + 后端过滤）
- [ ] "确认入库"点击后弹二次确认对话框，含汇总数据
- [ ] 入库成功显示 10 秒 toast，toast 中"撤销"能 DELETE 成功
- [ ] toast 消失后再点撤销返 410 并提示
- [ ] AI 识别日期距今 > 7 天弹出校验提示
- [ ] date 已有记录，确认面板顶部"该日期已有数据"提示
- [ ] 解析日志侧边栏展示 status='parse_failed' 记录

**前置依赖**：1b.1 + Phase 1a.3 通过。

---

### 1b.4 AI 顾问页面（0.5 天）

**目标**：聊天 CRUD 完整可见，能看到 routedTo 标识在 UI 体现。

**范围**：
- 1b.20 AI 顾问：发送消息 → 显示回复（**[P0-3.6]**）
- 1b.21 对话列表
- 1b.22 新建对话按钮

**验收目标（DoD）**：
- [ ] 在 `/ai` 页可发送消息，多轮历史可上滚
- [ ] 投资决策类问题回复带有"专业顾问"语气
- [ ] 闲聊类问题回复不带顾问语气（"垃圾回路"特征）
- [ ] 对话列表显示所有 conversation
- [ ] 新建按钮创建空 conversation 并切换

**前置依赖**：1b.1 + Phase 1a.6 通过。

---

## 4. 子阶段总览表

| # | 子阶段 | 预估 | 状态 | 完成日期 | 关联 checklist | 关联 P0 | 前置 |
|---|--------|------|------|----------|---------------|---------|------|
| 1a.1 | 后端基础设施确认 | 0.5d | ✅ 完成 | 2026-07-15 | 1a.1–1a.3 | — | — |
| 1a.2 | 截图解析 API 链 | 0.5–1d | ✅ 完成 | 2026-07-16 | 1a.4–1a.6, 1a.23 | P0-1.4, P0-4.4 | 1a.1 |
| 1a.3 | 快照确认与事务 | 1d | 🟡 业务闭环通过；真实 MySQL 持久化待统一验收 | 2026-07-16 | 1a.7–1a.8 | P0-1.2, P0-1.3, P0-1.5, P0-3.2, P0-3.3, P0-3.4 | 1a.2 |
| 1a.4 | 快照查询 + 首页辅助 | 0.5d | 1a.9–1a.15 | P0-1.1, P0-4.1, P0-4.3 | 1a.3 |
| 1a.5 | 大类映射 API | 0.25d | ✅ 完成 | 2026-07-17 | 1a.16–1a.17 | P0-1.3 | 1a.3 |
| 1a.6 | AI 顾问 API | 0.5–1d | ✅ 完成 2026-07-17 | 1a.18–1a.22 | P0-3.5, P0-3.6 | 1a.1（可与 1a.4 并行）|
| 1a.7 | 冒烟 + 覆盖率 | 0.5d | 1a.24 + 冒烟 1/2 | — | 1a.2–1a.6 |
| 1b.1 | 前端骨架 + 全局状态 | 0.5d | 1b.1–1b.5 | P0-3.1 | Phase 1a 全 |
| 1b.2 | 首页 + 全局联动 | 1d | 1b.6–1b.9, 1b.23 | P0-3.1, P0-4.1, P0-4.3 | 1b.1 + 1a.4 |
| 1b.3 | 数据管理 + 确认面板 | 1.5d | 1b.10–1b.19 | P0-1.4, P0-1.5, P0-1.6, P0-3.2, P0-3.3, P0-3.4, P0-3.7 | 1b.1 + 1a.3 |
| 1b.4 | AI 顾问页面 | 0.5d | 1b.20–1b.22 | P0-3.6 | 1b.1 + 1a.6 |

**总预算**：1a 约 3.25–4.25 天 + 1b 约 3.5 天，与 `decisions.md` 决策 6 一致（1a 3-4 天 + 1b 3-4 天）。

---

## 5. 进度跟踪（建议用法）

每个子阶段通过后：
1. 更新本文件：在表格对应行追加 ✅ 与完成日期。
2. 勾选 [`checklists/phase-1a.md`](./checklists/phase-1a.md) / [`phase-1b.md`](./checklists/phase-1b.md) 中对应条目并填日期。
3. 在 `acceptance-criteria.md` 末尾的"文档维护"节追加一行变更记录（如新增 P0 项）。

---

## 6. 与既有文档的关系

```
        ┌─────────────────────────────┐
        │   decisions.md（6 项锁定）  │ ← 不可违反
        └──────────────┬──────────────┘
                       │
        ┌──────────────▼──────────────┐
        │   api-contract.md（API 契约）│ ← 子阶段必须遵循
        └──────────────┬──────────────┘
                       │
   ┌───────────────────▼───────────────────┐
   │  acceptance-criteria.md（验收全集）   │ ← 总账
   └───────────────────┬───────────────────┘
                       │ 拆 解
   ┌───────────────────▼───────────────────┐
   │  subphase-plan.md（本文）             │ ← 执行计划
   └───────────────────┬───────────────────┘
                       │ 实 施
   ┌───────────────────▼───────────────────┐
   │  checklists/phase-1a.md / 1b.md      │ ← 实时勾选
   └───────────────────────────────────────┘
```

---

## 7. 待共识事项（草案标记）

> 以下为拆分方案中**需要用户确认**的判断，可一次性确认或在子阶段推进中按需调整：

- [x] **Q1** ✅ 已决策（2026-07-16）：1a.6（AI 顾问 API）是否与 1a.4（快照查询）并行？— **决定并行**（独立模块可同步启动，1a.6 前置 1a.1，与 1a.4 无依赖）。详见 1a.6 段。
- [ ] **Q2**：1b.3（数据管理页）拆为 1 天 / 1.5 天，预估偏紧。是否允许向后顺延到 1.5 天甚至 2 天？— **未决**（等 1b.1 + 1b.2 完成再评估）
- [x] **Q3** ✅ 已决策（2026-07-16）：是否需要在 1a.2 和 1a.3 之间插入"DeepSeek 客户端抽象 + 单测"？— **决定不插**（1a.2 已将 minimax 客户端做成独立 `VisionModelClient` 类，mock 5 类错误路径覆盖 5/5 PASS，已满足单元测试需求）。详见 1a.2 段进度条。
- [x] **Q4** ✅ 已决策（2026-07-16）：Phase 1b 启动条件？— **决定以"1a 全 24 项 API + 8 项 P0 + 2 条冒烟"为硬门槛**（不取巧；前端 mock 模式可独立开发骨架与首页）。

---

## 8. 文档维护

- 拆分方案调整：本文档是 v0.1 草案，**开始编码前**应确认第 7 节 4 个 Q。
- 任何子阶段新增 / 合并 / 重新拆分需同步更新本文表格与 checklists。
- Phase 2 启动前：把滚动未达项追加到 Phase 2 任务清单，并把本文归档。
