#!/usr/bin/env bash
# lib-common.sh — 1a.7 共享 helper
#
# 提供：
#   - log/info/warn/err   — 带时间戳的日志
#   - mask                — 脱敏 print（key 永远显示 ***）
#   - curl_json           — 调 API，自动保存响应到 logs/
#   - sql_query           — 调 mysql -e 执行 SQL
#   - wait_for_url        — 等 URL 健康
#   - require_cmd         — 校验工具存在
#   - section             — 输出分组标题
#
# 严禁 echo / print 任何包含 API key 的明文。

# ---------- 路径常量 ----------
SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPTS_DIR/../../.." && pwd)"
BACKEND_ROOT="$(cd "$SCRIPTS_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/docs/test-records/manual-tests"
API_OUT_DIR="$LOG_DIR/api-test-output"
LOG_FILE="$LOG_DIR/2026-07-17_phase1a7-smoke.log"
BACKEND_LOG="$BACKEND_ROOT/backend.log"
mkdir -p "$LOG_DIR" "$API_OUT_DIR"

# ---------- 颜色 ----------
if [ -t 1 ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; BLUE=''; NC=''
fi

# ---------- 日志 ----------
_ts() { date '+%Y-%m-%dT%H:%M:%S'; }
log()  { echo -e "[$(_ts)] $*" | tee -a "$LOG_FILE"; }
info() { echo -e "${BLUE}[$(_ts)] INFO${NC}  $*" | tee -a "$LOG_FILE"; }
warn() { echo -e "${YELLOW}[$(_ts)] WARN${NC}  $*" | tee -a "$LOG_FILE"; }
err() { echo -e "${RED}[$(_ts)] ERROR${NC} $*" | tee -a "$LOG_FILE"; }
ok()  { echo -e "${GREEN}[$(_ts)] OK${NC}    $*" | tee -a "$LOG_FILE"; }
section() { echo -e "\n${BLUE}=== $* ===${NC}" | tee -a "$LOG_FILE"; }

# ---------- 脱敏 ----------
# 把任何 echo / printf 调用此函数代替 echo，防止 key 泄漏
# 用法：mask "key value: $KEY";  → 输出 "key value: ***"
mask() {
  # 简单实现：把所有 key 替换为 ***
  local s="$*"
  # 替换 MINIMAX key 格式
  s=$(echo "$s" | sed -E 's/sk-api-[A-Za-z0-9_-]{20,}/sk-api-***/g')
  echo "$s"
}

# ---------- 工具检查 ----------
require_cmd() {
  for cmd in "$@"; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      err "缺失依赖: $cmd"
      err "请安装后重试"
      return 1
    fi
  done
}

# ---------- HTTP 客户端 ----------
# 调 API；自动保存响应到 logs/
# 用法：curl_json POST /api/foo -H 'X-User-Id: 1' -d '{"k":"v"}'
curl_json() {
  local method="$1"; shift
  local path="$1"; shift
  local outfile="$API_OUT_DIR/$(echo "$path" | tr '/?' '__').json"
  local extra=("$@")
  local url="http://127.0.0.1:8080${path}"
  log "  → ${method} ${path} ${extra[*]:-(no extra)}"
  # shellcheck disable=SC2086
  curl -sS -X "$method" "$url" \
    -H "Content-Type: application/json" \
    "${extra[@]}" \
    -o "$outfile" -w '\n__HTTP_STATUS__%{http_code}\n' 2>&1 \
    | tee -a "$LOG_FILE"
  local status=$(tail -n1 "$outfile" 2>/dev/null; grep -oP '(?<=__HTTP_STATUS__)\d+' <<< "$(cat "$outfile")" 2>/dev/null || echo 0)
  # 简单 status 提取（用 python 兜底）
  if command -v python >/dev/null 2>&1; then
    status=$(python -c "import json; d=json.load(open('$outfile')); print(d.get('code', 0))" 2>/dev/null || echo 0)
  fi
  echo "$outfile:$status"
}

# ---------- MySQL ----------
# 用 docker exec 调 mysql；本地 MySQL 也可改用 $MYSQL_HOST
MYSQL_CONTAINER="${MYSQL_CONTAINER:-fincontrol-mysql}"
sql_query() {
  docker exec -i "$MYSQL_CONTAINER" mysql -uroot -proot fincontrol -e "$1" 2>&1 | tee -a "$LOG_FILE"
}

# ---------- 健康检查 ----------
# 等 URL 返 200
wait_for_url() {
  local url="$1"
  local timeout="${2:-60}"
  local start=$(date +%s)
  while true; do
    if curl -fsS -o /dev/null -w "%{http_code}" "$url" 2>/dev/null | grep -q "200"; then
      ok "ready: $url"; return 0
    fi
    local now=$(date +%s)
    if [ $((now - start)) -gt "$timeout" ]; then
      err "timeout waiting: $url"
      return 1
    fi
    sleep 1
  done
}

# ---------- 文件大小 ----------
human_size() {
  local bytes="$1"
  if [ "$bytes" -lt 1024 ]; then echo "${bytes}B"
  elif [ "$bytes" -lt 1048576 ]; then echo "$((bytes/1024))KB"
  else echo "$((bytes/1048576))MB"
  fi
}
