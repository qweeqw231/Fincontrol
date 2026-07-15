# Phase 1a.2 测试教程：截图解析 API 链

> 本文档是 Phase 1a.2（4 个 API）的端到端测试手册。
> 在提交 `94fed26` 后请按本文档验收；通过后即可在 `docs/phase-1/checklists/phase-1a.md` 勾选 4 项 + P0-1.4 + P0-4.4。
>
> **范围**：
> - `1a.4` POST `/api/screenshot/upload`（仅存盘）
> - `1a.5` POST `/api/screenshot/parse`（含 P0-1.4 三类失败码 3001/3002/3003）
> - `1a.6` POST `/api/screenshot/reparse`（P0-4.4）
> - `1a.23` GET `/api/parse-logs`

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 配套 commit | `94fed26 feat(backend): complete Phase 1a.2 screenshot parse API chain` |
| 自动化测试状态 | ✅ 6/6 PASS（含 5 个新 Mockito 测试） |
| 适用阶段 | Phase 1a.2 验收 |
| 配套文档 | [api-contract.md §2 / §10 / §11](../phase-0/api-contract.md) |

---

## 1. 验收清单（按 checklist 顺序）

| # | checklist 编号 | 验收目标 | 必须达成的标准 |
|---|---------------|---------|--------------|
| 1 | 1a.4 | POST /api/screenshot/upload | 上传 jpg/png/webp ≤20MB，返回 fileId |
| 2 | 1a.5 | POST /api/screenshot/parse | 解析成功返回完整 ParsedAsset；失败按 3001/3002/3003 区分 |
| 3 | 1a.6 | POST /api/screenshot/reparse | 同 conversationId 重新调用 DeepSeek |
| 4 | 1a.23 | GET /api/parse-logs | 返回 chat_history 派生的解析日志列表 |
| 5 | P0-1.4 | 失败时也写 chat_history | parse 失败时 chat_history 应出现 assistant 错误记录 + 3001/3002/3003 错误码 |
| 6 | P0-4.4 | reparse 基于已有 conversation | reparse 不依赖前端重新上传 |

---

## 2. 前置条件

| 项 | 检查命令 | 期望 |
|---|---------|------|
| MySQL 已启动 | `mysqladmin -u root -p ping` | mysqld is alive |
| 数据库已建库 + 7 张表 | `mysql -u root -p fincontrol -e "SHOW TABLES;"` | 输出 7 张表名（含 `chat_history`） |
| prompt_versions 已预热 | `mysql -u root -p fincontrol -e "SELECT prompt_name, version FROM prompt_versions;"` | 至少出现 `screenshot_parser v1.0` |
| DeepSeek API Key 已配置 | `echo %DEEPSEEK_API_KEY%` 或检查 `application-local.yml` | 非 `REPLACE_ME_DEEPSEEK_API_KEY` |
| 后端已启动 | `curl http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| 任意一张 ≥1KB 的支付宝资产截图 | 文件管理器 / 截屏 | `.jpg` 或 `.png` |

---

## 3. 自动化测试（无需启动后端）

直接在 `fincontrol-backend/` 执行：

```bash
cd fincontrol-backend
mvn -B test -DfailIfNoTests=false
```

**期望输出**：

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.16 s -- in com.fincontrol.FincontrolApplicationTests
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.545 s -- in com.fincontrol.service.ScreenshotServiceTest
[INFO] BUILD SUCCESS
```

**5 个 Mockito 测试覆盖**：

| # | 测试方法 | 触发条件 | 验证 |
|---|---------|---------|------|
| 1 | `parse_validJson_returnsParsedAsset` | DeepSeek 返回合法 JSON | 路径到 ParsedAsset；写 2 行 chat_history（user + assistant） |
| 2 | `parse_deepSeekNonJson_throwsBusinessException3001` | DeepSeek 返回 Markdown 非 JSON | 抛 3001；写 assistant 错误记录 |
| 3 | `parse_deepSeekTimeout_throwsBusinessException3002` | DeepSeek 调用超时（mock 抛） | 抛 3002；写 assistant 错误记录 |
| 4 | `parse_zeroFunds_throwsBusinessException3003` | DeepSeek 返回 categories[].funds 全部空 | 抛 3003（data 含 conversationId） |
| 5 | `reparse_conversationNotFound_throwsBusinessException2001` | conversationId 找不到 user 消息 | 抛 2001 |

