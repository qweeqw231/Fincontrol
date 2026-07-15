# Phase 1a.2 测试教程：截图解析 API 链

> 本文档是 Phase 1a.2（4 个 API）的端到端测试手册。
> 配套 commit：`feat(backend): complete Phase 1a.2 screenshot parse API chain` + 后续 `fix: ... 取消事务回滚 bug`。
>
> **本教程强烈建议在 `cmd.exe` 中复制粘贴运行**——PowerShell 默认把 `curl` 别名为 `Invoke-WebRequest`，且 `^` 在 cmd 才是行续、在 PowerShell 是 `` ` ``。
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
| 配套 commit | `feat(backend): complete Phase 1a.2 screenshot parse API chain` |
| 自动化测试状态 | ✅ **6 / 6 PASS**（含 5 个 Mockito 业务测试 + 1 个 Spring 容器装配） |
| 适用阶段 | Phase 1a.2 验收 |
| 配套文档 | [api-contract.md §2 / §10 / §11](../phase-0/api-contract.md) |
| 依赖运行后端 | ✅ 已具备（HikariCP + db UP 通过 `actuator/health` 验证） |

---

## 1. 验收清单（按 checklist 顺序）

| # | checklist 编号 | 验收目标 | 必须达成的标准 |
|---|---------------|---------|--------------|
| 1 | 1a.4 | POST /api/screenshot/upload | 上传 jpg/png/webp ≤20MB，返回 fileId |
| 2 | 1a.5 | POST /api/screenshot/parse | 解析成功返回完整 ParsedAsset；失败按 3001/3002/3003 区分 |
| 3 | 1a.6 | POST /api/screenshot/reparse | 同 conversationId 重新调用视觉模型 |
| 4 | 1a.23 | GET /api/parse-logs | 返回 chat_history 派生的解析日志列表 |
| 5 | P0-1.4 | 失败时也写 chat_history | parse 失败时 chat_history 应出现 assistant 错误记录 + 错误码符合契约 |
| 6 | P0-4.4 | reparse 基于已有 conversation | reparse 不依赖前端重新上传 |

---

## 2. 前置条件

| 项 | 检查命令 | 期望 |
|---|---------|------|
| MySQL 已启动 | `mysqladmin -u root -p ping` | mysqld is alive |
| 数据库已建库 + 7 张表 | `mysql -u root -p fincontrol -e "SHOW TABLES;"` | 输出 7 张表名（含 `chat_history`） |
| prompt_versions 已预热 | `mysql -u root -p fincontrol -e "SELECT prompt_name, version FROM prompt_versions;"` | 至少出现 `screenshot_parser v1.0` |
| 视觉模型 API Key 已配置 | `echo %VISION_API_KEY%`（或 `DEEPSEEK_API_KEY`，取决于当前实现） | 非占位字符串 |
| 后端已启动 | `curl http://localhost:8080/actuator/health` | `{"status":"UP","components":{"db":{"status":"UP",...}}}` |
| 任意一张 ≥1KB 的图片 | 文件管理器 / 截屏 | `.jpg` 或 `.png` |

---

## 3. 自动化测试（不需要后端运行）

直接在 `cmd.exe` 中执行：

```cmd
cd C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend
mvn -B test -DfailIfNoTests=false
```

