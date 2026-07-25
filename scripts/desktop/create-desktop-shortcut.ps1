# 1b.4 PR8 / Decision 35: Create desktop shortcut FinControl.lnk
# Pure-ASCII only (PowerShell 5.1 compatibility, no Chinese chars in source)
# Idempotent: existing shortcut will be skipped

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

$DesktopPath = [Environment]::GetFolderPath('Desktop')
$lnkPath = Join-Path $DesktopPath 'FinControl.lnk'
$logoPath = Join-Path $ProjectRoot 'fincontrol-frontend\public\brand\logo.png'
$targetScript = Join-Path $ProjectRoot 'scripts\desktop\launch-fincontrol.ps1'

if (-not (Test-Path -LiteralPath $logoPath)) {
    Write-Host "[create-shortcut] ERROR: logo.png not found at $logoPath" -ForegroundColor Red
    Write-Host "[create-shortcut] Please run Step 0 first to copy brand assets" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path -LiteralPath $targetScript)) {
    Write-Host "[create-shortcut] ERROR: launch-fincontrol.ps1 not found at $targetScript" -ForegroundColor Red
    exit 1
}

if (Test-Path -LiteralPath $lnkPath) {
    Write-Host "[create-shortcut] Desktop FinControl.lnk already exists; skipping" -ForegroundColor Cyan
    Write-Host "[create-shortcut]   Path: $lnkPath" -ForegroundColor Gray
    Write-Host "[create-shortcut]   Delete it manually if you want to re-create" -ForegroundColor Gray
    exit 0
}

Write-Host "[create-shortcut] Creating desktop shortcut..." -ForegroundColor Cyan
Write-Host "[create-shortcut]   Desktop path: $lnkPath" -ForegroundColor Gray
Write-Host "[create-shortcut]   Target script: $targetScript" -ForegroundColor Gray
Write-Host "[create-shortcut]   Icon: $logoPath" -ForegroundColor Gray

try {
    $WshShell = New-Object -ComObject WScript.Shell
    $Shortcut = $WshShell.CreateShortcut($lnkPath)
    $Shortcut.TargetPath = 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe'
    $Shortcut.Arguments = "-ExecutionPolicy Bypass -File `"$targetScript`""
    $Shortcut.WorkingDirectory = $ProjectRoot
    $Shortcut.IconLocation = $logoPath
    $Shortcut.Description = 'FinControl - Personal Asset Allocation Control (click to launch)'
    $Shortcut.WindowStyle = 7
    $Shortcut.Save()

    Write-Host "[create-shortcut] OK Desktop shortcut created" -ForegroundColor Green
    Write-Host ""
    Write-Host "You can now:" -ForegroundColor Cyan
    Write-Host "  1. Double-click FinControl.lnk on your desktop to launch the system" -ForegroundColor White
    Write-Host "  2. Browser will auto-open http://localhost:5173/" -ForegroundColor White
    Write-Host "  3. After uploading snapshots, click the top-right shutdown button to stop services" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Host "[create-shortcut] ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
