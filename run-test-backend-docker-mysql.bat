@echo off
REM 启动后端连接到 Docker MySQL（3307）
set MYSQL_PORT=3307
set DB_HOST=127.0.0.1
set DB_PORT=3307
set DB_USER=root
set DB_PASSWORD=root
"C:\Program Files\Git\bin\bash.exe" -c "cd '/c/Users/lbc19/Desktop/Fincontrol/fincontrol-backend' && bash scripts/1a7/02-up-backend.sh" 2>&1