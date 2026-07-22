# scripts/1b/restart-backend.ps1
# Decision 24: Backend restart MUST use this SOP (kill + clean + build + start + healthcheck, idempotent)
# Usage: powershell -ExecutionPolicy Bypass -File scripts\1b\restart-backend.ps1
# Expected output: BUILD SUCCESS + backend PID + /actuator/health = UP

$ErrorActionPreference = 'Stop'
$WorkspaceRoot = Split-Path -Parent $PSScriptRoot | Split-Path -Parent
$BackendDir   = Join-Path $WorkspaceRoot 'fincontrol-backend'
$LogDir       = Join-Path $WorkspaceRoot 'log'
$JavaHome     = 'C:\Program Files\Java\jdk-17'
$JavaExe      = Join-Path $JavaHome 'bin\java.exe'
$JarPath      = Join-Path $BackendDir 'target\fincontrol-backend.jar'
$PomPath      = Join-Path $BackendDir 'pom.xml'
$StdoutLog   = Join-Path $LogDir 'backend-stdout.log'
$StderrLog   = Join-Path $LogDir 'backend-stderr.log'
$HealthUrl    = 'http://localhost:8080/actuator/health'

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# [Console]::OutputEncoding = [System.Text.Encoding]::UTF8   # uncomment if logs need utf8

Write-Host '=== Step 1: Kill java processes (keep VSCode JDT-LS) ===' -ForegroundColor Cyan
$fincontrolProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*fincontrol-backend*' }
foreach ($p in $fincontrolProcs) {
  $cmd = $p.CommandLine
  if ($cmd.Length -gt 80) { $cmd = $cmd.Substring(0, 80) }
  Write-Host "  Killing PID $($p.ProcessId) : $cmd"
  Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}
Get-Process mvn -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host '=== Step 2: Clean target ===' -ForegroundColor Cyan
$targetPath = Join-Path $BackendDir 'target'
if (Test-Path $targetPath) {
  Remove-Item $targetPath -Recurse -Force
  Write-Host '  target/ removed'
}

Write-Host '=== Step 3: mvn package -DskipTests ===' -ForegroundColor Cyan
$buildOut = & mvn -f $PomPath package -B -DskipTests 2>&1
$buildOut | Select-String -Pattern 'BUILD|ERROR' | ForEach-Object { Write-Host "  $_" }
if ($LASTEXITCODE -ne 0) { throw 'mvn package failed' }
if (-not (Test-Path $JarPath)) { throw "jar not found at $JarPath" }
$jarSize = (Get-Item $JarPath).Length
Write-Host "  jar size: $([Math]::Round($jarSize/1MB, 2)) MB" -ForegroundColor Green

Write-Host '=== Step 4: Start backend ===' -ForegroundColor Cyan
'' | Set-Content $StdoutLog
'' | Set-Content $StderrLog

$proc = Start-Process -FilePath $JavaExe `
  -ArgumentList @('-jar', $JarPath, '--spring.profiles.active=local') `
  -WorkingDirectory $BackendDir `
  -RedirectStandardOutput $StdoutLog `
  -RedirectStandardError  $StderrLog `
  -PassThru -WindowStyle Hidden
Write-Host "  backend started, PID=$($proc.Id)"

Write-Host '=== Step 5: Healthcheck (poll 30s) ===' -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds(30)
$healthy = $false
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-RestMethod -Uri $HealthUrl -Method Get -TimeoutSec 2
    if ($r.status -eq 'UP') {
      Write-Host '  /actuator/health = UP' -ForegroundColor Green
      $healthy = $true
      break
    }
  } catch {
    # not ready yet
  }
}
if (-not $healthy) {
  Write-Host '  Healthcheck timeout. Last 30 lines of stderr:' -ForegroundColor Red
  Get-Content $StderrLog -Tail 30 | ForEach-Object { Write-Host "    $_" }
  throw 'backend not healthy'
}

Write-Host ''
Write-Host '=== Restart complete ===' -ForegroundColor Green
Write-Host "  jar       : $JarPath"
Write-Host "  stdout log: $StdoutLog"
Write-Host "  stderr log: $StderrLog"
Write-Host "  PID       : $($proc.Id)"
Write-Host '  next step : node test/1b/step7.mjs'
