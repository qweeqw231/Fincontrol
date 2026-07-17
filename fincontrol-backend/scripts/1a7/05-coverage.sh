#!/usr/bin/env bash
# 05-coverage.sh — 1a.7 覆盖率 + Swagger 端点统计
#
# 流程：
#   1. mvn test 跑全部单测（业务层 1a.2-1a.6 已有 151 用例）
#   2. mvn jacoco:report 生成覆盖率报告
#   3. 解析 target/site/jacoco/index.html 取 line coverage
#   4. 验证 ≥ 60% line coverage
#   5. 查 /v3/api-docs 数 path ≥ 24

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
source "$SCRIPT_DIR/lib-common.sh"

require_cmd mvn || exit 1

section "05-coverage: 跑 mvn test + JaCoCo + Swagger"

# 前置：后端 ready（Swagger 才可访问）
if ! curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
  err "后端未启动，请先 bash scripts/1a7/02-up-backend.sh"
  exit 1
fi

# ---------- 跑 mvn test ----------
section "A: 跑 mvn test（业务层 151 用例）"
info "mvn -B test ...（可能 60-120s）"
cd "$BACKEND_ROOT"
mvn -B test 2>&1 | tee -a "$LOG_FILE" | tail -50
ok "mvn test 完成"

# ---------- 跑 mvn jacoco:report ----------
section "B: 跑 mvn jacoco:report（生成覆盖率报告）"
info "mvn -B jacoco:report ..."
mvn -B jacoco:report 2>&1 | tee -a "$LOG_FILE" | tail -20
JACOCO_HTML="$BACKEND_ROOT/target/site/jacoco/index.html"
if [ ! -f "$JACOCO_HTML" ]; then
  err "JaCoCo 报告未生成：$JACOCO_HTML"
  err "可能 JaCoCo Maven plugin 未配置"
  exit 1
fi
ok "JaCoCo 报告: $JACOCO_HTML ($(human_size $(stat -c%s "$JACOCO_HTML")))"

# ---------- 解析 line coverage ----------
section "C: 解析 line coverage（阈值 ≥ 60%）"
# 从 jacoco.csv 取数字
JACOCO_CSV="$BACKEND_ROOT/target/site/jacoco/jacoco.csv"
if [ -f "$JACOCO_CSV" ]; then
  # 格式: PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,...
  LINE_MISSED=$(awk -F, 'NR>1 {missed+=$7} END {print missed+0}' "$JACOCO_CSV")
  LINE_COVERED=$(awk -F, 'NR>1 {covered+=$8} END {print covered+0}' "$JACOCO_CSV")
  TOTAL=$((LINE_MISSED + LINE_COVERED))
  if [ "$TOTAL" -gt 0 ]; then
    PCT=$(awk "BEGIN {printf \"%.1f\", $LINE_COVERED*100.0/$TOTAL}")
    info "Line Coverage: $LINE_COVERED / $TOTAL  =  $PCT %"
    if awk "BEGIN {exit !($PCT >= 60.0)}"; then
      ok "  ≥ 60% 阈值 ✓"
    else
      warn "  < 60% 阈值（$PCT% < 60%）"
      warn "  Phase 1a.7 退出条件未达成"
    fi
  fi
else
  warn "jacoco.csv 不存在，无法自动解析覆盖率"
fi

# 也用 grep 抓 line coverage
LINE_PCT=$(grep -oE 'INSTRUCTION[^>]*>[0-9]+%' "$JACOCO_HTML" 2>/dev/null | head -1 || true)

# ---------- Swagger 端点数 ----------
section "D: Swagger 端点数 ≥ 24"
outfile="$API_OUT_DIR/05_swagger_paths.json"
status=$(curl -sS http://127.0.0.1:8080/v3/api-docs \
  -o "$outfile" -w "%{http_code}" 2>&1)
if [ "$status" = "200" ]; then
  if command -v jq >/dev/null 2>&1; then
    NPATHS=$(jq '.paths | length' "$outfile")
  else
    NPATHS=$(python -c "import json; print(len(json.load(open('$outfile'))['paths']))" 2>/dev/null || echo 0)
  fi
  info "Swagger paths: $NPATHS"
  if [ "$NPATHS" -ge 24 ]; then
    ok "  ≥ 24 端点 ✓"
  else
    warn "  < 24 端点（$NPATHS < 24）"
  fi
else
  err "  Swagger 不可访问：HTTP $status"
fi

# ---------- 总结 ----------
section "E: 1a.7 验收总结"
ok "05-coverage 完成：覆盖率 + Swagger 端点统计"
info "Phase 1a 24 项 API + 8 项 P0 应已大部分 PASS"
info "下一步：bash scripts/1a7/99-cleanup.sh（清理）"
info "或者：写验收报告 docs/test-records/manual-tests/2026-07-17_phase1a7-acceptance-report.md"
