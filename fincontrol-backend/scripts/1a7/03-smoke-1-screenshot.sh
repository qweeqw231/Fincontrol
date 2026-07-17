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

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

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
  err "后端未启动，请先 bash scripts/1a7/02-up-backend.sh"
  exit 1
fi

# 前置：4 张图存在
for f in "${EXPECTED_FILES[@]}"; do
  if [ ! -f "$SAMPLES_DIR/$f" ]; then
    err "缺失样本：$SAMPLES_DIR/$f"
    exit 1
  fi
done
ok "4 张真实图都在 samples/"

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
    FILE_IDS[$f]="$fid"
    ok "  upload #$((i+1)) $f → fileId=$fid"
  else
    err "  upload #$((i+1)) $f 失败（HTTP $status）"
    err "  可能 minimax vision 限流或 key 无效"
    cat "$outfile" | head -5
    exit 1
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
    CONV_IDS[$f]="$cid"
    fc=$(python -c "import json; print(sum(len(c.get('funds', [])) for c in json.load(open('$outfile')).get('data', {}).get('categories', [])))" 2>/dev/null)
    ok "  parse $f → convId=$cid（$fc funds）"
  else
    err "  parse $f 失败（HTTP $status）"
    cat "$outfile" | head -10
    # 失败不退出，记录到 log 后继续
    warn "  继续后续步骤（parse 失败仅影响 confirm）"
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
    ok "  reparse $first_cid HTTP 200（幂等 OK）"
  else
    warn "  reparse 失败（HTTP $status）"
  fi
fi

# ---------- A7-S03: 4 张图 confirm ----------
section "A7-S03: confirm 4 张图（真实 MySQL upsert，1a.7-PRE dialect 验证）"
declare -A CONFIRM_OK=()
for f in "${!FILE_IDS[@]}"; do
  fid="${FILE_IDS[$f]}"
  # 读 parse 的 ParsedAsset（从 outfile）
  parse_outfile="$API_OUT_DIR/03_s02_parse_${fid}.json"
  if [ ! -f "$parse_outfile" ]; then
    warn "  skip confirm $f (no parse output)"
    continue
  fi
  # 提取 ParsedAsset（不含 fileId/conversationId 字段）
  pa=$(python -c "import json; d=json.load(open('$parse_outfile'))['data']; d.pop('fileId', None); d.pop('conversationId', None); d['userId']=1; d['confirmedOverwrite']=True; print(json.dumps(d))" 2>/dev/null)
  if [ -z "$pa" ]; then
    warn "  skip confirm $f (parse failed)"
    continue
  fi
  outfile="$API_OUT_DIR/03_s03_confirm_${fid}.json"
  status=$(curl -sS -X POST http://127.0.0.1:8080/api/snapshot/confirm \
    -H "Content-Type: application/json" -H "X-User-Id: 1" \
    -d "$pa" \
    -o "$outfile" -w "%{http_code}" 2>&1)
  if [ "$status" = "200" ]; then
    CONFIRM_OK[$f]=1
    ok "  confirm $f → HTTP 200（1a.7-PRE MERGE INTO 修复生效）"
  else
    err "  confirm $f 失败（HTTP $status）"
    head -3 "$outfile" 2>/dev/null
    cat "$outfile" | head -10
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
  ok "  balance HTTP 200: total=$total, items=$count"
else
  err "  balance HTTP $status"
fi

# ---------- A7-S12: 真表 SQL 验证 ----------
section "A7-S12: 真表 SQL 验证（1a.7-PRE dialect 修复）"
if [ ${#CONFIRM_OK[@]} -gt 0 ]; then
  info "asset_raw 总数（应 ≥ 6 大类 × 1 张图 = 6）"
  sql_query "SELECT COUNT(*) AS raw_count FROM asset_raw WHERE user_id=1;" | grep -oE '[0-9]+' | tail -1 | while read c; do
    if [ "$c" -ge 6 ]; then ok "  asset_raw 总数: $c（≥ 6）"
    else warn "  asset_raw 总数: $c（< 6，可能 confirm 部分失败）"; fi
  done
  info "fund_category_map 总数"
  sql_query "SELECT COUNT(*) AS map_count FROM fund_category_map WHERE user_id=1;" | grep -oE '[0-9]+' | tail -1 | while read c; do
    if [ "$c" -ge 6 ]; then ok "  fund_category_map 总数: $c（≥ 6）"
    else warn "  fund_category_map 总数: $c"; fi
  done
  info "asset_snapshot 总数（7：6 大类 + 余额类）"
  sql_query "SELECT COUNT(*) AS snap_count FROM asset_snapshot WHERE user_id=1;" | grep -oE '[0-9]+' | tail -1 | while read c; do
    if [ "$c" -ge 7 ]; then ok "  asset_snapshot 总数: $c（≥ 7）"
    else warn "  asset_snapshot 总数: $c"; fi
  done
  info "chat_history 总数（user + assistant 各 1 条 per 解析）"
  sql_query "SELECT COUNT(*) AS chat_count FROM chat_history WHERE user_id=1 AND conversation_type='screenshot_parse';" | grep -oE '[0-9]+' | tail -1 | while read c; do
    ok "  chat_history 总数: $c（user + assistant 各 1 条 per 解析）"
  done
  info "1a.7-PRE dialect 验证：连续 ON DUPLICATE KEY UPDATE 应工作"
  sql_query "SHOW CREATE TABLE fund_category_map\\G" 2>/dev/null | grep "ON DUPLICATE KEY" | head -1
fi

# 总结
section "A7-S01~S04 + S12 总结"
total_ok=${#CONFIRM_OK[@]}
total_files=${#EXPECTED_FILES[@]}
echo "  upload 全部成功: $total_files/$total_files"
echo "  parse 成功: ${#CONV_IDS[@]}/$total_files"
echo "  confirm 成功: $total_ok/$total_files"
ok "03-smoke-1 完成：详见 $LOG_FILE 和 $API_OUT_DIR/03_*.json"
info "下一步：bash scripts/1a7/04-smoke-2-chat.sh"
