# FinControl Phase 1 验收标准（追加 P0 版）

> Phase 0 产出。本文档基于第八章原始 Phase 1 验收标准，**追加第一轮与第三轮评审中需要在 Phase 1 完成的 P0 项**。
>
> 原始 Phase 1 验收标准保留并标记；新增 P0 验收项标记为 **[P0-追加]**。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.0 |
| 编写日期 | 2026-07-09 |
| 编写者 | 架构审查助手 |
| 配套文档 | [decisions.md](../phase-0/decisions.md) + [api-contract.md](../phase-0/api-contract.md) |
| 适用阶段 | Phase 1a（后端）+ Phase 1b（前端） |

---

## 1. Phase 1 总体目标

> 实现"截图上传 → DeepSeek 解析 → 大类确认 → 数据写入 → 首页展示"的完整闭环，并支持 AI 顾问基础对话。

---

## 2. 原始 Phase 1 验收标准（第八章末尾）

### 2.1 基础 6 项（来自第八章末尾原文）

- [ ] 用户在 Web 前端上传支付宝截图
- [ ] 后端调用 DeepSeek API 解析图片，返回 JSON
- [ ] 解析结果正确写入 asset_raw 表，自动汇总写入 asset_snapshot 表
- [ ] 首页从后端 API 获取最新快照数据并正确展示六大类金额和占比
- [ ] AI顾问页面可进行多轮对话，对话历史存入 chat_history 表
- [ ] 所有 API 接口支持 user_id 参数（默认值 1）

---

## 3. P0 追加项（来自第一轮与第三轮评审）

### 3.1 第一轮评审 P0（6 项全部追加）

#### [P0-1.1] 余额类数据路径

- [ ] `asset_raw.category` 字段枚举值包含"余额类"
- [ ] 截图解析时，AI 返回的"余额宝"、"余额"记录写入 `asset_raw`（category="余额类"）
- [ ] `asset_snapshot.balance_fund` 通过 `SELECT SUM(amount) FROM asset_raw WHERE category='余额类' AND is_latest=true` 动态计算
- [ ] 首页"余额类卡片"展示 418.46 等数据
- [ ] `fund_category_map` 表预录余额宝、余额的映射（source='user_manual'）

**来源**：第一轮 P0 1.1（已采纳）

**关联 API**：`GET /api/asset/balance`（[api-contract.md](../phase-0/api-contract.md) 9.1）

#### [P0-1.2] 三表写入事务边界

- [ ] 截图确认入库 API（`POST /api/snapshot/confirm`）使用 `@Transactional` 注解
- [ ] `asset_raw` 写入、`fund_category_map` 写入（新增时）、`asset_snapshot` 更新在同一事务中
- [ ] 任一步失败整体回滚，不出现明细存在但汇总缺失的状态
- [ ] Spring Boot 事务隔离级别保持默认 READ_COMMITTED（不引入悲观锁）

**来源**：第一轮 P0 1.3（已采纳）+ 3.2（并发防护降级为前端防抖 + 默认隔离级）

#### [P0-1.3] 用户修改映射的 UPDATE 规则

- [ ] 大类确认面板中，用户在已匹配基金的下拉框中选择其他大类
- [ ] 后端调用 `POST /api/category-map/update`，执行 `UPDATE fund_category_map SET category=?, source='user_correct', confirmed_at=NOW()`
- [ ] 不新增行（因联合唯一索引已存在）
- [ ] `id` 主键不变，便于审计追溯

**来源**：第一轮 P0 2.2（已采纳）

**关联 API**：`POST /api/category-map/update`（[api-contract.md](../phase-0/api-contract.md) 7.2）

#### [P0-1.4] AI 解析失败异常路径

- [ ] 当 DeepSeek API 返回非 JSON、超时、识别 0 只基金、字段异常时
- [ ] 后端返回错误码 3001 / 3002 / 3003（[api-contract.md](../phase-0/api-contract.md) 11 节）
- [ ] 失败记录写入 `chat_history` 表（role=assistant，content=错误详情，conversation_type='screenshot_parse'）
- [ ] **不写入** `asset_raw` / `fund_category_map` / `asset_snapshot` 任何表
- [ ] 前端对话气泡显示"解析失败"，附"重新解析"按钮
- [ ] 解析日志侧边栏（10.1）显示失败记录（status='parse_failed'）

**来源**：第一轮 P0 1.6（已采纳）

**关联 API**：`POST /api/screenshot/parse`（[api-contract.md](../phase-0/api-contract.md) 2.2）

#### [P0-1.5] 快照日期校验规则

