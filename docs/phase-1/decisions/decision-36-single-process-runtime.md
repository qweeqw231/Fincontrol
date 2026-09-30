# 决策 36：单进程运行形态（后端托管前端产物）

- **日期**：2026-09-30
- **状态**：已实施并验证
- **关联**：决策 24（restart-backend SOP）、决策 35（即开即用层 / 关闭按钮）
- **前置决策记录**：`docs/phase-1/decisions/decision-33-pr6b-category-ux.md`（编号顺延至 34、35 为 1b.4 PR8/PR9 的运行层，本决策为其实质修订）

## 1. 背景

项目此前采用「即开即用、用完就关」的单机使用方式（无云服务，前后端同机），
第 35 号决策实现了启动脚本 + 关闭按钮 + 桌面快捷方式。但在设备迁移后发现该链路实际已断：

| 现象 | 原因 |
|---|---|
| 桌面无 `FinControl.lnk` | 迁移时丢失，新设备从未执行过 `create-desktop-shortcut.ps1` |
| `.tmp/` 为空 | 启动脚本在新设备从未成功跑通 |
| 关闭按钮只关后端 | 前端 Vite 与 MySQL 仍常驻，「关」不彻底 |

同时排查出三个实质缺陷：

1. `stop-fincontrol.ps1` 用 `Read-Host` 询问是否停 MySQL —— 从快捷方式运行无可交互控制台，会阻塞，与「懒得敲命令」的诉求直接冲突；
2. Vite 进程杀不干净：`vite.pid` 记录的是 **npm 的 PID**，真正监听 5173 的是 vite 子进程；且兜底条件 `MainWindowTitle -like '*Vite*'` 对 `-WindowStyle Hidden` 启动的进程**永远不匹配**（隐藏窗口无主窗口标题）；
3. `restart-backend.ps1` 每次删 `target/` 全量 `mvn package`，双击到可用需 1~2 分钟；healthcheck 仅等 30 秒，冷启动常超时误报失败。

## 2. 决策

**由 Spring Boot 直接托管前端构建产物，浏览器只访问 8080 单端口。**

- 「开」= 双击 `FinControl.lnk` → MySQL 检查 + 一个 java 进程
- 「关」= 点首页右上角关闭按钮（现有 `POST /api/system/shutdown`）→ 全关
- 前端开发模式（`npm run dev` + Vite proxy）**保留不变**，两种模式互不影响

### 2.1 子决策（本次拍板）

| 议题 | 选择 | 理由 |
|---|---|---|
| MySQL 服务处理 | **不碰**（②b） | `Start-Service`/`Stop-Service` 需管理员权限，快捷方式每次启动会弹 UAC。MySQL80 保持 Windows 开机自启，脚本只 `Get-Service` 检查状态并提示 |
| dist 构建时机 | **不存在时才 build**（②a） | 日常启动保持快速；改完前端用 `-ForceRebuild` 或手动 `npm run build`（实测仅约 4.5 秒） |
| 是否记录决策 | **是**（③） | 运行形态变更影响后续所有启动/关闭操作 |

## 3. 实施

### 3.1 后端（核心）

新增 `fincontrol-backend/src/main/java/com/fincontrol/config/WebMvcConfig.java`：

- `/**` 资源处理器 + 自定义 `PathResourceResolver`，未命中真实文件时回退 `index.html`（SPA 兜底）
- **`api/`、`actuator/`、`swagger-ui`、`v3/api-docs` 明确不参与兜底** —— 避免把「接口路径写错」伪装成「返回了一个页面」

`application.yml` 新增 `fincontrol.frontend.static-locations`，
并被 `spring.web.resources.static-locations` **引用**（单一来源）。

> 关键点：**必须让 Spring 自身的静态位置包含 dist**。根路径 `/` 由 Spring Boot 的
> welcome-page 机制处理；`PathResourceResolver` 收到空 `resourcePath` 时直接返回 null，
> 不会回调自定义 resolver。实测仅注册自定义 resolver 时 `/` 会 404。
> 启动日志可确认：`Adding welcome page: URL [file:.../fincontrol-frontend/dist/index.html]`

