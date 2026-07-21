# Phase 1b.1 工作计划（前端骨架 + 全局状态）

> **状态**：📝 待审阅（开工前）
> **编写日期**：2026-07-21
> **编写者**：架构审查助手（Cline 导师模式）
> **配套文档**：
> - [Phase 1b 验收清单](../../checklists/phase-1b.md)
> - [Phase 1b 验收计划](../../test-records/manual-tests/1b/2026-07-21_phase1b1-acceptance-plan.md)
> - [Phase 0 决策文档](../../phase-0/decisions.md)
> - [Phase 0 API 契约](../../phase-0/api-contract.md)

---

## 0. 元信息

| 字段 | 值 |
|------|---|
| 子阶段编号 | 1b.1 |
| 里程碑 | 前端骨架 + 全局状态管理 |
| 预估工时 | 0.5 天（Day 1 上午，~4 小时）|
| 关联 checklist | 1b.1, 1b.2, 1b.3, 1b.4, 1b.5（Phase 1b 验收清单）|
| 关联 P0 | [P0-3.1] 全局状态管理方案 |
| 关联决策 | 决策 16（图表库=Recharts）+ 决策 17（UI 库=纯 CSS，本计划追加）|
| 前置依赖 | Phase 1a 全部 24 项 API 完成 ✅（已完成）|

---

## 1. 目标

让 FinControl 前端工程跑起来，并具备后续 4 个里程碑所需的基础设施：

1. **Vite + React 18 工程可启动**：浏览器访问 `localhost:5173` 不报错
2. **3 路由可达**：首页（/）、数据管理（/data）、AI 顾问（/ai）+ 5 个灰显占位路由
3. **4 个 Zustand store 创建成功**：assetSnapshotStore / userConfigStore / operationStore / chatStore
4. **Axios 实例 + 拦截器**：自动注入 `X-User-Id: 1`，统一错误处理
5. **侧边栏布局**：240px 宽度，可折叠为 64px，状态保存 localStorage

---

## 2. 范围

### 2.1 必须完成

| 验收项 | 关联 checklist | 内容 |
|--------|----------------|------|
| Vite + React 18 项目启动 | 1b.1 | `npm run dev` 启动，浏览器无错 |
| React Router 3 路由 | 1b.2 | `/`、`/data`、`/ai` 互相跳转可达 |
| 4 个 Zustand store | 1b.3 | assetSnapshot / userConfig / operation / chat |
| Axios 拦截器 | 1b.4 | 自动注入 `X-User-Id: 1` + 错误处理 |
| 侧边栏布局 | 1b.5 | 240px ↔ 64px 折叠 + localStorage 持久化 |

### 2.2 显式不做（避免范围蔓延）

- ❌ 任何业务页面（首页卡片、数据管理上传、AI 聊天 UI）→ 1b.2/1b.3/1b.4
- ❌ 任何图表组件 → 1b.2
- ❌ 任何 fetch hook 业务实现 → 1b.2
- ❌ 配置文件实际加载（dotenv）→ 1b.1 只设默认值
- ❌ 国际化、主题切换、暗色模式 → 远期
- ❌ 单元测试覆盖率 ≥ 70% → Phase 2 才要求
- ❌ E2E 测试（Playwright）→ Phase 4

---

## 3. 验收目标（DoD — Definition of Done）

> 详细验证步骤见配套的 [acceptance-plan.md](../../test-records/manual-tests/1b/2026-07-21_phase1b1-acceptance-plan.md)。

### 3.1 checklist 项（5 项必达）

- [ ] **1b.1**：Vite + React 项目可启动（`npm run dev`，浏览器访问无错）
- [ ] **1b.2**：React Router 配置（/、/data、/ai 三路由可达 + 5 个占位路由）
- [ ] **1b.3**：Zustand stores 创建（[P0-3.1]：assetSnapshotStore、userConfigStore、operationStore、chatStore）
- [ ] **1b.4**：Axios 拦截器（含 X-User-Id 默认 1）
- [ ] **1b.5**：侧边栏布局（240px 宽度，可折叠为 64px）

### 3.2 附加验收（提升质量，非阻塞）

