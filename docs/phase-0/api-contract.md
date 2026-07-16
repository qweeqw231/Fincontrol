# FinControl REST API 契约文档

> Phase 0 产出。本文档定义 FinControl 所有 REST API 的端点、请求/响应结构、状态码。**前端开发与后端开发以此文档为准进行并行开发**。
>
> 与四轮评审的衔接：本文档补齐了第四轮评审（跨模块一致性）中识别的 API 契约缺失问题（问题 3.3.5）。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.0 |
| 编写日期 | 2026-07-09 |
| 编写者 | 架构审查助手 |
| 配套文档 | FinControl 技术设计文档 v2.0 + 四轮评审 |
| 后续修订 | Phase 1 编码过程中可调整，需同步更新本文档 |

---

## 1. 全局约定

### 1.1 基础 URL

```
http://localhost:8080/api
```

（生产环境待 Phase 5 部署时确定）

### 1.2 通用请求头

| Header | 必填 | 说明 |
|--------|------|------|
| Content-Type | ✅ | `application/json; charset=UTF-8` |
| X-User-Id | ❌ | 用户 ID（默认 1，MVP 单用户场景） |

### 1.3 通用响应结构

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

错误响应：

```json
{
  "code": 1001,
  "message": "快照日期无效",
  "data": null
}
```

### 1.4 错误码规范

| 错误码区间 | 类型 | 说明 |
|----------|------|------|
| 0 | 成功 | |
| 1001-1099 | 参数错误 | 请求参数缺失、格式错误、值越界 |
| 2001-2099 | 业务错误 | 数据不存在、状态冲突 |
| 3001-3099 | 外部依赖错误 | DeepSeek API 调用失败、超时 |
| 5001-5099 | 服务器错误 | 未捕获异常、数据库错误 |

### 1.5 分页规范

列表接口统一使用：

```
?page=1&pageSize=20&sort=created_at:desc
```

响应中：

```json
{
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 2. 截图解析 API

### 2.1 上传截图（不入库）

```
POST /api/screenshot/upload
```

**Request**：

- Content-Type: `multipart/form-data`
- Body: `file`（图片文件，jpg/png/webp，≤ 10MB）

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "fileId": "uuid-xxxx",
    "fileUrl": "/tmp/uploads/uuid-xxxx.png",
    "uploadedAt": "2026-07-09T20:30:45"
  }
}
```

**说明**：仅上传到临时存储，不调用 DeepSeek API。前端拿到 fileId 后调用 `/parse`。

### 2.2 调用 DeepSeek 解析

```
POST /api/screenshot/parse
```

**Request**：

```json
{
  "fileId": "uuid-xxxx",
  "userId": 1
}
```

**Response 200（成功）**：

```json
{
  "code": 0,
  "data": {
    "conversationId": "uuid-yyyy",
    "snapshotDate": "2026-06-09",
    "totalAsset": 6899.37,
    "sixCategoriesTotal": 6480.91,
    "balanceFund": 418.46,
    "categories": [
      {
        "categoryName": "货币类",
        "funds": [
          { "fundName": "中加货币E", "amount": 641.49, "profit": 1.49 }
        ],
        "categoryTotal": 641.49,
        "categoryPercentage": 9.90,
        "targetRatio": 10,
        "deviation": -0.10
      }
    ],
    "matchedFunds": ["中加货币E", "长城短债A"],
    "unmatchedFunds": [],
    "aiMarkdownReport": "### 支付宝资产明细...\n..."
  }
}
```

**Response 4xx（解析失败）**：

```json
{
  "code": 3001,
  "message": "DeepSeek API 返回非 JSON：超时/识别失败/0 只基金",
  "data": {
    "conversationId": "uuid-yyyy",
    "errorType": "parse_failed",
    "aiRawResponse": "..."
  }
}
```

**状态码**：

- 200：解析成功
- 400：fileId 无效或缺失
- 500：服务器内部错误
- 502：DeepSeek API 调用失败（code 3001）

### 2.3 重新解析

```
POST /api/screenshot/reparse
```

**Request**：

```json
{
  "conversationId": "uuid-yyyy"
}
```

**Response**：同 2.2

