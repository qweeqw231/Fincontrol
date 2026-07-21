# Phase 1b.1 验收计划（前端骨架 + 全局状态）

> **状态**：📝 待审阅（开工前）
> **编写日期**：2026-07-21
> **配套文档**：
> - [Phase 1b.1 工作计划](../../../phase-1/work-plans/1b/2026-07-21_phase1b1-work-plan.md)
> - [Phase 1b 验收清单](../../../phase-1/checklists/phase-1b.md)
> - [Phase 0 决策文档](../../../phase-0/decisions.md)
>
> **重要说明**：本文件为**开工前的验收计划**（DoD 基线）。实施过程中**不修改**，实际验收结果、需求变更、已知问题等将在完工后的 `acceptance-report.md` 中独立记录。

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段编号 | 1b.1 |
| 里程碑 | 前端骨架 + 全局状态管理 |
| 完成日期 | _____（待完工后填）|
| 关联 checklist | 1b.1, 1b.2, 1b.3, 1b.4, 1b.5 |
| 关联 P0 | [P0-3.1] |
| 关联决策 | 决策 16（Recharts）+ 决策 17（纯 CSS）|

---

## 1. 验收结果（基线 — 待填）

> 本节由验收人在完工后填写，每个 checklist 项 + 附加项的"通过/未通过" + 证据。

### 1.1 checklist 必达项（5 项）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| 1b.1 | Vite + React 18 项目启动 | ☐ 通过 / ☐ 未通过 | （npm run dev 截图 / URL / 日志）|
| 1b.2 | React Router 3 路由可达 | ☐ 通过 / ☐ 未通过 | （手动访问 3 URL 截图）|
| 1b.3 | 4 个 Zustand store 创建 | ☐ 通过 / ☐ 未通过 | （4 个测试文件输出）|
| 1b.4 | Axios 拦截器自动注入 X-User-Id | ☐ 通过 / ☐ 未通过 | （拦截器测试输出）|
| 1b.5 | 侧边栏 240px ↔ 64px 折叠 | ☐ 通过 / ☐ 未通过 | （折叠前后截图）|

### 1.2 附加验收（8 项 — 提升质量）

| # | 验收项 | 状态 | 证据 |
|---|--------|------|------|
| A1 | npm uninstall antd/echarts + install recharts/msw | ☐ 通过 / ☐ 未通过 | （package.json diff）|
| A2 | 4 store 单元测试可见、可读、可写 | ☐ 通过 / ☐ 未通过 | （vitest 输出）|
| A3 | Axios 拦截器测试断言 X-User-Id | ☐ 通过 / ☐ 未通过 | （测试输出）|
| A4 | 侧边栏折叠状态 localStorage 持久化 | ☐ 通过 / ☐ 未通过 | （刷新前后截图）|
| A5 | 全局 CSS 变量定义 | ☐ 通过 / ☐ 未通过 | （variables.css）|
| A6 | src/ 八大目录结构清晰 | ☐ 通过 / ☐ 未通过 | （目录树截图）|
| A7 | vite.config.js 含 /api 代理 | ☐ 通过 / ☐ 未通过 | （配置文件内容）|
| A8 | index.html 含 root + main.jsx | ☐ 通过 / ☐ 未通过 | （文件内容）|

---

## 2. 验收环境

### 2.1 软件版本要求

| 工具 | 版本要求 | 验证命令 |
|------|----------|----------|
| Node.js | ≥ 18.0 | `node -v` |
| npm | ≥ 9.0 | `npm -v` |
| 后端 | Phase 1a 全部 24 项 API | （1b.1 不依赖后端）|
| 浏览器 | Chrome 120+ / Edge 120+ | 手动 |

### 2.2 后端状态（1b.1 不需要，但记录）

- [ ] 后端未启动（1b.1 不影响）

---

## 3. 详细验证步骤

### 3.1 checklist 1b.1 — Vite + React 18 项目启动

**前置**：无

