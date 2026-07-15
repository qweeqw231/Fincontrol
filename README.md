# FinControl Workspace

> 个人资产配置控制系统 —— 多仓库工作区根目录

FinControl 是一个以控制论为理论基础、以真实个人账户为实验平台、以数学求解器为核心引擎的个人资产配置控制系统。

---

## 项目状态

- **当前阶段**：Phase 1 启动前（设计基线已确认）
- **架构文档**：技术设计文档 v2.0（2026-06-10）
- **关键评审**：数据流水线与数据模型评审（2026-07-09，已锁定决策）

---

## 仓库结构（polyrepo）

本工作区采用多仓库（polyrepo）布局，前后端代码各自独立，共享文档与跨端规范集中在 `docs/` 目录：

```
Fincontrol/
├── docs/                              ← 跨端架构文档与评审记录
│   ├── README.md                      ← 文档目录索引
│   ├── architecture/                  ← 架构主文档（待迁移）
│   ├── architecture-review/           ← 架构评审记录
│   └── adr/                           ← 架构决策记录
│
├── fincontrol-backend/                ← 【子仓库 1】Spring Boot 后端
│   └── .git/                          ← Phase 1 启动时执行 git init
│
├── fincontrol-frontend/               ← 【子仓库 2】React 前端
│   └── .git/                          ← Phase 1 启动时执行 git init
│
├── .gitignore                         ← 工作区级忽略规则
└── README.md                          ← 本文件
```

---

## 子仓库初始化指南

### fincontrol-backend（Spring Boot）

```bash
cd fincontrol-backend
git init
# 创建 Spring Boot 3.x 项目骨架（Maven 或 Gradle）
# 推荐包名：com.fincontrol
# 模块：controller / service / mapper / entity / config
```

技术栈：
- Java 17 + Spring Boot 3.x
- MyBatis-Plus
- MySQL 8.0
- DeepSeek API 集成

### fincontrol-frontend（React）

```bash
cd fincontrol-frontend
git init
# 创建 React 项目（Vite 或 CRA）
# 路由：/、/data、/correction、/config、/nav、/ratio、/ai、/quarterly
```

技术栈：
- React 18 + React Router
- ECharts / Recharts（图表）
- Axios（API 调用）

---

## 设计文档导航

### 核心文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 技术设计文档 v2.0 | `docs/architecture/技术设计文档v2.docx`（待迁移） | 11 章 + 2 附录，完整设计基线 |
| 数据流水线评审 | `docs/architecture-review/data-pipeline-review-2026-07-09.md` | 47 个问题，P0 已锁定 |

### 评审记录索引

- **2026-07-09**：数据流水线与数据模型评审（必读，Phase 1 启动依据）

---

## Phase 1 启动检查清单

依据 2026-07-09 评审结论，Phase 1 启动前需完成：

- [ ] 阅读 `docs/architecture-review/data-pipeline-review-2026-07-09.md` 的 P0 文档补正草案
- [ ] 将 P0 补正合并入技术设计文档 v2.1（或在评审文档中执行）
- [ ] 手动核对 18 只基金的 fund_category_map 映射（user_id=1）
- [ ] 初始化 fincontrol-backend 与 fincontrol-frontend 子仓库
- [ ] 前端"确认入库"按钮增加防抖逻辑
- [ ] 后端三表写入加 @Transactional 注解

---

## 与其他项目的关系

- **微控金融（鸿蒙版）**：v1.0 已封存，FinControl 的前身原型
- **DecisionMate**：技术共生项目，共享 React 组件与 DeepSeek API 封装

详见技术设计文档 1.4、1.5 节。

---

## 维护者

- 刘博丞（项目作者）
- 评审者：架构审查助手（2026-07-09 数据流水线评审）