**说明**：基于已存在的 conversationId，重新调用 DeepSeek API 解析原始截图。用于"重新解析"按钮。

---

## 3. 快照查询 API

### 3.1 获取最新快照

```
GET /api/snapshot/latest
```

**Query 参数**：

| 参数 | 必填 | 说明 |
|------|------|------|
| includeBalance | ❌ | 是否包含余额类（默认 true）|
| includeDetail | ❌ | 是否包含每只基金明细（默认 false）|

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "snapshotDate": "2026-06-09",
    "snapshotConfirmedAt": "2026-06-09T20:30:45",
    "sixCategoriesTotal": 6480.91,
    "balanceFund": 418.46,
    "totalAssetWithBalance": 6899.37,
    "categories": [
      {
        "categoryName": "货币类",
        "categoryTotal": 641.49,
        "actualRatio": 9.90,
        "targetRatio": 10,
        "deviation": -0.10,
        "fundCount": 1
      },
      {
        "categoryName": "固收类",
        "categoryTotal": 954.24,
        "actualRatio": 14.72,
        "targetRatio": 15,
        "deviation": -0.28,
        "fundCount": 3
      }
    ]
  }
}
```

**说明**：`includeDetail=true` 时，categories[].funds 会包含每只基金的明细（含 profit）。

### 3.2 获取最新快照详情（含每只基金 profit）

```
GET /api/snapshot/latest/detail
```

**Response**：同 3.1 + categories[].funds 数组

```json
{
  "data": {
    "categories": [
      {
        "categoryName": "货币类",
        "categoryTotal": 641.49,
        "funds": [
          {
            "fundName": "中加货币E",
            "amount": 641.49,
            "profit": 1.49,
            "category": "货币类"
          }
        ]
      }
    ]
  }
}
```

**说明**：用于首页六大类明细表格（折叠）。**解决第四轮 P0 2.1.1 profit 孤儿字段问题。**

### 3.3 获取指定日期快照

```
GET /api/snapshot/{date}
```

**Path 参数**：`date` 格式 `YYYY-MM-DD`

**Response 200**：同 3.2

**Response 404**：

```json
{
  "code": 2001,
  "message": "该日期无快照数据"
}
```

### 3.4 获取历史快照列表（分页）

```
GET /api/snapshot/history
```

**Query 参数**：

| 参数 | 必填 | 说明 |
|------|------|------|
| from | ❌ | 起始日期 |
| to | ❌ | 结束日期 |
| page | ❌ | 页码（默认 1）|
| pageSize | ❌ | 每页大小（默认 20）|

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "snapshotDate": "2026-07-08",
        "sixCategoriesTotal": 6450.00,
        "balanceFund": 380.00,
        "categoryCount": 6,
        "confirmedAt": "2026-07-08T20:30:00"
      }
    ],
    "total": 30,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 4. 快照入库 API

### 4.1 确认大类归属并入库

```
POST /api/snapshot/confirm
```

**Request**：

```json
{
  "conversationId": "uuid-yyyy",
  "snapshotDate": "2026-06-09",
  "fundMappings": [
    { "fundName": "中加货币E", "category": "货币类", "amount": 641.49, "profit": 1.49, "isIgnored": false },
    { "fundName": "某未知基金", "category": "商品类", "amount": 100.00, "profit": 0, "isIgnored": false },
    { "fundName": "噪音基金", "category": null, "amount": 0, "profit": 0, "isIgnored": true }
  ],
  "userId": 1
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "snapshotId": 123,
    "snapshotDate": "2026-06-09",
    "isLatest": true,
    "overwrittenExistingData": true,
    "newMappingCount": 1,
    "ignoredFundCount": 1
  }
}
```

**说明**：

- `isLatest=true`：本次入库已设为该日期最新
- `overwrittenExistingData=true`：覆盖了同日已有数据
- `newMappingCount`：新增的 fund_category_map 条目数
- `ignoredFundCount`：标记为忽略的基金数（**第三轮 P0 2.2.4.5 解决**）

**状态码**：

- 200：成功
- 400：fundMappings 为空或格式错误
- 404：conversationId 不存在
- 409：快照日期与其他对话冲突（code 2002）

### 4.2 撤销入库（10 秒撤销）

```
DELETE /api/snapshot/confirm/{snapshotId}
```

**Path 参数**：`snapshotId` 来自 4.1 的响应

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "deletedAssetRawCount": 18,
    "deletedFundMappingCount": 0,
    "restoredSnapshotId": 122
  }
}
```

