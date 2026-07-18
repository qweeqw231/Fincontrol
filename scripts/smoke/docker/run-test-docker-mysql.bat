@echo off
REM ============================================
REM Layer 1：Docker MySQL 在 3307（保留本地 3306）
REM ============================================
REM 验证 01-up-mysql.sh 在 MYSQL_PORT=3307 时启 Docker MySQL 容器
REM ============================================

set MYSQL_PORT=3307
set DB_PORT=3307

set SCRIPT_DIR=%~dp0
set BACKEND_DIR=%SCRIPT_DIR%..\..\..\fincontrol-backend

"C:\Program Files\Git\bin\bash.exe" -c "cd '%BACKEND_DIR:\=/%' && bash scripts/1a7/01-up-mysql.sh" 2>&1