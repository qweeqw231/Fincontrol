@echo off
REM ============================================
REM Layer 1+2 混合：本地 mvn 后端 + Docker MySQL on 3307
REM ============================================
REM 验证后端进程（本地 mvn）连到 Docker MySQL 容器
REM ============================================

set MYSQL_PORT=3307
set DB_HOST=127.0.0.1
set DB_PORT=3307
set DB_USER=root
set DB_PASSWORD=root

set SCRIPT_DIR=%~dp0
set BACKEND_DIR=%SCRIPT_DIR%..\..\..\fincontrol-backend

"C:\Program Files\Git\bin\bash.exe" -c "cd '%BACKEND_DIR:\=/%' && bash scripts/1a7/02-up-backend.sh" 2>&1