#!/bin/bash
# ============================================
# Phase 1a.8 end-to-end smoke script (Linux/macOS)
# Goal: run vision 4/4 + chat 5/5 + verify AiRouter fallback chain
# ============================================
# Usage:
#   1) Set real keys in application-local.yml (minimax + Doubao + optionally DeepSeek)
#   2) Start backend: mvn spring-boot:run
#   3) Run: bash scripts/1a8/03-e2e-smoke.sh
#
# Exit codes: 0 = all PASS, 1 = some FAIL (script continues, reports tally)
# ============================================

set -u
BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_ID="${USER_ID:-1}"

PASS=0
FAIL=0

echo "============================================"
echo "Phase 1a.8 end-to-end smoke"
echo "base_url=${BASE_URL}"
echo "============================================"

# ---------- Step 1: 启动检查 ----------
echo "[Step 1] startup check ..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health")
if [ "${HEALTH}" = "200" ]; then
    echo "[PASS] actuator/health UP"
    PASS=$((PASS+1))
else
    echo "[FAIL] actuator/health not available (HTTP ${HEALTH}); start the backend first"
    FAIL=$((FAIL+1))
    exit_report
fi

# ---------- Step 2: upload 4 测试图 ----------
echo "[Step 2] upload 4 test screenshots ..."
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BACKEND_DIR="${SCRIPT_DIR}/../.."
SCREENSHOT_DIR="${BACKEND_DIR}/uploads/screenshots"

if [ ! -d "${SCREENSHOT_DIR}" ]; then
    echo "[FAIL] missing ${SCREENSHOT_DIR}; place 4 test screenshots there first"
    FAIL=$((FAIL+1))
    exit_report
fi

if command -v jq &> /dev/null; then
    IDX=0
    for f in "${SCREENSHOT_DIR}"/*.jpg "${SCREENSHOT_DIR}"/*.png; do
        [ -e "$f" ] || continue
        IDX=$((IDX+1))
        if [ $IDX -gt 4 ]; then break; fi
        RESP=$(curl -s -X POST -H "X-User-Id: ${USER_ID}" -F "file=@${f}" "${BASE_URL}/api/screenshot/upload")
        FID=$(echo "${RESP}" | jq -r '.data.fileId')
        CONV="conv-${FID}"
        echo "  uploaded ${f} → fileId=${FID}"
        if [ "${FID}" = "null" ] || [ -z "${FID}" ]; then
            echo "[FAIL] upload ${f} returned no fileId (response=${RESP})"
            FAIL=$((FAIL+1))
            continue
        fi
        # ---------- Step 3: parse ----------
        PARSE=$(curl -s -X POST -H "X-User-Id: ${USER_ID}" -H "Content-Type: application/json" \
            -d "{\"fileId\":\"${FID}\",\"userId\":${USER_ID}}" \
            "${BASE_URL}/api/screenshot/parse")
        CODE=$(echo "${PARSE}" | jq -r '.code')
        if [ "${CODE}" = "0" ]; then
            echo "[PASS] parse ${FID} (code=0)"
            PASS=$((PASS+1))
        else
            echo "[FAIL] parse ${FID} (code=${CODE} response=${PARSE})"
            FAIL=$((FAIL+1))
        fi
    done
else
    echo "[WARN] jq not found; upload+parse steps skipped — install jq for full smoke"
fi

# ---------- Step 4: chat 5 个用例 ----------
echo "[Step 4] chat 5 用例 ..."
if command -v jq &> /dev/null; then
    CHAT_MSGS=("本月应该补仓多少" "今天天气怎么样" "海外权益类占比偏高怎么办" "早上好" "deepseek 是什么")
    for msg in "${CHAT_MSGS[@]}"; do
        RESP=$(curl -s -X POST -H "X-User-Id: ${USER_ID}" -H "Content-Type: application/json" \
            -d "{\"message\":\"${msg}\",\"userId\":${USER_ID}}" \
            "${BASE_URL}/api/chat/send")
        CODE=$(echo "${RESP}" | jq -r '.code')
        if [ "${CODE}" = "0" ]; then
            echo "[PASS] chat '${msg}' (code=0)"
            PASS=$((PASS+1))
        else
            echo "[FAIL] chat '${msg}' (code=${CODE})"
            FAIL=$((FAIL+1))
        fi
    done
fi

# ---------- Step 5: 验证 chat_history 监控字段 ----------
echo "[Step 5] chat_history 监控字段 ..."
echo "    Run:"
echo "      SELECT used_provider, fallback_triggered, COUNT(*) AS cnt"
echo "      FROM chat_history WHERE conversation_type='screenshot_parse' AND role='assistant'"
echo "      GROUP BY used_provider, fallback_triggered;"

exit_report() {
    echo ""
    echo "============================================"
    echo "1a.8 smoke 总计：${PASS} PASS / ${FAIL} FAIL"
    echo "============================================"
    echo ""
    if [ "${FAIL}" -eq 0 ]; then exit 0; else exit 1; fi
}

exit_report
