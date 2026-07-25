# 1b.4 PR8 / Decision 35: Launch FinControl full stack
# Pure-ASCII only (PowerShell 5.1 compatibility)
# Sequence: MySQL start -> Backend (decision 24 restart-backend.ps1) -> Vite dev server -> Browser open

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

Set-Location -LiteralPath $ProjectRoot

Write-Host "[launch-fincontrol] Working directory: $ProjectRoot" -ForegroundColor Cyan

# Step 1: Start MySQL (no-op if already running)
$mysqlService = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue
if ($null -eq $mysqlService) {
    Write-Host "[launch-fincontrol] WARN: MySQL80 service not installed; install MySQL 8.0 first" -ForegroundColor Yellow
} elseif ($mysqlService.Status -ne 'Running') {
    Write-Host "[launch-fincontrol] Starting MySQL80..." -ForegroundColor Yellow
    Start-Service -Name 'MySQL80'
    Start-Sleep -Seconds 3
    $mysqlService.Refresh()
    if ($mysqlService.Status -eq 'Running') {
        Write-Host "[launch-fincontrol] OK MySQL80 is running" -ForegroundColor Green
    } else {
        Write-Host "[launch-fincontrol] ERROR: MySQL80 failed to start (Status=$($mysqlService.Status)); backend will likely fail" -ForegroundColor Red
    }
} else {
    Write-Host "[launch-fincontrol] OK MySQL80 is already running" -ForegroundColor Green
}

# Step 2: Start backend (delegates to decision 24 restart-backend.ps1)
Write-Host "[launch-fincontrol] Starting backend (scripts/1b/restart-backend.ps1)..." -ForegroundColor Cyan
& "$ProjectRoot\scripts\1b\restart-backend.ps1" *>&1 | Out-Null

# Step 3: Wait for backend health = UP (max 30 seconds)
Write-Host "[launch-fincontrol] Waiting for backend health = UP..." -ForegroundColor Yellow
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) {
            $body = $r.Content | ConvertFrom-Json
            if ($body.status -eq 'UP') {
                $ready = $true
                Write-Host "[launch-fincontrol] OK backend health = UP (${i}s)" -ForegroundColor Green
                break
            }
        }
    } catch {}
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    Write-Host "[launch-fincontrol] ERROR: backend did not become UP within 30s; aborting Vite startup" -ForegroundColor Red
    Write-Host "[launch-fincontrol] See logs: $ProjectRoot\log\backend-stderr.log" -ForegroundColor Yellow
    exit 1
}

# Step 4: Start Vite dev server in background, write PID to .tmp/vite.pid
Write-Host "[launch-fincontrol] Starting Vite dev server..." -ForegroundColor Cyan
$tmpDir = Join-Path $ProjectRoot '.tmp'
if (-not (Test-Path -LiteralPath $tmpDir)) {
    New-Item -ItemType Directory -Path $tmpDir | Out-Null
}
$vitePidFile = Join-Path $tmpDir 'vite.pid'
if (Test-Path -LiteralPath $vitePidFile) {
    $oldVitePid = Get-Content -LiteralPath $vitePidFile -ErrorAction SilentlyContinue
    if ($oldVitePid) {
        $oldProc = Get-Process -Id $oldVitePid -ErrorAction SilentlyContinue
        if ($oldProc) {
            try { Stop-Process -Id $oldVitePid -Force -ErrorAction SilentlyContinue } catch {}
        }
    }
}
$npmPath = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
if (-not $npmPath) {
    Write-Host "[launch-fincontrol] ERROR: npm.cmd not in PATH; install Node.js 18+ first" -ForegroundColor Red
    exit 1
}
$proc = Start-Process -FilePath $npmPath -ArgumentList @('run','dev','--prefix','fincontrol-frontend') `
              -WindowStyle Hidden -PassThru
$proc.Id | Set-Content -LiteralPath $vitePidFile -Encoding UTF8
Write-Host "[launch-fincontrol] OK Vite started (PID=$($proc.Id))" -ForegroundColor Green

# Step 5: Wait for Vite to be ready and open browser
Write-Host "[launch-fincontrol] Waiting for Vite to be ready..." -ForegroundColor Yellow
$viteReady = $false
for ($i = 0; $i -lt 20; $i++) {
    try {
        $vr = Invoke-WebRequest -Uri 'http://localhost:5173/' -UseBasicParsing -TimeoutSec 2
        if ($vr.StatusCode -eq 200) {
            $viteReady = $true
            Write-Host "[launch-fincontrol] OK Vite is ready (${i}s)" -ForegroundColor Green
            break
        }
    } catch {}
    Start-Sleep -Seconds 1
}
if (-not $viteReady) {
    Write-Host "[launch-fincontrol] WARN: Vite not ready within 20s; you can refresh browser manually" -ForegroundColor Yellow
}

# Open default browser to home page
$url = 'http://localhost:5173/'
try {
    Start-Process -FilePath $url
    Write-Host "[launch-fincontrol] OK browser opened $url" -ForegroundColor Green
} catch {
    Write-Host "[launch-fincontrol] WARN: failed to auto-open browser; please visit $url" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[launch-fincontrol] All services ready" -ForegroundColor Green
Write-Host "  - MySQL: running" -ForegroundColor Gray
Write-Host "  - Backend: http://localhost:8080  (PID see .tmp\backend.pid)" -ForegroundColor Gray
Write-Host "  - Frontend: http://localhost:5173  (PID=$($proc.Id), see .tmp\vite.pid)" -ForegroundColor Gray
Write-Host "  - Shutdown: browser top-right button, or run scripts\desktop\stop-fincontrol.ps1" -ForegroundColor Gray
Write-Host ""
