# 1a.10 real confirm E2E (4 images -> parse -> aggregate -> single confirm)
# Input: P1..P4 parse responses from 02-single-e2e.ps1 (logs\1a10-single-*-resp.json)
# Output: user 19999 real MySQL three-table mirror (asset_raw=19, asset_snapshot=7, fund_category_map=19)
# Backend start: Start-Process java -jar target/fincontrol-backend.jar

function Read-Utf8([string]$Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

$ErrorActionPreference = "Stop"
$h = "http://localhost:8080"
$u = 19999  # isolated user; no pollution of user 1 / 18008
$base = "c:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"
$logs = Join-Path $base "logs"
if (-not (Test-Path $logs)) { New-Item -ItemType Directory -Force -Path $logs | Out-Null }

# 0) Cleanup user 19999 today's (2026-07-19) old data to avoid mirror pollution
$cleanupSql = "USE fincontrol;`nDELETE FROM asset_raw      WHERE user_id = $u AND snapshot_date = '2026-07-19';`nDELETE FROM asset_snapshot WHERE user_id = $u AND snapshot_date = '2026-07-19';`n-- fund_category_map keep history; this run will refresh last_seen_at`n"
$cleanupPath = Join-Path $logs "1a10-cleanup-19999.sql"
[IO.File]::WriteAllText($cleanupPath, $cleanupSql, (New-Object System.Text.UTF8Encoding $false))
Write-Host "=== cleanup user $u ==="
$cleanupProc = Start-Process -FilePath "mysql" -ArgumentList "-u","root","-proot","fincontrol" -RedirectStandardInput $cleanupPath -NoNewWindow -Wait -PassThru
$cleanupLine = "cleanup exit=" + $cleanupProc.ExitCode
Write-Host $cleanupLine

# 1) Read 4 pages parse responses, build parsedAssets[] for confirm body
$parsedAssets = New-Object System.Collections.Generic.List[object]
foreach ($name in @("P1","P2","P3","P4")) {
    $respPath = Join-Path $logs "1a10-single-$name-resp.json"
    if (-not (Test-Path $respPath)) { throw "missing $respPath" }
    $txt = Read-Utf8 $respPath
    $obj = $txt | ConvertFrom-Json
    if ($obj.code -ne 0) { throw "P $name parse code != 0 ($($obj.code)): $($obj.message)" }
    $pa = $obj.data
    if (-not $pa) { throw "P $name parse no data" }
    # Use the ParsedAsset fields directly; do NOT add `page` field (not in DTO)
    $parsedAssets.Add($pa) | Out-Null
    $catsCount = @($pa.categories).Count
    $pageLine = "P " + $name + " parsed: categories=" + $catsCount
    Write-Host $pageLine
}

# 2) Build confirm request body (SnapshotConfirmRequest shape)
$confirmBody = @{
    userId = $u
    snapshotDate = "2026-07-19"
    snapshotNote = "1a.10 real E2E - path A confirm"
    parsedAssets = $parsedAssets
    confirmedOverwrite = $true
    includeBalance = $true
} | ConvertTo-Json -Compress -Depth 12
$confirmPath = Join-Path $logs "1a10-confirm-body.json"
[IO.File]::WriteAllText($confirmPath, $confirmBody, (New-Object System.Text.UTF8Encoding $false))
$bodyBytes = (Get-Item -LiteralPath $confirmPath).Length
$bodyLine = "confirm req body bytes=" + $bodyBytes + " @ " + $confirmPath
Write-Host $bodyLine

# 3) POST /api/snapshot/confirm (SnapshotController 1a.3)
$confirmRespPath = Join-Path $logs "1a10-confirm-resp.json"
$curlExpr = "curl.exe -s --data-binary `@" + $confirmPath + " -H ""X-User-Id: 1"" -H ""Content-Type: application/json"" -o """ + $confirmRespPath + """ """ + ($h + "/api/snapshot/confirm") + """"
cmd.exe /c $curlExpr
$respTxt = Read-Utf8 $confirmRespPath
$respLine = "confirm raw: " + $respTxt
Write-Output $respLine
$respObj = $respTxt | ConvertFrom-Json
if ($respObj.code -ne 0) {
    throw ("confirm code != 0 (" + $respObj.code + "): " + $respObj.message)
}
$data = $respObj.data
if (-not $data) { throw "confirm no data" }
# Actual response fields: assetRawInserted, assetSnapshotUpserted, dedupReport
$rawInserted = $data.assetRawInserted
$snapshotUpserted = $data.assetSnapshotUpserted
$dedupInput = $data.dedupReport.inputRecordCount
$dedupMerged = $data.dedupReport.mergedRecordCount
$dedupDropped = $data.dedupReport.droppedCount

$line1 = "confirm data.assetRawInserted     = " + $rawInserted
Write-Host $line1
$line2 = "confirm data.assetSnapshotUpserted = " + $snapshotUpserted
Write-Host $line2
$line3 = "confirm dedupReport.inputRecordCount = " + $dedupInput
Write-Host $line3
$line4 = "confirm dedupReport.mergedRecordCount = " + $dedupMerged
Write-Host $line4
$line5 = "confirm dedupReport.droppedCount = " + $dedupDropped
Write-Host $line5

# 4) Validation
if ($rawInserted -ne 19) { throw ("rawInserted != 19 (got " + $rawInserted + ")") }
if ($snapshotUpserted -ne 7) { throw ("snapshotUpserted != 7 (got " + $snapshotUpserted + ")") }
if ($dedupInput -ne 20) { throw ("dedupInput != 20 (got " + $dedupInput + ")") }
if ($dedupMerged -ne 19) { throw ("dedupMerged != 19 (got " + $dedupMerged + ")") }
if ($dedupDropped -ne 1) { throw ("dedupDropped != 1 (got " + $dedupDropped + ")") }

Write-Host ""
Write-Host "============================================="
Write-Host "1a.10 real confirm result OK"
Write-Host "============================================="
$summaryLine = ("user " + $u + ", assetRawInserted=" + $rawInserted + ", assetSnapshotUpserted=" + $snapshotUpserted + ", dedupInput=" + $dedupInput + ", dedupMerged=" + $dedupMerged + ", dedupDropped=" + $dedupDropped)
Write-Host $summaryLine