**说明**：

- 删除本次确认写入的 asset_raw
- 如果是新增的 fund_category_map，删除
- 恢复 asset_snapshot 中被覆盖的旧 is_latest=true 记录
- **仅在确认后 10 秒内允许**（前端 toast UI 时长），超时报 410

**状态码**：

- 200：撤销成功
- 404：snapshotId 不存在
- 410：超过撤销时限（code 2003）

---

## 5. 月度校正 API

### 5.1 获取月度操作台默认值

```
GET /api/correction/defaults
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "vTotalSixCategories": 6480.91,
    "vMonetary": 641.49,
    "vBond": 954.24,
    "vHighVol": 4885.38,
    "uHigh": 560.00,
    "targetRatios": {
      "货币类": 10,
      "固收类": 15,
      "商品类": 25,
      "A股权益类": 25,
      "海外权益类": 20,
      "港股/大中华类": 5
    },
    "budgetLimit": 1000,
    "purchaseThreshold": 100,
    "snapshotDate": "2026-06-09",
    "snapshotNote": "六大类合计，不含余额类"
  }
}
```

**说明**：

- `vTotalSixCategories` = V_curr（六大类合计，**不含余额类**，第二轮已澄清）
- `targetRatios` 从 `user_config` 读取（**第四轮 P0 2.2.1 解锁决策**）
- `snapshotNote` 用于前端在输入框 placeholder 中展示

### 5.2 计算月度校正（不写入）

```
POST /api/correction/monthly/calculate
```

**Request**：

```json
{
  "vCurr": 6480.91,
  "vMonetary": 641.49,
  "vBond": 954.24,
  "uHigh": 560.00,
  "pMonetary": 10,
  "pBond": 15,
  "pHigh": 75,
  "budgetLimit": 1000,
  "purchaseThreshold": 100
}
```

