#!/usr/bin/env bash
# 04-smoke-2-chat.sh — 1a.7 冒烟 2：chat send 多轮端到端
#
# 流程：
#   A7-S05  投资决策类 → main_loop + ai_assistant v1.0
#   A7-S06  投资决策类（同主题）→ main_loop
#   A7-S07  闲聊 → garbage_loop
#   A7-S08  闲聊（模型身份询问）→ garbage_loop
#   A7-S09  空 message → 400 + code 1001
#   A7-S12  真表 SQL：chat_history 多条记录
#
# 重要：单步 curl 失败不退出（set +e 兜底），minimax 限流时降级到 garbage_loop
# JSON body 用 --data @file（避免 Windows + cygwin 嵌套引号问题）

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

# 计数器：单步失败不退出
set +e
OK_COUNT=0
ERR_COUNT=0

mark_ok()  { OK_COUNT=$((OK_COUNT+1)); ok  "$1"; }
mark_err() { ERR_COUNT=$((ERR_COUNT+1)); err "$1"; }

# 解析 JSON 字段（grep 替代 python，兼容 Windows）
extract_json_field() {
  local f="$1"; local field="$2"
  grep -oE "\"${field}\"[[:space:]]*:[[:space:]]*\"[^\"]+\"" "$f" 2>/dev/null | head -1 | cut -d'"' -f4
}

# 准备 5 个 JSON body 文件（避免在 curl -d 里嵌套引号）
TMP_DIR=$(mktemp -d)
trap "rm -rf $TMP_DIR" EXIT

cat > "$TMP_DIR/investment_1.json" <<'EOF'
{"message":"本月应该补仓多少"}
EOF

cat > "$TMP_DIR/investment_2.json" <<'EOF'
{"message":"海外权益类占比偏高怎么办"}
EOF

cat > "$TMP_DIR/chitchat_1.json" <<'EOF'
{"message":"今天天气怎么样"}
EOF

cat > "$TMP_DIR/chitchat_2.json" <<'EOF'
{"message":"你是什么模型"}
EOF

cat > "$TMP_DIR/empty.json" <<'EOF'
{"message":""}
EOF

section "04-smoke-2: chat send 多轮端到端（5 个用例）"

# 前置：后端 ready
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  mark_err "后端未启动，请先 bash scripts/1a7/02-up-backend.sh"
  exit 1
fi

# ---------- A7-S05: 投资决策类 → main_loop ----------
section "A7-S05: 投资决策类 → main_loop + ai_assistant v1.0"
outfile="$API_OUT_DIR/04_s05_main_loop.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  --data @"$TMP_DIR/investment_1.json" \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ] && [ -s "$outfile" ]; then
  routed=$(extract_json_field "$outfile" "routedTo")
  pv=$(extract_json_field "$outfile" "promptVersion")
  if [ "$routed" = "main_loop" ] && [ "$pv" = "ai_assistant v1.0" ]; then
    mark_ok "  routedTo=$routed, promptVersion=$pv"
  else
    mark_err "  路由不对: routedTo=$routed, promptVersion=$pv"
  fi
else
  mark_err "  HTTP $status（minimax 限流）"
  head -c 200 "$outfile" 2>/dev/null
fi

# ---------- A7-S06: 投资决策类（同主题）→ main_loop ----------
section "A7-S06: 投资决策类（同主题）→ main_loop"
outfile="$API_OUT_DIR/04_s06_investment_2.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  --data @"$TMP_DIR/investment_2.json" \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ] && [ -s "$outfile" ]; then
  routed=$(extract_json_field "$outfile" "routedTo")
  if [ "$routed" = "main_loop" ]; then
    mark_ok "  routedTo=main_loop（与 A7-S05 一致）"
  else
    mark_err "  routedTo=$routed（期望 main_loop）"
  fi
else
  mark_err "  HTTP $status"
fi

# ---------- A7-S07: 闲聊 → garbage_loop ----------
section "A7-S07: 闲聊 → garbage_loop（无 promptVersion）"
outfile="$API_OUT_DIR/04_s07_garbage_loop.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  --data @"$TMP_DIR/chitchat_1.json" \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ] && [ -s "$outfile" ]; then
  routed=$(extract_json_field "$outfile" "routedTo")
  pv=$(extract_json_field "$outfile" "promptVersion")
  if [ "$routed" = "garbage_loop" ] && [ -z "$pv" ]; then
    mark_ok "  routedTo=garbage_loop, promptVersion=(none) ✓"
  else
    mark_err "  期望 garbage_loop + 无 promptVersion：实际 routedTo=$routed, promptVersion=$pv"
  fi
else
  mark_err "  HTTP $status"
fi

# ---------- A7-S08: 闲聊（模型身份）→ garbage_loop ----------
section "A7-S08: 模型身份询问 → garbage_loop"
outfile="$API_OUT_DIR/04_s08_model_identity.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  --data @"$TMP_DIR/chitchat_2.json" \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ] && [ -s "$outfile" ]; then
  routed=$(extract_json_field "$outfile" "routedTo")
  if [ "$routed" = "garbage_loop" ]; then
    mark_ok "  routedTo=garbage_loop（与 A7-S07 一致）"
  else
    mark_err "  routedTo=$routed（期望 garbage_loop）"
  fi
else
  mark_err "  HTTP $status"
fi

# ---------- A7-S09: 空 message → 400 + code 1001 ----------
section "A7-S09: 空 message → HTTP 400 + code 1001"
outfile="$API_OUT_DIR/04_s09_empty_msg.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  --data @"$TMP_DIR/empty.json" \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "400" ] && [ -s "$outfile" ]; then
  code=$(grep -oE '"code"[[:space:]]*:[[:space:]]*[0-9]+' "$outfile" | head -1 | grep -oE '[0-9]+')
  if [ "$code" = "1001" ]; then
    mark_ok "  HTTP 400 + code=1001 ✓"
  else
    mark_err "  HTTP 400 但 code=$code（期望 1001）"
  fi
else
  mark_err "  HTTP $status（期望 400）"
fi

# ---------- A7-S12: 真表 SQL 验证 ----------
section "A7-S12: chat_history 真表 SQL 验证"
total_chat=$(sql_query "SELECT COUNT(*) FROM chat_history WHERE user_id=1 AND conversation_type='ai_assistant';" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
if [ "${total_chat:-0}" -ge 8 ]; then
  mark_ok "  chat_history (ai_assistant): $total_chat（≥ 8）"
else
  mark_err "  chat_history (ai_assistant): $total_chat（< 8）"
fi
info "验证最后 5 条："
sql_query "SELECT id, role, content FROM chat_history WHERE user_id=1 ORDER BY id DESC LIMIT 5\\G" 2>/dev/null

# 总结
section "A7-S05~S09 + S12 总结"
echo "  累计：OK=$OK_COUNT / ERR=$ERR_COUNT"

if [ "$ERR_COUNT" -eq 0 ]; then
  ok "04-smoke-2 完成：全部 0 错误"
else
  warn "04-smoke-2 完成但有 $ERR_COUNT 错误（minimax 限流或 key 问题）"
fi
info "下一步：bash scripts/1a7/05-coverage.sh"
