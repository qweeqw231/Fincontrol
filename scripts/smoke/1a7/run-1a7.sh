#!/usr/bin/env bash
# ============================================
# 1a.7 端到端冒烟（Git Bash 版）
# ============================================
# 用法：cd scripts/smoke/1a7 && ./run-1a7.sh
# 或：  bash scripts/smoke/1a7/run-1a7.sh
# ============================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# script 在 scripts/smoke/1a7/，backend bash 脚本在 ../../../fincontrol-backend/scripts/1a7/
BACKEND_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)/fincontrol-backend"
BACKEND_SCRIPTS="$BACKEND_ROOT/scripts/1a7"

echo "=== 1a.7 端到端冒烟启动 ==="
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo

cd "$BACKEND_ROOT"

# Step 1: 启 MySQL
echo "[Step 1/6] 启 MySQL Docker 容器（或本地 fallback）"
bash "$BACKEND_SCRIPTS/01-up-mysql.sh" || exit 1

# Step 2: 启后端
echo
echo "[Step 2/6] 启 Spring Boot 后端（后台 + 等 8080）"
bash "$BACKEND_SCRIPTS/02-up-backend.sh" || exit 1

# Step 3: 冒烟 1（截图）
echo
echo "[Step 3/6] 冒烟 1 — 截图全链路"
if ! bash "$BACKEND_SCRIPTS/03-smoke-1-screenshot.sh"; then
  echo "  warn: 冒烟 1 失败但继续（minimax 限流可能）"
fi

# Step 4: 冒烟 2（chat）
echo
echo "[Step 4/6] 冒烟 2 — chat 多轮"
bash "$BACKEND_SCRIPTS/04-smoke-2-chat.sh" || true

# Step 5: 覆盖率 + Swagger
echo
echo "[Step 5/6] 覆盖率 + Swagger 端点"
bash "$BACKEND_SCRIPTS/05-coverage.sh" || true

# Step 6: 清理（保留 MySQL 容器）
echo
echo "[Step 6/6] 清理（保留 MySQL 容器）"
bash "$BACKEND_SCRIPTS/99-cleanup.sh" || true

echo
echo "=== 1a.7 端到端冒烟完成 ==="
echo "日志：$(cd "$SCRIPT_DIR/../../.." && pwd)/docs/test-records/manual-tests/2026-07-17_phase1a7-smoke.log"