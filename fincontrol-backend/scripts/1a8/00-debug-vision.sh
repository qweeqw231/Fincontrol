#!/usr/bin/env bash
# 1a.8 Step 0: 调查 "1 fund per image" 之谜
# 直接调真实 minimax M3，看 4 张样本图的原始 JSON

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LOG_DIR="$BACKEND_ROOT/docs/test-records/automated-smoke/1a8"
mkdir -p "$LOG_DIR"

echo "======================================"
echo " 1a.8 Step 0: minimax M3 原始响应调查"
echo "======================================"
echo

# 健康检查
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  echo "[ERROR] 后端未启动"
  exit 1
fi

# 截图列表
SAMPLES=(
  "phase1a2-alipay-fund-list-20260715-2355-1.jpg"
  "phase1a2-alipay-fund-list-20260715-2355-2.jpg"
  "phase1a2-alipay-fund-list-20260715-2355-3.jpg"
  "phase1a2-alipay-fund-list-20260715-2356-1.jpg"
)

for F in "${SAMPLES[@]}"; do
  echo
  echo "---------- $F ----------"

  # Upload
  upload_resp=$(curl -sS -X POST http://127.0.0.1:8080/api/screenshot/upload \
    -H "X-User-Id: 1" \
    -F "file=@$BACKEND_ROOT/uploads/samples/$F")

  fileId=$(echo "$upload_resp" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('data',{}).get('fileId',''))" 2>/dev/null)
  echo "fileId=$fileId"

  if [ -z "$fileId" ]; then
    echo "Upload failed: $upload_resp"
    continue
  fi

  # Parse - 拿原始响应
  parse_resp=$(curl -sS -X POST http://127.0.0.1:8080/api/screenshot/parse \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 1" \
    -d "{\"fileId\":\"$fileId\",\"userId\":1}")

  # 保存原始响应
  echo "$parse_resp" > "$LOG_DIR/00-raw-${F%.jpg}.json"

  # 摘要
  size=$(echo -n "$parse_resp" | wc -c)
  echo "Raw response size: $size bytes"
  # 顶层 keys
  echo "Top-level keys:"
  echo "$parse_resp" | python -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print('  ' + ', '.join(d.keys()))
    if 'data' in d and isinstance(d['data'], dict):
        print('  data.*: ' + ', '.join(d['data'].keys()))
except Exception as e:
    print('  parse error: ' + str(e))
" 2>/dev/null

  # 统计 fund 数量（从 chat_history 表）
  echo "Chat history funds (assistant content):"
  convId="conv-$fileId"
  docker exec fincontrol-mysql mysql -uroot -proot fincontrol -N -e "SELECT LENGTH(content), LEFT(content, 300) FROM chat_history WHERE conversation_id='$convId' AND role='assistant' LIMIT 1;" 2>/dev/null
done

echo
echo "======================================"
echo " Step 0 调查完成"
echo " 输出: $LOG_DIR/00-raw-*.json (4 个文件)"
echo "======================================"