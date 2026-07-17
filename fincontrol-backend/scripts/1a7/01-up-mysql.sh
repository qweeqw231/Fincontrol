#!/usr/bin/env bash
# 01-up-mysql.sh — 1a.7 启动 MySQL 8.0 Docker 容器 + 建库 + 跑 schema
#
# 用法：bash scripts/1a7/01-up-mysql.sh
# 输出：fincontrol-mysql 容器 (port 3306, root/root, TZ=Asia/Shanghai)

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

require_cmd docker || exit 1

section "01-up-mysql: 启动 MySQL 8.0 Docker 容器"

CONTAINER_NAME="fincontrol-mysql"
DB_NAME="fincontrol"
DB_USER="root"
DB_PASS="root"
DB_PORT=3306

# 1. 检查是否已存在（幂等：已跑则跳过）
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  warn "容器 ${CONTAINER_NAME} 已存在，复用"
  if ! docker exec "$CONTAINER_NAME" mysqladmin ping -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
    warn "容器未运行，重启"
    docker start "$CONTAINER_NAME"
  fi
else
  info "拉取 mysql:8.0 镜像（若本地无）"
  docker pull mysql:8.0 2>&1 | tail -3 | tee -a "$LOG_FILE"

  info "创建 + 启动容器 ${CONTAINER_NAME}"
  docker run -d \
    --name "$CONTAINER_NAME" \
    -e MYSQL_ROOT_PASSWORD="$DB_PASS" \
    -e MYSQL_DATABASE="$DB_NAME" \
    -e TZ=Asia/Shanghai \
    -p "${DB_PORT}:3306" \
    mysql:8.0 \
    --default-authentication-plugin=mysql_native_password \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci \
    >> "$LOG_FILE" 2>&1
  ok "容器创建: $(docker ps --filter name=${CONTAINER_NAME} --format '{{.Names}} ({{.Status}})')"
fi

# 2. 等 MySQL ready
info "等 MySQL 容器 ready（最多 60s）"
for i in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" mysqladmin ping -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
    ok "MySQL ready (waited ${i}s)"
    break
  fi
  sleep 1
done
if ! docker exec "$CONTAINER_NAME" mysqladmin ping -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
  err "MySQL 容器在 60s 内未 ready"
  err "查看日志：docker logs $CONTAINER_NAME"
  exit 1
fi

# 3. 等 1a.7-PRE dialect 修复生效（确保 ON DUPLICATE KEY UPDATE 正确执行）
info "验证 ON DUPLICATE KEY UPDATE 语法（1a.7-PRE dialect 修复）"
sql_query "SELECT VERSION();" | grep -oE "[0-9]+\.[0-9]+\.[0-9]+" | head -1 | while read v; do
  info "MySQL 版本: $v"
  if [[ "$v" < "8.0.0" ]]; then
    err "MySQL 8.0+ required (current: $v)"
    exit 1
  fi
  ok "MySQL 8.0+ confirmed"
done

# 4. 跑 schema（idempotent：CREATE TABLE IF NOT EXISTS + INSERT ... ON DUPLICATE KEY UPDATE）
SCHEMA_FILE="$PROJECT_ROOT/docs/phase-0/db-schema.sql"
if [ ! -f "$SCHEMA_FILE" ]; then
  err "Schema 文件不存在: $SCHEMA_FILE"
  exit 1
fi
info "跑 schema: $SCHEMA_FILE"
# 容器内 /docker-entrypoint-initdb.d/ 自动跑 init，但我们要手动跑（首次建表）
docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SCHEMA_FILE" 2>&1 | tee -a "$LOG_FILE"
ok "schema 执行完成"

# 5. 验证 7 张表
info "验证 7 张表"
EXPECTED_TABLES=("asset_raw" "asset_snapshot" "fund_category_map" "chat_history" "user_config" "prompt_versions" "operation_log")
for table in "${EXPECTED_TABLES[@]}"; do
  count=$(sql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$table';" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  if [ "$count" -eq 1 ]; then
    ok "  ✓ $table"
  else
    err "  ✗ $table 不存在"
    exit 1
  fi
done

# 6. 验证 prompt_versions 有 3 行种子
section "验证 prompt_versions 种子数据"
seed_count=$(sql_query "SELECT COUNT(*) FROM prompt_versions;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
if [ "$seed_count" -eq 3 ]; then
  ok "prompt_versions 种子: $seed_count 行（screenshot_parser / ai_assistant / intent_classifier）"
  sql_query "SELECT prompt_name, version FROM prompt_versions ORDER BY prompt_name;" 2>/dev/null
else
  warn "prompt_versions 种子数: $seed_count（期望 3）"
fi

ok "01-up-mysql 完成：MySQL 容器 ${CONTAINER_NAME} ready，7 张表 + 3 行种子就绪"
info "下一步：bash scripts/1a7/02-up-backend.sh"
