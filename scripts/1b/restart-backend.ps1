# scripts/1b/restart-backend.ps1
# Decision 24: Backend restart MUST use this SOP (kill + clean + build + start + healthcheck, idempotent)
# Usage: powershell -ExecutionPolicy Bypass -File scripts\1b\restart-backend.ps1
#        powershell -ExecutionPolicy Bypass -File scripts\1b\restart-backend.ps1 -SkipRebuild
# Expected output: BUILD SUCCESS + backend PID + /actuator/health = UP
#
# Decision 36 (2026-09-30): single-process runtime -- backend also serves the frontend
# build output (fincontrol-frontend/dist), so this script now passes an absolute static
# location. -SkipRebuild reuses an existing jar (daily "open" path is much faster);
# the full clean+build path stays the default for development.

param(
    [switch]$SkipRebuild
)

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

# Decision 36: frontend dist location (absolute, so it does not depend on the working dir)
$FrontendDist = (Join-Path $WorkspaceRoot 'fincontrol-frontend\dist') -replace '\\', '/'
$StaticLocationsArg = "--fincontrol.frontend.static-locations=classpath:/static/,file:$FrontendDist/"

# Decision 36: resolve mvn explicitly. A desktop shortcut starts a fresh powershell.exe,
# whose PATH is inherited from the registry and may not resolve 'mvn' (the machine PATH
# stores a literal "%MAVEN_HOME%\bin" entry). Explicit candidates + PATH as last resort.
$MvnCmd = $null
$mvnCandidates = @()
if ($env:MAVEN_HOME) { $mvnCandidates += (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd') }
$mvnCandidates += 'C:\apache-maven-3.9.16\bin\mvn.cmd'
$mvnOnPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mvnOnPath) { $mvnCandidates += $mvnOnPath.Source }
foreach ($cand in $mvnCandidates) {
    if ($cand -and (Test-Path -LiteralPath $cand)) { $MvnCmd = $cand; break }
}

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

if ($SkipRebuild -and (Test-Path $JarPath)) {
    $jarSize = (Get-Item $JarPath).Length
    Write-Host "=== Step 2-3: skipped (-SkipRebuild, reusing jar $([Math]::Round($jarSize/1MB, 2)) MB) ===" -ForegroundColor Yellow
} else {
    Write-Host '=== Step 2: Clean target ===' -ForegroundColor Cyan
    $targetPath = Join-Path $BackendDir 'target'
    if (Test-Path $targetPath) {
      Remove-Item $targetPath -Recurse -Force
      Write-Host '  target/ removed'
    }

    Write-Host '=== Step 3: mvn package -DskipTests ===' -ForegroundColor Cyan
    if (-not $MvnCmd) {
        Write-Host '  ERROR: mvn not found. Set MAVEN_HOME or install Maven 3.9.x, then retry.' -ForegroundColor Red
        throw 'mvn not found'
    }
    Write-Host "  using: $MvnCmd" -ForegroundColor Gray
    $buildOut = & $MvnCmd -f $PomPath package -B -DskipTests 2>&1
    $buildExit = $LASTEXITCODE
    $buildOut | Select-String -Pattern 'BUILD|ERROR' | ForEach-Object { Write-Host "  $_" }
    if ($buildExit -ne 0 -or -not (Test-Path $JarPath)) {
        # Decision 36: dump the tail of the build log instead of failing silently.
        # First-run builds have failed here on a slow/unreachable Maven Central, and the
        # old script only said "jar not found", which said nothing about the real cause.
        Write-Host "  BUILD FAILED (mvn exit=$buildExit). Last 30 lines:" -ForegroundColor Red
        $buildOut | Select-Object -Last 30 | ForEach-Object { Write-Host "    $_" }
        if ($buildExit -eq 0) { throw 'mvn package reported success but no jar was produced' }
        throw 'mvn package failed'
    }
    $jarSize = (Get-Item $JarPath).Length
    Write-Host "  jar size: $([Math]::Round($jarSize/1MB, 2)) MB" -ForegroundColor Green
}

Write-Host '=== Step 4: Start backend ===' -ForegroundColor Cyan
'' | Set-Content $StdoutLog
'' | Set-Content $StderrLog

$proc = Start-Process -FilePath $JavaExe `
  -ArgumentList @('-jar', $JarPath, '--spring.profiles.active=local', $StaticLocationsArg) `
  -WorkingDirectory $BackendDir `
  -RedirectStandardOutput $StdoutLog `
  -RedirectStandardError  $StderrLog `
  -PassThru -WindowStyle Hidden
Write-Host "  backend started, PID=$($proc.Id)"
Write-Host "  static locations: $StaticLocationsArg"

Write-Host '=== Step 5: Healthcheck (poll 90s) ===' -ForegroundColor Cyan
# Decision 36: raised from 30s to 90s -- a cold JVM start plus HikariCP/MyBatis init
# regularly exceeds 30s on this machine, which made the old timeout report false failures.
$deadline = (Get-Date).AddSeconds(90)
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
