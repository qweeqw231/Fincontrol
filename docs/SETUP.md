# FinControl 本地开发环境搭建指南

> Phase 0 产出。本指南回答"如何执行 db-schema.sql"、"DEEPSEEK_API_KEY 在哪配置"、"前后端是否分别启动"等实操问题。

---

## 0. 前置依赖检查

开始之前，确认以下软件已安装：

| 软件 | 最低版本 | 检查命令 |
|------|---------|---------|
| MySQL | 8.0 | `mysql --version` |
| Java JDK | 17 | `java --version` |
| Maven | 3.9 | `mvn --version` |
| Node.js | 20 | `node --version` |
| npm | 10 | `npm --version` |
| Git | 2.x | `git --version` |

如果某项缺失，先安装再继续。

---

## 1. 数据库初始化（执行 db-schema.sql + seed-data.sql）

### 1.1 创建数据库

打开 MySQL 客户端（命令行、MySQL Workbench、DBeaver、Navicat 等均可）。

**命令行方式**（推荐）：

```bash
# 登录 MySQL（输入密码）
mysql -u root -p
```

登录后执行：

```sql
CREATE DATABASE IF NOT EXISTS fincontrol
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

EXIT;
```

### 1.2 执行 Schema 文件

**方式 A：命令行（推荐）**

```bash
# 在项目根目录 Fincontrol/ 下执行
mysql -u root -p fincontrol < docs/phase-0/db-schema.sql
mysql -u root -p fincontrol < docs/phase-0/seed-data.sql
```

输入密码后，表结构创建完成。

**方式 B：MySQL Workbench**

1. 打开 MySQL Workbench
2. 连接本地 MySQL Server
3. **File → Open SQL Script** → 选择 `docs/phase-0/db-schema.sql`
4. 点击工具栏 ⚡ Execute 按钮
5. 同样方式执行 `docs/phase-0/seed-data.sql`

**方式 C：VS Code MySQL 扩展**

1. 安装 VS Code 扩展："MySQL"（作者 cweijan）
2. 配置 MySQL 连接
3. 右键 `docs/phase-0/db-schema.sql` → "Run MySQL Query"
4. 同样方式运行 `docs/phase-0/seed-data.sql`

**方式 D：DBeaver / Navicat / DataGrip**

新建 MySQL 连接 → 打开 SQL 编辑器 → 粘贴 SQL 文件内容 → 执行。

### 1.3 验证数据库

```bash
mysql -u root -p fincontrol -e "
  SHOW TABLES;
  SELECT COUNT(*) AS fund_count FROM fund_category_map;
  SELECT * FROM user_config;
"
```

**期望输出**：

```
+------------------------+
| Tables_in_fincontrol   |
+------------------------+
| asset_raw              |
| asset_snapshot         |
| chat_history           |
| fund_category_map      |
| operation_log          |
| prompt_versions        |
| user_config            |
+------------------------+

fund_count: 20

（user_config 5 行配置）
```

如果显示 7 张表、20 条基金映射、5 行配置，则数据库初始化成功。

---

## 2. DeepSeek API Key 配置

### 2.1 申请 Key

访问 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册并创建 API Key。

### 2.2 三种配置方式

**方式 A：环境变量（推荐，Phase 1a 推荐）**

Windows CMD：
```cmd
set DEEPSEEK_API_KEY=sk-your-real-key-here
```

Windows PowerShell：
```powershell
$env:DEEPSEEK_API_KEY="sk-your-real-key-here"
```

Linux/macOS：
```bash
export DEEPSEEK_API_KEY=sk-your-real-key-here
```

**关键**：环境变量需在启动 `mvn spring-boot:run` 的同一终端中设置，否则 Spring Boot 读取不到。

**方式 B：application-local.yml（本地覆盖文件，推荐 IDE 调试）**

创建 `fincontrol-backend/src/main/resources/application-local.yml`：

```yaml
deepseek:
  api-key: sk-your-real-key-here
```

Spring Boot 会自动加载 `application-local.yml`（优先级高于 `application.yml`）。

**重要**：手动将 `application-local.yml` 添加到 `.gitignore`（避免误提交密钥）：

```bash
echo "application-local.yml" >> fincontrol-backend/.gitignore
```

**方式 C：IDE Run Configuration**

IntelliJ IDEA / Eclipse：
1. Run → Edit Configurations
2. Environment variables → 添加 `DEEPSEEK_API_KEY=sk-...`
3. 保存

VS Code + Spring Boot Extension Pack：
1. `.vscode/launch.json`
2. 添加 `"env": { "DEEPSEEK_API_KEY": "sk-..." }`

### 2.3 数据库密码（如非默认 root）

如果 MySQL root 密码不是 `root`，设置环境变量：

Windows CMD：
```cmd
set DB_PASSWORD=your-mysql-password
```

或在 `application-local.yml` 中：

```yaml
spring:
  datasource:
    password: your-mysql-password
```

---

## 3. 后端启动（独立终端 1）

### 3.1 首次编译

```bash
cd Fincontrol/fincontrol-backend
mvn clean install -DskipTests
```

首次编译约 3-5 分钟（下载依赖）。

