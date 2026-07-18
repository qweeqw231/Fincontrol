@echo off
REM 停掉旧的 mvn 后端 + 旧 fincontrol-mysql 容器，准备 docker compose up
taskkill /F /IM java.exe /FI "WINDOWTITLE eq Maven*" 2>nul
"C:\Program Files\Git\bin\bash.exe" -c "cd '/c/Users/lbc19/Desktop/Fincontrol' && docker rm -f fincontrol-mysql fincontrol-backend 2>/dev/null; docker compose down -v 2>/dev/null; docker compose up -d 2>&1"