如 6/6 全绿，继续到第 4 节做手工测试。

---

## 4. 手工测试（curl 命令清单）

> 所有命令在 `cmd.exe` 里可直接复制执行；PowerShell 把 `\` 改为 `` ` `` 转义。

### 4.0 启动后端（如未启动）

```cmd
cd fincontrol-backend
set DEEPSEEK_API_KEY=sk-your-real-key
mvn spring-boot:run
```

> 第二次启动后的关键日志：
> - `Started FincontrolApplication in N seconds`
> - `Tomcat started on port 8080`
> - `Swagger UI: http://localhost:8080/swagger-ui.html`

### 4.1 测试 1a.4：upload（无 DeepSeek 调用，最快路径）

准备一张 ≥1KB 的真实或合成 jpg/png：

```cmd
curl -sS -X POST http://localhost:8080/api/screenshot/upload ^
  -H "X-User-Id: 1" ^
  -F "file=@C:\path\to\your\alipay-screenshot.png" ^
  | python -m json.tool
```

**期望**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "<32-char-uuid-no-dash>",
    "fileUrl": "C:\\Users\\...\\fincontrol-backend\\uploads\\screenshots\\<uuid>.png",
    "uploadedAt": "2026-07-15T14:00:00"
  }
}
```

**记录返回的 `fileId`**（下一步要用）。

### 4.2 测试 1a.5：parse（调真实 DeepSeek）

```cmd
curl -sS -X POST http://localhost:8080/api/screenshot/parse ^
  -H "Content-Type: application/json" ^
  -H "X-User-Id: 1" ^
  -d "{\"fileId\":\"<上一步的fileId>\",\"userId\":1}" ^
  | python -m json.tool
```

**期望路径 A（成功）**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "conversationId": "conv-<fileId>",
    "snapshotDate": "2026-07-15",
    "totalAsset": 6899.37,
    "sixCategoriesTotal": 6480.91,
    "balanceFund": 418.46,
    "categories": [
      {
        "categoryName": "货币类",
        "funds": [{"fundName": "...", "amount": 641.49, "profit": 1.49}],
        "categoryTotal": 641.49,
        "categoryPercentage": 9.90,
        "targetRatio": 10,
        "deviation": -0.10
      }
    ],
    "matchedFunds": ["..."],
    "unmatchedFunds": [],
    "aiMarkdownReport": "### 支付宝资产明细..."
  }
}
```

**记录返回的 `conversationId`**（下一步 + 1a.6 用）。

**期望路径 B（DeepSeek 返回非 JSON / 超时 / 0 基金）**：

```json
{
  "code": 3001,
  "message": "DeepSeek API 返回非 JSON：...",
  "data": {
    "conversationId": "conv-<fileId>",
    "errorType": "parse_failed",
    "aiRawResponse": "..."
  }
}
```

> HTTP 状态：3001 → 502 / 3002 → 504 / 3003 → 502
> 三类失败都会写 assistant 错误记录到 chat_history（验证：第 4.5 节 SQL 查询）

### 4.3 测试 1a.6：reparse（[P0-4.4]）

```cmd
curl -sS -X POST http://localhost:8080/api/screenshot/reparse ^
  -H "Content-Type: application/json" ^
  -H "X-User-Id: 1" ^
  -d "{\"conversationId\":\"<上一步的conversationId>\"}" ^
  | python -m json.tool
```

**期望**：与 4.2 路径 A 同形（但 conversationId 与上次一致）。**这次不依赖前端 fileId**。

### 4.4 测试 1a.23：parse-logs

```cmd
curl -sS -X GET "http://localhost:8080/api/parse-logs?limit=20" ^
  -H "X-User-Id: 1" ^
  | python -m json.tool
```