### 3.2 启动应用

**前置**：确保 MySQL 已启动、DeepSeek API Key 已设置。

```bash
# 在 fincontrol-backend/ 目录
mvn spring-boot:run
```

启动成功日志：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

Started FincontrolApplication in 3.45 seconds (JVM running for 4.12)
Swagger UI: http://localhost:8080/swagger-ui.html
API Docs:   http://localhost:8080/v3/api-docs
```

### 3.3 验证后端

打开浏览器：http://localhost:8080/swagger-ui.html

应看到 springdoc-openapi 生成的 API 文档（Phase 1a 编码完成后才有内容）。

curl 测试：

```bash
curl http://localhost:8080/v3/api-docs | head -50
```

应返回 OpenAPI 3 JSON 规范。

---

## 4. 前端启动（独立终端 2）

### 4.1 首次安装依赖

```bash
cd Fincontrol/fincontrol-frontend
npm install
```

首次安装约 2-5 分钟。Phase 0 仅创建 `package.json`，无 `package-lock.json`，npm 会自动生成。

**国内镜像加速**（可选）：

```bash
npm config set registry https://registry.npmmirror.com
npm install
```

### 4.2 启动开发服务器

```bash
npm run dev
```

启动成功：

```
VITE v5.4.10  ready in 500 ms

➜  Local:   http://localhost:5173/
➜  Network: use --host to expose
```

### 4.3 验证前端

打开浏览器：http://localhost:5173

Phase 0 阶段页面是空白（仅 Vite 默认页面 + index.html），需要 Phase 1b 编码后才有内容。

### 4.4 前端代理配置（Phase 1b 完成时启用）

Vite 会将 `/api/*` 请求代理到 `http://localhost:8080`，无需 CORS 配置。

待 Phase 1b 时在 `vite.config.js` 中添加：

```js
export default {
  // ... 其他配置
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}
```

---

## 5. 三个终端的典型启动顺序

```
终端 1（MySQL）        : MySQL 服务一直运行
终端 2（Backend）      : cd fincontrol-backend && mvn spring-boot:run
终端 3（Frontend）     : cd fincontrol-frontend && npm run dev
```

**关键**：
- 后端和前端**分别**在**不同的终端窗口**中启动
- 前端启动前必须先启动后端（前端要调用后端 API）
- 终端关闭时服务停止（MySQL 除外，需在系统服务中保持运行）

---

## 6. 测试记录目录

### 6.1 自动生成（无需手动创建）

后端单元测试运行后，会在 `fincontrol-backend/target/surefire-reports/` 自动生成测试报告。

### 6.2 手动测试记录（推荐创建）

建议在 `docs/test-records/` 下保存：

```
docs/test-records/
├── README.md                              ← 本说明
├── manual-tests/                          ← 手动测试记录
│   └── 2026-07-09_smoke-test.md
└── api-test-output/                       ← API 测试输出
    └── 2026-07-09_screenshot_parse.json
```

### 6.3 Phase 1a 验收清单位置

实时验收清单已移至 `docs/phase-1/checklists/phase-1a.md`，Phase 1a 编码期间每完成一项即勾选。

---

## 7. 常见问题

### Q1：MySQL 连接失败 `Communications link failure`

- 检查 MySQL 服务是否启动（`net start mysql` 或服务管理器）
- 检查端口 3306 是否开放
- 检查 `application.yml` 中的 url、username、password

### Q2：DeepSeek API Key 无效 `401 Unauthorized`

- 检查环境变量是否正确（重启终端）
- 验证 API Key 是否有效（DeepSeek 平台测试）
- 检查账户余额

### Q3：前端 `npm install` 慢或失败

```bash
npm config set registry https://registry.npmmirror.com
```

### Q4：后端启动 `mvn spring-boot:run` 找不到命令

需要安装 Maven 或使用 IDE（IntelliJ IDEA 自带 Maven）。

### Q5：端口冲突

- 后端 8080：修改 `application.yml` 中 `server.port: 8081`
- 前端 5173：修改 `vite.config.js` 中 `server.port: 5174`
- MySQL 3306：检查是否有其他 MySQL 实例

### Q6：时区问题（DATETIME 偏差 8 小时）

`application.yml` 中已配置 `serverTimezone=Asia/Shanghai`，如仍异常检查 MySQL 时区：

```sql
SELECT @@global.time_zone, @@session.time_zone;
SET GLOBAL time_zone = '+08:00';
```

### Q7：如何停止服务

- 后端：Ctrl+C（终端）
- 前端：Ctrl+C（终端）
- MySQL：服务管理器停止 或 `mysqladmin -u root -p shutdown`

---

## 8. 下一步

完成环境搭建后：

1. 在 Phase 1a 编码前，确认数据库连接 + 后端启动 + 前端启动三个绿灯
2. 进入 Phase 1a 后端编码（按 [docs/phase-1/acceptance-criteria.md](./phase-1/acceptance-criteria.md) 的 24 项 API 实现）
3. 每完成一个 API，用 Swagger UI 或 curl 测试
4. 在 [docs/phase-1/checklists/phase-1a.md](./phase-1/checklists/phase-1a.md) 中打勾验收
</content>