**步骤**：
1. 打开终端，cd 到 `fincontrol-frontend/`
2. 执行 `npm run dev`
3. 观察终端输出，应看到：
   ```
   VITE v5.x.x  ready in xxx ms
   ➜  Local:   http://localhost:5173/
   ```
4. 浏览器访问 `http://localhost:5173/`
5. 打开 DevTools → Console，应**无红色错误**

**通过标准**：浏览器访问 5173，无 Console error。

### 3.2 checklist 1b.2 — React Router 3 路由可达

**步骤**：
1. 浏览器访问 `http://localhost:5173/`（应自动展示 HomePage 占位）
2. 浏览器访问 `http://localhost:5173/data`（应展示 DataPage 占位）
3. 浏览器访问 `http://localhost:5173/ai`（应展示 AIPage 占位）
4. 浏览器访问 `http://localhost:5173/config`、`/correction`、`/nav`、`/ratio`、`/quarterly`（应展示对应灰显占位页）
5. 点击侧边栏菜单，应能相互跳转不报错

**通过标准**：3 个主路由 + 5 个占位路由全部可达，菜单点击切换无报错。

### 3.3 checklist 1b.3 — 4 个 Zustand store 创建

**步骤**：
1. 打开终端，执行 `npm test`
2. 应看到 4 个 store 测试文件全部 PASS：
   - `assetSnapshotStore.test.js`
   - `userConfigStore.test.js`
   - `operationStore.test.js`
   - `chatStore.test.js`
3. 每个测试应至少包含 3 个用例（初始 state / setter / reset）

**通过标准**：4 个 store 测试全 PASS。

### 3.4 checklist 1b.4 — Axios 拦截器自动注入 X-User-Id

**步骤**：
1. 执行 `npm test -- client.test.js`
2. 应看到测试用例 "自动注入 X-User-Id: 1" PASS
3. 测试应 mock 一个 fetch，断言请求头含 `X-User-Id: 1`

**通过标准**：拦截器测试 PASS。

### 3.5 checklist 1b.5 — 侧边栏 240px ↔ 64px 折叠

**步骤**：
1. 浏览器访问 `http://localhost:5173/`
2. 侧边栏初始宽度应为 240px
3. 点击底部折叠按钮
4. 侧边栏宽度应变为 64px，仅图标可见
5. 刷新页面（F5），侧边栏应**保持 64px 折叠态**（localStorage 持久化）

**通过标准**：折叠功能正常 + 刷新后保持。

### 3.6 附加验收 A1 — 包管理调整

**步骤**：
1. 打开 `fincontrol-frontend/package.json`
2. 应看到：
   - `antd`、`echarts`、`echarts-for-react` 已从 dependencies 移除
   - `recharts` 已添加到 dependencies
   - `msw` 已添加到 devDependencies

**通过标准**：package.json 符合预期。

### 3.7 附加验收 A2 — 4 store 单元测试

**步骤**：同 3.3。

**通过标准**：每个 store 测试至少 3 个用例。

### 3.8 附加验收 A3 — Axios 拦截器测试

**步骤**：同 3.4。

**通过标准**：拦截器测试断言 `X-User-Id: 1` 自动注入。

### 3.9 附加验收 A4 — localStorage 持久化

**步骤**：
1. 浏览器访问 `http://localhost:5173/`
2. F12 → Application → LocalStorage → `http://localhost:5173`
3. 应看到 `fincontrol.sidebar.collapsed=true`（折叠后）
4. 折叠 → 刷新 → 折叠状态保持

**通过标准**：localStorage key 存在 + 刷新保持。

### 3.10 附加验收 A5 — 全局 CSS 变量

**步骤**：
1. 打开 `fincontrol-frontend/src/styles/variables.css`
2. 应看到 8 类变量：主色 / 大类色 / 灰阶 / 状态色 / 间距 / 字号 / 圆角阴影 / 布局
3. 数量应 ≥ 20 个 CSS 变量

