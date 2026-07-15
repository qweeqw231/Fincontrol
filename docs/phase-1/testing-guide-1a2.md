# Phase 1a.2 测试教程：截图解析 API 链（视觉模型 = minimax M3）

> 本文档是 Phase 1a.2（4 个 API）的端到端测试手册，**已切到 minimax 多模态视觉模型**。
> 配套 commit：`feat(backend): complete Phase 1a.2 ...` + `fix(1a.2): cancel @Transactional ...`。
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
| 配套 commit | `feat(backend): complete Phase 1a.2 screenshot parse API chain` + `fix(1a.2): cancel @Transactional` |
| 视觉模型后端 | **minimax M3 系列**（OpenAI-compatible chat completions，原生多模态） |
| 自动化测试状态 | ✅ **6 / 6 PASS**（5 个 Mockito 业务测试 + 1 个 Spring 容器装配） |
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
| minimax API Key 已配置 | `echo %VISION_API_KEY%` | 非占位字符串 |
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
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11 s   in FincontrolApplicationTests
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s    in ScreenshotServiceTest
[INFO] BUILD SUCCESS
```

**5 个 Mockito 测试覆盖（与契约 [api-contract.md §11] 一一对应）**：

| # | 测试方法 | 触发条件 | 验证 |
|---|---------|---------|------|
| 1 | `parse_validJson_returnsParsedAsset` | 上游视觉模型返回合法 JSON | 路径到 ParsedAsset；写 2 行 chat_history（user + assistant） |
| 2 | `parse_visionNonJson_throwsBusinessException3001` | 上游返回 Markdown 非 JSON | 抛 3001；写 assistant 错误记录 |
| 3 | `parse_visionTimeout_throwsBusinessException3002` | 上游调用超时（mock 抛） | 抛 3002；写 assistant 错误记录 |
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
set VISION_API_KEY=eyJ-your-real-minimax-key-here
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

### 4.2 测试 1a.5：parse（调真实视觉模型）

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
{"code":3001,"message":"视觉模型 HTTP 401: ... unauthorized ...","data":null}
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
2   conv-<fileId>                     assistant  screenshot_parse      [error code=3001] 视觉模型 HTTP 401...   2026-07-15 21:25:10
```

> 注：history 中可能含 `reparse` 留下的额外行（每条 reparse 都写一条新 assistant 行）。

---

## 5. 故障排查（FAQ）

### Q1：parse 返回 `5001 Internal server error: ...视觉模型 API Key 未配置...`

`fincontrol.vision.api-key` 仍是占位 `REPLACE_ME_VISION_API_KEY`，未设置环境变量 / application-local.yml。

修复：

- 方式 A：设置环境变量 `set VISION_API_KEY=eyJ-...` 后重跑 `mvn spring-boot:run`。
- 方式 B：编辑 `src/main/resources/application-local.yml` 写入 `fincontrol.vision.api-key: <YOUR_KEY>`（该文件在 .gitignore 中）。

### Q2：parse 返回 `3001 视觉模型 HTTP 401 / 403 / 4xx ...`

- `401 / 403`：API Key 无效 / 无权限。检查：
  1. minimax 平台订阅状态（TokenPlanPlus 应已激活）
  2. Key 是否过期（重新生成 + 复制完整 token）
  3. `application.yml` 中 `fincontrol.vision.base-url = https://api.minimaxi.chat/v1` 是否正确
- `404`：路径错误，检查 minimax 平台最新文档的 base URL
- `400`：请求体 schema 与 minimax 不匹配（理论上 OpenAI-compatible 应该 OK）。看后端 `视觉模型非 2xx: status=400 body=...` 日志

### Q3：parse 返回 `3003 0 只基金`

视觉模型看得见图片但没识别出基金行。可能原因：

- 图片分辨率太低或含表格不在模型训练分布
- prompt 没引导模型输出结构化 JSON

临时调试：换张更清晰的支付宝截图重试；或手动给 parse 重写的 prompt 加 `MUST output {"snapshot_date": "...", "categories": [...]}` 强调。

### Q4：parse-logs 列表为空

`GET /api/parse-logs` 派生于 chat_history。先确认至少调用过一次 `POST /api/screenshot/parse`（无论成功或失败），并且 4.5 的 SQL 能查到 `conversation_type='screenshot_parse'` 的行。

如果 SQL 仍返回空，**两种可能**：

1. 后端是**本次 commit 之前**的版本，存在老版事务回滚 bug。拉一下代码 + 重启。
2. MySQL 数据库连接到了其他 schema（不是 `fincontrol`）。`SELECT DATABASE();` 验证。

### Q5：`More?` 提示

教程里的 `^` 行续字符只在 PowerShell 有效（PowerShell 用 `` ` ``，cmd 没有）。去掉所有 `^` 把命令压成单行即可。

### Q6：上传超大文件被 Spring 拒绝

`application.yml` 默认 20MB（`spring.servlet.multipart.max-file-size`）。在 `application-local.yml` 覆盖即可。

### Q7：parse 失败后 reparse 提示 `2001 未找到 conversation...`

如果**修复前**的旧版本遗留了 user 消息缺失，请重启后端（`Ctrl+C` + `mvn spring-boot:run`）。修复后的版本（去掉 `@Transactional`）已经保证 user 消息不被回滚。

### Q8：如何停 minimax 套娃式 minmax 错误

如果 model 名 `MiniMax-Text-01` 不被接受，看 minimax 平台的「模型广场 / API 文档」最新可用 ID 列表，常见可能包括 `abab-7-chat`、`MiniMax-Text-01`、`MiniMax-VL`、`MiniMax-Text-02` 等。把 `application.yml` 中 `fincontrol.vision.model` 改成对应 ID 即可。

### Q9：每次启动都报 `视觉模型 API Key 未配置`

可能是 `application-local.yml` 没被加载。Spring Boot 只有 `application-local.yml` 跟 `spring.profiles.active=local` 同时才会被读。检查 `application.yml` 第 9 行 `profiles.active: ${SPRING_PROFILES_ACTIVE:local}` 是不是被覆盖成了 `prod`。

---

## 6. 1a.5+ 视觉模型选型记录（已闭环）

| 时间 | 决策 |
|------|------|
| Phase 0 | 锁定 DeepSeek（**错误假设**——DeepSeek 全部纯文本模型，无多模态） |
| Phase 1a.2 | 写入 DeepSeek 文本 API 占位实现 → Phase 1a.5 真实调通时撞墙 HTTP 400 unknown variant |
| Phase 1a.5 | **改用 minimax M3（TokenPlanPlus）**，原生多模态图像 + 视频。`VisionModelClient.java` 重构，按 OpenAI-compatible schema 实现 |

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
- 视觉能力跟具体模型强耦合。如果 minimax 接口 schema 与 OpenAI-compatible 不一致（HTTP 4xx），仅需修改 `VisionModelClient.java` 内部 message 构造；其它 12 个文件（Controller / DTO / Service / ErrorCode / 等）不受影响。
- 多模态真正落地还需要前端 1b.3 配合（截图上传后立刻贴图给后端，不要走 OCR 中转）。