- [ ] **A1**：`npm uninstall antd echarts echarts-for-react`，`npm install recharts msw` 成功
- [ ] **A2**：4 个 store 单元测试可见、可读、可写（`npm test` 跑通）
- [ ] **A3**：Axios 拦截器测试断言 `X-User-Id: 1` 自动注入
- [ ] **A4**：侧边栏折叠状态在刷新页面后保持（localStorage）
- [ ] **A5**：全局 CSS 变量（主色 #1e40af、灰阶、文字色等 8 个变量）已定义
- [ ] **A6**：目录结构清晰（pages / components / stores / api / hooks / utils / styles / tests 八大目录到位）
- [ ] **A7**：`vite.config.js` 含 `/api` → `localhost:8080` 代理配置
- [ ] **A8**：`index.html` 含 `<div id="root">` + `<script src="/src/main.jsx">`（Vite 入口）

---

## 4. 实施步骤（按顺序，10 步）

### Step 1：调整 package.json
```bash
cd fincontrol-frontend

# 移除 antd + ECharts
npm uninstall antd echarts echarts-for-react

# 新增 Recharts + MSW（mock AI 响应用）
npm install recharts
npm install -D msw

# 验证 dev 还能跑（已有 Vite 配置，理论上无影响）
npm run dev   # 应能正常启动
```

### Step 2：创建 `src/` 目录结构
```
fincontrol-frontend/src/
├── main.jsx                    # React 入口
├── App.jsx                     # 路由 + Layout
├── pages/
│   ├── HomePage.jsx            # 占位
│   ├── DataPage.jsx            # 占位
│   ├── AIPage.jsx              # 占位
│   ├── ConfigPage.jsx          # 灰显占位
│   ├── CorrectionPage.jsx      # 灰显占位
│   ├── NAVPage.jsx             # 灰显占位
│   ├── RatioPage.jsx           # 灰显占位
│   └── QuarterlyPage.jsx       # 灰显占位
├── stores/
│   ├── assetSnapshotStore.js
│   ├── userConfigStore.js
│   ├── operationStore.js
│   └── chatStore.js
├── api/
│   ├── client.js               # Axios 实例 + 拦截器
│   └── endpoints.js            # API endpoint 常量
├── components/
│   └── layout/
│       ├── Sidebar.jsx
│       └── Topbar.jsx
├── utils/
│   ├── date.js                 # dayjs 封装
│   └── number.js               # 金额格式化
├── hooks/
│   └── useFetch.js             # 通用 fetch hook（占位）
├── styles/
│   ├── variables.css           # 全局 CSS 变量
│   └── global.css              # 全局 reset + 字体
└── tests/
    ├── stores/
    │   ├── assetSnapshotStore.test.js
    │   ├── userConfigStore.test.js
    │   ├── operationStore.test.js
    │   └── chatStore.test.js
    └── api/
        └── client.test.js      # 拦截器测试
```

### Step 3：创建 `index.html` + `vite.config.js`

**`index.html`**（Vite 模板，~12 行）：
```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>FinControl - 个人资产配置控制</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

**`vite.config.js`**（含代理）：
```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/tests/setup.js'],
  },
})
```

### Step 4：写 `src/main.jsx` + `src/App.jsx`
- `main.jsx`：标准 ReactDOM render
- `App.jsx`：BrowserRouter + Routes + Layout 包装

### Step 5：写 4 个 Zustand store（每个 < 50 行）

**为什么选 Zustand 而非 Redux**：API 类似 Vuex 风格的 `create()`，无需 Provider，5 分钟上手。你鸿蒙 ArkTS 的 `@State` 也是类似思路。

**示例**（`assetSnapshotStore.js`）：
```js
import { create } from 'zustand'

export const useAssetSnapshotStore = create((set, get) => ({
  latestSnapshot: null,
  balance: null,
  operationsRecent: [],
  loading: false,
  error: null,

  setLatest: (data) => set({ latestSnapshot: data }),
  setBalance: (data) => set({ balance: data }),
  setOperations: (data) => set({ operationsRecent: data }),

  reset: () => set({
    latestSnapshot: null,
    balance: null,
    operationsRecent: [],
    loading: false,
    error: null,
  }),
}))
```

其他 3 个 store 同模式（userConfig / operation / chat）。

### Step 6：写 Axios 实例 + 拦截器

**`src/api/client.js`**：
```js
import axios from 'axios'

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json; charset=UTF-8' },
})

