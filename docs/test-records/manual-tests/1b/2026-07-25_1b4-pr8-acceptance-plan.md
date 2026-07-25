# 1b.4-pr8 验收计划（日常使用层 + 品牌资源 + 关闭服务入口）

> **日期**：2026-07-25
> **PR 名**：1b.4-pr8（Phase 1 收官后扩展环节）
> **范围**：品牌资源（logo + gif）+ 启动页（白底）+ 关闭服务按钮 + 桌面快捷方式 + 决策 35 落档
> **目标**：在不影响 Phase 1 业务闭环的前提下，交付「日常使用层」完整功能

---

## 一、验收总览

| 段 | 编号 | 用例数 | 状态 |
|---|---|---|---|
| B1 资源归档与 favicon | A1–A3 | 3 | ☐ |
| B2 启动页（Splash） | B1–B3 | 3 | ☐ |
| B3 HomePage 关闭服务按钮 | C1–C5 | 5 | ☐ |
| B4 后端 SystemController | D1–D3 | 3 | ☐ |
| B5 桌面快捷方式 | E1–E3 | 3 | ☐ |
| B6 决策 35 + README 增章节 | F1–F2 | 2 | ☐ |
| **合计** | — | **19** | — |

---

## 二、详细验收用例

### B1 资源归档与 favicon

#### A1：logo.png 落入 public/brand/
- 步骤：检查 `C:\Users\lbc19\Desktop\Fincontrol\fincontrol-frontend\public\brand\logo.png`
- 期望：
  - 文件存在
  - 大小 > 0 字节
  - 与项目根 `C:\Users\lbc19\Desktop\Fincontrol\logo.png` 内容一致（byte-equality）
- 验证：`Get-FileHash` 对比 SHA256

#### A2：logo_animation.gif 落入 public/brand/
- 步骤：检查 `C:\Users\lbc19\Desktop\Fincontrol\fincontrol-frontend\public\brand\logo_animation.gif`
- 期望：与项目根 `logo_animation_v5.gif` 一致

#### A3：favicon 替换
- 步骤：浏览器访问 `http://localhost:5173/brand/logo.png`
- 期望：HTTP 200，content-type `image/png`，返回 logo.png
- 步骤：浏览器 tab 检查 favicon
- 期望：不再是 💰 emoji，而是 logo.png（圆形 logo）

### B2 启动页（Splash）

#### B1：白底 + 居中 gif
- 步骤：清空浏览器缓存（Ctrl+Shift+R）后访问 `http://localhost:5173/`
- 期望：
  - 浏览器首屏（React 渲染前）显示白底；
  - 居中位置显示 `logo_animation.gif`（240px 宽）；
  - 下方显示蓝色 "FinControl 加载中…" 文字；
  - 副标题灰色 "个人资产配置控制系统"。

#### B2：fade-out + 卸载
- 步骤：继续观察浏览器
- 期望：
  - React 接管后（≤ 600ms），启动页开始 fade-out（200ms）；
  - 250ms 后启动页从 DOM 移除；
  - 主页内容正常显示。

#### B3：splash 不阻塞首页
- 步骤：观察浏览器 console
- 期望：
  - 无 React error；
  - 无 404（gif 路径正确）；
  - 主页 home 数据加载正常。

### B3 HomePage 关闭服务按钮

#### C1：按钮渲染 + hover tooltip
- 步骤：浏览器访问 `http://localhost:5173/`，观察 HomePage 顶部 Header 右上角
- 期望：
  - 36×36 圆形按钮，灰色边框 + 白色背景；
  - 中心是电源符号 SVG；
  - 鼠标悬停 → 浏览器原生 tooltip "点击关闭服务，可以停止本系统的运行以节约资源"；
  - 浏览器 console 无警告。

#### C2：第 1 次点击 → 二次确认状态
- 步骤：点击 ⏻ 按钮 1 次
- 期望：
  - 按钮变红色（warning 状态）；
  - 屏幕下方/按钮旁出现 toast "再点一次确认关闭（5 秒倒计时）"；
  - 5 秒内不二次点击 → 按钮恢复灰色，toast 消失。

#### C3：5 秒内第 2 次点击 → 调后端
- 步骤：在 5 秒内再次点击 ⏻
- 期望：
  - 按钮 disabled；
  - toast 变为 "服务关闭中…请重新启动快捷方式"；
  - 浏览器发出 `POST /api/system/shutdown`（DevTools Network 可见）；
  - 后端响应 200 `{shuttingDown: true, graceMillis: 800}`。

#### C4：后端 800ms 内退出
- 步骤：观察 8080 端口
- 期望：
  - 800ms 内 `curl http://localhost:8080/actuator/health` 返回 503 / connection refused；
  - Spring Boot 日志显示 "Shutting down";
  - Vite dev server 检测到 backend 断开（前端不再报错 200）;
  - 浏览器前端因 backend 不可用，subsequent 任何 API 调用会失败。

#### C5：浏览器显示关闭提示
- 步骤：观察浏览器
- 期望：浏览器显示 "服务已关闭，请重新启动快捷方式" 提示（建议用 React 的友好错误边界 + 顶部 banner）。

### B4 后端 SystemController

