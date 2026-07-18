#!/usr/bin/env bash
# ============================================
# scripts/git/commit-and-push.sh
# ============================================
# 一站式 git add + commit + push
# 用法：
#   ./commit-and-push.sh "feat: xxx"                    # 默认 add 所有 tracked changes
#   ./commit-and-push.sh -F /path/to/commit-msg.txt     # 用文件作 commit message
# ============================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROXY="${PROXY:-http://127.0.0.1:7892}"

cd "$PROJECT_ROOT"

# 检查 commit message
if [ $# -eq 0 ]; then
  echo "用法: $0 \"<commit message>\""
  echo "或:  $0 -F <commit-msg-file>"
  exit 1
fi

echo "=== 当前状态 ==="
git status --short
echo

# 默认只 add 已有 tracked 文件（不 add 新文件，避免意外提交调试脚本）
git add -u
if [ -n "$(git status --short | grep '^??')" ]; then
  echo "⚠ 未追踪的新文件（不会自动 add，需要手动 git add <file>）："
  git status --short | grep '^??'
  echo
fi

# commit + push
echo "=== 提交 + 推送 ==="
git -c http.proxy="$PROXY" commit "$@"
git -c http.proxy="$PROXY" push origin main

echo
echo "=== 完成 ==="
git log --oneline -1