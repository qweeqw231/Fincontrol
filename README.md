# FinControl

> 个人资产配置控制系统 —— 以支付宝基金截图作为数据源，借助多模态模型完成基金去重、分类与三表事务入库，并通过 React 资产看板呈现当前快照、累计/持有收益与配置偏差。

FinControl 是一个以控制论为理论基础、以真实个人账户为实验平台、以多模态 AI 与稳健工程为底座的个人资产配置控制系统。Phase 1（数据流水线闭环）已于 2026-07-25 通过真实用户端到端验收正式收官；后续 Phase 2–5 的范围与排序由 Phase 0 决策 34 统一规定。

---

## 一句话价值主张

> 把 4 张支付宝基金截图丢进去，自动产出“三表入库的当前资产看板 + 累计/持有收益 + 配置偏差 + 解析历史”，整套链路真实可演示、真实可验收、真实 19/19 基金一致。

---

## 当前状态（2026-07-25）

| Phase | 状态 | 里程碑证据 |
|---|---|---|
| **Phase 0** 基础设施 + 评审决策 | ✅ 完成（2026-07-09）| 7 张表 schema、5 类评审 199 个问题、Phase 0 决策 1–6 锁定 |
| **Phase 1** 数据流水线闭环 | ✅ 完成（2026-07-25）| 1b.4-pr7 V1–V8 全通过；19 funds / ¥7,850.38 真实 4 图 confirm；后端 SnapShotConfirmServiceP7Test 9/9；前端 Vitest 12 文件 57/57；Vite build 1.08s |
| **Phase 2** 未完成的数据管理 + 资产配置 + 月度操作台 | ⏳ 待启动 | 复用 Phase 1a 已建 API（`/api/snapshot/history`、`/api/snapshot/{date}`、`/api/parse-logs`、`DELETE /api/snapshot/confirm/{id}`、`/api/screenshot/reparse`）|
| **Phase 3** 数据模型 + 净值/比例/日收益可视化 | ⏳ 待启动 | 先建 4 张表（nav_history、daily_returns、event_log、manual_nav_entry），再实现三类看板 |
| **Phase 4** 已知问题、数据校验、UI 优化、前端收尾 | ⏳ 待启动 | 全量 Maven 测试 fixture 债务、跨页面状态、空/错/加载态、可访问性 |
| **Phase 5** AI 对话 UI、LQR、多用户、移动端 | ⏳ 远期 | AI 顾问后端 API + 意图分类已完成；前端 UI 顺延至本阶段 |

