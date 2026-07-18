@echo off
REM 测试 Docker MySQL 路径：MYSQL_PORT=3307
set MYSQL_PORT=3307
set DB_PORT=3307
"C:\Program Files\Git\bin\bash.exe" -c "cd '/c/Users/lbc19/Desktop/Fincontrol/fincontrol-backend' && bash scripts/1a7/01-up-mysql.sh" 2>&1