#!/usr/bin/env bash
# 02-up-backend.sh — 1a.7 启动 Spring Boot 后端（后台）
#
# 用法：bash scripts/1a7/02-up-backend.sh
# 输出：fincontrol-backend 进程（http://127.0.0.1:8080）
#
# 环境变量：
#   DB_HOST, DB_PORT, DB_USER, DB_PASSWORD  → 传给 application.yml
#   MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASS  → 同上别名（01 脚本风格）
#   SERVER_PORT  → 8080（默认）
#   UPLOAD_SCREENSHOT_PATH → 截图存储路径

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

require_cmd mvn java || exit 1

# 兼容两种命名风格（01 脚本用 MYSQL_*，Spring 用 DB_*）
DB_HOST="${DB_HOST:-${MYSQL_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${MYSQL_PORT:-3306}}"
DB_USER="${DB_USER:-${MYSQL_USER:-root}}"
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PASS:-root}}"
CONTAINER_NAME="${CONTAINER_NAME:-fincontrol-mysql}"

section "02-up-backend: 启动 Spring Boot 后端（DB=$DB_HOST:$DB_PORT user=$DB_USER）"

# 0. 检查前置：MySQL 可达（本地或 Docker 二选一）
LOCAL_OK=0
if mysqladmin ping -h127.0.0.1 -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" --silent 2>/dev/null; then
  LOCAL_OK=1
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$" 2>/dev/null; then
  if docker exec "$CONTAINER_NAME" mysqladmin ping -u"$DB_USER" -p"$DB_PASSWORD" --silent 2>/dev/null; then
    LOCAL_OK=1
  fi
fi
if [ "$LOCAL_OK" -eq 0 ]; then
  err "MySQL 不可达（本地或 Docker 容器）"
  err "  1) 本地 MySQL: net start mysql"
  err "  2) Docker:    bash scripts/1a7/01-up-mysql.sh"
  exit 1
fi
ok "MySQL ready（本地或 Docker 容器），目标 DB_HOST=$DB_HOST DB_PORT=$DB_PORT"

# 1. 检查 8080 是否已被占用
if curl -fsS -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q "200"; then
  warn "端口 8080 已有服务在跑（health 200）"
  warn "跳过启动，直接用现有后端"
  ok "02-up-backend 完成（复用）"
  exit 0
fi

# 2. 优先复用 Docker 容器 fincontrol-backend（docker-compose up 已起）
BACKEND_CONTAINER="${BACKEND_CONTAINER:-fincontrol-backend}"
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q "^${BACKEND_CONTAINER}$" 2>/dev/null; then
  info "Docker 容器 ${BACKEND_CONTAINER} 已在跑（docker-compose up 启动），等 health UP..."
  if ! wait_for_url "http://127.0.0.1:8080/actuator/health" 90; then
    err "Docker backend 在 90s 内未 health UP"
    err "查看日志：docker logs $BACKEND_CONTAINER"
    exit 1
  fi
  ok "Docker backend ${BACKEND_CONTAINER} ready：$(curl -sS http://127.0.0.1:8080/actuator/health)"
  ok "02-up-backend 完成：Docker 容器 ${BACKEND_CONTAINER} 在 8080 ready"
  info "下一步：bash scripts/1a7/03-smoke-1-screenshot.sh"
  exit 0
fi

# 3. 检查 8080 端口占用（非我们的后端）
if netstat -an 2>/dev/null | grep -q ":8080.*LISTENING" || ss -tln 2>/dev/null | grep -q ":8080 "; then
  err "端口 8080 已被占用（但非我们的后端）"
  err "请检查：netstat -ano | findstr :8080"
  exit 1
fi

# 3. 确认 application-local.yml 已合并 fincontrol: 单块
LOCAL_YML="$BACKEND_ROOT/src/main/resources/application-local.yml"
if [ ! -f "$LOCAL_YML" ]; then
  err "$LOCAL_YML 不存在（请先建好）"
  exit 1
fi
info "应用 local profile（继承 application-local.yml 覆盖）"

# 4. 后台启 mvn spring-boot:run（DB_HOST/DB_PORT 等传给 Spring）
info "后台启 mvn spring-boot:run（最多 90s 等 ready）"
cd "$BACKEND_ROOT"
# 重定向 stdout+stderr 到 backend.log；保留 PID 供后续脚本
nohup mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--DB_HOST=$DB_HOST --DB_PORT=$DB_PORT --DB_USER=$DB_USER --DB_PASSWORD=$DB_PASSWORD" \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7892 \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7892 \
  > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$LOG_DIR/.backend.pid"
log "后端进程 PID=$BACKEND_PID，log=$BACKEND_LOG，DB=$DB_HOST:$DB_PORT"

# 5. 等 health UP
info "等 actuator/health UP（最多 90s）"
if ! wait_for_url "http://127.0.0.1:8080/actuator/health" 90; then
  err "后端在 90s 内未 ready"
  err "log 最后 30 行："
  tail -n 30 "$BACKEND_LOG" | sed 's/^/  /'
  exit 1
fi

ok "后端 ready：$(curl -sS http://127.0.0.1:8080/actuator/health)"

# 6. 等 Swagger API doc 加载
info "等 /v3/api-docs 可访问"
if ! wait_for_url "http://127.0.0.1:8080/v3/api-docs" 30; then
  warn "/v3/api-docs 不可访问（但 health UP）"
fi

ok "02-up-backend 完成：后端在 8080 ready，PID=$BACKEND_PID"
info "下一步：bash scripts/1a7/03-smoke-1-screenshot.sh"
