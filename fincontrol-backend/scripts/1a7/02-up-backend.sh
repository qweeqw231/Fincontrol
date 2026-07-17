#!/usr/bin/env bash
# 02-up-backend.sh — 1a.7 启动 Spring Boot 后端（后台）
#
# 用法：bash scripts/1a7/02-up-backend.sh
# 输出：fincontrol-backend 进程（http://127.0.0.1:8080）

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

require_cmd mvn java || exit 1

section "02-up-backend: 启动 Spring Boot 后端"

# 0. 检查前置：MySQL 容器必须已启动
if ! docker ps --format '{{.Names}}' | grep -q "^fincontrol-mysql$"; then
  err "MySQL 容器 fincontrol-mysql 未运行"
  err "请先执行：bash scripts/1a7/01-up-mysql.sh"
  exit 1
fi

# 1. 检查 8080 是否已被占用
if curl -fsS -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q "200"; then
  warn "端口 8080 已有服务在跑（health 200）"
  warn "跳过启动，直接用现有后端"
  ok "02-up-backend 完成（复用）"
  exit 0
fi

# 2. 检查 8080 端口占用
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

# 4. 后台启 mvn spring-boot:run
info "后台启 mvn spring-boot:run（最多 90s 等 ready）"
cd "$BACKEND_ROOT"
# 重定向 stdout+stderr 到 backend.log；保留 PID 供后续脚本
nohup mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7892 \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7892 \
  > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$LOG_DIR/.backend.pid"
log "后端进程 PID=$BACKEND_PID，log=$BACKEND_LOG"

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
