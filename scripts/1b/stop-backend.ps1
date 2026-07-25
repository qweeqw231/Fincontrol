# 1b.4 PR8 / Decision 35: Stop backend jar gracefully
# Pure-ASCII only (PowerShell 5.1 compatibility)
# Sequence: find java process -> prefer API shutdown -> 30s wait -> force kill fallback

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

Set-Location -LiteralPath $ProjectRoot

Write-Host "[stop-backend] Searching fincontrol backend java process..." -ForegroundColor Cyan

# Filter by CommandLine (fincontrol-backend.jar)
$backendProcs = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
}

if (-not $backendProcs) {
    Write-Host "[stop-backend] No fincontrol backend process found (probably not running)" -ForegroundColor Gray
    exit 0
}

Write-Host "[stop-backend] Found $($backendProcs.Count) fincontrol backend process(es): $($backendProcs.Id -join ',')" -ForegroundColor Cyan

# Prefer API graceful shutdown (cooperates with SystemController, 800ms grace)
$useApi = $false
try {
    $health = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 2
    if ($health.StatusCode -eq 200) {
        $useApi = $true
    }
} catch {}

if ($useApi) {
    Write-Host "[stop-backend] Triggering graceful shutdown via API (POST /api/system/shutdown)..." -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri 'http://localhost:8080/api/system/shutdown' -Method Post -UseBasicParsing -TimeoutSec 3
        Write-Host "[stop-backend] OK API call succeeded, waiting 5s for backend exit..." -ForegroundColor Green
    } catch {
        Write-Host "[stop-backend] WARN: API call exception: $_" -ForegroundColor Yellow
    }
    Start-Sleep -Seconds 5
}

# Watch process exit within 30 seconds
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    $stillRunning = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
    }
    if (-not $stillRunning) {
        Write-Host "[stop-backend] OK backend exited" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 2
}

# Fallback: force kill
$stillRunning = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
}
if ($stillRunning) {
    Write-Host "[stop-backend] WARN: 30s timeout, force killing: $($stillRunning.Id -join ',')" -ForegroundColor Yellow
    foreach ($p in $stillRunning) {
        try { Stop-Process -Id $p.Id -Force } catch {}
    }
    Start-Sleep -Seconds 2
}

# Final state check
$final = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
    $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol-backend*'
}
if ($final) {
    Write-Host "[stop-backend] ERROR: backend still running: $($final.Id -join ',')" -ForegroundColor Red
    exit 1
} else {
    Write-Host "[stop-backend] OK backend fully stopped" -ForegroundColor Green
}

# Cleanup .tmp/backend.pid
$pidFile = Join-Path $ProjectRoot '.tmp\backend.pid'
if (Test-Path -LiteralPath $pidFile) {
    Remove-Item -LiteralPath $pidFile -ErrorAction SilentlyContinue
}
