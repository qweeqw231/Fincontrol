#!/usr/bin/env bash
# 99-cleanup.sh — 1a.7 清理（关后端 + 删 MySQL 容器，可选）
#
# 用法：bash scripts/1a7/99-cleanup.sh
# 默认：仅关后端进程（保留 MySQL 容器供后续调试）

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

section "99-cleanup: 清理 1a.7 端到端环境"

# 1. 停后端进程
if [ -f "$LOG_DIR/.backend.pid" ]; then
  BACKEND_PID=$(cat "$LOG_DIR/.backend.pid")
  if kill -0 "$BACKEND_PID" 2>/dev/null; then
    info "停后端进程 PID=$BACKEND_PID"
    kill "$BACKEND_PID" 2>/dev/null || true
    sleep 2
    if kill -0 "$BACKEND_PID" 2>/dev/null; then
      warn "进程未响应 SIGTERM，强杀 SIGKILL"
      kill -9 "$BACKEND_PID" 2>/dev/null || true
    fi
    ok "后端进程已停"
  else
    info "后端进程 $BACKEND_PID 已不在"
  fi
  rm -f "$LOG_DIR/.backend.pid"
else
  # 退路：按端口杀
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    warn "8080 仍在响应，杀端口进程"
    if command -v fuser >/dev/null 2>&1; then
      fuser -k 8080/tcp 2>&1 || true
    elif command -v lsof >/dev/null 2>&1; then
      lsof -ti:8080 | xargs -r kill -9 2>&1 || true
    fi
    sleep 1
  fi
fi

# 2. 删 MySQL 容器（仅当显式 --with-mysql）
if [ "${1:-}" = "--with-mysql" ] || [ "${1:-}" = "-m" ]; then
  if docker ps -a --format '{{.Names}}' | grep -q "^fincontrol-mysql$"; then
    info "删 MySQL 容器 fincontrol-mysql（--with-mysql）"
    docker rm -f fincontrol-mysql
    ok "MySQL 容器已删"
  fi
else
  info "保留 MySQL 容器（删除用 --with-mysql / -m）"
  if docker ps --format '{{.Names}}' | grep -q "^fincontrol-mysql$"; then
    ok "MySQL 容器 fincontrol-mysql 仍在运行（保留供 1b 联调）"
  fi
fi

# 3. 总结
section "99-cleanup 完成"
ok "后端进程已停"
ok "日志保留在 $LOG_FILE"
ok "API 输出保留在 $API_OUT_DIR"
info "重启后端：bash scripts/1a7/02-up-backend.sh"
info "完全清理：bash scripts/1a7/99-cleanup.sh --with-mysql"
