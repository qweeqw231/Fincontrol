# Start the backend using java -jar in the background (Spring Boot profile=local)
$ErrorActionPreference = "Stop"
$base = "c:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"
$logs = Join-Path $base "logs"
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Force -Path $logs | Out-Null }
$jar = Join-Path $base "target\fincontrol-backend.jar"
$out = Join-Path $logs "backend.out.log"
$err = Join-Path $logs "backend.err.log"
if (-not (Test-Path $jar)) { throw "jar not found: $jar" }

# Clean any prior process
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*jdk-17*" } | Stop-Process -Force
Start-Sleep -Seconds 1

$proc = Start-Process -FilePath "C:\Program Files\Java\jdk-17\bin\java.exe" `
    -ArgumentList "-jar", $jar `
    -RedirectStandardOutput $out `
    -RedirectStandardError $err `
    -WorkingDirectory $base `
    -PassThru `
    -WindowStyle Hidden

Write-Host ("started PID=" + $proc.Id)
$proc.Id | Out-File -Encoding ascii (Join-Path $logs "backend.pid")