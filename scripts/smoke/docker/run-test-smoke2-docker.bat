@echo off
REM ============================================
REM 通用：跑 chat smoke（针对当前运行的 backend）
REM ============================================
REM 验证后端 chat send 多轮端到端
REM ============================================

set SCRIPT_DIR=%~dp0
set BACKEND_DIR=%SCRIPT_DIR%..\..\..\fincontrol-backend

"C:\Program Files\Git\bin\bash.exe" -c "cd '%BACKEND_DIR:\=/%' && bash scripts/1a7/04-smoke-2-chat.sh" 2>&1