**Response 200**：

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
      {
        "type": "exceed_budget_limit",
        "message": "理论总投入 1146.45 超出 budgetLimit 1000，建议削减高波校正预算"
      }
    ]
  }
}
```

**字段说明**：

- `deltaMTheory / deltaBTheory`：方程组精确解
- `uMonetaryDca / uBondDca`：低波定投份额反推值
- `roundingSuggestion`：取整建议（默认向上取整到 10 的倍数）
- `warnings`：约束警告列表（结构化）

### 5.3 实时重算偏差（取整弹窗内调用）

```
POST /api/correction/monthly/recalculate
```

**Request**：

```json
{
  "vCurr": 6480.91,
  "vMonetary": 641.49,
  "vBond": 954.24,
  "uHigh": 560.00,
  "deltaMActual": 170,
  "deltaBActual": 220,
  "uMonetaryDcaActual": 89.21,
  "uBondDcaActual": 113.79
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "newRatios": {
      "货币类": 10.12,
      "固收类": 15.07
    },
    "deviations": {
      "货币类": 0.12,
      "固收类": 0.07
    },
    "totalInvestmentActual": 1153.00,
    "overBudgetLimit": true
  }
}
```

**说明**：

- 用户修改补仓数字后，前端调用此接口实时计算偏差
- 偏差超过阈值时返回 `overBudgetLimit=true`

### 5.4 确认月度校正并写入

```
POST /api/correction/monthly/confirm
```

**Request**：

```json
{
  "vCurr": 6480.91,
  "vMonetary": 641.49,
  "vBond": 954.24,
  "uHigh": 560.00,
  "deltaMTheory": 169.79,
  "deltaBTheory": 212.65,
  "deltaMActual": 170,
  "deltaBActual": 220,
  "roundingStrategy": "round_up_10",
  "snapshotDate": "2026-06-09",
  "userId": 1
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "operationLogId": 456,
    "operationDate": "2026-07-09",
    "writtenToOperationLog": true
  }
}
```

**说明**：

- 写入 `operation_log` 表（**第二轮 P0 2.1.10 新增表**）
- 不修改 asset_raw 或 asset_snapshot
- `operationDate` 是用户操作当天

---

## 6. 资产配置 API

### 6.1 获取当前配置

```
GET /api/config
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "targetRatios": {
      "货币类": 10,
      "固收类": 15,
      "商品类": 25,
      "A股权益类": 25,
      "海外权益类": 20,
      "港股/大中华类": 5
    },
    "budgetLimit": 1000,
    "purchaseThreshold": 100,
    "highVolDcaBudget": 560
  }
}
```

### 6.2 保存配置

```
POST /api/config
```

**Request**：

```json
{
  "targetRatios": {
    "货币类": 10,
    "固收类": 15,
    "商品类": 30,
    "A股权益类": 25,
    "海外权益类": 15,
    "港股/大中华类": 5
  },
  "budgetLimit": 1000,
  "purchaseThreshold": 100,
  "highVolDcaBudget": 560,
  "userId": 1
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "savedAt": "2026-07-09T20:30:00",
    "triggeredSnapshotUpdate": false,
    "note": "target_ratio 写入策略：仅写入 user_config，asset_snapshot.target_ratio 通过月度校正时同步"
  }
}
```

**说明**：

- 仅写入 `user_config` 表（**第四轮 P0 2.2.1 解锁决策 = 解读 4**）
- 不触发 `asset_snapshot` 的级联更新
- `triggeredSnapshotUpdate=false` 是预期的（与原文档 7.4.4 的"同步更新 asset_snapshot 表的 target_ratio 字段"不同，此处明确选择解读 4）

---

## 7. 基金大类映射 API

### 7.1 批量查询映射

```
GET /api/category-map/match
```

**Query 参数**：

| 参数 | 必填 | 说明 |
|------|------|------|
| funds | ✅ | 基金名称列表，逗号分隔（最多 50 个）|

**示例**：`?funds=中加货币E,长城短债债券A,未知基金`

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "matchedFunds": [
      { "fundName": "中加货币E", "category": "货币类", "source": "ai_guess", "confirmedAt": "2026-06-09T20:30:00" }
    ],
    "unmatchedFunds": ["未知基金"]
  }
}
```

### 7.2 更新单条映射

```
POST /api/category-map/update
```

**Request**：

```json
{
  "fundName": "某基金",
  "category": "商品类",
  "source": "user_correct",
  "userId": 1
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "mappingId": 78,
    "fundName": "某基金",
    "category": "商品类",
    "source": "user_correct",
    "confirmedAt": "2026-07-09T20:30:00",
    "updated": true
  }
}
```

**说明**：

- 已存在的映射：`UPDATE fund_category_map SET category=?, source='user_correct', confirmed_at=NOW()`
- 不存在的映射：`INSERT new row with source='user_manual'`
- **第一轮 P0 2.2 解锁决策**

---

## 8. 对话与 AI 顾问 API

### 8.1 发送消息

```
POST /api/chat/send
```

**Request**：

```json
{
  "conversationId": "uuid-zzzz",
  "message": "本月应该补仓多少？",
  "userId": 1
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "conversationId": "uuid-zzzz",
    "userMessage": {
      "role": "user",
      "content": "本月应该补仓多少？",
      "createdAt": "2026-07-09T20:30:00"
    },
    "assistantMessage": {
      "role": "assistant",
      "content": "基于您当前持仓...",
      "createdAt": "2026-07-09T20:30:05",
      "routedTo": "main_loop",
      "promptVersion": "ai_assistant v1.0"
    },
    "intentClassification": {
      "result": true,
      "latencyMs": 230
    }
  }
}
```

**说明**：

- 后端先调用 `intent_classifier` 判定（第三轮 P0 3.1.1 few-shot 补齐）
- 根据结果加载 `ai_assistant` 或垃圾回路 prompt
- 每次 API 调用都重新组装 messages（第三轮 P0 3.2.1 解锁决策）
- `routedTo` 标识走的是主回路（main_loop）还是垃圾回路（garbage_loop）

### 8.2 获取对话列表

