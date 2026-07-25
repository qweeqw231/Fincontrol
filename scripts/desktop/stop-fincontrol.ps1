# 1b.4 PR8 / 决策 35：FinControl 一键停止脚本
#
# 流程：
#  1) 停 Vite（按 .tmp/vite.pid 找进程）
#  2) 调 scripts/1b/stop-backend.ps1 停后端
#  3) 询问是否关闭 MySQL（默认否）
#
# 使用：
#   PowerShell -ExecutionPolicy Bypass -File scripts\desktop\stop-fincontrol.ps1

$ErrorActionPreference = 'Continue'

# 工作目录：项目根
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
Set-Location -LiteralPath $ProjectRoot

Write-Host "[stop-fincontrol] 工作目录：$ProjectRoot" -ForegroundColor Cyan

# 1) 停 Vite
$vitePidFile = Join-Path $ProjectRoot '.tmp\vite.pid'
if (Test-Path -LiteralPath $vitePidFile) {
    $vitePid = Get-Content -LiteralPath $vitePidFile -ErrorAction SilentlyContinue
    if ($vitePid) {
        $viteProc = Get-Process -Id $vitePid -ErrorAction SilentlyContinue
        if ($viteProc) {
            try {
                Stop-Process -Id $vitePid -Force
                Write-Host "[stop-fincontrol] ✓ Vite 已停止（PID=$vitePid）" -ForegroundColor Green
            } catch {
                Write-Host "[stop-fincontrol] ⚠ 停止 Vite 失败：$_" -ForegroundColor Yellow
            }
        } else {
            Write-Host "[stop-fincontrol] Vite PID=$vitePid 不存在；跳过" -ForegroundColor Gray
        }
    }
    Remove-Item -LiteralPath $vitePidFile -ErrorAction SilentlyContinue
} else {
    Write-Host "[stop-fincontrol] .tmp\vite.pid 不存在；尝试按 node 进程名兜底停" -ForegroundColor Gray
    $nodeProcs = Get-Process -Name 'node' -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*node.exe' -and $_.MainWindowTitle -like '*Vite*' }
    foreach ($p in $nodeProcs) {
        try { Stop-Process -Id $p.Id -Force; Write-Host "[stop-fincontrol] ✓ node($($p.Id)) 已停" -ForegroundColor Green } catch {}
    }
}
# 兜底：杀掉所有 npm.cmd（Vite 的父进程）
$npmProcs = Get-Process -Name 'npm' -ErrorAction SilentlyContinue
foreach ($p in $npmProcs) {
    try { Stop-Process -Id $p.Id -Force } catch {}
}

# 2) 停后端
Write-Host "[stop-fincontrol] 停止后端（scripts/1b/stop-backend.ps1）..." -ForegroundColor Cyan
if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'scripts\1b\stop-backend.ps1')) {
    & "$ProjectRoot\scripts\1b\stop-backend.ps1"
} else {
    Write-Host "[stop-fincontrol] ⚠ scripts\1b\stop-backend.ps1 不存在；回退到手动查找 java 进程" -ForegroundColor Yellow
    $javaProcs = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol*'
    }
    foreach ($p in $javaProcs) {
        try { Stop-Process -Id $p.Id -Force; Write-Host "[stop-fincontrol] ✓ java($($p.Id)) 已停" -ForegroundColor Green } catch {}
    }
}

# 3) 询问 MySQL
Write-Host ""
$ans = Read-Host "[stop-fincontrol] 关闭 MySQL80？(y/N)"
if ($ans -eq 'y' -or $ans -eq 'Y') {
    Stop-Service -Name 'MySQL80' -Force -ErrorAction SilentlyContinue
    Write-Host "[stop-fincontrol] ✓ MySQL80 已停止" -ForegroundColor Green
} else {
    Write-Host "[stop-fincontrol] MySQL80 保持运行" -ForegroundColor Gray
}

Write-Host ""
Write-Host "[stop-fincontrol] ✓✓✓ 停止完成 ✓✓✓" -ForegroundColor Green
