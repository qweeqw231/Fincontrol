# Decision 36 (2026-09-30): Launch FinControl -- SINGLE PROCESS runtime
# Pure-ASCII only (PowerShell 5.1 compatibility, no Chinese chars in source)
#
# Sequence: MySQL status check -> (build frontend if dist missing) -> backend
#           -> healthcheck -> open browser at http://localhost:8080/ -> toast
#
# What changed vs the old launcher:
#   - Vite dev server is NO LONGER started. The backend serves the frontend build
#     output (fincontrol-frontend/dist) itself, so the whole app is one port (8080).
#   - MySQL is only *checked*, never started/stopped here: Start-Service/Stop-Service
#     require admin rights, which would make this shortcut raise a UAC prompt on every
#     launch. MySQL80 keeps its Windows auto-start instead.
#   - Backend start reuses an existing jar (-SkipRebuild) unless it is missing.

param(
    [switch]$ForceRebuild
)

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$AppUrl = 'http://localhost:8080/'

Set-Location -LiteralPath $ProjectRoot
Write-Host "[launch-fincontrol] Working directory: $ProjectRoot" -ForegroundColor Cyan

# ---------------------------------------------------------------
# Step 0: already running? -> just open the browser (idempotent)
# ---------------------------------------------------------------
$port80Listen = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($port80Listen) {
    Write-Host "[launch-fincontrol] Port 8080 already in use; app appears to be running" -ForegroundColor Green
    Start-Process -FilePath $AppUrl
    Write-Host "[launch-fincontrol] Opened $AppUrl" -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------
# Step 1: MySQL status check only (no elevation, no service start)
# ---------------------------------------------------------------
$mysqlService = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue
if ($null -eq $mysqlService) {
    Write-Host "[launch-fincontrol] WARN: MySQL80 service not found; install MySQL 8.0 first" -ForegroundColor Yellow
} elseif ($mysqlService.Status -ne 'Running') {
    Write-Host "[launch-fincontrol] WARN: MySQL80 is not running (Status=$($mysqlService.Status))." -ForegroundColor Yellow
    Write-Host "[launch-fincontrol]       Starting it needs admin rights, so it is NOT started here." -ForegroundColor Yellow
    Write-Host "[launch-fincontrol]       Fix: run 'net start MySQL80' in an elevated shell, or set the" -ForegroundColor Yellow
    Write-Host "[launch-fincontrol]       service to Automatic so Windows starts it at boot." -ForegroundColor Yellow
    Write-Host "[launch-fincontrol]       Backend will likely fail to connect; continuing anyway." -ForegroundColor Yellow
} else {
    Write-Host "[launch-fincontrol] OK MySQL80 is running" -ForegroundColor Green
}

# ---------------------------------------------------------------
# Step 2: frontend build output (dist) -- build only when missing
# Decision 36 option 2a: skip the build if dist already exists, so daily
# startup stays fast. Use -ForceRebuild after changing frontend code.
# ---------------------------------------------------------------
$distDir = Join-Path $ProjectRoot 'fincontrol-frontend\dist'
$distIndex = Join-Path $distDir 'index.html'
$needBuild = $ForceRebuild -or (-not (Test-Path -LiteralPath $distIndex))

if ($needBuild) {
    Write-Host "[launch-fincontrol] Frontend build output missing (or -ForceRebuild); building..." -ForegroundColor Cyan
    # Resolve npm explicitly: this script is started by a desktop shortcut (fresh
    # powershell.exe), whose PATH comes from the registry and is not guaranteed to
    # resolve 'npm'. Explicit candidates first, PATH as last resort.
    $npmPath = $null
    $npmCandidates = @('C:\Program Files\nodejs\npm.cmd')
    $npmOnPath = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($npmOnPath) { $npmCandidates += $npmOnPath.Source }
    foreach ($cand in $npmCandidates) {
        if ($cand -and (Test-Path -LiteralPath $cand)) { $npmPath = $cand; break }
    }
    if (-not $npmPath) {
        Write-Host "[launch-fincontrol] ERROR: npm.cmd not found; install Node.js 18+ first" -ForegroundColor Red
        exit 1
    }
    Write-Host "[launch-fincontrol]   using: $npmPath" -ForegroundColor Gray
    Push-Location (Join-Path $ProjectRoot 'fincontrol-frontend')
    & $npmPath run build
    $buildExit = $LASTEXITCODE
    Pop-Location
    if ($buildExit -ne 0 -or -not (Test-Path -LiteralPath $distIndex)) {
        Write-Host "[launch-fincontrol] ERROR: frontend build failed (exit=$buildExit)" -ForegroundColor Red
        exit 1
    }
    Write-Host "[launch-fincontrol] OK frontend built" -ForegroundColor Green
} else {
    $distAge = (Get-Item -LiteralPath $distIndex).LastWriteTime
    Write-Host "[launch-fincontrol] OK dist found (built $distAge); skipping build" -ForegroundColor Gray
    Write-Host "[launch-fincontrol]    (run with -ForceRebuild after changing frontend code)" -ForegroundColor Gray
}

# ---------------------------------------------------------------
# Step 3: start backend -- reuse existing jar, backend serves dist too
# (restart-backend.ps1 already passes the absolute dist path; Decision 36)
# ---------------------------------------------------------------
$jarPath = Join-Path $ProjectRoot 'fincontrol-backend\target\fincontrol-backend.jar'
$restartScript = Join-Path $ProjectRoot 'scripts\1b\restart-backend.ps1'
if (-not (Test-Path -LiteralPath $restartScript)) {
    Write-Host "[launch-fincontrol] ERROR: $restartScript not found" -ForegroundColor Red
    exit 1
}

if (Test-Path -LiteralPath $jarPath) {
    Write-Host "[launch-fincontrol] Starting backend (reusing existing jar)..." -ForegroundColor Cyan
    & $restartScript -SkipRebuild
} else {
    Write-Host "[launch-fincontrol] First run: building backend jar (this takes a while)..." -ForegroundColor Cyan
    & $restartScript
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "[launch-fincontrol] ERROR: backend start failed; see log\backend-stderr.log" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------
# Step 4: toast notification
# ---------------------------------------------------------------
Write-Host "[launch-fincontrol] Showing desktop toast..." -ForegroundColor Cyan
try {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $notify = New-Object System.Windows.Forms.NotifyIcon
    $notify.Icon = [System.Drawing.SystemIcons]::Information
    $notify.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Info
    $notify.BalloonTipTitle = 'FinControl'
    $notify.BalloonTipText = "Ready. Opening $AppUrl"
    $notify.Visible = $true
    $notify.ShowBalloonTip(8000)
    $job = Start-Job -ScriptBlock {
        param($n)
        Start-Sleep -Seconds 12
        $n.Visible = $false
        $n.Dispose()
    } -ArgumentList $notify
    Write-Host "[launch-fincontrol] OK desktop toast shown" -ForegroundColor Green
} catch {
    Write-Host "[launch-fincontrol] WARN: failed to show desktop toast: $_" -ForegroundColor Yellow
}

# ---------------------------------------------------------------
# Step 5: open browser
# ---------------------------------------------------------------
try {
    Start-Process -FilePath $AppUrl
    Write-Host "[launch-fincontrol] OK browser opened $AppUrl" -ForegroundColor Green
} catch {
    Write-Host "[launch-fincontrol] WARN: failed to auto-open browser; please visit $AppUrl" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[launch-fincontrol] All services ready" -ForegroundColor Green
Write-Host "  - MySQL  : running (auto-start service; not managed by this script)" -ForegroundColor Gray
Write-Host "  - App    : $AppUrl   (frontend + backend on ONE port)" -ForegroundColor Gray
Write-Host "  - Shutdown: browser top-right button, or scripts\desktop\stop-fincontrol.ps1" -ForegroundColor Gray
Write-Host ""