**通过标准**：variables.css 存在且 ≥ 20 个变量。

### 3.11 附加验收 A6 — 目录结构

**步骤**：
1. 打开 `fincontrol-frontend/src/`
2. 应看到 8 个目录：pages / components / stores / api / hooks / utils / styles / tests

**通过标准**：8 个目录到位。

### 3.12 附加验收 A7 — vite.config.js 代理

**步骤**：
1. 打开 `fincontrol-frontend/vite.config.js`
2. 应看到 `server.proxy['/api']` 配置指向 `http://localhost:8080`

**通过标准**：代理配置正确。

### 3.13 附加验收 A8 — index.html

**步骤**：
1. 打开 `fincontrol-frontend/index.html`
2. 应看到 `<div id="root"></div>` + `<script type="module" src="/src/main.jsx">`

**通过标准**：index.html 符合 Vite 模板要求。

---

## 4. 自动化测试

### 4.1 单元测试（Vitest）

```bash
cd fincontrol-frontend
npm test -- --run
```

**预期输出**：
```
✓ tests/stores/assetSnapshotStore.test.js  (3 tests) ✓
✓ tests/stores/userConfigStore.test.js     (3 tests) ✓
✓ tests/stores/operationStore.test.js      (3 tests) ✓
✓ tests/stores/chatStore.test.js           (3 tests) ✓
✓ tests/api/client.test.js                 (2 tests) ✓

Test Files  5 passed (5)
     Tests  14 passed (14)
```

### 4.2 类型检查（如引入 TypeScript）

1b.1 暂不引入 TypeScript（保持简单），跳过此步。

---

## 5. 验证步骤汇总表

| 步骤 | 工具 | 期望输出 |
|------|------|----------|
| Step 1 | 终端 | `npm run dev` 启动无错 |
| Step 2 | 浏览器 | 5173 端口访问无 Console error |
| Step 3 | 浏览器 | 3 主路由 + 5 占位路由全部可达 |
| Step 4 | 终端 | `npm test` 全 PASS（14/14）|
| Step 5 | 浏览器 | 侧边栏折叠 + 刷新保持 |

---

## 6. 已知风险与缓解

| # | 风险 | 缓解 |
|---|------|------|
| R1 | Node.js 版本 < 18 | 安装 Node 18 LTS |
| R2 | 首次 npm install 慢 | 用 cnpm / 配置 npm 镜像 |
| R3 | 后端未启动 | 1b.1 不依赖后端，可独立验收 |

---

## 7. 退出条件

- [ ] 5 项 checklist 必达项全部通过
- [ ] 8 项附加验收全部通过（其中 A1-A4 必须通过，A5-A8 至少 6 项通过）
- [ ] `npm test` 14/14 PASS
- [ ] `npm run dev` 启动无错
- [ ] **无 Console error / warning**

---

## 8. 验收签字

- 验收人：刘博丞
- 验收日期：____
- 签字：____

---

## 附录 A：手动验证命令清单

```bash
# 1. 启动前端
cd fincontrol-frontend
npm run dev

# 2. 在另一终端跑测试
cd fincontrol-frontend
npm test -- --run

# 3. 验证后端是否启动（1b.1 不需要，但确认环境就绪）
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"} 或 connection refused（1b.1 允许）
```

## 附录 B：环境截图清单

完工后应提供：
1. 浏览器访问 `/` 截图（首页占位 + 侧边栏）
2. 浏览器访问 `/data` 截图（数据管理占位）
3. 浏览器访问 `/ai` 截图（AI 顾问占位）
4. 侧边栏折叠后截图（64px 宽度）
5. F12 → Network → Headers 截图（显示 X-User-Id: 1，可选）
6. `npm test` 终端输出截图
7. F12 → Application → LocalStorage 截图

---

*文档生成时间：2026-07-21*
*配套工作计划：[work-plan.md](../../../phase-1/work-plans/1b/2026-07-21_phase1b1-work-plan.md)*