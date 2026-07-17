@echo off
rem 1a.7 端到端冒烟批处理 wrapper
rem 用法：双击 run-1a7.bat 或 cmd /c run-1a7.bat
rem 自动 cd 到 backend 目录并按顺序跑 6 个步骤

cd /d "C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"

echo === 1a.7 端到端冒烟启动 ===
echo 时间: %date% %time%

rem Step 1: 启 MySQL Docker
echo.
echo [Step 1/6] 启 MySQL Docker 容器
"C:\Program Files\Git\bin\bash.exe" scripts/1a7/01-up-mysql.sh
if errorlevel 1 goto :end

rem Step 2: 启后端
echo.
echo [Step 2/6] 启 Spring Boot 后端（后台 + 等 8080）
"C:\Program Files\Git\bin\bash.exe" scripts/1a7/02-up-backend.sh
if errorlevel 1 goto :end

rem Step 3: 冒烟 1（截图）
echo.
echo [Step 3/6] 冒烟 1 — 截图全链路
"C:\Program Files\Git\bin\bash.exe" scripts/1a7/03-smoke-1-screenshot.sh
if errorlevel 1 goto :step4

rem Step 4: 冒烟 2（chat）
echo.
echo [Step 4/6] 冒烟 2 — chat 多轮
"C:\Program Files\Git\bin\bash.exe" scripts/1a7/04-smoke-2-chat.sh

:step4
rem Step 5: 覆盖率 + Swagger
echo.
echo [Step 5/6] 覆盖率 + Swagger 端点
"C:\Program Files\Git\bin\bash.exe" scripts/1a7/05-coverage.sh

rem Step 6: 清理（保留 MySQL 容器）
echo.
echo [Step 6/6] 清理（保留 MySQL 容器）
"C:\Program Files\Git\bin\bash.exe" scripts/1a7/99-cleanup.sh

:end
echo.
echo === 1a.7 端到端冒烟完成 ===
echo 日志：%cd%\..\docs\test-records\manual-tests\2026-07-17_phase1a7-smoke.log
pause
