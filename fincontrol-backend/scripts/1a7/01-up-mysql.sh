#!/usr/bin/env bash
# 01-up-mysql.sh — 1a.7 启动 MySQL 8.0
#
# 优先级：
#   1. 如有 MYSQL_HOST 环境变量（自定）→ 跳过本脚本，提示后续步骤用
#      $MYSQL_HOST / $MYSQL_PORT / $MYSQL_USER / $MYSQL_PASS
#   2. MYSQL_PORT（默认 3306）被占 → 直接用本地 MySQL
#   3. MYSQL_PORT 空闲 → Docker 启 mysql:8.0 容器（容器内 3306 映射到主机 $MYSQL_PORT）
#
# 场景示例：
#   - 本地 mysqld 占用 3306，但要跑 Docker MySQL：
#       export MYSQL_PORT=3307    # 容器映到主机 3307
#       bash scripts/1a7/01-up-mysql.sh
#   - 恢复本地 mysqld 模式：unset MYSQL_PORT（默认 3306）
#   - 自定义 user/pass：
#       export MYSQL_USER=root MYSQL_PASS=yourpass
#   - 跳过本地 fallback（强制 Docker 路径）：
#       export MYSQL_PORT=3307   # 用 3307（假设空闲）→ Docker

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

DB_NAME="${MYSQL_DATABASE:-fincontrol}"
DB_USER="${MYSQL_USER:-root}"
DB_PASS="${MYSQL_PASS:-root}"
DB_PORT="${MYSQL_PORT:-3306}"
CONTAINER_NAME="fincontrol-mysql"

section "01-up-mysql: 启动 MySQL 8.0（自动选 Docker / 本地，目标端口 $DB_PORT）"

# 1. 优先用环境变量指定的外部 MySQL
if [ -n "$MYSQL_HOST" ]; then
  info "环境变量 MYSQL_HOST=$MYSQL_HOST 已设，跳过启动"
  if [ "$MYSQL_HOST" = "127.0.0.1" ] || [ "$MYSQL_HOST" = "localhost" ]; then
    info "用本地 MySQL（端口 $DB_PORT）"
  else
    info "用远程 MySQL $MYSQL_HOST:$DB_PORT"
  fi
  ok "01-up-mysql 跳过（外部 MySQL）"
  exit 0
fi

# 2. 优先看 Docker 容器 fincontrol-mysql 是否已在跑（跨 MYSQL_PORT 都能复用）
require_cmd docker 2>/dev/null && DOCKER_OK=1 || DOCKER_OK=0
if [ "$DOCKER_OK" = "1" ]; then
  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    info "Docker 容器 ${CONTAINER_NAME} 已在跑 → 复用"
    # 验证可 ping
    if ! docker exec "$CONTAINER_NAME" mysqladmin ping -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
      err "Docker 容器存在但 mysqladmin ping 失败"
      exit 1
    fi
    # 验证 7 张表
    table_count=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';" 2>/dev/null | tr -d '[:space:]')
    if [ "$table_count" != "7" ]; then
      err "Docker 容器 $DB_NAME 库表数=$table_count（期望 7），需重跑 schema"
      info "手动跑 schema: docker exec -i $CONTAINER_NAME mysql -uroot -proot $DB_NAME < docs/phase-0/db-schema.sql"
      exit 1
    fi
    ok "Docker 容器 ${CONTAINER_NAME} ready：$table_count 张表"
    # 验证 prompt_versions 种子
    seed_count=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" -N -e "SELECT COUNT(*) FROM $DB_NAME.prompt_versions;" 2>/dev/null | tr -d '[:space:]')
    ok "prompt_versions 种子: $seed_count 行"
    ok "01-up-mysql 完成：Docker 复用模式（主机端口 $DB_PORT）"
    exit 0
  fi
  # 容器存在但停止 → 启动它
  if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    warn "Docker 容器 ${CONTAINER_NAME} 存在但停止 → 启动"
    docker start "$CONTAINER_NAME" >> "$LOG_FILE" 2>&1
    sleep 3
    # 继续走下方启动逻辑（重启后跳到下方的 ready 等待）
  fi
fi

# 3. 端口 $DB_PORT 已被占？—— 优先用本地 MySQL
if netstat -an 2>/dev/null | grep -q ":${DB_PORT}.*LISTENING" || ss -tln 2>/dev/null | grep -q ":${DB_PORT} "; then
  warn "端口 $DB_PORT 已被占 → 用本地 MySQL（已含 fincontrol 库 + 7 张表 + 3 种子）"
  for i in $(seq 1 30); do
    if mysqladmin ping -h127.0.0.1 -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
      ok "本地 MySQL ready (waited ${i}s)"
      break
    fi
    sleep 1
  done
  if ! mysqladmin ping -h127.0.0.1 -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" --silent 2>/dev/null; then
    err "本地 MySQL 在 30s 内未 ready"
    exit 1
  fi
  # 验证 7 张表 + 3 种子
  table_count=$(mysql -h127.0.0.1 -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW TABLES;" 2>/dev/null | grep -v "^Tables_in" | grep -cE "^\S+$")
  if [ "$table_count" -ne 7 ]; then
    err "本地 MySQL $DB_NAME 库表数=$table_count（期望 7），需先跑 db-schema.sql"
    err "  mysql -uroot -proot $DB_NAME < docs/phase-0/db-schema.sql"
    exit 1
  fi
  ok "本地 MySQL $DB_NAME 库就绪：$table_count 张表"
  # 验证 prompt_versions 种子
  seed_count=$(mysql -h127.0.0.1 -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SELECT COUNT(*) FROM prompt_versions;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  ok "prompt_versions 种子: $seed_count 行"
  ok "01-up-mysql 完成：本地 MySQL 模式"
  exit 0
fi

# 4. 端口空闲 → Docker MySQL
if [ "$DOCKER_OK" = "0" ]; then
  err "docker 命令不可用，且端口 $DB_PORT 空闲，无法启动 MySQL"
  err "请安装 Docker Desktop 或预先启动本地 MySQL"
  exit 1
fi
#（上方 #2 启动了已存在的容器，这里只处理全新创建）
if ! docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  info "拉取 mysql:8.0 镜像（若本地无）"
  docker pull mysql:8.0 2>&1 | tail -3 | tee -a "$LOG_FILE"

  info "创建 + 启动容器 ${CONTAINER_NAME}（主机端口 $DB_PORT → 容器端口 3306）"
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

# 等 MySQL ready（容器刚启动约 5-15s）
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

# 版本检查（≥ 8.0，重试 3 次以兼容刚启动情况）
info "验证 MySQL 版本（需 ≥ 8.0，最多重试 3 次）"
v=""
for i in 1 2 3; do
  v=$(docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASS" -N -e "SELECT VERSION();" 2>/dev/null | tr -d '[:space:]')
  if [ -n "$v" ]; then break; fi
  warn "  第 $i 次未获版本，等 2s 重试"
  sleep 2
done
info "MySQL 版本: $v"
if [[ -z "$v" || "$v" < "8.0.0" ]]; then
  err "MySQL 8.0+ required (current: '$v')"
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

ok "01-up-mysql 完成：Docker 容器 ${CONTAINER_NAME} ready（主机端口 $DB_PORT），7 张表 + 3 种子"
info "下一步：bash scripts/1a7/02-up-backend.sh（注意：DB_PORT=$DB_PORT 应通过 -DDB_PORT=$DB_PORT 传给后端）"