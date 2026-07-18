@echo off
REM ============================================
REM Layer 2：docker compose 一键起 MySQL + backend
REM ============================================
REM 验证 docker-compose.yml + Dockerfile 路径全过
REM ============================================

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..\..\..

taskkill /F /IM java.exe 2>nul
"C:\Program Files\Git\bin\bash.exe" -c "cd '%PROJECT_ROOT:\=/%' && docker rm -f fincontrol-mysql fincontrol-backend 2>/dev/null; docker compose down -v 2>/dev/null; docker compose up -d 2>&1"