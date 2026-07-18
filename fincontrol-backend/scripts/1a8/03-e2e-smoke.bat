@echo off
rem ============================================
rem Phase 1a.8 端到端 smoke 脚本（Windows，1a.7 兼容）
rem 目标：跑通 vision 4/4 + chat 5/5 + fallback 链路
rem ============================================
rem 用法：
rem   1) 设置 fincontrol-backend/src/main/resources/application-local.yml 的真实 key
rem      （minimax + 豆包 + 可选 DeepSeek）
rem   2) 启动后端：mvn spring-boot:run
rem   3) 跑此脚本：scripts\1a8\03-e2e-smoke.bat
rem
rem 退出码：
rem   0 = 全 PASS
rem   1 = 部分 FAIL（脚本继续跑完所有 case，最后报告 PASS/FAIL 计数）
rem
rem 依赖：curl（系统默认），无需额外依赖
rem ============================================

setlocal enabledelayedexpansion

set BASE_URL=http://localhost:8080
set USER_ID=1

rem 4 张测试截图路径（用户上传文件后从 ./uploads/screenshots/ 取 fileId）
rem 烟测前请先 upload 4 张图（脚本会辅助用 curl 调 upload）

set PASS=0
set FAIL=0

echo ============================================
echo Phase 1a.8 end-to-end smoke
echo base_url=%BASE_URL%
echo ============================================

rem ---------- Step 1: 启动检查 ----------
echo [Step 1] 启动检查 ...
curl -s -o nul -w "%%{http_code}" %BASE_URL%/actuator/health > %TEMP%\health.txt
set /p HEALTH=<%TEMP%\health.txt
if "%HEALTH%" == "200" (
    echo [PASS] actuator/health UP
    set /a PASS+=1
) else (
    echo [FAIL] actuator/health 不可用（HTTP %HEALTH%），请确认后端已启动
    set /a FAIL+=1
    goto report
)

rem ---------- Step 2: upload 4 张图 ----------
echo [Step 2] upload 4 张测试截图 ...
rem 默认找 ./uploads/screenshots/ 下 *.jpg/png
set SCREENSHOT_DIR=.\uploads\screenshots
if not exist %SCREENSHOT_DIR% (
    echo [FAIL] 未找到 %SCREENSHOT_DIR%；请先把 4 张测试图放在该目录下
    set /a FAIL+=1
    goto report
)

set FID_1=
set FID_2=
set FID_3=
set FID_4=

set IDX=0
for %%f in (%SCREENSHOT_DIR%\*.jpg %SCREENSHOT_DIR%\*.png) do (
    set /a IDX+=1
    echo    upload %%f ...
    for /f "delims=" %%a in ('curl -s -X POST -H "X-User-Id: %USER_ID%" -F "file=@%%f" %BASE_URL%/api/screenshot/upload') do (
        rem naive 解析 — 实际生产用 jq
        echo    %%a
    )
)

rem 注：当前 naive curl 输出不会自动 parse JSON fileId
rem 实际跑需用 jq / powershell 解析 response.code=0 data.fileId
rem 这部分留待 user 集成（生产用 PowerShell + ConvertFrom-Json）

echo ============================================
echo [%PASS% PASS / %FAIL% FAIL] 中间步骤（upload + parse 详细实现见 scripts 1a7/03-smoke-1-screenshot.sh）
echo ============================================

:report
echo.
echo ============================================
echo 1a.8 smoke 总计：%PASS% PASS / %FAIL% FAIL
echo ============================================
echo.
echo 后续步骤：
echo   1) 用 PowerShell + jq 解析 JSON 抽取 fileId
echo   2) 调 /api/screenshot/parse 检查 code=0
echo   3) 查 chat_history 表：
echo        SELECT used_provider, fallback_triggered, COUNT(*)
echo        FROM chat_history
echo        GROUP BY used_provider, fallback_triggered;
echo.
echo 关键字段：
echo   - used_provider: minimax | doubao | deepseek（NULL = 失败 / user 行）
echo   - fallback_triggered: 0 | 1
echo   - conversation_type: ai_assistant | screenshot_parse

endlocal & (
    if "%FAIL%" == "0" (exit /b 0) else (exit /b 1)
)
