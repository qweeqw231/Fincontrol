@echo off
REM ============================================
REM 1a.8 Step 0: 调查 "1 fund per image" 之谜
REM ============================================
REM 跑真实 minimax M3，看 4 张样本图的原始响应
REM 看模型实际返回多少只基金 + 我们的抽取代码是否漏抽
REM ============================================

set SCRIPT_DIR=%~dp0
set BACKEND_DIR=%SCRIPT_DIR%..\..\..
set LOG_DIR=%SCRIPT_DIR%..\..\..\docs\test-records\automated-smoke\1a8
mkdir "%LOG_DIR%" 2>nul

echo ======================================
echo  1a.8 Step 0: minimax M3 原始响应调查
echo ======================================
echo.

REM 检查后端在跑
curl -fsS http://127.0.0.1:8080/actuator/health >nul 2>&1
if errorlevel 1 (
  echo [ERROR] 后端未启动，请先跑 02-up-backend.sh
  exit /b 1
)

REM 读 prompt_versions 表里的 screenshot_parser prompt
echo === 读 prompt_versions 表里的 screenshot_parser prompt ===
docker exec fincontrol-mysql mysql -uroot -proot fincontrol -e "SELECT prompt_name, version, LEFT(prompt_content, 200) AS preview FROM prompt_versions WHERE prompt_name='screenshot_parser';" 2>&1 | tee "%LOG_DIR%\00-prompt-preview.txt"

echo.
echo === 4 张样本图 upload + parse 原始响应 ===
echo （脚本只收集原始 JSON，不做抽取）

for %%F in (phase1a2-alipay-fund-list-20260715-2355-1.jpg phase1a2-alipay-fund-list-20260715-2355-2.jpg phase1a2-alipay-fund-list-20260715-2355-3.jpg phase1a2-alipay-fund-list-20260715-2356-1.jpg) do (
  echo.
  echo ---------- %%F ----------
  
  REM Upload
  for /f "delims=" %%J in ('curl -sS -X POST http://127.0.0.1:8080/api/screenshot/upload -H "X-User-Id: 1" -F "file=@%BACKEND_DIR%\uploads\samples\%%F"') do (
    set FILE_ID=%%J
  )
  
  REM Extract fileId via Python (handles JSON parsing reliably)
  for /f "delims=" %%K in ('echo !FILE_ID! ^| python -c "import json,sys; d=json.load(sys.stdin); print(d.get('data',{}).get('fileId',''))"') do (
    set FID=%%K
  )
  
  echo FileId: !FID!
  
  REM Parse - capture raw response
  curl -sS -X POST http://127.0.0.1:8080/api/screenshot/parse ^
    -H "Content-Type: application/json" ^
    -H "X-User-Id: 1" ^
    -d "{\"fileId\":\"!FID!\",\"userId\":1}" > "%LOG_DIR%\00-raw-%%~nF.json"
  
  echo Raw response (前 2000 字符):
  powershell -Command "Get-Content '%LOG_DIR%\00-raw-%%~nF.json' -Encoding UTF8 -TotalCount 50" 2>nul
  echo.
  echo Raw response 字符数: 
  for %%S in ("%LOG_DIR%\00-raw-%%~nF.json") do @echo %%~zS bytes
  echo.
)

echo.
echo ======================================
echo  Step 0 调查完成
echo  输出: %LOG_DIR%\00-raw-*.json (4 个文件)
echo  下一步：人工检查这些 JSON，看：
echo  1) minimax 实际返回多少只基金（count {"fundName"}）？
echo  2) 我们的 extractFirstJsonObject 有没有漏抽（看是 JSON 对象还是对象数组）？
echo  3) prompt 本身有没有要求"只挑一只"？
echo ======================================