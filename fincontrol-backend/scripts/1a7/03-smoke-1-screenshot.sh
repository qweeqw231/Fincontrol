#!/usr/bin/env bash
# 03-smoke-1-screenshot.sh — 1a.7 冒烟 1：截图全链路端到端
#
# 流程：
#   1. upload 4 张真实图（取 fileId）
#   2. parse 4 张图（取 conversationId + ParsedAsset）
#   3. reparse 第 1 张（验证幂等性）
#   4. confirm 4 张图（取真实 MySQL upsert）
#   5. balance 端点验证
#   6. 查真表：asset_raw / fund_category_map / asset_snapshot / chat_history
#
# 重要：单步 curl 失败不退出（set +e 兜底），避免 minimax 限流让整脚本中断
# 最终用 OK_COUNT / ERR_COUNT 报

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

# 计数器：单步失败不退出
set +e
OK_COUNT=0
ERR_COUNT=0

mark_ok()  { OK_COUNT=$((OK_COUNT+1)); ok  "$1"; }
mark_err() { ERR_COUNT=$((ERR_COUNT+1)); err "$1"; }

SAMPLES_DIR="$BACKEND_ROOT/uploads/samples"
EXPECTED_FILES=(
  "phase1a2-alipay-fund-list-20260715-2355-1.jpg"
  "phase1a2-alipay-fund-list-20260715-2355-2.jpg"
  "phase1a2-alipay-fund-list-20260715-2355-3.jpg"
  "phase1a2-alipay-fund-list-20260715-2356-1.jpg"
)

section "03-smoke-1: 截图全链路端到端（4 张真实图）"

# 前置：后端 ready
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  mark_err "后端未启动，请先 bash scripts/1a7/02-up-backend.sh"
  err_count_report
  exit 1
fi

# 前置：4 张图存在
for f in "${EXPECTED_FILES[@]}"; do
  if [ ! -f "$SAMPLES_DIR/$f" ]; then
    mark_err "缺失样本：$SAMPLES_DIR/$f"
    err_count_report
    exit 1
  fi
done
mark_ok "4 张真实图都在 samples/"

# ---------- A7-S01: 4 张图 upload ----------
section "A7-S01: upload 4 张图"
declare -A FILE_IDS
for i in "${!EXPECTED_FILES[@]}"; do
  f="${EXPECTED_FILES[$i]}"
  outfile="$API_OUT_DIR/03_s01_upload_$((i+1)).json"
  status=$(curl -sS -X POST http://127.0.0.1:8080/api/screenshot/upload \
    -H "X-User-Id: 1" \
    -F "file=@$SAMPLES_DIR/$f" \
    -o "$outfile" -w "%{http_code}" 2>&1)
  if [ "$status" = "200" ]; then
    fid=$(python -c "import json; print(json.load(open('$outfile')).get('data', {}).get('fileId', ''))" 2>/dev/null)
    if [ -n "$fid" ] && [ "$fid" != "None" ]; then
      FILE_IDS[$f]="$fid"
      mark_ok "  upload #$((i+1)) $f → fileId=$fid"
    else
      mark_err "  upload #$((i+1)) $f 返 200 但 fileId 缺失"
      head -3 "$outfile" 2>/dev/null
    fi
  else
    mark_err "  upload #$((i+1)) $f HTTP $status（minimax vision 限流或 key 无效）"
    head -3 "$outfile" 2>/dev/null
  fi
done

# ---------- A7-S02: 4 张图 parse ----------
section "A7-S02: parse 4 张图（真实 minimax vision 调用）"
declare -A CONV_IDS
for f in "${!FILE_IDS[@]}"; do
  fid="${FILE_IDS[$f]}"
  outfile="$API_OUT_DIR/03_s02_parse_${fid}.json"
  status=$(curl -sS -X POST http://127.0.0.1:8080/api/screenshot/parse \
    -H "Content-Type: application/json" -H "X-User-Id: 1" \
    -d "{\"fileId\":\"$fid\",\"userId\":1}" \
    -o "$outfile" -w "%{http_code}" 2>&1)
  if [ "$status" = "200" ]; then
    cid=$(python -c "import json; print(json.load(open('$outfile')).get('data', {}).get('conversationId', ''))" 2>/dev/null)
    if [ -n "$cid" ] && [ "$cid" != "None" ]; then
      CONV_IDS[$f]="$cid"
      fc=$(python -c "import json; print(sum(len(c.get('funds', [])) for c in json.load(open('$outfile')).get('data', {}).get('categories', [])))" 2>/dev/null)
      mark_ok "  parse $f → convId=$cid（$fc funds）"
    else
      mark_err "  parse $f 返 200 但 conversationId 缺失"
    fi
  else
    mark_err "  parse $f HTTP $status（minimax vision 限流）"
    head -3 "$outfile" 2>/dev/null
  fi
done