```
GET /api/conversations
```

**Query 参数**：

| 参数 | 必填 | 说明 |
|------|------|------|
| type | ❌ | `screenshot_parse` 或 `ai_assistant` |
| page | ❌ | 页码 |
| pageSize | ❌ | 每页大小 |

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "conversationId": "uuid-yyyy",
        "type": "screenshot_parse",
        "snapshotDate": "2026-06-09",
        "fundCount": 18,
        "status": "imported",
        "createdAt": "2026-06-09T20:30:00",
        "lastMessageAt": "2026-06-09T20:30:05"
      }
    ],
    "total": 30
  }
}
```

**说明**：

- `fundCount`、`status` 通过派生查询得到（第四轮 2.3.1 推荐方案 A）
- 不需要单独的 conversation_meta 表

### 8.3 获取单个对话详情

```
GET /api/conversations/{conversationId}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "conversationId": "uuid-yyyy",
    "type": "screenshot_parse",
    "messages": [
      {
        "role": "user",
        "content": "请解析以下支付宝资产截图",
        "createdAt": "2026-06-09T20:30:00"
      },
      {
        "role": "assistant",
        "content": "### 支付宝资产明细...",
        "createdAt": "2026-06-09T20:30:05",
        "parsedSnapshot": { ... }
      }
    ]
  }
}
```

### 8.4 新建对话

```
POST /api/conversations
```

**Request**：

```json
{
  "type": "ai_assistant",
  "userId": 1
}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "conversationId": "uuid-aaaa",
    "type": "ai_assistant",
    "createdAt": "2026-07-09T20:30:00"
  }
}
```

### 8.5 删除对话

```
DELETE /api/conversations/{conversationId}
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "deletedMessageCount": 8
  }
}
```

---

## 9. 首页辅助 API

### 9.1 获取余额类卡片数据

```
GET /api/asset/balance
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "balanceFundTotal": 418.46,
    "items": [
      { "fundName": "余额宝", "amount": 418.46, "profit": 1.56, "category": "余额类" },
      { "fundName": "余额", "amount": 0, "profit": 0, "category": "余额类" }
    ],
    "snapshotDate": "2026-06-09"
  }
}
```

**说明**：第一轮 P0 1.1 解决方案的读取路径。

### 9.2 获取最近操作时间线

```
GET /api/asset/operations/recent
```

**Query 参数**：`limit`（默认 5）

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "operationType": "screenshot_parse",
        "operationDate": "2026-07-09T20:30:00",
        "summary": "解析 18 只基金",
        "snapshotDate": "2026-07-09"
      },
      {
        "operationType": "monthly_correction",
        "operationDate": "2026-07-01T09:15:00",
        "summary": "月度校正：货币 +170 元，固收 +220 元",
        "snapshotDate": "2026-06-30"
      }
    ]
  }
}
```

**说明**：

- Phase 1 临时方案：从当前 user 的 `chat_history` 查最近 5 条 `conversation_type=screenshot_parse` 的 assistant 消息；此处表示“解析活动”，不表示确认入库已经成功。
- Phase 1 的 `operationType` 固定为 `screenshot_parse`，`operationDate` 来自 `chat_history.created_at`，summary 由解析结果派生。
- Phase 2 `operation_log` 上线后改为跨表查询，届时才展示月度校正等真实操作记录和确认入库状态。

### 9.3 获取累计收益率（Phase 3 启用）

```
GET /api/asset/cumulative-return
```

**Phase 1 返回**：

```json
{
  "code": 0,
  "data": {
    "available": false,
    "message": "累计收益率功能将在 Phase 3 上线"
  }
}
```

**Phase 3 返回**：

```json
{
  "code": 0,
  "data": {
    "available": true,
    "cumulativeReturnRate": 8.42,
    "cumulativeReturn": 545.36,
    "investedPrincipal": 6475.32,
    "currentTotal": 7020.68
  }
}
```

**说明**：第四轮 P0 3.3.1 解锁决策——Phase 1 隐藏卡片，Phase 3 上线。

---

## 10. 解析日志与对话元数据 API

### 10.1 获取解析日志列表

```
GET /api/parse-logs
```

