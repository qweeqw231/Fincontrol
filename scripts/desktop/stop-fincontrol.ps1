# 1b.4 PR8 / Decision 35: Stop FinControl full stack
# Pure-ASCII only (PowerShell 5.1 compatibility)
# Sequence: Vite stop -> Backend stop (scripts/1b/stop-backend.ps1) -> Ask user for MySQL

$ErrorActionPreference = 'Continue'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

Set-Location -LiteralPath $ProjectRoot

Write-Host "[stop-fincontrol] Working directory: $ProjectRoot" -ForegroundColor Cyan

# Step 1: Stop Vite (by PID file, then by node.exe fallback)
$vitePidFile = Join-Path $ProjectRoot '.tmp\vite.pid'
if (Test-Path -LiteralPath $vitePidFile) {
    $vitePid = Get-Content -LiteralPath $vitePidFile -ErrorAction SilentlyContinue
    if ($vitePid) {
        $viteProc = Get-Process -Id $vitePid -ErrorAction SilentlyContinue
        if ($viteProc) {
            try {
                Stop-Process -Id $vitePid -Force
                Write-Host "[stop-fincontrol] OK Vite stopped (PID=$vitePid)" -ForegroundColor Green
            } catch {
                Write-Host "[stop-fincontrol] WARN: failed to stop Vite: $_" -ForegroundColor Yellow
            }
        } else {
            Write-Host "[stop-fincontrol] Vite PID=$vitePid not running; skipping" -ForegroundColor Gray
        }
    }
    Remove-Item -LiteralPath $vitePidFile -ErrorAction SilentlyContinue
} else {
    Write-Host "[stop-fincontrol] .tmp\vite.pid not found; trying fallback by node process name" -ForegroundColor Gray
    $nodeProcs = Get-Process -Name 'node' -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*node.exe' -and $_.MainWindowTitle -like '*Vite*' }
    foreach ($p in $nodeProcs) {
        try { Stop-Process -Id $p.Id -Force; Write-Host "[stop-fincontrol] OK node($($p.Id)) stopped" -ForegroundColor Green } catch {}
    }
}
# Fallback: kill all npm.cmd processes (Vite parent)
$npmProcs = Get-Process -Name 'npm' -ErrorAction SilentlyContinue
foreach ($p in $npmProcs) {
    try { Stop-Process -Id $p.Id -Force } catch {}
}

# Step 2: Stop backend (delegate to scripts/1b/stop-backend.ps1)
Write-Host "[stop-fincontrol] Stopping backend (scripts/1b/stop-backend.ps1)..." -ForegroundColor Cyan
if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'scripts\1b\stop-backend.ps1')) {
    & "$ProjectRoot\scripts\1b\stop-backend.ps1"
} else {
    Write-Host "[stop-fincontrol] WARN: scripts\1b\stop-backend.ps1 not found; falling back to manual java process search" -ForegroundColor Yellow
    $javaProcs = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like '*fincontrol-backend*' -or $_.Modules.FileName -like '*fincontrol*'
    }
    foreach ($p in $javaProcs) {
        try { Stop-Process -Id $p.Id -Force; Write-Host "[stop-fincontrol] OK java($($p.Id)) stopped" -ForegroundColor Green } catch {}
    }
}

# Step 3: Ask user about MySQL
Write-Host ""
$ans = Read-Host "[stop-fincontrol] Stop MySQL80 too? (y/N)"
if ($ans -eq 'y' -or $ans -eq 'Y') {
    Stop-Service -Name 'MySQL80' -Force -ErrorAction SilentlyContinue
    Write-Host "[stop-fincontrol] OK MySQL80 stopped" -ForegroundColor Green
} else {
    Write-Host "[stop-fincontrol] MySQL80 kept running" -ForegroundColor Gray
}

Write-Host ""
Write-Host "[stop-fincontrol] All services stopped" -ForegroundColor Green
