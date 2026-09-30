# Decision 36 (2026-09-30): Stop FinControl -- SINGLE PROCESS runtime
# Pure-ASCII only (PowerShell 5.1 compatibility, no Chinese chars in source)
#
# Sequence: shutdown backend (HTTP, graceful) -> stop leftover Vite if any -> done
#
# What changed vs the old stop script:
#   - The interactive "Read-Host ... Stop MySQL80 too?" prompt is GONE. It blocked
#     whenever the script ran from a shortcut / double-click (no interactive console),
#     which defeated the whole "no typing" goal. MySQL80 is a Windows auto-start
#     service and is intentionally left alone (toggling it needs admin rights).
#   - Vite cleanup no longer relies on MainWindowTitle: the dev server is launched
#     hidden, so it has no window title and the old fallback could never match.
#     It now resolves the PID that actually LISTENS on 5173, then kills its parent.
#   - Backend shutdown goes through POST /api/system/shutdown first (the same path the
#     in-app button uses, so it is graceful), and falls back to killing the java process.

param(
    [switch]$KeepVite
)

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

Set-Location -LiteralPath $ProjectRoot
Write-Host "[stop-fincontrol] Working directory: $ProjectRoot" -ForegroundColor Cyan

# ---------------------------------------------------------------
# Step 1: backend -- graceful shutdown via HTTP, then verify
# ---------------------------------------------------------------
$backendStopped = $false
$healthUp = $false
try {
    Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 3 | Out-Null
    $healthUp = $true
} catch {}

if ($healthUp) {
    Write-Host "[stop-fincontrol] Requesting graceful backend shutdown..." -ForegroundColor Cyan
    try {
        Invoke-WebRequest -Uri 'http://localhost:8080/api/system/shutdown' -Method POST -UseBasicParsing -TimeoutSec 5 | Out-Null
    } catch {
        # Connection reset after the response is expected (Spring exits mid-reply)
    }
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Seconds 1
        $still = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
        if (-not $still) { $backendStopped = $true; break }
    }
    if ($backendStopped) {
        Write-Host "[stop-fincontrol] OK backend stopped (graceful)" -ForegroundColor Green
    } else {
        Write-Host "[stop-fincontrol] WARN: backend still listening after 15s; forcing" -ForegroundColor Yellow
    }
} else {
    Write-Host "[stop-fincontrol] Backend not reachable on 8080; checking for a stuck process" -ForegroundColor Gray
}

if (-not $backendStopped) {
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*fincontrol-backend*' }
    foreach ($p in $javaProcs) {
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
            Write-Host "[stop-fincontrol] OK java($($p.ProcessId)) stopped" -ForegroundColor Green
        } catch {}
    }
}
Get-Process mvn -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# ---------------------------------------------------------------
# Step 2: leftover Vite dev server (only relevant in frontend-dev mode)
# Resolve whoever actually LISTENS on 5173 instead of matching by window title.
# ---------------------------------------------------------------
if ($KeepVite) {
    Write-Host "[stop-fincontrol] -KeepVite set; leaving any Vite dev server running" -ForegroundColor Gray
} else {
    $viteListener = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
    if ($viteListener) {
        $vitePid = $viteListener[0].OwningProcess
        $viteProc = Get-CimInstance Win32_Process -Filter "ProcessId=$vitePid" -ErrorAction SilentlyContinue
        Write-Host "[stop-fincontrol] Stopping Vite dev server (PID=$vitePid)" -ForegroundColor Cyan
        # Kill the parent npm process too, otherwise it may respawn/linger
        if ($viteProc -and $viteProc.ParentProcessId) {
            Stop-Process -Id $viteProc.ParentProcessId -Force -ErrorAction SilentlyContinue
        }
        Stop-Process -Id $vitePid -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        $still = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
        if ($still) {
            Write-Host "[stop-fincontrol] WARN: 5173 still listening (PID=$($still[0].OwningProcess))" -ForegroundColor Yellow
        } else {
            Write-Host "[stop-fincontrol] OK Vite stopped, port 5173 released" -ForegroundColor Green
        }
    } else {
        Write-Host "[stop-fincontrol] No Vite dev server on 5173; nothing to do" -ForegroundColor Gray
    }
}

# ---------------------------------------------------------------
# Step 3: remove stale pid files from the pre-Decision-36 launcher
# ---------------------------------------------------------------
foreach ($f in @('.tmp\vite.pid', '.tmp\backend.pid')) {
    $p = Join-Path $ProjectRoot $f
    if (Test-Path -LiteralPath $p) {
        Remove-Item -LiteralPath $p -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "[stop-fincontrol] Done" -ForegroundColor Green
Write-Host "  - Backend : stopped" -ForegroundColor Gray
Write-Host "  - Frontend: served by backend, so nothing else to stop" -ForegroundColor Gray
Write-Host "  - MySQL80 : left running on purpose (Windows auto-start service;" -ForegroundColor Gray
Write-Host "              run 'net stop MySQL80' in an elevated shell to stop it)" -ForegroundColor Gray
Write-Host ""