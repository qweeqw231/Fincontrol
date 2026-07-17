#!/usr/bin/env bash
# 01-up-mysql.sh — 1a.7 启动 MySQL 8.0
#
# 优先级：
#   1. 如有 MYSQL_HOST 环境变量（自定）→ 跳过本脚本，提示后续步骤用
#      $MYSQL_HOST / $MYSQL_PORT / $MYSQL_USER / $MYSQL_PASS
#   2. 端口 3306 被占（本地 mysqld.exe 在跑）→ 直接用本地 MySQL
#   3. 端口 3306 空闲 → Docker 启 mysql:8.0 容器（默认 3306:3306）

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

DB_NAME="fincontrol"
DB_USER="root"
DB_PASS="root"
DB_PORT=3306

section "01-up-mysql: 启动 MySQL 8.0（自动选 Docker / 本地）"

# 1. 优先用环境变量指定的外部 MySQL
if [ -n "$MYSQL_HOST" ]; then
  info "环境变量 MYSQL_HOST=$MYSQL_HOST 已设，跳过启动"
  if [ "$MYSQL_HOST" = "127.0.0.1" ] || [ "$MYSQL_HOST" = "localhost" ]; then
    info "用本地 MySQL（端口 $MYSQL_PORT）"
  else
    info "用远程 MySQL $MYSQL_HOST:$MYSQL_PORT"
  fi
  ok "01-up-mysql 跳过（外部 MySQL）"
  exit 0
fi

# 2. 端口 3306 已被占？—— 优先用本地 MySQL
if netstat -an 2>/dev/null | grep -q ":3306.*LISTENING" || ss -tln 2>/dev/null | grep -q ":3306 "; then
  warn "端口 3306 已被占 → 用本地 MySQL（已含 fincontrol 库 + 7 张表 + 3 种子）"
  for i in $(seq 1 30); do
    if mysqladmin ping -h127.0.0.1 -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
      ok "本地 MySQL ready (waited ${i}s)"
      break
    fi
    sleep 1
  done
  if ! mysqladmin ping -h127.0.0.1 -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
    err "本地 MySQL 在 30s 内未 ready"
    exit 1
  fi
  # 验证 7 张表 + 3 种子
  table_count=$(mysql -uroot -proot fincontrol -e "SHOW TABLES;" 2>/dev/null | grep -v "^Tables_in" | grep -cE "^\S+$")
  if [ "$table_count" -ne 7 ]; then
    err "本地 MySQL fincontrol 库表数=$table_count（期望 7），需先跑 db-schema.sql"
    err "  mysql -uroot -proot fincontrol < docs/phase-0/db-schema.sql"
    exit 1
  fi
  ok "本地 MySQL fincontrol 库就绪：$table_count 张表"
  # 验证 prompt_versions 种子
  seed_count=$(mysql -uroot -proot fincontrol -e "SELECT COUNT(*) FROM prompt_versions;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  ok "prompt_versions 种子: $seed_count 行"
  ok "01-up-mysql 完成：本地 MySQL 模式"
  exit 0
fi

# 3. 端口空闲 → Docker MySQL
require_cmd docker || exit 1

CONTAINER_NAME="fincontrol-mysql"

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

# 等 MySQL ready
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

# 版本检查
info "验证 MySQL 版本（需 ≥ 8.0）"
v=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" -e "SELECT VERSION();" 2>/dev/null | grep -oE "[0-9]+\.[0-9]+\.[0-9]+" | head -1)
info "MySQL 版本: $v"
if [[ "$v" < "8.0.0" ]]; then
  err "MySQL 8.0+ required (current: $v)"
  exit 1
fi
ok "MySQL 8.0+ confirmed"

# 跑 schema
SCHEMA_FILE="$PROJECT_ROOT/docs/phase-0/db-schema.sql"
if [ ! -f "$SCHEMA_FILE" ]; then
  err "Schema 文件不存在: $SCHEMA_FILE"
  exit 1
fi
info "跑 schema: $SCHEMA_FILE"
docker exec -i "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SCHEMA_FILE" 2>&1 | tee -a "$LOG_FILE"
ok "schema 执行完成"

# 验证 7 张表
info "验证 7 张表"
EXPECTED_TABLES=("asset_raw" "asset_snapshot" "fund_category_map" "chat_history" "user_config" "prompt_versions" "operation_log")
for table in "${EXPECTED_TABLES[@]}"; do
  count=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" -e "USE $DB_NAME; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$table';" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  if [ "$count" -eq 1 ]; then
    ok "  ✓ $table"
  else
    err "  ✗ $table 不存在"
    exit 1
  fi
done

# 验证 prompt_versions 种子
section "验证 prompt_versions 种子数据"
seed_count=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" -e "USE $DB_NAME; SELECT COUNT(*) FROM prompt_versions;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
if [ "$seed_count" -eq 3 ]; then
  ok "prompt_versions 种子: $seed_count 行"
else
  warn "prompt_versions 种子数: $seed_count（期望 3）"
fi

ok "01-up-mysql 完成：Docker 容器 ${CONTAINER_NAME} ready，7 张表 + 3 种子"
info "下一步：bash scripts/1a7/02-up-backend.sh"