#### D1：POST /api/system/shutdown（enabled=true）
- 步骤：`curl -X POST http://localhost:8080/api/system/shutdown`
- 期望：返回 200，body `{"code":0,"message":"success","data":{"shuttingDown":true,"graceMillis":800}}`
- 步骤：800ms 内 `curl http://localhost:8080/actuator/health`
- 期望：connection refused 或 503

#### D2：POST /api/system/shutdown（enabled=false）
- 步骤：在 `application.yml` 设置 `fincontrol.system.shutdown.enabled=false` + 重启后端
- 步骤：`curl -X POST http://localhost:8080/api/system/shutdown`
- 期望：返回 403 `{"code":403,"message":"shutdown endpoint disabled"}`
- 步骤：1 秒后 `curl /actuator/health`
- 期望：仍返回 UP（后端未退出）

#### D3：SystemControllerTest 单元测试
- 步骤：`mvn -f fincontrol-backend -o test -Dtest=SystemControllerTest`
- 期望：5+ 测试用例全绿，包括：
  - enabled=true → 同步返回 shuttingDown=true；异步触发 exit 线程；
  - enabled=false → 返回 403；
  - 异常 context 注入 → 500 / 4xx（具体看实现）；
  - graceMillis 配置生效（可用 `@MockBean` 验证）。

### B5 桌面快捷方式

#### E1：create-desktop-shortcut.ps1 创建 .lnk
- 步骤：PowerShell 跑 `scripts/desktop/create-desktop-shortcut.ps1`
- 期望：
  - `C:\Users\lbc19\Desktop\FinControl.lnk` 存在；
  - 图标为 logo.png（不是默认 PowerShell 图标）；
  - Target 指向 `powershell.exe -File launch-fincontrol.ps1`；
  - WorkingDirectory 为项目根；
  - 脚本输出 "Desktop shortcut created"。

#### E2：脚本幂等性
- 步骤：再跑一次 `create-desktop-shortcut.ps1`
- 期望：脚本检测到 .lnk 已存在，跳过创建；输出 "Shortcut already exists, skip"。

#### E3：双击 .lnk 启动全套
- 步骤：双击 `C:\Users\lbc19\Desktop\FinControl.lnk`
- 期望：
  - MySQL 未起 → 自动 `Start-Service MySQL80`；
  - 后端 jar 自动 rebuild + start（`scripts/1b/restart-backend.ps1`）；
  - 30 秒内 `actuator/health` = UP；
  - Vite dev server 后台启动，PID 写入 `.tmp/vite.pid`；
  - Chrome 自动打开 `http://localhost:5173/`，首页资产看板正常显示。

### B6 决策 35 + README 增章节

#### F1：决策 35 落档
- 步骤：检查 `docs/phase-0/decisions.md`
- 期望：
  - 在决策 34 之后追加「决策 35：日常使用层与品牌资源（1b.4 扩展）」段；
  - 末尾「决策总结表（追加后）」新增 #35 行；
  - 文档元信息 v1.1 → v1.2（最近更新 2026-07-25）。

#### F2：README 增「日常使用」章节
- 步骤：检查 `README.md`
- 期望：在"本地开发快速启动"后新增「日常使用（dev server 之外）」章节，包含：
  - 创建桌面快捷方式 1 行命令；
  - 双击启动使用流程；
  - 通过 HomePage 关闭服务流程；
  - 资源占用预估（MySQL 常开 ~300MB；后端启动期 +10s 内 +500MB；空闲可关闭）。

---

## 三、自动化测试矩阵

| 测试类型 | 命令 | 期望 |
|---|---|---|
| 前端 Vitest | `npm --prefix fincontrol-frontend test -- --run` | 12 文件 + 新增 SplashLoader.test.jsx + HomePage.test.jsx 全绿 |
| 前端 build | `npm --prefix fincontrol-frontend run build` | 通过，gif + png 复制到 dist/ |
| 后端单元测试 | `mvn -f fincontrol-backend -o test -Dtest=SystemControllerTest` | 5+ 用例全绿 |
| 后端专项（PR7 回归） | `mvn -f fincontrol-backend -o test -Dtest=SnapShotConfirmServiceP7Test` | 9/9 PASS（无回归）|
| 全量 Maven | `mvn -f fincontrol-backend -o test` | 与 PR7 后基线一致（fixture 债务见 README 诚实口径）|

---

## 四、链接与回归保证

- **业务逻辑零回归**：1b.4-pr8 不修改任何 service / store / 业务组件；
- **API 兼容性**：仅新增 `POST /api/system/shutdown`，不影响现有 30+ 端点；
- **数据兼容**：无 schema 变更；
- **构建兼容**：gif / png 复制到 dist/，Vite build 仍通过；
- **历史验收**：1b.4-pr7 V1–V8 + PR6b 主页固收类 + 用户验收全部保留。

---

## 五、关联文档

- 工作计划：`docs/phase-1/work-plans/1b/2026-07-25_1b4-pr8-work-plan.md`
- 决策 35：`docs/phase-0/decisions.md`（追加段）
- 联调记录：`test/1b/2026-07-25-1b4-pr8-联调记录.md`
- 验收报告：`docs/test-records/manual-tests/1b/2026-07-25_1b4-pr8-acceptance-report.md`
- 1b.4-pr7 验收报告：链接同上
