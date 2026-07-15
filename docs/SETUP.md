# FinControl 本地开发环境搭建指南

> Phase 0/1 产出。本指南回答"如何执行 db-schema.sql"、"VISION_API_KEY 在哪配置"、"前后端是否分别启动"等实操问题。

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

**方式 B/C/D**：MySQL Workbench / VS Code MySQL 扩展 / DBeaver/Navicat 可参照操作（详见 git 历史）。

### 1.3 验证数据库

```bash
mysql -u root -p fincontrol -e "SHOW TABLES;"
```

**期望**：输出 7 张表名（`asset_raw` / `asset_snapshot` / `chat_history` / `fund_category_map` / `operation_log` / `prompt_versions` / `user_config`）。

---

## 2. 视觉模型 API Key 配置（Phase 1a.5+：minimax M3 多模态）

### 2.1 申请 Key

访问 minimax 开放平台 https://api.minimaxi.chat/ 注册并创建 API Key。推荐订阅 TokenPlanPlus（含原生多模态视觉 + 长上下文）。

### 2.2 三种配置方式

**方式 A：环境变量（推荐）**

```cmd
set VISION_API_KEY=eyJ-your-real-key-here
```

```powershell
$env:VISION_API_KEY="eyJ-your-real-key-here"
```

```bash
export VISION_API_KEY=eyJ-your-real-key-here
```

关键：环境变量需在启动 `mvn spring-boot:run` 的同一终端中设置。

**方式 B：application-local.yml（推荐 IDE 调试）**

编辑 `fincontrol-backend/src/main/resources/application-local.yml`（该文件已在 .gitignore 中忽略，不会污染仓库）：

```yaml
fincontrol:
  vision:
    api-key: <YOUR_MINIMAX_API_KEY_HERE>
```

**方式 C：IDE Run Configuration**

IntelliJ IDEA / Eclipse：Run → Edit Configurations → Environment variables → 添加 `VISION_API_KEY=eyJ-...`

VS Code + Spring Boot Extension Pack：`.vscode/launch.json` 添加 `"env": { "VISION_API_KEY": "eyJ-..." }`

加载优先级：**环境变量 > application-local.yml > application.yml 占位符（启动时会抛 5001 强制填）**

### 2.3 数据库密码（如非默认 root）

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

首次编译约 3-5 分钟。

### 3.2 启动应用

**前置**：MySQL 已启动 + 视觉模型 API Key 已设置 + db-schema 已建表。

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

Started FincontrolApplication in 5.8 seconds (JVM running for 6.1)
Tomcat started on port 8080
Swagger UI: http://localhost:8080/swagger-ui.html
API Docs:   http://localhost:8080/v3/api-docs
```

### 3.3 验证后端

```cmd
curl http://localhost:8080/actuator/health
```

应返回 `{"status":"UP","components":{"db":{"status":"UP",...}},"diskSpace":{...},"ping":{...}}`。

curl Swagger：

```cmd
curl http://localhost:8080/v3/api-docs | head -20
```

---

## 4. 前端启动（独立终端 2，Phase 1b 启动时启用）

### 4.1 首次安装依赖

```bash
cd Fincontrol/fincontrol-frontend
npm install
```

### 4.2 启动开发服务器

```bash
npm run dev
```

启动成功：`VITE v5.4.10 ready in 500 ms`，本地 `http://localhost:5173/`。

### 4.3 前端代理配置（Phase 1b 完成时启用）

Vite 将 `/api/*` 请求代理到 `http://localhost:8080`：

```js
export default {
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
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

后端和前端**分别**在不同终端窗口中启动；前端启动前必须先启动后端；终端关闭时服务停止（MySQL 除外）。

---

## 6. 测试记录目录

### 6.1 自动生成

后端单元测试运行后，会在 `fincontrol-backend/target/surefire-reports/` 自动生成测试报告。

### 6.2 Phase 1a 验收清单位置

实时验收清单已移至 `docs/phase-1/checklists/phase-1a.md` 和 `phase-1b.md`。

---

## 7. 常见问题

### Q1：MySQL 连接失败 `Communications link failure`

- 检查 MySQL 服务是否启动（`net start mysql` 或服务管理器）
- 检查端口 3306 是否开放
- 检查 `application.yml` 中的 url/username/password

### Q2：minimax API Key 无效 `401 Unauthorized`

- 检查环境变量是否正确（重启终端）
- 验证 API Key 是否有效（minimax 平台测试）
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

`application.yml` 已配置 `serverTimezone=Asia/Shanghai`，如仍异常：

```sql
SELECT @@global.time_zone, @@session.time_zone;
SET GLOBAL time_zone = '+08:00';
```

### Q7：parse 上传的真实截图一直失败

- 检查 `application-local.yml` 的 `fincontrol.vision.api-key` 是否正确
- 检查 `fincontrol.vision.model`（默认 `MiniMax-Text-01`，可能需改成 `MiniMax-VL` 或其它变体）
- 看后端日志 `视觉模型非 2xx: status=... body=...` 拿具体错误码

### Q8：如何停止服务

- 后端：Ctrl+C（终端）
- 前端：Ctrl+C（终端）
- MySQL：服务管理器或 `mysqladmin -u root -p shutdown`

---

## 8. 下一步

完成环境搭建后：

1. 进入 Phase 1a 编码（按 [docs/phase-1/acceptance-criteria.md](./phase-1/acceptance-criteria.md) 的 24 项 API 实现）
2. 每完成一个 API，跑 `mvn -B test -DfailIfNoTests=false` 验证 6/6 PASS
3. 在 [docs/phase-1/checklists/phase-1a.md](./phase-1/checklists/phase-1a.md) 中打勾验收
4. 完成 1a.2 等子阶段，按 [docs/phase-1/testing-guide-1a2.md](./phase-1/testing-guide-1a2.md) curl 端到端测试
