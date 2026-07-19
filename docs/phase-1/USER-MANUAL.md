# Phase 1a 使用说明书

> **目标读者**：后端开发、前端开发（1b）、AI 顾问、运维、code review  
> **版本**：v0.1.0（2026-07-20 02:30 Asia/Shanghai）  
> **代码基线**：`commit 5ac9574`（1a.10 决策 13 实施补全 + Test 1B + E2E 验证）  
> **配套文档**：[Phase 0 决策](docs/phase-0/decisions.md) | [API 契约](docs/phase-0/api-contract.md) | [1a 整体验收](docs/test-records/manual-tests/2026-07-19_phase1a-acceptance.md)

---

## 目录

1. [简介与状态](#1-简介与状态)
2. [快速开始（5 分钟跑通）](#2-快速开始5-分钟跑通)
3. [系统架构](#3-系统架构)
4. [核心功能详解](#4-核心功能详解)
   - 4.1 [上传截图](#41-上传截图)
   - 4.2 [AI 解析（单图）](#42-ai-解析单图)
   - 4.3 [AI 解析（多图 batch）](#43-ai-解析多图-batch)
   - 4.4 [重新解析](#44-重新解析)
   - 4.5 [快照查询](#45-快照查询)
   - 4.6 [快照确认（10 秒撤销）](#46-快照确认10-秒撤销)
   - 4.7 [月度校正（数学求解器）](#47-月度校正数学求解器)
   - 4.8 [配置管理](#48-配置管理)
   - 4.9 [基金大类映射（user override）](#49-基金大类映射user-override)
   - 4.10 [大类主数据管理](#410-大类主数据管理)
   - 4.11 [AI 顾问（多轮对话）](#411-ai-顾问多轮对话)
5. [dataTime 字段（决策 13）](#5-datetime-字段决策-13)
6. [AI 路由与缓存机制](#6-ai-路由与缓存机制)
7. [数据模型（主要表）](#7-数据模型主要表)
8. [错误码参考](#8-错误码参考)
9. [测试与验证](#9-测试与验证)
10. [故障排查](#10-故障排查)
11. [已知限制 + 后续工作](#11-已知限制--后续工作)
12. [附录：关键决策与变更日志](#12-附录关键决策与变更日志)

---

## 1. 简介与状态

### 1.1 什么是 FinControl？

FinControl 是一个**个人资产配置控制系统**：

- **理论基础**：控制论（feedback loop、目标偏差修正）
- **实验平台**：真实个人账户（支付宝/天天基金等）
- **核心引擎**：数学求解器（月度校正方程组）

### 1.2 Phase 1a 是什么？

Phase 1a 是**后端核心能力**（Spring Boot + AI 集成）：

| 类别 | 能力 |
|---|---|
| 截图处理 | 上传 → AI 视觉模型解析 → 基金明细提取 |
| 数据存储 | 资产快照 + 基金分类映射 + 用户配置 + 操作日志 |
| 查询服务 | 快照查询、首页辅助、配置 CRUD |
| AI 顾问 | 意图分类 + 多轮对话 + system prompt 切换 |
| 韧性 | 双 provider 路由 + Caffeine cache + resilience4j 重试/熔断 |
| 校验 | 路径 A/B 双路径 + DedupEngine 去重 + DISCREPANCY 1% 报警 |

### 1.3 Phase 1a 状态

- ✅ **mvn test**：241/241 PASS（无回归）
- ✅ **真实 E2E**：20260716 灰测，19/19 fund 100% 匹配 DeepSeek
- ✅ **缓存验证**：同 JVM 重跑 460x 加速
- ✅ **决策 13 dataTime**：后端 override AI 解析值 + E2E 验证

详细数据见 [1a 整体验收报告](docs/test-records/manual-tests/2026-07-19_phase1a-acceptance.md)。

### 1.4 1a vs 1b 边界

| 1a（已完成）| 1b（待启动）|
|---|---|
| 后端 API 全闭环 | 前端 UI（React + ECharts）|
| AI 集成与缓存 | 截图上传组件 + 解析结果展示 |
| 数据持久化 | 余额类下拉 + 10s 撤销按钮 |
| AI 顾问 chat | 多轮对话 UI + 意图分类展示 |
| 数学求解器 | 月度校正操作台 UI |

> **1a 不依赖前端**：所有 API 可通过 curl/Postman/Swagger UI 直接测试。  
> **前端联调参考本文档 §4**：所有端点 + 请求/响应 + 边界场景。

---

## 2. 快速开始（5 分钟跑通）

### 2.1 前置条件

| 工具 | 版本 | 检查 |
|---|---|---|
| Java | 17 (LTS) | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| MySQL | 8.0+ | `mysql --version` |
| 视觉模型 Key | minimax M3 | https://api.minimaxi.chat/ 注册 |

### 2.2 启动步骤

```cmd
:: Step 1: 启动 MySQL，建库 + 初始化 schema
mysql -u root -p < docs/phase-0\db-schema.sql
mysql -u root -p < docs\phase-0\seed-data.sql

:: Step 2: 配置 minimax Key（已在 .gitignore，不入仓）
notepad fincontrol-backend\src\main\resources\application-local.yml
```

`application-local.yml` 内容：
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

```cmd
:: Step 3: 启动后端（首次会 mvn install + spring-boot:run）
cd fincontrol-backend
mvn -B test -DfailIfNoTests=false       :: 跑全量测试（241 个）
mvn spring-boot:run                       :: 启动后端，监听 :8080

:: Step 4: 验证后端启动
curl http://localhost:8080/actuator/health
:: 预期：{"status":"UP"}

:: Step 5: 访问 Swagger UI
:: 浏览器打开 http://localhost:8080/swagger-ui.html
```

### 2.3 5 分钟 Demo：上传一张支付宝截图并解析

```bash
# 1) 上传图片（multipart/form-data）
curl -X POST -H "X-User-Id: 1" \
  -F "file=@/path/to/alipay-screenshot.jpg" \
  http://localhost:8080/api/screenshot/upload
# 响应: {"code":0,"data":{"fileId":"a808aad6-...","fileUrl":"/tmp/...","uploadedAt":"..."}}

# 2) 调用解析（注意：3-5 分钟等待 vision 模型）
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"fileId":"a808aad6-...","userId":1,"dataTime":"2026-07-15"}' \
  http://localhost:8080/api/screenshot/parse
# 响应：19 fund / 6 categories / top=7850.38 / snapshotDate=2026-07-15

# 3) 查询最新快照
curl "http://localhost:8080/api/snapshot/latest?includeDetail=true"
```

---

## 3. 系统架构

### 3.1 后端技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Java | 17 LTS | 主语言 |
| Spring Boot | 3.3.5 | Web 框架 + 自动配置 |
| MyBatis-Plus | 3.5.9 | ORM + SQL Builder |
| MySQL | 8.0 | 主数据库 |
| HikariCP | 内置 | 连接池（pool size = 10）|
| OkHttp | 4.12 | HTTP 客户端（5min read timeout）|
| minimax M3 | - | 视觉多模态 primary |
| 豆包 ARK | - | vision fallback（决策 12 暂废）|
| springdoc-openapi | 2.6 | Swagger UI 自动生成 |
| JUnit 5 + Mockito | - | 单元测试 |
| H2 | - | 集成测试 in-memory DB |
| resilience4j | - | 重试 + 熔断 |
| Caffeine | - | vision cache（1024 entries / 24h TTL）|

### 3.2 关键组件

```
HTTP Request
    ↓
Controller 层 (REST API)
    ↓
Service 层（业务编排）
    ├── ScreenshotService（截图 + AI 解析）
    ├── SnapShotConfirmService（确认入库 + 10s 撤销）
    ├── SnapShotQueryService（查询）
    ├── CategoryMapService（基金分类映射）
    ├── CategoryMasterService（大类主数据）
    ├── FundCategoryResolver（user_correct > alias > raw，决策 8）
    ├── DedupEngine（基金去重 + top/sum 校验，决策 9）
    └── ChatService（AI 顾问）
    ↓
AI 路由层 (AiRouter)
    ├── imageCount ≤ 2 → minimax primary
    ├── imageCount > 2 → minimax primary（决策 12 暂废豆包）
    └── retry × 2 + CB（10 窗口 50% 失败率）
    ↓
Data Access 层 (MyBatis-Plus Mapper)
    ↓
MySQL 8.0
```

### 3.3 数据流（截图解析 → 入库）

```
1. POST /api/screenshot/upload          ← multipart/form-data
   ↓ 返回 fileId
2. POST /api/screenshot/parse            ← JSON {fileId, userId, dataTime?}
   ↓ ScreenshotService.parse()
   ↓ ① chat_history 写 user 消息
   ↓ ② AiRouter.callVision()            ← Caffeine cache check
   ↓ ③ minimax M3 vision call           ← 30-300s 真实推理
   ↓ ④ VisionModelClient.extractFirstJsonObject()
   ↓ ⑤ mapToParsedAsset()               ← 解析为 ParsedAsset
   ↓ ⑥ applyDataTimeOverride()          ← 决策 13：dataTime 覆盖 AI snapshot_date
   ↓ ⑦ hasCompleteFund()                ← 1a.10：零基金校验
   ↓ ⑧ chat_history 写 assistant 消息
   ↓ 返回 ParsedAsset

3. POST /api/snapshot/confirm           ← 业务方确认大类归属
   ↓ SnapShotConfirmService.confirm()
   ↓ ① DedupEngine.deduplicate()        ← 基金去重
   ↓ ② asset_raw INSERT (1 行 / 基金)
   ↓ ③ asset_snapshot UPSERT (1 行 / 大类)
   ↓ ④ fund_category_map UPSERT (1 行 / 基金×用户)
   ↓ ⑤ 事务提交，10 秒内可撤销
```

---

## 4. 核心功能详解

### 4.1 上传截图

**用途**：将支付宝截图上传到后端临时存储，返回 `fileId`（不入库，不调 AI）。

**API**：
```
POST /api/screenshot/upload
Content-Type: multipart/form-data
Body: file=<binary>（jpg/png/webp，≤ 10MB）
```

**curl 示例**：
```bash
curl -X POST -H "X-User-Id: 1" \
  -F "file=@/path/to/screenshot.jpg" \
  http://localhost:8080/api/screenshot/upload
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "fileId": "a808aad6-165f-4bf4-bf4c-992cedc605de",
    "fileUrl": "/tmp/uploads/2026-07-20/a808aad6-...jpg",
    "uploadedAt": "2026-07-20T02:30:45"
  }
}
```

**注意事项**：
- 上传后图片存到 `uploads/` 目录（gitignored）
- `fileId` 在解析完成后保留 24h（可重新解析）
- 错误码 1001：fileId 缺失 / 格式错误 / > 10MB

---

### 4.2 AI 解析（单图）

**用途**：调 minimax M3 视觉模型，解析单张截图，返回基金明细 JSON。

**API**：
```
POST /api/screenshot/parse
Content-Type: application/json
Body: {
  "fileId": "uuid-xxxx",
  "userId": 1,
  "dataTime": "2026-07-15"  // 1a.10 决策 13：可选，截图真实数据日期
}
```

**curl 示例**：
```bash
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"fileId":"a808aad6-...","userId":1,"dataTime":"2026-07-15"}' \
  http://localhost:8080/api/screenshot/parse
```

**响应**（成功）：
```json
{
  "code": 0,
  "data": {
    "conversationId": "conv-a808aad6-...",
    "snapshotDate": "2026-07-15",  ← 被 dataTime 覆盖
    "totalAsset": 7850.38,
    "totalAssetSource": "top",
    "sixCategoriesTotal": 7563.83,
    "balanceFund": 286.55,
    "dedupedFundSum": 7563.83,
    "categories": [
      {
        "categoryName": "货币类",
        "categoryTotal": 796.32,
        "categoryPercentage": 10.53,
        "targetRatio": 10,
        "deviation": 0.53,
        "funds": [
          {
            "fundName": "中加货币E",
            "amount": 796.32,
            "holdingProfit": 2.32,
            "cumulativeProfit": 2.32,
            "isUserConfirmed": false,
            "confirmedAt": null
          }
        ]
      }
    ],
    "matchedFunds": ["中加货币E", "..."],
    "unmatchedFunds": [],
    "aiMarkdownReport": "### 支付宝资产明细\n...",
    "usedProvider": "minimax",
    "fallbackTriggered": false,
    "cacheHit": false
  }
}
```

**性能**：
- **首次解析**（cache MISS）：30-300s（真实 minimax M3 推理）
- **同 JVM 重解析**（cache HIT）：0.08-0.12s = **460x 加速**
- 缓存键 = (fileId, promptVersion, imageCount)，24h TTL

**错误码**：
- 3001（VISION_INVALID_JSON）：minimax 返回非 JSON
- 3002（VISION_TIMEOUT）：minimax 调用超时（> 300s）
- 3003（VISION_ZERO_FUNDS）：AI 提取 0 只完整基金
- 5001（INTERNAL_ERROR）：服务器内部错误

**1a.10 决策 13 dataTime 字段**：
- 提供 → 覆盖 AI 提取的 `snapshot_date`
- 不提供 → 保持 AI 提取值
- 优先级：`req.dataTime` > `mapToParsedAsset` AI 解析值 > `LocalDate.now()`

**注意事项**：
- 路径 A：每张图调一次 `parse`，最后汇总
- 路径 B：见 §4.3（一次 4 图 batch）
- `cacheHit=true` 表示从 Caffeine 缓存返回，无需调 AI

---

### 4.3 AI 解析（多图 batch）

**用途**：一次性提交 1-10 张图，让 vision 模型一次推理（更准确、更省时），返回合并 JSON + DedupEngine 去重报告。

**API**：
```
POST /api/screenshot/parse-batch
Content-Type: application/json
Body: {
  "userId": 1,
  "fileIds": ["uuid-1", "uuid-2", "uuid-3", "uuid-4"],
  "dataTime": "2026-07-16"  // 1a.10 决策 13：可选
}
```

**curl 示例**：
```bash
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"userId":1,"fileIds":["a808-...","7ac58b-...","f6232-...","3730f-..."]}' \
  http://localhost:8080/api/screenshot/parse-batch
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "imageCount": 4,
    "parsedAsset": {
      "conversationId": "conv-batch-uuid",
      "snapshotDate": "2026-07-15",
      "totalAsset": 7884.68,
      "totalAssetSource": "top",
      "dedupedFundSum": 7884.68,
      "sixCategoriesTotal": 7563.83,
      "balanceFund": 320.85,
      "categories": [ /* 6 大类 + 19 funds */ ],
      "matchedFunds": ["中加货币E", "..."],
      "unmatchedFunds": []
    },
    "dedupReport": {
      "inputRecordCount": 20,
      "mergedRecordCount": 19,
      "droppedCount": 1,
      "warnings": [
        { "code": "TOP_INCONSISTENT", "message": "...", "context": {} }
      ]
    },
    "usedProvider": "minimax",
    "fallbackTriggered": false,
    "cacheHit": false
  }
}
```

**顶部总资产三级判定**（决策 9 + 1a.10）：

| 情景 | totalAsset | totalAssetSource | 报警 |
|---|---|---|---|
| 4 页 `top` 一致 | `top` 数值 | `top` | 无 |
| 4 页 `top` 不一致 | `dedupedFundSum` | `visible_sum` | `TOP_INCONSISTENT` |
| 4 页 `top` 全 null | `dedupedFundSum` | `visible_sum` | 无 |
| 有 top 且偏差 > 1% | `top` | `top` | `DISCREPANCY` |

**已知限制**：
- 路径 B 在 minimax 4 图 batch 时**偶发 timeout**（PRODUCTION_BLOCKED 已知）
- 推荐生产用**路径 A**（4×单图 + 后端汇总）保证可用性

**错误码**：
- 1001：fileIds 为空 / 重复 / 数量 > 10 / userId 缺失
- 3001/3002/3003：与 §4.2 同

---

### 4.4 重新解析

**用途**：基于已存在的 conversationId，重新调 vision 模型（用于"重新解析"按钮，AI 推理有随机性）。

**API**：
```
POST /api/screenshot/reparse
Content-Type: application/json
Body: {
  "conversationId": "conv-a808aad6-...",
  "dataTime": "2026-07-15"  // 1a.10 决策 13：可选
}
```

**curl 示例**：
```bash
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"conversationId":"conv-a808aad6-..."}' \
  http://localhost:8080/api/screenshot/reparse
```

**响应**：同 §4.2（成功）或 §4.2（错误码）

**注意事项**：
- 会用原 fileId 重新读图
- 缓存键含 fileId，**24h 内第二次 reparse 是 cache HIT**（0.08s）
- 不会写入 chat_history（避免污染审计日志）

---

### 4.5 快照查询

**用途**：查询资产快照（首页 + 历史列表 + 单日详情）。

#### 4.5.1 最新快照（首页）

```
GET /api/snapshot/latest?includeBalance=true&includeDetail=false
```

**curl 示例**：
```bash
curl "http://localhost:8080/api/snapshot/latest?includeDetail=true"
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "snapshotDate": "2026-07-15",
    "snapshotConfirmedAt": "2026-07-15T20:30:45",
    "sixCategoriesTotal": 7563.83,
    "balanceFund": 286.55,
    "totalAssetWithBalance": 7850.38,
    "categories": [
      {
        "categoryName": "货币类",
        "categoryTotal": 796.32,
        "actualRatio": 10.53,
        "targetRatio": 10,
        "deviation": 0.53,
        "fundCount": 1,
        "funds": [ /* includeDetail=true 时填充 */ ]
      }
    ]
  }
}
```

#### 4.5.2 指定日期快照

```
GET /api/snapshot/{date}  // date 格式 YYYY-MM-DD
```

**curl 示例**：
```bash
curl "http://localhost:8080/api/snapshot/2026-07-15"
```

**响应 404**：
```json
{ "code": 2001, "message": "该日期无快照数据" }
```

#### 4.5.3 历史列表（分页）

```
GET /api/snapshot/history?from=2026-01-01&to=2026-07-20&page=1&pageSize=20
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "snapshotDate": "2026-07-15",
        "sixCategoriesTotal": 7563.83,
        "balanceFund": 286.55,
        "categoryCount": 6,
        "confirmedAt": "2026-07-15T20:30:00"
      }
    ],
    "total": 30,
    "page": 1,
    "pageSize": 20
  }
}
```

---

### 4.6 快照确认（10 秒撤销）

**用途**：将 `parse` / `parse-batch` 返回的 ParsedAsset 确认写入 `asset_raw` + `asset_snapshot` + `fund_category_map` 三表（事务）。

**API**：
```
POST /api/snapshot/confirm
Content-Type: application/json
Body: {
  "userId": 1,
  "snapshotDate": "2026-07-15",
  "confirmedOverwrite": false,
  "includeBalance": true,
  "parsedAssets": [ /* 1 个或多个 ParsedAsset */ ]
}
```

**curl 示例**：
```bash
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{
    "userId":1,
    "snapshotDate":"2026-07-15",
    "confirmedOverwrite":false,
    "includeBalance":true,
    "parsedAssets":[
      {
        "conversationId":"conv-batch-uuid",
        "snapshotDate":"2026-07-15",
        "totalAsset":7884.68,
        "totalAssetSource":"top",
        "categories":[ ... ]
      }
    ]
  }' \
  http://localhost:8080/api/snapshot/confirm
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "assetRawInserted": 19,
    "assetSnapshotUpserted": 7,
    "dedupReport": {
      "inputRecordCount": 20,
      "mergedRecordCount": 19,
      "droppedCount": 1,
      "warnings": []
    },
    "warnings": [],
    "rollbackAvailable": true,
    "rollbackDeadline": "2026-07-15T20:30:10"
  }
}
```

**10 秒撤销**：
```
DELETE /api/snapshot/confirm/{snapshotId}
```

**curl 示例**：
```bash
curl -X DELETE -H "X-User-Id: 1" \
  "http://localhost:8080/api/snapshot/confirm/{snapshotId}"
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "rolledBack": true,
    "snapshotDate": "2026-07-15",
    "previousSnapshotRestored": null,
    "assetRawUpdated": 19,
    "assetSnapshotUpdated": 7
  }
}
```

**状态码**：
- 200：撤销成功
- 404：snapshotId 不存在
- 410：超过撤销时限（10 秒）→ `code 2003`

**错误码**：
- 2001：对话不存在
- 2002：同日已有快照 + 未 `confirmedOverwrite: true`
- 2003：超过 10 秒撤销时限

**事务保证**：
- 三表 INSERT/UPDATE 在一个 @Transactional 中
- 任何失败 → 全部回滚（10 秒内可重新 confirm）

---

### 4.7 月度校正（数学求解器）

**用途**：基于控制论反馈环，每月计算"应补多少货币类 / 固收类"以保持目标比例。

#### 4.7.1 获取默认值

```
GET /api/correction/defaults
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "vTotalSixCategories": 7563.83,
    "vMonetary": 796.32,
    "vBond": 954.24,
    "vHighVol": 4885.38,
    "uHigh": 560.00,
    "targetRatios": {
      "货币类": 10,
      "固收类": 15,
      "商品类": 25,
      "A股权益类": 25,
      "海外权益类": 20,
      "港股大中华类": 5
    },
    "budgetLimit": 1000,
    "purchaseThreshold": 100,
    "snapshotDate": "2026-07-15"
  }
}
```

#### 4.7.2 计算（不写入）

```
POST /api/correction/monthly/calculate
Body: { vCurr, vMonetary, vBond, uHigh, pMonetary, pBond, pHigh, budgetLimit, purchaseThreshold }
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "deltaMTheory": 169.79,
    "deltaBTheory": 212.65,
    "uMonetaryDca": 89.21,
    "uBondDca": 113.79,
    "totalInvestment": 1146.45,
    "roundingSuggestion": {
      "deltaMSuggested": 170,
      "deltaBSuggested": 220,
      "roundingStrategy": "round_up_10",
      "deviationMSuggested": 0.12,
      "deviationBSuggested": 0.07
    },
    "warnings": [
      { "type": "exceed_budget_limit", "message": "理论总投入 1146.45 超出 budgetLimit 1000" }
    ]
  }
}
```

#### 4.7.3 实时重算（取整弹窗内调用）

```
POST /api/correction/monthly/recalculate
Body: { vCurr, vMonetary, vBond, uHigh, deltaMActual, deltaBActual, uMonetaryDcaActual, uBondDcaActual }
```

**响应**：newRatios + deviations + overBudgetLimit

#### 4.7.4 确认写入

```
POST /api/correction/monthly/confirm
Body: { vCurr, vMonetary, vBond, uHigh, deltaMTheory, deltaBTheory, deltaMActual, deltaBActual, roundingStrategy, snapshotDate, userId }
```

**响应**：`operationLogId` + `writtenToOperationLog: true`

**数学模型**（简化版）：

设 vCurr = 六大类合计（不含余额类），pM + pB + pH = 100%。

方程组：
```
deltaM + uM_DCA = pM × (vCurr + deltaM + deltaB) / 100 - vM
deltaB + uB_DCA = pB × (vCurr + deltaM + deltaB) / 100 - vB
uM_DCA + uB_DCA + deltaM + deltaB = uHigh
```

求解 (deltaM, deltaB, uM_DCA, uB_DCA) → 取整到 10 的倍数 → 偏差校验。

---

### 4.8 配置管理

#### 4.8.1 获取当前配置

```
GET /api/config
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "targetRatios": { "货币类": 10, "固收类": 15, ... },
    "budgetLimit": 1000,
    "purchaseThreshold": 100,
    "highVolDcaBudget": 560
  }
}
```

#### 4.8.2 保存配置

```
POST /api/config
Body: {
  "targetRatios": { ... },
  "budgetLimit": 1000,
  "purchaseThreshold": 100,
  "highVolDcaBudget": 560,
  "userId": 1
}
```

**响应**：
```json
{ "code": 0, "data": { "savedAt": "...", "triggeredSnapshotUpdate": false } }
```

**注意**：仅写入 `user_config` 表，不级联更新 `asset_snapshot.target_ratio`（决策 2.2.1）。

---

### 4.9 基金大类映射（user override）

**用途**：用户对 AI 推断的基金分类做手动修正（mixed-asset 边界 case 关键）。

#### 4.9.1 批量查询

```
GET /api/category-map/match?userId=1&funds=中加货币E,长城短债债券A,未知基金
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "matchedFunds": [
      {
        "fundName": "中加货币E",
        "category": "货币类",
        "source": "ai_guess",
        "isUserConfirmed": false,
        "lastSeenAt": "2026-07-15T20:30:00"
      }
    ],
    "unmatchedFunds": ["未知基金"]
  }
}
```

#### 4.9.2 更新单条（user_correct）

```
POST /api/category-map/update
Body: { "fundName": "某基金", "category": "商品类", "userId": 1 }
```

**响应**：`source: "user_correct"`，优先级最高

#### 4.9.3 删除 / 重置 / stale 列表

- `DELETE /api/category-map/{userId}/{fundName}`：删除 user_correct
- `POST /api/category-map/reset`：重置为 ai_guess
- `GET /api/category-map/stale?userId=1&days=90`：列 90 天未使用的 user_correct

**优先级（决策 8）**：`user_correct` > `any-source` (fromAlias) > `raw` (AI 原始)

**mixed-asset 边界 case**：如"安信新价值"既是 A股权益类又是 固收类，由 user_correct 决定（决策 14 前端 UI 跟进）。

---

### 4.10 大类主数据管理

**用途**：管理 6 大类的主数据（名称、别名、激活状态），用于 AI 归一化（决策 8）。

#### 4.10.1 列出大类

```
GET /api/category-master?includeInactive=false
```

**响应**：
```json
{
  "code": 0,
  "data": [
    { "id": 1, "nameCanonical": "货币类", "aliases": ["货币", "货基"], "active": true, ... },
    { "id": 2, "nameCanonical": "固收类", "aliases": ["固收", "债基"], "active": true, ... }
  ]
}
```

#### 4.10.2 新增 / 更新 / 删除

- `POST /api/category-master` Body: `{ nameCanonical, aliases, active }`
- `PUT /api/category-master/{id}` Body: `{ nameCanonical, aliases, active }`
- `DELETE /api/category-master/{id}`（软删除，active=false）

**用途场景**：
- 新增第 7 类（如"数字资产"）
- 添加新别名（如"海外权益类"→"QDII"）
- 停用旧类别（迁移用户已有数据后）

---

### 4.11 AI 顾问（多轮对话）

**用途**：基于快照数据的对话式问答（"我的海外权益类比例是否合理？"）。

#### 4.11.1 发起对话

```
POST /api/chat/send
Body: {
  "userId": 1,
  "message": "我的海外权益类比例偏高吗？",
  "intent": "auto"  // 或 "analysis" / "suggestion" / "qa"
}
```

**响应**：
```json
{
  "code": 0,
  "data": {
    "conversationId": "chat-uuid",
    "intent": "analysis",
    "reply": "根据您 2026-07-15 的快照，海外权益类占比 20.5%，目标 20%，偏差 +0.5%，属于合理范围。",
    "usedPrompt": "advisor_analysis_v1",
    "tokensUsed": 234
  }
}
```

#### 4.11.2 继续对话

```
POST /api/chat/{conversationId}/send
Body: { "userId": 1, "message": "那商品类呢？" }
```

**意图分类**（`IntentClassifier`）：
- `analysis`：分析现状
- `suggestion`：建议操作
- `qa`：答疑
- `chitchat`：闲聊（fallback 礼貌回答）

**system prompt 切换**：
- `analysis` → 加载 `advisor_analysis_v1` prompt（重点在数据展示）
- `suggestion` → 加载 `advisor_suggestion_v1` prompt（重点在操作建议）
- `qa` → 加载 `advisor_qa_v1` prompt（重点在概念解释）

---

## 5. dataTime 字段（决策 13）

### 5.1 背景

AI 视觉模型提取的 `snapshot_date` 经常出错（如把"2026-07-15"读成"2025-07-21"或"2026-01-24"）。决策 13 决定：

- **前端**：从 EXIF DateTimeOriginal 提取真实数据日期，或让用户手动选择
- **后端**：将前端传入的 `dataTime` 覆盖 AI 提取值

### 5.2 涉及字段

| DTO | 字段 | 必填 | 说明 |
|---|---|---|---|
| `ScreenshotParseRequest` | `dataTime: LocalDate` | ❌ | 单图解析时传入 |
| `ScreenshotBatchParseRequest` | `dataTime: LocalDate` | ❌ | 多图 batch 时传入 |
| `ScreenshotReparseRequest` | `dataTime: LocalDate` | ❌ | 重新解析时传入 |

### 5.3 优先级

```
req.dataTime (前端 EXIF / 用户选择)
    ↓ 最高权威
mapToParsedAsset AI 解析值 (fallback)
    ↓
LocalDate.now() (最差兜底)
```

### 5.4 E2E 验证（2026-07-20 02:00 fresh JVM）

| 请求 | req.dataTime | AI 提取 | 实际 snapshotDate | 结论 |
|---|---|---|---|---|
| dataTime=2026-07-15 + 灰测图 a808 | 2026-07-15 | 2026-01-24 | **2026-07-15** | ✅ override 生效 |
| dataTime=2026-07-16 + 灰测图 7ac58b | 2026-07-16 | 2026-01-26 | **2026-07-16** | ✅ override 生效 |
| cache HIT + dataTime override (Replay 3) | 2026-07-15 | 2026-01-24 | **2026-07-15** | ✅ cache HIT 0.117s + 正确 override |

### 5.5 实现位置

`ScreenshotService.applyDataTimeOverride(asset, dataTime, ctx)`：

```java
static void applyDataTimeOverride(ParsedAsset asset, LocalDate dataTime, String ctx) {
    if (asset == null) return;
    if (dataTime == null) {
        log.debug("决策13: dataTime=null，ctx={} 保持 AI 解析 snapshot_date={}", ctx, asset.getSnapshotDate());
        return;
    }
    String old = asset.getSnapshotDate();
    String neu = dataTime.toString();
    asset.setSnapshotDate(neu);
    log.info("决策13: dataTime override ctx={} AI={} → 实际={}", ctx, old, neu);
}
```

**调用点**：`parse()` / `parseBatch()` / `reparse()` 三处。

### 5.6 与 cache 关系

| 缓存键 | 包含 dataTime？ |
|---|---|
| (fileId, promptVersion, imageCount) | **否** |
| 缓存内容 | AI 推理结果（含 AI 提取的 snapshotDate）|
| dataTime override 时机 | 缓存返回后执行 |

**合理性**：cache 复用 AI 推理结果（expensive），dataTime 是请求级业务字段（可任意指定）。

---

## 6. AI 路由与缓存机制

### 6.1 AiRouter 路由逻辑（决策 11 + 12）

```
imageCount = 1（单图 parse）
    ↓
imageCount ≤ 2 → minimax primary + 豆包 fallback
imageCount > 2 → 豆包 primary + minimax fallback
```

**2026-07-19 决策 12 修正**：豆包 vision 4 个 model（1-5-pro / 1-8 / 2-0-pro / 2-1-turbo）实测全部失败（404/429/timeout），**Phase 1a 按 minimax-only 验收通过**。AiRouter 代码保留豆包路径，1a.11+ 重新评估其他视觉供应商。

### 6.2 韧性（resilience4j）

```yaml
fincontrol.ai.vision:
  minimax:
    retry: 2
  cb:
    sliding-window: 10
    failure-rate: 50%
```

- 失败重试 2 次（指数退避）
- 10 个请求窗口，失败率 > 50% 触发熔断
- 熔断后调用 fallback provider

### 6.3 缓存机制（Caffeine）

| 配置 | 值 |
|---|---|
| 缓存键 | (fileId, promptVersion, imageCount) |
| 容量上限 | 1024 entries |
| TTL | 24h |
| 实现 | `AiRouter.visionCache` |

**实测加速**（2026-07-20 02:00）：
- fresh JVM 4 cache MISS（latency 32-62s，真实模型调用）
- 同 JVM 3 cache HIT（latency 0.077-0.117s） = **460x 加速**

**数据时间 override 不影响 cache**：dataTime 在 cache 返回后 override，cache HIT 时仍正确。

---

## 7. 数据模型（主要表）

### 7.1 asset_raw（原始资产）

每只基金一行（一笔持仓）。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 |
| snapshot_date | DATE | 决策 13 字段（实际数据日期，可与 upload 日期不同）|
| fund_name | VARCHAR(128) | 基金名（来自 AI 解析）|
| amount | DECIMAL(18,2) | 持仓金额 |
| holding_profit | DECIMAL(18,2) | 持有收益（决策 7 双字段）|
| cumulative_profit | DECIMAL(18,2) | 累计收益（决策 7 双字段）|
| source | VARCHAR(32) | user_confirm / ai_guess / user_manual |
| created_at | DATETIME | 创建时间 |

### 7.2 asset_snapshot（按大类快照）

每个大类一行（一天最多 7 行：6 大类 + 余额类）。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 |
| snapshot_date | DATE | 快照日期 |
| category_name | VARCHAR(64) | 大类名（货币类/固收类/...）|
| category_total | DECIMAL(18,2) | 该大类总额 |
| fund_count | INT | 包含基金数 |
| actual_ratio | DECIMAL(5,2) | 实际占比 (%) |
| target_ratio | DECIMAL(5,2) | 目标占比（从 user_config 同步）|
| deviation | DECIMAL(5,2) | 偏差 (actual - target) |
| is_latest | BOOLEAN | 是否当前最新（保证唯一）|
| is_balance | BOOLEAN | 是否余额类 |
| created_at | DATETIME | 创建时间 |

### 7.3 fund_category_map（基金分类映射）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户（多用户隔离）|
| fund_name | VARCHAR(128) | 基金名 |
| category_name | VARCHAR(64) | 归类（货币类 / 固收类 / ...）|
| source | VARCHAR(32) | user_correct / ai_guess / user_manual / any-source |
| is_user_confirmed | BOOLEAN | 是否用户手动确认 |
| confirmed_at | DATETIME | 用户确认时间 |
| last_seen_at | DATETIME | 最近在快照中出现时间 |

**优先级**（决策 8）：`user_correct` > `any-source` > `fromAlias` > `raw`

### 7.4 user_config（用户配置）

单行（key-value）。

| key | value |
|---|---|
| target_ratios | JSON {货币类: 10, 固收类: 15, ...} |
| budget_limit | 1000 |
| purchase_threshold | 100 |
| high_vol_dca_budget | 560 |

### 7.5 operation_log（月度操作日志）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 |
| operation_date | DATE | 操作当天（≠ snapshotDate）|
| v_curr, v_monetary, v_bond, u_high | DECIMAL | 月度校正输入 |
| delta_m_theory, delta_b_theory | DECIMAL | 理论解 |
| delta_m_actual, delta_b_actual | DECIMAL | 实际补仓 |
| rounding_strategy | VARCHAR(32) | round_up_10 / round_down_10 / none |

### 7.6 chat_history（AI 顾问对话）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 |
| conversation_id | VARCHAR(64) | 对话 ID（多轮共享）|
| role | VARCHAR(16) | user / assistant |
| content | TEXT | 消息内容 |
| conversation_type | VARCHAR(32) | chat / screenshot_parse |
| used_provider | VARCHAR(32) | minimax / doubao / null |
| fallback_triggered | BOOLEAN | 决策 11 监控字段 |

### 7.7 category_master（6 大类主数据）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| name_canonical | VARCHAR(64) | 标准名（货币类 / 固收类 / ...）|
| aliases | VARCHAR(512) | 别名（JSON 数组，如 ["货币", "货基"]）|
| active | BOOLEAN | 是否激活 |
| created_at, updated_at | DATETIME | 时间戳 |

---

## 8. 错误码参考

| 区间 | 类型 | 示例 |
|---|---|---|
| 0 | 成功 | — |
| 1001-1099 | 参数错误 | 1001 参数缺失 / 1002 格式错误 / 1003 越界 |
| 2001-2099 | 业务错误 | 2001 快照不存在 / 2002 同日已存在 / 2003 超过撤销时限 |
| 3001-3099 | 外部依赖错误 | 3001 VISION_INVALID_JSON / 3002 VISION_TIMEOUT / 3003 VISION_ZERO_FUNDS |
| 5001-5099 | 服务器错误 | 5001 INTERNAL_ERROR（启动时缺 Key 也走 5001）|

完整错误码定义见 `ErrorCode.java`。

---

## 9. 测试与验证

### 9.1 单元 + 集成测试

```cmd
cd fincontrol-backend
mvn -B test -DfailIfNoTests=false
```

**当前状态**：**241/241 PASS**（无回归）

测试结构：
- `src/test/java/.../service/` - 业务单元测试（Mockito strict）
- `src/test/java/.../controller/` - 控制器测试（MockMvc）
- `src/test/java/.../ai/` - AI 客户端测试
- `src/test/java/.../it/` - 集成测试（H2 in-memory DB）

### 9.2 真实 E2E 灰测

20260716 灰测脚本（路径 A + 路径 B）：

```cmd
powershell -File .tmp\gray-test-20260716.ps1
```

**结果**：19/19 fund 100% 匹配 DeepSeek。

### 9.3 Test 1B 同 JVM 重跑

```cmd
:: 需要 backend 已启动且 4 张 20260716 灰测图已上传
powershell -File .tmp\test-1b-and-datetime.ps1
```

**结果**：4 cache MISS (32-62s) + 3 cache HIT (0.077-0.117s) = **460x 加速**

### 9.4 决策 13 E2E

```bash
# dataTime=2026-07-15
curl -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d '{"fileId":"a808aad6-...","userId":1,"dataTime":"2026-07-15"}' \
  http://localhost:8080/api/screenshot/parse
# 响应：snapshotDate=2026-07-15（被覆盖）
```

---

## 10. 故障排查

### 10.1 启动失败：5001 INTERNAL_ERROR "AI provider not configured"

**原因**：`application-local.yml` 缺 minimax api-key。

**解决**：参考 §2.2 配置 api-key。

### 10.2 上传图片后 parse 返回 3002 VISION_TIMEOUT

**原因**：minimax M3 推理超时（> 300s）。可能原因：
1. 图片过大（> 10MB）
2. 网络问题
3. minimax 服务端繁忙

**解决**：
- 压缩图片后重试
- 多次重试（resilience4j 自动重试 2 次）
- 1a.11+ 优化：异步任务 + 进度查询

### 10.3 解析返回 3003 VISION_ZERO_FUNDS

**原因**：AI 提取 0 只完整基金。可能原因：
1. 截图不清晰（4K 屏截图过大被压缩）
2. 截图非支付宝资产页
3. 余额类 holding=null 边界（1a.10 路径 A 修复后不会触发）

**解决**：
- 重新截图
- 用工具截图（不压缩）
- 4 张连续截图（路径 A）通常能覆盖

### 10.4 cache HIT 但结果与首次不同

**原因**：cache 键含 promptVersion，prompt 升级会失效。**这是预期行为**。

**强制 MISS**：重启后端（清空 JVM Caffeine）。

### 10.5 confirm 返回 2002 同日已存在

**原因**：同日已 confirm 过快照（可能其他用户/前端 tab）。

**解决**：
- 撤销已有（`DELETE /api/snapshot/confirm/{snapshotId}`，10 秒内）
- 或 `confirmedOverwrite: true` 强制覆盖

### 10.6 撤销返回 410 / 2003

**原因**：超过 10 秒撤销时限。

**解决**：手动修复（直接 UPDATE SQL 或重新 confirm）。

---

## 11. 已知限制 + 后续工作

### 11.1 Phase 1a 已知限制

1. **路径 B 间歇性失败**：minimax 4 图 batch 偶发 timeout（PRODUCTION_BLOCKED）→ 推荐生产用路径 A
2. **豆包 vision 暂废**：决策 12，1a.11+ 重新评估
3. **AI 提取 snapshotDate 不准**：决策 13 已用 dataTime 解决，**前端 EXIF + UI 选择待 1b+ 实施**
4. **fund 分类混合资产边界**：决策 14，**前端 user override UI 待 1b+ 实施**

### 11.2 Phase 1b+ follow-up

| 阶段 | 任务 | 来源 |
|---|---|---|
| 1b.1 | 首页展示（余额类 + 六大类总值 + 六大类明细含 profit）| 决策 1 |
| 1b.2 | 数据管理页面（上传截图 → AI 解析 → 大类确认面板）| 决策 1 |
| 1b.3 | 大类确认面板（余额类下拉 + 10s 撤销 + 忽略按钮）| 决策 1 |
| 1b.4 | AI 顾问页面（多轮对话 + 意图分类 + system prompt 切换）| 决策 1 |
| 1b.5 | 全局状态管理（Zustand）| 决策 1 |
| 1b.6 | 前端防抖（确认入库按钮 disabled 状态机）| 决策 1 |
| 1b.7 | 累计收益率卡片隐藏 | 决策 1 |
| 1b.8 | 截图日期默认值（前端 EXIF + UI 选择）| **决策 13** 关键 |
| 1b+ | fund 分类 user override UI（mixed-asset 边界）| **决策 14** 关键 |
| 1a.11+ | 路径 B 4 图 batch 稳定性优化 | 决策 11 |
| 1a.11+ | 豆包 vision 重新评估 | 决策 12 |
| 1a.11+ | 异步任务 + 进度查询（避免 HTTP 长连接）| 决策 1 |
| 1a.11+ | 多用户支持（当前 X-User-Id 写死为 1）| 决策 1 |

---

## 12. 附录：关键决策与变更日志

### 12.1 关键决策（14 项）

| 编号 | 决策 | 状态 |
|---|---|---|
| 1 | Phase 1 后端范围（Spring Boot + MySQL + AI 集成）| ✅ |
| 2 | 持仓 / 累计收益双字段设计 | ✅ |
| 3 | 六大类固定：货币/固收/商品/A股/海外/港股 | ✅ |
| 4 | 目标比例 / 月度校正算法（控制论反馈环）| ✅ |
| 5 | asset_raw / asset_snapshot / fund_category_map 三表分离 | ✅ |
| 6 | 10 秒撤销窗口（防误操作）| ✅ |
| 7 | holding_profit vs cumulative_profit 双字段（防误读）| ✅ |
| 8 | 基金分类优先级 user_correct > any-source > fromAlias > raw | ✅ |
| 9 | 总资产 top/sum 双轨 + 1% DISCREPANCY 报警 | ✅ |
| 10 | 路径 A（4×单图）+ 路径 B（1×parse-batch）并存 | ✅ |
| 11 | AiRouter imageCount-threshold 路由（minimax primary）| ✅ |
| 12 | 豆包 vision 暂废（4 model 实测失败）| ✅ |
| 13 | dataTime 字段覆盖 AI 解析值 | ✅ |
| 14 | fund 分类 AI 辅助 + 用户自定义（1b+ 实施）| 🟡 1b+ |

完整内容见 [docs/phase-0/decisions.md](docs/phase-0/decisions.md)。

### 12.2 Phase 1a 变更日志

| 日期 | 阶段 | 主要变更 |
|---|---|---|
| 2026-07-09 | 1a.1 | Spring Boot + HikariCP 基础设施 |
| 2026-07-10 | 1a.2 | 截图解析 API 链（4 端点 + 5 类错误路径）|
| 2026-07-12 | 1a.2 修复 | @Transactional 回滚 bug |
| 2026-07-15 | 1a.3 | 快照入库业务闭环（19/7/19 跑通）|
| 2026-07-16 | 1a.4 | 19 API 全部完成（查询 / 首页 / 映射 / 顾问 / 日志）|
| 2026-07-17 | 1a.5 | 视觉模型切到 minimax 多模态 |
| 2026-07-17 | 1a.6 | 文本模型切到 minimax |
| 2026-07-18 | 1a.7 | 真实 MySQL 持久化 + dialect 修复 |
| 2026-07-18 | 1a.8 | AI 服务韧性增强（双 provider + Caffeine + resilience4j）|
| 2026-07-19 | 1a.9 | 总资产双轨 + DISCREPANCY 1% 报警 |
| 2026-07-19 | 1a.10 | 双路径并存（4×单图 + 1×parse-batch）|
| 2026-07-19 | 1a.10 缓存验证 | Test 1A fresh JVM + Test 1B 同 JVM 460x 加速 |
| 2026-07-19 | 决策 13 实施 | dataTime 字段（DTO + Service override + 3 单测）|
| 2026-07-20 | 1a.10 决策 13 E2E | Test 1B + E2E 验证完成 |

### 12.3 已知 commit 里程碑

| commit | 说明 |
|---|---|
| `df6a8ff` | 1a.1 基础设施 |
| `94fed26` | 1a.2 API 链 |
| `db8728e` | 1a.2 @Transactional 修复 |
| `831d14c` | 1a.10 决策 13 后端小改（DTO 字段）|
| `e396750` | 1a.10 缓存 + AI vs DeepSeek 灰测验收 |
| `5ac9574` | 1a.10 决策 13 实施补全 + Test 1B + E2E（**最新**）|

---

## 附录 A：完整 API 速查

| 方法 | 路径 | 用途 |
|---|---|---|
| **截图** | | |
| POST | /api/screenshot/upload | 上传图片到临时存储 |
| POST | /api/screenshot/parse | AI 解析单图（路径 A 第 1 步）|
| POST | /api/screenshot/parse-batch | AI 解析多图 batch（路径 B）|
| POST | /api/screenshot/reparse | 重新解析（基于 conversationId）|
| **快照** | | |
| GET | /api/snapshot/latest | 最新快照 |
| GET | /api/snapshot/latest/detail | 最新快照详情（含每只基金）|
| GET | /api/snapshot/{date} | 指定日期快照 |
| GET | /api/snapshot/history | 历史快照列表（分页）|
| POST | /api/snapshot/confirm | 确认大类归属并入库 |
| DELETE | /api/snapshot/confirm/{snapshotId} | 10 秒内撤销入库 |
| **月度校正** | | |
| GET | /api/correction/defaults | 获取月度操作台默认值 |
| POST | /api/correction/monthly/calculate | 计算（不写入）|
| POST | /api/correction/monthly/recalculate | 实时重算（取整弹窗内）|
| POST | /api/correction/monthly/confirm | 确认并写入 operation_log |
| **配置** | | |
| GET | /api/config | 获取当前配置 |
| POST | /api/config | 保存配置 |
| **基金大类映射** | | |
| GET | /api/category-map/match | 批量查询映射 |
| POST | /api/category-map/update | 更新单条映射（user_correct）|
| DELETE | /api/category-map/{userId}/{fundName} | 删除映射 |
| POST | /api/category-map/reset | 重置为 ai_guess |
| GET | /api/category-map/stale | 列 stale user_correct |
| **大类主数据** | | |
| GET | /api/category-master | 列出大类 |
| POST | /api/category-master | 新增大类 |
| PUT | /api/category-master/{id} | 更新大类 |
| DELETE | /api/category-master/{id} | 删除大类（软删）|
| **AI 顾问** | | |
| POST | /api/chat/send | 发起对话 |
| POST | /api/chat/{conversationId}/send | 继续对话 |
| GET | /api/chat/{conversationId}/history | 获取对话历史 |
| **健康检查** | | |
| GET | /actuator/health | 后端健康检查 |
| GET | /swagger-ui.html | Swagger UI（自动生成）|

---

## 附录 B：常用 curl 命令

### 上传 + 解析 + 确认（一气呵成）

```bash
# 1. 上传
FILE_ID=$(curl -s -X POST -H "X-User-Id: 1" \
  -F "file=@/path/to/screenshot.jpg" \
  http://localhost:8080/api/screenshot/upload | \
  python -c "import sys,json; print(json.load(sys.stdin)['data']['fileId'])")

# 2. 解析
PARSED=$(curl -s -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d "{\"fileId\":\"$FILE_ID\",\"userId\":1,\"dataTime\":\"2026-07-15\"}" \
  http://localhost:8080/api/screenshot/parse)

# 3. 确认（从 PARSED 中提取 parsedAsset 字段，组装 confirm 请求）
# 详见 §4.6
```

### 4 图 batch 解析

```bash
# 假设已上传 4 张图获得 4 个 fileId
curl -s -X POST -H "X-User-Id: 1" -H "Content-Type: application/json" \
  -d "{\"userId\":1,\"fileIds\":[\"$FID1\",\"$FID2\",\"$FID3\",\"$FID4\"]}" \
  http://localhost:8080/api/screenshot/parse-batch
```

### 数据清理

```sql
-- 清空所有快照
DELETE FROM asset_raw;
DELETE FROM asset_snapshot;
DELETE FROM fund_category_map;
DELETE FROM chat_history;
DELETE FROM operation_log;

-- 重置 fund 分类为 ai_guess
UPDATE fund_category_map SET source='ai_guess', is_user_confirmed=false, confirmed_at=NULL;
```

---

*本文档随 Phase 1b 推进持续更新。最后更新：2026-07-20 02:30*