// 请求拦截器：自动注入 X-User-Id
apiClient.interceptors.request.use((config) => {
  config.headers['X-User-Id'] = config.headers['X-User-Id'] || '1'
  return config
})

// 响应拦截器：统一处理 {code, data, message} 结构
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      // 业务错误
      return Promise.reject({
        code: body.code,
        message: body.message,
        raw: body,
      })
    }
    return body
  },
  (error) => Promise.reject(error)
)

export default apiClient
```

**`src/api/endpoints.js`**：所有 endpoint 路径常量（暂只定义，未来 1b.2/1b.3/1b.4 逐步补全）。

### Step 7：写 Sidebar + Layout

**Sidebar**：8 个菜单项（首页/数据/月度/配置/净值/比例/AI/季度），后 4 个灰显（`disabled` + 灰色 + tooltip "Phase X 上线"）。折叠按钮放底部。

**Layout**：左侧 Sidebar + 右侧 Outlet（React Router 嵌套）。

### Step 8：写 8 个测试文件

每个 store 一个 `.test.js`，验证：
- 初始 state 正确
- setter 函数能更新 state
- reset 函数能重置

`client.test.js`：拦截器测试（用 mock fetch 拦截，断言 `X-User-Id: 1` 自动注入）。

### Step 9：写全局 CSS 变量

**`src/styles/variables.css`**：
```css
:root {
  /* 主色 */
  --color-primary: #1e40af;
  --color-primary-hover: #1d4ed8;
  --color-primary-light: #dbeafe;

  /* 6 大类（用于环形图配色）*/
  --color-cat-monetary: #10b981;
  --color-cat-bond: #3b82f6;
  --color-cat-commodity: #f59e0b;
  --color-cat-a-share: #ef4444;
  --color-cat-overseas: #8b5cf6;
  --color-cat-hk: #ec4899;

  /* 灰阶 */
  --color-bg: #f9fafb;
  --color-bg-card: #ffffff;
  --color-border: #e5e7eb;
  --color-text-primary: #111827;
  --color-text-secondary: #6b7280;
  --color-text-disabled: #9ca3af;

  /* 状态色 */
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-error: #ef4444;

  /* 间距 */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-6: 24px;
  --space-8: 32px;

  /* 字号 */
  --text-sm: 12px;
  --text-base: 14px;
  --text-lg: 16px;
  --text-xl: 20px;
  --text-2xl: 24px;
  --text-3xl: 32px;

  /* 圆角与阴影 */
  --radius: 6px;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.07);

  /* 布局 */
  --sidebar-width: 240px;
  --sidebar-width-collapsed: 64px;
}
```

### Step 10：写工作计划 + 验收计划文档（即本文件 + acceptance-plan.md）

本计划已写完，验收计划另写。

---

## 5. 关键决策（已锁定，无需用户决策）

| # | 决策点 | 选择 | 理由 |
|---|--------|------|------|
| 1 | 状态管理库 | **Zustand** | 与后端 Pinia/Vuex 风格相近，5 分钟上手；体积 1KB |
| 2 | HTTP 客户端 | **Axios** | 与后端 API 错误码体系（code/data/message）天然契合 |
| 3 | 测试框架 | **Vitest + RTL + MSW** | Vite 同源，无需复杂配置；MSW 拦截 AI 响应 |
| 4 | 路由库 | **React Router v6** | SPA 标准，嵌套路由用 Outlet |
| 5 | 图表库 | **Recharts**（决策 16）| API 简单（`<PieChart><Pie>`）；包体小 |
| 6 | UI 组件库 | **纯 CSS**（决策 17）| 工科风 + 学习价值 + 简历亮点 |
| 7 | 侧边栏折叠状态 | **localStorage 持久化** | 刷新保持体验 |
| 8 | Axios baseURL | **`/api`**（相对路径）| 通过 Vite 代理转发到 :8080，避免 CORS |
| 9 | Vite 代理 | **`/api` → `http://localhost:8080`** | 解决 dev 期跨域 |
| 10 | 日期库 | **dayjs** | 轻量，2KB；snapshot_date 格式化和距今校验 |
| 11 | package.json type | **module**（保留）| Vite 强制 ESM |

---

## 6. 4 个 Store 字段预览