- [ ] AI 识别的 `snapshot_date` 与系统当前日期对比
- [ ] 差值绝对值 > 7 天时，弹出"AI 识别的日期为 XXXX-XX-XX，与系统日期相差 N 天，请确认是否正确"
- [ ] `is_latest` 翻转 SQL：`ORDER BY confirmed_at DESC, id DESC LIMIT 1`
- [ ] 确认面板顶部显示"该日期已有数据，确认将覆盖"提示（若 date 已有记录）

**来源**：第一轮 P0 1.4（已采纳）+ 第四轮 P0 2.2.3.2

#### [P0-1.6] 前端防抖 + 事务隔离级别

- [ ] 确认入库按钮在请求返回前 disabled，防止双击或多次触发
- [ ] Spring Boot 后端事务保持默认 READ_COMMITTED
- [ ] 不引入悲观锁或分布式锁（单用户 MVP 场景）

**来源**：第一轮 P0 3.2（采纳方案降级版）

### 3.2 第三轮评审 P0 中与数据入库相关的项（7 项追加）

#### [P0-3.1] 全局状态管理方案

- [ ] 前端引入 Zustand（推荐）作为全局状态管理库
- [ ] 创建 stores：`assetSnapshotStore`、`userConfigStore`、`operationStore`、`chatStore`
- [ ] `/data` 上传后 → assetSnapshotStore 更新 → 首页、月度操作台、比例演化自动响应
- [ ] `/config` 修改后 → userConfigStore 更新 → 月度、首页自动响应

**来源**：第三轮 P0 1.5.1（已采纳方案）

#### [P0-3.2] 大类确认面板误操作防护

- [ ] [确认入库] 按钮点击后，弹出"二次确认"对话框，显示汇总（各类别合计、偏差）
- [ ] 确认后，资产数据写入，并显示 10 秒"已入库 · 撤销"toast
- [ ] 10 秒内可点击"撤销"调用 `DELETE /api/snapshot/confirm/{snapshotId}`
- [ ] 撤销时删除刚写入的 asset_raw、删除新增的 fund_category_map、恢复 asset_snapshot 旧 is_latest=true
- [ ] 超时撤销返回 410 错误码

**来源**：第三轮 P0 2.2.1.1（提交前预览）+ 2.2.2.1（10 秒撤销）

**关联 API**：`DELETE /api/snapshot/confirm/{snapshotId}`（[api-contract.md](../phase-0/api-contract.md) 4.2）

#### [P0-3.3] 余额类纳入下拉选项

- [ ] 大类确认面板的下拉选择器包含"余额类"选项
- [ ] fund_category_map 中余额宝、余额映射已预录
- [ ] AI 返回余额类记录时自动选中"余额类"

**来源**：第三轮 P0 2.2.4.3（衔接第一轮 1.1）

#### [P0-3.4] "忽略该条"按钮

- [ ] 大类确认面板每条基金右侧增加"忽略"按钮
- [ ] 用户点击"忽略"后，该条 `fundMappings.isIgnored=true` 提交
- [ ] 后端过滤掉 isIgnored=true 的记录，不写入数据库
- [ ] ignoredFundCount 在响应中返回

**来源**：第三轮 P0 2.2.4.5（已采纳）

**关联 API**：`POST /api/snapshot/confirm`（[api-contract.md](../phase-0/api-contract.md) 4.1）

#### [P0-3.5] AI 顾问 B.3 Prompt few-shot

- [ ] `intent_classifier v1.0` Prompt 增加正反例 few-shot（至少 5 对正例 + 5 对反例）
- [ ] 正例：投资决策类问题示例
- [ ] 反例：闲聊、技术支持、写作辅助、模型身份询问示例
- [ ] `prompt_versions` 表录入 `intent_classifier v1.0`（第三轮 3.4.4）

**来源**：第三轮 P0 3.1.1 + 3.4.4（已采纳）

#### [P0-3.6] AI 顾问 system prompt 切换实现

- [ ] `POST /api/chat/send` 后端逻辑：
  1. 先调用 `intent_classifier` 判定
  2. 根据判定结果选择 `ai_assistant v1.0` 或垃圾回路（无 system prompt）
  3. 每次 API 调用都重新组装 messages 列表（system prompt 来自当前消息分类结果）
  4. 响应中 `routedTo` 标识走的是 `main_loop` 还是 `garbage_loop`

**来源**：第三轮 P0 3.2.1 + 3.2.2（已采纳）

**关联 API**：`POST /api/chat/send`（[api-contract.md](../phase-0/api-contract.md) 8.1）

#### [P0-3.7] 快照日期默认值优先级

- [ ] 确认面板打开时，snapshot_date 默认值优先级：
  1. AI 识别日期（最高优先）
  2. 系统当前日期
  3. 用户上次确认日期
- [ ] 默认值通过 `GET /api/correction/defaults` 的 `snapshotNote` 字段返回说明

