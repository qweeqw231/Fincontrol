# 1b.4 PR8 / 决策 35：后端 jar 优雅停止脚本
#
# 流程：
#  1) 找 fincontrol 后端 java 进程（通过 commandline 关键字）
#  2) 优先用 API 触发 /api/system/shutdown 优雅停机（如端口可达）
#  3) 30 秒内未退 → Stop-Process -Force
#  4) 写 .tmp/backend.pid 标记（如有）

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
Set-Location -LiteralPath $ProjectRoot

Write-Host "[stop-backend] 查找 fincontrol 后端 java 进程..." -ForegroundColor Cyan

# 通过 CommandLine 过滤（fincontrol-backend.jar）
$backendProcs = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
}

if (-not $backendProcs) {
    Write-Host "[stop-backend] 未发现 fincontrol 后端进程（可能未启动）" -ForegroundColor Gray
    exit 0
}

Write-Host "[stop-backend] 找到 $($backendProcs.Count) 个 fincontrol 后端进程：$($backendProcs.Id -join ',')" -ForegroundColor Cyan

# 优先尝试 API 优雅停机（与 SystemController 协同，800ms grace）
$useApi = $false
try {
    $health = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 2
    if ($health.StatusCode -eq 200) {
        $useApi = $true
    }
} catch {}

if ($useApi) {
    Write-Host "[stop-backend] 通过 API 触发优雅停机（POST /api/system/shutdown）..." -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri 'http://localhost:8080/api/system/shutdown' -Method Post -UseBasicParsing -TimeoutSec 3
        Write-Host "[stop-backend] ✓ API 调用成功，等待 5 秒后端 exit..." -ForegroundColor Green
    } catch {
        Write-Host "[stop-backend] ⚠ API 调用异常：$_" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds 5
}

# 30 秒内观察进程是否退出
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    $stillRunning = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
    }
    if (-not $stillRunning) {
        Write-Host "[stop-backend] ✓ 后端已退出" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 2
}

# 兜底：强制 kill
$stillRunning = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
}
if ($stillRunning) {
    Write-Host "[stop-backend] ⚠ 30 秒未自动退出，强制 kill: $($stillRunning.Id -join ',')" -ForegroundColor Yellow
    foreach ($p in $stillRunning) {
        try { Stop-Process -Id $p.Id -Force } catch {}
    }
    Start-Sleep -Seconds 2
}

# 最终状态
$final = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
}
if ($final) {
    Write-Host "[stop-backend] ✗ 仍有 fincontrol 后端进程运行：$($final.Id -join ',')" -ForegroundColor Red
    exit 1
} else {
    Write-Host "[stop-backend] ✓✓ 后端已完全停止" -ForegroundColor Green
}

# 清理 .tmp/backend.pid（如有）
$pidFile = Join-Path $ProjectRoot '.tmp\backend.pid'
if (Test-Path -LiteralPath $pidFile) {
    Remove-Item -LiteralPath $pidFile -ErrorAction SilentlyContinue
}