详细路线锁定于 [Phase 0 决策 34](docs/phase-0/decisions.md#决策-34phase-1-里程碑收官与后续阶段重排2026-07-25)。

---

## 可演示闭环（Phase 1 已落地）

### 4 张截图 → 资产看板的真实链路

```
┌──────────┐  upload 4 imgs  ┌────────────────────┐  parse  ┌────────────────────┐  confirm  ┌────────────────────┐
│ DataPage │ ───────────────▶│ /screenshot/upload │ ──────▶│ /screenshot/parse- │ ────────▶│ /snapshot/confirm  │
│ /data    │                 │  → 4 × fileId      │        │ batch?mode=single  │          │ (三表事务)        │
└──────────┘                 └────────────────────┘        │  → 19-fund parsed   │          │ asset_raw / fund_  │
                                                              │  asset + 7 canonical│          │ category_map /     │
                                                              │  + 余额类 + profit  │          │ asset_snapshot     │
                                                              └────────────────────┘          │ + snapshot_meta    │
                                                                                              │ (is_current 切换)  │
                                                                                              └─────────┬──────────┘
                                                                                                        │ bumpRefresh
                                                                                                        ▼
┌──────────┐  fetchLatest    ┌────────────────────┐  getCumul  ┌────────────────────┐
│ HomePage │ ◀──────────────│ assetSnapshotStore  │ ◀─────────│ /api/asset/         │
│ /        │  (订阅 refresh │ (Zustand)           │            │  cumulative-return  │
└──────────┘   Counter)     └────────────────────┘            └────────────────────┘
   ▲
   │ 展示：总资产 / 六大类环形图 / 余额 / 累计+持有 / 配置偏差 / 最近操作
```

### 端到端验收要点（1b.4-pr7 V1–V8 用户亲自跑通）

1. `npm --prefix fincontrol-frontend run dev` 启动前端（5173，决策 29 统一）
2. `scripts/1b/restart-backend.ps1` 启动后端（决策 24 强制）
3. 在 `/data` 选择 4 张支付宝截图 → 选 `single` 模式 + 日期 → 点「上传并解析」
4. preview modal 逐行确认分类（`user_correct` 持久化、决策 32 冲突自动保留）；决策 33 D1–D7 UX 全部生效
5. 点「确认入库」→ 顶部绿色 banner 显示 `✓ 来自 {date} 的 {N} 只基金入库成功`
6. 弹「设为当前吗?」 → 蓝色「改为当前日期」调用 `setCurrent`；白色「取消」保持旧 current
7. 切回 `/`，看到当前日期 + 全部资产 + 累计/持有收益（决策 25 v3 smart fallback）
8. 任何操作通过 Zustand `bumpRefresh` 同步，HomePage 不再需要绕一圈刷新

---

## 功能介绍（Phase 1 已实现）

### 资产上传与解析

- 4 张支付宝基金截图批量上传、缩略图管理、删除与替换（PR4a GLOBAL-016）
- 解析模式 `single` / `multi` 切换，默认 `single`（决策 26）
- `dataTime` 字段（决策 13）覆盖 AI 提取的 snapshotDate
- 实时 provider / fallback 状态显示：成功显示 `provider=minimax, fallback=false`

### 基金分类与预览

- 7 大 canonical 类别（货币 / 固收 / 商品 / A股权益 / 海外权益 / 港股大中华 / 余额类）+ 类别归一化（决策 8）
- AI 误归大类时支持 dropdown 手动覆盖，`user_correct` 持久化（决策 32）
- 批量确认模式 + 顶部 toggle（决策 33 D1–D4）
- 类别冲突黄色 banner + `CATEGORY_CONFLICT` warning（决策 32）
- 消失-重现提示：黄色 banner 提示「上次确认 t0，您可能于 t1 及之前清仓」（决策 33 D7）
- preview modal 概览卡：总资产 / 六大类 / 余额类 / 基金数

### 入库与当前快照

- 三表事务写入（`asset_raw` / `fund_category_map` / `asset_snapshot`）
- 同 `snapshot_date` 二次入库幂等覆盖（PR7 Fix 4–5：`asset_raw` upsert + `asset_snapshot` DELETE 清扫）
- `snapshot_meta` 双层语义（决策 27 + 决策 33 V6）：`is_latest` per-date，`is_current` 跨日期
- 手动「设为当前快照」按钮 + 兜底兜底主列表
- 成功 banner + 蓝色 / 白色 prompt 弹窗解耦数据写入与 current 切换（PR7 Fix 1–3 + Fix 8）

### 首页资产看板

- 品牌 Header + 当前快照日期 + 来源
- 总资产 Hero（含余额类）+ 三大数据卡（六大类总值 / 余额 / 累计+持有）
- 累计/持有双列 + 4 个 ℹ️ InfoModal + 余额宝 Smart Fallback（决策 25 v3）
- 六大类环形图（Recharts PieChart + 6 类固定颜色）
- 配置偏差分析表
- 基金持仓明细表（含 profit 列、类内占比、零值显式显示决策 25）
- 最近操作时间线（5 条，含「基金数未知」标记）

### 全局状态与质量

- Zustand `assetSnapshotStore` 4 个 store（asset / user / operation / chat）
- `bumpRefresh` + `refreshCounter` 跨页面联动（决策 33 V6 Bug B 修复）
- `error` / `loading` / `empty` / `stale` / `disabled` 五态壳（StateShell）
- Vite proxy `/api/*` 转发 8080；axios 拦截器自动注入 `X-User-Id: 1`
- 配置可改：上传历史限制 7 / 14 / 30 / 180 / 不限制（决策 30 + 决策 31 settings 表）

### 当前已具备但未完整产品化的能力（明确边界，不在 Phase 1 验收范围）

| 项目 | 状态 | 后续阶段 |
|---|---|---|
| 历史快照列表/分页/日期筛选 | 后端 API 完整（`/api/snapshot/history`），前端未调用 | Phase 2 |
| 指定日期快照详情查看 | 后端 `GET /api/snapshot/{date}` 已有，前端无 UI 入口 | Phase 2 |
| 10 秒「已入库 · 撤销」toast | 后端 `DELETE /api/snapshot/confirm/{id}` 已有，前端未接 | Phase 2 |
| 单条「忽略该条」按钮 | 后端 `isIgnored` 字段语义已实现，前端 UI 缺失 | Phase 2 |
| 解析失败 / 重新解析 UI | 后端 `/api/screenshot/reparse` 已有，前端未暴露 `conversationId` | Phase 2 |
| 解析日志侧边栏 | 后端 `/api/parse-logs` 已有，前端未调用 | Phase 2 |
| 已有日期覆盖提示 | 后端 `confirmedOverwrite` 已有，前端未在确认前提示 | Phase 2 |
| 逐文件状态 + 单文件重试 | 全局 uploading / parsing 已有，逐文件状态缺失 | Phase 2 |
| 收益列 / DedupReport 完整展示 | preview 只显示总额和分类小计，缺少收益列和去重报告 | Phase 2 |
| AI 顾问前端对话 UI | 后端 `chat` + `conversations` API 完整，`/ai` 是占位页 | Phase 5 |
| 月度操作台 | 决策 4 v2 + 决策 25 已铺垫，UI 与求解待 Phase 2 | Phase 2 |
| 资产配置页面 | `/config` 占位页 | Phase 2 |
| 净值 / 比例 / 日收益 | 4 张表待建 | Phase 3 |
| LQR / 多用户 / 移动端 | — | Phase 5 |

---

## 技术架构

### 整体分层

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Browser  React 18 + Vite 5 + React Router 6 + Zustand 5                 │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ Pages    HomePage │ DataPage │ AIPage(占位) │ 占位 5 路由         │    │
│  │ Stores   assetSnapshot / userConfig / operation / chat            │    │
│  │ Comps    Recharts Pie / CumulativeReturnCard / CategoryDetail    │    │
│  │ Tests    Vitest 12 files · 57/57 pass · jsdom env                 │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                     axios 拦截器 (X-User-Id: 1) + Vite proxy /api → 8080 │
├─────────────────────────────────────────────────────────────────────────┤
│  Backend  Spring Boot 3.3.5 + Java 17 + MyBatis-Plus 3.5.9              │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │ Controller  Screenshot / Snapshot / Asset / Chat / Conversation  │    │
│  │              CategoryMap / Settings / ParseLog                    │    │
│  │ Service     ScreenshotService / SnapShotConfirmService            │    │
│  │              AssetQueryService / SnapshotQueryService             │    │
│  │              CategoryMapService / SettingsService                 │    │
│  │              ChatService / ConversationService                    │    │
│  │ Mapper      MyBatis-Plus BaseMapper + 自定义 SQL/XML              │    │
│  │ AI          AiRouter (imageCount 路由) + VisionModelClient       │    │
│  │              + TextAiClient (minimax M3)                         │    │
│  │ Resilience  resilience4j retry / circuit breaker                  │    │
│  │ Cache       Caffeine (1024 entries / 24h TTL, key=file+prompt)   │    │
│  │ Tests       JUnit 5 + Mockito + AssertJ + JaCoCo ≥ 60%            │    │
│  └─────────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────────┤
│  Database  MySQL 8.0 + HikariCP (pool=10)                                │
│  asset_raw (holding/cumulative profit)  asset_snapshot (is_latest)      │
│  fund_category_map (source + last_seen + first_missing)                   │
│  chat_history (used_provider + fallback_triggered)  user_config            │
│  prompt_versions (screenshot_parser v2.7.1)  snapshot_meta (is_current)   │
│  settings (max_snapshot_age_days)                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 关键数据流

- **截图上传**：`POST /api/screenshot/upload` 接收 multipart，返回 `fileId`；`/api/screenshot/parse-batch?mode=single` 串行 4 次 `/parse`。
- **AI 推理**：`AiRouter` 根据 `imageCount` 选 primary（minimax M3 OPENAI_CHAT），互为 fallback；同 `(fileIds, promptVersion)` 走 Caffeine 24h cache。
- **去重确认**：`DedupEngine` 三级判定（top 一致 → top / 不一致 → visible_sum + TOP_INCONSISTENT / 全 null → visible_sum 无 warning），`user_correct` 优先级最高（决策 32）。
- **三表事务**：`SnapShotConfirmService.confirm` 在 `@Transactional` 内顺序：`asset_raw` upsert → `fund_category_map` upsert（user_correct 保护）→ `asset_snapshot` DELETE 清扫后 INSERT → `snapshot_meta` upsert + is_current 切换。
- **决策 27 双层快照**：`is_latest` per-date，`is_current` 全表唯一；`/api/snapshot/latest` 按 `is_current` 查询，不再 MAX(snapshot_date)。
- **决策 33 D7 消失-重现**：`last_seen_snapshot_date` + `first_missing_snapshot_date` 锚点算法，forward-only。
- **决策 25 v3 累计/持有**：`Σcumulative/Σamount`（`phase1_simple`），余额宝 holding=NULL 时用 cumulative 兜底，状态机 `normal / included / excluded_unknown`。

### 视觉模型双 provider 路由（决策 12 + 决策 28）

- **primary**：minimax M3（`MiniMax-M3` / `MiniMax-Text-01`），OpenAI Chat Completions 兼容
- **fallback 基础设施**：豆包 ARK（OpenAI Responses + OpenAI Chat），Phase 1a 阶段因 ARK 账户 vision 权限问题暂时未启用
- **cache key**：`fileId + promptVersion + imageCount`，同图 24h 复用
- **前端 timeout**：60s → 120s（决策 28 临时放宽）；后端 provider `timeoutSeconds=300` 通过 OkHttp 真正生效

---

## 仓库结构（polyrepo · 2026-07-25 现状）

```text
Fincontrol/                                       ← 工作区根（单仓多子目录，git polyrepo 协作）
├── docs/                                          ← 跨端架构 / 决策 / 验收文档
│   ├── README.md                                  ← 文档目录索引
│   ├── SETUP.md                                   ← 本地开发环境搭建（含 vision API Key 配置）
│   ├── requirements/                              ← 跨阶段产品基线（页面需求 v1.0 冻结）
│   ├── architecture/                              ← 架构主文档 + 4 轮评审记录
│   │   └── review/                                ← data-pipeline / algorithm / frontend / cross-module 4 份
│   ├── phase-0/                                   ← Phase 0 决策锁定 + db-schema + api-contract
│   │   └── decisions.md                           ← 决策 1–34（决策 34 = Phase 1 收官 + 路线重排）
│   └── phase-1/                                   ← Phase 1 验收 / 子阶段计划 / 子决策
│       ├── checklists/phase-1a.md + phase-1b.md  ← 24 + 23 + 4 P0 实时清单
│       ├── subphase-plan.md                       ← Phase 1a.1–1a.10 实施蓝图
│       ├── chat-prompt-issues.md                  ← chat prompt 已知缺陷追踪
│       ├── decisions/                             ← 子决策详细档（决策 27 / 30 / 31 / 32 / 33）
│       └── work-plans/1a + 1b/                    ← 各 PR 工作计划
│
│   └── test-records/                              ← 测试记录与产物
│       ├── manual-tests/                          ← 手动验收报告（按日期命名）
│       │   ├── 1a/                               ← Phase 1a 24 项 API 验收
│       │   └── 1b/                               ← Phase 1b 1b.1–1b.4 + 1b.4-pr6b/pr7 收尾
│       ├── automated-smoke/                       ← 自动化 smoke 脚本
│       ├── ocr-results/                           ← OCR 解析结果按日期归档
│       └── screenshots/                           ← 截图原始资料
│
├── fincontrol-backend/                            ← 【子仓库 1】Spring Boot 后端
│   ├── Dockerfile / pom.xml / .dockerignore
│   ├── scripts/                                   ← SQL / 数据迁移（1b3/00-snapshot-meta.sql 等）
│   ├── src/main/java/com/fincontrol/              ← ai / common / controller / dto / entity / mapper / service
│   ├── src/main/resources/                        ← application.yml + mybatis-config.xml
│   └── src/test/java/                             ← 单元 + H2 集成测试
│
├── fincontrol-frontend/                           ← 【子仓库 2】React + Vite 前端
│   ├── index.html / vite.config.js / package.json
│   └── src/
│       ├── App.jsx + main.jsx                     ← 路由 + 入口（含 ErrorBoundary）
│       ├── api/                                   ← axios 客户端 + endpoints 常量
│       ├── components/                            ← 业务组件 + common/ + layout/Sidebar
│       │   ├── common/                            ← ErrorBoundary, PlaceholderPage, StateShell
│       │   ├── home/                              ← 首页相关
│       │   └── data/                              ← 数据管理相关（HistoryLimitDialog 等）
│       ├── pages/                                 ← 路由页面
│       │   ├── HomePage.jsx（✅ 完整）
│       │   ├── DataPage.jsx（✅ 核心完成，Phase 2 补 UI）
│       │   ├── AIPage.jsx（⏳ 占位）
│       │   ├── ConfigPage.jsx（⏳ 占位，Phase 2）
│       │   ├── CorrectionPage.jsx（⏳ 占位，Phase 2）
│       │   ├── NAVPage.jsx（⏳ 占位，Phase 3）
│       │   ├── RatioPage.jsx（⏳ 占位，Phase 3）
│       │   └── QuarterlyPage.jsx（⏳ 占位，Phase 5）
│       ├── stores/                                ← Zustand stores（asset / user / operation / chat）
│       ├── styles/                                ← 全局 + 页面级 CSS（variables / global / sidebar / data-page / cumulative-return）
│       ├── utils/                                 ← formatters / blob / categoryColors
│       └── tests/                                 ← Vitest 12 文件 / 57 用例（jsdom env）
│
├── scripts/                                       ← 跨端运维 / smoke / git 辅助
│   ├── README.md
│   ├── 1b/                                        ← restart-backend.ps1（决策 24 强制）
│   ├── git/                                       ← push-deferred / 提交辅助
│   └── smoke/                                     ← smoke 测试入口
│
├── test/                                          ← 临时调试与联调记录（gitignored；决策 20）
│   └── 1b/                                        ← Phase 1b step 联调记录
│
├── docker-compose.yml                             ← 工作区级 compose（MySQL + backend，可选）
├── package.json / package-lock.json               ← 工作区级 workspaces
├── .gitignore                                     ← 工作区级忽略规则（含 test/、uploads/screenshots/）
└── README.md                                      ← 本文件
```

---

## 验证结果（2026-07-25）

### 阶段专项验收（Phase 1 真正可声明的状态）

| 验证项 | 结果 | 证据 |
|---|---|---|
| **1b.4-pr7 V1–V8 用户亲手跑通** | ✅ 8/8 | [PR7 最终验收报告](docs/test-records/manual-tests/1b/2026-07-25_1b4-pr7-acceptance-report.md) |
| **后端 `SnapShotConfirmServiceP7Test`** | ✅ 9/9 | `mvn -o test -Dtest=SnapShotConfirmServiceP7Test` 全绿（PR7 收官） |
| **后端 `SnapshotQueryServiceTest` 主页固收类修复** | ✅ | 1b.4-pr6b-recovery V6 |
| **前端 Vitest 单元测试** | ✅ 12 文件 / 57 用例 | `npm --prefix fincontrol-frontend test -- --run` 全绿 |
| **前端 Vite build** | ✅ 1.08s / 114 modules | `npm --prefix fincontrol-frontend run build` |
| **首页首屏 / 数据管理 / 快照列表 / 状态壳** | ✅ 用户验收 | 决策 33 PR4a + PR7 收官 |
| **真实 4 图 confirm** | ✅ 19 funds / ¥7,850.38 / 固收¥1,189.92 + 余额¥140.94 | 1b.4-pr6b-recovery V6 验证报告 |
| **决策 33 七条 UX 规则 + V6 修复** | ✅ 用户跑通 | [决策 33 子档](docs/phase-1/decisions/decision-33-pr6b-category-ux.md) |

### 1b 阶段累计交付

| 阶段 | 状态 | 关键交付 |
|---|---|---|
| **1b.1** 前端骨架 | ✅ | Vite + React + Router + Axios + Zustand + 4 store + 侧边栏（240px 可折叠）|
| **1b.2** 首页 + Recharts 环形图 | ✅ | HomePage + StateShell + 累计/持有卡片 + 决策 4 v2 |
| **1b.3** 数据管理 + 确认面板 | ✅ 核心 | 上传 → 解析 → preview → confirm 全链路，决策 27/32/33 |
| **1b.4** 设计债务修复 | ✅ 2026-07-25 收官 | PR0–PR7 共 7 PR，29 条设计 bug 全部关闭，决策 30/31/32/33 落地 |

### 历史基线（不要把历史数字与当前状态混为一谈）

| 基线 | 时点 | 结果 | 备注 |
|---|---|---|---|
| Phase 1a 全量 `mvn test` | 2026-07-19 | **241/241 PASS** | 1a.10 收官，决策 12 确认后数字 |
| Phase 1a.10 真实 4 图 E2E | 2026-07-19 | 19 funds / ¥7,884.68 跑通 | 路径 A 4×单图 + 路径 B 1×parse-batch |
| Phase 1a JaCoCo 行覆盖 | 2026-07-19 | ~77% | 阈值 ≥ 60% |

### 当前全量 Maven 测试的诚实口径（**注意区分**）

- `npm test`、`npm run build`、1b.4-pr7 专项后端测试、PR7 V1–V8 手工验收**全部通过**。
- 当前的 `mvn test` 全量运行会暴露 3 failures + 55 errors，集中在测试装配和 fixture：H2 集成测试没有 `settings` 表、若干 `WebMvcTest` 在 `local` profile 下被加载到没有 `SqlSessionFactory` 的 mapper。这属于**测试环境/测试基线债务**，不是 1b.4 用户链路的功能故障。
- 这部分**不构成 Phase 1 退出条件**，但会作为 Phase 4「前端整体收尾 / 全量测试基线」的一项工程任务整改。
- 未来修复路径（建议在 Phase 4 优先级 P1）：
  - H2 schema 同步新增 `settings`、`fund_category_map.last_seen_snapshot_date` 等列；
  - `WebMvcTest` 用 `@MockBean` 显式替代 `assetRawQueryMapper` 等带 SqlSessionFactory 依赖的 bean，或在测试配置中 `@SpringBootTest` 加载 MyBatis。

---

## 视觉模型 API Key 配置

> **当前推荐**：1a.5 起使用 minimax M3 多模态模型（M3 原生支持图像/语音/音乐）。1a.8 引入双 provider 路由（minimax + 豆包），但豆包路径在 1a.10 实测 4 个 model 全部失败（决策 12），**Phase 1 按 minimax-only 验收通过**。豆包代码完整保留，未来可重启用。

1. 访问 [https://api.minimaxi.chat/](https://api.minimaxi.chat/) 注册并获取 API Key
2. 编辑 `fincontrol-backend/src/main/resources/application-local.yml`（已在 .gitignore 中）：

   ```yaml
   fincontrol:
     ai:
       text:
         api-key: <YOUR_MINIMAX_API_KEY_HERE>   # MiniMax-Text-01（chat / intent 路由）
       vision:
         minimax:
           api-key: <YOUR_MINIMAX_API_KEY_HERE>   # MiniMax-M3（多模态）
   ```

3. 或用环境变量覆盖：

   ```cmd
   set TEXT_AI_API_KEY=eyJxxxxx...
   set VISION_API_KEY=eyJxxxxx...
   ```

加载优先级：**环境变量 > application-local.yml > application.yml 占位符（启动时抛 5001/5002 强制填）**

详细配置见 [docs/SETUP.md](docs/SETUP.md) §2。

---

## 后端技术栈

- Java 17 + Spring Boot 3.3.5
- MyBatis-Plus 3.5.9（DAO + SqlSessionFactory 自动配置）
- MySQL 8.0 + HikariCP（pool size = 10）
- minimax M3 / OpenAI-compatible chat completions（视觉多模态 primary）
- 豆包 ARK / OpenAI Responses（fallback 基础设施，决策 12 暂时废弃）
- OkHttp 4.12（双 provider 独立连接池 + 5min read timeout，决策 28）
- springdoc-openapi 2.6 + Swagger UI
- JUnit 5 + Mockito + AssertJ（单元 + H2 集成）
- resilience4j（重试 + 熔断）
- Caffeine（vision cache 1024 entries / 24h TTL）

### Phase 1a 关键性能指标

| 指标 | 实测值 | 备注 |
|---|---|---|
| `mvn test` 通过率 | 241/241（1a.10 收官历史基线）| 当前全量 Maven 状态见上文「诚实口径」|
| 真实 4 图灰测 | 19/19 fund + 19/19 amount = 100% 匹配 DeepSeek baseline | Phase 1a.10 |
| 持有 + 累计收益双字段 | 19/19 唯一 / 总额 ¥7,884.68 | decision-7 + decision-25 |
| 同 JVM cache HIT 加速 | **460x**（38s → 0.08s）| Caffeine 24h TTL |
| 路径 A 真实 confirm | 19/7/19 跑通 | decision-13 dataTime 覆盖 |
| 决策 13 dataTime 覆盖 | ✅ PASS | 前端 EXIF / 用户选择 → 后端 override AI 解析值 |

---

## 前端技术栈（Phase 1 已完成部分）

- React 18 + React Router 6
- Vite 5（dev server 默认 5173，决策 29 统一）
- ECharts 移除 → **Recharts 2.12**（环形图，决策 16）
- Ant Design 移除 → **纯手写 CSS**（决策 17，< 50 行/组件）
- Axios（拦截器 + X-User-Id 默认 1，决策 19 + 22 + 23）
- Zustand 5（4 stores：assetSnapshot / userConfig / operation / chat）
- Vitest 2.1 + jsdom + Testing Library（57 用例，决策 13/14/24 + 修正）
- Puppeteer 25（仅 devDependency，决策 13 E2E 留作 Phase 4）

### 已实现路由

| 路径 | 页面 | Phase | 状态 |
|---|---|---|---|
| `/` | HomePage（首页资产看板）| 1 | ✅ 完成 |
| `/data` | DataPage（数据管理）| 1 | ✅ 核心完成（Phase 2 补 UI）|
| `/ai` | AIPage（AI 顾问）| 1→5 | ⏳ 后端 API 完整 / 前端占位 |
| `/config` | ConfigPage（资产配置）| 2 | ⏳ 占位 |
| `/correction` | CorrectionPage（月度操作台）| 2 | ⏳ 占位 |
| `/nav` | NAVPage（净值曲线）| 3 | ⏳ 占位 |
| `/ratio` | RatioPage（比例演化）| 3 | ⏳ 占位 |
| `/quarterly` | QuarterlyPage（季度操作台）| 5 | ⏳ 占位 |

详见 `fincontrol-frontend/package.json` 和 `src/App.jsx`（RouterShell + 8 路由 + 标题同步）。

---

## 设计文档导航

| 文档 | 路径 | 说明 |
|---|---|---|
| **Phase 0 决策汇总（含决策 34）** | [docs/phase-0/decisions.md](docs/phase-0/decisions.md) | 决策 1–34 权威汇总表位于文档最末；决策 34 锁定 Phase 1 收官 + 后续路线 |
| **Phase 1a 验收清单** | [docs/phase-1/checklists/phase-1a.md](docs/phase-1/checklists/phase-1a.md) | 24 项 API + 8 项 P0 实时清单 |
| **Phase 1b 验收清单（历史编号）** | [docs/phase-1/checklists/phase-1b.md](docs/phase-1/checklists/phase-1b.md) | 23 项 UI + 4 项 P0 + 1b.3 补救 + 1b.4 设计债务收尾 |
| **Phase 1 子阶段计划** | [docs/phase-1/subphase-plan.md](docs/phase-1/subphase-plan.md) | 1a.1–1a.10 实施蓝图 |
| **全站页面需求 v1.0（冻结）** | [docs/requirements/2026-07-22_fincontrol-page-requirements.md](docs/requirements/2026-07-22_fincontrol-page-requirements.md) | 8 页面 / 全局样式 / 数据口径宪法 |
| **1b.4-pr7 最终验收报告** | [docs/test-records/manual-tests/1b/2026-07-25_1b4-pr7-acceptance-report.md](docs/test-records/manual-tests/1b/2026-07-25_1b4-pr7-acceptance-report.md) | V1–V8 全部通过，1b.4 圆满收官 |
| **1b.4-pr6b 主页固收类 + stale-cache 修复** | [docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md](docs/test-records/manual-tests/1b/2026-07-25_1b4-pr6b-acceptance-report.md) | 决策 33 V6 收尾证据 |
| **数据流水线评审（4 轮汇总入口）** | [docs/architecture/review/](docs/architecture/review/) | 199 个评审问题 + 4 份评审记录 |
| **技术设计文档 v2.0（基线）** | [docs/architecture/FinControl 技术设计文档v2.docx](docs/architecture/FinControl%20技术设计文档v2.docx) | 2026-06-10，11 章 + 2 附录 |
| **后端启动 + 视觉模型 Key 配置** | [docs/SETUP.md](docs/SETUP.md) | MySQL 初始化、Key 配置、启动命令 |

---

## 本地开发快速启动

```cmd
:: 1. 启动 MySQL，建库 + 跑 schema
mysql -u root -p
  CREATE DATABASE fincontrol DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
  EXIT;
mysql -u root -p fincontrol < docs/phase-0/db-schema.sql
mysql -u root -p fincontrol < docs/phase-0/seed-data.sql

:: 2. 编辑 application-local.yml 填 minimax Key
notepad fincontrol-backend\src\main\resources\application-local.yml

:: 3. 后端启动（统一用决策 24 脚本）
powershell -ExecutionPolicy Bypass -File scripts\1b\restart-backend.ps1

:: 4. 前端启动（决策 29 统一 5173）
cd fincontrol-frontend
npm install
npm run dev
```

启动后访问：

- 前端 http://localhost:5173/
- 后端 Swagger UI http://localhost:8080/swagger-ui.html
- 后端 API 文档 http://localhost:8080/v3/api-docs
- 后端健康检查 http://localhost:8080/actuator/health

完整说明：[docs/SETUP.md](docs/SETUP.md) + [docs/phase-1/USER-MANUAL.md](docs/phase-1/USER-MANUAL.md)。

---

## 与其他项目的关系

- **微控金融（鸿蒙版）**：v1.0 已封存，FinControl 的前身原型
- **DecisionMate**：技术共生项目，共享 React 组件

详见技术设计文档 1.4、1.5 节。

---

## 维护者

- 刘博丞（项目作者）
- 评审者：架构审查助手（2026-07-09 四轮评审）

---

## 决策变更记录

| 日期 | 变更 | 触发 |
|---|---|---|
| 2026-07-09 | 决策 1–6 锁定（Phase 0 基础设施 + Phase 计划）| Phase 0 评审 |
| 2026-07-18 | 决策 7（profit 拆分 holding + cumulative）+ 决策 8（类别归一化）| 1a.8 真实四图闭环 |
| 2026-07-19 | 决策 12（豆包 vision 暂时废弃）+ 决策 13（dataTime 覆盖）+ 1a.10 收官 | 1a.10 真实 E2E |
| 2026-07-22 | 决策 25 v3（持有收益 Smart Fallback）+ 决策 26（parse single/multi toggle）| 1b.2 step 7 |
| 2026-07-24 | 决策 30 / 31（settings 表）+ 决策 32（user_correct 优先）| 1b.4 PR3plus |
| 2026-07-25 | 决策 33（七条 UX + V6 收尾）+ 决策 34（Phase 1 收官 + 路线重排）| 1b.4 PR6b / PR7 收官 |

---

*Phase 1 收官完成时间：2026-07-25 12:42（Asia/Shanghai）*
*最新 commit：`4756b2a`（1b.4-pr7 Fix 8 · success banner 双段动画）*
*权威阶段计划：[docs/phase-0/decisions.md#决策-34phase-1-里程碑收官与后续阶段重排2026-07-25](docs/phase-0/decisions.md#决策-34phase-1-里程碑收官与后续阶段重排2026-07-25)*