**期望**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "logId": <id>,
        "conversationId": "conv-<fileId>",
        "snapshotDate": "2026-07-15",
        "fundCount": <count>,
        "status": "imported",
        "source": "screenshot_manual",
        "createdAt": "2026-07-15T14:00:00",
        "confirmedAt": null
      }
    ],
    "total": <items.size>,
    "page": 1,
    "pageSize": 20
  }
}
```

注：
- `status` 是 `imported` 还是 `parse_failed`，取决于 4.2 跑的是哪条路径
- `confirmedAt` 在 Phase 1 简版为 `null`（详见 ScreenshotService.parse 注释）

### 4.5 验证 chat_history 写入（[P0-1.4] 关键证据）

```cmd
mysql -u root -p fincontrol -e "
  SELECT id, conversation_id, role, conversation_type, LEFT(content, 60) AS content_head, created_at
  FROM chat_history
  WHERE conversation_type='screenshot_parse'
  ORDER BY created_at DESC
  LIMIT 10;"
```

**期望**：

- 成功路径：每个 conversation 对应 2 行（`user` 含 fileId、`assistant` 含 Markdown 表格 + JSON）
- 失败路径：每个 conversation 对应 2 行（`user` + `assistant` 含 `[error code=...]` 前缀）
- 不同次 upload / reparse 会有多条 assistant 行，**logId 反映的是 assistant 行 id**

---

## 5. 故障排查（FAQ）

### Q1：1a.5 返回 `5001 Internal server error: ...DeepSeek API Key 未配置...`

`deepseek.api-key` 仍是 `REPLACE_ME_DEEPSEEK_API_KEY` 占位。

修复：

- 方式 A：设置环境变量 `set DEEPSEEK_API_KEY=sk-...` 然后重启 mvn spring-boot:run。
- 方式 B：编辑 `src/main/resources/application-local.yml` 写入真实 key（该文件在 .gitignore 中，不会污染仓库）。

### Q2：1a.4 返回 `Bad credentials` 或 DeepSeek API 直接报错

DeepSeek 平台侧未生效：检查 `https://platform.deepseek.com/` 的余额是否 ≥ ¥1。

### Q3：parse 频繁报 `3001` 而 assistant 写入了长 Markdown

模型合规问题。检查 `prompt_versions.screenshot_parser v1.0` 内容是否仍然存在：

```cmd
mysql -u root -p fincontrol -e "SELECT prompt_name, LEFT(prompt_content, 80) FROM prompt_versions WHERE prompt_name='screenshot_parser';"
```

### Q4：本地 ParseLog 列表为空

`GET /api/parse-logs` 派生于 chat_history。先确认至少调用过一次 `POST /api/screenshot/parse`，并且 chat_history 表里有 `conversation_type='screenshot_parse'` 的 assistant 行（参见 4.5）。

### Q5：上传超大文件被 Spring 拒绝

application.yml 默认 20MB（`spring.servlet.multipart.max-file-size`）。如确实更大，在 application-local.yml 覆盖即可。

---

## 6. 验收执行清单

测试通过后请在 [`docs/phase-1/checklists/phase-1a.md`](./checklists/phase-1a.md) 把以下 6 项标 `[x]` 并填完成日期 2026-07-15：

- [ ] **1a.4** `POST /api/screenshot/upload`
- [ ] **1a.5** `POST /api/screenshot/parse`（含 [P0-1.4] AI 解析失败处理）
- [ ] **1a.6** `POST /api/screenshot/reparse`（[P0-4.4]）
- [ ] **1a.23** `GET /api/parse-logs`
- [ ] **[P0-1.4]** AI 解析失败异常路径（API 1a.5）
- [ ] **[P0-4.4]** 重新解析（API 1a.6）

---

## 7. 给后续验收者的提示

- Phase 1a.3 快照入库 API 还在 `feature` 阶段之前；上传 + 解析 + 重新解析的数据可以在 Phase 1a.3 启动时复用。
- DeepSeek 真正的视觉解析能力在截图质量差（300x200、低对比）时可能返回 0 基金（3003 错误码），这是设计内的失败路径，不算 bug。
- 多模态的 multipart 图片由前端在 1b.3 阶段对接。
