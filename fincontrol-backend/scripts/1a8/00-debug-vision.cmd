@echo off
setlocal enabledelayedexpansion
REM 1a.8 Step 0 v4: 简化提取 fileId

set "LOG_DIR=C:\Users\lbc19\Desktop\Fincontrol\docs\test-records\automated-smoke\1a8"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ======================================
echo  1a.8 Step 0 v4
echo ======================================
echo.

for %%F in (phase1a2-alipay-fund-list-20260715-2355-1.jpg phase1a2-alipay-fund-list-20260715-2355-2.jpg phase1a2-alipay-fund-list-20260715-2355-3.jpg phase1a2-alipay-fund-list-20260715-2356-1.jpg) do (
  echo ---------- %%F ----------
  
  REM Upload
  curl -sS -X POST http://127.0.0.1:8080/api/screenshot/upload -H "X-User-Id: 1" -F "file=@C:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend\uploads\samples\%%F" > "%LOG_DIR%\00-up-%%~nF.json"
  
  REM Use Python to extract and write fileId to fid file
  C:\Windows\py.exe -c "import json,re; s=open(r'%LOG_DIR%\00-up-%%~nF.json',encoding='utf-8').read(); m=re.search(r'\"fileId\":\"([^\"]+)\"',s); print(m.group(1) if m else '')" > "%LOG_DIR%\00-fid-%%~nF.txt" 2>nul
  set /p FID=<"%LOG_DIR%\00-fid-%%~nF.txt"
  echo fileId: !FID!
  
  if "!FID!"=="" (
    echo FAILED
    continue
  )
  
  REM Build parse body
  echo {"fileId":"!FID!","userId":1} > "%LOG_DIR%\00-req-%%~nF.json"
  curl -sS -X POST http://127.0.0.1:8080/api/screenshot/parse -H "Content-Type: application/json" -H "X-User-Id: 1" --data "@%LOG_DIR%\00-req-%%~nF.json" > "%LOG_DIR%\00-resp-%%~nF.json"
  
  REM Print resp summary
  for %%S in ("%LOG_DIR%\00-resp-%%~nF.json") do @echo response size: %%~zS bytes
  
  REM Use Python to summarize
  C:\Windows\py.exe -c "import json; d=json.load(open(r'%LOG_DIR%\00-resp-%%~nF.json',encoding='utf-8')); print('code:',d.get('code')); data=d.get('data') or {}; cats=data.get('categories') or []; total=sum(len(c.get('funds') or []) for c in cats); print('  categories:',len(cats),'total_funds:',total); [print('   cat:',c.get('categoryName'),'funds:',[f.get('fundName') for f in (c.get('funds') or [])]) for c in cats]; print('  total_asset:',data.get('totalAsset'))" 2>nul
  
  REM Chat history check
  docker exec fincontrol-mysql mysql -uroot -proot fincontrol -N -e "SELECT LENGTH(content) FROM chat_history WHERE conversation_id='conv-!FID!' AND role='assistant' LIMIT 1;" 2>nul
  echo.
)

echo ======================================
echo  Done
echo ======================================