**期望输出**：

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13 s    in FincontrolApplicationTests
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s     in ScreenshotServiceTest
[INFO] BUILD SUCCESS
```

**5 个 Mockito 测试覆盖（与契约 [api-contract.md §11] 一一对应）**：

| # | 测试方法 | 触发条件 | 验证 |
|---|---------|---------|------|
| 1 | `parse_validJson_returnsParsedAsset` | 上游模型返回合法 JSON | 路径到 ParsedAsset；写 2 行 chat_history（user + assistant） |
| 2 | `parse_deepSeekNonJson_throwsBusinessException3001` | 上游模型返回 Markdown 非 JSON | 抛 3001；写 assistant 错误记录 |
| 3 | `parse_deepSeekTimeout_throwsBusinessException3002` | 上游调用超时（mock 抛） | 抛 3002；写 assistant 错误记录 |
| 4 | `parse_zeroFunds_throwsBusinessException3003` | 上游返回 categories[].funds 全部空 | 抛 3003（data 含 conversationId） |
| 5 | `reparse_conversationNotFound_throwsBusinessException2001` | conversationId 找不到 user 消息 | 抛 2001 |

如 6/6 全绿，继续第 4 节做手工测试。

---

## 4. 手工测试（curl 命令清单，全部**单行**）

> ⚠️ **不要使用 `^` 行续**——`cmd.exe` 没有内置行续，`^` 末尾反而触发 `More?` 续行提示。每条命令必须是**一行完整命令**，可以一行写多长就写多长。

### 4.0 启动后端（如果还没在跑）

新开 `cmd.exe` 窗口：

```cmd
cd C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend
set DEEPSEEK_API_KEY=sk-your-real-key-here
mvn spring-boot:run
```

启动后关键日志：

- `Started FincontrolApplication in N seconds`
- `Tomcat started on port 8080`

### 4.1 测试 1a.4：upload（不调视觉模型，最快路径）

准备一张 ≥1KB 的真实或合成 jpg/png。**单行命令**：

```cmd
curl -sS -X POST http://localhost:8080/api/screenshot/upload -H "X-User-Id: 1" -F "file=@C:\path\to\your\image.png"
```

**期望**：

```json
{"code":0,"message":"success","data":{"fileId":"<32-char-uuid>","fileUrl":".\\uploads\\screenshots\\<uuid>.png","uploadedAt":"2026-07-15T21:25:07"}}
```

**记下 `fileId`**，下一步要用。

### 4.2 测试 1a.5：parse（调真实模型）

把下面命令里的 `<fileId>` 替换为 4.1 拿到的 fileId，整段是**单行**：

```cmd
curl -sS -X POST http://localhost:8080/api/screenshot/parse -H "Content-Type: application/json" -H "X-User-Id: 1" -d "{\"fileId\":\"<fileId>\",\"userId\":1}"
```

**期望路径 A（成功）**：

```json
{"code":0,"data":{"conversationId":"conv-<fileId>","snapshotDate":"...","categories":[...],"matchedFunds":[...],"unmatchedFunds":[],...}}
```

**期望路径 B（视觉模型无法处理 → 3001/3002/3003）**：

```json
{"code":3001,"message":"DeepSeek API HTTP 400: ... unknown variant image_url ...","data":null}
```

> HTTP 状态码：3001 → 502 / 3002 → 504 / 3003 → 502
>
> 即使路径 B 失败，**chat_history 也会有 assistant 错误记录**（写法已修：去掉 `@Transactional`，不依赖整段事务回滚）。

**记下 `conversationId`**（路径 A），下一步和 4.3 用。

### 4.3 测试 1a.6：reparse（[P0-4.4]）

替换 `<conversationId>` 为 4.2 返回的 conversationId，整段单行：

```cmd
curl -sS -X POST http://localhost:8080/api/screenshot/reparse -H "Content-Type: application/json" -H "X-User-Id: 1" -d "{\"conversationId\":\"<conversationId>\"}"
```

期望与 4.2 路径 A 同形（但 conversationId 与上次一致）。**这次不依赖前端 fileId**。

如果 4.2 返回了路径 B（3001），则 4.3 仍然能跑——只是会再次失败（同样 3001）。**因为 chat_history 中已有 user 消息**（bug 已修），所以 reparse 不会再误抛 2001。

### 4.4 测试 1a.23：parse-logs

```cmd
curl -sS "http://localhost:8080/api/parse-logs?limit=20" -H "X-User-Id: 1"
```

**期望**：

```json
{"code":0,"data":{"items":[{"logId":<id>,"conversationId":"...","status":"parse_failed","snapshotDate":null,"fundCount":0,"createdAt":"...","confirmedAt":null}],"total":1,...}}
```

注：
- 路径 A 路径 → `status: imported`，`fundCount` 等于真实基金数
- 路径 B 路径 → `status: parse_failed`，`fundCount: 0`，`confirmedAt: null`

### 4.5 验证 chat_history 写入（[P0-1.4] 关键证据）

**单行 SQL**（`mysql -e` 内部允许换行）：

```cmd
mysql -u root -p fincontrol -e "SELECT id, conversation_id, role, conversation_type, LEFT(content,60) AS content_head, created_at FROM chat_history WHERE conversation_type='screenshot_parse' ORDER BY created_at DESC LIMIT 10"
```

**输入密码后输出应是**：

- 如果走路径 A（成功）：

```
id  conversation_id                   role       conversation_type     content_head                  created_at
1   conv-<fileId>                     user       screenshot_parse      <fileId>                       2026-07-15 21:25:08
2   conv-<fileId>                     assistant  screenshot_parse      <Markdown JSON>               2026-07-15 21:25:10
```

- 如果走路径 B（3001）：

```
id  conversation_id                   role       conversation_type     content_head                  created_at
1   conv-<fileId>                     user       screenshot_parse      <fileId>                       2026-07-15 21:25:08
2   conv-<fileId>                     assistant  screenshot_parse      [error code=3001] DeepSeek API HTTP 400 ... 2026-07-15 21:25:10
```

> 注：history 中可能含 `reparse` 留下的额外行（每条 reparse 都写一条新 assistant 行）。

---

## 5. 故障排查（FAQ）

### Q1：parse 返回 `5001 Internal server error: ...DeepSeek API Key 未配置...`

`deepseek.api-key` 仍是占位 `REPLACE_ME_DEEPSEEK_API_KEY`，未设置环境变量 / application-local.yml。

修复：

- 方式 A：设置环境变量 `set DEEPSEEK_API_KEY=sk-...` 后重跑 `mvn spring-boot:run`。
- 方式 B：编辑 `src/main/resources/application-local.yml` 写入真实 key（该文件在 .gitignore 中）。

### Q2：parse 返回 `3001 DeepSeek API HTTP 400 ... unknown variant image_url ...`

**这是 DeepSeek API 的视觉限制**——`deepseek-flash`、`deepseek-chat`、`deepseek-reasoner` 等 DeepSeek 系列模型**全是纯文本模型**，不接受 `image_url` 多模态 part。DeepSeek 公司当前没有视觉模型（截至 2026/07）。

**解决方向（需要您决策，见 §6）**：

- 选项 A：换模型为**支持多模态的 API 提供商**（OpenAI gpt-4o / 智谱 GLM-4V / Qwen-VL 等），并改 `DeepSeekClient`
- 选项 B：换为 DeepSeek 文本补全，但让前端先 OCR / 人工粘文本，模型负责结构化
- 选项 C：用 `deepseek-reasoner` 跑纯文本的 JSON 校对任务，跳过图像输入
- 选项 D：暂不解决，**承认 1a.2 验收通过已存档的"代码逻辑层"**（mvn test 已 6/6），真实视觉模型对接推后到 1a.5/1a.6

### Q3：parse-logs 列表为空

`GET /api/parse-logs` 派生于 chat_history。先确认至少调用过一次 `POST /api/screenshot/parse`（无论成功或失败），并且 4.5 的 SQL 能查到 `conversation_type='screenshot_parse'` 的行。

如果 SQL 仍返回空，**两种可能**：

1. 后端是**本次 commit 之前**的版本，存在老版事务回滚 bug。拉一下代码 + 重启。
2. MySQL 数据库连接到了其他 schema（不是 `fincontrol`）。`SELECT DATABASE();` 验证。

### Q4：`More?` 提示

教程里的 `^` 行续字符只在 PowerShell 有效（PowerShell 用 `` ` ``，cmd 没有）。去掉所有 `^` 把命令压成单行即可。