`GlobalExceptionHandler` 新增 `NoResourceFoundException → 404`（errorCode 2001）。
这是本决策**暴露出的既有缺陷**：原 `@ExceptionHandler(Exception.class)` 把资源未找到
一并吞成 500，使「路径写错」表现为「服务故障」。此前无人从 8080 访问静态路径所以未暴露。

### 3.2 脚本

| 文件 | 变更 |
|---|---|
| `scripts/1b/restart-backend.ps1` | 新增 `-SkipRebuild`（jar 存在则跳过全量重建）；启动时传 dist 绝对路径；healthcheck 30s → **90s**；构建失败时回显日志尾部（原先只报 "jar not found"，无从定位） |
| `scripts/desktop/launch-fincontrol.ps1` | **移除 Vite 启动**；MySQL 仅检查；8080 已监听则直接开浏览器（幂等）；dist 缺失才 build；打开 `http://localhost:8080/` |
| `scripts/desktop/stop-fincontrol.ps1` | **删除 `Read-Host`**；后端走 HTTP 优雅关闭 + 超时兜底强杀；Vite 清理改为**按 5173 端口反查 PID**（不再依赖失效的窗口标题） |
| `scripts/desktop/create-desktop-shortcut.ps1` | 创建**两个**快捷方式（启动 / 关闭）；改为幂等覆盖而非跳过 |

**工具路径显式解析**：快捷方式启动的是干净的 `powershell.exe`，其 PATH 来自注册表
（机器级 PATH 中 `%MAVEN_HOME%\bin` 为字面量条目），`mvn`/`npm` 未必可解析。
故脚本按「`MAVEN_HOME` → 已知安装路径 → PATH」顺序解析 `mvn.cmd` / `npm.cmd`，
与既有对 `java.exe` 用绝对路径的做法一致。

> 脚本约定：`scripts/**/*.ps1` **必须为纯 ASCII**（PowerShell 5.1 UTF-8 兼容），
> 本次改动已校验（非 ASCII 字节数 = 0）。

## 4. 验证结果（2026-09-30）

**单端口 8080 路径行为（8/8）**

| 请求 | 结果 |
|---|---|
| `/` | 200 text/html（index.html） |
| `/nav`、`/ratio`、`/ai`、`/data` 直刷 | 200 text/html（SPA 兜底生效） |
| `/brand/logo.png`、`/assets/index-*.js` | 200（正确 Content-Type） |
| `/api/not-exist` | **404**（未被兜底为页面、未吞成 500） |
| `/api/nav/history` 等真实接口 | code=0 |

**开关流程**

- 关闭：7.7 秒完成，无交互阻塞；成功按端口反查并杀掉隐藏运行的 Vite（PID 27928），5173 释放
- 启动（复用 jar 快速路径）：java 进程 8.4 秒启动完成，healthcheck 通过，浏览器自动打开 8080
- 启动（幂等路径）：检测到 8080 已监听 → 1.3 秒直接开浏览器

## 5. 影响与后续

- **日常操作**：双击 `FinControl.lnk` 开启；点页面右上角按钮或双击 `FinControl Stop.lnk` 关闭
- **MySQL80**：始终保持运行（开机自启）。如需彻底关闭，可在管理员终端执行 `net stop MySQL80`
- **前端改动的发布**：`cd fincontrol-frontend; npm run build`（约 4.5 秒），或 `launch-fincontrol.ps1 -ForceRebuild`
- **前端开发**：`cd fincontrol-frontend; npm run dev` → 5173（Vite proxy 转发 `/api` 到 8080），与生产模式并存
- **首次构建依赖网络**：首次（或 `target/` 被清后）需 `mvn package`，需可达 Maven Central；
  本次实施中即遇到一次拉取失败，故脚本改为失败时回显构建日志
- **jar 位置**：`fincontrol-backend/target/fincontrol-backend.jar`（`target/` 不入仓）