**来源**：第三轮 P0 4.1.2（已采纳）

**关联 API**：`GET /api/correction/defaults`（[api-contract.md](../phase-0/api-contract.md) 5.1）

---

## 4. 第四轮评审 P0 中与 Phase 1 相关的项（4 项追加）

#### [P0-4.1] profit 字段读取位置

- [ ] 首页"六大类明细表格"（7.4.1 末尾，默认折叠）新增"持有收益"列
- [ ] 调用 `GET /api/snapshot/latest/detail` 获取每只基金 profit
- [ ] 表格列：基金名称、金额、占比、目标比例、偏差、**持有收益**

**来源**：第四轮 P0 2.1.1 + [decisions.md](../phase-0/decisions.md) 决策 3

#### [P0-4.2] target_ratio 同步 = 解读 4

- [ ] `POST /api/config` 仅写入 `user_config` 表
- [ ] 不触发 `asset_snapshot` 级联更新
- [ ] 响应中 `triggeredSnapshotUpdate=false`
- [ ] `GET /api/correction/defaults` 从 `user_config` 读取 `targetRatios`

**来源**：第四轮 P0 2.2.1 + [decisions.md](../phase-0/decisions.md) 决策 2

#### [P0-4.3] 累计收益率卡片 Phase 1 隐藏

- [ ] 首页顶部从三卡片改为两卡片（余额类 + 六大类总值）
- [ ] "累计收益率"卡片用 `isPhase1Mode` 标志控制，Phase 1 默认隐藏
- [ ] `GET /api/asset/cumulative-return` 在 Phase 1 返回 `{ available: false }`

**来源**：第四轮 P0 3.3.1 + [decisions.md](../phase-0/decisions.md) 决策 4

#### [P0-4.4] 重新解析按钮 API

- [ ] 大类确认面板的"重新解析"按钮调用 `POST /api/screenshot/reparse`
- [ ] body 传 `conversationId`
- [ ] 后端重新调用 DeepSeek API 解析原始截图
- [ ] 响应同 `POST /api/screenshot/parse`

**来源**：第四轮 P0 3.3.3（已采纳方案 A）

**关联 API**：`POST /api/screenshot/reparse`（[api-contract.md](../phase-0/api-contract.md) 2.3）

---

## 5. Phase 1a vs Phase 1b 验收项拆分

### 5.1 Phase 1a（后端流水线，3-4 天）

| # | 验收项 | 关联 P0 |
|---|------|---------|
| 1a.1 | Spring Boot 项目可启动 | — |
| 1a.2 | MySQL 连接池配置正确 | — |
| 1a.3 | `docs/phase-0/db-schema.sql` 运行建表无错误 | — |
| 1a.4 | `POST /api/screenshot/upload` 实现 | — |
| 1a.5 | `POST /api/screenshot/parse` 实现（含 1.6 失败处理）| [P0-1.4] |
| 1a.6 | `POST /api/screenshot/reparse` 实现 | [P0-4.4] |
| 1a.7 | `POST /api/snapshot/confirm` 实现（含 1.2 事务 + 1.4 校验）| [P0-1.2] [P0-1.3] [P0-1.5] [P0-3.3] [P0-3.4] |
| 1a.8 | `DELETE /api/snapshot/confirm/{id}` 实现 | [P0-3.2] |
| 1a.9 | `GET /api/snapshot/latest` 实现 | — |
| 1a.10 | `GET /api/snapshot/latest/detail` 实现（含 profit）| [P0-4.1] |
| 1a.11 | `GET /api/snapshot/{date}` 实现 | — |
| 1a.12 | `GET /api/snapshot/history` 实现 | — |
| 1a.13 | `GET /api/asset/balance` 实现 | [P0-1.1] |
| 1a.14 | `GET /api/asset/operations/recent` 实现（Phase 1 临时方案）| — |
| 1a.15 | `GET /api/asset/cumulative-return` 实现（返回 available=false）| [P0-4.3] |
| 1a.16 | `GET /api/category-map/match` 实现 | — |
| 1a.17 | `POST /api/category-map/update` 实现 | [P0-1.3] |
| 1a.18 | `POST /api/chat/send` 实现（含 3.6 prompt 切换）| [P0-3.5] [P0-3.6] |
| 1a.19 | `GET /api/conversations` 实现 | — |
| 1a.20 | `GET /api/conversations/{id}` 实现 | — |
| 1a.21 | `POST /api/conversations` 实现 | — |
| 1a.22 | `DELETE /api/conversations/{id}` 实现 | — |
| 1a.23 | `GET /api/parse-logs` 实现 | — |
| 1a.24 | 单元测试覆盖率 ≥ 60% | — |

### 5.2 Phase 1b（前端对接，3-4 天）

