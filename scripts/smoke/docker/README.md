# scripts/smoke/docker/ — Docker 模式验证脚本

> 实验性脚本：用于验证 Docker 容器化路径（Layer 1 + Layer 2）。
> 这些不是正式冒烟流程，是 docker 集成验证工具。

## 脚本清单

| 脚本 | 验证目标 | 对应 1a.7 验收报告章节 |
|---|---|---|
| `run-test-docker-mysql.bat` | Layer 1：仅 Docker MySQL 在 3307（保留本地 3306）| §9.1 |
| `run-test-backend-docker-mysql.bat` | Layer 1+2 混合：本地 mvn 后端 + Docker MySQL on 3307 | §9.1 |
| `run-test-smoke2-docker.bat` | 通用：跑 chat smoke against 当前运行的 backend | — |
| `run-test-docker-compose-up.bat` | Layer 2：docker compose 一键起 MySQL + backend | §9.2 |

## 使用示例

### Layer 1: 验证 Docker MySQL 在 3307
```cmd
run-test-docker-mysql.bat           :: 启 Docker MySQL 容器（3307→3306）
run-test-backend-docker-mysql.bat   :: 启本地 mvn 后端连 Docker MySQL
run-test-smoke2-docker.bat          :: 跑 chat smoke 验证
```

### Layer 2: 验证 docker-compose 全栈
```cmd
run-test-docker-compose-up.bat      :: docker compose down -v && up -d
                                    :: 等待两个容器 healthy
run-test-smoke2-docker.bat          :: 跑 chat smoke against backend 容器
```

## 已知问题

- **prod profile 无 AI key**：容器内 `SPRING_PROFILES_ACTIVE=prod`，没有 `VISION_API_KEY/TEXT_AI_API_KEY` 环境变量，
  AI 路由 5001 是**预期行为**。如要跑通 AI 路由，需在 `docker-compose.yml` 取消注释并填入真实 key。
- **首次构建慢**：Maven 镜像下载 + 依赖下载约 3-4 分钟。