**Response 200**：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "logId": 1,
        "conversationId": "uuid-yyyy",
        "snapshotDate": "2026-07-09",
        "fundCount": 18,
        "status": "imported",
        "source": "screenshot_manual",
        "createdAt": "2026-07-09T20:30:00",
        "confirmedAt": "2026-07-09T20:30:05"
      },
      {
        "logId": 2,
        "conversationId": "uuid-xxxx",
        "snapshotDate": null,
        "fundCount": 0,
        "status": "parse_failed",
        "source": "screenshot_manual",
        "createdAt": "2026-07-09T19:00:00"
      }
    ]
  }
}
```

**说明**：

- `status` 取值：`imported`、`parse_failed`、`pending`
- Phase 1 解析失败也写入（第一轮 P0 1.6 解锁决策）

---

## 11. 错误码速查表

| 错误码 | 含义 | HTTP 状态 |
|-------|------|----------|
| 0 | 成功 | 200 |
| 1001 | 快照日期无效 | 400 |
| 1002 | 基金名称缺失 | 400 |
| 1003 | 金额必须 > 0 | 400 |
| 1004 | 大类名称不在枚举值内 | 400 |
| 2001 | 该日期无快照数据 | 404 |
| 2002 | 快照日期与其他对话冲突 | 409 |
| 2003 | 超过撤销时限（10 秒）| 410 |
| 3001 | DeepSeek API 返回非 JSON | 502 |
| 3002 | DeepSeek API 调用超时 | 504 |
| 3003 | DeepSeek API 返回 0 只基金 | 502 |
| 5001 | 服务器内部错误 | 500 |
| 5002 | 数据库写入失败 | 500 |

---

## 12. 待补充 API（Phase 2-3）

以下 API 在 Phase 2-3 启动前补齐：

- [ ] Phase 2：`GET /api/correction/quarterly/calculate`（LQR 季度策略）
- [ ] Phase 2：`POST /api/correction/quarterly/confirm`
- [ ] Phase 3：`GET /api/nav/history?from=&to=`（净值曲线）
- [ ] Phase 3：`POST /api/nav/manual-correction`（手动校正）
- [ ] Phase 3：`GET /api/snapshot/ratios?date=`（比例演化）
- [ ] Phase 5：`POST /api/auth/login`（多用户）

---

## 13. 与四轮评审的衔接

| 评审问题 | 本文档对应 API |
|---------|---------------|
| 第一轮 1.1 余额类落点 | 3.1 / 3.2 / 9.1 |
| 第一轮 1.3 三表事务 | 4.1（@Transactional） |
| 第一轮 1.6 AI 解析失败 | 2.2（3001/3003 错误码） |
| 第一轮 2.2 映射 UPDATE 规则 | 7.2 |
| 第二轮 1.1 接口字段对齐 | 5.1（完整字段） |
| 第二轮 2.1.10 operation_log | 5.4 |
| 第三轮 1.5.1 全局状态管理 | （前端实现，后端无额外 API） |
| 第三轮 2.2.4.3 余额类下拉选项 | 7.1（matchedFunds 返回余额类） |
| 第三轮 2.2.4.5 忽略该条 | 4.1（isIgnored 字段） |
| 第三轮 2.2.2.1 撤销机制 | 4.2 |
| 第三轮 3.2.1 system prompt 切换 | 8.1（每次重新组装 messages） |
| 第三轮 4.1.2 默认值优先级 | 5.1（snapshotNote 字段） |
| 第四轮 2.1.1 profit 字段读取 | 3.2 |
| 第四轮 2.2.1 target_ratio 解读 4 | 6.2（triggeredSnapshotUpdate=false） |
| 第四轮 3.3.1 累计收益率 | 9.3（Phase 1 available=false） |
| 第四轮 3.3.2 最近操作时间线 | 9.2 |
| 第四轮 3.3.3 重新解析 | 2.3 |

---

## 14. 文档维护

- **Phase 1 编码期间**：根据实际开发调整 API，需同步更新本文档
- **Phase 2-3**：补齐 Phase 2-3 待补充 API
- **Phase 5**：补齐多用户、SDK 阶段 API