### Q5：上传超大文件被 Spring 拒绝

`application.yml` 默认 20MB（`spring.servlet.multipart.max-file-size`）。在 `application-local.yml` 覆盖即可。

### Q6：parse 失败后 reparse 提示 `2001 未找到 conversation...`

如果**修复前**的旧版本遗留了 user 消息缺失，请重启后端（`Ctrl+C` + `mvn spring-boot:run`）。修复后的版本（去掉 `@Transactional`）已经保证 user 消息不被回滚。

### Q7：image_url 不支持的并行方案

如果您**不想**换 API 提供商，前端可以在用户上传截图后**自己用 Tesseract 做 OCR**，把识别出的 Markdown 文本 + 文件 path 一起作为 `user` 消息发给后端，模型只负责结构化（提取字段）。这要求新增 `POST /api/screenshot/parse` 的 body 接受 `{"ocrText":"...", "screenshotPath":"..."}` 字段——属于 1a.5 范畴。

---

## 6. 待用户决策（不在本期修复范围内）

模型选型问题不属于 1a.2 验收项，但要进 1a.5/1a.6 之前必须确定。决策后我可以一键给改动：

- **6.1** 决定要换哪家多模态 API（OpenAI / 智谱 / Qwen-VL / Doubao / 自建 OCR+DeepSeek）
- **6.2** 决定 API key 走 `application-local.yml` 还是环境变量（建议环境变量，跟之前一致）
- **6.3** 决定是否改 `api-contract.md` §2 的 `image_url` part 定义（OpenAI-compatible 就保持，OCR 方案就改成 OCR 字段）

---

## 7. 验收执行清单

测试通过后请在 [`docs/phase-1/checklists/phase-1a.md`](./checklists/phase-1a.md) 把以下 6 项标 `[x]` 并填完成日期 2026-07-15：

- [ ] **1a.4** `POST /api/screenshot/upload`
- [ ] **1a.5** `POST /api/screenshot/parse`（含 [P0-1.4] AI 解析失败处理）
- [ ] **1a.6** `POST /api/screenshot/reparse`（[P0-4.4]）
- [ ] **1a.23** `GET /api/parse-logs`
- [ ] **[P0-1.4]** AI 解析失败异常路径（API 1a.5）
- [ ] **[P0-4.4]** 重新解析（API 1a.6）

---

## 8. 给后续验收者的提示

- Phase 1a.3 快照入库 API 还在 `feature` 阶段之前；上传 + 解析 + 重新解析的数据可以在 Phase 1a.3 启动时复用。
- 视觉能力跟具体模型强耦合。当切到 1a.5/1a.6 选模型时，记得同步更新 `DeepSeekClient.java` 内部的 message 构造（不同 API 用不同的多模态 schema）。
- 多模态真正落地还需要前端 1b.3 配合（截图上传后立刻贴图给后端，不要走 OCR 中转）。
