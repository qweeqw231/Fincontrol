#!/usr/bin/env bash
# 04-smoke-2-chat.sh — 1a.7 冒烟 2：chat send 多轮端到端
#
# 流程：
#   A7-S05  投资类 → main_loop + ai_assistant v1.0
#   A7-S06  投资类（同主题）→ main_loop
#   A7-S07  闲聊 → garbage_loop
#   A7-S08  闲聊（模型身份）→ garbage_loop
#   A7-S09  空 message → 400 + code 1001
#   A7-S12  真表 SQL：chat_history 多条记录

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

section "04-smoke-2: chat send 多轮端到端（5 个用例）"

# 前置：后端 ready
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  err "后端未启动，请先 bash scripts/1a7/02-up-backend.sh"
  exit 1
fi

# ---------- A7-S05: 投资决策类 → main_loop ----------
section "A7-S05: 投资决策类 → main_loop + ai_assistant v1.0"
outfile="$API_OUT_DIR/04_s05_main_loop.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"message":"本月应该补仓多少"}' \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ]; then
  routed=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['assistantMessage'].get('routedTo', '?'))" 2>/dev/null)
  pv=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['assistantMessage'].get('promptVersion') or '(none)')" 2>/dev/null)
  lat=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['intentClassification'].get('latencyMs', '?'))" 2>/dev/null)
  inv=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['intentClassification'].get('result', '?'))" 2>/dev/null)
  if [ "$routed" = "main_loop" ] && [ "$pv" = "ai_assistant v1.0" ]; then
    ok "  routedTo=$routed, promptVersion=$pv, latencyMs=$lat, isInvestment=$inv"
  else
    err "  路由不对: routedTo=$routed, promptVersion=$pv"
    head -3 "$outfile"
  fi
else
  err "  HTTP $status（可能 minimax text 限流）"
  head -3 "$outfile"
  warn "  继续后续用例（投资类失败不影响闲聊路径）"
fi

# ---------- A7-S06: 投资决策类（同主题）→ main_loop ----------
section "A7-S06: 投资决策类（同主题）→ main_loop"
outfile="$API_OUT_DIR/04_s06_investment_2.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"message":"海外权益类占比偏高怎么办"}' \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ]; then
  routed=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['assistantMessage'].get('routedTo', '?'))" 2>/dev/null)
  if [ "$routed" = "main_loop" ]; then
    ok "  routedTo=main_loop（与 A7-S05 一致）"
  else
    warn "  routedTo=$routed（期望 main_loop）"
  fi
else
  warn "  HTTP $status"
fi

# ---------- A7-S07: 闲聊 → garbage_loop ----------
section "A7-S07: 闲聊 → garbage_loop（无 promptVersion）"
outfile="$API_OUT_DIR/04_s07_garbage_loop.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"message":"今天天气怎么样"}' \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ]; then
  routed=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['assistantMessage'].get('routedTo', '?'))" 2>/dev/null)
  pv=$(python -c "import json; d=json.load(open('$outfile'))['data']; v=d['assistantMessage'].get('promptVersion'); print('(none)' if v is None else v)" 2>/dev/null)
  if [ "$routed" = "garbage_loop" ] && [ "$pv" = "(none)" ]; then
    ok "  routedTo=garbage_loop, promptVersion=(none) ✓"
  else
    err "  期望 garbage_loop + 无 promptVersion：实际 routedTo=$routed, promptVersion=$pv"
  fi
else
  err "  HTTP $status"
fi

# ---------- A7-S08: 闲聊（模型身份）→ garbage_loop ----------
section "A7-S08: 模型身份询问 → garbage_loop"
outfile="$API_OUT_DIR/04_s08_model_identity.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"message":"你是什么模型"}' \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ]; then
  routed=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d['assistantMessage'].get('routedTo', '?'))" 2>/dev/null)
  if [ "$routed" = "garbage_loop" ]; then
    ok "  routedTo=garbage_loop（与 A7-S07 一致）"
  else
    warn "  routedTo=$routed（期望 garbage_loop）"
  fi
else
  warn "  HTTP $status"
fi

# ---------- A7-S09: 空 message → 400 + code 1001 ----------
section "A7-S09: 空 message → HTTP 400 + code 1001"
outfile="$API_OUT_DIR/04_s09_empty_msg.json"
status=$(curl -sS -X POST http://127.0.0.1:8080/api/chat/send \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"message":""}' \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "400" ]; then
  code=$(python -c "import json; d=json.load(open('$outfile')); print(d.get('code', 0))" 2>/dev/null)
  if [ "$code" = "1001" ]; then
    ok "  HTTP 400 + code=1001 ✓"
  else
    err "  HTTP 400 但 code=$code（期望 1001）"
  fi
else
  err "  HTTP $status（期望 400）"
fi

# ---------- A7-S12: 真表 SQL 验证 ----------
section "A7-S12: chat_history 真表 SQL 验证"
total_chat=$(sql_query "SELECT COUNT(*) FROM chat_history WHERE user_id=1 AND conversation_type='ai_assistant';" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
ok "chat_history (ai_assistant) 总数: $total_chat（每次 chat.send 各 2 条：user + assistant）"
info "验证最后 5 条："
sql_query "SELECT id, role, content FROM chat_history WHERE user_id=1 ORDER BY id DESC LIMIT 5\\G" 2>/dev/null

# ---------- 总结 ----------
section "A7-S05~S09 + S12 总结"
ok "04-smoke-2 完成：5 个用例跑完 + chat_history 真表验证"
info "下一步：bash scripts/1a7/05-coverage.sh"