### assetSnapshotStore（首页 + 数据管理页共用）
```js
{
  latestSnapshot: null,        // GET /snapshot/latest 响应
  balance: null,               // GET /asset/balance 响应
  operationsRecent: [],        // GET /asset/operations/recent 响应
  loading: false,
  error: null,
  // setters + reset
}
```

### userConfigStore（配置页 + 月度操作台）
```js
{
  targetRatios: { 货币类: 10, 固收类: 15, 商品类: 25, A股权益类: 25, 海外权益类: 20, 港股大中华类: 5 },
  budgetLimit: 1000,
  purchaseThreshold: 100,
  highVolDcaBudget: 560,
  loaded: false,
  // setters + reset
}
```

### operationStore（月度操作台 + 首页操作时间线）
```js
{
  recentOperations: [],
  loading: false,
  // setters + reset
}
```

### chatStore（AI 顾问页）
```js
{
  conversations: [],            // GET /conversations
  currentConversationId: null,
  messages: [],                 // 当前对话 messages
  loading: false,
  // setters + reset
}
```

---

## 7. 风险与依赖

### 7.1 风险

| # | 风险 | 影响 | 应对 |
|---|------|------|------|
| R1 | Node.js 版本不兼容 Vite 5 | 中 | Vite 5 要求 Node 18+；如未装需先升级 |
| R2 | npm install 网络慢 | 低 | 已有 node_modules，1b.1 只需新增/移除少量包 |
| R3 | 后端未启动导致联调失败 | 低 | 1b.1 不依赖后端，4 个 store 在 1b.2 才接 API |

### 7.2 依赖

- 后端：1b.1 不依赖（无 API 调用）
- 后端：1b.2 起依赖 `mvn spring-boot:run` 在 :8080
- 包管理：node_modules 已存在（Phase 0 阶段已 npm install 过）

---

## 8. 决策追加计划

本里程碑完成后，将追加 2 条决策到 `docs/phase-0/decisions.md`：

### 决策 16：图表库选型 = Recharts（2026-07-21）

**背景**：Phase 1a 时前端脚手架沿用了 ECharts（与后端图表组件复用）。Phase 1b 启动时发现：
- ECharts API 是 option 对象（命令式），React 心智负担重
- 包体大（~1MB），与"工科风 + 简洁"不符
- Recharts 是 React-native 组件（声明式），与 React 心智一致
- 包体小（~100KB）

**决策**：Phase 1b 及之后图表统一用 Recharts。移除 `echarts` + `echarts-for-react`。

**影响**：
- package.json：移除 2 个包，新增 `recharts`
- 1b.2 首页环形图改用 `<PieChart><Pie data={...} />`
- 1b.3 数据管理无影响
- Phase 3 净值曲线仍用 Recharts（趋势线、面积图）

### 决策 17：UI 库选型 = 纯 CSS（2026-07-21）

**背景**：Phase 0 时前端脚手架装了 `antd`（Ant Design），是 antd 5.x（~700KB）。Phase 1b 启动时发现：
- antd 样式与"工科风"不符（圆角大、颜色鲜）
- 包体大（~700KB）
- 简历价值：手写 CSS 展示前端功底
- Phase 1b 只需极少量组件（Toast / Modal / Button），纯 CSS < 50 行可实现

**决策**：Phase 1b 不引入任何 UI 组件库，纯手写 CSS。移除 `antd`。

**影响**：
- package.json：移除 `antd`
- 自实现：`Button` / `Toast` / `Modal` 等基础组件（每个 < 50 行 CSS + JSX）
- Phase 2/3 仍坚持此原则（除非 antd 的复杂组件如 DatePicker / Table 能显著降低工作量）

---

## 9. 文档维护

- 实施过程中如发现需要修改本计划，**不在本文件改**，而是在完工后的 `acceptance-report.md` 中记录"需求变更记录"段
- 重要决策追加到 `docs/phase-0/decisions.md`
- checklist 中对应项（1b.1–1b.5）勾选并填完成日期

---

## 10. 验收人

- 计划编写：Cline（架构审查助手）
- 计划审阅：刘博丞（项目作者）
- 实施人：Cline（架构审查助手）
- 验收人：刘博丞（项目作者）

---

*文档生成时间：2026-07-21*
*配套验收计划：[acceptance-plan.md](../../test-records/manual-tests/1b/2026-07-21_phase1b1-acceptance-plan.md)*