| # | 验收项 | 关联 P0 |
|---|------|---------|
| 1b.1 | Vite + React 项目可启动 | — |
| 1b.2 | React Router 配置（首页 /、/data、/ai）| — |
| 1b.3 | Zustand stores 创建 | [P0-3.1] |
| 1b.4 | Axios 拦截器（含 X-User-Id 默认 1）| — |
| 1b.5 | 侧边栏布局（240px 宽度，可折叠）| — |
| 1b.6 | 首页两卡片（余额类 + 六大类总值）| [P0-4.3] |
| 1b.7 | 首页六大类环形图（ECharts/Recharts）| — |
| 1b.8 | 首页六大类明细表格（含 profit 列）| [P0-4.1] |
| 1b.9 | 首页最近操作时间线 | — |
| 1b.10 | 数据管理页面：上传截图 → 解析气泡 | [P0-1.4] |
| 1b.11 | 大类确认面板 UI（灰底预填、黄底高亮）| [P0-3.3] |
| 1b.12 | 确认面板下拉选项含余额类 | [P0-3.3] |
| 1b.13 | 确认面板"忽略该条"按钮 | [P0-3.4] |
| 1b.14 | 确认面板二次确认弹窗 | [P0-3.2] |
| 1b.15 | 10 秒"已入库 · 撤销"toast | [P0-3.2] |
| 1b.16 | 快照日期默认值（AI 识别 > 系统当前日）| [P0-3.7] |
| 1b.17 | "该日期已有数据"提示 | [P0-1.5] |
| 1b.18 | 确认入库按钮防抖 | [P0-1.6] |
| 1b.19 | 解析日志侧边栏（10.1）| — |
| 1b.20 | AI 顾问页面：发送消息 → 显示回复 | [P0-3.6] |
| 1b.21 | AI 顾问对话列表 | — |
| 1b.22 | AI 顾问新建对话按钮 | — |
| 1b.23 | 全局状态联动测试 | [P0-3.1] |

---

## 6. Phase 1 退出条件（DoD）

### 6.1 必达条件（不达不可进入 Phase 2）

- [ ] Phase 1a 全部 24 项验收通过
- [ ] Phase 1b 全部 23 项验收通过
- [ ] 原始 Phase 1 验收标准 6 项全部达成
- [ ] 本文档 18 个 P0 追加项全部达成（[P0-1.1] - [P0-1.6]、[P0-3.1] - [P0-3.7]、[P0-4.1] - [P0-4.4]）
- [ ] 端到端测试：截图上传 → 解析 → 确认 → 入库 → 首页展示全流程通过
- [ ] 端到端测试：AI 顾问基础对话多轮测试通过

### 6.2 建议条件（不阻塞 Phase 2 启动）

- [ ] 单元测试覆盖率 ≥ 70%
- [ ] E2E 测试覆盖至少 3 条主路径
- [ ] 前端 Lighthouse 评分 ≥ 80
- [ ] API 响应时间 P95 < 500ms（DeepSeek 调用除外）

---

## 7. Phase 1 不做（明确排除）

以下项**不在 Phase 1 范围内**，避免范围蔓延：

- ❌ 月度操作台（Phase 2）
- ❌ 资产配置页面 UI（Phase 2，**后端 API 已实现**）
- ❌ 净值曲线（Phase 3）
- ❌ 比例演化看板（Phase 3）
- ❌ 季度操作台（Phase 5a）
- ❌ 多用户、登录、SDK（Phase 5b/5c）
- ❌ nav_history 表设计（Phase 3a）
- ❌ target_ratio_history 表设计（Phase 2）
- ❌ 移动端适配（Phase 5c）

---

## 8. 与第八章原始描述的差异

| 项目 | 第八章原始 | 本文档修订 |
|------|----------|----------|
| Phase 1 时间 | 3-4 天 | 拆分为 Phase 1a（3-4 天）+ Phase 1b（3-4 天）|
| Phase 1 验收项数 | 6 项 | 6 项原始 + 18 项 P0 追加 = 24 项 |
| 累计收益率卡片 | 卡片在首页显示 | **Phase 1 隐藏**，Phase 3 显示 |
| profit 字段 | 写入但无读取位置 | **首页明细表读取**（决策 3）|
| target_ratio 同步 | 同步更新 asset_snapshot | **仅写 user_config**（决策 2）|
| 大类确认面板 | 基础确认 | + 二次确认 + 10 秒撤销 + 忽略按钮 + 余额类 |

---

## 9. 文档维护

- **Phase 1a 编码期间**：每完成一个验收项，更新对应 checkbox
- **Phase 1b 编码期间**：同上
- **Phase 2 启动前**：将剩余未达项滚动到 Phase 2 任务清单
- **发现新问题**：追加 P0/P1 项，更新本文档
</content>