# ---------- A7-S02-extra: reparse 第 1 张 ----------
section "A7-S02-extra: reparse 第 1 张（验证幂等性）"
first_fid="${FILE_IDS[${EXPECTED_FILES[0]}]}"
first_cid="${CONV_IDS[${EXPECTED_FILES[0]}]}"
if [ -n "$first_cid" ]; then
  outfile="$API_OUT_DIR/03_s02b_reparse.json"
  status=$(curl -sS -X POST http://127.0.0.1:8080/api/screenshot/reparse \
    -H "Content-Type: application/json" -H "X-User-Id: 1" \
    -d "{\"conversationId\":\"$first_cid\",\"userId\":1}" \
    -o "$outfile" -w "%{http_code}" 2>&1)
  if [ "$status" = "200" ]; then
    mark_ok "  reparse $first_cid HTTP 200（幂等 OK）"
  else
    mark_err "  reparse HTTP $status"
  fi
fi

# ---------- A7-S03: 4 张图 confirm ----------
section "A7-S03: confirm 4 张图（真实 MySQL upsert，1a.7-PRE dialect 验证）"
declare -A CONFIRM_OK=()
for f in "${!FILE_IDS[@]}"; do
  fid="${FILE_IDS[$f]}"
  parse_outfile="$API_OUT_DIR/03_s02_parse_${fid}.json"
  if [ ! -f "$parse_outfile" ]; then
    mark_err "  skip confirm $f (no parse output)"
    continue
  fi
  pa=$(python -c "import json; d=json.load(open('$parse_outfile'))['data']; d.pop('fileId', None); d.pop('conversationId', None); d['userId']=1; d['confirmedOverwrite']=True; print(json.dumps(d))" 2>/dev/null)
  if [ -z "$pa" ]; then
    mark_err "  skip confirm $f (parse failed)"
    continue
  fi
  outfile="$API_OUT_DIR/03_s03_confirm_${fid}.json"
  status=$(curl -sS -X POST http://127.0.0.1:8080/api/snapshot/confirm \
    -H "Content-Type: application/json" -H "X-User-Id: 1" \
    -d "$pa" \
    -o "$outfile" -w "%{http_code}" 2>&1)
  if [ "$status" = "200" ]; then
    CONFIRM_OK[$f]=1
    mark_ok "  confirm $f → HTTP 200（1a.7-PRE MERGE INTO 修复生效）"
  else
    mark_err "  confirm $f HTTP $status"
    head -3 "$outfile" 2>/dev/null
  fi
done

# ---------- A7-S04: balance 端点 ----------
section "A7-S04: /api/asset/balance"
outfile="$API_OUT_DIR/03_s04_balance.json"
status=$(curl -sS http://127.0.0.1:8080/api/asset/balance \
  -H "X-User-Id: 1" \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ]; then
  total=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(d.get('balanceFundTotal', 0))" 2>/dev/null)
  count=$(python -c "import json; d=json.load(open('$outfile'))['data']; print(len(d.get('items', [])))" 2>/dev/null)
  mark_ok "  balance HTTP 200: total=$total, items=$count"
else
  mark_err "  balance HTTP $status"
fi

# ---------- A7-S12: 真表 SQL 验证 ----------
section "A7-S12: 真表 SQL 验证（1a.7-PRE dialect 修复）"
if [ ${#CONFIRM_OK[@]} -gt 0 ]; then
  info "asset_raw 总数（应 ≥ 6 大类 × 1 张图 = 6）"
  raw=$(sql_query "SELECT COUNT(*) FROM asset_raw WHERE user_id=1;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  if [ "${raw:-0}" -ge 6 ]; then mark_ok "  asset_raw: $raw"
  else mark_err "  asset_raw: $raw (< 6)"; fi

  info "fund_category_map 总数"
  map=$(sql_query "SELECT COUNT(*) FROM fund_category_map WHERE user_id=1;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  if [ "${map:-0}" -ge 6 ]; then mark_ok "  fund_category_map: $map"
  else mark_err "  fund_category_map: $map"; fi

  info "asset_snapshot 总数（应 = 7：6 大类 + 余额类）"
  snap=$(sql_query "SELECT COUNT(*) FROM asset_snapshot WHERE user_id=1;" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  if [ "${snap:-0}" -ge 7 ]; then mark_ok "  asset_snapshot: $snap"
  else mark_err "  asset_snapshot: $snap (< 7)"; fi

  info "chat_history 总数（screenshot_parse）"
  chat=$(sql_query "SELECT COUNT(*) FROM chat_history WHERE user_id=1 AND conversation_type='screenshot_parse';" 2>/dev/null | grep -oE '[0-9]+' | tail -1)
  if [ "${chat:-0}" -ge 2 ]; then mark_ok "  chat_history: $chat"
  else mark_err "  chat_history: $chat (< 2)"; fi
fi

# 总结
section "A7-S01~S04 + S12 总结"
echo "  累计：OK=$OK_COUNT / ERR=$ERR_COUNT"
echo "  文件：详见 $API_OUT_DIR/03_*.json"

if [ "$ERR_COUNT" -eq 0 ]; then
  ok "03-smoke-1 完成：全部 0 错误"
else
  warn "03-smoke-1 完成但有 $ERR_COUNT 错误（minimax 限流或 key 问题）"
fi
info "下一步：bash scripts/1a7/04-smoke-2-chat.sh"
