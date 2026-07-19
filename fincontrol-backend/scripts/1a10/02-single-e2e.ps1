# 1a.10 single-image real E2E (minimax primary).
# 4 images x 1 upload + 1 POST /api/screenshot/parse each (independent).
# Verifies 4/4 return code=0 and parsed.funds has fund data.
# Backend start: Start-Process java -jar target/fincontrol-backend.jar

function Read-Utf8([string]$Path) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

$ErrorActionPreference = "Stop"
$h = "http://localhost:8080"
$u = 18008
$base = "c:\Users\lbc19\Desktop\Fincontrol\fincontrol-backend"
$samples = Join-Path $base "uploads\samples"
$pages = @(
    @{ name = "P1"; file = "phase1a2-alipay-fund-list-20260715-2355-1.jpg"; expectTopVisible = $false; expectCompleteFunds = 6 },
    @{ name = "P2"; file = "phase1a2-alipay-fund-list-20260715-2355-2.jpg"; expectTopVisible = $true;  expectTopAmount = 7884.68; expectCompleteFunds = 3 },
    @{ name = "P3"; file = "phase1a2-alipay-fund-list-20260715-2355-3.jpg"; expectTopVisible = $false; expectCompleteFunds = 5 },
    @{ name = "P4"; file = "phase1a2-alipay-fund-list-20260715-2356-1.jpg"; expectTopVisible = $false; expectCompleteFunds = 6 }
)

$failures = New-Object System.Collections.Generic.List[object]
$results = New-Object System.Collections.Generic.List[object]

foreach ($p in $pages) {
    $path = Join-Path $samples $p.file
    Write-Host "====================================================="
    $banner = "=== " + $p.name + " : " + $p.file
    Write-Host $banner
    Write-Host "====================================================="

    # 1) upload
    $uploadPath = Join-Path $base "logs\1a10-single-$($p.name)-upload.body"
    & curl.exe -s -X POST -H "X-User-Id: 1" -F "file=@$path" ($h + "/api/screenshot/upload") -o $uploadPath
    $uploadTxt = Read-Utf8 $uploadPath
    $uploadLine = "upload: " + $uploadTxt
    Write-Output $uploadLine
    $upObj = $uploadTxt | ConvertFrom-Json
    if (-not $upObj.data -or -not $upObj.data.fileId) {
        $failLine = "FAIL upload " + $p.name
        Write-Host $failLine
        $failures.Add(@{ page = $p.name; stage = "upload" }) | Out-Null
        continue
    }
    $fileId = $upObj.data.fileId
    $okLine = "upload OK fileId=" + $fileId
    Write-Host $okLine

    # 2) parse (POST /api/screenshot/parse, single-image minimax primary)
    $body = (@{ userId = $u; fileId = $fileId } | ConvertTo-Json -Compress)
    $reqPath = Join-Path $base "logs\1a10-single-$($p.name)-body.json"
    [IO.File]::WriteAllText($reqPath, $body, (New-Object System.Text.UTF8Encoding $false))
    $respPath = Join-Path $base "logs\1a10-single-$($p.name)-resp.json"
    & curl.exe -s --data-binary ('@' + $reqPath) -H "X-User-Id: 1" -H "Content-Type: application/json" ($h + "/api/screenshot/parse") -o $respPath
    $parseTxt = Read-Utf8 $respPath
    $parsePreview = $parseTxt.Substring(0, [Math]::Min(500, $parseTxt.Length))
    $parseLine = "parse raw: " + $parsePreview
    Write-Output $parseLine

    try {
        $parseObj = $parseTxt | ConvertFrom-Json
    } catch {
        $decFail = "FAIL parse not JSON: " + $parseTxt.Substring(0, 200)
        Write-Host $decFail
        $failures.Add(@{ page = $p.name; stage = "parse-decode" }) | Out-Null
        continue
    }

    if ($parseObj.code -ne 0) {
        $codeLine = "FAIL parse code=" + $parseObj.code + " msg=" + $parseObj.message
        Write-Host $codeLine
        $failures.Add(@{ page = $p.name; stage = "parse-code" }) | Out-Null
        continue
    }

    $parsed = $parseObj.data
    if (-not $parsed) {
        Write-Host "FAIL parse no data"
        $failures.Add(@{ page = $p.name; stage = "parse-nodata" }) | Out-Null
        continue
    }

    $categoryCount = 0
    $totalAmount = 0.0
    $fundNames = @()
    if ($parsed.categories) {
        $categoryCount = @($parsed.categories).Count
        foreach ($cat in $parsed.categories) {
            if ($cat.funds) {
                foreach ($f in $cat.funds) {
                    $fundNames += $f.fundName
                    if ($f.amount) { $totalAmount += [double]$f.amount }
                }
            }
        }
    }

    $topValue = $parsed.totalAsset
    $topSource = $parsed.totalAssetSource
    $topLine = "  parsed.top      = " + $topValue
    Write-Host $topLine
    $topSrcLine = "  parsed.topSource= " + $topSource
    Write-Host $topSrcLine
    $catsLine = "  parsed.categories=" + $categoryCount
    Write-Host $catsLine
    $fcLine = "  parsed.fundCount=" + $fundNames.Count
    Write-Host $fcLine
    $fsLine = "  parsed.fundSum  = " + $totalAmount.ToString("F2")
    Write-Host $fsLine
    $fnLine = "  parsed.funds    = " + ($fundNames -join ",")
    Write-Host $fnLine

    $ok = $true
    if ($categoryCount -lt 1) {
        Write-Host "  [WARN] no categories returned"
        $ok = $false
    }
    if ($fundNames.Count -lt $p.expectCompleteFunds) {
        $expLine = "  [WARN] expected at least " + $p.expectCompleteFunds + " complete funds, got " + $fundNames.Count
        Write-Host $expLine
        $ok = $false
    }
    if ($p.expectTopVisible -and -not $topValue) {
        Write-Host "  [WARN] expected top totalAsset to be visible"
        $ok = $false
    }
    if ($ok) {
        Write-Host "  OK"
        $results.Add(@{ page = $p.name; fileId = $fileId; top = $topValue; source = $topSource; funds = $fundNames.Count; sum = $totalAmount; categories = $categoryCount; pass = $true }) | Out-Null
    } else {
        $results.Add(@{ page = $p.name; fileId = $fileId; top = $topValue; source = $topSource; funds = $fundNames.Count; sum = $totalAmount; categories = $categoryCount; pass = $false }) | Out-Null
        $failures.Add(@{ page = $p.name; stage = "validation" }) | Out-Null
    }
}

Write-Host ""
Write-Host "============================================="
Write-Host "1a.10 single-image minimax E2E summary (v2.7.1)"
Write-Host "============================================="
foreach ($r in $results) {
    $status = if ($r.pass) { "OK  " } else { "FAIL" }
    $line = "{0}  {1}  top={2} source={3} funds={4} sum={5} cats={6}" -f $status, $r.page, $r.top, $r.source, $r.funds, $r.sum, $r.categories
    Write-Host $line
}
Write-Host ""
if ($failures.Count -eq 0) {
    Write-Host "RESULT: 4/4 single-image minimax E2E PASS"
} else {
    $summary = ("RESULT: " + $failures.Count + " failed / " + $pages.Count + " total")
    Write-Host $summary
    foreach ($f in $failures) {
        $stage = $f.stage
        $page = $f.page
        $failLine = "  - " + $page + "  " + $stage
        Write-Host $failLine
    }
}